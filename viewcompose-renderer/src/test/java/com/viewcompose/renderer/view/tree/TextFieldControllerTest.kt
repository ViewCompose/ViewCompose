package com.viewcompose.renderer.view.tree

import android.content.ClipData
import android.content.Context
import android.net.Uri
import android.text.Selection
import android.view.inputmethod.BaseInputConnection
import android.view.inputmethod.EditorInfo
import androidx.core.view.ContentInfoCompat
import androidx.core.view.ViewCompat
import androidx.core.view.inputmethod.EditorInfoCompat
import com.viewcompose.text.InputTransformation
import com.viewcompose.text.InlineTextAttachment
import com.viewcompose.text.ParagraphStyle
import com.viewcompose.text.ParagraphTextAlignment
import com.viewcompose.text.ReceiveContentConfiguration
import com.viewcompose.text.ReceiveContentSource
import com.viewcompose.text.TextFieldState
import com.viewcompose.text.TextFieldValue
import com.viewcompose.text.TextFontStyle
import com.viewcompose.text.TextRange
import com.viewcompose.text.TextSpanStyle
import com.viewcompose.text.textDocument
import com.viewcompose.ui.node.TextFieldImeAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class TextFieldControllerTest {
    private val context: Context
        get() = RuntimeEnvironment.getApplication()

    @Test
    fun `external state patch minimally updates editable and restores selection`() {
        val state = TextFieldState(TextFieldValue("hello"))
        val view = boundView(state)
        val editable = view.editableText

        state.edit {
            replace(5, 5, " world")
            selection = TextRange(2, 8)
        }
        view.textController.bind(spec(state))

        assertSame(editable, view.editableText)
        assertEquals("hello world", view.text.toString())
        assertEquals(2, view.selectionStart)
        assertEquals(8, view.selectionEnd)
    }

    @Test
    fun `native text and selection changes synchronously update complete state`() {
        val state = TextFieldState(TextFieldValue("hello"))
        val view = boundView(state)

        view.editableText.replace(1, 4, "i")
        Selection.setSelection(view.editableText, 0, 2)

        assertEquals("hio", state.text)
        assertEquals(TextRange(0, 2), state.selection)
    }

    @Test
    fun `ime composing session reaches state and forms one undo unit`() {
        val state = TextFieldState()
        val view = boundView(state)
        val connection = requireNotNull(view.onCreateInputConnection(EditorInfo()))

        assertTrue(connection.setComposingText("ni", 1))
        assertEquals("ni", state.text)
        assertEquals(TextRange(0, 2), state.composition)

        assertTrue(connection.commitText("你", 1))
        assertEquals("你", state.text)
        assertEquals(null, state.composition)
        assertTrue(state.undo())
        assertEquals("", state.text)
        assertFalse(state.canUndo)
    }

    @Test
    fun `rejected platform edit is restored without a feedback mutation`() {
        val state = TextFieldState(TextFieldValue("12"))
        val view = boundView(
            state = state,
            inputTransformation = InputTransformation.digitsOnly(),
        )

        view.setText("12a")

        assertEquals("12", state.text)
        assertEquals("12", view.text.toString())
        assertFalse(state.canUndo)
    }

    @Test
    fun `framework value reapplies composing range`() {
        val state = TextFieldState(
            TextFieldValue(
                text = "ni",
                selection = TextRange(2),
                composition = TextRange(0, 2),
            ),
        )
        val view = boundView(state)

        assertEquals(0, BaseInputConnection.getComposingSpanStart(view.editableText))
        assertEquals(2, BaseInputConnection.getComposingSpanEnd(view.editableText))
    }

    @Test
    fun `editor action dispatches semantic action`() {
        val state = TextFieldState()
        var dispatched: TextFieldImeAction? = null
        val view = ViewComposeEditText(context)
        val spec = spec(
            state = state,
            imeAction = TextFieldImeAction.Search,
            onKeyboardAction = {
                dispatched = it
                true
            },
        )
        InputViewBinder.bindTextField(view, spec)

        view.onEditorAction(EditorInfo.IME_ACTION_SEARCH)
        assertEquals(TextFieldImeAction.Search, dispatched)
    }

    @Test
    fun `rich document round trips through native spannable`() {
        val original = textDocument {
            append(
                "ViewCompose",
                TextSpanStyle(
                    color = 0xFF123456.toInt(),
                    fontWeight = 700,
                    fontStyle = TextFontStyle.Italic,
                    link = "https://example.test",
                ),
            )
            append("\n")
            appendAttachment(
                InlineTextAttachment(
                    id = "image",
                    mimeType = "image/png",
                    uri = "content://images/1",
                ),
            )
            addParagraphStyle(
                range = TextRange(0, length),
                style = ParagraphStyle(
                    alignment = ParagraphTextAlignment.Center,
                    lineHeightPx = 32f,
                ),
            )
        }
        val state = TextFieldState(TextFieldValue(original))
        val view = boundView(state)

        val roundTripped = AndroidTextDocumentAdapter.fromCharSequence(
            requireNotNull(view.text),
        )

        assertEquals(original, roundTripped)
    }

    @Test
    fun `receive content inserts transformed clipboard document as one undo unit`() {
        var source: ReceiveContentSource? = null
        val configuration = ReceiveContentConfiguration(
            mimeTypes = setOf("text/*"),
            transformation = { content ->
                source = content.source
                textDocument {
                    append(
                        content.document.text.uppercase(),
                        TextSpanStyle(fontWeight = 700),
                    )
                }
            },
        )
        val state = TextFieldState(TextFieldValue("hello "))
        val view = boundView(
            state = state,
            receiveContent = configuration,
        )
        val payload = ContentInfoCompat.Builder(
            ClipData.newPlainText("text", "world"),
            ContentInfoCompat.SOURCE_CLIPBOARD,
        ).build()

        val remaining = ViewCompat.performReceiveContent(view, payload)

        assertEquals(null, remaining)
        assertEquals(ReceiveContentSource.Clipboard, source)
        assertEquals("hello WORLD", state.text)
        assertEquals(
            TextRange(6, 11),
            state.document.spanStyles.single().range,
        )
        assertTrue(state.undo())
        assertEquals("hello ", state.text)
    }

    @Test
    fun `receive content maps URI item to inline attachment`() {
        val state = TextFieldState()
        val view = boundView(state)
        val clip = ClipData(
            "image",
            arrayOf("image/png"),
            ClipData.Item(Uri.parse("content://images/42")),
        )
        val payload = ContentInfoCompat.Builder(
            clip,
            ContentInfoCompat.SOURCE_DRAG_AND_DROP,
        ).build()

        val remaining = ViewCompat.performReceiveContent(view, payload)

        assertEquals(null, remaining)
        assertEquals(1, state.document.inlineAttachments.size)
        assertEquals("image/png", state.document.inlineAttachments.single().attachment.mimeType)
        assertEquals(
            "content://images/42",
            state.document.inlineAttachments.single().attachment.uri,
        )
    }

    @Test
    fun `editor info publishes receive content MIME types`() {
        val state = TextFieldState()
        val view = boundView(
            state = state,
            receiveContent = ReceiveContentConfiguration(
                mimeTypes = setOf("image/png", "text/html"),
            ),
        )
        val editorInfo = EditorInfo()

        view.onCreateInputConnection(editorInfo)

        assertEquals(
            setOf("image/png", "text/html"),
            EditorInfoCompat.getContentMimeTypes(editorInfo).toSet(),
        )
    }

    private fun boundView(
        state: TextFieldState,
        inputTransformation: InputTransformation? = null,
        receiveContent: ReceiveContentConfiguration = ReceiveContentConfiguration.Default,
    ): ViewComposeEditText {
        return ViewComposeEditText(context).also { view ->
            InputViewBinder.bindTextField(
                view = view,
                spec = spec(
                    state = state,
                    inputTransformation = inputTransformation,
                    receiveContent = receiveContent,
                ),
            )
        }
    }

    private fun spec(
        state: TextFieldState,
        inputTransformation: InputTransformation? = null,
        receiveContent: ReceiveContentConfiguration = ReceiveContentConfiguration.Default,
        imeAction: TextFieldImeAction = TextFieldImeAction.Default,
        onKeyboardAction: ((TextFieldImeAction) -> Boolean)? = null,
    ): InputViewBinder.TextFieldSpec {
        return InputViewBinder.TextFieldSpec(
            state = state,
            value = state.value,
            placeholder = "",
            enabled = true,
            singleLine = true,
            minLines = 1,
            maxLines = 1,
            inputType = android.text.InputType.TYPE_CLASS_TEXT,
            editorOptions = EditorInfo.IME_ACTION_UNSPECIFIED,
            hintColor = 0,
            readOnly = false,
            inputTransformation = inputTransformation,
            onKeyboardAction = onKeyboardAction,
            imeAction = imeAction,
            onFocusChange = null,
            autofillHints = emptySet(),
            cursorColor = 0,
            receiveContent = receiveContent,
        )
    }
}
