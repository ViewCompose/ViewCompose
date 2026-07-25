package com.viewcompose.renderer.view.tree

import android.content.Context
import android.text.Selection
import android.view.inputmethod.BaseInputConnection
import android.view.inputmethod.EditorInfo
import com.viewcompose.text.InputTransformation
import com.viewcompose.text.TextFieldState
import com.viewcompose.text.TextFieldValue
import com.viewcompose.text.TextRange
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

    private fun boundView(
        state: TextFieldState,
        inputTransformation: InputTransformation? = null,
    ): ViewComposeEditText {
        return ViewComposeEditText(context).also { view ->
            InputViewBinder.bindTextField(
                view = view,
                spec = spec(
                    state = state,
                    inputTransformation = inputTransformation,
                ),
            )
        }
    }

    private fun spec(
        state: TextFieldState,
        inputTransformation: InputTransformation? = null,
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
        )
    }
}
