package com.viewcompose.text

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TextFieldStateTest {
    @Test
    fun `programmatic edits update text selection and undo history atomically`() {
        val state = TextFieldState(TextFieldValue("hello"))

        state.edit {
            replace(0, 5, "world")
            selectAll()
        }

        assertEquals("world", state.text)
        assertEquals(TextRange(0, 5), state.selection)
        assertTrue(state.canUndo)
        assertTrue(state.undo())
        assertEquals(TextFieldValue("hello"), state.value)
        assertTrue(state.canRedo)
        assertTrue(state.redo())
        assertEquals("world", state.text)
    }

    @Test
    fun `selection-only input does not create undo history`() {
        val state = TextFieldState(TextFieldValue("hello"))

        state.updateFromInput(
            TextFieldValue(
                text = "hello",
                selection = TextRange(1, 4),
            ),
        )

        assertEquals(TextRange(1, 4), state.selection)
        assertFalse(state.canUndo)
    }

    @Test
    fun `ime composition is one undo unit`() {
        val state = TextFieldState(TextFieldValue(""))

        state.updateFromInput(
            TextFieldValue(
                text = "n",
                selection = TextRange(1),
                composition = TextRange(0, 1),
            ),
        )
        state.updateFromInput(
            TextFieldValue(
                text = "ni",
                selection = TextRange(2),
                composition = TextRange(0, 2),
            ),
        )
        state.updateFromInput(
            TextFieldValue(
                text = "你",
                selection = TextRange(1),
                composition = null,
            ),
        )

        assertEquals("你", state.text)
        assertTrue(state.undo())
        assertEquals("", state.text)
        assertNull(state.composition)
        assertFalse(state.canUndo)
    }

    @Test
    fun `input transformation can reject a proposed edit`() {
        val state = TextFieldState(TextFieldValue("12"))

        val accepted = state.updateFromInput(
            proposedValue = TextFieldValue("12a"),
            inputTransformation = InputTransformation.digitsOnly(),
        )

        assertEquals(TextFieldValue("12"), accepted)
        assertEquals("12", state.text)
        assertFalse(state.canUndo)
    }

    @Test
    fun `max code point transformation does not split surrogate pairs`() {
        val state = TextFieldState(TextFieldValue("😀"))
        val limit = InputTransformation.maxCodePoints(1)

        state.updateFromInput(
            proposedValue = TextFieldValue("😀a"),
            inputTransformation = limit,
        )

        assertEquals("😀", state.text)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `text field value rejects selection outside text`() {
        TextFieldValue(
            text = "a",
            selection = TextRange(2),
        )
    }
}
