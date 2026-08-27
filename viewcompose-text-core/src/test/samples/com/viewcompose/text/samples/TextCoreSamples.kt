package com.viewcompose.text.samples

import com.viewcompose.text.InlineTextAttachment
import com.viewcompose.text.InputTransformation
import com.viewcompose.text.ParagraphStyle
import com.viewcompose.text.TextDocument
import com.viewcompose.text.TextDocumentSaveCodec
import com.viewcompose.text.TextFieldState
import com.viewcompose.text.TextRange
import com.viewcompose.text.TextSpanStyle
import com.viewcompose.text.textDocument
import com.viewcompose.text.then

fun richTextDocumentSample(): TextDocument {
    // DOCS_REGION_START(text-core-module-document)
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
    // DOCS_REGION_END(text-core-module-document)
    return document
}

fun textFieldStateSample() {
    // DOCS_REGION_START(text-core-module-state)
val state = TextFieldState()

state.edit {
    replaceAll("Hello")
    selection = TextRange(0, 5)
}

state.edit {
    replace(selection.min, selection.max, "ViewCompose")
}

check(state.text == "ViewCompose")
check(state.undo())
check(state.text == "Hello")
    // DOCS_REGION_END(text-core-module-state)
}

fun inputTransformationSample() {
    // DOCS_REGION_START(text-core-module-transformation)
val policy = InputTransformation.digitsOnly()
    .then(InputTransformation.maxCodePoints(6))
val state = TextFieldState()

state.updateFromInput(
    proposedValue = state.value.copy(
        document = TextDocument.plain("123456"),
        selection = TextRange(6),
    ),
    inputTransformation = policy,
)
    // DOCS_REGION_END(text-core-module-transformation)
}

fun textDocumentSaveCodecSample() {
    // DOCS_REGION_START(text-core-module-save)
val original = richTextDocumentSample()
val saved = TextDocumentSaveCodec.encode(original)
val restored = TextDocumentSaveCodec.decode(saved)

check(restored == original)
    // DOCS_REGION_END(text-core-module-save)
}
