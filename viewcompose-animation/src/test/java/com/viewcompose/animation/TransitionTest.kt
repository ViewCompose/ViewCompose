@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")

package com.viewcompose.animation

/*
 * 测试职责：覆盖 animation DSL 中的 Transition 行为，防止关键契约在后续重构中回退。
 * Test responsibility: covers Transition behavior in animation DSL and guards the contract against regressions.
 */

import com.viewcompose.animation.core.EasingDefaults
import com.viewcompose.animation.core.AnimationConverter
import com.viewcompose.animation.core.FiniteAnimationSpec
import com.viewcompose.animation.core.spring
import com.viewcompose.animation.core.tween
import com.viewcompose.runtime.composition.ComposerLite
import com.viewcompose.runtime.observation.RuntimeObservation
import com.viewcompose.ui.foundation.ComposerContext
import com.viewcompose.ui.foundation.UiTreeBuilder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class TransitionTest {
    @Test
    fun `transition mirrors publish one complete logical segment`() {
        val transition = Transition(
            initialState = false,
            label = "atomic",
        )
        val published = mutableListOf<TransitionSnapshot>()
        val (_, observation) = RuntimeObservation.observeReads(
            onInvalidated = { published += transition.snapshot() },
        ) {
            transition.snapshot()
        }

        transition.updateTarget(true)

        assertEquals(
            listOf(
                TransitionSnapshot(
                    current = false,
                    target = true,
                    running = true,
                    version = 1L,
                    playTimeNanos = 0L,
                    segmentInitial = false,
                    segmentTarget = true,
                ),
            ),
            published,
        )
        observation.dispose()
    }

    @Test
    fun `mutable transition state mirrors current target and idle atomically`() {
        val state = MutableTransitionState(initialState = false)
        val published = mutableListOf<Triple<Boolean, Boolean, Boolean>>()
        val (_, observation) = RuntimeObservation.observeReads(
            onInvalidated = {
                published += Triple(state.currentState, state.targetState, state.isIdle)
            },
        ) {
            Triple(state.currentState, state.targetState, state.isIdle)
        }

        state.syncFromTransition(
            currentState = false,
            targetState = true,
            isIdle = false,
        )

        assertEquals(listOf(Triple(false, true, false)), published)
        observation.dispose()
    }

    @Test
    fun `committed channel set recomputes its longest duration after removal`() {
        val transition = Transition(
            initialState = false,
            label = "test",
        )
        val composer = ComposerLite()

        fun compose(includeLongChannel: Boolean) {
            ComposerContext.withComposer(composer) {
                composer.requestRootRecompose()
                composer.composeRoot {
                    UiTreeBuilder().apply {
                        transition.updateTarget(true)
                        transition.animateFloat(
                            transitionSpec = { tween(durationMillis = 100) },
                            targetValueByState = { state -> if (state) 1f else 0f },
                        )
                        if (includeLongChannel) {
                            transition.animateInt(
                                transitionSpec = { tween(durationMillis = 300) },
                                targetValueByState = { state -> if (state) 10 else 0 },
                            )
                        }
                    }
                }
            }
            composer.commitSideEffects()
        }

        compose(includeLongChannel = true)
        assertEquals(300_000_000L, transition.segmentDurationNanos)

        compose(includeLongChannel = false)
        assertEquals(100_000_000L, transition.segmentDurationNanos)
        composer.dispose()
    }

    @Test
    fun `a new segment starts from its current sample after the previous segment completed`() {
        val harness = FloatTransitionCompositionHarness()

        assertEquals(0f, harness.compose(target = false), 0f)
        assertEquals(0f, harness.compose(target = true), 0f)
        harness.advanceToEnd()
        harness.compose(target = true)
        assertEquals(1f, harness.compose(target = true), 0f)

        assertEquals(1f, harness.compose(target = false), 0f)
        assertEquals(1f, harness.compose(target = false), 0f)
        harness.dispose()
    }

    @Test
    fun `segment identity is stable for frames and changes only for a new segment`() {
        val harness = FloatTransitionCompositionHarness()

        harness.compose(target = false)
        val settledSegment = harness.segment()
        harness.compose(target = true)
        val activeSegment = harness.segment()

        assertNotSame(settledSegment, activeSegment)
        assertTrue(activeSegment.isTransitioningTo(initial = false, target = true))

        harness.advanceTo(playTimeNanos = 100_000_000L)
        harness.compose(target = true)
        assertSame(activeSegment, harness.segment())
        harness.dispose()
    }

    @Test
    fun `segment receiver selects direction specific specifications once per segment`() {
        val selectedSegments = mutableListOf<TransitionSegment<Boolean>>()
        val harness = FloatTransitionCompositionHarness(
            transitionSpec = {
                selectedSegments += this
                if (isTransitioningTo(false, true)) {
                    tween(durationMillis = 400, easing = EasingDefaults.Linear)
                } else {
                    tween(durationMillis = 200, easing = EasingDefaults.Linear)
                }
            },
        )

        harness.compose(target = false)
        harness.compose(target = true)
        assertEquals(400_000_000L, harness.durationNanos())
        harness.compose(target = true)
        assertEquals(1, selectedSegments.size)

        harness.advanceToEnd()
        harness.compose(target = true)
        harness.compose(target = false)
        assertEquals(200_000_000L, harness.durationNanos())
        assertEquals(2, selectedSegments.size)
        harness.dispose()
    }

    @Test
    fun `generic channel samples every converter dimension on the shared timeline`() {
        val transition = Transition(initialState = false, label = "generic")
        val composer = ComposerLite()
        var sample = TestPoint(Float.NaN, Float.NaN)

        fun compose(target: Boolean) {
            ComposerContext.withComposer(composer) {
                composer.requestRootRecompose()
                composer.composeRoot {
                    UiTreeBuilder().apply {
                        transition.updateTarget(target)
                        sample = transition.animateValue(
                            converter = TestPointConverter,
                            transitionSpec = {
                                tween(durationMillis = 200, easing = EasingDefaults.Linear)
                            },
                            targetValueByState = { state ->
                                if (state) TestPoint(10f, 30f) else TestPoint(0f, 0f)
                            },
                        ).value
                    }
                }
            }
            composer.commitSideEffects()
        }

        compose(target = false)
        compose(target = true)
        transition.advanceFrame(
            version = transition.runtimeSegmentVersion(),
            playTimeNanos = 100_000_000L,
        )
        compose(target = true)
        compose(target = true)

        assertEquals(5f, sample.x, 0f)
        assertEquals(15f, sample.y, 0f)
        composer.dispose()
    }

    @Test
    fun `an interrupted segment retargets without reusing the previous segment play time`() {
        val harness = FloatTransitionCompositionHarness()

        assertEquals(0f, harness.compose(target = false), 0f)
        assertEquals(0f, harness.compose(target = true), 0f)
        harness.advanceTo(playTimeNanos = 150_000_000L)
        harness.compose(target = true)
        assertEquals(0.5f, harness.compose(target = true), 0f)

        assertEquals(0.5f, harness.compose(target = false), 0f)
        assertEquals(0.5f, harness.compose(target = false), 0f)
        harness.dispose()
    }

    @Test
    fun `physical transition retarget keeps outgoing velocity before reversing`() {
        val harness = FloatTransitionCompositionHarness(
            transitionSpec = { spring(dampingRatio = 0.45f, stiffness = 140f) },
        )

        harness.compose(target = false)
        harness.compose(target = true)
        harness.advanceTo(playTimeNanos = 80_000_000L)
        harness.compose(target = true)
        val retargetValue = harness.compose(target = false)
        harness.advanceTo(playTimeNanos = 16_000_000L)
        harness.compose(target = false)
        val continuedValue = harness.compose(target = false)

        assertEquals(true, continuedValue > retargetValue)
        harness.dispose()
    }

    private class FloatTransitionCompositionHarness(
        private val transitionSpec: TransitionSegment<Boolean>.() -> FiniteAnimationSpec = {
            tween(
                durationMillis = 300,
                easing = EasingDefaults.Linear,
            )
        },
    ) {
        private val transition = Transition(
            initialState = false,
            label = "test",
        )
        private val composer = ComposerLite()

        fun compose(target: Boolean): Float {
            var sample = Float.NaN
            ComposerContext.withComposer(composer) {
                composer.requestRootRecompose()
                composer.composeRoot {
                    UiTreeBuilder().apply {
                        transition.updateTarget(target)
                        sample = transition.sampleFloat(
                            transitionSpec = transitionSpec,
                            segmentEndpoints = { segment, current ->
                                current to if (segment.targetState) 1f else 0f
                            },
                            valueForSettledState = { settled -> if (settled) 1f else 0f },
                        )
                    }
                }
            }
            composer.commitSideEffects()
            return sample
        }

        fun advanceTo(playTimeNanos: Long) {
            transition.advanceFrame(
                version = transition.runtimeSegmentVersion(),
                playTimeNanos = playTimeNanos,
            )
        }

        fun advanceToEnd() {
            advanceTo(playTimeNanos = transition.segmentDurationNanos)
        }

        fun segment(): TransitionSegment<Boolean> = transition.segment

        fun durationNanos(): Long = transition.segmentDurationNanos

        fun dispose() {
            composer.dispose()
        }
    }

    private data class TestPoint(
        val x: Float,
        val y: Float,
    )

    private object TestPointConverter : AnimationConverter<TestPoint, TestPoint> {
        override val vectorSize: Int = 2
        override val zeroVelocity: TestPoint = TestPoint(0f, 0f)
        override val visibilityThreshold: TestPoint = TestPoint(0.01f, 0.01f)

        override fun convertToVector(value: TestPoint, destination: FloatArray) {
            destination[0] = value.x
            destination[1] = value.y
        }

        override fun convertFromVector(vector: FloatArray): TestPoint {
            return TestPoint(vector[0], vector[1])
        }

        override fun convertVelocityToVector(velocity: TestPoint, destination: FloatArray) {
            convertToVector(velocity, destination)
        }

        override fun convertVelocityFromVector(vector: FloatArray): TestPoint {
            return convertFromVector(vector)
        }
    }

    private fun Transition<Boolean>.snapshot(): TransitionSnapshot {
        return TransitionSnapshot(
            current = currentState,
            target = targetState,
            running = isRunning,
            version = segmentVersion,
            playTimeNanos = playTimeNanos,
            segmentInitial = segmentInitialState,
            segmentTarget = segmentTargetState,
        )
    }

    private data class TransitionSnapshot(
        val current: Boolean,
        val target: Boolean,
        val running: Boolean,
        val version: Long,
        val playTimeNanos: Long,
        val segmentInitial: Boolean,
        val segmentTarget: Boolean,
    )
}
