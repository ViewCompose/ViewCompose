package com.viewcompose.host.android.runtime

/*
 * 测试职责：覆盖 Android host 中的 Frame Aligned Render Dispatcher 行为，防止关键契约在后续重构中回退。
 * Test responsibility: covers Frame Aligned Render Dispatcher behavior in Android host and guards the contract against regressions.
 */

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.ArrayDeque
import java.util.LinkedHashSet

class FrameAlignedRenderDispatcherTest {
    @Test
    fun `coalesces multiple requests into single frame callback`() {
        val clock = FakeFrameClock()
        var renders = 0
        val dispatcher = FrameAlignedRenderDispatcher(
            frameClock = clock,
            onFrameRender = { renders += 1 },
            isMainThread = { true },
            postToMain = { runnable -> runnable.run() },
        )

        dispatcher.requestFrame()
        dispatcher.requestFrame()
        dispatcher.requestFrame()

        assertEquals(1, clock.postCount)
        assertEquals(0, renders)

        clock.fireFrame()
        assertEquals(1, renders)
    }

    @Test
    fun `cancel pending prevents frame render`() {
        val clock = FakeFrameClock()
        var renders = 0
        val dispatcher = FrameAlignedRenderDispatcher(
            frameClock = clock,
            onFrameRender = { renders += 1 },
            isMainThread = { true },
            postToMain = { runnable -> runnable.run() },
        )

        dispatcher.requestFrame()
        dispatcher.cancelPending()

        assertEquals(1, clock.postCount)
        assertEquals(1, clock.removeCount)

        clock.fireFrame()
        assertEquals(0, renders)
    }

    @Test
    fun `reentrant invalidation inside render schedules next frame`() {
        val clock = FakeFrameClock()
        var renders = 0
        lateinit var dispatcher: FrameAlignedRenderDispatcher
        dispatcher = FrameAlignedRenderDispatcher(
            frameClock = clock,
            onFrameRender = {
                renders += 1
                if (renders == 1) {
                    dispatcher.requestFrame()
                }
            },
            isMainThread = { true },
            postToMain = { runnable -> runnable.run() },
        )

        dispatcher.requestFrame()
        assertEquals(1, clock.postCount)

        clock.fireFrame()
        assertEquals(1, renders)
        assertEquals(2, clock.postCount)

        clock.fireFrame()
        assertEquals(2, renders)
    }

    @Test
    fun `cross-thread requests still enqueue a single main-thread callback`() {
        val clock = FakeFrameClock()
        val mainQueue = ArrayDeque<Runnable>()
        var renders = 0
        val dispatcher = FrameAlignedRenderDispatcher(
            frameClock = clock,
            onFrameRender = { renders += 1 },
            isMainThread = { false },
            postToMain = { runnable -> mainQueue.addLast(runnable) },
        )

        dispatcher.requestFrame()
        dispatcher.requestFrame()
        dispatcher.requestFrame()

        assertEquals(1, mainQueue.size)
        assertEquals(0, clock.postCount)

        mainQueue.removeFirst().run()
        assertEquals(1, clock.postCount)

        clock.fireFrame()
        assertEquals(1, renders)
    }

    @Test
    fun `dispose cancels pending callback once`() {
        val clock = FakeFrameClock()
        val dispatcher = FrameAlignedRenderDispatcher(
            frameClock = clock,
            onFrameRender = {},
            isMainThread = { true },
            postToMain = { runnable -> runnable.run() },
        )

        dispatcher.requestFrame()
        dispatcher.dispose()
        dispatcher.dispose()

        assertEquals(1, clock.removeCount)
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

