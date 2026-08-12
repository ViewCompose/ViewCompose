package com.viewcompose.ui.foundation

import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.ThreadContextElement

/**
 * Marks integration-owned effect callbacks that run after their declaring provider stack returns.
 *
 * Standard ViewCompose effects install this marker automatically. An integration implementing a
 * new callback or coroutine effect uses [run] or adds [coroutineContext] so a composition-local
 * read fails with the Local's diagnostic name instead of silently selecting a default or an
 * unrelated provider active on the callback thread. This marker does not restore Locals;
 * integrations still capture resolved values during declaration. Nested markers are supported and
 * restored in `finally` blocks.
 *
 * Application code normally uses `SideEffect`, `DisposableEffect`, `LaunchedEffect`, or lifecycle
 * effects instead of calling this low-level integration API directly.
 *
 * @sample com.viewcompose.ui.foundation.samples.compositionEffectContextSample
 */
object CompositionEffectContext {
    private val depth = ThreadLocal<Int?>()

    /**
     * Returns an immutable coroutine context element that marks every resumed callback as an effect.
     *
     * Add this context to integration-owned coroutine scopes without replacing their dispatcher or
     * Job. The coroutine runtime installs and restores the marker across thread switches.
     *
     * @return reusable context containing only the effect-callback thread marker
     */
    val coroutineContext: CoroutineContext = EffectCallbackContextElement

    internal fun isActive(): Boolean = (depth.get() ?: 0) > 0

    /**
     * Runs [block] as an integration-owned synchronous effect callback.
     *
     * The marker is thread-local, nestable, and restored even when [block] throws. The original
     * result or exception is returned unchanged.
     *
     * @param T type returned by [block]
     * @param block synchronous callback whose uncaptured Local reads require diagnostics
     * @return the value returned by [block]
     */
    fun <T> run(block: () -> T): T {
        val previous = depth.get()
        depth.set((previous ?: 0) + 1)
        return try {
            block()
        } finally {
            restore(previous)
        }
    }

    private fun restore(previous: Int?) {
        if (previous == null) {
            depth.remove()
        } else {
            depth.set(previous)
        }
    }

    private object EffectCallbackContextElement :
        ThreadContextElement<Int?>,
        AbstractCoroutineContextElement(Key) {
        object Key : CoroutineContext.Key<EffectCallbackContextElement>

        override fun updateThreadContext(context: CoroutineContext): Int? {
            val previous = depth.get()
            depth.set((previous ?: 0) + 1)
            return previous
        }

        override fun restoreThreadContext(
            context: CoroutineContext,
            oldState: Int?,
        ) {
            restore(oldState)
        }
    }
}
