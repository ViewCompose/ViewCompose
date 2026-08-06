package com.viewcompose.ui.foundation

import com.viewcompose.runtime.composition.ComposerLite
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

/**
 * Thread-local composer context used while widget-core exposes DSL APIs.
 */
internal object ComposerContext {
    private val currentComposer = ThreadLocal<ComposerLite?>()
    private val currentCoroutineContext = ThreadLocal<CoroutineContext?>()

    /**
     * Installs composer and coroutineContext on the current thread and restores previous values afterward.
     */
    fun <T> withComposer(
        composer: ComposerLite,
        coroutineContext: CoroutineContext = EmptyCoroutineContext,
        block: () -> T,
    ): T {
        val previous = currentComposer.get()
        val previousCoroutineContext = currentCoroutineContext.get()
        currentComposer.set(composer)
        currentCoroutineContext.set(coroutineContext)
        return try {
            block()
        } finally {
            currentComposer.set(previous)
            currentCoroutineContext.set(previousCoroutineContext)
        }
    }

    /**
     * Returns the current composer, or null outside composition.
     */
    fun currentComposer(): ComposerLite? = currentComposer.get()

    /**
     * Gets the current composer and throws an API-specific error outside composition.
     */
    fun requireCurrentComposer(apiName: String): ComposerLite =
        checkNotNull(currentComposer()) {
            "$apiName must be called during an active ViewCompose composition."
        }

    /**
     * Returns the current composition coroutine context so effects and animations inherit host scheduling.
     */
    fun currentCoroutineContext(): CoroutineContext? = currentCoroutineContext.get()
}
