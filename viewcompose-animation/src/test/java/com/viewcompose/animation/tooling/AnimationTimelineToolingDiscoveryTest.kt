package com.viewcompose.animation.tooling

import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class AnimationTimelineToolingRegistryTest {
    @Test
    fun `selects one installed implementation without discovery`() {
        val slot = AnimationTimelineToolingSlot()
        val provider = EmptyTimelineTooling()

        slot.install(provider)

        assertSame(provider, slot.resolve())
        assertSame(provider, slot.resolve())
    }

    @Test
    fun `absence and ambiguity freeze to no tooling`() {
        val absent = AnimationTimelineToolingSlot()
        assertNull(absent.resolve())
        absent.install(EmptyTimelineTooling())
        assertNull(absent.resolve())

        val ambiguous = AnimationTimelineToolingSlot()
        val first = EmptyTimelineTooling()
        val second = EmptyTimelineTooling()
        ambiguous.install(first)
        ambiguous.install(first)
        ambiguous.install(second)

        assertNull(ambiguous.resolve())
    }

    private class EmptyTimelineTooling : AnimationTimelineTooling {
        override fun register(source: AnimationTimelineSource): AnimationTimelineRegistration? {
            return null
        }
    }
}
