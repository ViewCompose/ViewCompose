package com.viewcompose.ui.foundation

import com.viewcompose.runtime.State
import com.viewcompose.runtime.mutableStateOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.CoroutineContext

/** Writable [State] scope and coroutine lifetime exposed to a [produceState] producer. */
interface ProduceStateScope<T> : CoroutineScope {
    /**
     * Current produced value.
     *
     * Writes update the returned state and invalidate compositions that observed the previous value.
     */
    var value: T

    /**
     * Suspends until the producer is cancelled and then invokes [onDispose].
     *
     * Use this at the end of a producer that registered a callback or acquired a resource. The
     * cleanup runs at most once when keys change or the owning composition leaves the tree.
     */
    suspend fun awaitDispose(onDispose: () -> Unit): Nothing
}

/**
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
 * Converts values produced by a composition-scoped coroutine into observable [State].
 *
 * The producer launches after composition commit. Changing any [keys] cancels the previous
 * producer and starts a new one while preserving the same state holder. Leaving the composition
 * cancels the producer. Without keys, the producer runs once for the remembered call site.
 *
 * @sample com.viewcompose.ui.foundation.samples.produceStateSample
 * @param initialValue value exposed before the producer publishes its first result
 * @param keys values that define the producer's restart identity
 * @param producer coroutine that updates [ProduceStateScope.value]
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
