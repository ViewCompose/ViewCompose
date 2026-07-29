package com.viewcompose.ui.node.spec
import com.viewcompose.ui.unit.UiDp

/**
 * FlowRow 节点的间距属性。
 * Spacing properties for a FlowRow node.
 */
data class FlowRowNodeProps(
    val horizontalSpacing: UiDp,
    val verticalSpacing: UiDp,
    val maxItemsInEachRow: Int,
) : NodeSpec
