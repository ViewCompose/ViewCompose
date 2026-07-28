package com.viewcompose.animation.core

/*
 * 测试职责：覆盖 animation core 中的 Animation Engine 行为，防止关键契约在后续重构中回退。
 * Test responsibility: covers Animation Engine behavior in animation core and guards the contract against regressions.
 */

import com.viewcompose.runtime.frame.MonotonicFrameClock
import kotlin.math.abs
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AnimationEngineTest {
    @Test
    fun `runAnimation reaches target for tween`() = runBlocking {
        val clock = FakeClock()
        var last = 0f
        val result = runAnimation(
            frameClock = clock,
            startValue = 0f,
            endValue = 1f,
            animationSpec = tween(durationMillis = 64),
            converter = AnimationConverters.Float,
        ) { value ->
            last = value
        }
        assertEquals(AnimationRunResult.Completed, result)
        assertTrue(abs(last - 1f) < 0.0001f)
    }

    @Test
    fun `repeatable reverse ends at start value on even iterations`() = runBlocking {
        val clock = FakeClock()
        var last = 0f
        val result = runAnimation(
            frameClock = clock,
            startValue = 0f,
            endValue = 10f,
            animationSpec = repeatable(
                iterations = 2,
                animation = tween(durationMillis = 32),
                repeatMode = RepeatMode.Reverse,
            ),
            converter = AnimationConverters.Float,
        ) { value ->
            last = value
        }
        assertEquals(AnimationRunResult.Completed, result)
        assertTrue(abs(last - 0f) < 0.0001f)
    }

    @Test
    fun `tween delay keeps start value before interpolation`() = runBlocking {
        val clock = FakeClock()
        val samples = mutableListOf<Float>()
        val result = runAnimation(
            frameClock = clock,
            startValue = 0f,
            endValue = 1f,
            animationSpec = tween(
                durationMillis = 64,
                delayMillis = 48,
            ),
            converter = AnimationConverters.Float,
        ) { value ->
            samples += value
        }
        assertEquals(AnimationRunResult.Completed, result)
        assertTrue("Expected delay samples before interpolation", samples.size >= 4)
        assertTrue(abs(samples[0] - 0f) < 0.0001f)
        assertTrue(abs(samples[1] - 0f) < 0.0001f)
        assertTrue(abs(samples[2] - 0f) < 0.0001f)
    }

    @Test
    fun `cancelled animation does not force terminal value`() = runBlocking {
        val clock = SuspendingFakeClock()
        val samples = mutableListOf<Float>()
        lateinit var animationJob: Job
        animationJob = launch {
            runAnimation(
                frameClock = clock,
                startValue = 0f,
                endValue = 1f,
                animationSpec = tween(durationMillis = 320),
                converter = AnimationConverters.Float,
            ) { value ->
                samples += value
                if (samples.size == 1) {
                    animationJob.cancel()
                }
            }
        }
        animationJob.join()
        assertTrue(samples.isNotEmpty())
        assertNotEquals(1f, samples.last(), 0.0001f)
    }

    @Test
    fun `repeatable duration and sampling honor iterations and reverse terminal`() {
        val spec = repeatable(
            iterations = 2,
            animation = tween(durationMillis = 100),
            repeatMode = RepeatMode.Reverse,
        )
        val durationNanos = animationDurationNanos(spec)
        assertEquals(200_000_000L, durationNanos)
        val quarter = sampleAnimationValue(
            startValue = 0f,
            endValue = 1f,
            animationSpec = spec,
            converter = AnimationConverters.Float,
            playTimeNanos = 50_000_000L,
        )
        val midReverse = sampleAnimationValue(
            startValue = 0f,
            endValue = 1f,
            animationSpec = spec,
            converter = AnimationConverters.Float,
            playTimeNanos = 150_000_000L,
        )
        val terminal = sampleAnimationValue(
            startValue = 0f,
            endValue = 1f,
            animationSpec = spec,
            converter = AnimationConverters.Float,
            playTimeNanos = durationNanos,
        )
        assertTrue(abs(quarter - 0.5f) < 0.001f)
        assertTrue(abs(midReverse - 0.5f) < 0.001f)
        assertTrue(abs(terminal - 0f) < 0.001f)
    }

    @Test
    fun `infinite repeatable is never finished`() {
        val spec = infiniteRepeatable(
            animation = tween(durationMillis = 100),
            repeatMode = RepeatMode.Restart,
        )
        assertFalse(
            isAnimationFinished(
                spec = spec,
                playTimeNanos = Long.MAX_VALUE,
            ),
        )
        assertEquals(Long.MAX_VALUE, animationDurationNanos(spec))
    }

    @Test
    fun `infinite snap animation still advances through frame clock`() = runBlocking {
        val clock = CountingSuspendingClock()
        var sampleCount = 0
        try {
            runAnimation(
                frameClock = clock,
                startValue = 0f,
                endValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = snap(),
                    repeatMode = RepeatMode.Restart,
                ),
                converter = AnimationConverters.Float,
            ) {
                sampleCount += 1
                if (sampleCount >= 3) {
                    throw StopInfiniteAnimationTestException
                }
            }
        } catch (_: StopInfiniteAnimationTestException) {
        }
        assertTrue(clock.frameCalls >= 3)
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

    private class SuspendingFakeClock(
        private val frameStepNanos: Long = 16_000_000L,
    ) : MonotonicFrameClock {
        private var nowNanos: Long = 0L

        override suspend fun <R> withFrameNanos(
            onFrame: (frameTimeNanos: Long) -> R,
        ): R {
            delay(1)
            nowNanos += frameStepNanos
            return onFrame(nowNanos)
        }
    }

    private class CountingSuspendingClock(
        private val frameStepNanos: Long = 16_000_000L,
    ) : MonotonicFrameClock {
        var frameCalls: Int = 0
            private set
        private var nowNanos: Long = 0L

        override suspend fun <R> withFrameNanos(
            onFrame: (frameTimeNanos: Long) -> R,
        ): R {
            delay(1)
            frameCalls += 1
            nowNanos += frameStepNanos
            return onFrame(nowNanos)
        }
    }

    private object StopInfiniteAnimationTestException : RuntimeException()
}
