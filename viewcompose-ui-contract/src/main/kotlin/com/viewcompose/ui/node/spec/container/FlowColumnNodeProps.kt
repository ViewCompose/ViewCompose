package com.viewcompose.ui.node.spec

/**
 * FlowColumn 节点的间距属性。
 * Spacing properties for a FlowColumn node.
 */
data class FlowColumnNodeProps(
    val horizontalSpacing: Int,
    val verticalSpacing: Int,
    val maxItemsInEachColumn: Int,
) : NodeSpec
