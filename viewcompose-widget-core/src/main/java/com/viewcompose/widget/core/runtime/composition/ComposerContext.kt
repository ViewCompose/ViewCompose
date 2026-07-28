package com.viewcompose.widget.core

import com.viewcompose.runtime.composition.ComposerLite
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

/**
 * widget-core 暴露 DSL API 时使用的线程局部 composer 上下文。
 * Thread-local composer context used while widget-core exposes DSL APIs.
 */
internal object ComposerContext {
    private val currentComposer = ThreadLocal<ComposerLite?>()
    private val currentCoroutineContext = ThreadLocal<CoroutineContext?>()

    /**
     * 在当前线程安装 composer 和 coroutineContext，并在 block 结束后恢复旧值。
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
     * 返回当前 composer；不在 composition 中时为 null。
     * Returns the current composer, or null outside composition.
     */
    fun currentComposer(): ComposerLite? = currentComposer.get()

    /**
     * 获取当前 composer，不在 composition 中调用会抛出带 API 名称的错误。
     * Gets the current composer and throws an API-specific error outside composition.
     */
    fun requireCurrentComposer(apiName: String): ComposerLite =
        checkNotNull(currentComposer()) {
            "$apiName must be called during an active ViewCompose composition."
        }

    /**
     * 返回当前 composition 协程上下文，供 effects 和动画继承宿主调度能力。
     * Returns the current composition coroutine context so effects and animations inherit host scheduling.
     */
    fun currentCoroutineContext(): CoroutineContext? = currentCoroutineContext.get()
}
