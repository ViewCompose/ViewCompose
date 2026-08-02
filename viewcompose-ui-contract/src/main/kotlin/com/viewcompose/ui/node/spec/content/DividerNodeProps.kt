package com.viewcompose.ui.node.spec

import com.viewcompose.ui.unit.UiDp

/**
 * Immutable renderer properties for a divider.
 *
 * Orientation is selected by the node type rather than this spec.
 *
 * @property color divider color
 * @property thickness requested cross-axis thickness
 */
data class DividerNodeProps(
    val color: Int,
    val thickness: UiDp,
) : NodeSpec
