package com.viewcompose.text

/** Unicode object-replacement character reserved for inline attachment positions. */
const val INLINE_ATTACHMENT_CHARACTER: Char = '\uFFFC'

/** Requested font posture for a text span. */
enum class TextFontStyle {
    Normal,
    Italic,
}

/** Vertical placement of a span relative to the surrounding text baseline. */
enum class TextVerticalAlignment {
    Normal,
    Superscript,
    Subscript,
}

/**
 * Immutable character-level rich-text style.
 *
 * Nullable values inherit from the surrounding text style. Colors are packed ARGB integers and
 * pixel values are physical rendering hints interpreted by the platform adapter.
 *
 * @property color optional foreground ARGB color
 * @property backgroundColor optional background ARGB color
 * @property fontWeight optional OpenType-style weight in `1..1000`
 * @property fontStyle optional font posture
 * @property relativeSize optional positive multiplier relative to surrounding text
 * @property underline whether to draw an underline
 * @property lineThrough whether to draw a strike-through line
 * @property verticalAlignment baseline-relative placement
 * @property link optional application-defined link target
 */
data class TextSpanStyle(
    val color: Int? = null,
    val backgroundColor: Int? = null,
    val fontWeight: Int? = null,
    val fontStyle: TextFontStyle? = null,
    val relativeSize: Float? = null,
    val underline: Boolean = false,
    val lineThrough: Boolean = false,
    val verticalAlignment: TextVerticalAlignment = TextVerticalAlignment.Normal,
    val link: String? = null,
) {
    init {
        fontWeight?.let { weight ->
            require(weight in 1..1_000) { "fontWeight must be in 1..1000." }
        }
        relativeSize?.let { scale ->
            require(scale.isFinite() && scale > 0f) {
                "relativeSize must be finite and greater than zero."
            }
        }
    }
}

/** Logical horizontal alignment for a paragraph. */
enum class ParagraphTextAlignment {
    Start,
    Center,
    End,
    Justify,
}

/**
 * Immutable paragraph bullet geometry.
 *
 * @property radiusPx non-negative bullet radius in physical pixels
 * @property gapWidthPx non-negative gap between bullet and paragraph in physical pixels
 * @property color optional packed ARGB color; `null` inherits paragraph text color
 */
data class TextBullet(
    val radiusPx: Float = 3f,
    val gapWidthPx: Float = 8f,
    val color: Int? = null,
) {
    init {
        require(radiusPx.isFinite() && radiusPx >= 0f) {
            "Bullet radius must be finite and non-negative."
        }
        require(gapWidthPx.isFinite() && gapWidthPx >= 0f) {
            "Bullet gap width must be finite and non-negative."
        }
    }
}

/**
 * Immutable paragraph-level rich-text style.
 *
 * @property alignment optional logical alignment
 * @property lineHeightPx optional positive line height in physical pixels
 * @property firstLineIndentPx non-negative first-line indentation in physical pixels
 * @property restLineIndentPx non-negative indentation for remaining lines in physical pixels
 * @property bullet optional bullet geometry
 */
data class ParagraphStyle(
    val alignment: ParagraphTextAlignment? = null,
    val lineHeightPx: Float? = null,
    val firstLineIndentPx: Float = 0f,
    val restLineIndentPx: Float = 0f,
    val bullet: TextBullet? = null,
) {
    init {
        lineHeightPx?.let { lineHeight ->
            require(lineHeight.isFinite() && lineHeight > 0f) {
                "lineHeightPx must be finite and greater than zero."
            }
        }
        require(firstLineIndentPx.isFinite() && firstLineIndentPx >= 0f) {
            "firstLineIndentPx must be finite and non-negative."
        }
        require(restLineIndentPx.isFinite() && restLineIndentPx >= 0f) {
            "restLineIndentPx must be finite and non-negative."
        }
    }
}

/** Associates [style] with an ordered, document-bounded UTF-16 [range]. */
data class TextSpanRange(
    /** Ordered range to which [style] applies. */
    val range: TextRange,
    /** Character style applied inside [range]. */
    val style: TextSpanStyle,
)

/** Associates paragraph [style] with an ordered, document-bounded UTF-16 [range]. */
data class ParagraphStyleRange(
    /** Ordered range intersecting the paragraphs to style. */
    val range: TextRange,
    /** Paragraph style applied inside [range]. */
    val style: ParagraphStyle,
)

/**
 * Immutable metadata for one non-text inline attachment.
 *
 * The attachment has value equality across every field. URI resolution, loading, rendering, and
 * accessibility behavior belong to the platform adapter.
 *
 * @property id non-blank stable document-local identity
 * @property mimeType non-blank MIME type
 * @property uri optional external content location
 * @property contentDescription optional accessibility description
 * @property widthPx optional positive intrinsic width in physical pixels
 * @property heightPx optional positive intrinsic height in physical pixels
 */
class InlineTextAttachment(
    val id: String,
    val mimeType: String,
    val uri: String? = null,
    val contentDescription: String? = null,
    val widthPx: Int? = null,
    val heightPx: Int? = null,
    metadata: Map<String, String> = emptyMap(),
) {
    /** Immutable application metadata snapshot. */
    val metadata: Map<String, String> = metadata.toMap()

    init {
        require(id.isNotBlank()) { "Inline attachment id must not be blank." }
        require(mimeType.isNotBlank()) { "Inline attachment MIME type must not be blank." }
        widthPx?.let { require(it > 0) { "Attachment width must be greater than zero." } }
        heightPx?.let { require(it > 0) { "Attachment height must be greater than zero." } }
    }

    /** Compares every attachment field structurally. */
    override fun equals(other: Any?): Boolean {
        return this === other || (
            other is InlineTextAttachment &&
                id == other.id &&
                mimeType == other.mimeType &&
                uri == other.uri &&
                contentDescription == other.contentDescription &&
                widthPx == other.widthPx &&
                heightPx == other.heightPx &&
                metadata == other.metadata
            )
    }

    /** Returns the structural hash of every attachment field. */
    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + mimeType.hashCode()
        result = 31 * result + (uri?.hashCode() ?: 0)
        result = 31 * result + (contentDescription?.hashCode() ?: 0)
        result = 31 * result + (widthPx ?: 0)
        result = 31 * result + (heightPx ?: 0)
        result = 31 * result + metadata.hashCode()
        return result
    }

    /** Returns a concise identity and location description without dumping metadata. */
    override fun toString(): String {
        return "InlineTextAttachment(id=$id, mimeType=$mimeType, uri=$uri)"
    }
}

/** Binds one [attachment] to an object-replacement character at [offset]. */
data class InlineAttachmentRange(
    /** UTF-16 offset of [INLINE_ATTACHMENT_CHARACTER] in the owning document. */
    val offset: Int,
    /** Metadata for the content represented at [offset]. */
    val attachment: InlineTextAttachment,
)

/**
 * Immutable rich-text document containing text, span styles, paragraph styles, and attachments.
 *
 * Offsets and ranges use UTF-16 code units. Style ranges must be ordered and bounded by [text]. Each
 * inline attachment must point to a unique [INLINE_ATTACHMENT_CHARACTER]. Constructor collections
 * are copied and exposed in their supplied order; overlapping style ranges are allowed and resolved
 * by the platform adapter.
 *
 * @sample com.viewcompose.text.samples.richTextDocumentSample
 * @property text plain-text storage, including attachment placeholder characters
 */
class TextDocument(
    val text: String,
    spanStyles: List<TextSpanRange> = emptyList(),
    paragraphStyles: List<ParagraphStyleRange> = emptyList(),
    inlineAttachments: List<InlineAttachmentRange> = emptyList(),
) {
    /** Immutable character-style ranges in caller-supplied order. */
    val spanStyles: List<TextSpanRange> = spanStyles.toList()
    /** Immutable paragraph-style ranges in caller-supplied order. */
    val paragraphStyles: List<ParagraphStyleRange> = paragraphStyles.toList()
    /** Immutable attachment positions in caller-supplied order. */
    val inlineAttachments: List<InlineAttachmentRange> = inlineAttachments.toList()

    /** Whether the document contains no styles or attachments. */
    val isPlainText: Boolean
        get() = spanStyles.isEmpty() &&
            paragraphStyles.isEmpty() &&
            inlineAttachments.isEmpty()

    init {
        this.spanStyles.forEach { span ->
            requireOrderedRange(span.range, "span style")
        }
        this.paragraphStyles.forEach { paragraph ->
            requireOrderedRange(paragraph.range, "paragraph style")
        }
        val occupiedOffsets = HashSet<Int>()
        this.inlineAttachments.forEach { inline ->
            require(inline.offset in text.indices) {
                "Inline attachment offset ${inline.offset} is outside text length ${text.length}."
            }
            require(text[inline.offset] == INLINE_ATTACHMENT_CHARACTER) {
                "Inline attachment at ${inline.offset} must point to " +
                    "INLINE_ATTACHMENT_CHARACTER."
            }
            require(occupiedOffsets.add(inline.offset)) {
                "Only one inline attachment may occupy offset ${inline.offset}."
            }
        }
    }

    /**
     * Replaces ordered [range] with [replacement] while preserving unaffected annotations.
     *
     * Ranges before the replacement remain unchanged, ranges after it shift by the length delta,
     * overlapping ranges retain only uncovered fragments, and replacement annotations shift to the
     * insertion point. Attachments inside the removed range are dropped.
     *
     * @return a new validated document
     * @throws IllegalArgumentException when [range] exceeds this document
     */
    fun replace(
        range: TextRange,
        replacement: TextDocument,
    ): TextDocument {
        require(range.max <= text.length) {
            "Replacement range $range exceeds text length ${text.length}."
        }
        val start = range.min
        val end = range.max
        val replacementLength = replacement.text.length
        val delta = replacementLength - (end - start)
        val nextText = buildString(text.length + delta) {
            append(text, 0, start)
            append(replacement.text)
            append(text, end, text.length)
        }
        // Preserve uncovered styles and shift replacement annotations to their inserted offsets.
        val nextSpans = spanStyles.flatMap { span ->
            preserveRangeAroundReplacement(
                range = span.range,
                start = start,
                end = end,
                delta = delta,
            ).map { preserved ->
                span.copy(range = preserved)
            }
        } + replacement.spanStyles.map { span ->
            span.copy(range = span.range.shift(start))
        }
        val nextParagraphs = paragraphStyles.flatMap { paragraph ->
            preserveRangeAroundReplacement(
                range = paragraph.range,
                start = start,
                end = end,
                delta = delta,
            ).map { preserved ->
                paragraph.copy(range = preserved)
            }
        } + replacement.paragraphStyles.map { paragraph ->
            paragraph.copy(range = paragraph.range.shift(start))
        }
        val nextAttachments = inlineAttachments.mapNotNull { inline ->
            when {
                inline.offset < start -> inline
                inline.offset >= end -> inline.copy(offset = inline.offset + delta)
                else -> null
            }
        } + replacement.inlineAttachments.map { inline ->
            inline.copy(offset = inline.offset + start)
        }
        return TextDocument(
            text = nextText,
            spanStyles = nextSpans,
            paragraphStyles = nextParagraphs,
            inlineAttachments = nextAttachments,
        )
    }

    /** Replaces [range] with unstyled plain [replacement]. */
    fun replace(
        range: TextRange,
        replacement: CharSequence,
    ): TextDocument {
        return replace(
            range = range,
            replacement = plain(replacement.toString()),
        )
    }

    /** Appends [other], shifting all of its annotations by this document's length. */
    fun append(other: TextDocument): TextDocument {
        return replace(
            range = TextRange(text.length),
            replacement = other,
        )
    }

    /** Compares text and every annotation collection structurally. */
    override fun equals(other: Any?): Boolean {
        return this === other || (
            other is TextDocument &&
                text == other.text &&
                spanStyles == other.spanStyles &&
                paragraphStyles == other.paragraphStyles &&
                inlineAttachments == other.inlineAttachments
            )
    }

    /** Returns the structural hash of text and annotation collections. */
    override fun hashCode(): Int {
        var result = text.hashCode()
        result = 31 * result + spanStyles.hashCode()
        result = 31 * result + paragraphStyles.hashCode()
        result = 31 * result + inlineAttachments.hashCode()
        return result
    }

    /** Returns text plus annotation counts for diagnostics. */
    override fun toString(): String {
        return "TextDocument(text=$text, spans=${spanStyles.size}, " +
            "paragraphs=${paragraphStyles.size}, attachments=${inlineAttachments.size})"
    }

    private fun requireOrderedRange(
        range: TextRange,
        label: String,
    ) {
        require(range.start <= range.end) {
            "TextDocument $label range must be ordered, but was $range."
        }
        require(range.end <= text.length) {
            "TextDocument $label range $range exceeds text length ${text.length}."
        }
    }

    /** Plain-text constructors and shared constants. */
    companion object {
        /** Shared empty document. */
        val Empty: TextDocument = TextDocument("")

        /** Returns an unstyled document, reusing [Empty] when [text] is empty. */
        fun plain(text: String): TextDocument {
            return if (text.isEmpty()) Empty else TextDocument(text)
        }
    }
}

/**
 * Mutable builder for incrementally creating one validated [TextDocument].
 *
 * Appended documents retain their annotations after offset translation. Validation occurs when
 * [build] constructs the immutable document.
 */
class TextDocumentBuilder {
    private val text = StringBuilder()
    private val spanStyles = mutableListOf<TextSpanRange>()
    private val paragraphStyles = mutableListOf<ParagraphStyleRange>()
    private val inlineAttachments = mutableListOf<InlineAttachmentRange>()

    /** Current text length in UTF-16 code units. */
    val length: Int
        get() = text.length

    /** Appends unstyled [value]. */
    fun append(value: CharSequence): TextDocumentBuilder = apply {
        text.append(value)
    }

    /** Appends [value] and records [style] over its non-empty range. */
    fun append(
        value: CharSequence,
        style: TextSpanStyle,
    ): TextDocumentBuilder = apply {
        val start = text.length
        text.append(value)
        if (text.length > start) {
            spanStyles += TextSpanRange(
                range = TextRange(start, text.length),
                style = style,
            )
        }
    }

    /** Appends [document], translating all annotation offsets. */
    fun append(document: TextDocument): TextDocumentBuilder = apply {
        val offset = text.length
        text.append(document.text)
        spanStyles += document.spanStyles.map { span ->
            span.copy(range = span.range.shift(offset))
        }
        paragraphStyles += document.paragraphStyles.map { paragraph ->
            paragraph.copy(range = paragraph.range.shift(offset))
        }
        inlineAttachments += document.inlineAttachments.map { inline ->
            inline.copy(offset = inline.offset + offset)
        }
    }

    /** Appends an object-replacement character bound to [attachment]. */
    fun appendAttachment(
        attachment: InlineTextAttachment,
    ): TextDocumentBuilder = apply {
        val offset = text.length
        text.append(INLINE_ATTACHMENT_CHARACTER)
        inlineAttachments += InlineAttachmentRange(
            offset = offset,
            attachment = attachment,
        )
    }

    /** Records character [style] for [range]; bounds are validated by [build]. */
    fun addSpan(
        range: TextRange,
        style: TextSpanStyle,
    ): TextDocumentBuilder = apply {
        spanStyles += TextSpanRange(range, style)
    }

    /** Records paragraph [style] for [range]; bounds are validated by [build]. */
    fun addParagraphStyle(
        range: TextRange,
        style: ParagraphStyle,
    ): TextDocumentBuilder = apply {
        paragraphStyles += ParagraphStyleRange(range, style)
    }

    /** Returns a validated immutable snapshot of the current builder content. */
    fun build(): TextDocument {
        return TextDocument(
            text = text.toString(),
            spanStyles = spanStyles,
            paragraphStyles = paragraphStyles,
            inlineAttachments = inlineAttachments,
        )
    }
}

/**
 * Builds a validated rich-text document with [block].
 *
 * @sample com.viewcompose.text.samples.richTextDocumentSample
 */
fun textDocument(
    block: TextDocumentBuilder.() -> Unit,
): TextDocument {
    return TextDocumentBuilder()
        .apply(block)
        .build()
}

private fun TextRange.shift(offset: Int): TextRange {
    return TextRange(
        start = start + offset,
        end = end + offset,
    )
}

/** Calculates preserved fragments of an existing style range after one replacement. */
private fun preserveRangeAroundReplacement(
    range: TextRange,
    start: Int,
    end: Int,
    delta: Int,
): List<TextRange> {
    if (start == end) {
        return when {
            range.end <= start -> listOf(range)
            range.start >= start -> listOf(range.shift(delta))
            else -> listOf(
                TextRange(range.start, start),
                TextRange(start + delta, range.end + delta),
            ).filterNot { it.collapsed }
        }
    }
    if (range.end <= start) return listOf(range)
    if (range.start >= end) return listOf(range.shift(delta))
    return buildList {
        if (range.start < start) {
            add(TextRange(range.start, start))
        }
        if (range.end > end) {
            add(TextRange(start + (end - start) + delta, range.end + delta))
        }
    }
}
