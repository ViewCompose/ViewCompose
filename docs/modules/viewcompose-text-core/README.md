# Text Core

`viewcompose-text-core` is ViewCompose's platform-neutral text editing model. It defines immutable
rich-text documents, directional selections, IME composition snapshots, transactional editing
buffers, observable text-field state with undo/redo, input transformations, normalized Receive
Content contracts, and a versioned save codec.

The module contains no Android types. Android `Editable`, `InputConnection`, clipboard, drag/drop,
and rich-text span adapters live in renderer and host modules and translate to these contracts.

## Artifact and stability

```kotlin
dependencies {
    implementation("com.viewcompose:viewcompose-text-core:0.1.0-alpha03")
}
```

- Stability: **Alpha**. Rich-text and external-content contracts may evolve between alpha releases.
- Platform: Kotlin/JVM library targeting Java 11.
- Direct ViewCompose dependency: `viewcompose-runtime` for observable `TextFieldState` values.
- Platform boundary: no Android, View, resource, or lifecycle types are allowed.

## Offset and range contract

Every text offset is a UTF-16 code-unit index. This matches Android `Editable` and
`InputConnection`, but it is not a Unicode code-point or grapheme-cluster index.

`TextRange(start, end)` preserves direction: a selection from right to left has `start > end`.
Document operations use `min` and `max` when they require an ordered range. A `TextRange` validates
only non-negative values; `TextDocument` and `TextFieldValue` validate ranges against their owning
text.

## Rich-text documents

```kotlin
val document = textDocument {
    append("ViewCompose", TextSpanStyle(fontWeight = 700))
    append(" text")
    addParagraphStyle(
        range = TextRange(0, length),
        style = ParagraphStyle(lineHeightPx = 24f),
    )
    appendAttachment(
        InlineTextAttachment(
            id = "diagram",
            mimeType = "image/png",
            contentDescription = "Architecture diagram",
        ),
    )
}
```

`TextDocument` is immutable and owns copied snapshots of:

- plain text, including `INLINE_ATTACHMENT_CHARACTER` object-replacement positions;
- character-level `TextSpanRange` values;
- paragraph-level `ParagraphStyleRange` values;
- `InlineAttachmentRange` metadata for non-text content.

Style ranges are ordered and bounded by the document. Overlap is allowed and resolved by the
platform adapter. Every attachment offset is unique and must point to an object-replacement
character. Loading attachment URIs and rendering their content are not text-core responsibilities.

Replacing document content preserves annotations outside the edited range, shifts later ranges,
retains only uncovered fragments of overlapping styles, shifts inserted annotations to the target
position, and removes attachments whose placeholders were replaced.

## Text-field state and edit transactions

```kotlin
val state = TextFieldState()

state.edit {
    replaceAll("Hello")
    selection = TextRange(0, 5)
}

state.edit {
    replace(selection.min, selection.max, "ViewCompose")
}

state.undo()
```

`TextFieldState` exposes a stable observable owner around immutable `TextFieldValue` snapshots. Its
editing buffer and history stacks are UI-thread-confined. This is a Q3 state API: each edit, undo,
or redo publishes its complete value and `canUndo`/`canRedo` availability through one snapshot
transaction, so observers cannot receive a committed text value paired with stale history status.

- `edit` is one atomic application-owned transaction and bypasses user-input transformations.
- `TextFieldBuffer` keeps document annotations and maps selection/composition across replacements.
- Document changes end active IME composition, add one undo entry, and clear redo history.
- Selection-only changes do not add history.
- Multiple platform updates during one IME composition are coalesced into a single undo unit when
  composition commits.
- Undo and redo restore documents without reviving ephemeral IME composition.
- `historyLimit` bounds only the undo stack; the default is 100 entries.

## Input transformations

`InputTransformation` receives an isolated buffer for a platform-proposed user edit. It may rewrite
that proposal or reject it with `revertAllChanges()`:

```kotlin
val policy = InputTransformation.digitsOnly()
    .then(InputTransformation.maxCodePoints(6))

state.updateFromInput(proposedValue, policy)
```

Chained policies share the same buffer and execute in declaration order. `maxCodePoints` counts
Unicode code points rather than UTF-16 units so a valid surrogate pair is not split. `digitsOnly`
uses Kotlin character digit classification.

## Receive Content

`ReceivedContent` normalizes clipboard, drag/drop, input-method, autofill, or application payloads
into a `TextDocument`, source, MIME set, and original platform-item count.

`ReceiveContentConfiguration` owns a non-empty MIME allowlist and an optional
`ReceiveContentTransformation`. A transformation returns the document to insert or `null` to reject
the whole payload. MIME values compare structurally; transformation lambdas compare by identity.
The default configuration accepts `text/*` and `image/*`.

Platform adapters remain responsible for MIME negotiation and payload normalization. The accepted
document should enter `TextFieldState` through the same input-transaction path as ordinary user
editing so history and input policy remain coherent.

## Save and restore

```kotlin
val saved: Map<String, Any?> = TextDocumentSaveCodec.encode(document)
val restored: TextDocument = TextDocumentSaveCodec.decode(saved)
```

The versioned codec preserves text, styles, paragraph data, and attachments using only strings,
numbers, booleans, lists, and string-keyed maps. Decoding validates the complete structure and fails
closed on unsupported versions, invalid enum values, incompatible types, bad ranges, or attachment
invariants. IME composition is intentionally not part of the document codec.

## Related documentation

- [Text input runtime architecture](../../architecture/text-input.md)
- [Edit, validate, and submit text](../../guides/text-input.md)
- [Use rich and received text content](../../guides/text-input-rich-text.md)
- [Lifecycle and saved-state architecture](../../architecture/lifecycle-and-saved-state.md)
- [State snapshot architecture](../../architecture/state-snapshots.md)
- [Source documentation and API comment standard](../../project/api-documentation-quality.md)

The complete generated reference is available in the
[`viewcompose-text-core` API tree](https://docs.viewcompose.com/api/viewcompose-text-core/current/).

## Compatibility notes

The `0.1.0-alpha02` line establishes UTF-16 offsets, immutable document annotations, edit-buffer
mapping, IME composition history coalescing, Receive Content normalization, and save format version
1. Do not persist `TextFieldState`, `TextFieldBuffer`, active composition ranges, transformation
instances, or platform adapters. Persist only values explicitly encoded by a compatible codec.
