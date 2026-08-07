package com.viewcompose.ui.foundation

import com.viewcompose.ui.modifier.Modifier
import com.viewcompose.ui.modifier.clickable
import com.viewcompose.ui.modifier.height
import com.viewcompose.ui.modifier.size
import com.viewcompose.ui.node.ImageContentScale
import com.viewcompose.ui.node.ImageSource
import com.viewcompose.ui.node.NodeType
import com.viewcompose.ui.node.SegmentedControlItem
import com.viewcompose.ui.node.UiImageRequestOptions
import com.viewcompose.ui.node.UiStateLayerColors
import com.viewcompose.ui.node.spec.ButtonNodeProps
import com.viewcompose.ui.node.spec.IconButtonNodeProps
import com.viewcompose.ui.node.spec.SegmentedControlNodeProps
import com.viewcompose.ui.node.spec.uiFontFamily

/**
 * Displays a themed text action and invokes [onClick] for an accepted enabled click.
 *
 * The current theme resolves visual hierarchy, typography, padding, shape, effective minimum
 * height, and the centered visible container height. A design system may therefore retain a 48dp
 * semantic and touch target while drawing a smaller container. Explicit visual modifiers remain
 * authoritative over the component surface. State remains caller-owned; this component stores no
 * selection or progress value.
 *
 * @sample com.viewcompose.ui.foundation.samples.buttonSample
 * @receiver active tree builder that receives the emitted Button node
 * @param text visible action label
 * @param onClick callback invoked synchronously on the renderer thread for an enabled click
 * @param leadingIcon optional resource icon placed before [text]
 * @param trailingIcon optional resource icon placed after [text]
 * @param variant visual hierarchy used to resolve container, content, and border roles
 * @param size interaction-density tier used for target, visible container, padding, icon, and text
 * @param enabled whether input is accepted and enabled color roles are used
 * @param stateLayerColors resolved pressed, focused, and hovered colors clipped to the visible
 * container
 * @param style immutable text appearance snapshot for the label
 * @param key optional stable sibling identity used during reconciliation
 * @param modifier ordered configuration appended to the emitted Button node
 */
fun UiTreeBuilder.Button(
    text: String,
    onClick: (() -> Unit)? = null,
    leadingIcon: ImageSource.Resource? = null,
    trailingIcon: ImageSource.Resource? = null,
    variant: ButtonVariant = ButtonVariant.Primary,
    size: ButtonSize = ButtonSize.Medium,
    enabled: Boolean = true,
    stateLayerColors: UiStateLayerColors = ButtonDefaults.stateLayerColors(variant),
    style: UiTextStyle = ButtonDefaults.textStyle(size),
    key: Any? = null,
    modifier: Modifier = Modifier,
) {
    emitButton(
        text = text,
        onClick = onClick,
        leadingIcon = leadingIcon,
        trailingIcon = trailingIcon,
        variant = variant,
        size = size,
        enabled = enabled,
        rippleColor = stateLayerColors.pressedColor,
        stateLayerColors = stateLayerColors,
        style = style,
        key = key,
        modifier = modifier,
    )
}

/**
 * Displays a themed text action with one compatibility feedback color for every active state.
 *
 * Prefer the state-layer overload for distinct pressed, focused, and hovered feedback. This
 * overload preserves source compatibility for callers that explicitly supplied the former
 * single-color `rippleColor` parameter; Android Renderer also retains the corresponding nullable
 * NodeSpec fallback for custom emitters.
 *
 * @sample com.viewcompose.ui.foundation.samples.buttonSample
 * @receiver active tree builder that receives the emitted Button node
 * @param text visible action label
 * @param onClick callback invoked synchronously on the renderer thread for an enabled click
 * @param leadingIcon optional resource icon placed before [text]
 * @param trailingIcon optional resource icon placed after [text]
 * @param variant visual hierarchy used to resolve container, content, and border roles
 * @param size interaction-density tier used for target, visible container, padding, icon, and text
 * @param enabled whether input is accepted and enabled color roles are used
 * @param rippleColor ARGB feedback color used for pressed, focused, and hovered states
 * @param style immutable text appearance snapshot for the label
 * @param key optional stable sibling identity used during reconciliation
 * @param modifier ordered configuration appended to the emitted Button node
 */
fun UiTreeBuilder.Button(
    text: String,
    onClick: (() -> Unit)? = null,
    leadingIcon: ImageSource.Resource? = null,
    trailingIcon: ImageSource.Resource? = null,
    variant: ButtonVariant = ButtonVariant.Primary,
    size: ButtonSize = ButtonSize.Medium,
    enabled: Boolean = true,
    rippleColor: Int,
    style: UiTextStyle = ButtonDefaults.textStyle(size),
    key: Any? = null,
    modifier: Modifier = Modifier,
) {
    emitButton(
        text = text,
        onClick = onClick,
        leadingIcon = leadingIcon,
        trailingIcon = trailingIcon,
        variant = variant,
        size = size,
        enabled = enabled,
        rippleColor = rippleColor,
        stateLayerColors = null,
        style = style,
        key = key,
        modifier = modifier,
    )
}

private fun UiTreeBuilder.emitButton(
    text: String,
    onClick: (() -> Unit)?,
    leadingIcon: ImageSource.Resource?,
    trailingIcon: ImageSource.Resource?,
    variant: ButtonVariant,
    size: ButtonSize,
    enabled: Boolean,
    rippleColor: Int,
    stateLayerColors: UiStateLayerColors?,
    style: UiTextStyle,
    key: Any?,
    modifier: Modifier,
) {
    val contentColor = ButtonDefaults.contentColor(variant, enabled)
    val iconSizeValue = ButtonDefaults.iconSize(size)
    val iconSpacingValue = ButtonDefaults.iconSpacing(size)
    emit(
        type = NodeType.Button,
        key = key,
        spec = ButtonNodeProps(
            text = text,
            enabled = enabled,
            onClick = onClick,
            textColor = contentColor,
            textSizeSp = style.fontSizeSp,
            fontWeight = style.fontWeight,
            fontFamily = uiFontFamily(style.fontFamily),
            letterSpacingEm = style.letterSpacingEm,
            lineHeightSp = style.lineHeightSp,
            includeFontPadding = style.includeFontPadding,
            backgroundColor = ButtonDefaults.containerColor(variant, enabled),
            borderWidth = ButtonDefaults.borderWidth(variant),
            borderColor = ButtonDefaults.borderColor(variant, enabled),
            shape = ButtonDefaults.shape(),
            rippleColor = rippleColor,
            minHeight = ButtonDefaults.height(size),
            paddingHorizontal = ButtonDefaults.horizontalPadding(size, variant),
            paddingVertical = ButtonDefaults.verticalPadding(size),
            leadingIcon = leadingIcon,
            trailingIcon = trailingIcon,
            iconTint = contentColor,
            iconSize = iconSizeValue,
            iconSpacing = iconSpacingValue,
            visualHeight = ButtonDefaults.visualHeight(size),
            stateLayerColors = stateLayerColors,
        ),
        modifier = modifier,
    )
}

/**
 * Emits an icon-only button whose image uses the current scoped loader.
 *
 * A non-null [icon] is loaded with [ImageContentScale.Inside]; a resource remains directly
 * renderable when no loader is installed. Click semantics are emitted only while [enabled] is
 * `true` and [onClick] is non-null. The component applies its required square size and click
 * semantics before appending [modifier].
 *
 * @sample com.viewcompose.ui.foundation.samples.imageLoadingSample
 * @receiver active tree builder that receives the emitted button node
 * @param icon primary icon source, or `null` for no icon content
 * @param contentDescription accessibility description, or `null` for decorative content
 * @param onClick callback invoked synchronously for an enabled click, or `null` for no action
 * @param variant visual button variant used to resolve theme colors and shape
 * @param size control size used for bounds, padding, and theme defaults
 * @param tint optional ARGB icon tint; `null` resolves the themed content color
 * @param requestOptions immutable decode, cache, transition, and adapter-extension policy
 * @param enabled whether the button accepts clicks and uses enabled theme tokens
 * @param stateLayerColors resolved pressed, focused, and hovered colors clipped to the button shape
 * @param key optional stable sibling identity used during reconciliation
 * @param modifier ordered configuration appended after required size and click semantics
 */
fun UiTreeBuilder.IconButton(
    icon: ImageSource?,
    contentDescription: String? = null,
    onClick: (() -> Unit)? = null,
    variant: ButtonVariant = ButtonVariant.Text,
    size: ButtonSize = ButtonSize.Medium,
    tint: Int? = null,
    requestOptions: UiImageRequestOptions = UiImageRequestOptions(),
    enabled: Boolean = true,
    stateLayerColors: UiStateLayerColors = IconButtonDefaults.stateLayerColors(variant),
    key: Any? = null,
    modifier: Modifier = Modifier,
) {
    val resolvedTint = tint ?: IconButtonDefaults.contentColor(variant, enabled)
    val contentPaddingValue = IconButtonDefaults.contentPadding(size)
    val semanticModifier = Modifier
        .size(
            width = IconButtonDefaults.size(size),
            height = IconButtonDefaults.size(size),
        )
        .then(
            if (enabled && onClick != null) {
                Modifier.clickable(onClick)
            } else {
                Modifier
            },
        )
        .then(modifier)
    emit(
        type = NodeType.IconButton,
        key = key,
        spec = IconButtonNodeProps(
            contentDescription = contentDescription,
            contentScale = ImageContentScale.Inside,
            tint = resolvedTint,
            source = icon,
            placeholder = null,
            error = null,
            fallback = null,
            imageLoader = ImageLoading.current,
            requestOptions = requestOptions,
            enabled = enabled,
            backgroundColor = IconButtonDefaults.containerColor(variant, enabled),
            borderWidth = IconButtonDefaults.borderWidth(variant),
            borderColor = IconButtonDefaults.borderColor(variant, enabled),
            shape = IconButtonDefaults.shape(),
            rippleColor = stateLayerColors.pressedColor,
            contentPadding = contentPaddingValue,
            stateLayerColors = stateLayerColors,
        ),
        modifier = semanticModifier,
    )
}

/**
 * Text-style convenience variant of Button.
 */
fun UiTreeBuilder.TextButton(
    text: String,
    onClick: (() -> Unit)? = null,
    size: ButtonSize = ButtonSize.Medium,
    enabled: Boolean = true,
    style: UiTextStyle = ButtonDefaults.textStyle(size),
    key: Any? = null,
    modifier: Modifier = Modifier,
) {
    Button(
        text = text,
        onClick = onClick,
        variant = ButtonVariant.Text,
        size = size,
        enabled = enabled,
        style = style,
        key = key,
        modifier = modifier,
    )
}

/**
 * Emits a SegmentedControl node.
 */
fun UiTreeBuilder.SegmentedControl(
    items: List<String>,
    selectedIndex: Int,
    onSelectionChange: (Int) -> Unit,
    size: SegmentedControlSize = SegmentedControlSize.Medium,
    enabled: Boolean = true,
    key: Any? = null,
    modifier: Modifier = Modifier,
) {
    val style = SegmentedControlDefaults.textStyle(size)
    val resolvedItems = items.map { label -> SegmentedControlItem(label = label) }
    val backgroundColor = SegmentedControlDefaults.backgroundColor(enabled)
    val indicatorColor = SegmentedControlDefaults.indicatorColor(enabled)
    val shape = SegmentedControlDefaults.shape()
    val textColor = SegmentedControlDefaults.textColor(enabled)
    val selectedTextColor = SegmentedControlDefaults.selectedTextColor(enabled)
    val rippleColor = SegmentedControlDefaults.rippleColor(enabled)
    val paddingHorizontal = SegmentedControlDefaults.paddingHorizontal(size)
    val paddingVertical = SegmentedControlDefaults.paddingVertical(size)
    emit(
        type = NodeType.SegmentedControl,
        key = key,
        spec = SegmentedControlNodeProps(
            items = resolvedItems,
            selectedIndex = selectedIndex,
            onSelectionChange = onSelectionChange,
            enabled = enabled,
            backgroundColor = backgroundColor,
            indicatorColor = indicatorColor,
            shape = shape,
            textColor = textColor,
            selectedTextColor = selectedTextColor,
            rippleColor = rippleColor,
            textSizeSp = style.fontSizeSp,
            fontWeight = style.fontWeight,
            fontFamily = uiFontFamily(style.fontFamily),
            letterSpacingEm = style.letterSpacingEm,
            lineHeightSp = style.lineHeightSp,
            includeFontPadding = style.includeFontPadding,
            paddingHorizontal = paddingHorizontal,
            paddingVertical = paddingVertical,
            unselectedStateLayerColors = SegmentedControlDefaults.stateLayerColors(selected = false),
            selectedStateLayerColors = SegmentedControlDefaults.stateLayerColors(selected = true),
        ),
        modifier = Modifier
            .height(SegmentedControlDefaults.height(size))
            .then(modifier),
    )
}
