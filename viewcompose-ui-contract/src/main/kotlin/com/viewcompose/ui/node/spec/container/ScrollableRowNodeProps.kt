package com.viewcompose.ui.node.spec

import com.viewcompose.ui.layout.MainAxisArrangement
import com.viewcompose.ui.layout.VerticalAlignment

/**
 * ScrollableRow 节点的间距、内边距和滚动状态属性。
 * Spacing, padding, and scroll-state properties for a ScrollableRow node.
 */
data class ScrollableRowNodeProps(
    val spacing: Int,
    val arrangement: MainAxisArrangement,
    val verticalAlignment: VerticalAlignment,
) : NodeSpec
