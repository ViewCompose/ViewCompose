# Focus and Hardware Key Input

## 1. Architecture

The focus contract is split into three layers:

1. `viewcompose-ui-contract` owns platform-independent focus directions, requester state,
   focus properties, and key-event values.
2. `viewcompose-renderer-android` binds those contracts to Android `View` focus and key dispatch.
3. `viewcompose-ui-foundation` exposes the current render session through
   `LocalFocusManager.current`.

Focus requesters never retain a `View` directly. The renderer attaches and detaches connectors as
nodes mount, rebind, roll back, and dispose.

## 2. Public APIs

- `Modifier.focusable(enabled)`
- `Modifier.focusRequester(requester)`
- `Modifier.focusProperties { ... }`
- `Modifier.focusGroup(enabled)`
- `Modifier.onFocusChanged { state -> ... }`
- `Modifier.onPreviewKeyEvent { event -> ... }`
- `Modifier.onKeyEvent { event -> ... }`
- `LocalFocusManager.current`
- `FocusRequester.requestFocus(direction)`
- `FocusRequester.saveFocusedChild()` / `restoreFocusedChild()`

`onPreviewKeyEvent` travels from the root-most declarative ancestor to the focused target.
Unconsumed events then travel from the target back through its declarative ancestors via
`onKeyEvent`.

## 3. Example

```kotlin
val emailFocus = remember { FocusRequester() }
val passwordFocus = remember { FocusRequester() }
val focusManager = LocalFocusManager.current

Column {
    TextField(
        state = email,
        modifier = Modifier
            .focusRequester(emailFocus)
            .focusProperties {
                next = passwordFocus
                down = passwordFocus
            },
    )
    TextField(
        state = password,
        modifier = Modifier
            .focusRequester(passwordFocus)
            .onKeyEvent { event ->
                if (event.key == Key.Escape && event.type == KeyEventType.KeyDown) {
                    focusManager.clearFocus(force = true)
                    true
                } else {
                    false
                }
            },
    )
}
```

## 4. Restoration

`saveFocusedChild()` records the focused connector's declarative node key. If that target is
temporarily removed or recycled, `restoreFocusedChild()` remains pending until the same requester
is attached to a target carrying that key again.

This API restores focus across framework node reuse/remount. Durable screen and process restoration
must still be driven by application state, followed by an explicit focus request after mounting.

## 5. Focused-editor visibility

Focused editors inside LazyColumn, LazyVerticalGrid, and ScrollableColumn use Android's native
child-rectangle request chain automatically. There is no focus-follow flag. The nearest vertical
scroll owner moves only enough to reveal the editor, and this programmatic movement remains active
when `userScrollEnabled = false`.

A pager is different: it owns complete-page selection, not arbitrary movement inside a page. Put a
form that may be obscured by the IME inside a page-local scroll owner:

```kotlin
VerticalPager(currentPage = page, onPageChanged = { page = it }) {
    Page(key = "profile", contentRevision = profile.version) {
        ScrollableColumn {
            Text("Profile")
            TextField(state = name, placeholder = "Name")
        }
    }
}
```

The page boundary stops the editor's within-page request before it reaches the pager, so opening
the IME does not select another page. Control unwanted initial movement by controlling which editor
receives focus; do not disable the scroll owner's visibility contract.

## 6. Android boundaries

- Hardware key events are separate from IME text editing. Soft-keyboard composition continues
  through the `TextFieldState` and `InputConnection` bridge.
- Native Android focus search remains the fallback when no explicit `focusProperties` destination
  is declared.
- On Android versions where the initial focus request occurs before the IME finishes resizing the
  window, the renderer reissues the same native rectangle request after the visible viewport
  changes. It does not calculate or assign a container scroll offset.
- `AndroidView` owns arbitrary native listeners. A `nativeView` callback that replaces
  `View.OnKeyListener` also replaces framework key dispatch for that native target; use the
  declarative key modifiers as the single owner when preview/bubble semantics are required.
