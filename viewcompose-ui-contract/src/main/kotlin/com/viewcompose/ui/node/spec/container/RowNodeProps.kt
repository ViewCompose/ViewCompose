package com.viewcompose.ui.node.spec

import com.viewcompose.ui.layout.MainAxisArrangement
import com.viewcompose.ui.layout.VerticalAlignment

/**
 * Row 节点的间距和垂直对齐属性。
 * Spacing and vertical-alignment properties for a Row node.
 */
data class RowNodeProps(
    val spacing: Int,
    val arrangement: MainAxisArrangement,
    val verticalAlignment: VerticalAlignment,
) : NodeSpec
