package com.viewcompose.animation

import com.viewcompose.animation.core.AnimationSpec
import com.viewcompose.animation.core.tween
import com.viewcompose.runtime.mutableStateOf
import com.viewcompose.ui.modifier.Modifier
import com.viewcompose.ui.modifier.alpha
import com.viewcompose.ui.modifier.fillMaxSize
import com.viewcompose.widget.core.Box
import com.viewcompose.widget.core.BoxScope
import com.viewcompose.widget.core.SideEffect
import com.viewcompose.widget.core.UiTreeBuilder
import com.viewcompose.widget.core.remember

/**
 * Cross-fades content for the last displayed state into content for [targetState].
 *
 * A state change keeps the outgoing subtree mounted while the incoming subtree is rendered above it
 * in a fill-size [Box]. [content] is therefore invoked twice during a transition and once while
 * settled. The outgoing state is committed and removed in a [SideEffect] after its alpha reaches the
 * terminal threshold, so tree mutation does not occur during composition.
 *
 * Equality determines whether a transition is needed. If another target arrives mid-transition,
 * incoming content switches to the latest target at the existing progress rather than restarting
 * from zero; the last committed state remains the outgoing content. Nullable `T` is supported by the
 * signature, but a previously displayed `null` is also the internal no-outgoing sentinel and cannot
 * be rendered as outgoing content.
 *
 * This release animates alpha only; it does not provide size transforms, content keys, or per-pair
 * transition scopes. Descendant state must be keyed by [targetState] when separate state per content
 * identity is required.
 *
 * @sample com.viewcompose.animation.samples.animatedContentSample
 *
 * @param T logical state used to produce content
 * @receiver tree builder for the current composition
 * @param targetState state whose content should become fully visible
 * @param modifier modifier applied to the outer stacking container
 * @param transitionSpec factory for the progress animation specification
 * @param content content rendered with either the outgoing or incoming state
 */
fun <T> UiTreeBuilder.AnimatedContent(
    targetState: T,
    modifier: Modifier = Modifier,
    transitionSpec: () -> AnimationSpec = { tween() },
    content: BoxScope.(T) -> Unit,
) {
    val displayedState = remember {
        mutableStateOf(targetState)
    }
    val hasPendingTransition = targetState != displayedState.value
    val outgoingState: T? = if (hasPendingTransition) displayedState.value else null
    val progress = animateFloatAsState(
        targetValue = if (hasPendingTransition) 1f else 0f,
        animationSpec = transitionSpec(),
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
            displayedState.value = targetState
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
                content(previous)
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

/**
 * Cross-fades state content using one fixed [animationSpec].
 *
 * This is a convenience wrapper around [AnimatedContent] and shares its layering, equality,
 * retargeting, nullable-state, and content-state contracts.
 *
 * @sample com.viewcompose.animation.samples.animatedContentSample
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
    AnimatedContent(
        targetState = targetState,
        modifier = modifier,
        transitionSpec = { animationSpec },
        content = content,
    )
}
