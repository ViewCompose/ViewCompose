package com.viewcompose.text

/**
 * Mutable transaction buffer used by programmatic edits and input transformations.
 *
 * Mutations are isolated until the owning [TextFieldState] accepts the buffer. Replacements preserve
 * unaffected document annotations and map selection/composition offsets across the edited range.
 */
class TextFieldBuffer internal constructor(
    /** Last committed value from which this transaction began. */
    val originalValue: TextFieldValue,
    proposedValue: TextFieldValue,
) {
    private var content = proposedValue.document

    /** Directional selection, automatically clamped to the current document length. */
    var selection: TextRange = proposedValue.selection
        set(value) {
            field = value.coerceIn(content.text.length)
        }

    /** Optional IME composition range, automatically clamped to the current document length. */
    var composition: TextRange? = proposedValue.composition
        set(value) {
            field = value?.coerceIn(content.text.length)
        }

    /** Current plain-text projection. */
    val text: String
        get() = content.text

    /** Current immutable rich-text document snapshot. */
    val document: TextDocument
        get() = content

    /** Current plain-text length in UTF-16 code units. */
    val length: Int
        get() = content.text.length

    /**
     * Replaces UTF-16 range `[start, end)` with plain [replacement].
     *
     * Existing annotations outside the range are preserved and moved as required. Selection and
     * composition are mapped to equivalent logical positions.
     *
     * @throws IllegalArgumentException when the ordered range is outside the current text
     */
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
        // Keep selection and composition attached to equivalent logical positions.
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

    /**
     * Replaces UTF-16 range `[start, end)` with rich-text [replacement].
     *
     * @throws IllegalArgumentException when the ordered range is outside the current text
     */
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

    /** Replaces the complete document with plain [text] and places the cursor at its end. */
    fun replaceAll(text: CharSequence) {
        replaceAll(TextDocument.plain(text.toString()))
    }

    /** Replaces the complete document and places the cursor at its end. */
    fun replaceAll(document: TextDocument) {
        content = document
        selection = TextRange(content.text.length)
        composition = null
    }

    /** Collapses the selection at the current document end. */
    fun placeCursorAtEnd() {
        selection = TextRange(content.text.length)
    }

    /** Selects the complete current document in forward direction. */
    fun selectAll() {
        selection = TextRange(0, content.text.length)
    }

    /** Restores document, selection, and composition from [originalValue]. */
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

/** Clamps both directional offsets into the current text length. */
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
