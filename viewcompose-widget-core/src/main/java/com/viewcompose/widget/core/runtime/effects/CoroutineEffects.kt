package com.viewcompose.widget.core

import com.viewcompose.runtime.composition.RememberObserver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

/**
 * 在 composition commit 后启动协程；keys 变化或离开 composition 时取消旧协程。
 * Launches a coroutine after composition commit; old coroutines are cancelled when keys change or leave composition.
 */
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

/**
 * remember 一个跟随 composition 生命周期取消的 CoroutineScope。
 * Remembers a CoroutineScope that is cancelled with the composition lifecycle.
 */
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

/**
 * LaunchedEffect 的 RememberObserver 桥接，在 remember 生命周期中启动/取消 job。
 * RememberObserver bridge for LaunchedEffect that starts and cancels the job with remember lifecycle.
 */
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

/**
 * rememberCoroutineScope 返回的 scope，使用 SupervisorJob 隔离子任务失败。
 * Scope returned by rememberCoroutineScope, using SupervisorJob to isolate child task failures.
 */
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
