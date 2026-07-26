package com.viewcompose.widget.core

import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlin.coroutines.CoroutineContext

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

    fun cancel() {
        job.cancel()
    }
}
