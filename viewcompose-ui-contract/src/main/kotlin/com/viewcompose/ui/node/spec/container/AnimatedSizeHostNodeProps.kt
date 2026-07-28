package com.viewcompose.ui.node.spec

import com.viewcompose.ui.modifier.ContentSizeAnimationSpecModel

/**
 * AnimatedSizeHost 节点用于尺寸动画的属性。
 * Properties used by an AnimatedSizeHost node for size animation.
 */
data class AnimatedSizeHostNodeProps(
    val animationSpec: ContentSizeAnimationSpecModel,
) : NodeSpec
