package com.viewcompose.text

import com.viewcompose.runtime.Snapshot
import com.viewcompose.runtime.SnapshotApplyResult
import org.junit.Assert.*
import org.junit.Test

class TextHistorySnapshotTest {
    @Test fun `same-document composition finish records one undo unit`() {
        val state = TextFieldState()
        state.updateFromInput(TextFieldValue("你", composition = TextRange(0, 1)))
        state.updateFromInput(TextFieldValue("你"))
        assertTrue(state.canUndo)
        assertTrue(state.undo())
        assertEquals("", state.text)
        assertFalse(state.canUndo)
        assertTrue(state.redo())
        assertEquals("你", state.text)
        assertNull(state.composition)
    }

    @Test fun `unchanged composition start preserves the pre-edit baseline`() {
        val state = TextFieldState(TextFieldValue("a"))
        state.updateFromInput(TextFieldValue("a", composition = TextRange(0, 1)))
        state.updateFromInput(TextFieldValue("ab", composition = TextRange(0, 2)))
        state.updateFromInput(TextFieldValue("ab"))
        assertTrue(state.undo())
        assertEquals("a", state.text)
    }

    @Test fun `cancelled composition does not pollute the next edit`() {
        val state = TextFieldState()
        state.updateFromInput(TextFieldValue("n", composition = TextRange(0, 1)))
        state.updateFromInput(TextFieldValue(""))
        assertFalse(state.canUndo)
        state.updateFromInput(TextFieldValue("x"))
        assertTrue(state.undo())
        assertEquals("", state.text)
        assertFalse(state.canUndo)
    }

    @Test fun `selection-only programmatic edit preserves active composition history`() {
        val state = TextFieldState()
        state.updateFromInput(TextFieldValue("ab", composition = TextRange(0, 2)))
        state.edit { selection = TextRange(1) }
        state.updateFromInput(state.value.copy(composition = null))
        assertTrue(state.undo())
        assertEquals("", state.text)
    }

    @Test fun `abandoned edit restores value and history`() {
        val state = TextFieldState()
        Snapshot.takeMutableSnapshot().use { snapshot ->
            snapshot.enter {
                state.setTextAndPlaceCursorAtEnd("candidate")
                assertTrue(state.canUndo)
            }
        }
        assertEquals("", state.text)
        assertFalse(state.canUndo)
        assertFalse(state.canRedo)
    }

    @Test fun `abandoned undo and clearHistory preserve committed history`() {
        val state = TextFieldState()
        state.setTextAndPlaceCursorAtEnd("committed")
        Snapshot.takeMutableSnapshot().use { snapshot -> snapshot.enter {
            assertTrue(state.undo())
            assertTrue(state.canRedo)
            state.clearHistory()
            assertFalse(state.canRedo)
        } }
        assertEquals("committed", state.text)
        assertTrue(state.canUndo)
        assertFalse(state.canRedo)
        assertTrue(state.undo())
        assertEquals("", state.text)
    }

    @Test fun `abandoned redo leaves the redo entry available`() {
        val state = TextFieldState()
        state.setTextAndPlaceCursorAtEnd("committed")
        state.undo()
        Snapshot.takeMutableSnapshot().use { snapshot -> snapshot.enter { assertTrue(state.redo()) } }
        assertEquals("", state.text)
        assertTrue(state.canRedo)
        assertTrue(state.redo())
        assertEquals("committed", state.text)
    }

    @Test fun `conflicting edit cannot append to the winning history`() {
        val state = TextFieldState()
        Snapshot.takeMutableSnapshot().use { first ->
            Snapshot.takeMutableSnapshot().use { second ->
                first.enter { state.setTextAndPlaceCursorAtEnd("first") }
                second.enter { state.setTextAndPlaceCursorAtEnd("second") }
                assertEquals(SnapshotApplyResult.Success, first.apply())
                assertEquals(SnapshotApplyResult.Failure(1), second.apply())
            }
        }
        assertEquals("first", state.text)
        assertTrue(state.undo())
        assertEquals("", state.text)
        assertFalse(state.canUndo)
    }

    @Test fun `pinned readers observe historical availability with historical text`() {
        val state = TextFieldState()
        Snapshot.takeSnapshot().use { read ->
            state.setTextAndPlaceCursorAtEnd("new")
            read.enter {
                assertEquals("", state.text)
                assertFalse(state.canUndo)
                assertFalse(state.canRedo)
            }
        }
        assertTrue(state.canUndo)
    }

    @Test fun `abandoning composition completion preserves the original baseline`() {
        val state = TextFieldState()
        state.updateFromInput(TextFieldValue("n", composition = TextRange(0, 1)))
        Snapshot.takeMutableSnapshot().use { snapshot -> snapshot.enter {
            state.updateFromInput(TextFieldValue("n"))
            assertTrue(state.canUndo)
        } }
        assertFalse(state.canUndo)
        assertNotNull(state.composition)
        state.updateFromInput(TextFieldValue("ni", composition = TextRange(0, 2)))
        state.updateFromInput(TextFieldValue("ni"))
        assertTrue(state.undo())
        assertEquals("", state.text)
    }

    @Test fun `clearing history also clears an in-progress baseline without undo entries`() {
        val state = TextFieldState()
        state.updateFromInput(TextFieldValue("n", composition = TextRange(0, 1)))
        state.clearHistory()
        state.updateFromInput(TextFieldValue("n"))
        assertFalse(state.canUndo)
    }

    @Test fun `bounded history preserves order through undo and redo`() {
        val state = TextFieldState(historyLimit = 2)
        listOf("a", "b", "c").forEach(state::setTextAndPlaceCursorAtEnd)
        assertTrue(state.undo()); assertEquals("b", state.text)
        assertTrue(state.undo()); assertEquals("a", state.text)
        assertFalse(state.undo())
        assertTrue(state.redo()); assertEquals("b", state.text)
        assertTrue(state.redo()); assertEquals("c", state.text)
        assertFalse(state.redo())
    }
}
