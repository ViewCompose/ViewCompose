package com.viewcompose.host.android.runtime

import java.util.LinkedHashSet
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AndroidFrameAlignedRenderSessionRuntimeTest {
    @Test
    fun `inactive runtime coalesces invalidations until rendering resumes`() {
        val clock = FakeFrameClock()
        var renders = 0
        val runtime = AndroidFrameAlignedRenderSessionRuntime(
            onRenderNow = { renders += 1 },
            onDisposeNow = {},
            frameClock = clock,
        )

        runtime.setRenderingActive(false)
        runtime.requestRender()
        runtime.requestRender()
        runtime.requestRender()

        assertEquals(0, clock.postCount)
        runtime.setRenderingActive(true)
        assertEquals(1, clock.postCount)

        clock.fireFrame()
        assertEquals(1, renders)
    }

    @Test
    fun `deactivation preserves an already scheduled render as pending`() {
        val clock = FakeFrameClock()
        var renders = 0
        val runtime = AndroidFrameAlignedRenderSessionRuntime(
            onRenderNow = { renders += 1 },
            onDisposeNow = {},
            frameClock = clock,
        )

        runtime.requestRender()
        runtime.setRenderingActive(false)

        assertEquals(1, clock.removeCount)
        clock.fireFrame()
        assertEquals(0, renders)

        runtime.setRenderingActive(true)
        assertEquals(2, clock.postCount)
        clock.fireFrame()
        assertEquals(1, renders)
    }

    @Test
    fun `explicit render runs while inactive and clears deferred invalidation`() {
        val clock = FakeFrameClock()
        var renders = 0
        val runtime = AndroidFrameAlignedRenderSessionRuntime(
            onRenderNow = { renders += 1 },
            onDisposeNow = {},
            frameClock = clock,
        )

        runtime.setRenderingActive(false)
        runtime.requestRender()
        runtime.render()
        runtime.setRenderingActive(true)

        assertEquals(1, renders)
        assertEquals(0, clock.postCount)
    }

    private class FakeFrameClock : RenderFrameClock {
        var postCount: Int = 0
        var removeCount: Int = 0
        private val pending = LinkedHashSet<RenderFrameCallback>()

        override fun postFrameCallback(callback: RenderFrameCallback) {
            postCount += 1
            pending += callback
        }

        override fun removeFrameCallback(callback: RenderFrameCallback) {
            removeCount += 1
            pending.remove(callback)
        }

        fun fireFrame(frameTimeNanos: Long = 0L) {
            val callbacks = pending.toList()
            pending.clear()
            callbacks.forEach { callback ->
                callback.doFrame(frameTimeNanos)
            }
        }
    }
}
