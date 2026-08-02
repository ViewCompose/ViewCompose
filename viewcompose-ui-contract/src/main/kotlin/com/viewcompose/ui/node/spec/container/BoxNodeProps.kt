package com.viewcompose.ui.node.spec

import com.viewcompose.ui.layout.BoxAlignment

/**
 * Immutable renderer properties for an overlaying box container.
 *
 * @property contentAlignment default alignment for children without explicit box parent data
 * @property rippleColor optional container-level pressed-state ripple color
 */
data class BoxNodeProps(
    val contentAlignment: BoxAlignment,
    val rippleColor: Int? = null,
) : NodeSpec
