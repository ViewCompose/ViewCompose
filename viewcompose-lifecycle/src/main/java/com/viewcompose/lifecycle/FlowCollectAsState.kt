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
 * 收集 StateFlow 并以当前 value 作为初始值暴露为 UIFramework State。
 * Collects a StateFlow and exposes it as UIFramework State using the current value as the initial value.
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
 * 收集 Flow 并用调用方提供的 initial 作为首帧状态。
 * Collects a Flow and uses the caller-provided initial value for the first frame.
 *
 * context 不允许携带 Job，避免破坏 produceState 自身的结构化生命周期。
 * The context must not carry a Job, so it cannot break produceState's structured lifecycle.
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
 * 在 Lifecycle 至少达到 minActiveState 时收集 StateFlow。
 * Collects a StateFlow while the Lifecycle is at least minActiveState.
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
 * 使用当前 LifecycleOwner 在活跃生命周期内收集 Flow。
 * Collects a Flow while the current LifecycleOwner is active.
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
 * 使用显式 Lifecycle 在活跃生命周期内收集 Flow。
 * Collects a Flow while an explicit Lifecycle is active.
 *
 * collect 会随 repeatOnLifecycle 自动取消和重启，避免后台生命周期继续消耗上游。
 * Collection is automatically cancelled and restarted by repeatOnLifecycle to avoid consuming upstream while inactive.
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
