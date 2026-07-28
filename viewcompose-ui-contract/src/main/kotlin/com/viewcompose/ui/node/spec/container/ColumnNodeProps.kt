package com.viewcompose.ui.node.spec

import com.viewcompose.ui.layout.HorizontalAlignment
import com.viewcompose.ui.layout.MainAxisArrangement

/**
 * Column 节点的间距和水平对齐属性。
 * Spacing and horizontal-alignment properties for a Column node.
 */
data class ColumnNodeProps(
    val spacing: Int,
    val arrangement: MainAxisArrangement,
    val horizontalAlignment: HorizontalAlignment,
) : NodeSpec
