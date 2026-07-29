package com.viewcompose.ui.node.spec

import com.viewcompose.ui.node.SegmentedControlItem
import com.viewcompose.ui.shape.UiShape
import com.viewcompose.ui.unit.UiDp
import com.viewcompose.ui.unit.UiSp

/**
 * SegmentedControl 节点传给 renderer 的完整属性快照。
 * Complete property snapshot passed to the renderer for a SegmentedControl node.
 */
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
    val textSizeSp: UiSp,
    val fontWeight: Int? = null,
    val fontFamily: UiFontFamily? = null,
    val letterSpacingEm: Float? = null,
    val lineHeightSp: UiSp? = null,
    val includeFontPadding: Boolean = false,
    val paddingHorizontal: UiDp,
    val paddingVertical: UiDp,
) : NodeSpec
