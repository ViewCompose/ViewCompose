# ViewCompose Text Input

## 1. Scope

The current model is a complete document editor state built on the Android View engine. It owns:

- immutable `TextDocument` content
- inline span styles and links
- paragraph alignment, line height, indentation, and bullets
- URI-backed inline attachments
- directional selection
- ephemeral IME composition
- atomic programmatic edits
- input transformations
- undo/redo history
- keyboard options and semantic IME actions
- autofill hints
- unified clipboard, drag/drop, and IME Receive Content
- save/restore of the complete document and selection

It deliberately does not implement a custom text layout engine or a replacement `InputConnection`.
Android `Spannable`, `ContentInfoCompat`, and URI decoding remain renderer details and do not leak
into `viewcompose-text-core`.

## 2. Ownership

`TextFieldState` is the single source of truth. `TextField`, `TextArea`, typed fields, and `SearchBar` only accept a stable state instance.

```kotlin
val state = rememberTextFieldState("initial")

TextField(
    state = state,
    inputTransformation = InputTransformation.maxCodePoints(40),
)
```

Rich display text and editable text use the same document:

```kotlin
val document = textDocument {
    append("ViewCompose", TextSpanStyle(fontWeight = 700))
    append("\n")
    appendAttachment(
        InlineTextAttachment(
            id = "preview",
            mimeType = "image/png",
            uri = "content://example/preview",
        ),
    )
}
val state = rememberTextFieldState(document)

RichText(document)
TextArea(state = state)
```

The old `value: String` plus `onValueChange(String)` API is removed. Reintroducing it as a second core path would discard selection and composition and is not allowed.

## 3. State model

`TextFieldValue` contains a `TextDocument`, a directional `TextRange` selection, and an optional
composing range. `value.text` remains a derived convenience accessor. All offsets are UTF-16
indices so they map exactly to Android `Editable` and `InputConnection`.

`TextDocument` is platform-neutral and immutable. It contains:

1. the UTF-16 text buffer;
2. `TextSpanRange` entries for character style and links;
3. `ParagraphStyleRange` entries for paragraph semantics;
4. `InlineAttachmentRange` entries that point at `INLINE_ATTACHMENT_CHARACTER`.

`TextDocumentBuilder` and `textDocument { ... }` are the construction APIs. A replacement keeps
annotations outside the changed range, clips intersecting annotations, shifts following ranges,
and inserts the replacement document's annotations atomically.

Application changes use one atomic edit:

```kotlin
state.edit {
    replace(0, length, "replacement")
    selectAll()
}
```

`InputTransformation` runs only for platform-proposed user edits. Programmatic edits are never silently rejected by a field filter.

Composition updates are grouped into one undo unit. Undo and redo clear the active composing range because an IME session cannot be replayed safely.

## 4. Receive Content

Every editable field registers `ReceiveContentConfiguration.Default`, accepting `text/*` and
`image/*`. The same listener handles:

1. clipboard paste;
2. drag and drop;
3. IME `commitContent`;
4. app-originated `ViewCompat.performReceiveContent`.

Styled or HTML text is converted from `Spanned` into `TextDocument`. URI items become
`InlineTextAttachment` entries. Unsupported clip items are returned to the platform as the
remaining payload instead of being silently discarded.

Fields can narrow MIME types or transform/reject a received document before insertion:

```kotlin
TextArea(
    state = state,
    receiveContent = ReceiveContentConfiguration(
        mimeTypes = setOf("text/*", "image/png"),
        transformation = { received ->
            audit(received.source, received.mimeTypes)
            received.document
        },
    ),
)
```

Receive Content insertion goes through `InputTransformation`, forms one undo unit, replaces the
current selection, and terminates active IME composition.

## 5. Android bridge

The renderer creates `ViewComposeEditText`, an `AppCompatEditText` subclass with an `AndroidTextFieldController`.

The bridge follows these invariants:

1. Native editing remains synchronous.
2. Text, selection, and composition are read as one snapshot.
3. `InputConnection` mutations and batch edits are observed as transaction boundaries.
4. A native edit updates `TextFieldState`; the resulting recomposition does not write the same value back.
5. External state changes use the smallest `Editable.replace()` range.
6. Framework-owned document spans are reapplied without deleting IME/platform spans.
7. Framework writes restore selection and composition while suppressing feedback callbacks.
8. Input type or editor option changes restart the active input connection only when required.
9. `EditorInfo` publishes the configured Receive Content MIME types.
10. Renderer rollback rebinds the previous document, selection, and composition snapshot.

The View continues to supply platform behavior such as IME integration, cursor handles, clipboard actions, hardware keyboard input, accessibility, bidi layout, spell checking, autofill, and stylus handwriting.

`RichText` uses the same `TextDocument -> Spannable` adapter as `TextField`. Image URI rendering is
best effort; unresolved or non-image attachments render as an inline placeholder while retaining
their full document metadata.

## 6. Persistence

`rememberTextFieldState` uses the host saveable-state registry. It persists:

- text, span styles, paragraph styles, and inline attachments
- selection start
- selection end

It intentionally does not persist:

- IME composition
- undo/redo history
- focus
- keyboard visibility

Those values belong to the active window and input session.

## 7. Testing contract

Every text bridge change must cover:

- native text and selection synchronization
- composing text followed by commit
- input transformation acceptance/rejection
- rich document `Spannable` round-trip
- clipboard and URI Receive Content insertion
- IME MIME publication
- external edits without replacing the `Editable`
- renderer rollback
- rich document save/restore without composition

Real-device coverage remains required for representative Chinese and Japanese IMEs, hardware keyboards, TalkBack, autofill services, and stylus input.

## 8. Boundary

The framework still delegates glyph shaping, bidi, line breaking, cursor geometry, selection
handles, spell checking, and accessibility text traversal to the native View text engine. It does
not attempt compiler-level text lowering or a custom paragraph renderer.
