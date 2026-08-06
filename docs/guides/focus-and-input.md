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

## 5. Android boundaries

- Hardware key events are separate from IME text editing. Soft-keyboard composition continues
  through the `TextFieldState` and `InputConnection` bridge.
- Native Android focus search remains the fallback when no explicit `focusProperties` destination
  is declared.
- `AndroidView` owns arbitrary native listeners. A `nativeView` callback that replaces
  `View.OnKeyListener` also replaces framework key dispatch for that native target; use the
  declarative key modifiers as the single owner when preview/bubble semantics are required.
