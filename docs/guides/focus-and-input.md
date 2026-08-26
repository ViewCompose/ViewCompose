---
schema_version: 2
document_id: guide.focus-and-key-input
doc_type: guide
owner:
  kind: capability
  id: focus.input
version_lane: released
capability_ids:
  - focus.input
artifact_ids:
  - viewcompose-ui-contract
  - viewcompose-ui-foundation
  - viewcompose-renderer-android
sample_ids:
  - guide.focus-form
task: Move focus, handle hardware keys, and keep an editor visible without creating a second scroll policy.
success_checks:
  - Every imperative FocusRequester is stable and attached before requestFocus is called.
  - Directional overrides point to the intended target and unhandled keys remain available to later dispatch.
  - Keyed restoration is limited to temporary remount while screen or process state remains application-owned.
  - A pager form uses a page-local vertical scroll owner for IME visibility.
failure_checks:
  - A FocusRequester is reconstructed during every composition or invoked before mounting.
  - Preview and bubble handlers both consume an event without owning that shortcut.
  - Focus-follow behavior is represented by a container Boolean or a manually assigned scroll offset.
  - A pager is expected to reveal arbitrary within-page editor coordinates by changing pages.
---

# Control focus and hardware keys

## Move and clear focus

Keep each `FocusRequester` stable with `remember`, attach it to the destination, and declare
directional traversal on the source. Use `LocalFocusManager.current` for session-wide move or clear
operations. A requester must be attached to a mounted target before `requestFocus()` is called.

{/* compiled-region source="samples/tutorials/src/main/java/com/viewcompose/samples/tutorials/FocusAndNestedScrollGuideSamples.kt" region="focus-form" sample_id="guide.focus-form" build_target=":samples:tutorials:compileDebugKotlin" */}
```kotlin
fun UiTreeBuilder.CredentialFocusForm() {
    val email = rememberTextFieldState()
    val password = rememberTextFieldState()
    val passwordFocus = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current

    Column(
        modifier = Modifier
            .focusGroup()
            .onPreviewKeyEvent { event ->
                if (event.key == Key.Escape && event.type == KeyEventType.KeyDown) {
                    focusManager.clearFocus(force = true)
                    true
                } else {
                    false
                }
            },
    ) {
        TextField(
            state = email,
            label = "Email",
            modifier = Modifier.focusProperties {
                next = passwordFocus
                down = passwordFocus
            },
        )
        TextField(
            state = password,
            label = "Password",
            modifier = Modifier.focusRequester(passwordFocus),
        )
        Button(
            text = "Focus password",
            onClick = { passwordFocus.requestFocus() },
        )
    }
}
```

`onPreviewKeyEvent` travels from the outermost declarative ancestor to the focused target. An
unconsumed event then bubbles from the target through `onKeyEvent`. Hardware keys are separate from
soft-keyboard composition, which continues through `TextFieldState` and the Android input bridge.

## Restore a keyed child

Call `saveFocusedChild()` before temporarily removing or recycling a focus group, then call
`restoreFocusedChild()` after it returns. Restoration uses the declarative node key and remains
pending until a matching target mounts. Screen or process restoration still belongs to application
state followed by an explicit request after mounting.

## Keep a pager editor visible

Vertical scroll owners reveal a focused editor through Android's native child-rectangle chain, even
when direct user scrolling is disabled. A pager owns whole-page selection, so place a potentially
obscured form in a page-local `ScrollableColumn`; the enclosing `VerticalPager` remains controlled
by application page state. There is no focus-follow flag. Control unwanted initial movement by
deciding which editor receives focus; do not disable the scroll owner's visibility contract. See
[Modifier architecture](../architecture/modifier.md) for ownership, propagation, and Android
mapping invariants.
