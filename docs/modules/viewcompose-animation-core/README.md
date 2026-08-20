# Animation Core

`viewcompose-animation-core` is the platform-neutral timing and sampling engine for ViewCompose
motion. It defines immutable animation specifications, easing and value conversion, deterministic
timeline sampling, coroutine-driven frame loops, a low-level mutable animated value, and shared
transition-segment coordination. It contains no Android UI or composition dependency.

## Artifact and stability

```kotlin
dependencies {
    implementation("com.viewcompose:viewcompose-animation-core:0.1.0-alpha04")
}
```

- Stability: **Alpha**. Timing normalization, repetition, cancellation, and transition-segment
  behavior are reviewed and tested; names and higher-level composition integration may still evolve
  between alphas.
- Platform: Kotlin/JVM with no Android framework dependency.
- `viewcompose-runtime` is exposed transitively because `MonotonicFrameClock` is part of public
  clock and animation APIs. Kotlin coroutines provide structured cancellation.
- Applications normally receive it transitively from `viewcompose-animation`; depend on it directly
  for custom runtimes, deterministic sampling, preview tooling, or platform-neutral tests.

## Specifications and easing

`AnimationSpec` is an immutable time-to-progress description. It does not own a clock, coroutine, or
value. The built-in families are:

- `tween`: fixed duration, optional delay, and an `Easing` curve;
- `spring`: a deterministic bounded damped-oscillation approximation over a fixed duration;
- `keyframes`: timestamped progress checkpoints with linear interpolation between them;
- `snap`: immediate target selection;
- `repeatable`: a finite number of restart or alternating cycles;
- `infiniteRepeatable`: cycles until its driving coroutine is cancelled.

```kotlin
val motion = repeatable(
    iterations = 2,
    animation = tween(
        durationMillis = 240,
        easing = EasingDefaults.FastOutSlowIn,
    ),
    repeatMode = RepeatMode.Reverse,
)
```

The engine normalizes negative delays to zero and non-positive finite durations to one millisecond.
A zero-iteration repeat is the exception: it has zero duration and remains at the start value.
Factories preserve requested values in the immutable object; normalization occurs during duration
queries and sampling.

`EasingDefaults` provides allocation-stable polynomial curves. `CubicBezierEasing` supports custom
control points and performs a bounded inversion of the x axis. Keep Bézier x coordinates in
`0f..1f`; the constructor does not reject non-monotonic curves. The engine clamps final visual
progress to `0f..1f`, including spring and easing output, so animation-core does not expose visual
overshoot in this release.

## Semantic motion schemes and reduced motion

`MotionScheme` groups five semantic timing roles without naming a component or design system:
fast/default effects, fast/default spatial movement, and expressive spatial movement. A component
selects a `MotionRole`; it does not copy raw durations into its structural recipe. The immutable
scheme owns neither a clock nor animation state and resolves to the existing `AnimationSpec`
families.

`ReducedMotionPolicy` resolves the same target state while replacing non-essential movement with
`SnapSpec` or a shortened specification. Essential state communication is duration-scaled rather
than hidden. Scaling applies recursively to tween delay, bounded spring duration, keyframe duration
and checkpoints, and repeating child specifications. Applications or integration roots supply the
host's reduced-motion decision explicitly; animation-core performs no platform settings lookup.

`MotionInterruptionPolicy.RetargetFromCurrent` matches the last-writer behavior in
`viewcompose-animation`. `SnapToTarget` is a component-owner policy: the owner must select the
target immediately instead of starting a runner. A scheme never launches competing loops.

## Deterministic sampling

`sampleAnimationValue` evaluates one specification at an explicit nanosecond play time. It has no
clock, coroutine, or state ownership, making it the preferred primitive for seeking, tests,
transition channels, and preview tooling:

```kotlin
val halfway = sampleAnimationValue(
    startValue = 20f,
    endValue = 100f,
    animationSpec = tween(durationMillis = 400, easing = EasingDefaults.Linear),
    converter = AnimationConverters.Float,
    playTimeNanos = 200_000_000L,
)
```

`animationDurationNanos` includes tween delay, multiplies repeat cycles with saturation instead of
overflow, and returns `Long.MAX_VALUE` for an infinite repeat. `isAnimationFinished` always returns
false for infinite repeats.

Sampling allocates start, end, and result vectors through the converter on each call. Frame-sensitive
custom runtimes should avoid unnecessary wrapper allocation and should not use converters for
blocking or I/O-bound work.

## Value conversion

`AnimationConverter<T>` decomposes a domain value into independently interpolated `Float`
dimensions, then reconstructs it. Implementations must keep a stable dimension count, return an
independent vector, and never retain the result vector passed to `fromVector`.

Built-in converters cover `Float`, `Int`, and packed ARGB `Int`. Integer reconstruction truncates
toward zero. ARGB conversion interpolates encoded channels independently; it is not gamma-correct or
color-space aware.

```kotlin
data class Point(val x: Float, val y: Float)

val converter = object : AnimationConverter<Point> {
    override fun toVector(value: Point) = floatArrayOf(value.x, value.y)

    override fun fromVector(vector: FloatArray) = Point(vector[0], vector[1])
}
```

When endpoint converter dimensions differ, sampling uses the start vector's size. Missing end
dimensions retain their corresponding start dimensions; extra end dimensions are ignored. Treat a
dimension mismatch as a converter defect rather than relying on this recovery behavior.

## Frame-driven execution and cancellation

`runAnimation` awaits a `MonotonicFrameClock` and calls `onValue` for each sample on the caller's
coroutine. A finite completion publishes the exact terminal sample after the frame loop, so the
terminal value can be observed twice. Infinite specifications return only through cancellation.

Cancellation does not force the target value. Depending on the frame clock, cancellation either
propagates as the coroutine's cancellation exception or is observed between callbacks and reported
as `AnimationRunResult.Cancelled`. Frame-clock and callback exceptions propagate unchanged. Keep
callbacks short because they execute in the frame path.

`AnimatableCore` stores the latest sample but intentionally does not provide a mutex, mutation
priority, or coroutine scope. Concurrent `animateTo` and `snapTo` calls can overwrite each other.
Higher-level code must serialize mutations or cancel and join the previous job before retargeting.
Cancellation leaves the last published value available.

`viewcompose-animation` provides the composition-aware, last-writer-oriented APIs most application
code should use. Use `AnimatableCore` directly only when the caller already owns structured
concurrency and a frame clock.

## Multi-channel transition coordination

`TransitionCore<S>` coordinates logical endpoints and one timeline across multiple animation
channels. A transition owner follows this order:

1. call `updateTarget` when the desired state changes;
2. register every channel's normalized duration with `registerDuration`;
3. advance the shared segment using `updatePlayTime`;
4. let time reaching the maximum duration commit the target, or call `finishRunningSegment`.

The longest registered channel defines segment duration. Shorter channels settle in their own
samplers. During a running retarget, the next logical segment starts from the previous target rather
than each channel's current sampled value; higher-level channel owners preserve visual continuity.
`TransitionCore` is not thread-safe and does not launch or cancel work.

## Testing custom animation code

- Use `sampleAnimationValue` for exact boundary, delay, repeat, and reverse-cycle assertions.
- Supply a deterministic fake `MonotonicFrameClock` when testing `runAnimation` or `AnimatableCore`.
- Verify cancellation before the target and ensure no terminal sample is forced.
- Verify custom converter round trips, stable dimensions, missing-data policy, and numeric precision.
- For transitions, register channels before advancing time and test mid-segment retargeting
  explicitly.

The module test suite covers tween completion and delay, reverse-repeat terminal state, infinite
frame pacing, cancellation, ARGB round trips, maximum channel duration, transition retargeting,
semantic role resolution, and deterministic reduced-motion substitution.

## Related documentation

- [Runtime module](../viewcompose-runtime/README.md)
- [Architecture overview](../../architecture/overview.md)
- [Source documentation and API comment standard](../../project/api-documentation-quality.md)
- [Project roadmap](../../project/roadmap.md)

The complete generated reference is available in the
[`viewcompose-animation-core` API tree](https://docs.viewcompose.com/api/viewcompose-animation-core/current/).

## Compatibility notes

The `0.1.0-alpha03` line establishes normalized finite timing, restart and reverse repetition,
frame-clock-driven cancellation, per-dimension converters, and shared transition-segment timing.
These contracts are intentionally platform-neutral; Android interop belongs to host modules and
composition ownership belongs to `viewcompose-animation`.
