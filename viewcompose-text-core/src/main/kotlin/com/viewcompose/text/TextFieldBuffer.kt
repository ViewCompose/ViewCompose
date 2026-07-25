package com.viewcompose.text

/**
 * Mutable editing buffer presented to programmatic edits and input transformations.
 */
class TextFieldBuffer internal constructor(
    val originalValue: TextFieldValue,
    proposedValue: TextFieldValue,
) {
    private var content = StringBuilder(proposedValue.text)

    var selection: TextRange = proposedValue.selection
        set(value) {
            field = value.coerceIn(content.length)
        }

    var composition: TextRange? = proposedValue.composition
        set(value) {
            field = value?.coerceIn(content.length)
        }

    val text: String
        get() = content.toString()

    val length: Int
        get() = content.length

    fun replace(
        start: Int,
        end: Int,
        replacement: CharSequence,
    ) {
        require(start in 0..content.length) { "replace start $start is out of bounds." }
        require(end in start..content.length) { "replace end $end is out of bounds." }
        val replacementLength = replacement.length
        content.replace(start, end, replacement.toString())
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
        content = StringBuilder(text)
        selection = TextRange(content.length)
        composition = null
    }

    fun placeCursorAtEnd() {
        selection = TextRange(content.length)
    }

    fun selectAll() {
        selection = TextRange(0, content.length)
    }

    fun revertAllChanges() {
        content = StringBuilder(originalValue.text)
        selection = originalValue.selection
        composition = originalValue.composition
    }

    internal fun toTextFieldValue(): TextFieldValue {
        return TextFieldValue(
            text = content.toString(),
            selection = selection.coerceIn(content.length),
            composition = composition?.coerceIn(content.length),
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
