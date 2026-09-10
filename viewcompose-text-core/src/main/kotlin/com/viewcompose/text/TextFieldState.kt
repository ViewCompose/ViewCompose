package com.viewcompose.text

import com.viewcompose.runtime.MutableState
import com.viewcompose.runtime.mutableStateOf

/**
 * Stable observable owner of editable text, selection, IME composition, and undo history.
 *
 * Reads participate in the ViewCompose snapshot runtime. Edit transactions and history stacks are
 * thread-confined and should be accessed from the owning UI thread. Programmatic document changes
 * become individual undo units. A sequence of IME composition updates is coalesced into one undo
 * unit when composition commits. Each edit, undo, or redo publishes the complete value and
 * undo/redo availability in one snapshot transaction, so observers never receive a committed text
 * value paired with stale history availability.
 * The value, composition baseline, and history belong to one immutable snapshot-managed state:
 * abandoning or conflicting an enclosing snapshot leaves all of them unchanged. History updates
 * copy bounded lists of immutable value references, not the document contents.
 *
 * @sample com.viewcompose.text.samples.textFieldStateSample
 * @param initialValue first committed editable snapshot
 * @property historyLimit maximum number of retained undo entries; must be positive
 */
class TextFieldState(
    initialValue: TextFieldValue = TextFieldValue(""),
    val historyLimit: Int = DEFAULT_HISTORY_LIMIT,
) {
    init {
        require(historyLimit > 0) { "historyLimit must be greater than zero." }
    }

    private data class EditingState(
        val value: TextFieldValue,
        val undo: List<TextFieldValue> = emptyList(),
        val redo: List<TextFieldValue> = emptyList(),
        val compositionBase: TextFieldValue? = null,
    )

    private val editingState: MutableState<EditingState> = mutableStateOf(EditingState(initialValue))

    /** Current complete editable snapshot. */
    val value: TextFieldValue
        get() = editingState.value.value

    /** Current plain-text projection. */
    val text: String
        get() = value.text

    /** Current immutable rich-text document. */
    val document: TextDocument
        get() = value.document

    /** Current directional selection or cursor range. */
    val selection: TextRange
        get() = value.selection

    /** Active ephemeral IME composition range, if any. */
    val composition: TextRange?
        get() = value.composition

    /** Whether [undo] can restore a previous committed document. */
    val canUndo: Boolean
        get() = editingState.value.undo.isNotEmpty()

    /** Whether [redo] can reapply a document removed by [undo]. */
    val canRedo: Boolean
        get() = editingState.value.redo.isNotEmpty()

    /**
     * Applies one atomic application-owned edit.
     *
     * [block] receives an isolated buffer initialized from the current value. A document change
     * terminates active IME composition, adds the previous document to undo history, and clears redo
     * history. Selection-only changes do not create a history entry. Input transformations are not
     * applied to programmatic edits.
     */
    fun edit(block: TextFieldBuffer.() -> Unit) {
        val previous = editingState.value
        val current = previous.value
        val buffer = TextFieldBuffer(
            originalValue = current,
            proposedValue = current,
        )
        buffer.block()
        val proposed = buffer.toTextFieldValue()
        val next = if (proposed.document != current.document) {
            proposed.copy(composition = null)
        } else {
            proposed
        }
        commitProgrammaticValue(previous, next)
    }

    /** Replaces the document with plain [text] as one edit and places the cursor at the end. */
    fun setTextAndPlaceCursorAtEnd(text: String) {
        edit {
            replaceAll(text)
        }
    }

    /** Replaces the rich document as one edit and places the cursor at the end. */
    fun setDocumentAndPlaceCursorAtEnd(document: TextDocument) {
        edit {
            replaceAll(document)
        }
    }

    /** Replaces the document with empty plain text. */
    fun clearText() {
        setTextAndPlaceCursorAtEnd("")
    }

    /**
     * Applies a user edit proposed by a platform adapter and returns the accepted value.
     *
     * [inputTransformation] may rewrite or reject the proposal before commit. Selection-only and
     * in-progress composition changes update the value without creating independent undo units.
     * Ending composition finalizes its undo unit even when the document text is unchanged, as with
     * an IME finish-composition event. A cancellation returning to the baseline adds no undo unit.
     *
     * @param proposedValue complete platform-proposed editable snapshot
     * @param inputTransformation optional synchronous policy applied to an isolated buffer
     * @return the final committed value after transformation and history handling
     */
    fun updateFromInput(
        proposedValue: TextFieldValue,
        inputTransformation: InputTransformation? = null,
    ): TextFieldValue {
        val previous = editingState.value
        val current = previous.value
        val buffer = TextFieldBuffer(
            originalValue = current,
            proposedValue = proposedValue,
        )
        inputTransformation?.transformInput(buffer)
        val accepted = buffer.toTextFieldValue()
        commitInputValue(previous, accepted)
        return value
    }

    /** Restores the previous document without IME composition, returning whether history existed. */
    fun undo(): Boolean {
        val previous = editingState.value
        val restored = previous.undo.lastOrNull() ?: return false
        editingState.value = previous.copy(
            value = restored.withoutComposition(),
            undo = previous.undo.dropLast(1),
            redo = previous.redo + previous.value.withoutComposition(),
            compositionBase = null,
        )
        return true
    }

    /** Reapplies the next redo document without IME composition, returning whether history existed. */
    fun redo(): Boolean {
        val previous = editingState.value
        val restored = previous.redo.lastOrNull() ?: return false
        editingState.value = previous.copy(
            value = restored.withoutComposition(),
            undo = pushUndo(previous.undo, previous.value.withoutComposition()),
            redo = previous.redo.dropLast(1),
            compositionBase = null,
        )
        return true
    }

    /** Clears undo, redo, and any pending IME composition baseline without changing [value]. */
    fun clearHistory() {
        val previous = editingState.value
        editingState.value = previous.copy(undo = emptyList(), redo = emptyList(), compositionBase = null)
    }

    private fun commitProgrammaticValue(
        previous: EditingState,
        next: TextFieldValue,
    ) {
        if (previous.value == next) return
        if (previous.value.document != next.document) {
            editingState.value = previous.copy(
                value = next,
                undo = pushUndo(previous.undo, previous.value.withoutComposition()),
                redo = emptyList(),
                compositionBase = null,
            )
        } else {
            commitInputValue(previous, next)
        }
    }

    private fun commitInputValue(
        previous: EditingState,
        next: TextFieldValue,
    ) {
        val current = previous.value
        if (current == next) return
        var compositionBase = previous.compositionBase
        var undo = previous.undo
        var redo = previous.redo
        when {
            current.composition == null && next.composition != null -> {
                // Capture the pre-composition baseline so the final IME commit is one undo unit.
                compositionBase = current.withoutComposition()
            }

            next.composition == null && (current.composition != null || compositionBase != null) -> {
                val base = compositionBase ?: current.withoutComposition()
                compositionBase = null
                if (base.document != next.document) {
                    undo = pushUndo(undo, base)
                    redo = emptyList()
                }
            }

            current.composition == null && next.composition == null && current.document != next.document -> {
                undo = pushUndo(undo, current.withoutComposition())
                redo = emptyList()
            }
        }
        editingState.value = EditingState(next, undo, redo, compositionBase)
    }

    private fun pushUndo(history: List<TextFieldValue>, value: TextFieldValue): List<TextFieldValue> {
        if (history.lastOrNull() == value) return history
        return history.takeLast(historyLimit - 1) + value
    }

    private fun TextFieldValue.withoutComposition(): TextFieldValue {
        return if (composition == null) this else copy(composition = null)
    }

    /** Text-field state defaults. */
    companion object {
        /** Default maximum number of retained undo entries. */
        const val DEFAULT_HISTORY_LIMIT: Int = 100
    }
}
