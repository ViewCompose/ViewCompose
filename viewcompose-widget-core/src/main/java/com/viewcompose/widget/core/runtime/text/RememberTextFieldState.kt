package com.viewcompose.widget.core

import com.viewcompose.text.TextFieldState
import com.viewcompose.text.TextFieldValue
import com.viewcompose.text.TextRange
import com.viewcompose.text.TextDocument
import com.viewcompose.text.TextDocumentSaveCodec

/**
 * 记住稳定的文本编辑状态，并在宿主重建后恢复文本与方向性选区；IME 组合和撤销历史保持会话级。
 * Remembers a stable text editor state and restores text plus directional selection after host
 * recreation. Active IME composition and undo history are intentionally session-local.
 */
fun rememberTextFieldState(
    initialText: String = "",
    initialSelection: TextRange = TextRange(initialText.length),
    historyLimit: Int = TextFieldState.DEFAULT_HISTORY_LIMIT,
): TextFieldState {
    return rememberSaveable(
        saver = textFieldStateSaver(historyLimit),
    ) {
        TextFieldState(
            initialValue = TextFieldValue(
                text = initialText,
                selection = initialSelection,
            ),
            historyLimit = historyLimit,
        )
    }
}

/**
 * 使用富文本文档初始化可保存的文本编辑状态。
 * Initializes saveable text editor state from a rich text document.
 */
fun rememberTextFieldState(
    initialDocument: TextDocument,
    initialSelection: TextRange = TextRange(initialDocument.text.length),
    historyLimit: Int = TextFieldState.DEFAULT_HISTORY_LIMIT,
): TextFieldState {
    return rememberSaveable(
        saver = textFieldStateSaver(historyLimit),
    ) {
        TextFieldState(
            initialValue = TextFieldValue(
                document = initialDocument,
                selection = initialSelection,
            ),
            historyLimit = historyLimit,
        )
    }
}

/**
 * 编解码 TextFieldState 的可保存快照，明确版本号以便未来扩展格式。
 * Encodes and decodes saveable TextFieldState snapshots with an explicit version for future format changes.
 */
fun textFieldStateSaver(
    historyLimit: Int = TextFieldState.DEFAULT_HISTORY_LIMIT,
): Saver<TextFieldState, List<Any?>> {
    require(historyLimit > 0) { "historyLimit must be greater than zero." }
    return listSaver(
        save = { state ->
            listOf(
                TEXT_FIELD_STATE_FORMAT_VERSION,
                TextDocumentSaveCodec.encode(state.document),
                state.selection.start,
                state.selection.end,
            )
        },
        restore = { saved ->
            require(saved.size == TEXT_FIELD_STATE_ENVELOPE_SIZE) {
                "Saved TextFieldState has ${saved.size} values; expected " +
                    "$TEXT_FIELD_STATE_ENVELOPE_SIZE."
            }
            require(saved[0] == TEXT_FIELD_STATE_FORMAT_VERSION) {
                "Unsupported TextFieldState format version: ${saved[0]}."
            }
            val document = TextDocumentSaveCodec.decode(saved[1])
            val selectionStart = saved[2] as? Int
                ?: error("Saved TextFieldState selection start must be an Int.")
            val selectionEnd = saved[3] as? Int
                ?: error("Saved TextFieldState selection end must be an Int.")
            TextFieldState(
                initialValue = TextFieldValue(
                    document = document,
                    selection = TextRange(selectionStart, selectionEnd),
                    composition = null,
                ),
                historyLimit = historyLimit,
            )
        },
    )
}

private const val TEXT_FIELD_STATE_FORMAT_VERSION = 2
private const val TEXT_FIELD_STATE_ENVELOPE_SIZE = 4
