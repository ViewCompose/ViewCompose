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
 * Displays plain text with theme-resolved typography and color defaults.
 *
 * The node is patched in place when text or style values change. Resource-backed localized text
 * should be resolved during composition so environment revisions update it automatically.
 *
 * @sample com.viewcompose.ui.foundation.samples.contentDslSample
 * @receiver active tree builder receiving the text node
 * @param text immutable plain-text snapshot displayed by the node
 * @param style resolved typography including size, weight, family, spacing, and line height
 * @param color packed ARGB text color
 * @param maxLines positive maximum visual line count
 * @param overflow handling applied when content exceeds [maxLines]
 * @param textAlign horizontal paragraph alignment
 * @param textDecoration explicit decoration, defaulting to the value in [style]
 * @param key optional stable sibling identity used during reconciliation
 * @param modifier ordered layout, drawing, input, and semantics configuration
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
 * Displays text whose content is patched directly when its observed dependencies change.
 *
 * The initial and later values use the same typography, color, overflow, alignment, key, and
 * Modifier supplied to this call. Only the text document is observed; changing any other ordinary
 * argument requires normal composition. All invalidated observed text values in one RenderSession
 * frame are read from one Snapshot and committed atomically with other observed node properties.
 *
 * @sample com.viewcompose.ui.foundation.samples.observedTextValueSample
 * @receiver active tree builder receiving the observed text node
 * @param text observed plain-text declaration and its explicit ordinary inputs
 * @param style resolved static typography for every property transaction
 * @param color static packed ARGB text color
 * @param maxLines positive static maximum visual line count
 * @param overflow static handling when content exceeds [maxLines]
 * @param textAlign static horizontal paragraph alignment
 * @param textDecoration static decoration, defaulting to the value in [style]
 * @param key optional stable sibling identity used during reconciliation
 * @param modifier structural ordered layout, drawing, input, and semantics configuration
 */
fun UiTreeBuilder.Text(
    text: ObservedValue<String>,
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
        spec = observedNodeSpec(
            inputs = text.inputs + listOf(
                style,
                color,
                maxLines,
                overflow,
                textAlign,
                textDecoration,
            ),
        ) {
            TextNodeProps(
                document = TextDocument.plain(text.read()),
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
            )
        },
        modifier = modifier,
    )
}

/**
 * Displays an immutable rich-text document while preserving span and paragraph ranges.
 *
 * Values in [document] take part in node equality and patching; callers should replace the
 * document snapshot when its styled content changes.
 *
 * @sample com.viewcompose.ui.foundation.samples.contentDslSample
 * @receiver active tree builder receiving the rich-text node
 * @param document immutable text, span, and paragraph snapshot
 * @param style fallback typography for ranges not overridden by the document
 * @param color fallback packed ARGB color for ranges without a foreground color
 * @param maxLines positive maximum visual line count
 * @param overflow handling applied when content exceeds [maxLines]
 * @param textAlign horizontal paragraph alignment
 * @param textDecoration fallback decoration, defaulting to the value in [style]
 * @param key optional stable sibling identity used during reconciliation
 * @param modifier ordered layout, drawing, input, and semantics configuration
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
