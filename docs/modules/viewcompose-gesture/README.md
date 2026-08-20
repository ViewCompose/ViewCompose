# Gesture

`viewcompose-gesture` is the composition-facing gesture DSL for ViewCompose. It adds raw pointer,
combined click, drag, anchored drag, transform, priority, and nested-scroll elements to `Modifier`,
and provides remembered callback/state holders for renderer delivery. The module declares behavior;
the Android renderer owns the native pointer stream and recognition engine.

## Artifact and stability

```kotlin
dependencies {
    implementation("com.viewcompose:viewcompose-gesture:0.1.0-alpha04")
}
```

- Stability: **Alpha**. Modifier shapes and current state semantics are reviewed and tested; gesture
  arbitration and richer mutation APIs may evolve between alphas.
- Platform: Android library API, although the public values are renderer-neutral.
- Gesture Core, Runtime, UI Contract, and UI Foundation are exposed transitively because their policy,
  state, modifier, and builder types appear in the public gesture surface.
- Most applications should depend on this artifact rather than gesture core directly.

## Recognition ownership

Gesture modifiers are immutable descriptions. They do not install Android listeners when built and
do not recognize input themselves. The renderer interprets them when a node is mounted, owns
`MotionEvent`, pointer IDs, `VelocityTracker`, touch slop, layout-direction resolution, competing
recognizers, nested-scroll ordering, and cancellation caused by replacement or disposal.

Callbacks run synchronously on the renderer dispatch thread, normally Android's UI thread. Keep
them short and move blocking or suspend work into an application-owned coroutine. Distances and pan
values are normally physical pixels; terminal drag velocity is pixels per second.

## Pointer and click input

`pointerInput` exposes normalized down, move, up, and cancel events without the native event object.
Its key is an identity input for the handler. Return `Consumed` only when subsequent recognition
should treat the event as consumed.

`combinedClickable` lets one renderer recognizer coordinate single, double, and long clicks. Timing
and slop come from Android. Disabled calls and calls with no callbacks return the original modifier
unchanged, avoiding an inert native recognizer.

```kotlin
val actions = Modifier.combinedClickable(
    onClick = { openItem() },
    onLongClick = { openContextMenu() },
)
```

## One-dimensional drag

`rememberDraggableState` returns a stable object while always forwarding to the latest callback.
It does not accumulate or clamp an offset, so application state owns those rules. `draggable`
declares the orientation and lifecycle callbacks. Start is delivered only after recognition crosses
slop and produces local movement; normal stop carries signed axis velocity, while cancellation is a
separate callback.

Free orientation locks to the dominant axis. Cancellation can report system cancellation,
transform takeover, pointer consumption, modifier replacement, or disposal. Do not treat
cancellation as a zero-velocity normal stop.

## Anchored drag

`DraggableAnchors` is a validated, immutable, non-empty set of finite unique pixel offsets. Input
order is sorted automatically. Semantic values may repeat, but unique values are recommended
because `offsetOf` selects the first match. Exact floating-point equality is used by `valueAt`.

```kotlin
val anchors = draggableAnchors<SheetValue> {
    anchor(0f, SheetValue.Collapsed)
    anchor(480f, SheetValue.Expanded)
}
val sheet = rememberAnchoredDraggableState(SheetValue.Collapsed)
val modifier = Modifier.anchoredDraggable(
    state = sheet,
    anchors = anchors,
    orientation = GestureOrientation.Vertical,
)
```

The modifier synchronously installs its latest anchors into the state on each composition call.
When the current value still exists, an active offset survives equivalent anchor reinstallation.
If the value disappears, the nearest anchor to the current visual offset becomes current. Raw
movement updates an offset clamped to the installed range. Completion commits the renderer-selected
nearest target and may report it through `onValueSettled`; cancellation restores the last committed
anchor before its callback. This release settles immediately without an animation, so `targetValue`
normally changes with `currentValue`. The Q3 state contract publishes semantic value, target,
offset, and dragging status in one snapshot transaction for each delta, snap, settle, cancellation,
or anchor reconciliation. Mutations remain serialized on the owning UI thread.

`rememberAnchoredDraggableState` reads `initialValue` only when first remembered. Changing the
argument later does not reset state; call `snapTo` explicitly. Snapping to a value absent from the
current anchors stores the semantic value and clears the offset until the next anchor reconciliation.
Anchored dragging accepts horizontal or vertical orientation only.

## Controlled two-state drag

`rememberToggleDragState` and `toggleDraggable` adapt anchored dragging to caller-controlled
two-state components such as a design-system-owned Switch. The checked anchor is a signed physical
pixel offset from unchecked zero. Its exposed `progress` stays logical from `0f` unchecked to `1f`
checked, so callers can use a negative checked offset in RTL without reversing drawing logic.

```kotlin
val drag = rememberToggleDragState(
    checked = checked,
    checkedAnchorOffsetPx = density.toPx(if (rtl) -20.dp else 20.dp),
    onCheckedChange = onCheckedChange,
)
val target = Modifier
    .clickable { onCheckedChange(!checked) }
    .toggleDraggable(drag)
```

The renderer keeps taps available to the click modifier until movement becomes a drag. Accepted
drags consume completion, settle by position or velocity, and request a replacement state exactly
once. Components own geometry, density/layout-direction conversion, settled animation, checked
semantics, and persistence. Use `isDragging` to render follow-finger progress directly and the
design system's normal motion contract while idle. `lastCompletion` is published synchronously
before the replacement-state callback and retains the logical progress immediately before normal
settling or cancellation restored an endpoint. A component uses its per-state sequence and start
progress to continue the settled animation without briefly jumping to a stale endpoint.

## Transform gestures

`rememberTransformableState` forwards incremental multiplicative zoom, pan in physical pixels, and
clockwise rotation in degrees. It does not accumulate, constrain, or animate a transform.
`transformable` supplies start, normal-stop, and cancellation callbacks. Multi-pointer activation
can take over and cancel an active drag on the same dispatch path.

Application state should accumulate scale and translation and apply its own bounds. Avoid retaining
per-event objects or launching a new coroutine for every transform delta.

## Gesture priority and nested scroll

`gesturePriority(High)` requests an earlier recognition opportunity but never guarantees
consumption. A high-priority recognizer that rejects the event still allows other recognizers to run.

`nestedScroll` attaches a `NestedScrollConnection` to the mounted ancestor chain. Pre callbacks run
before child consumption and post callbacks after it. An optional `NestedScrollDispatcher` supports
imperative application dispatch; while detached it consumes zero, and renderer disposal detaches
the old connector without disrupting a newer mount. Connections must return only the distance or
velocity they actually consumed.

## Testing gesture UI

- Unit-test state accumulation and anchor replacement separately from native recognition.
- Test callback order across start, deltas, normal stop, and every cancellation reason.
- Exercise touch-slop thresholds, competing drag/transform recognizers, and RTL swipe direction in
  renderer or instrumented tests.
- Test nested-scroll pre order outside-in and post order inside-out, including over-consumption
  clamping at the renderer boundary.
- Recompose with changed lambdas and verify remembered state forwards to the latest callback.

The module suite covers modifier encoding, no-op click declarations, drag and transform forwarding,
anchored bounds/recomposition/cancellation/settle behavior, controlled LTR/RTL toggle progress,
pre-endpoint toggle completion snapshots, invalid free orientation, priority encoding, and
nested-scroll attachment.

## Related documentation

- [Gesture Core module](../viewcompose-gesture-core/README.md)
- [UI Contract module](../viewcompose-ui-contract/README.md)
- [Renderer module](../viewcompose-renderer-android/README.md)
- [Source documentation and API comment standard](../../project/api-documentation-quality.md)

The complete generated reference is available in the
[`viewcompose-gesture` API tree](https://docs.viewcompose.com/api/viewcompose-gesture/current/).

## Compatibility notes

The `0.1.0-alpha03` line establishes synchronous callback delivery, latest-lambda remembered state,
renderer-owned recognition, immediate anchored settling, explicit cancellation, and detachable
nested-scroll dispatch. API resemblance to Jetpack Compose gesture modifiers does not imply the same
suspend mutation, `MutatorMutex`, or animation behavior.
