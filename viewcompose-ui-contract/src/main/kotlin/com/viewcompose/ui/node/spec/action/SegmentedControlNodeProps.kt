package com.viewcompose.ui.node.spec

import com.viewcompose.ui.node.SegmentedControlItem
import com.viewcompose.ui.shape.UiShape

data class SegmentedControlNodeProps(
    val items: List<SegmentedControlItem>,
    val selectedIndex: Int,
    val onSelectionChange: ((Int) -> Unit)?,
    val enabled: Boolean,
    val backgroundColor: Int,
    val indicatorColor: Int,
    val shape: UiShape,
    val textColor: Int,
    val selectedTextColor: Int,
    val rippleColor: Int,
    val textSizeSp: Int,
    val fontWeight: Int? = null,
    val fontFamily: UiFontFamily? = null,
    val letterSpacingEm: Float? = null,
    val lineHeightSp: Int? = null,
    val includeFontPadding: Boolean = false,
    val paddingHorizontal: Int,
    val paddingVertical: Int,
) : NodeSpec
