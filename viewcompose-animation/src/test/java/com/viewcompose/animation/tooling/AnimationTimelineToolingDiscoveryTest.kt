package com.viewcompose.animation.tooling

import java.util.ServiceLoader
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AnimationTimelineToolingDiscoveryTest {
    @Test
    fun `animation artifact alone contains no concrete timeline provider`() {
        val providers = ServiceLoader.load(
            AnimationTimelineTooling::class.java,
            AnimationTimelineTooling::class.java.classLoader,
        ).toList()

        assertTrue(providers.isEmpty())
        assertNull(selectSingleAnimationTimelineTooling(providers))
    }

    @Test
    fun `ambiguous providers disable tooling instead of selecting implicitly`() {
        val first = EmptyTimelineTooling()
        val second = EmptyTimelineTooling()

        assertNull(selectSingleAnimationTimelineTooling(listOf(first, second)))
    }

    private class EmptyTimelineTooling : AnimationTimelineTooling {
        override fun register(source: AnimationTimelineSource): AnimationTimelineRegistration? {
            return null
        }
    }
}
