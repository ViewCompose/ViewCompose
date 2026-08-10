package com.viewcompose.ui.foundation

import com.viewcompose.ui.layout.BoxAlignment
import com.viewcompose.ui.layout.VerticalAlignment
import com.viewcompose.ui.modifier.Modifier
import com.viewcompose.ui.modifier.padding
import com.viewcompose.ui.node.ImageSource
import com.viewcompose.ui.node.UiStateLayerColors
import com.viewcompose.ui.modifier.SemanticsRole
import com.viewcompose.ui.unit.UiDp

/**
 * Defines fully resolved values for a [BasicButton] action composite.
 *
 * This Q2 snapshot contains no design-system variant or theme lookup. A design-system recipe
 * resolves enabled or disabled values before constructing it.
 *
 * @property surface resolved container fill, shape, border, clipping, elevation, and shadows
 * @property contentColor packed ARGB color used for the label and decorative icons
 * @property textStyle resolved label typography
 * @property stateLayerColors resolved pressed, focused, and hovered colors
 * @property minimumWidth non-negative minimum effective action width in dp
 * @property minimumHeight non-negative minimum effective action height in dp
 * @property visualHeight optional non-negative container height centered inside effective bounds
 * @property paddingHorizontal non-negative content padding on each horizontal edge in dp
 * @property paddingVertical non-negative content padding on each vertical edge in dp
 * @property iconSize non-negative icon width and height in dp
 * @property iconSpacing non-negative gap between an icon and the label in dp
 * @throws IllegalArgumentException if a dimension is negative
 */
data class BasicButtonStyle(
    val surface: BasicSurfaceStyle,
    val contentColor: Int,
    val textStyle: UiTextStyle,
    val stateLayerColors: UiStateLayerColors,
    val minimumWidth: UiDp = UiDp.Zero,
    val minimumHeight: UiDp = UiDp.Zero,
    val visualHeight: UiDp? = null,
    val paddingHorizontal: UiDp = UiDp.Zero,
    val paddingVertical: UiDp = UiDp.Zero,
    val iconSize: UiDp = UiDp.Zero,
    val iconSpacing: UiDp = UiDp.Zero,
) {
    init {
        require(minimumWidth >= UiDp.Zero) { "BasicButtonStyle minimumWidth must be non-negative." }
        require(minimumHeight >= UiDp.Zero) { "BasicButtonStyle minimumHeight must be non-negative." }
        require(visualHeight == null || visualHeight >= UiDp.Zero) {
            "BasicButtonStyle visualHeight must be non-negative when specified."
        }
        require(paddingHorizontal >= UiDp.Zero) { "BasicButtonStyle paddingHorizontal must be non-negative." }
        require(paddingVertical >= UiDp.Zero) { "BasicButtonStyle paddingVertical must be non-negative." }
        require(iconSize >= UiDp.Zero) { "BasicButtonStyle iconSize must be non-negative." }
        require(iconSpacing >= UiDp.Zero) { "BasicButtonStyle iconSpacing must be non-negative." }
    }
}

/**
 * Emits a design-system-neutral text action composed from [BasicSurface], Row, Text, and Icon.
 *
 * The action deliberately emits no `NodeType.Button`; native Button remains available through the
 * existing high-level `Button` API for compatibility. The complete effective View is clickable,
 * focusable through Android's click contract, and exposed with Button semantics. Decorative icons
 * inherit the resolved content color and do not create separate accessibility nodes.
 *
 * @sample com.viewcompose.ui.foundation.samples.basicButtonSample
 *
 * @receiver active tree builder receiving the action composite
 * @param text localized action label
 * @param onClick optional callback invoked synchronously after the renderer accepts an enabled
 * click; `null` keeps Button semantics but installs no action
 * @param style fully resolved action values for the current enabled state
 * @param enabled whether input, state layers, and enabled semantics are active
 * @param leadingIcon optional decorative icon before [text]
 * @param trailingIcon optional decorative icon after [text]
 * @param key optional stable sibling identity for the root surface
 * @param modifier caller configuration appended to the root surface
 */
fun UiTreeBuilder.BasicButton(
    text: String,
    onClick: (() -> Unit)?,
    style: BasicButtonStyle,
    enabled: Boolean = true,
    leadingIcon: ImageSource? = null,
    trailingIcon: ImageSource? = null,
    key: Any? = null,
    modifier: Modifier = Modifier,
) {
    val spacing = if (leadingIcon != null || trailingIcon != null) style.iconSpacing else UiDp.Zero
    BasicSurface(
        style = style.surface,
        contentColor = style.contentColor,
        enabled = enabled,
        onClick = onClick,
        stateLayerColors = style.stateLayerColors,
        rippleColor = if (enabled && onClick != null) {
            style.stateLayerColors.pressedColor
        } else {
            0x00000000
        },
        minimumWidth = style.minimumWidth,
        minimumHeight = style.minimumHeight,
        visualHeight = style.visualHeight,
        role = SemanticsRole.Button,
        key = key,
        contentAlignment = BoxAlignment.Center,
        modifier = modifier,
    ) {
        Row(
            spacing = spacing,
            verticalAlignment = VerticalAlignment.Center,
            modifier = Modifier.padding(
                horizontal = style.paddingHorizontal,
                vertical = style.paddingVertical,
            ),
        ) {
            if (leadingIcon != null) {
                Icon(
                    source = leadingIcon,
                    tint = style.contentColor,
                    size = style.iconSize,
                )
            }
            Text(
                text = text,
                color = style.contentColor,
                style = style.textStyle,
                maxLines = 1,
            )
            if (trailingIcon != null) {
                Icon(
                    source = trailingIcon,
                    tint = style.contentColor,
                    size = style.iconSize,
                )
            }
        }
    }
}
