@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")

package com.viewcompose.animation

import com.viewcompose.animation.core.EasingDefaults
import com.viewcompose.animation.core.snap
import com.viewcompose.animation.core.tween
import com.viewcompose.runtime.composition.ComposerLite
import com.viewcompose.runtime.frame.MonotonicFrameClock
import com.viewcompose.ui.foundation.ComposerContext
import com.viewcompose.ui.foundation.DisposableEffect
import com.viewcompose.ui.foundation.ProvideMonotonicFrameClock
import com.viewcompose.ui.foundation.Text
import com.viewcompose.ui.foundation.UiEnvironment
import com.viewcompose.ui.foundation.UiTreeBuilder
import com.viewcompose.ui.environment.UiEnvironmentValues
import com.viewcompose.ui.environment.UiLayoutDirection
import com.viewcompose.ui.modifier.TransformOrigin
import com.viewcompose.ui.node.NodeType
import com.viewcompose.ui.node.VNode
import com.viewcompose.ui.node.spec.AnimatedContentHostNodeProps
import com.viewcompose.ui.node.spec.AnimatedContentItemNodeProps
import com.viewcompose.ui.node.spec.TextNodeProps
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class AnimatedContentTest {
    @Test
    fun `unequal keys retain one inactive outgoing and one active incoming item`() {
        val harness = AnimatedContentHarness<String>()

        val initial = harness.compose("A")
        val requestFrame = harness.compose("B")
        val replacement = harness.compose("B")

        assertEquals(listOf("A"), initial.contentLabels())
        assertEquals(listOf("A"), requestFrame.contentLabels())
        assertEquals(listOf("A", "B"), replacement.contentLabels())
        assertEquals(listOf(false, true), replacement.itemSpecs().map { it.active })
        assertEquals(1f, replacement.itemSpecs().first().alpha, 0f)
        assertEquals(0f, replacement.itemSpecs().last().alpha, 0f)
        harness.dispose()
    }

    @Test
    fun `rapid retarget promotes the last incoming item and retains at most two trees`() {
        val harness = AnimatedContentHarness<String>()

        harness.compose("A")
        harness.compose("B")
        harness.compose("B")
        val cRequestFrame = harness.compose("C")
        val cReplacement = harness.compose("C")

        assertEquals(listOf("A", "B"), cRequestFrame.contentLabels())
        assertEquals(listOf("B", "C"), cReplacement.contentLabels())
        assertEquals(2, cReplacement.host().children.size)
        harness.dispose()
    }

    @Test
    fun `equal content keys patch one retained subtree without selecting a transition`() {
        data class Screen(val id: Int, val label: String)

        var transitionSelections = 0
        val harness = AnimatedContentHarness<Screen>(
            contentKey = { it.id },
            transitionSpec = {
                transitionSelections += 1
                fadeIn() togetherWith fadeOut()
            },
            label = { it.label },
        )

        harness.compose(Screen(1, "A"))
        harness.compose(Screen(1, "A updated"))
        val patched = harness.compose(Screen(1, "A updated"))

        assertEquals(listOf("A updated"), patched.contentLabels())
        assertEquals(1, patched.host().children.size)
        assertEquals(0, transitionSelections)
        harness.dispose()
    }

    @Test
    fun `pair scope resolves logical slide scale origin z order and nullable identities`() {
        val origin = TransformOrigin(0.25f, 0.75f)
        val harness = AnimatedContentHarness<String?>(
            transitionSpec = {
                ContentTransform(
                    targetContentEnter = fadeIn(
                        animationSpec = linearTween(),
                        initialAlpha = 0.2f,
                    ) + slideIntoContainer(
                        from = ContentSlideDirection.End,
                        animationSpec = linearTween(),
                        distanceFraction = 0.5f,
                    ) + scaleIn(
                        initialScale = 0.8f,
                        transformOrigin = origin,
                        animationSpec = linearTween(),
                    ),
                    initialContentExit = fadeOut(linearTween()),
                    sizeTransform = SizeTransform(linearTween(), clip = true),
                    targetContentZIndex = 3f,
                )
            },
            label = { it ?: "null" },
        )

        harness.compose(null)
        harness.compose("target")
        val replacement = harness.compose("target")
        val hostSpec = replacement.host().spec as AnimatedContentHostNodeProps
        val incoming = replacement.itemSpecs().last()

        assertEquals(listOf("null", "target"), replacement.contentLabels())
        assertTrue(hostSpec.sizeTransformEnabled)
        assertTrue(hostSpec.clipToBounds)
        assertEquals(0f, hostSpec.sizeProgress, 0f)
        assertEquals(0.2f, incoming.alpha, 0f)
        assertEquals(0.8f, incoming.scaleX, 0f)
        assertEquals(0.8f, incoming.scaleY, 0f)
        assertEquals(0.5f, incoming.translationXFraction, 0f)
        assertEquals(0f, incoming.translationYFraction, 0f)
        assertEquals(origin, incoming.transformOrigin)
        assertEquals(3f, replacement.host().children.last().modifier.zIndexSum(), 0f)
        harness.dispose()
    }

    @Test
    fun `logical start slide resolves against the provided layout direction`() {
        fun incomingStartOffset(direction: UiLayoutDirection): Float {
            val harness = AnimatedContentHarness<String>(
                environment = UiEnvironmentValues.Default.copy(layoutDirection = direction),
                transitionSpec = {
                    slideIntoContainer(
                        from = ContentSlideDirection.Start,
                        animationSpec = linearTween(),
                        distanceFraction = 0.5f,
                    ) togetherWith fadeOut(linearTween())
                },
            )
            harness.compose("A")
            harness.compose("B")
            val replacement = harness.compose("B")
            val offset = replacement.itemSpecs().last().translationXFraction
            harness.dispose()
            return offset
        }

        assertEquals(-0.5f, incomingStartOffset(UiLayoutDirection.Ltr), 0f)
        assertEquals(0.5f, incomingStartOffset(UiLayoutDirection.Rtl), 0f)
    }

    @Test
    fun `null size transform uses maximum child measurement policy`() {
        val harness = AnimatedContentHarness<String>(
            transitionSpec = {
                (fadeIn() togetherWith fadeOut()) using null
            },
        )

        harness.compose("A")
        harness.compose("B")
        val replacement = harness.compose("B")
        val hostSpec = replacement.host().spec as AnimatedContentHostNodeProps

        assertFalse(hostSpec.sizeTransformEnabled)
        assertFalse(hostSpec.clipToBounds)
        harness.dispose()
    }

    @Test
    fun `invalid finite contracts fail before a content transform is published`() {
        assertThrows(IllegalArgumentException::class.java) {
            ContentTransform(targetContentZIndex = Float.NaN)
        }
        val scope = AnimatedContentTransitionScope(
            initialState = "A",
            targetState = "B",
            layoutDirection = com.viewcompose.ui.environment.UiLayoutDirection.Ltr,
        )
        assertThrows(IllegalArgumentException::class.java) {
            scope.slideIntoContainer(
                from = ContentSlideDirection.Start,
                distanceFraction = Float.POSITIVE_INFINITY,
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            scope.scaleIn(initialScale = Float.NaN)
        }
    }

    @Test
    fun `disposing the owner releases both retained content trees exactly once`() {
        val started = mutableListOf<String>()
        val disposed = mutableListOf<String>()
        val harness = AnimatedContentHarness<String>(
            content = { state ->
                DisposableEffect(state) {
                    started += state
                    onDispose { disposed += state }
                }
                Text(state)
            },
        )

        harness.compose("A")
        harness.compose("B")
        harness.compose("B")

        assertEquals(listOf("A", "B"), started)
        harness.dispose()
        assertEquals(listOf("A", "B"), disposed.sorted())
    }

    @Test
    fun `aborted request candidate does not replace the committed content identity`() {
        val harness = AnimatedContentHarness<String>()

        harness.compose("A")
        val aborted = harness.composeAndAbort("B")
        val retry = harness.compose("A")

        assertEquals(listOf("A"), aborted.contentLabels())
        assertEquals(listOf("A"), retry.contentLabels())
        harness.dispose()
    }

    @Test
    fun `midpoint retarget releases the oldest tree and starts from the live incoming sample`() {
        val started = mutableListOf<String>()
        val disposed = mutableListOf<String>()
        val harness = AnimatedContentHarness<String>(
            transitionSpec = {
                ContentTransform(
                    targetContentEnter = fadeIn(linearTween()) + slideIntoContainer(
                        from = ContentSlideDirection.End,
                        animationSpec = linearTween(),
                        distanceFraction = 0.4f,
                    ) + scaleIn(
                        initialScale = 0.8f,
                        animationSpec = linearTween(),
                    ),
                    initialContentExit = fadeOut(linearTween()),
                    sizeTransform = SizeTransform(linearTween()),
                )
            },
            content = { state ->
                DisposableEffect(state) {
                    started += state
                    onDispose { disposed += state }
                }
                Text(state)
            },
        )

        harness.compose("A")
        harness.compose("B")
        harness.compose("B")
        harness.advanceTo(0L)
        harness.advanceTo(150_000_000L)
        val midpoint = harness.compose("B")
        val midpointIncoming = midpoint.itemSpecs().last()
        val midpointHost = midpoint.host().spec as AnimatedContentHostNodeProps

        assertEquals(0.5f, midpointIncoming.alpha, 0.01f)
        assertEquals(0.2f, midpointIncoming.translationXFraction, 0.01f)
        assertEquals(0.9f, midpointIncoming.scaleX, 0.01f)
        assertEquals(0.5f, midpointHost.sizeProgress, 0.01f)

        harness.compose("C")
        val retarget = harness.compose("C")

        assertEquals(listOf("B", "C"), retarget.contentLabels())
        assertEquals(0.5f, retarget.itemSpecs().first().alpha, 0.01f)
        assertEquals(listOf("A", "B", "C"), started)
        assertEquals(listOf("A"), disposed)

        harness.dispose()
        assertEquals(listOf("A", "B", "C"), disposed.sorted())
    }

    @Test
    fun `nested content transitions keep independent keyed hosts`() {
        data class NestedState(val outer: String, val inner: String)

        val harness = AnimatedContentHarness<NestedState>(
            contentKey = { it.outer },
            content = { state ->
                AnimatedContent(targetState = state.inner) { inner ->
                    Text(inner)
                }
            },
        )

        harness.compose(NestedState("A", "x"))
        harness.compose(NestedState("B", "y"))
        val replacement = harness.compose(NestedState("B", "y"))
        val nestedHosts = replacement.host().children.map { item ->
            item.children.single { child -> child.type == NodeType.AnimatedContentHost }
        }

        assertEquals(2, nestedHosts.size)
        assertEquals(listOf("x", "y"), nestedHosts.map { host ->
            val text = host.children.single().children.single { it.type == NodeType.Text }
            (text.spec as TextNodeProps).document.text
        })
        harness.dispose()
    }

    @Test
    fun `snap transform releases outgoing content at the first sampled play time`() {
        val harness = AnimatedContentHarness<String>(
            transitionSpec = {
                (fadeIn(snap()) togetherWith fadeOut(snap())) using SizeTransform(snap())
            },
        )

        harness.compose("A")
        harness.compose("B")
        harness.compose("B")
        harness.advanceTo(0L)
        harness.advanceTo(1L)
        val settled = harness.compose("B")

        assertEquals(listOf("B"), settled.contentLabels())
        assertEquals(1, settled.host().children.size)
        harness.dispose()
    }

    private class AnimatedContentHarness<S>(
        private val contentKey: (S) -> Any? = { it },
        private val transitionSpec: AnimatedContentTransitionScope<S>.() -> ContentTransform = {
            fadeIn(linearTween()) togetherWith fadeOut(linearTween())
        },
        private val label: (S) -> String = { it.toString() },
        private val environment: UiEnvironmentValues = UiEnvironmentValues.Default,
        private val content: AnimatedContentScope<S>.(S) -> Unit = { state -> Text(label(state)) },
    ) {
        private val composer = ComposerLite()
        private val clock = SuspendedFrameClock()

        fun compose(target: S): List<VNode> {
            composer.requestRootRecompose()
            val result = ComposerContext.withComposer(
                composer = composer,
                coroutineContext = Dispatchers.Unconfined,
            ) {
                composer.composeRoot {
                    UiTreeBuilder().apply {
                        ProvideMonotonicFrameClock(clock) {
                            UiEnvironment(environment) {
                                AnimatedContent(
                                    targetState = target,
                                    contentKey = contentKey,
                                    transitionSpec = transitionSpec,
                                    content = content,
                                )
                            }
                        }
                    }.build()
                }
            }
            composer.commitSideEffects()
            return result
        }

        fun composeAndAbort(target: S): List<VNode> {
            composer.requestRootRecompose()
            val prepared = ComposerContext.withComposer(
                composer = composer,
                coroutineContext = Dispatchers.Unconfined,
            ) {
                composer.prepareRoot {
                    buildTree(target)
                }
            }
            val result = prepared.value
            prepared.abort()
            return result
        }

        fun advanceTo(frameTimeNanos: Long) {
            clock.advanceTo(frameTimeNanos)
        }

        private fun buildTree(target: S): List<VNode> {
            return UiTreeBuilder().apply {
                ProvideMonotonicFrameClock(clock) {
                    UiEnvironment(environment) {
                        AnimatedContent(
                            targetState = target,
                            contentKey = contentKey,
                            transitionSpec = transitionSpec,
                            content = content,
                        )
                    }
                }
            }.build()
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
        fun linearTween() = tween(
            durationMillis = 300,
            easing = EasingDefaults.Linear,
        )
    }
}

private fun List<VNode>.host(): VNode {
    return single { it.type == NodeType.AnimatedContentHost }
}

private fun List<VNode>.itemSpecs(): List<AnimatedContentItemNodeProps> {
    return host().children.map { it.spec as AnimatedContentItemNodeProps }
}

private fun List<VNode>.contentLabels(): List<String> {
    return host().children.map { item ->
        val text = item.children.single { it.type == NodeType.Text }
        (text.spec as TextNodeProps).document.text
    }
}

private fun com.viewcompose.ui.modifier.Modifier.zIndexSum(): Float {
    return elements.sumOf { element ->
        ((element as? com.viewcompose.ui.modifier.ZIndexModifierElement)?.zIndex ?: 0f).toDouble()
    }.toFloat()
}
