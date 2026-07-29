package com.viewcompose.ui.node.spec
import com.viewcompose.ui.unit.UiDp

/**
 * FlowColumn 节点的间距属性。
 * Spacing properties for a FlowColumn node.
 */
data class FlowColumnNodeProps(
    val horizontalSpacing: UiDp,
    val verticalSpacing: UiDp,
    val maxItemsInEachColumn: Int,
) : NodeSpec
