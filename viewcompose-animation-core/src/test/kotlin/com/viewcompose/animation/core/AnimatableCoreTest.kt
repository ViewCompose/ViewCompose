package com.viewcompose.animation.core

import com.viewcompose.runtime.frame.MonotonicFrameClock
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AnimatableCoreTest {
    @Test
    fun `new physical target cancels old caller and continues retained velocity`() = runBlocking {
        val clock = ManualClock()
        val value = AnimatableCore(
            initialValue = 0f,
            converter = AnimationConverters.Float,
        )
        var firstCancelled = false
        val first = launch {
            try {
                value.animateTo(
                    targetValue = 100f,
                    animationSpec = spring(dampingRatio = 0.55f, stiffness = 160f),
                    frameClock = clock,
                )
            } catch (_: CancellationException) {
                firstCancelled = true
            }
        }
        clock.advanceToMillis(0)
        clock.advanceToMillis(32)
        val retainedVelocity = value.velocity.valuePerSecond
        assertTrue(retainedVelocity > 0f)

        val second = launch {
            value.animateTo(
                targetValue = 30f,
                animationSpec = spring(dampingRatio = 0.7f, stiffness = 220f),
                frameClock = clock,
            )
        }
        yield()
        first.join()
        clock.advanceToMillis(48)
        clock.advanceToMillis(64)

        assertTrue(firstCancelled)
        assertTrue(value.velocity.valuePerSecond > 0f)

        while (second.isActive) {
            clock.advanceByMillis(16)
        }
        second.join()
        assertEquals(30f, value.value, 0f)
        assertEquals(0f, value.velocity.valuePerSecond, 0f)
    }

    @Test
    fun `invalid replacement leaves the active mutation authoritative`() = runBlocking {
        val clock = ManualClock()
        val value = AnimatableCore(
            initialValue = 0f,
            converter = AnimationConverters.Float,
        )
        val first = launch {
            value.animateTo(
                targetValue = 100f,
                animationSpec = spring(dampingRatio = 0.7f, stiffness = 180f),
                frameClock = clock,
            )
        }
        clock.advanceToMillis(0)
        clock.advanceToMillis(32)

        var replacementFailed = false
        try {
            value.animateTo(
                targetValue = Float.MAX_VALUE,
                animationSpec = spring(dampingRatio = 0.2f, stiffness = 100f),
                frameClock = clock,
            )
        } catch (_: IllegalArgumentException) {
            replacementFailed = true
        }

        assertTrue(replacementFailed)
        assertTrue(first.isActive)
        assertTrue(value.isRunning)
        assertEquals(100f, value.targetValue, 0f)
        first.cancel()
        first.join()
    }

    @Test
    fun `invalid snap leaves the active mutation authoritative`() = runBlocking {
        val clock = ManualClock()
        val value = AnimatableCore(
            initialValue = 0f,
            converter = AnimationConverters.Float,
        )
        val first = launch {
            value.animateTo(
                targetValue = 100f,
                animationSpec = spring(dampingRatio = 0.7f, stiffness = 180f),
                frameClock = clock,
            )
        }
        clock.advanceToMillis(0)
        clock.advanceToMillis(32)

        var replacementFailed = false
        try {
            value.snapTo(Float.NaN)
        } catch (_: IllegalArgumentException) {
            replacementFailed = true
        }

        assertTrue(replacementFailed)
        assertTrue(first.isActive)
        assertTrue(value.isRunning)
        assertEquals(100f, value.targetValue, 0f)
        first.cancel()
        first.join()
    }

    @Test(expected = IllegalArgumentException::class)
    fun `invalid initial state fails during owner construction`() {
        AnimatableCore(
            initialValue = Float.NaN,
            converter = AnimationConverters.Float,
        )
    }

    @Test
    fun `updated running bounds clamp the next sample and return bound reason`() = runBlocking {
        val clock = ManualClock()
        val value = AnimatableCore(
            initialValue = 0f,
            converter = AnimationConverters.Float,
        )
        val animation = launch {
            val result = value.animateTo(
                targetValue = 100f,
                animationSpec = spring(dampingRatio = 0.4f, stiffness = 180f),
                initialVelocity = AnimationVelocity(500f),
                frameClock = clock,
            )
            assertEquals(AnimationEndReason.BoundReached, result.endReason)
        }
        clock.advanceToMillis(0)
        clock.advanceToMillis(16)
        value.updateBounds(lowerBound = -20f, upperBound = 5f)
        clock.advanceToMillis(32)
        animation.join()

        assertEquals(5f, value.value, 0f)
        assertEquals(0f, value.velocity.valuePerSecond, 0f)
        assertFalse(value.isRunning)
    }

    @Test
    fun `idle bounds clamp value atomically and snap resets velocity`() = runBlocking {
        val value = AnimatableCore(
            initialValue = 20f,
            converter = AnimationConverters.Float,
        )

        value.updateBounds(lowerBound = 0f, upperBound = 10f)
        assertEquals(10f, value.value, 0f)
        assertEquals(10f, value.targetValue, 0f)
        assertEquals(0f, value.velocity.valuePerSecond, 0f)

        value.snapTo(40f)
        assertEquals(10f, value.value, 0f)
        assertEquals(0f, value.velocity.valuePerSecond, 0f)
    }

    @Test
    fun `decay hands velocity into bounds without exposing an out of range value`() = runBlocking {
        val value = AnimatableCore(
            initialValue = 0f,
            converter = AnimationConverters.Float,
        )
        value.updateBounds(lowerBound = -100f, upperBound = 25f)

        val result = value.animateDecay(
            initialVelocity = AnimationVelocity(600f),
            animationSpec = exponentialDecay(),
            frameClock = StepClock(),
        )

        assertEquals(AnimationEndReason.BoundReached, result.endReason)
        assertEquals(25f, value.value, 0f)
        assertEquals(0f, value.velocity.valuePerSecond, 0f)
    }

    @Test
    fun `target on bound finishes when its physical path never crosses that bound`() = runBlocking {
        val value = AnimatableCore(
            initialValue = 0f,
            converter = AnimationConverters.Float,
        )
        value.updateBounds(lowerBound = 0f, upperBound = 1f)

        val result = value.animateTo(
            targetValue = 1f,
            animationSpec = spring(dampingRatio = 1f, stiffness = 180f),
            frameClock = StepClock(),
        )

        assertEquals(AnimationEndReason.Finished, result.endReason)
        assertEquals(1f, result.endState.value, 0f)
    }

    private class ManualClock : MonotonicFrameClock {
        private val frames = Channel<Long>(capacity = Channel.UNLIMITED)
        private var currentMillis: Long = 0L

        override suspend fun <R> withFrameNanos(
            onFrame: (frameTimeNanos: Long) -> R,
        ): R = onFrame(frames.receive())

        suspend fun advanceToMillis(timeMillis: Long) {
            currentMillis = timeMillis
            frames.send(timeMillis * 1_000_000L)
            yield()
        }

        suspend fun advanceByMillis(deltaMillis: Long) {
            advanceToMillis(currentMillis + deltaMillis)
        }
    }

    private class StepClock : MonotonicFrameClock {
        private var nowNanos = 0L

        override suspend fun <R> withFrameNanos(
            onFrame: (frameTimeNanos: Long) -> R,
        ): R {
            nowNanos += 16_000_000L
            return onFrame(nowNanos)
        }
    }
}
