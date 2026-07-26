package com.viewcompose.text

const val INLINE_ATTACHMENT_CHARACTER: Char = '\uFFFC'

enum class TextFontStyle {
    Normal,
    Italic,
}

enum class TextVerticalAlignment {
    Normal,
    Superscript,
    Subscript,
}

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

enum class ParagraphTextAlignment {
    Start,
    Center,
    End,
    Justify,
}

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

data class TextSpanRange(
    val range: TextRange,
    val style: TextSpanStyle,
)

data class ParagraphStyleRange(
    val range: TextRange,
    val style: ParagraphStyle,
)

class InlineTextAttachment(
    val id: String,
    val mimeType: String,
    val uri: String? = null,
    val contentDescription: String? = null,
    val widthPx: Int? = null,
    val heightPx: Int? = null,
    metadata: Map<String, String> = emptyMap(),
) {
    val metadata: Map<String, String> = metadata.toMap()

    init {
        require(id.isNotBlank()) { "Inline attachment id must not be blank." }
        require(mimeType.isNotBlank()) { "Inline attachment MIME type must not be blank." }
        widthPx?.let { require(it > 0) { "Attachment width must be greater than zero." } }
        heightPx?.let { require(it > 0) { "Attachment height must be greater than zero." } }
    }

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

    override fun toString(): String {
        return "InlineTextAttachment(id=$id, mimeType=$mimeType, uri=$uri)"
    }
}

data class InlineAttachmentRange(
    val offset: Int,
    val attachment: InlineTextAttachment,
)

class TextDocument(
    val text: String,
    spanStyles: List<TextSpanRange> = emptyList(),
    paragraphStyles: List<ParagraphStyleRange> = emptyList(),
    inlineAttachments: List<InlineAttachmentRange> = emptyList(),
) {
    val spanStyles: List<TextSpanRange> = spanStyles.toList()
    val paragraphStyles: List<ParagraphStyleRange> = paragraphStyles.toList()
    val inlineAttachments: List<InlineAttachmentRange> = inlineAttachments.toList()

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

    fun replace(
        range: TextRange,
        replacement: CharSequence,
    ): TextDocument {
        return replace(
            range = range,
            replacement = plain(replacement.toString()),
        )
    }

    fun append(other: TextDocument): TextDocument {
        return replace(
            range = TextRange(text.length),
            replacement = other,
        )
    }

    override fun equals(other: Any?): Boolean {
        return this === other || (
            other is TextDocument &&
                text == other.text &&
                spanStyles == other.spanStyles &&
                paragraphStyles == other.paragraphStyles &&
                inlineAttachments == other.inlineAttachments
            )
    }

    override fun hashCode(): Int {
        var result = text.hashCode()
        result = 31 * result + spanStyles.hashCode()
        result = 31 * result + paragraphStyles.hashCode()
        result = 31 * result + inlineAttachments.hashCode()
        return result
    }

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

    companion object {
        val Empty: TextDocument = TextDocument("")

        fun plain(text: String): TextDocument {
            return if (text.isEmpty()) Empty else TextDocument(text)
        }
    }
}

class TextDocumentBuilder {
    private val text = StringBuilder()
    private val spanStyles = mutableListOf<TextSpanRange>()
    private val paragraphStyles = mutableListOf<ParagraphStyleRange>()
    private val inlineAttachments = mutableListOf<InlineAttachmentRange>()

    val length: Int
        get() = text.length

    fun append(value: CharSequence): TextDocumentBuilder = apply {
        text.append(value)
    }

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

    fun addSpan(
        range: TextRange,
        style: TextSpanStyle,
    ): TextDocumentBuilder = apply {
        spanStyles += TextSpanRange(range, style)
    }

    fun addParagraphStyle(
        range: TextRange,
        style: ParagraphStyle,
    ): TextDocumentBuilder = apply {
        paragraphStyles += ParagraphStyleRange(range, style)
    }

    fun build(): TextDocument {
        return TextDocument(
            text = text.toString(),
            spanStyles = spanStyles,
            paragraphStyles = paragraphStyles,
            inlineAttachments = inlineAttachments,
        )
    }
}

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
