# ViewCompose Text Input

## 1. Scope

The current model is a complete plain-text editor state built on the Android View engine. It owns:

- text
- directional selection
- ephemeral IME composition
- atomic programmatic edits
- input transformations
- undo/redo history
- keyboard options and semantic IME actions
- autofill hints
- save/restore of text and selection

It deliberately does not implement a custom text layout engine or a replacement `InputConnection`.

## 2. Ownership

`TextFieldState` is the single source of truth. `TextField`, `TextArea`, typed fields, and `SearchBar` only accept a stable state instance.

```kotlin
val state = rememberTextFieldState("initial")

TextField(
    state = state,
    inputTransformation = InputTransformation.maxCodePoints(40),
)
```

The old `value: String` plus `onValueChange(String)` API is removed. Reintroducing it as a second core path would discard selection and composition and is not allowed.

## 3. State model

`TextFieldValue` contains a `String`, a directional `TextRange` selection, and an optional composing range. All offsets are UTF-16 indices so they map exactly to Android `Editable` and `InputConnection`.

Application changes use one atomic edit:

```kotlin
state.edit {
    replace(0, length, "replacement")
    selectAll()
}
```

`InputTransformation` runs only for platform-proposed user edits. Programmatic edits are never silently rejected by a field filter.

Composition updates are grouped into one undo unit. Undo and redo clear the active composing range because an IME session cannot be replayed safely.

## 4. Android bridge

The renderer creates `ViewComposeEditText`, an `AppCompatEditText` subclass with an `AndroidTextFieldController`.

The bridge follows these invariants:

1. Native editing remains synchronous.
2. Text, selection, and composition are read as one snapshot.
3. `InputConnection` mutations and batch edits are observed as transaction boundaries.
4. A native edit updates `TextFieldState`; the resulting recomposition does not write the same value back.
5. External state changes use the smallest `Editable.replace()` range.
6. Framework writes restore selection and composition while suppressing feedback callbacks.
7. Input type or editor option changes restart the active input connection only when required.
8. Renderer rollback rebinds the previous value, selection, and composition snapshot.

The View continues to supply platform behavior such as IME integration, cursor handles, clipboard actions, hardware keyboard input, accessibility, bidi layout, spell checking, autofill, and stylus handwriting.

## 5. Persistence

`rememberTextFieldState` uses the host saveable-state registry. It persists:

- text
- selection start
- selection end

It intentionally does not persist:

- IME composition
- undo/redo history
- focus
- keyboard visibility

Those values belong to the active window and input session.

## 6. Testing contract

Every text bridge change must cover:

- native text and selection synchronization
- composing text followed by commit
- input transformation acceptance/rejection
- external edits without replacing the `Editable`
- renderer rollback
- save/restore without composition

Real-device coverage remains required for representative Chinese and Japanese IMEs, hardware keyboards, TalkBack, autofill services, and stylus input.

## 7. Separate advanced model

Rich text, paragraph semantics, inline attachments, and unified IME/clipboard/drag receive-content require an annotated document model. They must be designed as a separate layer above the plain-text editor state. Android `Spannable` and `ContentInfo` must not leak into `viewcompose-text-core`.
