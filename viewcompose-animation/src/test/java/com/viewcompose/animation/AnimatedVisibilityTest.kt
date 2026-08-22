@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")

package com.viewcompose.animation

import com.viewcompose.animation.core.EasingDefaults
import com.viewcompose.animation.core.snap
import com.viewcompose.animation.core.tween
import com.viewcompose.runtime.composition.ComposerLite
import com.viewcompose.runtime.frame.MonotonicFrameClock
import com.viewcompose.ui.environment.UiEnvironmentValues
import com.viewcompose.ui.environment.UiLayoutDirection
import com.viewcompose.ui.foundation.ComposerContext
import com.viewcompose.ui.foundation.DisposableEffect
import com.viewcompose.ui.foundation.ProvideMonotonicFrameClock
import com.viewcompose.ui.foundation.Text
import com.viewcompose.ui.foundation.UiEnvironment
import com.viewcompose.ui.foundation.UiTreeBuilder
import com.viewcompose.ui.layout.BoxAlignment
import com.viewcompose.ui.modifier.TransformOrigin
import com.viewcompose.ui.node.NodeType
import com.viewcompose.ui.node.VNode
import com.viewcompose.ui.node.spec.AnimatedVisibilityHostNodeProps
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class AnimatedVisibilityTest {
    @Test
    fun `first composition remains settled and does not play initial enter`() {
        val harness = VisibilityHarness(
            enter = fadeIn(linearTween(), initialAlpha = 0.2f) +
                slideIn(SlideDirection.Start, linearTween()) +
                scaleIn(linearTween(), initialScale = 0.8f),
        )

        val tree = harness.compose(true)
        val spec = tree.visibilityHostSpec()

        assertEquals(1f, spec.alpha, 0f)
        assertEquals(1f, spec.scaleX, 0f)
        assertEquals(0f, spec.translationXFraction, 0f)
        assertTrue(spec.active)
        assertEquals(1, tree.visibilityHost().children.size)
        harness.dispose()
    }

    @Test
    fun `hidden first composition keeps only a zero size identity host`() {
        val harness = VisibilityHarness()

        val tree = harness.compose(false)
        val spec = tree.visibilityHostSpec()

        assertEquals(0f, spec.alpha, 0f)
        assertEquals(0f, spec.widthScale, 0f)
        assertEquals(0f, spec.heightScale, 0f)
        assertFalse(spec.active)
        assertTrue(tree.visibilityHost().children.isEmpty())
        harness.dispose()
    }

    @Test
    fun `slide scale reveal and alignment sample one shared enter segment`() {
        val origin = TransformOrigin(0.25f, 0.75f)
        val harness = VisibilityHarness(
            enter = fadeIn(linearTween(), initialAlpha = 0.2f) +
                expandIn(linearTween(), initialScale = 0.4f, alignment = BoxAlignment.BottomEnd) +
                slideIn(SlideDirection.Start, linearTween(), distanceFraction = 0.5f) +
                scaleIn(linearTween(), initialScale = 0.8f, transformOrigin = origin),
            exit = fadeOut(linearTween()) + shrinkOut(linearTween()),
        )

        harness.compose(false)
        val start = harness.compose(true).visibilityHostSpec()
        harness.advanceTo(0L)
        harness.advanceTo(150_000_000L)
        val midpoint = harness.compose(true).visibilityHostSpec()

        assertEquals(0.2f, start.alpha, 0f)
        assertEquals(0.4f, start.widthScale, 0f)
        assertEquals(-0.5f, start.translationXFraction, 0f)
        assertEquals(0.8f, start.scaleX, 0f)
        assertEquals(origin, start.transformOrigin)
        assertEquals(BoxAlignment.BottomEnd, start.contentAlignment)
        assertEquals(0.6f, midpoint.alpha, 0.01f)
        assertEquals(0.7f, midpoint.widthScale, 0.01f)
        assertEquals(-0.25f, midpoint.translationXFraction, 0.01f)
        assertEquals(0.9f, midpoint.scaleX, 0.01f)
        harness.dispose()
    }

    @Test
    fun `logical slide direction mirrors in rtl and freezes for the segment`() {
        fun startOffset(direction: UiLayoutDirection): Float {
            val harness = VisibilityHarness(
                enter = slideInHorizontally(
                    from = SlideDirection.Start,
                    animationSpec = linearTween(),
                    distanceFraction = 0.5f,
                ),
                environment = UiEnvironmentValues.Default.copy(layoutDirection = direction),
            )
            harness.compose(false)
            val offset = harness.compose(true).visibilityHostSpec().translationXFraction
            harness.dispose()
            return offset
        }

        assertEquals(-0.5f, startOffset(UiLayoutDirection.Ltr), 0f)
        assertEquals(0.5f, startOffset(UiLayoutDirection.Rtl), 0f)
    }

    @Test
    fun `exit removes interaction immediately and retains content until all channels settle`() {
        val harness = VisibilityHarness(
            exit = fadeOut(linearTween()) +
                slideOut(SlideDirection.End, linearTween()) +
                scaleOut(linearTween(), targetScale = 0.8f),
        )

        harness.compose(true)
        val start = harness.compose(false)

        assertFalse(start.visibilityHostSpec().active)
        assertEquals(1, start.visibilityHost().children.size)

        harness.advanceTo(0L)
        harness.advanceTo(300_000_000L)
        val settled = harness.compose(false)

        assertTrue(settled.visibilityHost().children.isEmpty())
        assertEquals(0f, settled.visibilityHostSpec().widthScale, 0f)
        harness.dispose()
    }

    @Test
    fun `rapid exit enter reversal resumes every channel from its live sample`() {
        val harness = VisibilityHarness(
            enter = fadeIn(linearTween()) + slideIn(SlideDirection.Start, linearTween()),
            exit = fadeOut(linearTween()) + slideOut(SlideDirection.End, linearTween()),
        )

        harness.compose(true)
        harness.compose(false)
        harness.advanceTo(0L)
        harness.advanceTo(150_000_000L)
        val exiting = harness.compose(false).visibilityHostSpec()
        val reversed = harness.compose(true).visibilityHostSpec()

        assertEquals(0.5f, exiting.alpha, 0.01f)
        assertEquals(0.5f, exiting.translationXFraction, 0.01f)
        assertEquals(exiting.alpha, reversed.alpha, 0.01f)
        assertEquals(exiting.translationXFraction, reversed.translationXFraction, 0.01f)
        assertTrue(reversed.active)
        harness.dispose()
    }

    @Test
    fun `descendant enter exit joins parent transition and extends removal duration`() {
        val harness = VisibilityHarness(
            enter = fadeIn(snap()),
            exit = fadeOut(snap()),
            content = {
                AnimatedEnterExit(
                    enter = slideIn(SlideDirection.Up, longLinearTween()),
                    exit = slideOut(SlideDirection.Down, longLinearTween()),
                ) {
                    Text("Child")
                }
            },
        )

        harness.compose(false)
        val start = harness.compose(true)
        val childStart = start.descendantVisibilityHostSpec()
        harness.advanceTo(0L)
        harness.advanceTo(300_000_000L)
        val midpoint = harness.compose(true)
        val childMidpoint = midpoint.descendantVisibilityHostSpec()

        assertEquals(1f, start.visibilityHostSpec().alpha, 0f)
        assertEquals(-1f, childStart.translationYFraction, 0f)
        assertEquals(-0.5f, childMidpoint.translationYFraction, 0.01f)
        assertTrue(midpoint.visibilityHost().children.isNotEmpty())
        harness.dispose()
    }

    @Test
    fun `last applicable element wins duplicate channel and size alignment`() {
        val harness = VisibilityHarness(
            enter = fadeIn(linearTween(), initialAlpha = 0.1f) +
                fadeIn(linearTween(), initialAlpha = 0.4f) +
                expandHorizontally(
                    linearTween(),
                    initialScale = 0.2f,
                    alignment = BoxAlignment.CenterEnd,
                ),
        )

        harness.compose(false)
        val spec = harness.compose(true).visibilityHostSpec()

        assertEquals(0.4f, spec.alpha, 0f)
        assertEquals(0.2f, spec.widthScale, 0f)
        assertEquals(BoxAlignment.CenterEnd, spec.contentAlignment)
        harness.dispose()
    }

    @Test
    fun `external mutable state mirrors shared descendant idle completion`() {
        val state = MutableTransitionState(false)
        val harness = VisibilityHarness(
            enter = EnterTransition.None,
            state = state,
            content = {
                AnimatedEnterExit(enter = scaleIn(longLinearTween(), initialScale = 0.8f)) {
                    Text("Child")
                }
            },
        )

        harness.compose(false)
        state.targetState = true
        harness.compose(true)
        assertFalse(state.isIdle)
        harness.advanceTo(0L)
        harness.advanceTo(600_000_000L)
        harness.compose(true)

        assertTrue(state.currentState)
        assertTrue(state.targetState)
        assertTrue(state.isIdle)
        harness.dispose()
    }

    @Test
    fun `snap resolved reduced motion reaches both endpoints without retaining exit content`() {
        val state = MutableTransitionState(false)
        val harness = VisibilityHarness(
            enter = fadeIn(snap()) + slideIn(animationSpec = snap()) + scaleIn(animationSpec = snap()),
            exit = fadeOut(snap()) + slideOut(animationSpec = snap()) + scaleOut(animationSpec = snap()),
            state = state,
            content = {
                AnimatedEnterExit(
                    enter = slideInVertically(animationSpec = snap()),
                    exit = slideOutVertically(animationSpec = snap()),
                ) {
                    Text("Child")
                }
            },
        )

        harness.compose(false)
        state.targetState = true
        harness.compose(true)
        harness.advanceTo(0L)
        harness.advanceTo(1L)
        val shown = harness.compose(true)
        assertTrue(state.currentState)
        assertTrue(state.isIdle)
        assertEquals(1f, shown.visibilityHostSpec().alpha, 0f)
        assertEquals(0f, shown.visibilityHostSpec().translationXFraction, 0f)

        state.targetState = false
        harness.compose(false)
        harness.advanceTo(2L)
        harness.advanceTo(3L)
        val hidden = harness.compose(false)
        assertFalse(state.currentState)
        assertTrue(state.isIdle)
        assertTrue(hidden.visibilityHost().children.isEmpty())
        assertFalse(hidden.visibilityHostSpec().active)
        harness.dispose()
    }

    @Test
    fun `invalid transition values fail at construction`() {
        assertThrows(IllegalArgumentException::class.java) {
            slideIn(distanceFraction = Float.NaN)
        }
        assertThrows(IllegalArgumentException::class.java) {
            slideOutHorizontally(towards = SlideDirection.Down)
        }
        assertThrows(IllegalArgumentException::class.java) {
            scaleIn(initialScale = Float.POSITIVE_INFINITY)
        }
        assertThrows(IllegalArgumentException::class.java) {
            fadeOut(targetAlpha = Float.NaN)
        }
    }

    @Test
    fun `disposing a running parent releases descendant content exactly once`() {
        var starts = 0
        var disposals = 0
        val harness = VisibilityHarness(
            content = {
                AnimatedEnterExit(exit = slideOut(animationSpec = longLinearTween())) {
                    DisposableEffect(Unit) {
                        starts += 1
                        onDispose { disposals += 1 }
                    }
                    Text("Child")
                }
            },
        )

        harness.compose(true)
        harness.compose(false)
        harness.dispose()

        assertEquals(1, starts)
        assertEquals(1, disposals)
    }

    private class VisibilityHarness(
        private val enter: EnterTransition = fadeIn(linearTween()) + expandIn(linearTween()),
        private val exit: ExitTransition = shrinkOut(linearTween()) + fadeOut(linearTween()),
        private val environment: UiEnvironmentValues = UiEnvironmentValues.Default,
        private val state: MutableTransitionState<Boolean>? = null,
        private val content: AnimatedVisibilityScope.() -> Unit = { Text("Content") },
    ) {
        private val composer = ComposerLite()
        private val clock = SuspendedFrameClock()

        fun compose(visible: Boolean): List<VNode> {
            composer.requestRootRecompose()
            val result = ComposerContext.withComposer(
                composer = composer,
                coroutineContext = Dispatchers.Unconfined,
            ) {
                composer.composeRoot {
                    UiTreeBuilder().apply {
                        ProvideMonotonicFrameClock(clock) {
                            UiEnvironment(environment) {
                                val externalState = state
                                if (externalState == null) {
                                    AnimatedVisibility(
                                        visible = visible,
                                        enter = enter,
                                        exit = exit,
                                        content = content,
                                    )
                                } else {
                                    AnimatedVisibility(
                                        visibleState = externalState,
                                        enter = enter,
                                        exit = exit,
                                        content = content,
                                    )
                                }
                            }
                        }
                    }.build()
                }
            }
            composer.commitSideEffects()
            return result
        }

        fun advanceTo(frameTimeNanos: Long) {
            clock.advanceTo(frameTimeNanos)
        }

        fun dispose() {
            composer.dispose()
            clock.cancelPending()
        }
    }

    private class SuspendedFrameClock : MonotonicFrameClock {
        private val frames = Channel<Long>(capacity = Channel.UNLIMITED)

        override suspend fun <R> withFrameNanos(onFrame: (Long) -> R): R {
            return onFrame(frames.receive())
        }

        fun advanceTo(frameTimeNanos: Long) {
            runBlocking {
                frames.send(frameTimeNanos)
                yield()
            }
        }

        fun cancelPending() {
            frames.close()
        }
    }

    private companion object {
        fun linearTween() = tween(durationMillis = 300, easing = EasingDefaults.Linear)

        fun longLinearTween() = tween(durationMillis = 600, easing = EasingDefaults.Linear)
    }
}

private fun List<VNode>.visibilityHost(): VNode {
    return single { it.type == NodeType.AnimatedVisibilityHost }
}

private fun List<VNode>.visibilityHostSpec(): AnimatedVisibilityHostNodeProps {
    return visibilityHost().spec as AnimatedVisibilityHostNodeProps
}

private fun List<VNode>.descendantVisibilityHostSpec(): AnimatedVisibilityHostNodeProps {
    val descendant = visibilityHost().children.single { it.type == NodeType.AnimatedVisibilityHost }
    return descendant.spec as AnimatedVisibilityHostNodeProps
}
