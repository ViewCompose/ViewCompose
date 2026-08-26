---
schema_version: 2
document_id: architecture.text-input-runtime
doc_type: architecture
owner:
  kind: capability
  id: text.input
version_lane: released
capability_ids:
  - text.input
artifact_ids:
  - viewcompose-text-core
  - viewcompose-ui-foundation
  - viewcompose-renderer-android
sample_ids:
  - tutorial.text-input
  - guide.text-input-rich-text
  - guide.text-input-receive-content
invariants:
  - TextFieldState is the single authoritative document, selection, composition, and history owner for one logical editor.
  - TextDocument and all public text offsets use immutable platform-neutral UTF-16 contracts.
  - Native editing is observed synchronously as one text, selection, and composition transaction without feedback writes.
  - Only document and directional selection persist across host recreation; composition, history, focus, and keyboard visibility are session-local.
evidence:
  - viewcompose-text-core/src/test/kotlin/com/viewcompose/text/TextFieldStateTest.kt
  - viewcompose-text-core/src/test/kotlin/com/viewcompose/text/TextDocumentTest.kt
  - viewcompose-ui-foundation/src/test/java/com/viewcompose/ui/foundation/runtime/RememberSaveableTest.kt
  - viewcompose-renderer-android/src/test/java/com/viewcompose/renderer/view/tree/TextFieldControllerTest.kt
  - viewcompose-renderer-android/src/test/java/com/viewcompose/renderer/view/tree/AndroidTextDocumentAdapterTest.kt
---

# Text input runtime architecture

## 1. Ownership boundary

`viewcompose-text-core` owns the platform-neutral document and editing model:

- immutable `TextDocument` content, spans, paragraphs, links, and inline-attachment metadata;
- directional `TextRange` selection and ephemeral composition ranges;
- `TextFieldState`, transactional buffers, bounded undo/redo, and input transformations;
- normalized Receive Content values and the versioned document save codec.

UI Foundation owns state remembrance and the `BasicTextField`, `TextField`, and `SearchBar` DSLs.
Android Renderer owns `Editable`, `Spannable`, `InputConnection`, clipboard, drag/drop, autofill,
and IME adapters. The architecture deliberately does not replace native text layout or expose
Android span and content payload types through Text Core.

## 2. Document and offset model

`TextDocument` stores UTF-16 text plus character, paragraph, and inline-attachment ranges. UTF-16
is the public offset unit because it maps exactly to Android `Editable` and `InputConnection`; it
is not a Unicode code-point or grapheme-cluster index. `TextRange` preserves direction, so an
end-to-start selection is not normalized on storage.

A document replacement clips annotations intersecting the replaced range, shifts later ranges,
and inserts replacement annotations atomically. Each inline attachment owns one object-replacement
character and immutable metadata. URI decoding and drawable lifetime stay in the platform adapter.

`TextFieldValue` combines one document, selection, and optional composition range. `value.text` is
a convenience projection, not a second source of truth. `TextFieldState.edit` publishes one
complete value; `updateFromInput` evaluates a platform proposal through `InputTransformation`
before committing it.

## 3. Editing and history transactions

Text editing is UI-thread confined. Application edits bypass input transformations, end active
composition when the document changes, create one undo entry, and clear redo. Selection-only edits
do not add history. Platform composition updates coalesce into one undo unit when the IME commits
them. Undo and redo restore documents and selections but never revive an obsolete composition
session.

This distinction prevents validation policy from rejecting an application-owned replacement and
prevents intermediate text, selection, or history states from becoming observable.

## 4. Android bridge invariants

Renderer creates a native `ViewComposeEditText` and binds it through
`AndroidTextFieldController`. The bridge follows these rules:

1. Native editing stays synchronous; text, selection, and composition are read as one snapshot.
2. InputConnection mutations and batch edits establish transaction boundaries.
3. A native edit updates `TextFieldState`; its resulting recomposition does not write the same
   value back.
4. An external state change applies the smallest `Editable.replace()` range.
5. Framework document spans are refreshed without deleting IME or platform-owned spans.
6. Framework writes restore selection and composition while feedback callbacks are suppressed.
7. Input type or editor-option changes restart an active input connection only when required.
8. `EditorInfo` publishes configured Receive Content MIME types.
9. A renderer rollback rebinds the previously committed document, selection, and composition.

The native View continues to own glyph shaping, bidi, line breaking, cursor and selection handles,
hardware keyboard input, spell checking, autofill, accessibility text traversal, and stylus
handwriting.

## 5. Receive Content and rich display

Clipboard paste, drag/drop, IME `commitContent`, and application
`ViewCompat.performReceiveContent` enter one normalized Receive Content path. Styled/HTML text is
converted into `TextDocument`; URI items become inline-attachment entries. Supported items replace
the current selection as one input transaction and undo unit. Unsupported platform items are
returned as remaining payload instead of being discarded.

`RichText` and editable fields share the same document-to-`Spannable` adapter. An unresolved or
non-image URI draws a placeholder while retaining its document metadata. This preserves the model
without turning asynchronous image loading into text-state ownership.

## 6. Persistence and session lifetime

`rememberTextFieldState` uses the host saveable-state registry. It persists the versioned document
encoding and directional selection. It intentionally does not persist active IME composition,
undo/redo stacks, focus, or keyboard visibility; those values belong to one active window and
input connection.

The state instance must remain stable for the lifetime of one logical editor. Reconstructing it
during composition breaks selection, composition, history, and native-controller identity even if
the visible text is equal.

## 7. Verification boundary

Deterministic tests own document replacement, transformation, history, save/restore, `Spannable`
round-trip, Receive Content insertion, editor metadata, minimal external patches, and renderer
rollback. Representative Chinese and Japanese IMEs, hardware keyboards, TalkBack, autofill
services, and stylus handwriting remain device-backed acceptance because their behavior belongs to
platform implementations.

Use the focused guides for [editing and IME policy](../guides/text-input.md) and
[rich and received content](../guides/text-input-rich-text.md). Future delivery priorities remain in the project
[roadmap](../project/roadmap.md), not in this architecture contract.
