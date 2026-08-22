# Animation Core

`viewcompose-animation-core` is the platform-neutral timing and physical-motion engine for
ViewCompose. It defines immutable animation specifications, easing, value/velocity conversion,
deterministic explicit-time sampling, coroutine-driven frame loops, a low-level last-writer
animated value, and shared transition-segment coordination. It contains no Android UI or
composition dependency.

## Artifact and stability

```kotlin
dependencies {
    implementation("com.viewcompose:viewcompose-animation-core:0.1.0-alpha04")
}
```

- Stability: **Alpha**. Physical units, cancellation, bounds, results, timing normalization,
  repetition, and transition-segment behavior are reviewed and tested; names and higher-level
  composition integration may still evolve between alphas.
- Platform: Kotlin/JVM with no Android framework dependency.
- `viewcompose-runtime` is exposed transitively because `MonotonicFrameClock` is part of public
  clock and animation APIs. Kotlin coroutines provide structured cancellation.
- Applications normally receive it transitively from `viewcompose-animation`; depend on it
  directly for custom runtimes, deterministic sampling, preview tooling, or platform-neutral tests.

## Specifications, physics, and easing

`AnimationSpec` is an immutable motion description. It owns no clock, coroutine, or value.
`FiniteAnimationSpec` separates converging target motion from `InfiniteRepeatableSpec`, while
`DurationBasedAnimationSpec` identifies specifications legal inside finite or infinite repetition.

- `tween`: fixed duration, optional delay, and an `Easing` curve;
- `spring`: normalized-mass physical motion with damping, stiffness, typed initial velocity,
  equilibrium termination, and a safety guard rather than a nominal duration;
- `keyframes`: timestamped progress checkpoints with linear interpolation;
- `snap`: immediate target selection;
- `repeatable`: a finite number of restart or alternating duration-based cycles;
- `infiniteRepeatable`: duration-based cycles until cancellation; and
- `exponentialDecay`: target-free velocity decay with a friction multiplier and safety guard.

```kotlin
val motion = repeatable(
    iterations = 2,
    animation = tween(
        durationMillis = 240,
        easing = EasingDefaults.FastOutSlowIn,
    ),
    repeatMode = RepeatMode.Reverse,
)

val physical = spring(
    dampingRatio = 0.72f,
    stiffness = 240f,
)
```

The engine normalizes negative delays to zero and non-positive duration-based intervals to one
millisecond. A zero-iteration repeat has zero duration and remains at its start. Factories retain
requested values; evaluator construction owns normalization and one-time keyframe ordering.

`SpringSpec` solves `x'' + 2ζω₀x' + ω₀²(x - target) = 0` with normalized mass `1` and
`ω₀ = sqrt(stiffness)`. Under-, critical-, and over-damped branches use `Double` intermediates and
sample from the segment start rather than integrating previous frames. A settled spring publishes
the exact target and zero velocity. A solve that reaches `maxDurationMillis` retains its physical
sample and reports `DurationLimitReached`; `durationMillis` no longer exists. Springs cannot be
repeated because equilibrium time depends on endpoints, velocity, and thresholds.

`ExponentialDecaySpec` uses `λ = 4.2 × frictionMultiplier s⁻¹`. It stops at its converter-derived
velocity threshold, a bound, or its safety guard. It is deliberately platform-neutral and is not
an Android spline fling.

`EasingDefaults` provides allocation-stable polynomial curves. `CubicBezierEasing` performs a
bounded inversion of the x axis for custom control points. Duration-spec progress is clamped to
`0f..1f`; physical spring values are not progress-clamped and can overshoot.

## Semantic motion schemes and reduced motion

`MotionScheme` groups fast/default effect and spatial roles plus expressive spatial motion without
naming a component or design system. A component selects a `MotionRole`; it does not copy raw
parameters into its structural recipe.

`ReducedMotionPolicy` resolves the same logical target while replacing non-essential movement with
`SnapSpec` or a shortened specification. Scaling applies recursively to tween delay, keyframe
duration/checkpoints, and repeating children. For a physical spring, time scale `s` resolves
stiffness to `stiffness / s²` and scales the safety guard; it does not invent a nominal duration.
Applications supply the host's reduced-motion decision explicitly; animation-core does not read a
platform setting.

`MotionInterruptionPolicy.RetargetFromCurrent` matches `AnimatableCore` last-writer behavior.
`SnapToTarget` remains a component-owner instruction, not a second engine loop.

## Deterministic sampling and physical state

`TargetAnimation<T, V>` converts endpoints, velocity, threshold, and scratch storage once, then
evaluates immutable `AnimationState<T, V>` values at explicit nanosecond play times. It has no
clock or mutable ownership and is the preferred primitive for seeking, tests, transition channels,
renderer adapters, and preview tooling:

```kotlin
val animation = TargetAnimation(
    initialValue = 20f,
    targetValue = 100f,
    animationSpec = tween(durationMillis = 400, easing = EasingDefaults.Linear),
    converter = AnimationConverters.Float,
)
val halfway = animation.stateAt(200_000_000L)
```

`durationNanos` includes delay and saturated repeat multiplication for duration specifications, or
resolves a spring's first one-millisecond equilibrium sample. `DecayAnimation<T, V>` offers the
same explicit-time model for target-free motion and exposes its unbounded asymptotic target.
Evaluators reuse their arrays and are not thread-safe; one owner serializes sampling.

`AnimationState` carries value, typed velocity, and segment-relative play time. `AnimationResult`
carries a terminal state plus `Finished`, `BoundReached`, or `DurationLimitReached`. Coroutine
interruption is deliberately not a normal end reason.

## Separate value and velocity domains

`AnimationConverter<T, V>` keeps the animated value domain separate from its tangent/velocity
domain and writes into caller-owned buffers. Implementations declare one stable positive
`vectorSize`, `zeroVelocity`, and positive finite `visibilityThreshold`; every conversion uses that
dimension and may not retain supplied arrays.

Built-in mappings are `Float`/`Float`, `Int`/`Float`, and packed ARGB `Int`/`ArgbChannels`.
Separating domains preserves fractional integer velocity and signed alpha/red/green/blue rates.
Integer reconstruction truncates toward zero. ARGB values interpolate encoded channels and are not
gamma-correct or color-space aware.

```kotlin
data class Point(val x: Float, val y: Float)

val converter = object : AnimationConverter<Point, Point> {
    override val vectorSize = 2
    override val zeroVelocity = Point(0f, 0f)
    override val visibilityThreshold = Point(0.01f, 0.01f)

    override fun convertToVector(value: Point, destination: FloatArray) {
        destination[0] = value.x
        destination[1] = value.y
    }

    override fun convertFromVector(vector: FloatArray) = Point(vector[0], vector[1])

    override fun convertVelocityToVector(velocity: Point, destination: FloatArray) =
        convertToVector(velocity, destination)

    override fun convertVelocityFromVector(vector: FloatArray) = convertFromVector(vector)
}
```

Incomplete, non-finite, non-positive-threshold, incompatible-dimension, and invalid-zero conversion
fail before publication. Endpoint, position, velocity, threshold, and scratch vectors allocate once
per evaluator and are reused. A custom converter may allocate the immutable domain value returned
for a sample, but it must remain deterministic, non-blocking, and allocation-conscious. A derived
spring sample or decay target that cannot remain finite in the converter's vector domain also fails
before publication; it is never interpreted as equilibrium.

## Frame execution, mutation, and bounds

`runAnimation` awaits a `MonotonicFrameClock`, publishes `AnimationState` samples on the caller's
coroutine, and returns `AnimationResult`. `runDecayAnimation` follows the same contract. A
non-monotonic timestamp fails before its candidate sample publishes. Cancellation always
propagates and never forces the target. Clock, callback, and converter failures propagate unchanged.

`AnimatableCore<T, V>` is the single last-mutation-wins owner. `animateTo`, `animateDecay`,
`snapTo`, and `stop` cancel an older caller, reject stale samples, and atomically publish value and
velocity. Omitting `animateTo`'s initial velocity captures the retained value and velocity in one
mutation snapshot; duration specifications ignore that velocity. A candidate target or decay
animation is validated before ownership changes, so an invalid replacement leaves the active
mutation authoritative. Owner construction validates the initial value, vector dimension, zero
velocity, and visibility threshold before exposing state. `snapTo` and `stop` each replace an older
mutation with one atomic idle-state commit, retain zero velocity, and expose no transient running
state; an invalid snap leaves the active mutation unchanged.

`updateBounds` installs inclusive converter-domain lower and upper values. A crossing sample clamps
before publication, terminates the whole mutation with `BoundReached`, and publishes zero velocity.
Idle bound updates and later `snapTo` clamp immediately. Inverted component bounds fail without
changing the accepted state.

`viewcompose-animation` supplies composition clock binding and the facade most application code
should use. The core owner deliberately owns no scope or frame clock.

## Multi-channel transition coordination

`TransitionCore<S>` coordinates logical endpoints and one timeline across multiple channels. A
transition owner updates the target, registers each channel duration, advances the shared play time,
and commits the target when the longest channel finishes. Shorter channels settle in their own
evaluators. Retargeting preserves visual continuity in the higher-level channel owners.

`TransitionCore` is not thread-safe and does not launch or cancel work. Physical channels register
their resolved equilibrium duration, so transition state still commits only after every channel
settles.

## Testing custom animation code

- Use `TargetAnimation.stateAt` for exact boundary, delay, repeat, spring, velocity, and reverse
  assertions.
- Supply a deterministic `MonotonicFrameClock` for `runAnimation`, decay, and `AnimatableCore`.
- Verify cancellation before target completion and ensure no terminal state is forced.
- Verify custom value/velocity round trips, stable dimensions, thresholds, zero velocity, and
  numeric precision.
- Test under-, critical-, and over-damped springs, safety guards, bounds, rapid retarget, and decay.
- Register every transition channel before advancing the shared segment.

The module suite covers these physical branches, overshoot, structured results, signed ARGB
velocity, converter failures, reduced motion, transition retargeting, and duration behavior.

## Related documentation

- [ADR-0019: animation physics and ownership](../../architecture/decisions/0019-animation-physics-transition-and-inspection-ownership.md)
- [ADR-0020: separate animation value and velocity domains](../../architecture/decisions/0020-separate-animation-value-and-velocity-domains.md)
- [Animation module](../viewcompose-animation/README.md)
- [Runtime module](../viewcompose-runtime/README.md)
- [Source documentation and API comment standard](../../project/api-documentation-quality.md)

The complete generated reference is available in the
[`viewcompose-animation-core` API tree](https://docs.viewcompose.com/api/viewcompose-animation-core/current/).

## Compatibility notes

The Phase 1 alpha hard-cuts the old duration-bearing spring and single-domain converter/result
surface. Use duration specifications for exact intervals, physical `spring` for equilibrium motion,
and `AnimationConverter<T, V>` when value and velocity types differ. There are no deprecated
duration-spring, one-parameter converter, or same-domain `Animatable` adapters. Android interop
belongs to host modules and composition ownership belongs to `viewcompose-animation`.
