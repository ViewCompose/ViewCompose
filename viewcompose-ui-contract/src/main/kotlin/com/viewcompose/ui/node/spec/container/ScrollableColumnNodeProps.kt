package com.viewcompose.ui.node.spec

import com.viewcompose.ui.layout.HorizontalAlignment
import com.viewcompose.ui.layout.MainAxisArrangement

/**
 * ScrollableColumn 节点的间距、内边距和滚动状态属性。
 * Spacing, padding, and scroll-state properties for a ScrollableColumn node.
 */
data class ScrollableColumnNodeProps(
    val spacing: Int,
    val arrangement: MainAxisArrangement,
    val horizontalAlignment: HorizontalAlignment,
    val focusFollowKeyboard: Boolean = false,
) : NodeSpec
