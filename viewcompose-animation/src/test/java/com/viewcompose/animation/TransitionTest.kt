@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")

package com.viewcompose.animation

/*
 * 测试职责：覆盖 animation DSL 中的 Transition 行为，防止关键契约在后续重构中回退。
 * Test responsibility: covers Transition behavior in animation DSL and guards the contract against regressions.
 */

import com.viewcompose.animation.core.EasingDefaults
import com.viewcompose.animation.core.tween
import com.viewcompose.runtime.Snapshot
import com.viewcompose.runtime.composition.ComposerLite
import com.viewcompose.ui.foundation.ComposerContext
import com.viewcompose.ui.foundation.UiTreeBuilder
import org.junit.Assert.assertEquals
import org.junit.Test

class TransitionTest {
    @Test
    fun `multiple channels can register duration inside one composition snapshot`() {
        val transition = Transition(
            initialState = false,
            label = "test",
        )
        transition.updateTarget(true)

        Snapshot.takeSnapshot().use { snapshot ->
            snapshot.enter {
                transition.registerChannelDuration(100L)
                transition.registerChannelDuration(300L)
                transition.registerChannelDuration(200L)
            }
        }

        assertEquals(300L, transition.segmentDurationNanos)
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

    private class FloatTransitionCompositionHarness {
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
                        sample = transition.animateFloatBySegment(
                            transitionSpec = { _, _ ->
                                tween(
                                    durationMillis = 300,
                                    easing = EasingDefaults.Linear,
                                )
                            },
                            segmentEndpoints = { _, segmentTarget, current ->
                                current to if (segmentTarget) 1f else 0f
                            },
                            valueForSettledState = { settled -> if (settled) 1f else 0f },
                        ).value
                    }
                }
            }
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

        fun dispose() {
            composer.dispose()
        }
    }
}
