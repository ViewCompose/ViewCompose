package com.viewcompose.ui.foundation

import com.viewcompose.text.TextDocument
import com.viewcompose.ui.modifier.Modifier
import com.viewcompose.ui.modifier.size
import com.viewcompose.ui.node.ImageContentScale
import com.viewcompose.ui.node.ImageSource
import com.viewcompose.ui.node.NodeType
import com.viewcompose.ui.node.TextAlign
import com.viewcompose.ui.node.TextDecoration
import com.viewcompose.ui.node.TextOverflow
import com.viewcompose.ui.node.UiImageRequestOptions
import com.viewcompose.ui.node.spec.ImageNodeProps
import com.viewcompose.ui.node.spec.TextNodeProps
import com.viewcompose.ui.node.spec.uiFontFamily
import com.viewcompose.ui.unit.UiDp

/**
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
            document = TextDocument.plain(text),
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
        ),
        modifier = modifier,
    )
}

/**
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
            document = document,
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
        ),
        modifier = modifier,
    )
}

/**
 * Emits image content backed by a resource or the current scoped image loader.
 *
 * A `null` [source] displays [fallback] immediately without invoking a loader. For a non-null
 * source, the current [ImageLoading] loader receives every source type, including
 * [ImageSource.Resource]. Without a loader, resources render directly and other source types use
 * [error], then [placeholder], then [fallback]. [contentScale] controls final View display while
 * [requestOptions] controls decoding, caches, transitions, and adapter extensions.
 *
 * @sample com.viewcompose.ui.foundation.samples.imageLoadingSample
 * @receiver active tree builder that receives the emitted image node
 * @param source primary image source, or `null` to display [fallback]
 * @param contentDescription accessibility description, or `null` for decorative content
 * @param contentScale mapping from decoded image bounds to the rendered target bounds
 * @param tint optional ARGB tint; `null` preserves source colors
 * @param placeholder resource displayed when a new loader request starts
 * @param error resource displayed after loading fails; defaults to [placeholder]
 * @param fallback resource displayed when [source] is `null`; defaults to [placeholder]
 * @param requestOptions immutable decode, cache, transition, and adapter-extension policy
 * @param key optional stable sibling identity used during reconciliation
 * @param modifier ordered layout, drawing, input, and semantics configuration
 */
fun UiTreeBuilder.Image(
    source: ImageSource?,
    contentDescription: String? = null,
    contentScale: ImageContentScale = ImageContentScale.Fit,
    tint: Int? = null,
    placeholder: ImageSource.Resource? = null,
    error: ImageSource.Resource? = placeholder,
    fallback: ImageSource.Resource? = placeholder,
    requestOptions: UiImageRequestOptions = UiImageRequestOptions(),
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
            imageLoader = ImageLoading.current,
            requestOptions = requestOptions,
        ),
        modifier = modifier,
    )
}

/**
 * Emits fixed-size icon content using the current content color and image loader.
 *
 * The icon uses [ImageContentScale.Inside], so content that already fits is not upscaled. A
 * non-null [source] follows the same loader and direct-resource rules as [Image]; `null` emits an
 * empty icon target because this convenience API has no fallback resource.
 *
 * @sample com.viewcompose.ui.foundation.samples.imageLoadingSample
 * @receiver active tree builder that receives the emitted icon node
 * @param source primary icon source, or `null` for no icon content
 * @param contentDescription accessibility description, or `null` for decorative content
 * @param tint ARGB tint applied by the renderer; defaults to the current content color
 * @param size square logical layout size applied before [modifier]
 * @param requestOptions immutable decode, cache, transition, and adapter-extension policy
 * @param key optional stable sibling identity used during reconciliation
 * @param modifier ordered configuration appended after the icon's required square size
 */
fun UiTreeBuilder.Icon(
    source: ImageSource?,
    contentDescription: String? = null,
    tint: Int = IconDefaults.tint(),
    size: UiDp = IconDefaults.size(),
    requestOptions: UiImageRequestOptions = UiImageRequestOptions(),
    key: Any? = null,
    modifier: Modifier = Modifier,
) {
    Image(
        source = source,
        contentDescription = contentDescription,
        contentScale = ImageContentScale.Inside,
        tint = tint,
        requestOptions = requestOptions,
        key = key,
        modifier = Modifier
            .size(width = size, height = size)
            .then(modifier),
    )
}
