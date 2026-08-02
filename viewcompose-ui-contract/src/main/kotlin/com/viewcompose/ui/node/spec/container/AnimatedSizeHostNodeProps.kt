package com.viewcompose.ui.node.spec

import com.viewcompose.ui.modifier.ContentSizeAnimationSpecModel

/**
 * Immutable renderer properties for a host that animates content-size changes.
 *
 * @property animationSpec duration and easing model for the size transition
 */
data class AnimatedSizeHostNodeProps(
    val animationSpec: ContentSizeAnimationSpecModel,
) : NodeSpec
