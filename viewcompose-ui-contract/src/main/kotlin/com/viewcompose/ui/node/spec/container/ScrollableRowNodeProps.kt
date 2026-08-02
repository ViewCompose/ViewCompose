package com.viewcompose.ui.node.spec

import com.viewcompose.ui.unit.UiDp

import com.viewcompose.ui.layout.MainAxisArrangement
import com.viewcompose.ui.layout.VerticalAlignment

/**
 * Immutable renderer properties for an eagerly composed horizontal scrolling container.
 *
 * @property spacing fixed spacing between adjacent children
 * @property arrangement main-axis placement when content is smaller than the viewport
 * @property verticalAlignment default cross-axis child alignment
 */
data class ScrollableRowNodeProps(
    val spacing: UiDp,
    val arrangement: MainAxisArrangement,
    val verticalAlignment: VerticalAlignment,
) : NodeSpec
