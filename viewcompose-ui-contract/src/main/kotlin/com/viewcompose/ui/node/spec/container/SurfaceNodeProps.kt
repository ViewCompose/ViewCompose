package com.viewcompose.ui.node.spec

import com.viewcompose.graphics.core.Brush
import com.viewcompose.ui.layout.BoxAlignment
import com.viewcompose.ui.shape.UiShape
import com.viewcompose.ui.unit.UiDp

/**
 * Carries resolved, design-system-neutral properties for a styled surface container.
 *
 * The renderer applies [fill], [shape], and [borderWidth] to one shared geometry. Caller modifiers
 * may override those visual properties; an override also disables [visualHeight] insetting so the
 * application-owned surface occupies the complete effective bounds.
 *
 * @property contentAlignment default alignment for children without explicit box parent data
 * @property fill immutable color or gradient brush in the surface's local pixel coordinate space
 * @property shape logical-corner geometry shared by fill, border, interaction mask, clip, and outline
 * @property borderWidth non-negative border thickness in dp
 * @property borderColor packed ARGB border color
 * @property minimumWidth non-negative minimum effective width in dp
 * @property minimumHeight non-negative minimum effective height in dp
 * @property visualHeight optional non-negative surface height centered inside the effective bounds;
 * `null` makes the surface fill the measured height
 * @property clipContent whether content is clipped to [shape] in the absence of a caller override
 * @throws IllegalArgumentException if a dimension is negative
 */
data class SurfaceNodeProps(
    val contentAlignment: BoxAlignment,
    val fill: Brush,
    val shape: UiShape,
    val borderWidth: UiDp = UiDp.Zero,
    val borderColor: Int = 0x00000000,
    val minimumWidth: UiDp = UiDp.Zero,
    val minimumHeight: UiDp = UiDp.Zero,
    val visualHeight: UiDp? = null,
    val clipContent: Boolean = false,
) : NodeSpec {
    init {
        require(borderWidth >= UiDp.Zero) { "Surface borderWidth must be non-negative." }
        require(minimumWidth >= UiDp.Zero) { "Surface minimumWidth must be non-negative." }
        require(minimumHeight >= UiDp.Zero) { "Surface minimumHeight must be non-negative." }
        require(visualHeight == null || visualHeight >= UiDp.Zero) {
            "Surface visualHeight must be non-negative when specified."
        }
    }
}
