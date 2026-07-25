package com.viewcompose.text

import com.viewcompose.runtime.MutableState
import com.viewcompose.runtime.mutableStateOf

/**
 * Stable, observable owner of editable text, selection, IME composition, and undo history.
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

    val value: TextFieldValue
        get() = valueState.value

    val text: String
        get() = value.text

    val selection: TextRange
        get() = value.selection

    val composition: TextRange?
        get() = value.composition

    val canUndo: Boolean
        get() {
            historyVersion.value
            return undoStack.isNotEmpty()
        }

    val canRedo: Boolean
        get() {
            historyVersion.value
            return redoStack.isNotEmpty()
        }

    /**
     * Applies an application-owned edit. Text changes terminate any active IME composition.
     */
    fun edit(block: TextFieldBuffer.() -> Unit) {
        val current = valueState.value
        val buffer = TextFieldBuffer(
            originalValue = current,
            proposedValue = current,
        )
        buffer.block()
        val proposed = buffer.toTextFieldValue()
        val next = if (proposed.text != current.text) {
            proposed.copy(composition = null)
        } else {
            proposed
        }
        commitProgrammaticValue(current, next)
    }

    fun setTextAndPlaceCursorAtEnd(text: String) {
        edit {
            replaceAll(text)
        }
    }

    fun clearText() {
        setTextAndPlaceCursorAtEnd("")
    }

    /**
     * Applies a user edit proposed by a platform adapter and returns the accepted value.
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

    fun undo(): Boolean {
        if (undoStack.isEmpty()) return false
        val current = valueState.value.withoutComposition()
        redoStack.addLast(current)
        valueState.value = undoStack.removeLast().withoutComposition()
        compositionBase = null
        notifyHistoryChanged()
        return true
    }

    fun redo(): Boolean {
        if (redoStack.isEmpty()) return false
        val current = valueState.value.withoutComposition()
        pushUndo(current)
        valueState.value = redoStack.removeLast().withoutComposition()
        compositionBase = null
        notifyHistoryChanged()
        return true
    }

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
        if (current.text != next.text) {
            pushUndo(current.withoutComposition())
            redoStack.clear()
            notifyHistoryChanged()
        }
        valueState.value = next
    }

    private fun commitInputValue(
        current: TextFieldValue,
        next: TextFieldValue,
    ) {
        if (current == next) return
        if (current.text == next.text) {
            valueState.value = next
            return
        }

        when {
            current.composition == null && next.composition != null -> {
                compositionBase = current.withoutComposition()
            }

            next.composition == null && (current.composition != null || compositionBase != null) -> {
                val base = compositionBase ?: current.withoutComposition()
                compositionBase = null
                if (base.text != next.text) {
                    pushUndo(base)
                    redoStack.clear()
                    notifyHistoryChanged()
                }
            }

            current.composition == null && next.composition == null -> {
                pushUndo(current.withoutComposition())
                redoStack.clear()
                notifyHistoryChanged()
            }
        }
        valueState.value = next
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

    companion object {
        const val DEFAULT_HISTORY_LIMIT: Int = 100
    }
}
