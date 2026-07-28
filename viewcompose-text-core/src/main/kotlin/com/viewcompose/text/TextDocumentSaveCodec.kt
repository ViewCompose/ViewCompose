package com.viewcompose.text

/**
 * [TextDocument] 的 saveable 编解码器。
 * Saveable encoder/decoder for [TextDocument].
 */
object TextDocumentSaveCodec {
    /**
     * 将文档编码为只包含基础类型、List 和 Map 的结构。
     * Encodes a document into a structure made of primitives, Lists, and Maps only.
     */
    fun encode(document: TextDocument): Map<String, Any?> {
        return mapOf(
            KEY_VERSION to FORMAT_VERSION,
            KEY_TEXT to document.text,
            KEY_SPANS to document.spanStyles.map(::encodeSpan),
            KEY_PARAGRAPHS to document.paragraphStyles.map(::encodeParagraph),
            KEY_ATTACHMENTS to document.inlineAttachments.map(::encodeAttachment),
        )
    }

    /**
     * 从保存结构恢复文档；格式不兼容时抛出明确错误。
     * Restores a document from saved structure and fails clearly on incompatible format.
     */
    fun decode(saved: Any?): TextDocument {
        val root = saved.stringKeyMap("TextDocument")
        require(root[KEY_VERSION].intValue(KEY_VERSION) == FORMAT_VERSION) {
            "Unsupported TextDocument format version: ${root[KEY_VERSION]}."
        }
        val text = root[KEY_TEXT] as? String
            ?: error("Saved TextDocument text must be a String.")
        return TextDocument(
            text = text,
            spanStyles = root.listValue(KEY_SPANS).map(::decodeSpan),
            paragraphStyles = root.listValue(KEY_PARAGRAPHS).map(::decodeParagraph),
            inlineAttachments = root.listValue(KEY_ATTACHMENTS).map(::decodeAttachment),
        )
    }

    private fun encodeSpan(span: TextSpanRange): Map<String, Any?> {
        return buildMap {
            putRange(span.range)
            span.style.color?.let { put(KEY_COLOR, it) }
            span.style.backgroundColor?.let { put(KEY_BACKGROUND_COLOR, it) }
            span.style.fontWeight?.let { put(KEY_FONT_WEIGHT, it) }
            span.style.fontStyle?.let { put(KEY_FONT_STYLE, it.name) }
            span.style.relativeSize?.let { put(KEY_RELATIVE_SIZE, it) }
            put(KEY_UNDERLINE, span.style.underline)
            put(KEY_LINE_THROUGH, span.style.lineThrough)
            put(KEY_VERTICAL_ALIGNMENT, span.style.verticalAlignment.name)
            span.style.link?.let { put(KEY_LINK, it) }
        }
    }

    private fun decodeSpan(saved: Any?): TextSpanRange {
        val map = saved.stringKeyMap("Text span")
        return TextSpanRange(
            range = map.decodeRange(),
            style = TextSpanStyle(
                color = map.optionalInt(KEY_COLOR),
                backgroundColor = map.optionalInt(KEY_BACKGROUND_COLOR),
                fontWeight = map.optionalInt(KEY_FONT_WEIGHT),
                fontStyle = map.optionalEnum<TextFontStyle>(KEY_FONT_STYLE),
                relativeSize = map.optionalFloat(KEY_RELATIVE_SIZE),
                underline = map.optionalBoolean(KEY_UNDERLINE) ?: false,
                lineThrough = map.optionalBoolean(KEY_LINE_THROUGH) ?: false,
                verticalAlignment =
                    map.optionalEnum<TextVerticalAlignment>(KEY_VERTICAL_ALIGNMENT)
                        ?: TextVerticalAlignment.Normal,
                link = map[KEY_LINK] as? String,
            ),
        )
    }

    private fun encodeParagraph(paragraph: ParagraphStyleRange): Map<String, Any?> {
        return buildMap {
            putRange(paragraph.range)
            paragraph.style.alignment?.let { put(KEY_ALIGNMENT, it.name) }
            paragraph.style.lineHeightPx?.let { put(KEY_LINE_HEIGHT, it) }
            put(KEY_FIRST_LINE_INDENT, paragraph.style.firstLineIndentPx)
            put(KEY_REST_LINE_INDENT, paragraph.style.restLineIndentPx)
            paragraph.style.bullet?.let { bullet ->
                put(
                    KEY_BULLET,
                    buildMap<String, Any?> {
                        put(KEY_RADIUS, bullet.radiusPx)
                        put(KEY_GAP_WIDTH, bullet.gapWidthPx)
                        bullet.color?.let { put(KEY_COLOR, it) }
                    },
                )
            }
        }
    }

    private fun decodeParagraph(saved: Any?): ParagraphStyleRange {
        val map = saved.stringKeyMap("Paragraph style")
        val bullet = map[KEY_BULLET]?.stringKeyMap("Paragraph bullet")?.let { bulletMap ->
            TextBullet(
                radiusPx = bulletMap[KEY_RADIUS].floatValue(KEY_RADIUS),
                gapWidthPx = bulletMap[KEY_GAP_WIDTH].floatValue(KEY_GAP_WIDTH),
                color = bulletMap.optionalInt(KEY_COLOR),
            )
        }
        return ParagraphStyleRange(
            range = map.decodeRange(),
            style = ParagraphStyle(
                alignment = map.optionalEnum<ParagraphTextAlignment>(KEY_ALIGNMENT),
                lineHeightPx = map.optionalFloat(KEY_LINE_HEIGHT),
                firstLineIndentPx = map[KEY_FIRST_LINE_INDENT].floatValue(KEY_FIRST_LINE_INDENT),
                restLineIndentPx = map[KEY_REST_LINE_INDENT].floatValue(KEY_REST_LINE_INDENT),
                bullet = bullet,
            ),
        )
    }

    private fun encodeAttachment(inline: InlineAttachmentRange): Map<String, Any?> {
        return buildMap {
            put(KEY_OFFSET, inline.offset)
            put(KEY_ID, inline.attachment.id)
            put(KEY_MIME_TYPE, inline.attachment.mimeType)
            inline.attachment.uri?.let { put(KEY_URI, it) }
            inline.attachment.contentDescription?.let { put(KEY_CONTENT_DESCRIPTION, it) }
            inline.attachment.widthPx?.let { put(KEY_WIDTH, it) }
            inline.attachment.heightPx?.let { put(KEY_HEIGHT, it) }
            put(KEY_METADATA, inline.attachment.metadata)
        }
    }

    private fun decodeAttachment(saved: Any?): InlineAttachmentRange {
        val map = saved.stringKeyMap("Inline attachment")
        val metadata = map[KEY_METADATA]?.stringKeyMap("Attachment metadata")
            ?.mapValues { (_, value) ->
                value as? String ?: error("Attachment metadata values must be Strings.")
            }
            .orEmpty()
        return InlineAttachmentRange(
            offset = map[KEY_OFFSET].intValue(KEY_OFFSET),
            attachment = InlineTextAttachment(
                id = map[KEY_ID] as? String
                    ?: error("Attachment id must be a String."),
                mimeType = map[KEY_MIME_TYPE] as? String
                    ?: error("Attachment MIME type must be a String."),
                uri = map[KEY_URI] as? String,
                contentDescription = map[KEY_CONTENT_DESCRIPTION] as? String,
                widthPx = map.optionalInt(KEY_WIDTH),
                heightPx = map.optionalInt(KEY_HEIGHT),
                metadata = metadata,
            ),
        )
    }

    private fun MutableMap<String, Any?>.putRange(range: TextRange) {
        put(KEY_START, range.start)
        put(KEY_END, range.end)
    }

    private fun Map<String, Any?>.decodeRange(): TextRange {
        return TextRange(
            start = this[KEY_START].intValue(KEY_START),
            end = this[KEY_END].intValue(KEY_END),
        )
    }

    private fun Map<String, Any?>.listValue(key: String): List<Any?> {
        return this[key] as? List<*>
            ?: error("Saved TextDocument $key must be a List.")
    }

    private fun Map<String, Any?>.optionalInt(key: String): Int? {
        return this[key]?.intValue(key)
    }

    private fun Map<String, Any?>.optionalFloat(key: String): Float? {
        return this[key]?.floatValue(key)
    }

    private fun Map<String, Any?>.optionalBoolean(key: String): Boolean? {
        val value = this[key] ?: return null
        return value as? Boolean
            ?: error("Saved TextDocument $key must be a Boolean.")
    }

    private inline fun <reified T : Enum<T>> Map<String, Any?>.optionalEnum(
        key: String,
    ): T? {
        val value = this[key] ?: return null
        val name = value as? String
            ?: error("Saved TextDocument $key must be a String.")
        return enumValues<T>().firstOrNull { it.name == name }
            ?: error("Unknown ${T::class.simpleName} value: $name.")
    }

    private fun Any?.intValue(label: String): Int {
        return (this as? Number)?.toInt()
            ?: error("Saved TextDocument $label must be a Number.")
    }

    private fun Any?.floatValue(label: String): Float {
        return (this as? Number)?.toFloat()
            ?: error("Saved TextDocument $label must be a Number.")
    }

    /**
     * 将平台恢复出的 Map 校验为 String key Map，避免后续解码出现模糊 ClassCastException。
     * Validates a platform-restored Map as a String-key Map to avoid vague ClassCastExceptions later.
     */
    private fun Any?.stringKeyMap(label: String): Map<String, Any?> {
        val map = this as? Map<*, *>
            ?: error("$label must be a Map.")
        return buildMap {
            map.forEach { (key, value) ->
                require(key is String) { "$label keys must be Strings." }
                put(key, value)
            }
        }
    }

    private const val FORMAT_VERSION = 1
    private const val KEY_VERSION = "version"
    private const val KEY_TEXT = "text"
    private const val KEY_SPANS = "spans"
    private const val KEY_PARAGRAPHS = "paragraphs"
    private const val KEY_ATTACHMENTS = "attachments"
    private const val KEY_START = "start"
    private const val KEY_END = "end"
    private const val KEY_COLOR = "color"
    private const val KEY_BACKGROUND_COLOR = "backgroundColor"
    private const val KEY_FONT_WEIGHT = "fontWeight"
    private const val KEY_FONT_STYLE = "fontStyle"
    private const val KEY_RELATIVE_SIZE = "relativeSize"
    private const val KEY_UNDERLINE = "underline"
    private const val KEY_LINE_THROUGH = "lineThrough"
    private const val KEY_VERTICAL_ALIGNMENT = "verticalAlignment"
    private const val KEY_LINK = "link"
    private const val KEY_ALIGNMENT = "alignment"
    private const val KEY_LINE_HEIGHT = "lineHeight"
    private const val KEY_FIRST_LINE_INDENT = "firstLineIndent"
    private const val KEY_REST_LINE_INDENT = "restLineIndent"
    private const val KEY_BULLET = "bullet"
    private const val KEY_RADIUS = "radius"
    private const val KEY_GAP_WIDTH = "gapWidth"
    private const val KEY_OFFSET = "offset"
    private const val KEY_ID = "id"
    private const val KEY_MIME_TYPE = "mimeType"
    private const val KEY_URI = "uri"
    private const val KEY_CONTENT_DESCRIPTION = "contentDescription"
    private const val KEY_WIDTH = "width"
    private const val KEY_HEIGHT = "height"
    private const val KEY_METADATA = "metadata"
}
