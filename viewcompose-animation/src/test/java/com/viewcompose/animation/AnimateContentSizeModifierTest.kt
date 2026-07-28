package com.viewcompose.animation

/*
 * 测试职责：覆盖 animation DSL 中的 Animate Content Size Modifier 行为，防止关键契约在后续重构中回退。
 * Test responsibility: covers Animate Content Size Modifier behavior in animation DSL and guards the contract against regressions.
 */

import com.viewcompose.animation.core.keyframes
import com.viewcompose.animation.core.keyframe
import com.viewcompose.animation.core.repeatable
import com.viewcompose.animation.core.RepeatMode
import com.viewcompose.animation.core.infiniteRepeatable
import com.viewcompose.animation.core.snap
import com.viewcompose.animation.core.spring
import com.viewcompose.animation.core.tween
import com.viewcompose.ui.modifier.AnimateContentSizeModifierElement
import com.viewcompose.ui.modifier.ContentSizeEasingModel
import com.viewcompose.ui.modifier.ContentSizeInfiniteRepeatableSpecModel
import com.viewcompose.ui.modifier.ContentSizeKeyframeModel
import com.viewcompose.ui.modifier.ContentSizeKeyframesSpecModel
import com.viewcompose.ui.modifier.ContentSizeRepeatModeModel
import com.viewcompose.ui.modifier.ContentSizeRepeatableSpecModel
import com.viewcompose.ui.modifier.ContentSizeSnapSpecModel
import com.viewcompose.ui.modifier.ContentSizeSpringSpecModel
import com.viewcompose.ui.modifier.ContentSizeTweenSpecModel
import com.viewcompose.ui.modifier.Modifier
import org.junit.Assert.assertEquals
import org.junit.Test

class AnimateContentSizeModifierTest {
    @Test
    fun `tween spec converts to tween model`() {
        val modifier = Modifier.animateContentSize(
            animationSpec = tween(
                durationMillis = 260,
                delayMillis = 40,
            ),
        )
        val element = modifier.elements.last() as AnimateContentSizeModifierElement
        assertEquals(
            ContentSizeTweenSpecModel(
                durationMillis = 260,
                delayMillis = 40,
                easing = ContentSizeEasingModel.FastOutSlowIn,
            ),
            element.animationSpec,
        )
    }

    @Test
    fun `spring keyframes and snap map to expected models`() {
        val springModifier = Modifier.animateContentSize(
            animationSpec = spring(
                durationMillis = 420,
                dampingRatio = 0.76f,
                stiffness = 300f,
            ),
        )
        val keyframesModifier = Modifier.animateContentSize(
            animationSpec = keyframes(
                durationMillis = 360,
                keyframe(timeMillis = 120, valueFraction = 0.2f),
                keyframe(timeMillis = 240, valueFraction = 0.9f),
            ),
        )
        val snapModifier = Modifier.animateContentSize(
            animationSpec = snap(),
        )
        assertEquals(
            ContentSizeSpringSpecModel(
                durationMillis = 420,
                dampingRatio = 0.76f,
                stiffness = 300f,
            ),
            (springModifier.elements.last() as AnimateContentSizeModifierElement).animationSpec,
        )
        assertEquals(
            ContentSizeKeyframesSpecModel(
                durationMillis = 360,
                keyframes = listOf(
                    ContentSizeKeyframeModel(timeMillis = 120, valueFraction = 0.2f),
                    ContentSizeKeyframeModel(timeMillis = 240, valueFraction = 0.9f),
                ),
            ),
            (keyframesModifier.elements.last() as AnimateContentSizeModifierElement).animationSpec,
        )
        assertEquals(
            ContentSizeSnapSpecModel,
            (snapModifier.elements.last() as AnimateContentSizeModifierElement).animationSpec,
        )
    }

    @Test
    fun `repeatable and infinite specs keep repeat metadata`() {
        val repeatableModifier = Modifier.animateContentSize(
            animationSpec = repeatable(
                iterations = 3,
                animation = tween(durationMillis = 280),
                repeatMode = RepeatMode.Reverse,
            ),
        )
        val infiniteModifier = Modifier.animateContentSize(
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 200),
                repeatMode = RepeatMode.Restart,
            ),
        )
        assertEquals(
            ContentSizeRepeatableSpecModel(
                iterations = 3,
                animation = ContentSizeTweenSpecModel(
                    durationMillis = 280,
                    delayMillis = 0,
                    easing = ContentSizeEasingModel.FastOutSlowIn,
                ),
                repeatMode = ContentSizeRepeatModeModel.Reverse,
            ),
            (repeatableModifier.elements.last() as AnimateContentSizeModifierElement).animationSpec,
        )
        assertEquals(
            ContentSizeInfiniteRepeatableSpecModel(
                animation = ContentSizeTweenSpecModel(
                    durationMillis = 200,
                    delayMillis = 0,
                    easing = ContentSizeEasingModel.FastOutSlowIn,
                ),
                repeatMode = ContentSizeRepeatModeModel.Restart,
            ),
            (infiniteModifier.elements.last() as AnimateContentSizeModifierElement).animationSpec,
        )
    }
}
