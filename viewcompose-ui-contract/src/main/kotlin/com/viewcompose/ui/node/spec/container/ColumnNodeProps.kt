package com.viewcompose.ui.node.spec

import com.viewcompose.ui.unit.UiDp

import com.viewcompose.ui.layout.HorizontalAlignment
import com.viewcompose.ui.layout.MainAxisArrangement

/**
 * Immutable renderer properties for a vertical linear container.
 *
 * @property spacing fixed spacing between adjacent children
 * @property arrangement main-axis placement of the complete child group
 * @property horizontalAlignment default cross-axis child alignment
 */
data class ColumnNodeProps(
    val spacing: UiDp,
    val arrangement: MainAxisArrangement,
    val horizontalAlignment: HorizontalAlignment,
) : NodeSpec
