package com.viewcompose.ui.node.spec

import com.viewcompose.ui.layout.HorizontalAlignment
import com.viewcompose.ui.layout.MainAxisArrangement
import com.viewcompose.ui.state.ScrollState
import com.viewcompose.ui.unit.UiDp

/**
 * Immutable renderer properties for an eagerly composed vertical scrolling container.
 *
 * @property spacing fixed spacing between adjacent children
 * @property arrangement main-axis placement when content is smaller than the viewport
 * @property horizontalAlignment default cross-axis child alignment
 * @property state optional externally owned observable scroll state and command handle
 * @property userScrollEnabled whether direct pointer scrolling is accepted
 * @property focusFollowKeyboard whether focus navigation may scroll focused content into view
 */
data class ScrollableColumnNodeProps(
    val spacing: UiDp,
    val arrangement: MainAxisArrangement,
    val horizontalAlignment: HorizontalAlignment,
    val state: ScrollState? = null,
    val userScrollEnabled: Boolean = true,
    val focusFollowKeyboard: Boolean = false,
) : NodeSpec
