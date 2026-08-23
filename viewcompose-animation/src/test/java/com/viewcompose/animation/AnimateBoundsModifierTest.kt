package com.viewcompose.animation

import com.viewcompose.animation.core.SpringSpec
import com.viewcompose.animation.core.TweenSpec
import com.viewcompose.animation.core.spring
import com.viewcompose.animation.core.tween
import com.viewcompose.ui.modifier.AnimateBoundsModifierElement
import com.viewcompose.ui.modifier.ContentSizeSpringSpecModel
import com.viewcompose.ui.modifier.ContentSizeTweenSpecModel
import com.viewcompose.ui.modifier.Modifier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AnimateBoundsModifierTest {
    @Test
    fun `default bounds animation serializes one physical spring`() {
        val element = Modifier.animateBounds().elements.single() as AnimateBoundsModifierElement

        assertTrue(element.animationSpec is ContentSizeSpringSpecModel)
    }

    @Test
    fun `bounds animation preserves finite duration and physical configuration`() {
        val tweenSpec = tween(durationMillis = 320, delayMillis = 24)
        val springSpec = spring(dampingRatio = 0.72f, stiffness = 260f, maxDurationMillis = 1_800)

        val tweenElement = Modifier.animateBounds(tweenSpec).elements.single() as AnimateBoundsModifierElement
        val springElement = Modifier.animateBounds(springSpec).elements.single() as AnimateBoundsModifierElement

        assertEquals(
            (tweenSpec as TweenSpec).durationMillis,
            (tweenElement.animationSpec as ContentSizeTweenSpecModel).durationMillis,
        )
        assertEquals(
            (springSpec as SpringSpec).stiffness,
            (springElement.animationSpec as ContentSizeSpringSpecModel).stiffness,
        )
    }
}
