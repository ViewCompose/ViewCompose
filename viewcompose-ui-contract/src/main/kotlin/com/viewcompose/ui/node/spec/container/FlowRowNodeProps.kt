package com.viewcompose.ui.node.spec

/**
 * FlowRow 节点的间距属性。
 * Spacing properties for a FlowRow node.
 */
data class FlowRowNodeProps(
    val horizontalSpacing: Int,
    val verticalSpacing: Int,
    val maxItemsInEachRow: Int,
) : NodeSpec
