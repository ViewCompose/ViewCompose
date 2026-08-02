package com.viewcompose.ui.node.spec

import com.viewcompose.ui.unit.UiDp

import com.viewcompose.ui.layout.HorizontalAlignment
import com.viewcompose.ui.layout.MainAxisArrangement

/**
 * Immutable renderer properties for an eagerly composed vertical scrolling container.
 *
 * @property spacing fixed spacing between adjacent children
 * @property arrangement main-axis placement when content is smaller than the viewport
 * @property horizontalAlignment default cross-axis child alignment
 * @property focusFollowKeyboard whether focus navigation may scroll focused content into view
 */
data class ScrollableColumnNodeProps(
    val spacing: UiDp,
    val arrangement: MainAxisArrangement,
    val horizontalAlignment: HorizontalAlignment,
    val focusFollowKeyboard: Boolean = false,
) : NodeSpec
