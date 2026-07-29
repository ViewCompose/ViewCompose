package com.viewcompose.widget.core

import com.viewcompose.text.TextDocument
import com.viewcompose.ui.modifier.Modifier
import com.viewcompose.ui.modifier.size
import com.viewcompose.ui.node.ImageContentScale
import com.viewcompose.ui.node.ImageSource
import com.viewcompose.ui.node.NodeType
import com.viewcompose.ui.node.TextAlign
import com.viewcompose.ui.node.TextDecoration
import com.viewcompose.ui.node.TextOverflow
import com.viewcompose.ui.node.spec.ImageNodeProps
import com.viewcompose.ui.node.spec.TextNodeProps
import com.viewcompose.ui.node.spec.uiFontFamily
import com.viewcompose.ui.unit.UiDp

/**
 * 发射纯文本节点。
 * Emits a plain text node.
 */
fun UiTreeBuilder.Text(
    text: String,
    style: UiTextStyle = TextDefaults.currentStyle(),
    color: Int = TextDefaults.primaryColor(),
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
    textAlign: TextAlign = TextAlign.Start,
    textDecoration: TextDecoration = style.textDecoration ?: TextDecoration.None,
    key: Any? = null,
    modifier: Modifier = Modifier,
) {
    emit(
        type = NodeType.Text,
        key = key,
        spec = TextNodeProps(
            text = text,
            maxLines = maxLines,
            overflow = overflow,
            textAlign = textAlign,
            textColor = color,
            textSizeSp = style.fontSizeSp,
            fontWeight = style.fontWeight,
            fontFamily = uiFontFamily(style.fontFamily),
            letterSpacingEm = style.letterSpacingEm,
            lineHeightSp = style.lineHeightSp,
            includeFontPadding = style.includeFontPadding,
            textDecoration = textDecoration,
            document = TextDocument.plain(text),
        ),
        modifier = modifier,
    )
}

/**
 * 发射富文本节点，保留 TextDocument 的 span/段落信息。
 * Emits a rich text node while preserving TextDocument span/paragraph data.
 */
fun UiTreeBuilder.RichText(
    document: TextDocument,
    style: UiTextStyle = TextDefaults.currentStyle(),
    color: Int = TextDefaults.primaryColor(),
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
    textAlign: TextAlign = TextAlign.Start,
    textDecoration: TextDecoration = style.textDecoration ?: TextDecoration.None,
    key: Any? = null,
    modifier: Modifier = Modifier,
) {
    emit(
        type = NodeType.Text,
        key = key,
        spec = TextNodeProps(
            text = document.text,
            maxLines = maxLines,
            overflow = overflow,
            textAlign = textAlign,
            textColor = color,
            textSizeSp = style.fontSizeSp,
            fontWeight = style.fontWeight,
            fontFamily = uiFontFamily(style.fontFamily),
            letterSpacingEm = style.letterSpacingEm,
            lineHeightSp = style.lineHeightSp,
            includeFontPadding = style.includeFontPadding,
            textDecoration = textDecoration,
            document = document,
        ),
        modifier = modifier,
    )
}

/**
 * 发射图片节点，并使用当前 ImageLoading loader 处理远程资源。
 * Emits an image node and uses the current ImageLoading loader for remote sources.
 */
fun UiTreeBuilder.Image(
    source: ImageSource,
    contentDescription: String? = null,
    contentScale: ImageContentScale = ImageContentScale.Fit,
    tint: Int? = null,
    placeholder: ImageSource.Resource? = null,
    error: ImageSource.Resource? = placeholder,
    fallback: ImageSource.Resource? = placeholder,
    key: Any? = null,
    modifier: Modifier = Modifier,
) {
    emit(
        type = NodeType.Image,
        key = key,
        spec = ImageNodeProps(
            contentDescription = contentDescription,
            contentScale = contentScale,
            tint = tint,
            source = source,
            placeholder = placeholder,
            error = error,
            fallback = fallback,
            remoteImageLoader = ImageLoading.current,
        ),
        modifier = modifier,
    )
}

/**
 * Image 的图标语义便捷封装，默认使用 ContentColor。
 * Icon-oriented convenience wrapper around Image, using ContentColor by default.
 */
fun UiTreeBuilder.Icon(
    source: ImageSource,
    contentDescription: String? = null,
    tint: Int = IconDefaults.tint(),
    size: UiDp = IconDefaults.size(),
    key: Any? = null,
    modifier: Modifier = Modifier,
) {
    Image(
        source = source,
        contentDescription = contentDescription,
        contentScale = ImageContentScale.Inside,
        tint = tint,
        key = key,
        modifier = Modifier
            .size(width = size, height = size)
            .then(modifier),
    )
}
