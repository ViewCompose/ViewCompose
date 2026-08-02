package com.viewcompose.ui.node.spec

import com.viewcompose.ui.node.ImageSource
import com.viewcompose.ui.shape.UiShape
import com.viewcompose.ui.unit.UiDp
import com.viewcompose.ui.unit.UiSp

/**
 * Immutable renderer properties for a text button.
 *
 * @property text visible label, or `null` for no label
 * @property enabled whether the button accepts input and exposes an enabled semantic state
 * @property onClick callback invoked for an accepted click, or `null` for no action
 * @property textColor label color
 * @property textSizeSp label size in scale-independent pixels
 * @property fontWeight optional platform font weight override
 * @property fontFamily optional renderer-compatible font family
 * @property letterSpacingEm optional letter spacing in em units
 * @property lineHeightSp optional line height in scale-independent pixels
 * @property includeFontPadding whether platform font top and bottom padding is included
 * @property backgroundColor button surface color
 * @property borderWidth border width, normally non-negative
 * @property borderColor border color
 * @property shape outline used for background, border, clipping, and ripple
 * @property rippleColor pressed-state ripple color
 * @property minHeight minimum button height
 * @property paddingHorizontal content padding on the start and end edges
 * @property paddingVertical content padding on the top and bottom edges
 * @property leadingIcon optional resource icon before the label
 * @property trailingIcon optional resource icon after the label
 * @property iconTint tint applied to resource icons
 * @property iconSize requested icon width and height
 * @property iconSpacing spacing between an icon and the label
 */
data class ButtonNodeProps(
    val text: CharSequence?,
    val enabled: Boolean,
    val onClick: (() -> Unit)?,
    val textColor: Int,
    val textSizeSp: UiSp,
    val fontWeight: Int? = null,
    val fontFamily: UiFontFamily? = null,
    val letterSpacingEm: Float? = null,
    val lineHeightSp: UiSp? = null,
    val includeFontPadding: Boolean = false,
    val backgroundColor: Int,
    val borderWidth: UiDp,
    val borderColor: Int,
    val shape: UiShape,
    val rippleColor: Int,
    val minHeight: UiDp,
    val paddingHorizontal: UiDp,
    val paddingVertical: UiDp,
    val leadingIcon: ImageSource.Resource?,
    val trailingIcon: ImageSource.Resource?,
    val iconTint: Int,
    val iconSize: UiDp,
    val iconSpacing: UiDp,
) : NodeSpec
