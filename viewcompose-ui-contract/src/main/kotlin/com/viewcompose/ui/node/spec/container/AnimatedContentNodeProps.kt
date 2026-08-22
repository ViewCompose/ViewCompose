package com.viewcompose.ui.node.spec

import com.viewcompose.ui.layout.BoxAlignment
import com.viewcompose.ui.modifier.TransformOrigin

/**
 * Immutable renderer properties for one frame of a keyed content-replacement container.
 *
 * The host receives either one settled item or an outgoing/incoming pair. [segmentId] changes once
 * per replacement and lets the renderer preserve the last committed container size when a running
 * segment is retargeted. [sizeProgress] is deliberately not clamped because a physical animation
 * may overshoot; the renderer clamps only the resulting dimensions to non-negative values.
 *
 * @sample com.viewcompose.ui.samples.animatedContentNodeContractSample
 *
 * @property segmentId monotonically changing identity of the logical replacement segment
 * @property sizeProgress normalized sampled progress from the retained size to the target size
 * @property sizeTransformEnabled whether the host interpolates size instead of using the maximum
 * current item size
 * @property clipToBounds whether drawing outside the animated container bounds is clipped
 * @property contentAlignment logical placement shared by outgoing and incoming items
 * @throws IllegalArgumentException if [sizeProgress] is non-finite
 */
data class AnimatedContentHostNodeProps(
    val segmentId: Long,
    val sizeProgress: Float,
    val sizeTransformEnabled: Boolean,
    val clipToBounds: Boolean,
    val contentAlignment: BoxAlignment,
) : NodeSpec {
    init {
        require(sizeProgress.isFinite()) {
            "AnimatedContentHostNodeProps.sizeProgress must be finite."
        }
    }
}

/**
 * Immutable visual and ownership properties for one content-replacement item.
 *
 * Translation is expressed as a fraction of this item's measured width and height, keeping the
 * contract platform-neutral until the Android renderer has exact dimensions. Reveal fractions
 * clip drawing without changing the full measurement supplied to the replacement container.
 * Exactly one item is [active] after a replacement commits; inactive items remain renderable but
 * cannot own pointer input, focus, or accessibility.
 *
 * @sample com.viewcompose.ui.samples.animatedContentNodeContractSample
 *
 * @property alpha item opacity for the current frame
 * @property scaleX horizontal drawing scale around [transformOrigin]
 * @property scaleY vertical drawing scale around [transformOrigin]
 * @property translationXFraction horizontal translation divided by measured item width
 * @property translationYFraction vertical translation divided by measured item height
 * @property revealWidthFraction horizontal fraction retained by the drawing clip
 * @property revealHeightFraction vertical fraction retained by the drawing clip
 * @property transformOrigin fractional pivot for scale transforms
 * @property active whether this item exclusively participates in input, focus, and accessibility
 * @throws IllegalArgumentException if a visual value or transform-origin fraction is non-finite
 */
data class AnimatedContentItemNodeProps(
    val alpha: Float,
    val scaleX: Float,
    val scaleY: Float,
    val translationXFraction: Float,
    val translationYFraction: Float,
    val revealWidthFraction: Float,
    val revealHeightFraction: Float,
    val transformOrigin: TransformOrigin,
    val active: Boolean,
) : NodeSpec {
    init {
        val finiteValues = listOf(
            alpha,
            scaleX,
            scaleY,
            translationXFraction,
            translationYFraction,
            revealWidthFraction,
            revealHeightFraction,
            transformOrigin.pivotFractionX,
            transformOrigin.pivotFractionY,
        )
        require(finiteValues.all(Float::isFinite)) {
            "AnimatedContentItemNodeProps visual values and transform origin must be finite."
        }
    }
}
