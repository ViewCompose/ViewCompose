package com.viewcompose.renderer.view.tree

import android.content.ClipData
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.net.Uri
import android.text.Editable
import android.text.Layout
import android.text.Spannable
import android.text.SpannableString
import android.text.Spanned
import android.text.TextPaint
import android.text.style.AlignmentSpan
import android.text.style.BackgroundColorSpan
import android.text.style.BulletSpan
import android.text.style.CharacterStyle
import android.text.style.ForegroundColorSpan
import android.text.style.ImageSpan
import android.text.style.LeadingMarginSpan
import android.text.style.LineHeightSpan
import android.text.style.MetricAffectingSpan
import android.text.style.RelativeSizeSpan
import android.text.style.ReplacementSpan
import android.text.style.StrikethroughSpan
import android.text.style.StyleSpan
import android.text.style.SubscriptSpan
import android.text.style.SuperscriptSpan
import android.text.style.UnderlineSpan
import android.text.style.URLSpan
import android.widget.TextView
import androidx.core.view.ContentInfoCompat
import com.viewcompose.text.InlineAttachmentRange
import com.viewcompose.text.InlineTextAttachment
import com.viewcompose.text.ParagraphStyle
import com.viewcompose.text.ParagraphStyleRange
import com.viewcompose.text.ParagraphTextAlignment
import com.viewcompose.text.ReceiveContentSource
import com.viewcompose.text.TextBullet
import com.viewcompose.text.TextDocument
import com.viewcompose.text.TextFontStyle
import com.viewcompose.text.TextRange
import com.viewcompose.text.TextSpanRange
import com.viewcompose.text.TextSpanStyle
import com.viewcompose.text.TextVerticalAlignment
import kotlin.math.ceil
import kotlin.math.roundToInt

internal object AndroidTextDocumentAdapter {
    fun toCharSequence(
        view: TextView,
        document: TextDocument,
    ): CharSequence {
        return SpannableString(document.text).also { spannable ->
            applyDocumentSpans(
                context = view.context,
                spannable = spannable,
                document = document,
            )
        }
    }

    fun applyToEditable(
        view: TextView,
        editable: Editable,
        document: TextDocument,
    ) {
        editable.getSpans(0, editable.length, DocumentPlatformSpan::class.java)
            .forEach(editable::removeSpan)
        applyDocumentSpans(
            context = view.context,
            spannable = editable,
            document = document,
        )
    }

    fun fromCharSequence(value: CharSequence): TextDocument {
        val spanned = value as? Spanned ?: return TextDocument.plain(value.toString())
        val text = value.toString()
        val styles = mutableListOf<TextSpanRange>()
        val paragraphs = mutableListOf<ParagraphStyleRange>()
        val attachments = mutableListOf<InlineAttachmentRange>()
        spanned.getSpans(0, spanned.length, Any::class.java).forEach { span ->
            val start = spanned.getSpanStart(span)
            val end = spanned.getSpanEnd(span)
            if (start < 0 || end < start || end > text.length) return@forEach
            val range = TextRange(start, end)
            when (span) {
                is DocumentTextStyleSpan -> addOrMergeStyle(styles, range, span.style)
                is DocumentUrlSpan -> addOrMergeStyle(
                    styles,
                    range,
                    TextSpanStyle(link = span.url),
                )
                is DocumentParagraphSpan -> paragraphs += ParagraphStyleRange(
                    range = range,
                    style = span.style,
                )
                is DocumentAttachmentSpan -> attachments += InlineAttachmentRange(
                    offset = start,
                    attachment = span.attachment,
                )
                is ForegroundColorSpan -> addOrMergeStyle(
                    styles,
                    range,
                    TextSpanStyle(color = span.foregroundColor),
                )
                is BackgroundColorSpan -> addOrMergeStyle(
                    styles,
                    range,
                    TextSpanStyle(backgroundColor = span.backgroundColor),
                )
                is StyleSpan -> addOrMergeStyle(
                    styles,
                    range,
                    TextSpanStyle(
                        fontWeight = if (span.style and Typeface.BOLD != 0) 700 else null,
                        fontStyle = if (span.style and Typeface.ITALIC != 0) {
                            TextFontStyle.Italic
                        } else {
                            null
                        },
                    ),
                )
                is RelativeSizeSpan -> addOrMergeStyle(
                    styles,
                    range,
                    TextSpanStyle(relativeSize = span.sizeChange),
                )
                is UnderlineSpan -> addOrMergeStyle(
                    styles,
                    range,
                    TextSpanStyle(underline = true),
                )
                is StrikethroughSpan -> addOrMergeStyle(
                    styles,
                    range,
                    TextSpanStyle(lineThrough = true),
                )
                is SuperscriptSpan -> addOrMergeStyle(
                    styles,
                    range,
                    TextSpanStyle(verticalAlignment = TextVerticalAlignment.Superscript),
                )
                is SubscriptSpan -> addOrMergeStyle(
                    styles,
                    range,
                    TextSpanStyle(verticalAlignment = TextVerticalAlignment.Subscript),
                )
                is URLSpan -> addOrMergeStyle(
                    styles,
                    range,
                    TextSpanStyle(link = span.url),
                )
                is AlignmentSpan -> paragraphs += ParagraphStyleRange(
                    range = range,
                    style = ParagraphStyle(
                        alignment = span.alignment.toDocumentAlignment(),
                    ),
                )
                is BulletSpan -> paragraphs += ParagraphStyleRange(
                    range = range,
                    style = ParagraphStyle(
                        bullet = TextBullet(),
                    ),
                )
                is LeadingMarginSpan -> paragraphs += ParagraphStyleRange(
                    range = range,
                    style = ParagraphStyle(
                        firstLineIndentPx = span.getLeadingMargin(true).toFloat(),
                        restLineIndentPx = span.getLeadingMargin(false).toFloat(),
                    ),
                )
                is ImageSpan -> {
                    if (start < end && start in text.indices) {
                        attachments += InlineAttachmentRange(
                            offset = start,
                            attachment = InlineTextAttachment(
                                id = span.source ?: "platform-image@$start",
                                mimeType = "image/*",
                                uri = span.source,
                            ),
                        )
                    }
                }
            }
        }
        return TextDocument(
            text = text,
            spanStyles = styles,
            paragraphStyles = paragraphs,
            inlineAttachments = attachments.distinctBy(InlineAttachmentRange::offset),
        )
    }

    fun convertContent(
        context: Context,
        payload: ContentInfoCompat,
    ): ConvertedPlatformContent {
        val clip = payload.clip
        val accepted = mutableListOf<TextDocument>()
        val consumedIndices = mutableSetOf<Int>()
        for (index in 0 until clip.itemCount) {
            val item = clip.getItemAt(index)
            val itemDocument = when {
                item.uri != null -> attachmentDocument(
                    context = context,
                    clip = clip,
                    uri = item.uri,
                    index = index,
                )
                item.text != null || item.htmlText != null -> {
                    fromCharSequence(item.coerceToStyledText(context))
                }
                else -> null
            }
            if (itemDocument != null) {
                accepted += itemDocument
                consumedIndices += index
            }
        }
        val document = accepted.reduceOrNull { result, item ->
            result
                .append(TextDocument.plain("\n"))
                .append(item)
        }
        return ConvertedPlatformContent(
            document = document,
            consumedIndices = consumedIndices,
            source = payload.source.toDocumentSource(),
            mimeTypes = buildSet {
                for (index in 0 until clip.description.mimeTypeCount) {
                    add(clip.description.getMimeType(index))
                }
            },
            platformItemCount = clip.itemCount,
        )
    }

    fun remainingContent(
        payload: ContentInfoCompat,
        consumedIndices: Set<Int>,
    ): ContentInfoCompat? {
        if (consumedIndices.isEmpty()) return payload
        val clip = payload.clip
        val remainingItems = buildList {
            for (index in 0 until clip.itemCount) {
                if (index !in consumedIndices) {
                    add(clip.getItemAt(index))
                }
            }
        }
        if (remainingItems.isEmpty()) return null
        val remainingClip = ClipData(
            clip.description,
            remainingItems.first(),
        ).apply {
            remainingItems.drop(1).forEach(::addItem)
        }
        return ContentInfoCompat.Builder(remainingClip, payload.source)
            .setFlags(payload.flags)
            .setLinkUri(payload.linkUri)
            .setExtras(payload.extras)
            .build()
    }

    private fun applyDocumentSpans(
        context: Context,
        spannable: Spannable,
        document: TextDocument,
    ) {
        document.spanStyles.forEach { span ->
            if (!span.range.collapsed) {
                spannable.setSpan(
                    DocumentTextStyleSpan(span.style),
                    span.range.start,
                    span.range.end,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
                )
                span.style.link?.let { link ->
                    spannable.setSpan(
                        DocumentUrlSpan(link),
                        span.range.start,
                        span.range.end,
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
                    )
                }
            }
        }
        document.paragraphStyles.forEach { paragraph ->
            if (!paragraph.range.collapsed) {
                spannable.setSpan(
                    DocumentParagraphSpan(paragraph.style),
                    paragraph.range.start,
                    paragraph.range.end,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
                )
            }
        }
        document.inlineAttachments.forEach { inline ->
            spannable.setSpan(
                DocumentAttachmentSpan(
                    context = context,
                    attachment = inline.attachment,
                ),
                inline.offset,
                inline.offset + 1,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
            )
        }
    }

    private fun attachmentDocument(
        context: Context,
        clip: ClipData,
        uri: Uri,
        index: Int,
    ): TextDocument {
        val mimeType = context.contentResolver.getType(uri)
            ?: clip.description.run {
                (0 until mimeTypeCount)
                    .map(::getMimeType)
                    .firstOrNull { candidate -> candidate != "text/plain" }
            }
            ?: "application/octet-stream"
        val attachment = InlineTextAttachment(
            id = "${uri}#$index",
            mimeType = mimeType,
            uri = uri.toString(),
        )
        return com.viewcompose.text.textDocument {
            appendAttachment(attachment)
        }
    }

    private fun addOrMergeStyle(
        styles: MutableList<TextSpanRange>,
        range: TextRange,
        next: TextSpanStyle,
    ) {
        val existingIndex = styles.indexOfLast { it.range == range }
        if (existingIndex < 0) {
            styles += TextSpanRange(range, next)
            return
        }
        val existing = styles[existingIndex]
        styles[existingIndex] = existing.copy(
            style = existing.style.merge(next),
        )
    }

    private fun TextSpanStyle.merge(other: TextSpanStyle): TextSpanStyle {
        return TextSpanStyle(
            color = other.color ?: color,
            backgroundColor = other.backgroundColor ?: backgroundColor,
            fontWeight = other.fontWeight ?: fontWeight,
            fontStyle = other.fontStyle ?: fontStyle,
            relativeSize = other.relativeSize ?: relativeSize,
            underline = underline || other.underline,
            lineThrough = lineThrough || other.lineThrough,
            verticalAlignment = if (other.verticalAlignment != TextVerticalAlignment.Normal) {
                other.verticalAlignment
            } else {
                verticalAlignment
            },
            link = other.link ?: link,
        )
    }
}

internal data class ConvertedPlatformContent(
    val document: TextDocument?,
    val consumedIndices: Set<Int>,
    val source: ReceiveContentSource,
    val mimeTypes: Set<String>,
    val platformItemCount: Int,
)

private interface DocumentPlatformSpan

private class DocumentTextStyleSpan(
    val style: TextSpanStyle,
) : MetricAffectingSpan(), DocumentPlatformSpan {
    override fun updateDrawState(textPaint: TextPaint) {
        applyMetrics(textPaint)
        style.color?.let { textPaint.color = it }
        style.backgroundColor?.let { textPaint.bgColor = it }
        if (style.underline) textPaint.isUnderlineText = true
        if (style.lineThrough) textPaint.isStrikeThruText = true
    }

    override fun updateMeasureState(textPaint: TextPaint) {
        applyMetrics(textPaint)
    }

    private fun applyMetrics(textPaint: TextPaint) {
        style.relativeSize?.let { textPaint.textSize *= it }
        if (style.fontWeight != null || style.fontStyle != null) {
            val currentStyle = textPaint.typeface?.style ?: Typeface.NORMAL
            val bold = style.fontWeight?.let { it >= 600 }
                ?: (currentStyle and Typeface.BOLD != 0)
            val italic = when (style.fontStyle) {
                TextFontStyle.Italic -> true
                TextFontStyle.Normal -> false
                null -> currentStyle and Typeface.ITALIC != 0
            }
            val resolvedStyle =
                (if (bold) Typeface.BOLD else Typeface.NORMAL) or
                    (if (italic) Typeface.ITALIC else Typeface.NORMAL)
            textPaint.typeface = Typeface.create(textPaint.typeface, resolvedStyle)
        }
        when (style.verticalAlignment) {
            TextVerticalAlignment.Normal -> Unit
            TextVerticalAlignment.Superscript -> {
                textPaint.baselineShift += (textPaint.ascent() * 0.5f).roundToInt()
            }
            TextVerticalAlignment.Subscript -> {
                textPaint.baselineShift -= (textPaint.ascent() * 0.25f).roundToInt()
            }
        }
    }
}

private class DocumentUrlSpan(
    url: String,
) : URLSpan(url), DocumentPlatformSpan

private class DocumentParagraphSpan(
    val style: ParagraphStyle,
) : AlignmentSpan, LeadingMarginSpan, LineHeightSpan, DocumentPlatformSpan {
    override fun getAlignment(): Layout.Alignment {
        return when (style.alignment) {
            ParagraphTextAlignment.Center -> Layout.Alignment.ALIGN_CENTER
            ParagraphTextAlignment.End -> Layout.Alignment.ALIGN_OPPOSITE
            ParagraphTextAlignment.Start,
            ParagraphTextAlignment.Justify,
            null,
            -> Layout.Alignment.ALIGN_NORMAL
        }
    }

    override fun getLeadingMargin(first: Boolean): Int {
        val indent = if (first) style.firstLineIndentPx else style.restLineIndentPx
        val bulletWidth = style.bullet?.let { bullet ->
            bullet.radiusPx * 2f + bullet.gapWidthPx
        } ?: 0f
        return ceil(indent + bulletWidth).toInt()
    }

    override fun drawLeadingMargin(
        canvas: Canvas,
        paint: Paint,
        x: Int,
        dir: Int,
        top: Int,
        baseline: Int,
        bottom: Int,
        text: CharSequence,
        start: Int,
        end: Int,
        first: Boolean,
        layout: Layout,
    ) {
        val bullet = style.bullet ?: return
        if (!first) return
        val previousColor = paint.color
        val previousStyle = paint.style
        paint.color = bullet.color ?: previousColor
        paint.style = Paint.Style.FILL
        val centerX = x + dir * (style.firstLineIndentPx + bullet.radiusPx)
        canvas.drawCircle(
            centerX,
            (top + bottom) * 0.5f,
            bullet.radiusPx,
            paint,
        )
        paint.color = previousColor
        paint.style = previousStyle
    }

    override fun chooseHeight(
        text: CharSequence,
        start: Int,
        end: Int,
        spanstartv: Int,
        v: Int,
        fm: Paint.FontMetricsInt,
    ) {
        val target = style.lineHeightPx?.roundToInt() ?: return
        val current = fm.descent - fm.ascent
        val adjustment = target - current
        fm.descent += adjustment
        fm.bottom += adjustment
    }
}

private class DocumentAttachmentSpan(
    private val context: Context,
    val attachment: InlineTextAttachment,
) : ReplacementSpan(), DocumentPlatformSpan {
    private val drawable: Drawable? by lazy(::loadDrawable)

    override fun getSize(
        paint: Paint,
        text: CharSequence?,
        start: Int,
        end: Int,
        fm: Paint.FontMetricsInt?,
    ): Int {
        val height = resolvedHeight(paint)
        val width = resolvedWidth(height)
        fm?.let { metrics ->
            val bottom = paint.fontMetricsInt.descent
            metrics.ascent = bottom - height
            metrics.top = metrics.ascent
            metrics.descent = bottom
            metrics.bottom = bottom
        }
        return width
    }

    override fun draw(
        canvas: Canvas,
        text: CharSequence?,
        start: Int,
        end: Int,
        x: Float,
        top: Int,
        y: Int,
        bottom: Int,
        paint: Paint,
    ) {
        val height = resolvedHeight(paint)
        val width = resolvedWidth(height)
        val drawBottom = y + paint.fontMetricsInt.descent
        val drawTop = drawBottom - height
        val resolvedDrawable = drawable
        if (resolvedDrawable != null) {
            canvas.save()
            canvas.translate(x, drawTop.toFloat())
            resolvedDrawable.setBounds(0, 0, width, height)
            resolvedDrawable.draw(canvas)
            canvas.restore()
            return
        }
        val previousColor = paint.color
        val previousStyle = paint.style
        paint.color = ATTACHMENT_PLACEHOLDER_COLOR
        paint.style = Paint.Style.FILL
        canvas.drawRoundRect(
            RectF(x, drawTop.toFloat(), x + width, drawBottom.toFloat()),
            height * 0.18f,
            height * 0.18f,
            paint,
        )
        paint.color = previousColor
        paint.style = previousStyle
    }

    private fun resolvedHeight(paint: Paint): Int {
        return attachment.heightPx
            ?: ceil(paint.textSize * 1.2f).toInt().coerceAtLeast(1)
    }

    private fun resolvedWidth(height: Int): Int {
        attachment.widthPx?.let { return it }
        val resolvedDrawable = drawable ?: return height
        val intrinsicWidth = resolvedDrawable.intrinsicWidth.coerceAtLeast(1)
        val intrinsicHeight = resolvedDrawable.intrinsicHeight.coerceAtLeast(1)
        return (height * intrinsicWidth.toFloat() / intrinsicHeight)
            .roundToInt()
            .coerceAtLeast(1)
    }

    private fun loadDrawable(): Drawable? {
        if (!attachment.mimeType.startsWith("image/")) return null
        val uri = attachment.uri?.let(Uri::parse) ?: return null
        return runCatching {
            context.contentResolver.openInputStream(uri)?.use { input ->
                Drawable.createFromStream(input, attachment.id)
            }?.mutate()
        }.getOrNull()
    }

    companion object {
        private const val ATTACHMENT_PLACEHOLDER_COLOR: Int = Color.GRAY
    }
}

private fun Layout.Alignment.toDocumentAlignment(): ParagraphTextAlignment {
    return when (this) {
        Layout.Alignment.ALIGN_CENTER -> ParagraphTextAlignment.Center
        Layout.Alignment.ALIGN_OPPOSITE -> ParagraphTextAlignment.End
        else -> ParagraphTextAlignment.Start
    }
}

private fun Int.toDocumentSource(): ReceiveContentSource {
    return when (this) {
        ContentInfoCompat.SOURCE_CLIPBOARD -> ReceiveContentSource.Clipboard
        ContentInfoCompat.SOURCE_DRAG_AND_DROP -> ReceiveContentSource.DragAndDrop
        ContentInfoCompat.SOURCE_INPUT_METHOD -> ReceiveContentSource.InputMethod
        ContentInfoCompat.SOURCE_AUTOFILL -> ReceiveContentSource.Autofill
        ContentInfoCompat.SOURCE_APP,
        ContentInfoCompat.SOURCE_PROCESS_TEXT,
        -> ReceiveContentSource.Application
        else -> ReceiveContentSource.Unknown
    }
}
