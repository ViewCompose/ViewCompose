package com.viewcompose.ui.node.spec

import com.viewcompose.ui.layout.BoxAlignment
import com.viewcompose.ui.modifier.TransformOrigin

/**
 * Immutable renderer properties for one parent or descendant visibility-transition frame.
 *
 * @sample com.viewcompose.ui.samples.animatedVisibilityHostNodeContractSample
 *
 * @property alpha content opacity for the current frame
 * @property widthScale measured-width reveal fraction for the current frame
 * @property heightScale measured-height reveal fraction for the current frame
 * @property scaleX visual horizontal scale applied after measured-size reveal
 * @property scaleY visual vertical scale applied after measured-size reveal
 * @property translationXFraction horizontal translation as a fraction of full measured child width
 * @property translationYFraction vertical translation as a fraction of full measured child height
 * @property transformOrigin fractional pivot used by visual scale
 * @property contentAlignment placement of full-size content inside animated reveal bounds
 * @property clipToBounds whether drawing outside the animated host bounds is clipped
 * @property active whether descendants may own input, focus, or accessibility
 * @throws IllegalArgumentException if any sampled transform or pivot fraction is non-finite
 */
data class AnimatedVisibilityHostNodeProps(
    val alpha: Float,
    val widthScale: Float,
    val heightScale: Float,
    val scaleX: Float = 1f,
    val scaleY: Float = 1f,
    val translationXFraction: Float = 0f,
    val translationYFraction: Float = 0f,
    val transformOrigin: TransformOrigin = TransformOrigin.Center,
    val contentAlignment: BoxAlignment = BoxAlignment.TopStart,
    val clipToBounds: Boolean,
    val active: Boolean = true,
) : NodeSpec {
    init {
        require(
            alpha.isFinite() &&
                widthScale.isFinite() &&
                heightScale.isFinite() &&
                scaleX.isFinite() &&
                scaleY.isFinite() &&
                translationXFraction.isFinite() &&
                translationYFraction.isFinite() &&
                transformOrigin.pivotFractionX.isFinite() &&
                transformOrigin.pivotFractionY.isFinite(),
        ) {
            "AnimatedVisibilityHostNodeProps transform values must be finite."
        }
    }
}
