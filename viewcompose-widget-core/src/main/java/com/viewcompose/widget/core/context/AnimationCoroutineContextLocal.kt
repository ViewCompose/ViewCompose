package com.viewcompose.widget.core

import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

private val LocalAnimationCoroutineContextValue = uiLocalOf<CoroutineContext> { EmptyCoroutineContext }

/**
 * 当前动画使用的额外 coroutine context。
 * Additional coroutine context used by animations in the current composition.
 */
object LocalAnimationCoroutineContext {
    val current: CoroutineContext
        get() = UiLocals.current(LocalAnimationCoroutineContextValue)
}

/**
 * 在 content 范围内提供动画 coroutine context。
 * Provides an animation coroutine context within the content scope.
 */
fun UiTreeBuilder.ProvideAnimationCoroutineContext(
    context: CoroutineContext,
    content: UiTreeBuilder.() -> Unit,
) {
    ProvideLocal(
        local = LocalAnimationCoroutineContextValue,
        value = context,
        content = content,
    )
}
