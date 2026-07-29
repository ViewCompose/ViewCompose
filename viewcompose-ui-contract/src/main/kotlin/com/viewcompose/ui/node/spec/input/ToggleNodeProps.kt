package com.viewcompose.ui.node.spec

import com.viewcompose.ui.unit.UiSp

/**
 * Toggle 类节点的选中状态、文案和交互属性。
 * Checked state, label, and interaction properties for toggle-like nodes.
 */
data class ToggleNodeProps(
    val text: CharSequence?,
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
    val rippleColor: Int,
) : NodeSpec
