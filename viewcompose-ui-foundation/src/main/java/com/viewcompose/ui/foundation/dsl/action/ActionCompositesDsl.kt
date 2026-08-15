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
 * Emits a semantic floating action button with a caller-owned content slot.
 *
 * [size] selects the design-system tier. Appearance resolves from [FabDefaults], nested
 * [ProvideFloatingActionButtonOverrides] scopes, and instance [overrides] in increasing
 * precedence. The button is always enabled; omit it instead of supplying an inactive callback.
 *
 * @sample com.viewcompose.ui.foundation.samples.remainingComponentOverridesSample
 * @receiver active tree builder receiving the FAB
 * @param onClick callback invoked synchronously on the renderer thread after an accepted click
 * @param size semantic bounds and shape tier
 * @param overrides sparse instance appearance applied after scoped FAB overrides
 * @param key optional stable sibling identity used during reconciliation
 * @param modifier ordered configuration appended after resolved FAB geometry and interaction
 * @param content subtree built synchronously with the resolved content color
 */
fun UiTreeBuilder.FloatingActionButton(
    onClick: () -> Unit,
    size: FabSize = FabSize.Medium,
    overrides: FloatingActionButtonOverrides = FloatingActionButtonOverrides.None,
    key: Any? = null,
    modifier: Modifier = Modifier,
    content: UiTreeBuilder.() -> Unit,
) {
    val appearance = FabDefaults.resolve(size, overrides)
    val semanticModifier = Modifier
        .size(width = appearance.size, height = appearance.size)
        .backgroundColor(appearance.containerColor)
        .shape(appearance.shape)
        .elevation(appearance.elevation)
        .clip()
        .clickable(onClick)
        .then(modifier)
    ProvideLocal(LocalContentColor, appearance.contentColor) {
        StateLayerBox(
            key = key,
            contentAlignment = BoxAlignment.Center,
            rippleColor = appearance.rippleColor,
            stateLayerColors = appearance.stateLayerColors,
            modifier = semanticModifier,
        ) {
            content()
        }
    }
}

/**
 * Emits an extended floating action button with a label and optional icon.
 *
 * Appearance resolves from [FabDefaults], nested
 * [ProvideExtendedFloatingActionButtonOverrides] scopes, and instance [overrides].
 *
 * @sample com.viewcompose.ui.foundation.samples.remainingComponentOverridesSample
 * @receiver active tree builder receiving the extended FAB
 * @param text label displayed by the action
 * @param onClick callback invoked synchronously on the renderer thread after an accepted click
 * @param icon optional decorative icon rendered before [text]
 * @param overrides sparse instance appearance applied after scoped extended-FAB overrides
 * @param key optional stable sibling identity used during reconciliation
 * @param modifier ordered configuration appended after resolved geometry and interaction
 */
fun UiTreeBuilder.ExtendedFloatingActionButton(
    text: String,
    onClick: () -> Unit,
    icon: ImageSource? = null,
    overrides: ExtendedFloatingActionButtonOverrides = ExtendedFloatingActionButtonOverrides.None,
    key: Any? = null,
    modifier: Modifier = Modifier,
) {
    val appearance = FabDefaults.resolveExtended(overrides)
    val semanticModifier = Modifier
        .height(appearance.height)
        .backgroundColor(appearance.containerColor)
        .shape(appearance.shape)
        .elevation(appearance.elevation)
        .clip()
        .clickable(onClick)
        .padding(horizontal = appearance.horizontalPadding)
        .then(modifier)
    ProvideLocal(LocalContentColor, appearance.contentColor) {
        StateLayerRow(
            key = key,
            spacing = if (icon != null) appearance.iconSpacing else UiDp.Zero,
            verticalAlignment = VerticalAlignment.Center,
            rippleColor = appearance.rippleColor,
            stateLayerColors = appearance.stateLayerColors,
            modifier = semanticModifier,
        ) {
            if (icon != null) {
                Icon(
                    source = icon,
                    tint = appearance.contentColor,
                    size = appearance.iconSize,
                )
            }
            Text(
                text = text,
                style = appearance.textStyle,
                color = appearance.contentColor,
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
