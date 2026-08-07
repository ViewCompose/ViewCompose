package com.viewcompose.ui.node.spec

import com.viewcompose.ui.layout.BoxAlignment
import com.viewcompose.ui.node.UiStateLayerColors

/**
 * Immutable renderer properties for an overlaying box container.
 *
 * @property contentAlignment default alignment for children without explicit box parent data
 * @property rippleColor optional container-level pressed-state ripple color
 * @property stateLayerColors optional enabled-state interaction colors; `null` preserves the
 * single-color [rippleColor] compatibility behavior
 */
data class BoxNodeProps(
    val contentAlignment: BoxAlignment,
    val rippleColor: Int? = null,
    val stateLayerColors: UiStateLayerColors? = null,
) : NodeSpec
