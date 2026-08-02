package com.viewcompose.text

/**
 * Immutable snapshot of a complete editable text-field value.
 *
 * All offsets use UTF-16 indices to match Android `Editable` and `InputConnection`. [selection]
 * preserves direction. [composition] is an ephemeral IME-owned range and must not be persisted
 * across host recreation.
 *
 * @property document current immutable rich-text document
 * @property selection directional selection or cursor range within [document]
 * @property composition optional active IME composition range within [document]
 */
data class TextFieldValue(
    val document: TextDocument,
    val selection: TextRange = TextRange(document.text.length),
    val composition: TextRange? = null,
) {
    constructor(
        text: String,
        selection: TextRange = TextRange(text.length),
        composition: TextRange? = null,
    ) : this(
        document = TextDocument.plain(text),
        selection = selection,
        composition = composition,
    )

    /** Plain-text projection of [document]. */
    val text: String
        get() = document.text

    init {
        requireRangeInText(selection, "selection")
        composition?.let { requireRangeInText(it, "composition") }
    }

    private fun requireRangeInText(
        range: TextRange,
        label: String,
    ) {
        require(range.start <= text.length && range.end <= text.length) {
            "TextFieldValue $label $range exceeds text length ${text.length}."
        }
    }
}
