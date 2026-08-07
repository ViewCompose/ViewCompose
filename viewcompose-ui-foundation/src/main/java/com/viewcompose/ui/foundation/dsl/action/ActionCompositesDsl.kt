package com.viewcompose.ui.foundation

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
import com.viewcompose.ui.unit.UiDp

/**
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
        StateLayerBox(
            key = key,
            contentAlignment = BoxAlignment.Center,
            rippleColor = FabDefaults.pressedColor(),
            stateLayerColors = stateLayerColorsFor(contentColor),
            modifier = semanticModifier,
        ) {
            content()
        }
    }
}

/**
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
        StateLayerRow(
            key = key,
            spacing = if (icon != null) FabDefaults.extendedIconSpacing() else UiDp.Zero,
            verticalAlignment = VerticalAlignment.Center,
            rippleColor = FabDefaults.pressedColor(),
            stateLayerColors = stateLayerColorsFor(contentColor),
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
        .let { m -> if (bw > UiDp.Zero) m.border(bw, bc) else m }
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
        StateLayerRow(
            key = key,
            spacing = ChipDefaults.iconSpacing(),
            verticalAlignment = VerticalAlignment.Center,
            rippleColor = ChipDefaults.pressedColor(),
            stateLayerColors = if (enabled) stateLayerColorsFor(cColor) else null,
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
