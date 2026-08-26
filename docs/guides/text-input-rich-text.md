---
schema_version: 2
document_id: guide.text-input-rich-text
doc_type: guide
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
  - guide.text-input-rich-text
  - guide.text-input-receive-content
task: Preserve rich document metadata while constructing content or receiving clipboard, drop, and IME payloads.
success_checks:
  - Span, paragraph, link, and inline-attachment ranges remain in the immutable document through edits.
  - RichText and TextField consume the same document model.
  - Unresolved attachments keep their metadata and render a placeholder.
  - Accepted external content replaces the selection as one undoable input transaction.
failure_checks:
  - Rich content is flattened into String or Android Spannable application state.
  - Application code assigns offsets as Unicode code points instead of UTF-16 indices.
  - Attachment loading is treated as TextDocument ownership.
  - Unsupported platform items are silently consumed or a Receive Content callback blocks on I/O.
---

# Edit and display rich text

Use `TextDocument` when style, paragraph, link, or inline-attachment metadata must survive editing.
The model is immutable and platform-neutral; Android `Spannable` is an adapter detail. See the
[text input architecture](../architecture/text-input.md) for replacement and offset invariants.

## Build one document

{/* compiled-region source="samples/tutorials/src/main/java/com/viewcompose/samples/tutorials/TextInputGuideSamples.kt" region="text-input-rich-text" sample_id="guide.text-input-rich-text" build_target=":samples:tutorials:compileDebugKotlin" */}
```kotlin
fun UiTreeBuilder.RichTextEditor() {
    val initialDocument = textDocument {
        append("ViewCompose", TextSpanStyle(fontWeight = 700))
        append(" editor\n")
        appendAttachment(
            InlineTextAttachment(
                id = "preview",
                mimeType = "image/png",
                uri = "content://example/preview",
                contentDescription = "Preview",
            ),
        )
    }
    val state = rememberTextFieldState(initialDocument)

    Column {
        RichText(state.document)
        TextField(
            state = state,
            linePolicy = TextFieldLinePolicy.MultiLine(minLines = 3, maxLines = 8),
        )
    }
}
```

`RichText` and `TextField` use the same document-to-native adapter. Editing updates the state with a
new immutable document; the display observes `state.document` directly.

## Preserve annotations and attachments

All offsets are UTF-16 indices. Use `TextDocumentBuilder.length` and `TextRange` rather than
counting user-perceived characters. A replacement preserves unaffected annotations, clips covered
ranges, shifts following ranges, and removes an attachment only when its object-replacement
character is replaced.

An inline attachment stores identity, MIME type, optional URI, and accessibility description. URI
loading is best effort. A missing decoder, unresolved URI, or non-image payload renders an inline
placeholder but does not erase metadata from the document.

## Receive external content

Every editable field defaults to `text/*` and `image/*`. Provide
`ReceiveContentConfiguration` to narrow MIME negotiation or synchronously validate the normalized
document:

{/* compiled-region source="samples/tutorials/src/main/java/com/viewcompose/samples/tutorials/TextInputGuideSamples.kt" region="text-input-receive-content" sample_id="guide.text-input-receive-content" build_target=":samples:tutorials:compileDebugKotlin" */}
```kotlin
fun UiTreeBuilder.SharedContentField() {
    val state = rememberTextFieldState()
    val policy = ReceiveContentConfiguration(
        mimeTypes = setOf("text/*", "image/png"),
        transformation = { received ->
            received.document.takeIf { it.text.length <= 4_000 }
        },
    )

    TextField(
        state = state,
        linePolicy = TextFieldLinePolicy.MultiLine(),
        receiveContent = policy,
    )
}
```

Clipboard paste, drag/drop, IME `commitContent`, and application receive-content calls share one
listener. The synchronous transformation returns the document to insert or `null` to reject the
normalized payload; it must not retain platform data, load URIs, block on I/O, or launch work.
Accepted content runs through `InputTransformation`, replaces the selection, ends composition, and
forms one undo unit. Unsupported clip items return to the platform as remaining content.

## Verify the task

Compile with `./gradlew :samples:tutorials:compileDebugKotlin`, then verify:

1. insert text before and inside a styled range; unaffected styles must move or clip correctly;
2. edit beside an attachment; its identity and description must remain attached to the same object
   character;
3. use an unavailable attachment URI; a placeholder must appear without losing document metadata;
4. recreate the Activity; rich annotations, attachments, and selection must restore;
5. paste styled text and drop an image; each accepted payload must replace the selection as one
   undoable edit while unsupported mixed items remain available to the platform;
6. enter bidirectional and supplementary Unicode text; selection and edits must use native UTF-16
   positions without crashes or split surrogate pairs.

Flattened styling, Android types in shared state, missing attachment metadata, or code-point offsets
used as document indices are failed integrations.
