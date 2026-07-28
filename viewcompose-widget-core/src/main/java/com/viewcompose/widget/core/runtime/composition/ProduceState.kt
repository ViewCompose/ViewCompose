package com.viewcompose.widget.core

import com.viewcompose.runtime.State
import com.viewcompose.runtime.mutableStateOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.CoroutineContext

/**
 * produceState 协程体使用的可写 State scope。
 * Writable State scope used by a produceState coroutine body.
 */
interface ProduceStateScope<T> : CoroutineScope {
    var value: T

    /**
     * 挂起直到 producer 被取消，并在取消时执行清理。
     * Suspends until the producer is cancelled and runs cleanup on cancellation.
     */
    suspend fun awaitDispose(onDispose: () -> Unit): Nothing
}

/**
 * ProduceStateScope 的默认实现，把 value 写入底层 MutableState。
 * Default ProduceStateScope implementation that writes value into the backing MutableState.
 */
private class ProduceStateScopeImpl<T>(
    private val state: com.viewcompose.runtime.MutableState<T>,
    override val coroutineContext: CoroutineContext,
) : ProduceStateScope<T> {
    override var value: T
        get() = state.value
        set(value) {
            state.value = value
        }

    override suspend fun awaitDispose(onDispose: () -> Unit): Nothing {
        return suspendCancellableCoroutine { continuation ->
            continuation.invokeOnCancellation {
                onDispose()
            }
        }
    }
}

/**
 * 将协程生产的数据暴露为 State。
 * Exposes coroutine-produced data as State.
 */
fun <T> produceState(
    initialValue: T,
    vararg keys: Any?,
    producer: suspend ProduceStateScope<T>.() -> Unit,
): State<T> {
    val state = remember {
        mutableStateOf(initialValue)
    }
    val effectKeys = if (keys.isEmpty()) {
        arrayOf(ProduceStateUnitKey)
    } else {
        keys
    }
    LaunchedEffect(*effectKeys) {
        ProduceStateScopeImpl(
            state = state,
            coroutineContext = coroutineContext,
        ).producer()
    }
    return state
}

private object ProduceStateUnitKey
