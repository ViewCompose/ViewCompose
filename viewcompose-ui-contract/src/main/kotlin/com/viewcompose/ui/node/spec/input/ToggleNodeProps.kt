package com.viewcompose.ui.node.spec

import com.viewcompose.ui.unit.UiSp

/**
 * Immutable renderer properties shared by checkbox, radio, switch, and toggle nodes.
 *
 * @property text immutable plain-text label, or `null` for no label
 * @property enabled whether the control accepts input
 * @property checked externally controlled selected state
 * @property controlColor default control color
 * @property thumbColor optional switch thumb color
 * @property trackColor optional switch track color
 * @property checkedColor optional color used for the checked state
 * @property uncheckedColor optional color used for the unchecked state
 * @property onCheckedChange callback receiving an accepted checked state
 * @property textColor label color
 * @property textSizeSp label size in scale-independent pixels
 * @property fontWeight optional platform label font weight
 * @property fontFamily optional renderer-compatible label font family
 * @property letterSpacingEm optional label letter spacing in em units
 * @property lineHeightSp optional label line height
 * @property includeFontPadding whether platform font top and bottom padding is included
 */
data class ToggleNodeProps(
    val text: String?,
    val enabled: Boolean,
    val checked: Boolean,
    val controlColor: Int,
    val thumbColor: Int? = null,
    val trackColor: Int? = null,
    val checkedColor: Int? = null,
    val uncheckedColor: Int? = null,
    val onCheckedChange: ((Boolean) -> Unit)?,
    val textColor: Int,
    val textSizeSp: UiSp,
    val fontWeight: Int? = null,
    val fontFamily: UiFontFamily? = null,
    val letterSpacingEm: Float? = null,
    val lineHeightSp: UiSp? = null,
    val includeFontPadding: Boolean = false,
) : NodeSpec
