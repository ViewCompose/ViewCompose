---
schema_version: 2
document_id: module.viewcompose-gesture-core
doc_type: module
owner:
  kind: module
  id: viewcompose-gesture-core
version_lane: released
capability_ids:
  - gesture.modifiers
artifact_ids:
  - viewcompose-gesture-core
sample_ids:
  - module.gesture-core-dependency
  - module.gesture-core-axis-lock
  - module.gesture-core-anchored-settle
coordinate: com.viewcompose:viewcompose-gesture-core:0.1.0-alpha04
minimal_usage_sample_id: module.gesture-core-dependency
---

# Gesture Core

`viewcompose-gesture-core` is the platform-neutral policy layer for ViewCompose gesture
recognition. It converts renderer-supplied pointer distance, velocity, touch slop, and anchors into
axis locks, transform activation, swipe directions, and anchored settle targets. It owns no pointer
stream, Android `MotionEvent`, coroutine, mutable gesture state, or View.

## Artifact and stability

{/* compiled-region source="samples/tutorials/src/main/java/com/viewcompose/samples/tutorials/TutorialDependencySnippets.kt" region="gesture-core-module-dependency" sample_id="module.gesture-core-dependency" build_target=":samples:tutorials:compileDebugKotlin" */}
```kotlin
dependencies {
    implementation("com.viewcompose:viewcompose-gesture-core:0.1.0-alpha04")
}
```

- Stability: **Alpha**. The current threshold ordering and anchor-selection behavior are reviewed
  and tested; policy names and higher-level gesture integration may still evolve between alphas.
- Platform: Kotlin/JVM with no Android framework dependency.
- UI contract is exposed transitively because shared orientation and swipe values appear in public
  policy signatures.
- Applications normally receive it transitively from `viewcompose-gesture`; depend on it directly
  for custom renderers, deterministic policy tests, or non-Android pointer integrations.

## Responsibility boundary

Gesture core is deliberately a collection of synchronous, deterministic functions. A renderer must
still collect pointer IDs, accumulate movement, normalize transform motion, obtain platform touch
slop and fling velocity, resolve layout direction, arbitrate competing recognizers, and deliver
cancellation. Keeping those responsibilities outside this module makes identical policy decisions
available to Android rendering, previews, and unit tests.

Most distances are physical pixels because Android supplies slop and velocity in pixels. The core
does not convert dp, reject every non-finite input, or infer whether zoom and rotation are comparable
to pan. Normalize inputs at the renderer boundary.

## Axis locking and transform activation

`resolveLockAxis` waits until accumulated movement reaches touch slop. A fixed orientation also
requires its axis to dominate the perpendicular axis. Free orientation selects the larger movement,
with horizontal winning an exact tie.

{/* compiled-region source="viewcompose-gesture-core/src/test/samples/com/viewcompose/gesture/core/samples/GestureCoreSamples.kt" region="gesture-core-axis-lock" sample_id="module.gesture-core-axis-lock" build_target=":viewcompose-gesture-core:compileTestKotlin" */}
```kotlin
val axis = resolveLockAxis(
    dx = 18f,
    dy = 6f,
    orientation = GestureOrientation.Free,
    touchSlop = 8f,
)
```

`shouldActivateTransform` is a smaller threshold primitive. It activates when any normalized pan,
zoom, or rotation motion is strictly greater than slop; equality remains inactive. The caller must
convert these different physical quantities into comparable motion magnitudes.

## Swipe completion

`resolveSwipeDecision` gives terminal velocity priority over drag distance. When velocity is below
the fling threshold, distance must reach the greater of twice touch slop and 35 percent of the
two-anchor span. Positive horizontal motion produces logical start-to-end rather than a physical
rightward contract, leaving RTL resolution to the renderer.

If neither threshold wins and both anchors exist, the projected position settles to the nearer
endpoint; a tie chooses the minimum. An incomplete anchor pair produces `SwipeDecision.None`.
This policy is intended for simple two-endpoint swipe interactions. Multi-anchor drags should use
the anchored policy.

## Anchored drag policy

Anchor lists must be non-empty, finite, and strictly increasing. Call `requireValidAnchorsPx` at any
custom boundary; all public anchored resolution functions validate internally.

`resolveAnchoredSettleTarget` selects the anchor nearest the gesture's start as its segment origin,
then applies these rules in order:

1. velocity at or above the effective fling threshold moves one anchor in its sign direction;
2. distance crossing the configured threshold moves one anchor in the drag direction;
3. otherwise the anchor nearest the final visual position wins.

The distance threshold is the larger of touch slop multiplied by `slopMultiplier` and the adjacent
segment multiplied by `segmentFraction`. `AnchoredThresholdPolicy` may replace the platform fling
threshold, which is useful for renderer-specific behavior and deterministic tests. Movement is
clamped at the end anchors; one settle never skips multiple anchors.

{/* compiled-region source="viewcompose-gesture-core/src/test/samples/com/viewcompose/gesture/core/samples/GestureCoreSamples.kt" region="gesture-core-anchored-settle" sample_id="module.gesture-core-anchored-settle" build_target=":viewcompose-gesture-core:compileTestKotlin" */}
```kotlin
val result = resolveAnchoredSettleTarget(
    anchorsPx = listOf(0f, 160f, 320f),
    startOffsetPx = 160f,
    currentOffsetPx = 190f,
    velocityPxPerSecond = 1_200f,
    touchSlopPx = 8f,
    minFlingVelocityPxPerSecond = 600f,
)
```

When anchors change, `resolveAnchoredOffsetOnAnchorUpdate` first preserves an exact offset associated
with the current semantic value. It then falls back to the anchor nearest the current visual offset,
and finally to the first anchor. This prevents a resized or recomputed anchor set from silently
changing semantic state when the old value is still representable.

## Testing custom gesture integrations

- Test values immediately below, equal to, and above every slop or velocity threshold.
- Test dominant-axis ties and logical horizontal direction under both layout directions.
- Test anchor validation, endpoint clamping, equal-distance ties, and anchor-set replacement.
- Keep pointer cancellation and recognizer competition tests in the renderer or gesture DSL module;
  gesture core has no stream ownership.
- Supply physical-pixel values in integration tests so policy thresholds match the platform values
  used at runtime.

The module suite covers horizontal, vertical, and free locking; transform activation; velocity and
distance swipe priority; nearest endpoint settle; anchor validation; anchored fling, distance, and
nearest selection; and anchor-update preservation.

## Related documentation

- [UI contract module](../viewcompose-ui-contract/README.md)
- [Architecture overview](../../architecture/overview.md)
- [Source documentation and API comment standard](../../project/api-documentation-quality.md)
- [Project roadmap](../../project/roadmap.md)

The complete generated reference is available in the
[`viewcompose-gesture-core` API tree](https://docs.viewcompose.com/api/viewcompose-gesture-core/current/).

## Compatibility notes

The `0.1.0-alpha04` line establishes velocity-before-distance arbitration, logical horizontal swipe
directions, adjacent-anchor movement, strict anchor ordering, and semantic offset preservation.
Pointer dispatch, mutable state, composition ownership, and Android event integration belong to
`viewcompose-gesture` and the renderer.
