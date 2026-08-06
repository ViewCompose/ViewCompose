package com.viewcompose.ui.foundation

import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

private val LocalAnimationCoroutineContextValue = uiLocalOf<CoroutineContext> { EmptyCoroutineContext }

/** Exposes the additional coroutine context used by animations in the current composition. */
object LocalAnimationCoroutineContext {
    /** Current context, or [EmptyCoroutineContext] when no provider is active. */
    val current: CoroutineContext
        get() = UiLocals.current(LocalAnimationCoroutineContextValue)
}

/**
 * Provides [context] to animation APIs invoked while building [content].
 *
 * Nested providers restore the previous value after [content] returns, including after failure.
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
