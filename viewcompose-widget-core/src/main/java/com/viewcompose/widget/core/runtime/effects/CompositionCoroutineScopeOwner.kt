package com.viewcompose.widget.core

import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

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

private object RenderSessionCoroutineContextProvider {
    @Volatile
    var context: CoroutineContext = EmptyCoroutineContext
}

fun installRenderSessionCoroutineContext(context: CoroutineContext) {
    RenderSessionCoroutineContextProvider.context = context
}

internal fun renderSessionCoroutineContext(): CoroutineContext {
    return RenderSessionCoroutineContextProvider.context
}
