package com.viewcompose.widget.core

import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlin.coroutines.CoroutineContext

/**
 * RenderSession 级 composition 协程作用域所有者。
 * RenderSession-level owner for the composition coroutine scope.
 *
 * 它继承宿主 context，但替换 Job 为 SupervisorJob，避免一个子协程失败取消整棵 composition。
 * It inherits the host context but replaces Job with SupervisorJob so one child failure does not cancel the whole composition.
 */
internal class CompositionCoroutineScopeOwner(
    parentContext: CoroutineContext,
    onError: (Throwable) -> Unit,
) {
    private val job = SupervisorJob(parentContext[Job])
    private val exceptionHandler = parentContext[CoroutineExceptionHandler]
        ?: CoroutineExceptionHandler { _, error ->
            onError(error)
        }

    val coroutineContext: CoroutineContext =
        parentContext.minusKey(Job) + job + exceptionHandler

    /**
     * 取消该 session 下的所有 composition 协程。
     * Cancels all composition coroutines under this session.
     */
    fun cancel() {
        job.cancel()
    }
}
