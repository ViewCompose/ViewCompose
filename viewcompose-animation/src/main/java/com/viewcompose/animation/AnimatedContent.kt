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
 * 在旧状态和新状态内容之间执行交叉淡入淡出。
 * Cross-fades between content for the previous state and the target state.
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
        // 等离场内容几乎透明后再提交 displayedState，避免旧内容提前从树中移除。
        // Commit displayedState only after outgoing content is nearly transparent.
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
 * [AnimatedContent] 的单一动画规格便捷入口。
 * Convenience wrapper around [AnimatedContent] with one animation spec.
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
