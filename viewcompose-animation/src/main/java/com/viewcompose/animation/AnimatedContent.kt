package com.viewcompose.animation

import com.viewcompose.animation.core.AnimationSpec
import com.viewcompose.animation.core.tween
import com.viewcompose.runtime.mutableStateOf
import com.viewcompose.ui.modifier.Modifier
import com.viewcompose.ui.modifier.alpha
import com.viewcompose.ui.modifier.fillMaxSize
import com.viewcompose.ui.foundation.Box
import com.viewcompose.ui.foundation.BoxScope
import com.viewcompose.ui.foundation.SideEffect
import com.viewcompose.ui.foundation.UiTreeBuilder
import com.viewcompose.ui.foundation.remember

/**
 * Internal cross-fade engine retained behind the single public [Crossfade] contract.
 *
 * A state change keeps the outgoing subtree mounted while the incoming subtree is rendered above it
 * in a fill-size [Box]. [content] is therefore invoked twice during a transition and once while
 * settled. The outgoing state is committed and removed in a [SideEffect] after its alpha reaches the
 * terminal threshold, so tree mutation does not occur during composition.
 *
 * Equality determines whether a transition is needed. If another target arrives mid-transition,
 * incoming content switches to the latest target at the existing progress rather than restarting
 * from zero; the last committed state remains the outgoing content. [DisplayedState] keeps the
 * mounted value distinct from transition absence, so nullable states retain the same lifecycle.
 *
 * This release animates alpha only; it does not provide size transforms, content keys, or per-pair
 * transition scopes. Descendant state must be keyed by [targetState] when separate state per content
 * identity is required.
 *
 * @param T logical state used to produce content
 * @receiver tree builder for the current composition
 * @param targetState state whose content should become fully visible
 * @param modifier modifier applied to the outer stacking container
 * @param animationSpec progress animation specification
 * @param content content rendered with either the outgoing or incoming state
 */
private fun <T> UiTreeBuilder.crossfadeContent(
    targetState: T,
    modifier: Modifier = Modifier,
    animationSpec: AnimationSpec,
    content: BoxScope.(T) -> Unit,
) {
    val displayedState = remember {
        mutableStateOf(DisplayedState(targetState))
    }
    val hasPendingTransition = targetState != displayedState.value.value
    val outgoingState = displayedState.value.takeIf { hasPendingTransition }
    val progress = animateFloatAsState(
        targetValue = if (hasPendingTransition) 1f else 0f,
        animationSpec = animationSpec,
    )
    val incomingAlpha = if (hasPendingTransition) {
        progress.value
    } else {
        1f
    }.coerceIn(0f, 1f)
    val outgoingAlpha = 1f - incomingAlpha
    if (hasPendingTransition && outgoingAlpha <= 0.001f) {
        // Commit after rendering so the outgoing subtree remains mounted through the terminal frame.
        SideEffect {
            displayedState.value = DisplayedState(targetState)
        }
    }
    Box(
        modifier = modifier,
    ) {
        outgoingState?.let { previous ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .alpha(outgoingAlpha),
            ) {
                content(previous.value)
            }
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .alpha(incomingAlpha),
        ) {
            content(targetState)
        }
    }
}

private data class DisplayedState<T>(val value: T)

/**
 * Cross-fades state content using one fixed [animationSpec].
 *
 * A state change keeps the outgoing subtree mounted while the incoming subtree is rendered above
 * it in a fill-size Box. Equality controls transitions, nullable states are supported, and a new
 * target during a transition replaces the incoming content without discarding the last committed
 * outgoing state. This API intentionally promises alpha cross-fading only.
 *
 * @sample com.viewcompose.animation.samples.crossfadeSample
 *
 * @param T logical state used to produce content
 * @receiver tree builder for the current composition
 * @param targetState state whose content should become fully visible
 * @param modifier modifier applied to the outer stacking container
 * @param animationSpec fixed specification used for cross-fade progress
 * @param content content rendered with the outgoing or incoming state
 */
fun <T> UiTreeBuilder.Crossfade(
    targetState: T,
    modifier: Modifier = Modifier,
    animationSpec: AnimationSpec = tween(),
    content: BoxScope.(T) -> Unit,
) {
    crossfadeContent(
        targetState = targetState,
        modifier = modifier,
        animationSpec = animationSpec,
        content = content,
    )
}
