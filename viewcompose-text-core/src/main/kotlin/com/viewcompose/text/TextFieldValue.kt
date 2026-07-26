package com.viewcompose.text

/**
 * An immutable snapshot of the complete editable state.
 *
 * Offsets use UTF-16 indices to match Android's Editable and InputConnection contracts.
 * [composition] is an ephemeral IME-owned range and must not be persisted across host recreation.
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
