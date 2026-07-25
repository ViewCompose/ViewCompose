package com.viewcompose.widget.core

import com.viewcompose.runtime.composition.RememberObserver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

fun LaunchedEffect(
    vararg keys: Any?,
    block: suspend CoroutineScope.() -> Unit,
) {
    require(keys.isNotEmpty()) {
        "LaunchedEffect requires at least one key."
    }
    val parentContext = checkNotNull(ComposerContext.currentCoroutineContext()) {
        "LaunchedEffect requires an active composition."
    }
    remember(*keys) {
        LaunchedEffectObserver(
            parentContext = parentContext,
            block = block,
        )
    }
}

fun rememberCoroutineScope(
    getContext: () -> CoroutineContext = { EmptyCoroutineContext },
): CoroutineScope {
    val parentContext = checkNotNull(ComposerContext.currentCoroutineContext()) {
        "rememberCoroutineScope requires an active composition."
    }
    return remember {
        val overlayContext = getContext()
        require(overlayContext[Job] == null) {
            "rememberCoroutineScope context must not contain a Job."
        }
        RememberedCoroutineScope(
            parentContext = parentContext,
            overlayContext = overlayContext,
        )
    }
}

private class LaunchedEffectObserver(
    private val parentContext: CoroutineContext,
    private val block: suspend CoroutineScope.() -> Unit,
) : RememberObserver {
    private var job: Job? = null

    override fun onRemembered() {
        check(job == null) {
            "LaunchedEffect is already running."
        }
        job = CoroutineScope(parentContext).launch(block = block)
    }

    override fun onForgotten() {
        job?.cancel()
        job = null
    }

    override fun onAbandoned() {
        job?.cancel()
        job = null
    }
}

private class RememberedCoroutineScope(
    parentContext: CoroutineContext,
    overlayContext: CoroutineContext,
) : CoroutineScope, RememberObserver {
    private val job = SupervisorJob(parentContext[Job])

    override val coroutineContext: CoroutineContext =
        parentContext.minusKey(Job) + overlayContext + job

    override fun onRemembered() = Unit

    override fun onForgotten() {
        cancel()
    }

    override fun onAbandoned() {
        cancel()
    }
}
