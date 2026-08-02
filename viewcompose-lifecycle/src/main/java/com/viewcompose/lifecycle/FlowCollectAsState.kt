package com.viewcompose.lifecycle

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.viewcompose.runtime.State
import com.viewcompose.widget.core.produceState
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext

/**
 * Collects this [StateFlow] into composition-scoped ViewCompose [State].
 *
 * The returned state exposes [StateFlow.value] synchronously for the first frame. Collection starts
 * only after the composition commits, continues regardless of Android lifecycle state, and is
 * cancelled when the call leaves composition. Changing the flow or [context] restarts the collector
 * while preserving the remembered state holder and its last value.
 *
 * [context] may select a dispatcher or add contextual elements, but must not contain a [Job]; the
 * owning composition controls cancellation.
 *
 * @sample com.viewcompose.lifecycle.samples.collectStateFlowSample
 * @param context additional coroutine context used while collecting
 * @return remembered observable state updated by each flow emission
 * @throws IllegalArgumentException if [context] contains a [Job]
 */
fun <T> StateFlow<T>.collectAsState(
    context: CoroutineContext = EmptyCoroutineContext,
): State<T> {
    return (this as Flow<T>).collectAsState(
        initial = value,
        context = context,
    )
}

/**
 * Collects this [Flow] into composition-scoped ViewCompose [State].
 *
 * [initial] is exposed until the producer, launched after a successful composition commit, receives
 * its first value. Collection is not gated by Android lifecycle state. Leaving composition cancels
 * the collector; changing the flow or [context] restarts it without replacing the remembered state
 * holder.
 *
 * [context] may select a dispatcher or add contextual elements, but must not contain a [Job]; the
 * owning composition controls cancellation.
 *
 * @sample com.viewcompose.lifecycle.samples.collectFlowSample
 * @param initial value visible before the first collected emission
 * @param context additional coroutine context used while collecting
 * @return remembered observable state updated by each flow emission
 * @throws IllegalArgumentException if [context] contains a [Job]
 */
fun <T> Flow<T>.collectAsState(
    initial: T,
    context: CoroutineContext = EmptyCoroutineContext,
): State<T> {
    requireStructuredContext(context)
    return produceState(
        initialValue = initial,
        this,
        context,
    ) {
        withContext(context) {
            this@collectAsState.collect { next ->
                value = next
            }
        }
    }
}

/**
 * Collects this [StateFlow] while [lifecycleOwner] is at least [minActiveState].
 *
 * The current [StateFlow.value] is visible synchronously. Collection starts after composition commit,
 * stops below the threshold, and restarts serially when the lifecycle becomes active again. The
 * returned state retains the last collected value while inactive. Leaving composition or reaching
 * [Lifecycle.State.DESTROYED] cancels collection.
 *
 * When omitted, [lifecycleOwner] is resolved from [LocalLifecycleOwner]. [context] must not contain a
 * [Job] because the composition and `repeatOnLifecycle` own cancellation.
 *
 * @sample com.viewcompose.lifecycle.samples.collectWithLifecycleSample
 * @param lifecycleOwner owner whose lifecycle gates upstream collection
 * @param minActiveState minimum active state; must be `CREATED`, `STARTED`, or `RESUMED`
 * @param context additional coroutine context used while collecting
 * @return remembered observable state initialized from the current flow value
 * @throws IllegalArgumentException if no owner is provided, the active state is unsupported, or
 * [context] contains a [Job]
 */
fun <T> StateFlow<T>.collectAsStateWithLifecycle(
    lifecycleOwner: LifecycleOwner = currentLifecycleOwnerOrThrow(),
    minActiveState: Lifecycle.State = Lifecycle.State.STARTED,
    context: CoroutineContext = EmptyCoroutineContext,
): State<T> {
    return (this as Flow<T>).collectAsStateWithLifecycle(
        initial = value,
        lifecycle = lifecycleOwner.lifecycle,
        minActiveState = minActiveState,
        context = context,
    )
}

/**
 * Collects this [Flow] while [lifecycleOwner] is at least [minActiveState].
 *
 * [initial] remains visible until the first active collection emits. Collection is cancelled below
 * the threshold and restarted serially when the owner becomes active, retaining the most recently
 * collected value between active periods. The default owner comes from [LocalLifecycleOwner].
 *
 * @sample com.viewcompose.lifecycle.samples.collectWithLifecycleSample
 * @param initial value visible before the first active emission
 * @param lifecycleOwner owner whose lifecycle gates upstream collection
 * @param minActiveState minimum active state; must be `CREATED`, `STARTED`, or `RESUMED`
 * @param context additional coroutine context used while collecting; it must not contain a [Job]
 * @return remembered observable state updated only by active collection periods
 * @throws IllegalArgumentException if no owner is provided, the active state is unsupported, or
 * [context] contains a [Job]
 */
fun <T> Flow<T>.collectAsStateWithLifecycle(
    initial: T,
    lifecycleOwner: LifecycleOwner = currentLifecycleOwnerOrThrow(),
    minActiveState: Lifecycle.State = Lifecycle.State.STARTED,
    context: CoroutineContext = EmptyCoroutineContext,
): State<T> {
    return collectAsStateWithLifecycle(
        initial = initial,
        lifecycle = lifecycleOwner.lifecycle,
        minActiveState = minActiveState,
        context = context,
    )
}

/**
 * Collects this [Flow] while [lifecycle] is at least [minActiveState].
 *
 * This overload is useful when no [LifecycleOwner] is available or a nested component intentionally
 * targets a different lifecycle. `repeatOnLifecycle` cancels collection below the threshold and
 * waits for its cleanup before restarting, so rapid stop/start transitions never overlap upstream
 * collectors. The remembered state retains its last value while inactive.
 *
 * The flow, lifecycle instance, threshold, and [context] form the producer restart identity. A new
 * value for any of them cancels the previous producer after composition commit. [context] must not
 * contain a [Job].
 *
 * @sample com.viewcompose.lifecycle.samples.collectWithExplicitLifecycleSample
 * @param initial value visible before the first active emission
 * @param lifecycle lifecycle that gates upstream collection
 * @param minActiveState minimum active state; must be `CREATED`, `STARTED`, or `RESUMED`
 * @param context additional coroutine context used while collecting
 * @return remembered observable state updated only by active collection periods
 * @throws IllegalArgumentException if [minActiveState] is `INITIALIZED` or `DESTROYED`, or if
 * [context] contains a [Job]
 */
fun <T> Flow<T>.collectAsStateWithLifecycle(
    initial: T,
    lifecycle: Lifecycle,
    minActiveState: Lifecycle.State = Lifecycle.State.STARTED,
    context: CoroutineContext = EmptyCoroutineContext,
): State<T> {
    require(
        minActiveState == Lifecycle.State.CREATED ||
            minActiveState == Lifecycle.State.STARTED ||
            minActiveState == Lifecycle.State.RESUMED,
    ) {
        "minActiveState must be CREATED, STARTED, or RESUMED."
    }
    requireStructuredContext(context)
    return produceState(
        initialValue = initial,
        this,
        lifecycle,
        minActiveState,
        context,
    ) {
        lifecycle.repeatOnLifecycle(minActiveState) {
            withContext(context) {
                this@collectAsStateWithLifecycle.collect { next ->
                    value = next
                }
            }
        }
    }
}

private fun requireStructuredContext(context: CoroutineContext) {
    require(context[Job] == null) {
        "collectAsState context must not contain a Job."
    }
}

private fun currentLifecycleOwnerOrThrow(): LifecycleOwner {
    return requireNotNull(LocalLifecycleOwner.current) {
        "No LifecycleOwner found. Use ComponentActivity/Fragment.setUiContent " +
            "or wrap with ProvideLifecycleOwner."
    }
}
