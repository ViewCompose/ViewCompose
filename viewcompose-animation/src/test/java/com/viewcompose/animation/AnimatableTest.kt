package com.viewcompose.animation

import com.viewcompose.animation.core.AnimationConverters
import com.viewcompose.animation.core.tween
import com.viewcompose.runtime.frame.MonotonicFrameClock
import kotlin.math.abs
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AnimatableTest {
    @Test
    fun `animateTo requires bound frame clock`() = runBlocking {
        val animatable = Animatable(
            initialValue = 0f,
            converter = AnimationConverters.Float,
        )
        var thrown = false
        try {
            animatable.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 32),
            )
        } catch (_: IllegalArgumentException) {
            thrown = true
        }
        assertTrue(thrown)
    }

    @Test
    fun `animateTo reaches target with constructor provided frame clock`() = runBlocking {
        val animatable = Animatable(
            initialValue = 0f,
            converter = AnimationConverters.Float,
            defaultFrameClock = FakeClock(),
        )
        animatable.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 64),
        )
        assertTrue(abs(animatable.value - 1f) < 0.0001f)
    }

    @Test
    fun `new target cancels old animation and continues from current value`() = runBlocking {
        val clock = ManualClock()
        val animatable = Animatable(
            initialValue = 0f,
            converter = AnimationConverters.Float,
            defaultFrameClock = clock,
        )
        var firstCancelled = false
        val first = launch {
            try {
                animatable.animateTo(
                    targetValue = 100f,
                    animationSpec = tween(durationMillis = 100),
                )
            } catch (_: CancellationException) {
                firstCancelled = true
            }
        }
        clock.advanceToMillis(0)
        clock.advanceToMillis(50)
        yield()
        val interruptedValue = animatable.value

        val second = launch {
            animatable.animateTo(
                targetValue = 20f,
                animationSpec = tween(durationMillis = 100),
            )
        }
        yield()
        first.join()
        assertTrue(firstCancelled)
        assertEquals(20f, animatable.targetValue, 0f)
        assertTrue(animatable.isRunning)

        clock.advanceToMillis(60)
        clock.advanceToMillis(110)
        clock.advanceToMillis(160)
        second.join()

        assertTrue(interruptedValue in 49f..51f)
        assertEquals(20f, animatable.value, 0.0001f)
        assertEquals(20f, animatable.targetValue, 0.0001f)
        assertFalse(animatable.isRunning)
    }

    @Test
    fun `snapTo interrupts animation and stale frames cannot overwrite value`() = runBlocking {
        val clock = ManualClock()
        val animatable = Animatable(
            initialValue = 0f,
            converter = AnimationConverters.Float,
            defaultFrameClock = clock,
        )
        val animation = launch {
            try {
                animatable.animateTo(
                    targetValue = 100f,
                    animationSpec = tween(durationMillis = 100),
                )
            } catch (_: CancellationException) {
                // Expected: snapTo is the newer mutation.
            }
        }
        clock.advanceToMillis(0)
        clock.advanceToMillis(50)
        yield()

        animatable.snapTo(7f)
        clock.advanceToMillis(100)
        yield()
        animation.join()

        assertEquals(7f, animatable.value, 0f)
        assertEquals(7f, animatable.targetValue, 0f)
        assertFalse(animatable.isRunning)
    }

    @Test
    fun `stop preserves current value and clears running state`() = runBlocking {
        val clock = ManualClock()
        val animatable = Animatable(
            initialValue = 0f,
            converter = AnimationConverters.Float,
            defaultFrameClock = clock,
        )
        val animation = launch {
            try {
                animatable.animateTo(
                    targetValue = 100f,
                    animationSpec = tween(durationMillis = 100),
                )
            } catch (_: CancellationException) {
                // Expected: stop is the newer mutation.
            }
        }
        clock.advanceToMillis(0)
        clock.advanceToMillis(40)
        yield()
        val stoppedValue = animatable.value

        animatable.stop()
        clock.advanceToMillis(100)
        animation.join()

        assertEquals(stoppedValue, animatable.value, 0f)
        assertEquals(stoppedValue, animatable.targetValue, 0f)
        assertFalse(animatable.isRunning)
    }

    private class FakeClock(
        private val frameStepNanos: Long = 16_000_000L,
    ) : MonotonicFrameClock {
        private var nowNanos: Long = 0L

        override suspend fun <R> withFrameNanos(
            onFrame: (frameTimeNanos: Long) -> R,
        ): R {
            nowNanos += frameStepNanos
            return onFrame(nowNanos)
        }
    }

    private class ManualClock : MonotonicFrameClock {
        private val frames = Channel<Long>(capacity = Channel.UNLIMITED)

        override suspend fun <R> withFrameNanos(
            onFrame: (frameTimeNanos: Long) -> R,
        ): R = onFrame(frames.receive())

        suspend fun advanceToMillis(timeMillis: Long) {
            frames.send(timeMillis * 1_000_000L)
            yield()
        }
    }
}
