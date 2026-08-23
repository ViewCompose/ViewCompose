package com.viewcompose.ui.node.spec

import com.viewcompose.ui.modifier.LayoutAnimationSpecModel

/**
 * Immutable renderer properties for a host that animates one real layout rectangle.
 *
 * The first accepted rectangle is settled. Later targets are expressed in the immediate
 * ViewCompose parent's physical-pixel coordinate system after layout-direction resolution. The
 * renderer must keep drawing, hit testing, focus, and accessibility geometry on the same committed
 * rectangle and must not substitute visual translation or scale.
 *
 * @sample com.viewcompose.ui.samples.animatedBoundsHostNodeContractSample
 * @property animationSpec finite duration or physical model shared by all four rectangle edges
 */
data class AnimatedBoundsHostNodeProps(
    val animationSpec: LayoutAnimationSpecModel,
) : NodeSpec
