package com.viewcompose.widget.core

import com.viewcompose.ui.layout.BoxAlignment
import com.viewcompose.ui.layout.VerticalAlignment
import com.viewcompose.ui.modifier.Modifier
import com.viewcompose.ui.modifier.alpha
import com.viewcompose.ui.modifier.backgroundColor
import com.viewcompose.ui.modifier.border
import com.viewcompose.ui.modifier.clickable
import com.viewcompose.ui.modifier.clip
import com.viewcompose.ui.modifier.elevation
import com.viewcompose.ui.modifier.height
import com.viewcompose.ui.modifier.padding
import com.viewcompose.ui.modifier.size
import com.viewcompose.ui.modifier.shape
import com.viewcompose.ui.node.ImageSource

/**
 * 组合式 FloatingActionButton，由 Box、主题 token 和点击 modifier 构成。
 * Composite FloatingActionButton built from Box, theme tokens, and click modifiers.
 */
fun UiTreeBuilder.FloatingActionButton(
    onClick: () -> Unit,
    size: FabSize = FabSize.Medium,
    containerColor: Int = FabDefaults.containerColor(),
    contentColor: Int = FabDefaults.contentColor(),
    key: Any? = null,
    modifier: Modifier = Modifier,
    content: UiTreeBuilder.() -> Unit,
) {
    val fabSize = FabDefaults.size(size)
    val shape = FabDefaults.shape(size)
    val semanticModifier = Modifier
        .size(width = fabSize, height = fabSize)
        .backgroundColor(containerColor)
        .shape(shape)
        .elevation(FabDefaults.elevation())
        .clip()
        .clickable(onClick)
        .then(modifier)
    ProvideLocal(LocalContentColor, contentColor) {
        Box(
            key = key,
            contentAlignment = BoxAlignment.Center,
            rippleColor = FabDefaults.pressedColor(),
            modifier = semanticModifier,
        ) {
            content()
        }
    }
}

/**
 * 带文本和可选图标的扩展 FloatingActionButton。
 * Extended FloatingActionButton with text and an optional icon.
 */
fun UiTreeBuilder.ExtendedFloatingActionButton(
    text: String,
    onClick: () -> Unit,
    icon: ImageSource? = null,
    containerColor: Int = FabDefaults.containerColor(),
    contentColor: Int = FabDefaults.contentColor(),
    key: Any? = null,
    modifier: Modifier = Modifier,
) {
    val shape = FabDefaults.extendedShape()
    val semanticModifier = Modifier
        .height(FabDefaults.extendedHeight())
        .backgroundColor(containerColor)
        .shape(shape)
        .elevation(FabDefaults.elevation())
        .clip()
        .clickable(onClick)
        .padding(horizontal = FabDefaults.extendedHorizontalPadding())
        .then(modifier)
    ProvideLocal(LocalContentColor, contentColor) {
        Row(
            key = key,
            spacing = if (icon != null) FabDefaults.extendedIconSpacing() else 0,
            verticalAlignment = VerticalAlignment.Center,
            modifier = semanticModifier,
        ) {
            if (icon != null) {
                Icon(
                    source = icon,
                    tint = contentColor,
                    size = FabDefaults.iconSize(FabSize.Medium),
                )
            }
            Text(
                text = text,
                style = FabDefaults.extendedTextStyle(),
                color = contentColor,
            )
        }
    }
}

/**
 * 组合式 Chip，支持选中、禁用、leading icon 和 trailing icon action。
 * Composite Chip supporting selected/disabled states, leading icon, and trailing icon action.
 */
fun UiTreeBuilder.Chip(
    label: String,
    onClick: () -> Unit,
    variant: ChipVariant = ChipVariant.Assist,
    selected: Boolean = false,
    leadingIcon: ImageSource? = null,
    onTrailingIconClick: (() -> Unit)? = null,
    enabled: Boolean = true,
    key: Any? = null,
    modifier: Modifier = Modifier,
) {
    val bgColor = ChipDefaults.containerColor(variant, selected, enabled)
    val cColor = ChipDefaults.contentColor(variant, selected, enabled)
    val bw = ChipDefaults.borderWidth(variant, selected)
    val bc = ChipDefaults.borderColor(variant, selected, enabled)
    val shape = ChipDefaults.shape()
    val leftPadding = if (leadingIcon != null) {
        ChipDefaults.leadingIconPadding()
    } else {
        ChipDefaults.horizontalPadding()
    }
    val rightPadding = if (onTrailingIconClick != null) {
        ChipDefaults.leadingIconPadding()
    } else {
        ChipDefaults.horizontalPadding()
    }
    val semanticModifier = Modifier
        .height(ChipDefaults.height())
        .backgroundColor(bgColor)
        .let { m -> if (bw > 0) m.border(bw, bc) else m }
        .shape(shape)
        .clip()
        .let { m ->
            if (enabled) {
                m.clickable(onClick)
            } else {
                m.alpha(0.38f)
            }
        }
        .padding(left = leftPadding, right = rightPadding)
        .then(modifier)
    ProvideLocal(LocalContentColor, cColor) {
        Row(
            key = key,
            spacing = ChipDefaults.iconSpacing(),
            verticalAlignment = VerticalAlignment.Center,
            modifier = semanticModifier,
        ) {
            if (leadingIcon != null) {
                Icon(
                    source = leadingIcon,
                    tint = cColor,
                    size = ChipDefaults.iconSize(),
                )
            }
            Text(
                text = label,
                style = ChipDefaults.textStyle(),
                color = cColor,
                maxLines = 1,
            )
            if (onTrailingIconClick != null) {
                Icon(
                    source = ImageSource.Resource(android.R.drawable.ic_menu_close_clear_cancel),
                    tint = cColor,
                    size = ChipDefaults.trailingIconSize(),
                    modifier = Modifier.clickable(onTrailingIconClick),
                )
            }
        }
    }
}
