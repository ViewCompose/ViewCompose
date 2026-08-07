package com.viewcompose.ui.node.spec

import com.viewcompose.ui.unit.UiDp

import com.viewcompose.ui.layout.MainAxisArrangement
import com.viewcompose.ui.layout.VerticalAlignment
import com.viewcompose.ui.node.UiStateLayerColors

/**
 * Immutable renderer properties for a horizontal linear container.
 *
 * @property spacing fixed spacing between adjacent children
 * @property arrangement main-axis placement of the complete child group
 * @property verticalAlignment default cross-axis child alignment
 * @property rippleColor optional container-level pressed-state ripple color
 * @property stateLayerColors optional enabled-state interaction colors; `null` preserves the
 * single-color [rippleColor] compatibility behavior
 */
data class RowNodeProps(
    val spacing: UiDp,
    val arrangement: MainAxisArrangement,
    val verticalAlignment: VerticalAlignment,
    val rippleColor: Int? = null,
    val stateLayerColors: UiStateLayerColors? = null,
) : NodeSpec
