package com.viewcompose.text

import com.viewcompose.runtime.MutableState
import com.viewcompose.runtime.Snapshot
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

    private val valueState: MutableState<TextFieldValue> = mutableStateOf(initialValue)
    private val historyVersion: MutableState<Int> = mutableStateOf(0)
    private val undoStack = ArrayDeque<TextFieldValue>()
    private val redoStack = ArrayDeque<TextFieldValue>()
    private var compositionBase: TextFieldValue? = null

    /** Current complete editable snapshot. */
    val value: TextFieldValue
        get() = valueState.value

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
        get() {
            historyVersion.value
            return undoStack.isNotEmpty()
        }

    /** Whether [redo] can reapply a document removed by [undo]. */
    val canRedo: Boolean
        get() {
            historyVersion.value
            return redoStack.isNotEmpty()
        }

    /**
     * Applies one atomic application-owned edit.
     *
     * [block] receives an isolated buffer initialized from the current value. A document change
     * terminates active IME composition, adds the previous document to undo history, and clears redo
     * history. Selection-only changes do not create a history entry. Input transformations are not
     * applied to programmatic edits.
     */
    fun edit(block: TextFieldBuffer.() -> Unit) {
        val current = valueState.value
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
        commitProgrammaticValue(current, next)
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
     *
     * @param proposedValue complete platform-proposed editable snapshot
     * @param inputTransformation optional synchronous policy applied to an isolated buffer
     * @return the final committed value after transformation and history handling
     */
    fun updateFromInput(
        proposedValue: TextFieldValue,
        inputTransformation: InputTransformation? = null,
    ): TextFieldValue {
        val current = valueState.value
        val buffer = TextFieldBuffer(
            originalValue = current,
            proposedValue = proposedValue,
        )
        inputTransformation?.transformInput(buffer)
        val accepted = buffer.toTextFieldValue()
        commitInputValue(current, accepted)
        return valueState.value
    }

    /** Restores the previous document without IME composition, returning whether history existed. */
    fun undo(): Boolean {
        if (undoStack.isEmpty()) return false
        val current = valueState.value.withoutComposition()
        redoStack.addLast(current)
        val restored = undoStack.removeLast().withoutComposition()
        compositionBase = null
        Snapshot.withMutableSnapshot {
            valueState.value = restored
            notifyHistoryChanged()
        }
        return true
    }

    /** Reapplies the next redo document without IME composition, returning whether history existed. */
    fun redo(): Boolean {
        if (redoStack.isEmpty()) return false
        val current = valueState.value.withoutComposition()
        pushUndo(current)
        val restored = redoStack.removeLast().withoutComposition()
        compositionBase = null
        Snapshot.withMutableSnapshot {
            valueState.value = restored
            notifyHistoryChanged()
        }
        return true
    }

    /** Clears undo, redo, and any pending IME composition baseline without changing [value]. */
    fun clearHistory() {
        if (undoStack.isEmpty() && redoStack.isEmpty()) return
        undoStack.clear()
        redoStack.clear()
        compositionBase = null
        notifyHistoryChanged()
    }

    private fun commitProgrammaticValue(
        current: TextFieldValue,
        next: TextFieldValue,
    ) {
        if (current == next) return
        compositionBase = null
        if (current.document != next.document) {
            pushUndo(current.withoutComposition())
            redoStack.clear()
            Snapshot.withMutableSnapshot {
                notifyHistoryChanged()
                valueState.value = next
            }
        } else {
            valueState.value = next
        }
    }

    private fun commitInputValue(
        current: TextFieldValue,
        next: TextFieldValue,
    ) {
        if (current == next) return
        if (current.document == next.document) {
            valueState.value = next
            return
        }

        var historyChanged = false
        when {
            current.composition == null && next.composition != null -> {
                // Capture the pre-composition baseline so the final IME commit is one undo unit.
                compositionBase = current.withoutComposition()
            }

            next.composition == null && (current.composition != null || compositionBase != null) -> {
                val base = compositionBase ?: current.withoutComposition()
                compositionBase = null
                if (base.document != next.document) {
                    pushUndo(base)
                    redoStack.clear()
                    historyChanged = true
                }
            }

            current.composition == null && next.composition == null -> {
                pushUndo(current.withoutComposition())
                redoStack.clear()
                historyChanged = true
            }
        }
        if (historyChanged) {
            Snapshot.withMutableSnapshot {
                notifyHistoryChanged()
                valueState.value = next
            }
        } else {
            valueState.value = next
        }
    }

    private fun pushUndo(value: TextFieldValue) {
        if (undoStack.lastOrNull() == value) return
        undoStack.addLast(value)
        while (undoStack.size > historyLimit) {
            undoStack.removeFirst()
        }
    }

    private fun notifyHistoryChanged() {
        historyVersion.value = historyVersion.value + 1
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
