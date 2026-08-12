package com.viewcompose.lifecycle.samples

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import com.viewcompose.lifecycle.ProvideLifecycleOwner
import com.viewcompose.lifecycle.LifecycleResumeEffect
import com.viewcompose.lifecycle.LifecycleStartEffect
import com.viewcompose.lifecycle.collectAsState
import com.viewcompose.lifecycle.collectAsStateWithLifecycle
import com.viewcompose.lifecycle.currentStateAsState
import com.viewcompose.runtime.State
import com.viewcompose.ui.foundation.UiTreeBuilder
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/** Collects a hot state source for the lifetime of this composition call. */
fun UiTreeBuilder.collectStateFlowSample(source: StateFlow<String>): State<String> {
    return source.collectAsState()
}

/** Supplies the first-frame value required by a general flow. */
fun UiTreeBuilder.collectFlowSample(source: Flow<String>): State<String> {
    return source.collectAsState(initial = "Loading")
}

/** Uses an explicit owner when the current host is not the intended lifecycle boundary. */
fun UiTreeBuilder.collectWithLifecycleSample(
    source: StateFlow<String>,
    owner: LifecycleOwner,
): State<String> {
    return source.collectAsStateWithLifecycle(
        lifecycleOwner = owner,
        minActiveState = Lifecycle.State.STARTED,
    )
}

/** Uses a Lifecycle directly when no owner object is available. */
fun UiTreeBuilder.collectWithExplicitLifecycleSample(
    source: Flow<String>,
    lifecycle: Lifecycle,
): State<String> {
    return source.collectAsStateWithLifecycle(
        initial = "Loading",
        lifecycle = lifecycle,
        minActiveState = Lifecycle.State.RESUMED,
    )
}

/** Installs a nested lifecycle boundary used by the default-owner overload. */
fun UiTreeBuilder.provideLifecycleOwnerSample(
    source: StateFlow<String>,
    owner: LifecycleOwner,
): State<String> {
    lateinit var state: State<String>
    ProvideLifecycleOwner(owner) {
        state = source.collectAsStateWithLifecycle()
    }
    return state
}

/** Starts and stops a synchronous tracker with the supplied Android lifecycle. */
fun UiTreeBuilder.lifecycleStartEffectSample(
    owner: LifecycleOwner,
    trackerId: String,
    onStart: () -> Unit,
    onStop: () -> Unit,
) {
    LifecycleStartEffect(trackerId, lifecycleOwner = owner) {
        onStart()
        onStopOrDispose(onStop)
    }
}

/** Acquires and releases foreground-only work with resumed lifecycle state. */
fun UiTreeBuilder.lifecycleResumeEffectSample(
    owner: LifecycleOwner,
    requestId: String,
    onResume: () -> Unit,
    onPause: () -> Unit,
) {
    LifecycleResumeEffect(requestId, lifecycleOwner = owner) {
        onResume()
        onPauseOrDispose(onPause)
    }
}

/** Exposes the latest lifecycle state as ViewCompose observable state. */
fun UiTreeBuilder.lifecycleCurrentStateSample(
    owner: LifecycleOwner,
): State<Lifecycle.State> = owner.lifecycle.currentStateAsState()
