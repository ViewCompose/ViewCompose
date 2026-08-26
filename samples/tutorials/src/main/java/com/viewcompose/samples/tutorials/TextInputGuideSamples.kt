package com.viewcompose.samples.tutorials

import com.viewcompose.text.InlineTextAttachment
import com.viewcompose.text.InputTransformation
import com.viewcompose.text.ReceiveContentConfiguration
import com.viewcompose.text.TextSpanStyle
import com.viewcompose.text.textDocument
import com.viewcompose.ui.foundation.Button
import com.viewcompose.ui.foundation.Column
import com.viewcompose.ui.foundation.RichText
import com.viewcompose.ui.foundation.SearchBar
import com.viewcompose.ui.foundation.Text
import com.viewcompose.ui.foundation.TextField
import com.viewcompose.ui.foundation.TextFieldInputProfile
import com.viewcompose.ui.foundation.TextFieldLinePolicy
import com.viewcompose.ui.foundation.UiTreeBuilder
import com.viewcompose.ui.foundation.rememberTextFieldState
import com.viewcompose.ui.node.TextFieldImeAction
import com.viewcompose.ui.node.TextFieldKeyboardOptions
import com.viewcompose.ui.node.TextFieldType

// DOCS_REGION_START(text-input-editing)
fun UiTreeBuilder.EditableSearchForm(onSearch: (String) -> Unit) {
    val query = rememberTextFieldState()
    val name = rememberTextFieldState()

    Column {
        SearchBar(
            state = query,
            placeholder = "Search",
            onSearch = onSearch,
        )
        TextField(
            state = name,
            label = "Display name",
            supportingText = "Up to 24 characters",
            inputTransformation = InputTransformation.maxCodePoints(24),
        )
        Button(
            text = "Undo",
            enabled = name.canUndo,
            onClick = { name.undo() },
        )
    }
}
// DOCS_REGION_END(text-input-editing)

// DOCS_REGION_START(text-input-rich-text)
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
// DOCS_REGION_END(text-input-rich-text)

// DOCS_REGION_START(text-input-receive-content)
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
// DOCS_REGION_END(text-input-receive-content)

// DOCS_REGION_START(text-input-ime)
fun UiTreeBuilder.EmailSubmissionField(onSubmit: (String) -> Unit) {
    val email = rememberTextFieldState()

    TextField(
        state = email,
        label = "Email",
        inputProfile = TextFieldInputProfile(
            keyboardOptions = TextFieldKeyboardOptions(
                keyboardType = TextFieldType.Email,
                imeAction = TextFieldImeAction.Done,
            ),
            autofillHints = TextFieldInputProfile.Email.autofillHints,
        ),
        onKeyboardAction = { action ->
            if (action == TextFieldImeAction.Done) {
                onSubmit(email.text)
                true
            } else {
                false
            }
        },
    )
}
// DOCS_REGION_END(text-input-ime)
