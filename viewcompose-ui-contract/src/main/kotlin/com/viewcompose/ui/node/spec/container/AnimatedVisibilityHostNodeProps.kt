package com.viewcompose.ui.node.spec

/**
 * AnimatedVisibilityHost 节点用于可见性过渡的属性。
 * Properties used by an AnimatedVisibilityHost node for visibility transitions.
 */
data class AnimatedVisibilityHostNodeProps(
    val alpha: Float,
    val widthScale: Float,
    val heightScale: Float,
    val clipToBounds: Boolean,
) : NodeSpec
