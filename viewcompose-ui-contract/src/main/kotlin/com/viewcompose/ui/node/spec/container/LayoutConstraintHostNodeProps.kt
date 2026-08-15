package com.viewcompose.ui.node.spec

import com.viewcompose.ui.unit.UiDp

/**
 * Immutable renderer contract for one portable measurement-constraint boundary.
 *
 * Custom renderers must constrain the complete node, not just a mapped native widget's content,
 * and must treat [fillWidth] and [fillHeight] as bounded fill requests before applying [aspectRatio].
 *
 * @property maxWidth optional maximum measured width
 * @property maxHeight optional maximum measured height
 * @property aspectRatio optional positive width-to-height ratio
 * @property matchHeightConstraintsFirst whether ratio selection prefers height constraints
 * @property fillWidth whether available bounded width should be consumed
 * @property fillHeight whether available bounded height should be consumed
 * @throws IllegalArgumentException when a maximum or ratio is not positive and finite
 */
data class LayoutConstraintHostNodeProps(
    val maxWidth: UiDp?,
    val maxHeight: UiDp?,
    val aspectRatio: Float?,
    val matchHeightConstraintsFirst: Boolean,
    val fillWidth: Boolean,
    val fillHeight: Boolean,
) : NodeSpec {
    init {
        require(maxWidth == null || (maxWidth.value.isFinite() && maxWidth.value > 0f)) {
            "Layout constraint maxWidth must be positive and finite."
        }
        require(maxHeight == null || (maxHeight.value.isFinite() && maxHeight.value > 0f)) {
            "Layout constraint maxHeight must be positive and finite."
        }
        require(aspectRatio == null || (aspectRatio.isFinite() && aspectRatio > 0f)) {
            "Layout constraint aspectRatio must be positive and finite."
        }
    }
}
