package com.viewcompose.ui.node.spec

import com.viewcompose.ui.layout.HorizontalAlignment
import com.viewcompose.ui.layout.MainAxisArrangement
import com.viewcompose.ui.state.ScrollState
import com.viewcompose.ui.unit.UiDp

/**
 * Immutable renderer properties for an eagerly composed vertical scrolling container.
 *
 * Focused descendants use the renderer's native child-rectangle protocol without an opt-in field.
 *
 * @property spacing fixed spacing between adjacent children
 * @property arrangement main-axis placement when content is smaller than the viewport
 * @property horizontalAlignment default cross-axis child alignment
 * @property state optional externally owned observable scroll state and command handle
 * @property userScrollEnabled whether direct pointer scrolling is accepted
 */
data class ScrollableColumnNodeProps(
    val spacing: UiDp,
    val arrangement: MainAxisArrangement,
    val horizontalAlignment: HorizontalAlignment,
    val state: ScrollState? = null,
    val userScrollEnabled: Boolean = true,
) : NodeSpec
