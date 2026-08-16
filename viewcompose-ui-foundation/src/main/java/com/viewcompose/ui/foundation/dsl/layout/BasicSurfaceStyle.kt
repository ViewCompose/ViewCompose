package com.viewcompose.ui.foundation

import com.viewcompose.graphics.core.Brush
import com.viewcompose.ui.graphics.UiShadow
import com.viewcompose.ui.node.UiInteractionIndication
import com.viewcompose.ui.shape.UiShape
import com.viewcompose.ui.unit.UiDp

/**
 * Defines fully resolved visual values consumed by [BasicSurface].
 *
 * This Q2 value contains no theme lookup or design-system identity. Brush coordinates use the
 * rendered surface's local pixel coordinate space. Rendering snapshots the shadow lists at
 * construction, preserving declaration order even if a caller supplied a mutable list.
 *
 * @property fill solid or gradient fill drawn inside [shape]
 * @property shape geometry shared by fill, border, ripple, clipping, shadows, and diagnostics
 * @property borderWidth non-negative border thickness in dp
 * @property borderColor packed ARGB border color
 * @property elevation non-negative Android platform elevation in dp
 * @property dropShadows exact outer shadows drawn behind the surface in declaration order
 * @property innerShadows exact inner shadows drawn over surface content in declaration order
 * @property clipContent whether descendants are clipped to [shape]
 * @property interactionIndication optional renderer-neutral feedback resolved by the component
 * @throws IllegalArgumentException if [borderWidth] or [elevation] is negative
 */
data class BasicSurfaceStyle(
    val fill: Brush,
    val shape: UiShape,
    val borderWidth: UiDp = UiDp.Zero,
    val borderColor: Int = 0x00000000,
    val elevation: UiDp = UiDp.Zero,
    val dropShadows: List<UiShadow> = emptyList(),
    val innerShadows: List<UiShadow> = emptyList(),
    val clipContent: Boolean = false,
    val interactionIndication: UiInteractionIndication? = null,
) {
    init {
        require(borderWidth >= UiDp.Zero) { "BasicSurfaceStyle borderWidth must be non-negative." }
        require(elevation >= UiDp.Zero) { "BasicSurfaceStyle elevation must be non-negative." }
    }

    internal val stableDropShadows: List<UiShadow> = dropShadows.toList()
    internal val stableInnerShadows: List<UiShadow> = innerShadows.toList()
}
