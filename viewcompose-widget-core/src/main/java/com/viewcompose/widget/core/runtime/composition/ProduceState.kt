package com.viewcompose.widget.core

import com.viewcompose.runtime.State
import com.viewcompose.runtime.mutableStateOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.CoroutineContext

interface ProduceStateScope<T> : CoroutineScope {
    var value: T

    suspend fun awaitDispose(onDispose: () -> Unit): Nothing
}

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
