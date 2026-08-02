package com.viewcompose.ui.graphics

import com.viewcompose.ui.unit.UiDp

/**
 * Describes one platform-neutral shadow layer around a node outline.
 *
 * All distances remain in dp until a shadow backend renders the layer. [spreadRadius] may be
 * negative to contract the shadow mask; [blurRadius] may not be negative. This contract describes
 * geometry and color only and does not require a particular Android shadow implementation.
 *
 * @property color packed ARGB shadow color
 * @property blurRadius finite, non-negative blur radius in dp
 * @property spreadRadius finite amount added to the outline before blur; negative values contract it
 * @property offsetX finite horizontal offset in dp, positive toward the physical right
 * @property offsetY finite vertical offset in dp, positive downward
 * @throws IllegalArgumentException if a distance is non-finite or [blurRadius] is negative
 */
data class UiShadow(
    val color: Int = DefaultColor,
    val blurRadius: UiDp,
    val spreadRadius: UiDp = UiDp.Zero,
    val offsetX: UiDp = UiDp.Zero,
    val offsetY: UiDp = UiDp.Zero,
) {
    init {
        require(blurRadius.value.isFinite() && blurRadius >= UiDp.Zero) {
            "UiShadow.blurRadius must be finite and non-negative."
        }
        require(spreadRadius.value.isFinite()) {
            "UiShadow.spreadRadius must be finite."
        }
        require(offsetX.value.isFinite()) {
            "UiShadow.offsetX must be finite."
        }
        require(offsetY.value.isFinite()) {
            "UiShadow.offsetY must be finite."
        }
    }

    /** Provides default values shared by shadow-producing APIs. */
    companion object {
        /** Default 25%-opaque black color encoded as packed ARGB. */
        const val DefaultColor: Int = 0x40000000
    }
}
