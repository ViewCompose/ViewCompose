# Overlay positioning and transient feedback

`Dialog`, `Popup`, and `ModalBottomSheet` use session-bound overlay surfaces. `Snackbar` and
`Toast` use one host-owned transient-feedback lane so their ordering is deterministic even when
both types are declared in the same render.

## Backend selection

`viewcompose-overlay-android` is the neutral Android transport. It owns Dialog, PopupWindow, Toast,
nested overlay render sessions, anchor observation, and cleanup without a Material dependency.
Neutral `setUiContent` and the Android navigation host select it explicitly.

`viewcompose-overlay-material3-android` adds only Material Snackbar and modal-bottom-sheet
presenters. `setMaterial3UiContent` selects that adapter explicitly; placing the artifact on the
runtime classpath does not alter a neutral or One UI root.

`viewcompose-overlay-oneui7-android` adds Material-free One UI Snackbar and bottom-dialog
presenters. One UI roots keep neutral `setUiContent`, construct that adapter through
`overlayHostFactory`, and pass its `integrationAttribution` to `OneUi7Theme`. There is no duplicate
One UI Activity/Fragment host extension because One UI does not require a different root Context.

Custom low-level hosts may use
`AndroidOverlayHostDefaults.androidOrNoOp`, but service discovery accepts exactly one neutral
provider and never selects a design system.

The active design-system attribution reports each overlay transport, presenter, conformance, and
fallback. A One UI theme without its adapter reports Snackbar and modal bottom sheet as
`Unsupported`; explicit adapter assembly upgrades them to `Equivalent`. It never silently falls
back to Material.

## Modal bottom-sheet appearance updates

`ModalBottomSheet` resolves theme values and sparse `ModalBottomSheetOverrides` into one immutable
`ModalBottomSheetAppearance` before submitting its request. The snapshot contains container and
content colors, shape, scrim opacity, and navigation-bar policy. It participates in overlay-spec
equality, so changing the active theme or an override updates an existing same-key platform sheet
without discarding its logical request identity or nested saveable-state scope.

```kotlin
ModalBottomSheet(
    visible = sheetVisible,
    requestKey = "account-actions",
    overrides = ModalBottomSheetOverrides(
        containerColor = Theme.colors.surfaceContainerHigh,
        navigationBarColor = ModalBottomSheetNavigationBarColor.PlatformDefault,
    ),
    onDismissRequest = { sheetVisible = false },
) {
    AccountActions()
}
```

A nullable color alone cannot express all required states. `null` in the sparse override means
inherit; `Exact(color)` requests one ARGB color; `PlatformDefault` restores the value captured by
the active presenter. Material and One UI presenters apply the full snapshot on first show and
every changed same-key update. Material also makes `skipPartiallyExpanded` reversible; One UI has
only one intrinsic expanded state and therefore treats that policy as protocol compatibility.

Presenter-specific margins, handles, drag gestures, and branded chrome remain downstream. Raw
`Dialog` remains a caller-owned content/lifecycle protocol and does not share the bottom-sheet
appearance object.

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

The Android `PopupWindow` transport does not add platform elevation. The rendered popup content is
the single visual shadow owner: `DropdownMenu` keeps its theme elevation, tooltip or custom content
uses its own declaration, and zero-elevation generic content stays shadowless. This prevents a
second rectangular window shadow from competing with a rounded content outline.

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
