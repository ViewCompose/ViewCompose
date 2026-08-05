# Animation

`viewcompose-animation` integrates the platform-neutral animation engine with ViewCompose state,
composition effects, `Modifier`, UI node emission, and the Android View renderer. It provides
state-driven value animation, imperative last-writer mutations, synchronized transitions, infinite
channels, visibility/content transitions, and measured-size animation.

## Artifact and stability

```kotlin
dependencies {
    implementation("com.viewcompose:viewcompose-animation:0.1.0-alpha03")
}
```

- Stability: **Alpha**. State ownership, cancellation, retargeting, content retention, and renderer
  handoff have reviewed contracts; the APIs are intentionally smaller than Compose Animation and
  may expand between alphas.
- Platform: Android library, minimum SDK 24.
- Animation core, runtime, UI contract, and widget core are exposed transitively because their
  state, clock, modifier, unit, and builder types appear in the public animation surface.
- `viewcompose-animation-core` can also be used independently from an Android UI host.
- Android `View` property animation interop belongs to `viewcompose-host-android`, not this module.

## Composition animation environment

Composition-owned APIs use `LocalMonotonicFrameClock` for frame timestamps and
`LocalAnimationCoroutineContext` for dispatcher and context selection. The animation context must
not contain a `Job`: `LaunchedEffect` supplies the structured parent job, so installing another one
would detach cancellation from the composition.

Changing the frame clock or animation context restarts affected effects. Removing an animation call
from composition cancels it. Samples are written through ViewCompose observable state and invalidate
their readers.

## Target-as-state animation

`animateFloatAsState`, `animateIntAsState`, `animateColorAsState`, `animateDpAsState`, and the generic
`animateValueAsState` turn a changing target into stable composition-owned `State<T>`:

```kotlin
val alpha = animateFloatAsState(
    targetValue = if (enabled) 1f else 0.5f,
    animationSpec = tween(durationMillis = 180),
)
```

The first composition exposes its target immediately. Later target, specification, converter, clock,
or context changes cancel the previous effect and restart from the latest published value. These APIs
have no imperative cancellation handle or completion callback; use `Animatable` when commands,
stopping, or mutation arbitration are required.

Integer animation truncates samples toward zero. Color animation interpolates encoded ARGB channels
and is not gamma-correct. `UiDp` animation interpolates the density-independent number rather than
resolved pixels, so density changes do not restart it by themselves. A custom converter must keep a
stable dimension count and should itself remain stable across composition.

## Imperative Animatable

`Animatable<T>` exposes `value`, `targetValue`, `isRunning`, and the stable observable `asState` while
accepting suspending commands:

```kotlin
val progress = rememberAnimatable(
    initialValue = 0f,
    converter = AnimationConverters.Float,
)

LaunchedEffect(command) {
    when (command) {
        Command.Open -> progress.animateTo(1f, tween(durationMillis = 240))
        Command.Close -> progress.animateTo(0f, spring())
        Command.Stop -> progress.stop()
    }
}
```

Every `animateTo`, `snapTo`, and `stop` call is a mutation. A newer mutation from another coroutine
job cancels the old job, and stale frames are rejected by mutation identity. `animateTo` retargets
from the last accepted value. `snapTo` publishes immediately; `stop` preserves the current value.
Cancellation and failures leave the latest sample and reset the target to it.

`rememberAnimatable` uses `initialValue` only when an instance is first created. Changing the
converter creates a new instance; changing only `initialValue` does not reset it. The current frame
clock is rebound on every composition. A directly constructed instance can receive an explicit
clock; without one, only `snapTo` and `stop` are usable and `animateTo` reports a configuration error.

## Shared-state Transition

`updateTransition(targetState)` creates one logical segment and one frame timeline for multiple
derived channels:

```kotlin
val transition = updateTransition(
    targetState = if (expanded) PanelState.Expanded else PanelState.Collapsed,
    label = "panel",
)
val alpha = transition.animateFloat { state ->
    if (state == PanelState.Expanded) 1f else 0.6f
}
val height = transition.animateDp(
    animationSpec = { spring(durationMillis = 320) },
) { state ->
    if (state == PanelState.Expanded) 240.dp else 80.dp
}
```

The first composition is settled at the initial target. Each channel freezes its current sample and
new target when a later segment begins and registers its duration. The longest channel decides when
`currentState` commits `targetState`; shorter channels remain at their endpoint. Retargeting cancels
the old frame effect and starts each existing channel from its latest sampled value.

Channel factories currently receive no segment object; they provide one specification per channel
and map logical state to `Float`, `Int`, packed ARGB, or `UiDp`. The label is captured for future
diagnostics and does not alter identity.

## InfiniteTransition

`rememberInfiniteTransition` scopes continuously repeating channels declared with `animateFloat`,
`animateInt`, `animateColor`, `animateDp`, or generic `animateValue`:

```kotlin
val pulse = rememberInfiniteTransition(label = "pulse")
val scale = pulse.animateFloat(
    initialValue = 0.9f,
    targetValue = 1f,
    animationSpec = infiniteRepeatable(
        animation = tween(durationMillis = 600),
        repeatMode = RepeatMode.Reverse,
    ),
)
```

Each call position owns its state and effect. Reverse mode swaps endpoints between cycles; restart
mode republishes the initial value. Equal endpoints do not await frames. Endpoint, specification,
clock, or context changes restart from the newly supplied initial value—not the old sample. Changing
only a custom converter is not a restart key, so converter instances must remain stable.

Infinite channels run until removed from composition. Avoid using them for off-screen or invisible
content that remains composed, and prefer a finite state-driven animation when continuous motion is
not essential.

## AnimatedVisibility

`AnimatedVisibility` combines alpha and measured-size channels while controlling content lifetime:

```kotlin
AnimatedVisibility(
    visible = showDetails,
    enter = fadeIn(tween(durationMillis = 160)) + expandVertically(),
    exit = shrinkVertically() + fadeOut(tween(durationMillis = 120)),
) {
    Text("Details")
}
```

The first composition is settled and does not run enter motion. Later exit keeps content mounted
until every channel finishes, then removes the content subtree. The empty host remains mounted at
zero size as an identity anchor, so later visibility changes do not recreate following unkeyed
native sibling Views or truncate their pressed and focus state. An interrupted exit or enter
retargets from current alpha and size samples. Every new segment samples its live reset play time;
it never reuses the preceding segment's terminal time from a pinned composition snapshot. Size
motion clips content to animated bounds. When the host is a direct `Row` or `Column` child,
surrounding item spacing follows the applicable width or height progress; the lifecycle endpoints
therefore do not insert or remove a full gap in one frame.

Tree-builder defaults affect both axes. `RowScope` defaults affect width; `ColumnScope` defaults
affect height. Transition `+` concatenates elements, and the last fade or applicable size element
wins for a duplicate channel.

Use `MutableTransitionState<Boolean>` when code outside the call needs to set `targetState` and
observe `currentState` or `isIdle`. One object should be bound to one active host. In this release,
changing its target before the host's first composition does not play an initial enter; first compose
the hidden state, then change the target if that motion is required.

## AnimatedContent and Crossfade

`AnimatedContent` currently implements an alpha cross-fade. During a transition it invokes content
for the last committed state and latest target, stacks both fill-size subtrees, and removes outgoing
content in a post-composition side effect after it becomes transparent. Descendant state should be
keyed by the logical state when each content identity needs independent retention.

A new target arriving mid-fade replaces incoming content at the existing progress rather than
restarting from zero. The last committed state remains outgoing. A previously displayed nullable
state cannot be retained as outgoing content because `null` is also the internal no-outgoing
sentinel; use a non-null state wrapper when that distinction matters.

`Crossfade` is the fixed-specification convenience wrapper. Neither API currently provides content
keys, transition scopes, size transforms, slide motion, or per-state pair specifications.

## animateContentSize and native layout cost

`Modifier.animateContentSize` serializes the selected core specification into the renderer contract.
The renderer inserts a synthetic native host around the modified node, moves parent layout elements
to that host, and animates measured width and height with Android `ValueAnimator`:

```kotlin
Column(
    modifier = Modifier.animateContentSize(spring(durationMillis = 320)),
) {
    // Content whose measured size changes.
}
```

The first measurement snaps. Later changes retarget from the in-flight size, and parent constraints
continue to cap the result. Each frame requests Android layout, and the wrapper adds one View level;
avoid applying it indiscriminately to large lists. Infinite size repeats continuously request layout
until detach and are rarely appropriate.

Built-in easing and cubic Bézier control points cross the renderer boundary. Unknown custom easing
implementations fall back to `FastOutSlowIn`. When several `animateContentSize` elements occur in one
modifier chain, the last specification wins.

## Testing

- Provide deterministic frame clocks for imperative animation and cancellation tests.
- Verify first composition separately from subsequent target changes.
- Test retargeting before completion and ensure stale jobs cannot publish.
- For transitions, declare every channel in the same composition pass and assert the longest
  duration controls logical completion.
- Test visibility content retention through the terminal exit frame.
- Test size animation on the Android renderer when wrapper placement, constraints, or modifier
  routing matters; the animation module's unit tests verify only contract serialization.

## Related documentation

- [Animation core module](../viewcompose-animation-core/README.md)
- [Runtime module](../viewcompose-runtime/README.md)
- [Widget core module](../viewcompose-widget-core/README.md)
- [Renderer module](../viewcompose-renderer/README.md)
- [Architecture overview](../../architecture/overview.md)
- [Source documentation and API comment standard](../../project/api-documentation-quality.md)

The complete generated reference is available in the
[`viewcompose-animation` API tree](https://docs.viewcompose.com/api/viewcompose-animation/current/).

## Compatibility notes

The `0.1.0-alpha03` line establishes composition-owned target animation, explicit last-mutation-wins
`Animatable`, shared-duration transitions, continuous channels, exit-aware visibility lifetime,
alpha-only content replacement, and renderer-hosted measured-size motion. Similar API names do not
imply complete Jetpack Compose Animation parity; the behavioral differences above are part of this
release's public contract.
