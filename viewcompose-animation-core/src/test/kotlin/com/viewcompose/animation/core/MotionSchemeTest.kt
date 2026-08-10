package com.viewcompose.animation.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class MotionSchemeTest {
    private val scheme = MotionScheme(
        fastEffects = tween(durationMillis = 100, delayMillis = 20),
        defaultEffects = tween(durationMillis = 200),
        fastSpatial = tween(durationMillis = 160),
        defaultSpatial = tween(durationMillis = 320),
        expressiveSpatial = spring(durationMillis = 600),
        reducedMotion = ReducedMotionPolicy(
            nonEssentialBehavior = ReducedMotionBehavior.Snap,
            nonEssentialDurationScale = 0.25f,
            essentialDurationScale = 0.5f,
        ),
    )

    @Test
    fun `normal environment returns role specification by identity`() {
        assertSame(
            scheme.expressiveSpatial,
            scheme.resolve(MotionRole.ExpressiveSpatial, reducedMotionEnabled = false),
        )
    }

    @Test
    fun `reduced non essential motion snaps without changing target semantics`() {
        assertSame(
            SnapSpec,
            scheme.resolve(
                role = MotionRole.DefaultSpatial,
                reducedMotionEnabled = true,
                essential = false,
            ),
        )
    }

    @Test
    fun `reduced essential motion scales bounded spring duration`() {
        assertEquals(
            SpringSpec(durationMillis = 300),
            scheme.resolve(
                role = MotionRole.ExpressiveSpatial,
                reducedMotionEnabled = true,
                essential = true,
            ),
        )
    }

    @Test
    fun `shortened keyframes scale duration and checkpoints deterministically`() {
        val shortened = scheme.copy(
            fastEffects = keyframes(
                durationMillis = 400,
                keyframe(100, 0.2f),
                keyframe(300, 0.8f),
            ),
            reducedMotion = scheme.reducedMotion.copy(
                nonEssentialBehavior = ReducedMotionBehavior.Shorten,
            ),
        ).resolve(
            role = MotionRole.FastEffects,
            reducedMotionEnabled = true,
        ) as KeyframesSpec

        assertEquals(100, shortened.durationMillis)
        assertEquals(listOf(25, 75), shortened.keyframes.map { it.timeMillis })
    }
}
