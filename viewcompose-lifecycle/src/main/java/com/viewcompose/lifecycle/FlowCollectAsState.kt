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

fun <T> StateFlow<T>.collectAsState(
    context: CoroutineContext = EmptyCoroutineContext,
): State<T> {
    return (this as Flow<T>).collectAsState(
        initial = value,
        context = context,
    )
}

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
