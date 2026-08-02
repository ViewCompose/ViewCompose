package com.viewcompose.ui.node.spec

import com.viewcompose.ui.unit.UiDp

/**
 * Immutable renderer properties for a column-first wrapping flow.
 *
 * @property horizontalSpacing spacing between adjacent generated columns
 * @property verticalSpacing spacing between adjacent items in a column
 * @property maxItemsInEachColumn maximum items placed before wrapping to the next column
 */
data class FlowColumnNodeProps(
    val horizontalSpacing: UiDp,
    val verticalSpacing: UiDp,
    val maxItemsInEachColumn: Int,
) : NodeSpec
