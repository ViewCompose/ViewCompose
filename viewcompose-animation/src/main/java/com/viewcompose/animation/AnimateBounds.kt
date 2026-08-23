package com.viewcompose.animation

import com.viewcompose.animation.core.FiniteAnimationSpec
import com.viewcompose.animation.core.spring
import com.viewcompose.ui.modifier.AnimateBoundsModifierElement
import com.viewcompose.ui.modifier.Modifier

/**
 * Animates changes to a node's real position and size in its immediate layout parent.
 *
 * The first accepted layout is settled. A later layout target is measured once and becomes the
 * endpoint for all four physical-pixel rectangle edges after logical start/end and RTL resolution.
 * Every committed frame updates real Android layout geometry, so drawing, pointer input, focus, and
 * accessibility bounds remain aligned. Parent scrolling moves the complete local coordinate system;
 * reparenting ends the old local animation and starts the destination at its settled layout.
 *
 * A target change cancels the previous writer and retargets from the currently committed rectangle.
 * Physical springs retain sampled edge velocity; duration specifications restart from zero velocity.
 * Detaching the host cancels motion and the next attachment starts settled. Infinite specifications
 * are excluded by [FiniteAnimationSpec]. Combining this modifier with `animateContentSize` on the
 * same node is invalid because both would own size; rendering rejects that candidate before native
 * mutation. If several `animateBounds` elements occur on one node, the last specification wins.
 * Modifier order does not split layout ownership: parent-data, size, margin, alignment, offset,
 * visibility, and z-index elements on the same chain are promoted to the bounds host, while draw,
 * input, and content elements remain on the animated child.
 *
 * The renderer adds one transparent native layout host. Target changes require one target
 * measurement; animation frames reuse that measurement and perform layout without remeasurement.
 *
 * @sample com.viewcompose.animation.samples.animateBoundsSample
 *
 * @receiver modifier chain for the node whose parent-local rectangle should animate
 * @param animationSpec finite timing or physical policy shared by all four rectangle edges
 * @return a modifier chain containing the real-bounds animation instruction
 */
fun Modifier.animateBounds(
    animationSpec: FiniteAnimationSpec = spring(),
): Modifier {
    return then(
        AnimateBoundsModifierElement(
            animationSpec = animationSpec.toLayoutAnimationSpecModel(),
        ),
    )
}
