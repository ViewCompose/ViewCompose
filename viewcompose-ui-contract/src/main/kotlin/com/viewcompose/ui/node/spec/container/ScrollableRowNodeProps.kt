package com.viewcompose.ui.node.spec

import com.viewcompose.ui.layout.MainAxisArrangement
import com.viewcompose.ui.layout.VerticalAlignment
import com.viewcompose.ui.state.ScrollState
import com.viewcompose.ui.unit.UiDp

/**
 * Immutable renderer properties for an eagerly composed horizontal scrolling container.
 *
 * @property spacing fixed spacing between adjacent children
 * @property arrangement main-axis placement when content is smaller than the viewport
 * @property verticalAlignment default cross-axis child alignment
 * @property state optional externally owned observable scroll state and command handle
 * @property userScrollEnabled whether direct pointer scrolling is accepted
 */
data class ScrollableRowNodeProps(
    val spacing: UiDp,
    val arrangement: MainAxisArrangement,
    val verticalAlignment: VerticalAlignment,
    val state: ScrollState? = null,
    val userScrollEnabled: Boolean = true,
) : NodeSpec
