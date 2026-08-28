---
schema_version: 2
document_id: module.viewcompose-animation
doc_type: module
owner:
  kind: module
  id: viewcompose-animation
version_lane: released
capability_ids:
  - animation.composition-motion
artifact_ids:
  - viewcompose-animation
sample_ids:
  - module.animation-dependency
  - module.animation-target-as-state
  - module.animation-animatable
  - module.animation-transition
  - module.animation-seekable-transition
  - module.animation-infinite-transition
  - module.animation-visibility
  - module.animation-content
  - module.animation-content-size
  - module.animation-bounds
coordinate: com.viewcompose:viewcompose-animation:0.1.0-alpha05
minimal_usage_sample_id: module.animation-dependency
---

# Animation

`viewcompose-animation` integrates the platform-neutral animation engine with ViewCompose state,
composition effects, `Modifier`, UI node emission, and the Android View renderer. It provides
state-driven value animation, imperative last-writer mutations, synchronized transitions, infinite
channels, visibility/content transitions, measured-size animation, and real layout-bounds motion.

## Artifact and stability

{/* compiled-region source="samples/tutorials/src/main/java/com/viewcompose/samples/tutorials/TutorialDependencySnippets.kt" region="animation-module-dependency" sample_id="module.animation-dependency" build_target=":samples:tutorials:compileDebugKotlin" */}
```kotlin
dependencies {
    implementation("com.viewcompose:viewcompose-animation:0.1.0-alpha05")
}
```

- Stability: **Alpha**. State ownership, cancellation, retargeting, content retention, and renderer
  handoff have reviewed contracts; the APIs are intentionally smaller than Compose Animation and
  may expand between alphas.
- Platform: Android library, minimum SDK 24.
- Animation Core, Runtime, UI Contract, and UI Foundation are exposed transitively because their
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

Design-system components may resolve a semantic `MotionScheme` before calling these APIs. The
scheme remains immutable policy from animation-core; `Animatable`, target-as-state APIs, and
`Transition` remain the only composition-owned runners. Rapid retargeting therefore keeps the
existing last-writer cancellation and stale-frame rejection semantics instead of creating a
component-private animation loop.

## Shape transitions and fallback

`interpolateUiShape(start, end, fraction)` interpolates corresponding corners only when each pair
uses the same family and both size representations are absolute or both are relative. The result
reports `UiShapeInterpolationMode.Compatible` for that path. Family or size-kind mismatches select
the start shape before the midpoint and the destination at and after it, reporting
`DiscreteFallback` for diagnostics.

The helper owns no clock, View, or state. Drive its finite progress through `Animatable`,
`animateFloatAsState`, or `Transition`. It deliberately does not offer arbitrary Path Morph; a
component that cannot prove compatible geometry retains a deterministic static/discrete fallback
without changing bounds, input ownership, or semantics.

## Target-as-state animation

`animateFloatAsState`, `animateIntAsState`, `animateColorAsState`, `animateDpAsState`, and the generic
`animateValueAsState` turn a changing target into stable composition-owned `State<T>`:

{/* compiled-region source="viewcompose-animation/src/test/samples/com/viewcompose/animation/samples/AnimationSamples.kt" region="animation-target-as-state" sample_id="module.animation-target-as-state" build_target=":viewcompose-animation:compileDebugUnitTestKotlin" */}
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

All target-as-state APIs accept `FiniteAnimationSpec`; infinite specifications fail at compile time.
They share `AnimatableCore` mutation and physical sampling rather than owning a second runner.
Integer animation truncates samples toward zero. Color animation interpolates encoded ARGB channels
and is not gamma-correct. `UiDp` animation interpolates the density-independent number rather than
resolved pixels, so density changes do not restart it by themselves. A custom converter must keep a
stable dimension count and should itself remain stable across composition.

## Imperative Animatable

`Animatable<T, V>` exposes `value`, typed `velocity`, `targetValue`, `isRunning`, and the stable
observable `asState` while accepting suspending commands:

{/* compiled-region source="viewcompose-animation/src/test/samples/com/viewcompose/animation/samples/AnimationSamples.kt" region="animation-animatable" sample_id="module.animation-animatable" build_target=":viewcompose-animation:compileDebugUnitTestKotlin" */}
```kotlin
val progress = rememberAnimatable(
    initialValue = 0f,
    converter = AnimationConverters.Float,
)

LaunchedEffect(command) {
    when (command) {
        Command.Open -> progress.animateTo(
            targetValue = 1f,
            animationSpec = spring(dampingRatio = 0.7f, stiffness = 220f),
        )
        Command.Close -> progress.animateDecay(AnimationVelocity(-2.4f))
        Command.Stop -> progress.stop()
        else -> Unit
    }
}
```

Every `animateTo`, `animateDecay`, `snapTo`, and `stop` call is a mutation. A newer mutation from
another coroutine job cancels the old job, and stale frames are rejected by mutation identity.
Physical `animateTo` retargets from one atomic value/velocity snapshot when its nullable
`initialVelocity` is omitted; an explicit `AnimationVelocity<V>` replaces only that captured
velocity. An invalid replacement is rejected before mutation ownership changes and therefore does
not cancel the active animation. `snapTo` publishes immediately; `stop` preserves the current value.
Both reset velocity to zero. Cancellation and failures leave the latest sample and reset the target
to it. Normal completion returns `AnimationResult<T, V>` with `Finished`,
`BoundReached`, or `DurationLimitReached`; cancellation still throws. The Q3 `Animatable`
contract publishes target/running mutation start together for frame-driven animation, and publishes
the retained target/idle completion together; frame samples remain independent value commits.
`snapTo` and `stop` instead publish one atomic final idle snapshot without a transient running
state. Invalid construction or snap input fails before any state or mutation ownership changes.

`updateBounds(lowerBound, upperBound)` installs inclusive component-wise value bounds. A running
spring or decay clamps its crossing sample before publication, zeros velocity, and returns
`BoundReached`. An idle update or later `snapTo` clamps immediately. Density, RTL, and gesture-axis
conversion remain responsibilities of the caller that constructs `V`.

`rememberAnimatable` uses `initialValue` only when an instance is first created. Changing the
converter creates a new instance; changing only `initialValue` does not reset it. The current frame
clock is rebound on every composition. A directly constructed instance can receive an explicit
clock; without one, only `snapTo` and `stop` are usable and `animateTo` reports a configuration error.

## Shared-state Transition

`updateTransition(targetState)` creates one logical segment and one autonomous frame timeline for
multiple derived channels. Every channel receives the stable `TransitionSegment<S>` selected for
that segment, so direction-specific timing is type-safe and evaluated once:

{/* compiled-region source="viewcompose-animation/src/test/samples/com/viewcompose/animation/samples/AnimationSamples.kt" region="animation-transition" sample_id="module.animation-transition" build_target=":viewcompose-animation:compileDebugUnitTestKotlin" */}
```kotlin
val transition = updateTransition(
    targetState = if (expanded) PanelState.Expanded else PanelState.Collapsed,
    label = "panel",
)
val alpha = transition.animateFloat { state ->
    if (state == PanelState.Expanded) 1f else 0.6f
}
val height = transition.animateDp(
    transitionSpec = {
        if (isTransitioningTo(PanelState.Collapsed, PanelState.Expanded)) {
            spring(dampingRatio = 0.8f, stiffness = 240f)
        } else {
            tween(durationMillis = 180)
        }
    },
) { state ->
    if (state == PanelState.Expanded) 240.dp else 80.dp
}
```

The first composition is settled at the initial target. Each channel freezes its current sample and
new target when a later segment begins. The longest duration in the complete committed channel set
decides when `currentState` commits `targetState`; shorter channels clamp at their own endpoints.
Adding or removing a call position recomputes that maximum, including duration shrink. Retargeting
cancels the old autonomous effect and starts each existing channel from its latest sampled value
and retained physical velocity. The Q3 `Transition` publishes its logical state, target, running
flag, stable `segment`, and play time through atomic snapshots. `MutableTransitionState` mirrors its
framework-owned current/target/idle tuple through the same boundary.

`animateValue(converter, transitionSpec, targetValueByState)` is the generic Q3 channel. Built-in
`animateFloat`, `animateInt`, `animateColor`, and `animateDp` delegate to the same path. The typed
channel named argument is now `transitionSpec`; the former `animationSpec` name has no compatibility
overload. Infinite specifications remain excluded at compile time.

For gesture, scrubber, Preview, or predictive-progress ownership, bind one
`SeekableTransitionState<S>` instead of calling `updateTransition`:

{/* compiled-region source="viewcompose-animation/src/test/samples/com/viewcompose/animation/samples/AnimationSamples.kt" region="animation-seekable-transition" sample_id="module.animation-seekable-transition" build_target=":viewcompose-animation:compileDebugUnitTestKotlin" */}
```kotlin
val seekState = remember { SeekableTransitionState(PanelState.Collapsed) }
val transition = rememberTransition(seekState, label = "seekable panel")
val position = transition.animateValue(
    converter = pointConverter,
    transitionSpec = { tween(durationMillis = 600) },
) { state ->
    if (state == PanelState.Expanded) Point(96f, 32f) else Point(0f, 0f)
}

LaunchedEffect(command) {
    when (command) {
        Command.Preview -> seekState.seekTo(0.7f, PanelState.Expanded)
        Command.Commit -> seekState.animateTo(PanelState.Expanded)
        Command.Reset -> seekState.snapTo(PanelState.Collapsed)
        else -> Unit
    }
}
```

The state accepts exactly one active `rememberTransition` binding and one mutation writer.
`seekTo` validates a finite `0f..1f` fraction before taking ownership, cancels and joins an older
command, maps the fraction to the longest committed channel duration, and samples every channel
with zero physical velocity. A changed seek target freezes current channel samples as new starts.
Channel additions and removals retain the normalized fraction and resample against the new maximum.
Commands allow two frame opportunities for channels in the accepted segment to commit; a
zero-channel segment then uses the coordinator's one-nanosecond fallback rather than waiting
without bound.

`animateTo` leaves seeking and runs exactly one frame loop from the sampled values; because seeking
does not infer physical velocity, that handoff starts with zero velocity. A newer seek, animation,
or snap cancels and joins the old caller before publishing. `snapTo` uses no frame and atomically
collapses current state, target state, and both segment endpoints onto one idle value. Removing the
binding cancels its active writer and retains an unfinished visual sample as seeking state.

The seek state owns no coroutine scope, is not automatically saveable, and does not commit or roll
back navigation. A navigation owner may pass predictive-Back progress to `seekTo`, but it remains
responsible for the back-stack transaction and for choosing `animateTo` or `snapTo` after commit or
cancel. The label remains diagnostic metadata and does not alter identity.

## Optional animation timeline inspection port

`viewcompose-animation` exposes the Q3 `com.viewcompose.animation.tooling` contracts used by
optional downstream development tooling. `AnimationTimelineSource` supplies one immutable,
bounded snapshot of a committed `Transition`; `AnimationTimelineTooling` and its lifecycle
registration decide whether one explicit request currently selects that transition. The runtime
reads at most one provider from a frozen process-local in-memory slot. A downstream tooling
artifact may call the Q3 `installAnimationTimelineTooling` integration hook during Android
component initialization, before the first transition reads the port. Reinstalling the same
instance is idempotent; distinct early providers disable the port, and late installation is
ignored. The path performs no classpath scan, file I/O, or Android service lookup. Absence,
ambiguity, provider failure, and disposal failure remain diagnostic no-ops.

Snapshots contain a process-lifetime transition identity, bounded label, safe logical-state
summaries, segment version/time, running/idle/interrupted state, and at most 32 committed channels.
Built-in Float, exactly representable Int, `UiDp`, and encoded ARGB channels expose bounded numeric
components. Custom value domains deliberately expose `null` values rather than retaining
application objects or calling application formatting. Each channel reports its deterministic
runtime name, finite specification family, own duration, velocity where safe, completion, and
physical `DurationLimitReached` terminal condition.

The animation artifact contains no concrete provider, Android receiver, file format, Studio API,
thread, poll, or frame callback. With no optional provider it creates no source projection and pays
only the immutable diagnostic identity metadata plus the nullable registration check already on an
accepted transition publication. Optional-artifact presence may weakly retain the neutral source
before the first request so an already composed transition remains discoverable. The concrete
receiver rejects non-debuggable processes and performs no snapshot, serialization, or report I/O
until a nonce-bearing bounded request selects that identity. The port is read-only: live
application seeking is not part of the contract.

The initialization boundary is defined by
[ADR-0022](../../architecture/decisions/0022-in-memory-development-tooling-installation.md).

## InfiniteTransition

`rememberInfiniteTransition` scopes continuously repeating channels declared with `animateFloat`,
`animateInt`, `animateColor`, `animateDp`, or generic `animateValue`:

{/* compiled-region source="viewcompose-animation/src/test/samples/com/viewcompose/animation/samples/AnimationSamples.kt" region="animation-infinite-transition" sample_id="module.animation-infinite-transition" build_target=":viewcompose-animation:compileDebugUnitTestKotlin" */}
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

`AnimatedVisibility` coordinates alpha, measured reveal, measured-size-relative slide, pivoted
visual scale, and descendant choreography while controlling one content lifetime:

{/* compiled-region source="viewcompose-animation/src/test/samples/com/viewcompose/animation/samples/AnimationSamples.kt" region="animation-visibility" sample_id="module.animation-visibility" build_target=":viewcompose-animation:compileDebugUnitTestKotlin" */}
```kotlin
AnimatedVisibility(
    visible = showDetails,
    enter = fadeIn(tween(durationMillis = 160)) +
        slideInHorizontally(
            from = SlideDirection.Start,
            distanceFraction = 0.5f,
        ) +
        scaleIn(
            initialScale = 0.9f,
            transformOrigin = TransformOrigin(0f, 1f),
        ) +
        expandVertically(alignment = BoxAlignment.BottomStart),
    exit = shrinkVertically(alignment = BoxAlignment.TopEnd) +
        scaleOut(
            targetScale = 0.92f,
            transformOrigin = TransformOrigin(1f, 0f),
        ) +
        slideOutHorizontally(towards = SlideDirection.End) +
        fadeOut(tween(durationMillis = 120)),
) {
    Text("Parent transition running: ${transition.isRunning}")
    AnimatedEnterExit(
        enter = slideInVertically(from = SlideDirection.Down),
        exit = slideOutVertically(towards = SlideDirection.Up),
    ) {
        Text("Descendant shares the parent clock")
    }
}
```

The first composition is settled and does not run enter motion. Later exit keeps content mounted
for drawing until every parent and descendant channel finishes, then removes the content subtree.
Accepting an exit target immediately removes that retained subtree from pointer, focus, and
accessibility ownership. The empty host remains mounted at zero size as an identity anchor, so later
visibility changes do not recreate following unkeyed native sibling Views or truncate their pressed
and focus state. An interrupted exit or enter retargets every channel from its current sample.
Every new segment samples its live reset play time; it never reuses the preceding segment's terminal
time from a pinned composition snapshot.

`slideIn`/`slideOut` accept non-negative finite fractions of the full measured width or height.
Logical start/end resolve from the layout direction captured at segment start; up/down remain
physical. Expand and shrink keep their declared `BoxAlignment` edge stable and clip drawing to the
animated bounds. `scaleIn`/`scaleOut` use their explicit `TransformOrigin`. Translation and visual
scale do not change parent measurement. When the host is a direct `Row` or `Column` child,
surrounding item spacing follows the applicable width or height reveal progress, so lifecycle
endpoints do not insert or remove a full gap in one frame.

Tree-builder defaults affect both axes. `RowScope` defaults affect width; `ColumnScope` defaults
affect height. Transition `+` concatenates elements, and the last applicable alpha, size, slide, or
scale element wins for a duplicate channel. `AnimatedVisibilityScope.transition` is the owning
Boolean `Transition`; scoped `AnimatedEnterExit` adds descendant channels to that coordinator
instead of starting another frame loop. Descendant-local alpha/translation/scale/reveal applies
before the parent transform, with parent clipping last. A motion policy that resolves these finite
specifications to `snap` still commits visibility endpoints and removes exit content correctly.

The content receiver is now the Q3 `AnimatedVisibilityScope`, not `BoxScope`. This is an intentional
hard cut that makes shared transition ownership type-safe. Ordinary builder calls remain direct;
callers that relied on `BoxScope.align` must emit an explicit `Box` and apply child alignment there.
The slide/scale helpers, transition elements, scope, descendant host, renderer transport, and
compiled `richVisibilityTransitionsSample` form one Q3 API family.

Use `MutableTransitionState<Boolean>` when code outside the call needs to set `targetState` and
observe `currentState` or `isIdle`. One object should be bound to one active host. In this release,
changing its target before the host's first composition does not play an initial enter; first compose
the hidden state, then change the target if that motion is required.

## AnimatedContent and Crossfade

`AnimatedContent` is the keyed full-content replacement API. Its typed transition scope selects one
`ContentTransform` for the accepted initial/target pair and can combine fade, measured-item slide,
scale origin, drawing order, and an optional `SizeTransform`:

{/* compiled-region source="viewcompose-animation/src/test/samples/com/viewcompose/animation/samples/AnimationSamples.kt" region="animation-content" sample_id="module.animation-content" build_target=":viewcompose-animation:compileDebugUnitTestKotlin" */}
```kotlin
AnimatedContent(
    targetState = page,
    contentKey = { it.id },
    transitionSpec = {
        val forward = targetState.index > initialState.index
        val enter = fadeIn() + slideIntoContainer(
            from = if (forward) ContentSlideDirection.End else ContentSlideDirection.Start,
            distanceFraction = 0.35f,
        ) + scaleIn(initialScale = 0.96f)
        val exit = fadeOut() + slideOutOfContainer(
            towards = if (forward) ContentSlideDirection.Start else ContentSlideDirection.End,
            distanceFraction = 0.2f,
        )
        (enter togetherWith exit) using SizeTransform(clip = true)
    },
) { state ->
    Page(state)
}
```

`contentKey` is subtree identity. Equal keys patch one retained tree without selecting a replacement
transition. Unequal keys retain at most one outgoing and one incoming full tree; an A-to-B-to-C
interruption promotes B from its last committed visual sample, releases A once, and preserves B's
keyed descendant state. Nullable keys and states follow the same rule.

Both trees are measured under the same incoming parent constraints. A non-null `SizeTransform`
interpolates from the last committed host size to the incoming size and controls clipping; `null`
uses the maximum current child size. Slide distances are non-negative finite fractions of the
participating item's measured axis, with start/end resolved from the layout direction captured for
that segment. Callback-calculated offsets remain unsupported; general visibility slide/scale and
descendant choreography are provided by the separate `AnimatedVisibility` family above.

Incoming content exclusively owns pointer input, focus traversal, and accessibility after the
replacement transaction commits. Outgoing content remains draw-only until every channel settles.
A changed request is admitted only after one successful candidate commit, so a renderer failure
cannot publish candidate identity, focus ownership, descendant effects, or geometry. Host disposal
cancels the shared frame loop and releases every retained tree once.

`Crossfade` remains the smaller alpha-only contract. During a transition it invokes content for the
last committed state and latest target, stacks two fill-size subtrees, and removes outgoing content
after it becomes transparent. A new target replaces incoming content at the existing progress.
Choose it when content keys, measured size, pair-specific transforms, slide, scale, and explicit
interaction transfer are unnecessary.

## animateContentSize and native layout cost

`Modifier.animateContentSize` serializes a finite core specification into the renderer contract.
The renderer inserts a synthetic native host around the modified node and moves parent layout
elements to that host. Duration specifications use Android `ValueAnimator`; physical springs use
the shared animation-core solver and retain width/height velocity across retargeting:

{/* compiled-region source="viewcompose-animation/src/test/samples/com/viewcompose/animation/samples/AnimationSamples.kt" region="animation-content-size" sample_id="module.animation-content-size" build_target=":viewcompose-animation:compileDebugUnitTestKotlin" */}
```kotlin
Column(
    modifier = Modifier.animateContentSize(
        spring(dampingRatio = 0.75f, stiffness = 240f),
    ),
) {
    // Content whose measured size changes.
}
```

The first measurement snaps. Later changes retarget from the in-flight size, and parent constraints
continue to cap the result. Each frame requests Android layout, and the wrapper adds one View level;
avoid applying it indiscriminately to large lists. Infinite size specifications are rejected at
compile time because a layout animation must converge.

Built-in easing and cubic Bézier control points cross the renderer boundary. Unknown custom easing
implementations fall back to `FastOutSlowIn`. When several `animateContentSize` elements occur in one
modifier chain, the last specification wins.

## animateBounds and real layout geometry

`Modifier.animateBounds` animates a node's position and size in its immediate ViewCompose layout
parent after logical start/end and RTL resolution. It commits a real Android rectangle on every
frame rather than applying draw translation or scale, so visible, pointer, focus, and accessibility
geometry remain aligned:

{/* compiled-region source="viewcompose-animation/src/test/samples/com/viewcompose/animation/samples/AnimationSamples.kt" region="animation-bounds" sample_id="module.animation-bounds" build_target=":viewcompose-animation:compileDebugUnitTestKotlin" */}
```kotlin
Button(
    text = "Move and resize",
    onClick = onTargetClick,
    modifier = Modifier
        .width(if (expanded) 204.dp else 152.dp)
        .height(if (expanded) 58.dp else 48.dp)
        .align(if (expanded) BoxAlignment.BottomEnd else BoxAlignment.BottomStart)
        .animateBounds(tween(durationMillis = 900)),
)
```

The first accepted layout is settled. A target change performs one target measurement; duration
specifications retarget from the current rectangle with zero velocity, while physical springs
retain all four sampled edge velocities. Property frames reuse the target measurement. Parent
scrolling moves the complete local coordinate system; reparenting ends the old owner's motion and
the destination starts settled. Detach and lazy-item cross-owner reuse also cancel and clear old
motion before the next layout.

The renderer promotes same-chain size, margin, parent data, alignment, offset, visibility, and
z-index to one transparent outer host. Drawing, content, input, focus, and semantics remain on the
child. Repeated `animateBounds` elements are last-wins. Combining `animateBounds` and
`animateContentSize` on one node is rejected before native mutation because both would own size.
The host clips content to its sampled rectangle and adds one native View level. Use `snap()` or a
resolved snap motion policy when layout motion must be disabled.

## Testing

- Provide deterministic frame clocks for imperative animation and cancellation tests.
- Assert physical end reason, terminal velocity, bounds, decay direction, and rapid-retarget
  velocity continuity separately from duration behavior.
- Verify first composition separately from subsequent target changes.
- Test retargeting before completion and ensure stale jobs cannot publish.
- For transitions, declare every channel in the same composition pass and assert the longest
  duration controls logical completion.
- Test visibility content retention through the terminal exit frame.
- Test animated-content equal and unequal keys, nullable targets, RTL slide resolution, midpoint
  interruption, removed effects, input/focus/accessibility transfer, rollback, and host disposal.
- Test compatible and incompatible shape transitions separately, including fallback attribution.
- Test size animation on the Android renderer when wrapper placement, constraints, or modifier
  routing matters; the animation module's unit tests verify only contract serialization.
- Test bounds animation with real parent placement, RTL, active retargeting, detach/reuse, input and
  accessibility geometry, rollback, and target-measure counts; visual translation alone is not an
  acceptable substitute.

## Related documentation

- [Animation core module](../viewcompose-animation-core/README.md)
- [Runtime module](../viewcompose-runtime/README.md)
- [UI Foundation module](../viewcompose-ui-foundation/README.md)
- [Renderer module](../viewcompose-renderer-android/README.md)
- [Architecture overview](../../architecture/overview.md)
- [Source documentation and API comment standard](../../project/api-documentation-quality.md)

The complete generated reference is available in the
[`viewcompose-animation` API tree](https://docs.viewcompose.com/api/viewcompose-animation/current/).

## Compatibility notes

The Phase 1 alpha hard-cuts the fixed-duration spring and single-domain `Animatable<T>` surface.
Callers use physical `spring`, `Animatable<T, V>`, typed velocity, decay, bounds, and structured
results. `animateContentSize` shares that physical solver and no longer accepts infinite
specifications. Additive `animateBounds` is immediate-parent-local and does not yet provide shared
or cross-owner visual transitions. There are no deprecated compatibility overloads.
Shared-duration transitions, continuous channels, exit-aware visibility lifetime, and alpha-only
content replacement retain their documented ownership. Similar API names do not imply complete
Jetpack Compose Animation parity; the behavioral differences above remain part of the public
contract.
