@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")

package com.viewcompose.animation

import com.viewcompose.animation.core.EasingDefaults
import com.viewcompose.animation.core.tween
import com.viewcompose.runtime.composition.ComposerLite
import com.viewcompose.runtime.frame.MonotonicFrameClock
import com.viewcompose.ui.foundation.ComposerContext
import com.viewcompose.ui.foundation.ProvideMonotonicFrameClock
import com.viewcompose.ui.foundation.UiTreeBuilder
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.EmptyCoroutineContext
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class SeekableTransitionStateTest {
    @Test
    fun `normalized seek uses longest duration and clamps shorter channels`() = runBlocking {
        val harness = SeekableHarness()
        harness.compose()

        val seek = async(start = CoroutineStart.UNDISPATCHED) {
            harness.state.seekTo(fraction = 0.5f, targetState = true)
        }
        harness.compose()
        harness.clock.advanceTo(0L)
        seek.await()
        val sample = harness.settledSample()

        assertEquals(1f, sample.shortValue, 0f)
        assertEquals(0.5f, sample.longValue, 0f)
        assertEquals(0.5f, harness.state.fraction, 0f)
        assertFalse(harness.state.isAnimating)
        assertTrue(harness.state.isSeeking)
        harness.dispose()
    }

    @Test
    fun `removing longest channel recomputes normalized seek sample`() = runBlocking {
        val harness = SeekableHarness()
        harness.compose()
        harness.seek(fraction = 0.5f, target = true, frameTimeNanos = 0L)
        assertEquals(1f, harness.settledSample().shortValue, 0f)

        harness.compose(includeLongChannel = false)
        harness.compose(includeLongChannel = false)
        val afterRemoval = harness.compose(includeLongChannel = false)

        assertEquals(100_000_000L, harness.transition.segmentDurationNanos)
        assertEquals(50_000_000L, harness.transition.playTimeNanos)
        assertEquals(0.5f, afterRemoval.shortValue, 0.0001f)
        assertEquals(0.5f, harness.state.fraction, 0f)
        assertTrue(harness.state.isSeeking)
        harness.dispose()
    }

    @Test
    fun `seek retarget freezes sampled values and resets channel progress`() = runBlocking {
        val harness = SeekableHarness()
        harness.compose()
        harness.seek(fraction = 0.5f, target = true, frameTimeNanos = 0L)
        val beforeRetarget = harness.settledSample()

        harness.seek(fraction = 0f, target = false, frameTimeNanos = 10L)
        val retargetStart = harness.settledSample()

        assertEquals(beforeRetarget.shortValue, retargetStart.shortValue, 0f)
        assertEquals(beforeRetarget.longValue, retargetStart.longValue, 0f)
        assertEquals(0f, harness.state.fraction, 0f)

        harness.state.seekTo(fraction = 0.5f, targetState = false)
        val halfwayBack = harness.settledSample()
        assertEquals(0f, halfwayBack.shortValue, 0f)
        assertEquals(0.25f, halfwayBack.longValue, 0.0001f)
        harness.dispose()
    }

    @Test
    fun `seek to animate hands off to one autonomous writer from sampled values`() = runBlocking {
        val harness = SeekableHarness()
        harness.compose()
        harness.seek(fraction = 0.5f, target = true, frameTimeNanos = 0L)

        val animation = async(start = CoroutineStart.UNDISPATCHED) {
            harness.state.animateTo(true)
        }
        harness.compose()
        harness.clock.advanceTo(10L)
        harness.clock.advanceTo(20L)
        assertEquals(1, harness.clock.waiterCount)
        harness.clock.advanceTo(300_000_020L)
        animation.await()
        val settled = harness.settledSample()

        assertEquals(1f, settled.shortValue, 0f)
        assertEquals(1f, settled.longValue, 0f)
        assertEquals(true, harness.state.currentState)
        assertEquals(true, harness.state.targetState)
        assertEquals(0f, harness.state.fraction, 0f)
        assertFalse(harness.state.isAnimating)
        assertFalse(harness.state.isSeeking)
        assertEquals(0, harness.clock.waiterCount)
        harness.dispose()
    }

    @Test
    fun `animate to seek cancels and joins the autonomous writer before publishing`() = runBlocking {
        val harness = SeekableHarness()
        harness.compose()

        val animation = async(start = CoroutineStart.UNDISPATCHED) {
            harness.state.animateTo(true)
        }
        harness.compose()
        harness.clock.advanceTo(0L)
        harness.clock.advanceTo(10L)
        harness.clock.advanceTo(100_000_010L)
        assertEquals(1, harness.clock.waiterCount)

        val seek = async(start = CoroutineStart.UNDISPATCHED) {
            harness.state.seekTo(fraction = 0.25f, targetState = true)
        }
        seek.await()
        val sample = harness.settledSample()

        assertTrue(animation.isCancelled)
        assertEquals(0, harness.clock.waiterCount)
        assertEquals(0.75f, sample.shortValue, 0.0001f)
        assertEquals(0.25f, sample.longValue, 0.0001f)
        assertEquals(0.25f, harness.state.fraction, 0f)
        assertFalse(harness.state.isAnimating)
        assertTrue(harness.state.isSeeking)
        harness.dispose()
    }

    @Test
    fun `rapid animate target replacement cancels old writer and finishes newest target`() = runBlocking {
        val harness = SeekableHarness()
        harness.compose()

        val firstAnimation = async(start = CoroutineStart.UNDISPATCHED) {
            harness.state.animateTo(true)
        }
        harness.compose()
        harness.clock.advanceTo(0L)
        harness.clock.advanceTo(100_000_000L)
        assertEquals(1, harness.clock.waiterCount)

        val replacement = async(start = CoroutineStart.UNDISPATCHED) {
            harness.state.animateTo(false)
        }
        withTimeout(1_000L) {
            while (harness.state.targetState) yield()
        }
        harness.compose()
        assertTrue(firstAnimation.isCancelled)
        repeat(4) { frameIndex ->
            if (!replacement.isCompleted) {
                assertEquals(1, harness.clock.waiterCount)
                harness.clock.advanceTo(110_000_000L + frameIndex * 300_000_000L)
            }
        }
        withTimeout(1_000L) {
            replacement.await()
        }

        val settled = harness.settledSample()
        assertEquals(0f, settled.shortValue, 0f)
        assertEquals(0f, settled.longValue, 0f)
        assertEquals(false, harness.state.currentState)
        assertEquals(false, harness.state.targetState)
        assertFalse(harness.state.isAnimating)
        assertFalse(harness.state.isSeeking)
        assertEquals(0, harness.clock.waiterCount)
        harness.dispose()
    }

    @Test
    fun `invalid fraction fails before cancelling the active writer`() = runBlocking {
        val harness = SeekableHarness()
        harness.compose()
        val animation = async(start = CoroutineStart.UNDISPATCHED) {
            harness.state.animateTo(true)
        }
        harness.compose()
        harness.clock.advanceTo(0L)
        harness.clock.advanceTo(10L)

        listOf(Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY, -0.01f, 1.01f)
            .forEach { invalidFraction ->
                assertThrows(IllegalArgumentException::class.java) {
                    runBlocking {
                        harness.state.seekTo(invalidFraction, true)
                    }
                }
                assertTrue(animation.isActive)
                assertTrue(harness.state.isAnimating)
            }

        harness.state.snapTo(false)
        assertTrue(animation.isCancelled)
        assertEquals(false, harness.state.currentState)
        assertEquals(false, harness.state.targetState)
        assertEquals(0f, harness.state.fraction, 0f)
        harness.dispose()
    }

    @Test
    fun `seek without channels completes after bounded configuration wait`() = runBlocking {
        val state = SeekableTransitionState(false)
        val transition = Transition(initialState = false, label = "zero channels")
        val clock = ManualClock()
        val bindingId = state.bind(transition, clock, EmptyCoroutineContext)

        val seek = async(start = CoroutineStart.UNDISPATCHED) {
            state.seekTo(fraction = 0.5f, targetState = true)
        }
        clock.advanceTo(0L)
        clock.advanceTo(1L)
        seek.await()

        assertEquals(0.5f, state.fraction, 0f)
        assertFalse(state.isAnimating)
        assertTrue(state.isSeeking)
        assertEquals(0, clock.waiterCount)
        state.unbind(bindingId)
        clock.close()
    }

    @Test
    fun `snap publishes one idle endpoint without a frame`() = runBlocking {
        val harness = SeekableHarness()
        harness.compose()
        harness.seek(fraction = 0.4f, target = true, frameTimeNanos = 0L)

        harness.state.snapTo(false)

        assertEquals(false, harness.state.currentState)
        assertEquals(false, harness.state.targetState)
        assertEquals(0f, harness.state.fraction, 0f)
        assertFalse(harness.state.isAnimating)
        assertFalse(harness.state.isSeeking)
        assertEquals(0, harness.clock.waiterCount)
        assertEquals(false, harness.transition.segment.initialState)
        assertEquals(false, harness.transition.segment.targetState)
        harness.dispose()
    }

    @Test
    fun `binding is mandatory unique and disposal cancels its writer`() = runBlocking {
        val unbound = SeekableTransitionState(false)
        assertThrows(IllegalStateException::class.java) {
            runBlocking { unbound.snapTo(true) }
        }

        val clock = ManualClock()
        val first = Transition(initialState = false, label = "first")
        val second = Transition(initialState = false, label = "second")
        val bindingId = unbound.bind(first, clock, EmptyCoroutineContext)
        assertThrows(IllegalStateException::class.java) {
            unbound.bind(second, clock, EmptyCoroutineContext)
        }
        unbound.unbind(bindingId)

        val harness = SeekableHarness()
        harness.compose()
        val animation = async(start = CoroutineStart.UNDISPATCHED) {
            harness.state.animateTo(true)
        }
        harness.compose()
        harness.clock.advanceTo(0L)
        harness.clock.advanceTo(10L)
        harness.dispose()
        yield()

        assertTrue(animation.isCancelled)
        assertFalse(harness.state.isAnimating)
        assertTrue(harness.state.isSeeking)
        assertEquals(0, harness.clock.waiterCount)
    }

    private data class Sample(
        val shortValue: Float,
        val longValue: Float,
    )

    private class SeekableHarness {
        val state = SeekableTransitionState(false)
        val clock = ManualClock()
        lateinit var transition: Transition<Boolean>
            private set
        private val composer = ComposerLite()

        fun compose(includeLongChannel: Boolean = true): Sample {
            var shortValue = Float.NaN
            var longValue = Float.NaN
            ComposerContext.withComposer(composer) {
                composer.requestRootRecompose()
                composer.composeRoot {
                    UiTreeBuilder().apply {
                        ProvideMonotonicFrameClock(clock) {
                            transition = rememberTransition(state, label = "seekable test")
                            shortValue = transition.animateFloat(
                                transitionSpec = {
                                    tween(
                                        durationMillis = 100,
                                        easing = EasingDefaults.Linear,
                                    )
                                },
                                targetValueByState = { target -> if (target) 1f else 0f },
                            ).value
                            if (includeLongChannel) {
                                longValue = transition.animateFloat(
                                    transitionSpec = {
                                        tween(
                                            durationMillis = 300,
                                            easing = EasingDefaults.Linear,
                                        )
                                    },
                                    targetValueByState = { target -> if (target) 1f else 0f },
                                ).value
                            }
                        }
                    }
                }
            }
            composer.commitSideEffects()
            return Sample(shortValue = shortValue, longValue = longValue)
        }

        fun settledSample(): Sample {
            compose()
            return compose()
        }

        suspend fun seek(
            fraction: Float,
            target: Boolean,
            frameTimeNanos: Long,
        ) {
            val seek = kotlinx.coroutines.coroutineScope {
                async(start = CoroutineStart.UNDISPATCHED) {
                    state.seekTo(fraction = fraction, targetState = target)
                }.also {
                    compose()
                    clock.advanceTo(frameTimeNanos)
                }
            }
            seek.await()
        }

        fun dispose() {
            composer.dispose()
            clock.close()
        }
    }

    private class ManualClock : MonotonicFrameClock {
        private val frames = Channel<Long>()
        private val waiters = AtomicInteger(0)

        val waiterCount: Int
            get() = waiters.get()

        override suspend fun <R> withFrameNanos(onFrame: (Long) -> R): R {
            waiters.incrementAndGet()
            return try {
                onFrame(frames.receive())
            } finally {
                waiters.decrementAndGet()
            }
        }

        suspend fun advanceTo(frameTimeNanos: Long) {
            withTimeout(1_000L) {
                while (waiterCount == 0) yield()
                frames.send(frameTimeNanos)
                yield()
            }
        }

        fun close() {
            frames.close()
        }
    }
}
