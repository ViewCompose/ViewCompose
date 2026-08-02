package com.viewcompose.widget.core

import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlin.coroutines.CoroutineContext

/**
 * RenderSession-level owner for the composition coroutine scope.
 *
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
     * Cancels all composition coroutines under this session.
     */
    fun cancel() {
        job.cancel()
    }
}
