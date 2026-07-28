package com.viewcompose.ui.node.spec

import com.viewcompose.ui.layout.BoxAlignment

/**
 * Box 节点的默认对齐属性。
 * Default alignment properties for a Box node.
 */
data class BoxNodeProps(
    val contentAlignment: BoxAlignment,
    val rippleColor: Int? = null,
) : NodeSpec
