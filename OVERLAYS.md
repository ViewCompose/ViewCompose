# Overlay positioning and transient feedback

`Dialog`, `Popup`, and `ModalBottomSheet` use session-bound overlay surfaces. `Snackbar` and
`Toast` use one host-owned transient-feedback lane so their ordering is deterministic even when
both types are declared in the same render.

## Popup positioning

`Popup` resolves the anchor and popup in window coordinates. The Android presenter observes global
layout and scroll changes, so an open popup follows a moving anchor instead of retaining the
coordinates from its first frame.

```kotlin
Popup(
    visible = menuVisible,
    anchorId = "profile-menu-anchor",
    alignment = PopupAlignment.BelowEnd,
    overflowPolicy = PopupOverflowPolicy.FlipThenClamp,
    windowMargin = 8.dp,
    offsetY = 4.dp,
    onDismissRequest = { menuVisible = false },
) {
    ProfileMenu()
}
```

Available alignments cover below/above, logical start/end sides, and anchor center. Logical
start/end resolve from the anchor's layout direction.

Overflow policies:

- `FlipThenClamp` tries the opposite side when it has less overflow, then clamps the result into
  the visible window. This is the default.
- `Clamp` keeps the requested side and only clamps the final coordinates.
- `None` preserves the exact requested coordinates and disables platform clipping.

`PopupPositioner` is the platform-neutral positioning contract. Custom hosts can use the same
calculation with their own anchor bounds, visible viewport, and popup measurement.

## Snackbar and Toast queue

Snackbar and Toast declarations share a single FIFO lane. A request is identified by
`(render session, requestKey)` and is delivered once while that declaration remains visible.
Recomposition with an equal declaration does not enqueue it again. Changing the content for the
same key replaces that version.

```kotlin
Snackbar(
    visible = saveMessageVisible,
    requestKey = "save-result",
    message = "Saved",
    queuePolicy = TransientFeedbackQueuePolicy.Enqueue,
    onDismiss = { reason ->
        saveMessageVisible = false
        log("save-result ended: $reason")
    },
)
```

Queue policies:

- `Enqueue`: append behind the active request.
- `ReplaceCurrent`: dismiss the active request and place this request at the front.
- `ReplaceSameKey`: update the active or queued declaration with the same request key; otherwise
  enqueue normally.
- `DropIfBusy`: consume the request without presenting it when the lane is already busy.

Dismiss reasons are structured as `Timeout`, `Action`, `Gesture`, `Replaced`, `Removed`,
`SessionCleared`, `Dropped`, or `Platform`. Removing a declaration and clearing a render session
both dismiss an active platform object and allow the next valid queued request to advance.

For Toast, Android does not expose a reliable hide callback on every supported API level. The
Android presenter therefore advances the lane using the platform short/long display intervals,
and cancels that timer when the request is explicitly removed or replaced.
