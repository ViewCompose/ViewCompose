package com.viewcompose.widget.core

import com.viewcompose.runtime.composition.ComposerLite
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

internal object ComposerContext {
    private val currentComposer = ThreadLocal<ComposerLite?>()
    private val currentCoroutineContext = ThreadLocal<CoroutineContext?>()

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

    fun currentComposer(): ComposerLite? = currentComposer.get()

    fun currentCoroutineContext(): CoroutineContext? = currentCoroutineContext.get()
}
