package com.viewcompose.ui.node.spec

import com.viewcompose.ui.unit.UiDp

import com.viewcompose.ui.layout.MainAxisArrangement
import com.viewcompose.ui.layout.VerticalAlignment

/**
 * Immutable renderer properties for a horizontal linear container.
 *
 * @property spacing fixed spacing between adjacent children
 * @property arrangement main-axis placement of the complete child group
 * @property verticalAlignment default cross-axis child alignment
 */
data class RowNodeProps(
    val spacing: UiDp,
    val arrangement: MainAxisArrangement,
    val verticalAlignment: VerticalAlignment,
) : NodeSpec
