---
schema_version: 2
document_id: guide.overlay-presentation
doc_type: guide
owner:
  kind: capability
  id: overlay.foundation
version_lane: released
capability_ids:
  - overlay.foundation
  - overlay.anchor
  - overlay.android-transport
  - overlay.material3
  - overlay.oneui7
artifact_ids:
  - viewcompose-ui-foundation
  - viewcompose-ui-contract
  - viewcompose-overlay-android
  - viewcompose-overlay-material3-android
  - viewcompose-overlay-oneui7-android
sample_ids:
  - guide.overlay-bottom-sheet
  - guide.overlay-dropdown-menu
  - guide.overlay-snackbar
task: Present a bottom sheet, anchored menu, or transient message with stable caller-owned state and an explicit root-scoped backend.
success_checks:
  - Each visible request has a stable requestKey and one application state owner removes it after dismissal.
  - Popup anchorId matches one mounted overlayAnchor in the same host window and follows layout or scroll changes.
  - Snackbar and Toast requests use an intentional queue policy and handle every terminal callback once.
  - The root explicitly selects the neutral, Material 3, One UI 7, or custom host required by the requested presenters.
failure_checks:
  - A declaration expects platform dismissal to mutate caller state automatically.
  - Runtime classpath order is used to select a design-system backend.
  - Duplicate anchor identifiers or unstable request keys are used within one render session.
  - A neutral host is expected to silently supply Material or One UI Snackbar and bottom-sheet presentation.
---

# Present overlays and transient feedback

Overlay declarations are state-controlled requests. The application owns whether a request exists;
the root-scoped host owns its platform window, presentation, and cleanup. Use the
[overlay tutorial](../tutorials/overlays.md) first if you have not installed an Android presenter.

## Show a modal bottom sheet

Keep one stable key while the logical sheet remains visible. A platform dismissal invokes the
callback but does not change `visible`; update the same application state that created the request.

{/* compiled-region source="samples/tutorials/src/main/java/com/viewcompose/samples/tutorials/OverlayGuideSamples.kt" region="overlay-bottom-sheet" sample_id="guide.overlay-bottom-sheet" build_target=":samples:tutorials:compileDebugKotlin" */}
```kotlin
fun UiTreeBuilder.AccountActionsSheet(
    visible: Boolean,
    onDismissRequest: () -> Unit,
) {
    ModalBottomSheet(
        visible = visible,
        requestKey = "account-actions",
        skipPartiallyExpanded = true,
        onDismissRequest = onDismissRequest,
    ) {
        Text("Account actions")
    }
}
```

Theme values, nested `ProvideModalBottomSheetOverrides`, and instance overrides resolve into one
complete appearance snapshot. Material 3 supports reversible partial-state policy; the One UI 7
presenter has one intrinsic expanded state. Presenter-specific margins, handles, and gestures stay
inside their integration modules.

## Anchor a dropdown menu

Publish an anchor on the rendered node and use the same identifier in the menu request. Logical
start/end follows the anchor's layout direction. The default `FlipThenClamp` policy tries the
opposite side near an edge, then keeps the menu inside the visible window.

{/* compiled-region source="samples/tutorials/src/main/java/com/viewcompose/samples/tutorials/OverlayGuideSamples.kt" region="overlay-dropdown-menu" sample_id="guide.overlay-dropdown-menu" build_target=":samples:tutorials:compileDebugKotlin" */}
```kotlin
fun UiTreeBuilder.ProfileMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
) {
    Text(
        text = "Profile",
        modifier = Modifier.overlayAnchor("profile-menu-anchor"),
    )
    DropdownMenu(
        expanded = expanded,
        anchorId = "profile-menu-anchor",
        requestKey = "profile-menu",
        alignment = PopupAlignment.BelowEnd,
        onDismissRequest = onDismissRequest,
    ) {
        DropdownMenuItem("Settings", onClick = onDismissRequest)
        DropdownMenuItem("Sign out", onClick = onDismissRequest)
    }
}
```

The Android presenter observes global layout and scrolling while the popup is open. Popup content
owns its shape and elevation; the `PopupWindow` transport does not add a second platform shadow.
Use `Clamp` to retain the requested side or `None` only when exact unclipped coordinates are
intentional.

## Replace repeated transient feedback

Snackbar and Toast share one host-owned FIFO lane. Identity is `(render session, requestKey)`, so
equal recompositions do not enqueue the same declaration again. Choose the policy explicitly when
the same operation can report repeatedly.

{/* compiled-region source="samples/tutorials/src/main/java/com/viewcompose/samples/tutorials/OverlayGuideSamples.kt" region="overlay-snackbar" sample_id="guide.overlay-snackbar" build_target=":samples:tutorials:compileDebugKotlin" */}
```kotlin
fun UiTreeBuilder.SaveResultSnackbar(
    visible: Boolean,
    onUndo: () -> Unit,
    onDismiss: () -> Unit,
) {
    Snackbar(
        visible = visible,
        requestKey = "save-result",
        message = "Saved",
        actionLabel = "Undo",
        queuePolicy = TransientFeedbackQueuePolicy.ReplaceSameKey,
        onAction = onUndo,
        onDismiss = { onDismiss() },
    )
}
```

Use `Enqueue` for independent notices, `ReplaceCurrent` for a new urgent result,
`ReplaceSameKey` for the latest version of one operation, and `DropIfBusy` for disposable status.
Callbacks distinguish timeout, action, gesture, replacement, removal, session cleanup, dropped,
and platform dismissal. Android Toast timing is approximate because all supported API levels do
not provide a reliable hide callback.

## Select and verify the backend

- Neutral roots use `viewcompose-overlay-android` for Dialog, Popup, Toast, nested sessions, and
  cleanup. Snackbar and modal bottom sheet remain explicitly unsupported.
- Material roots use `viewcompose-overlay-material3-android`; `setMaterial3UiContent` selects its
  adapter explicitly.
- One UI roots keep neutral `setUiContent` and explicitly construct the
  `viewcompose-overlay-oneui7-android` host when One UI Snackbar or bottom-dialog presentation is
  required.

Verify that outside press and Android Back clear caller state, an open menu follows a moving
anchor, repeated same-key feedback does not duplicate, and session disposal removes only that
session's surfaces. Root selection, transport/session invariants, attribution, and fallback rules
belong to [ADR-0006](../architecture/decisions/0006-root-scoped-overlay-backend-selection.md).
Implementation contracts belong to the [neutral](../modules/viewcompose-overlay-android/README.md),
[Material 3](../modules/viewcompose-overlay-material3-android/README.md), and
[One UI 7](../modules/viewcompose-overlay-oneui7-android/README.md) module manuals.
