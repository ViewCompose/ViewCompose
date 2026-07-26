package com.viewcompose.text

/**
 * Mutable editing buffer presented to programmatic edits and input transformations.
 */
class TextFieldBuffer internal constructor(
    val originalValue: TextFieldValue,
    proposedValue: TextFieldValue,
) {
    private var content = proposedValue.document

    var selection: TextRange = proposedValue.selection
        set(value) {
            field = value.coerceIn(content.text.length)
        }

    var composition: TextRange? = proposedValue.composition
        set(value) {
            field = value?.coerceIn(content.text.length)
        }

    val text: String
        get() = content.text

    val document: TextDocument
        get() = content

    val length: Int
        get() = content.text.length

    fun replace(
        start: Int,
        end: Int,
        replacement: CharSequence,
    ) {
        require(start in 0..content.text.length) { "replace start $start is out of bounds." }
        require(end in start..content.text.length) { "replace end $end is out of bounds." }
        val replacementLength = replacement.length
        content = content.replace(
            range = TextRange(start, end),
            replacement = replacement,
        )
        selection = selection.mapAcrossReplacement(
            start = start,
            end = end,
            replacementLength = replacementLength,
        )
        composition = composition?.mapAcrossReplacement(
            start = start,
            end = end,
            replacementLength = replacementLength,
        )
    }

    fun replace(
        start: Int,
        end: Int,
        replacement: TextDocument,
    ) {
        require(start in 0..content.text.length) { "replace start $start is out of bounds." }
        require(end in start..content.text.length) { "replace end $end is out of bounds." }
        val replacementLength = replacement.text.length
        content = content.replace(
            range = TextRange(start, end),
            replacement = replacement,
        )
        selection = selection.mapAcrossReplacement(
            start = start,
            end = end,
            replacementLength = replacementLength,
        )
        composition = composition?.mapAcrossReplacement(
            start = start,
            end = end,
            replacementLength = replacementLength,
        )
    }

    fun replaceAll(text: CharSequence) {
        replaceAll(TextDocument.plain(text.toString()))
    }

    fun replaceAll(document: TextDocument) {
        content = document
        selection = TextRange(content.text.length)
        composition = null
    }

    fun placeCursorAtEnd() {
        selection = TextRange(content.text.length)
    }

    fun selectAll() {
        selection = TextRange(0, content.text.length)
    }

    fun revertAllChanges() {
        content = originalValue.document
        selection = originalValue.selection
        composition = originalValue.composition
    }

    internal fun toTextFieldValue(): TextFieldValue {
        return TextFieldValue(
            document = content,
            selection = selection.coerceIn(content.text.length),
            composition = composition?.coerceIn(content.text.length),
        )
    }
}

internal fun TextRange.coerceIn(textLength: Int): TextRange {
    return TextRange(
        start = start.coerceIn(0, textLength),
        end = end.coerceIn(0, textLength),
    )
}

private fun TextRange.mapAcrossReplacement(
    start: Int,
    end: Int,
    replacementLength: Int,
): TextRange {
    return TextRange(
        start = this.start.mapAcrossReplacement(start, end, replacementLength),
        end = this.end.mapAcrossReplacement(start, end, replacementLength),
    )
}

private fun Int.mapAcrossReplacement(
    start: Int,
    end: Int,
    replacementLength: Int,
): Int {
    return when {
        this <= start -> this
        this >= end -> this + replacementLength - (end - start)
        else -> start + minOf(this - start, replacementLength)
    }
}
