package com.viewcompose.ui.node.spec

import com.viewcompose.ui.unit.UiDp

/**
 * Divider 节点的方向、厚度和颜色属性。
 * Orientation, thickness, and color properties for a Divider node.
 */
data class DividerNodeProps(
    val color: Int,
    val thickness: UiDp,
) : NodeSpec
