package com.viewcompose.widget.core

import com.viewcompose.ui.modifier.Modifier
import com.viewcompose.ui.modifier.clickable
import com.viewcompose.ui.modifier.height
import com.viewcompose.ui.modifier.size
import com.viewcompose.ui.node.ImageContentScale
import com.viewcompose.ui.node.ImageSource
import com.viewcompose.ui.node.NodeType
import com.viewcompose.ui.node.SegmentedControlItem
import com.viewcompose.ui.node.spec.ButtonNodeProps
import com.viewcompose.ui.node.spec.IconButtonNodeProps
import com.viewcompose.ui.node.spec.SegmentedControlNodeProps
import com.viewcompose.ui.node.spec.uiFontFamily

/**
 * 发射 Button 节点，并根据主题 defaults 解析颜色、尺寸、形状和文字样式。
 * Emits a Button node and resolves colors, sizing, shape, and text style from theme defaults.
 */
fun UiTreeBuilder.Button(
    text: String,
    onClick: (() -> Unit)? = null,
    leadingIcon: ImageSource.Resource? = null,
    trailingIcon: ImageSource.Resource? = null,
    variant: ButtonVariant = ButtonVariant.Primary,
    size: ButtonSize = ButtonSize.Medium,
    enabled: Boolean = true,
    rippleColor: Int = ButtonDefaults.pressedColor(),
    style: UiTextStyle = ButtonDefaults.textStyle(size),
    key: Any? = null,
    modifier: Modifier = Modifier,
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
            paddingHorizontal = ButtonDefaults.horizontalPadding(size),
            paddingVertical = ButtonDefaults.verticalPadding(size),
            leadingIcon = leadingIcon,
            trailingIcon = trailingIcon,
            iconTint = contentColor,
            iconSize = iconSizeValue,
            iconSpacing = iconSpacingValue,
        ),
        modifier = modifier,
    )
}

/**
 * 发射 IconButton 节点，并把点击语义作为 Modifier 追加到外层。
 * Emits an IconButton node and appends click semantics through Modifier.
 */
fun UiTreeBuilder.IconButton(
    icon: ImageSource,
    contentDescription: String? = null,
    onClick: (() -> Unit)? = null,
    variant: ButtonVariant = ButtonVariant.Text,
    size: ButtonSize = ButtonSize.Medium,
    tint: Int? = null,
    enabled: Boolean = true,
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
            remoteImageLoader = ImageLoading.current,
            enabled = enabled,
            backgroundColor = IconButtonDefaults.containerColor(variant, enabled),
            borderWidth = IconButtonDefaults.borderWidth(variant),
            borderColor = IconButtonDefaults.borderColor(variant, enabled),
            shape = IconButtonDefaults.shape(),
            rippleColor = IconButtonDefaults.pressedColor(),
            contentPadding = contentPaddingValue,
        ),
        modifier = semanticModifier,
    )
}

/**
 * Button 的文本样式便捷变体。
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
 * 发射 SegmentedControl 节点。
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
        ),
        modifier = Modifier
            .height(SegmentedControlDefaults.height(size))
            .then(modifier),
    )
}
