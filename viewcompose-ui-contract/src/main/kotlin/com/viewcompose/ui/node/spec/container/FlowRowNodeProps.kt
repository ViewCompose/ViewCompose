package com.viewcompose.ui.node.spec

import com.viewcompose.ui.unit.UiDp

/**
 * Immutable renderer properties for a row-first wrapping flow.
 *
 * @property horizontalSpacing spacing between adjacent items in a row
 * @property verticalSpacing spacing between adjacent generated rows
 * @property maxItemsInEachRow maximum items placed before wrapping to the next row
 */
data class FlowRowNodeProps(
    val horizontalSpacing: UiDp,
    val verticalSpacing: UiDp,
    val maxItemsInEachRow: Int,
) : NodeSpec
