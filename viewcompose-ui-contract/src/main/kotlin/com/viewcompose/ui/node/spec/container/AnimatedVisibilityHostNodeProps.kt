package com.viewcompose.ui.node.spec

/**
 * Immutable renderer properties for one frame of a visibility transition.
 *
 * @property alpha content opacity for the current frame
 * @property widthScale horizontal scale for the current frame
 * @property heightScale vertical scale for the current frame
 * @property clipToBounds whether drawing outside the animated host bounds is clipped
 */
data class AnimatedVisibilityHostNodeProps(
    val alpha: Float,
    val widthScale: Float,
    val heightScale: Float,
    val clipToBounds: Boolean,
) : NodeSpec
