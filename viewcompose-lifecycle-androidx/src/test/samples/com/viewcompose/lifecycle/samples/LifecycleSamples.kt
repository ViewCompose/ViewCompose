package com.viewcompose.lifecycle.samples

import android.os.Bundle
import android.widget.TextView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.savedstate.SavedStateRegistryOwner
import com.viewcompose.host.android.AndroidView
import com.viewcompose.host.android.AndroidViewCommitScope
import com.viewcompose.host.android.AndroidViewCreateScope
import com.viewcompose.host.android.AndroidViewUpdateScope
import com.viewcompose.lifecycle.AndroidViewLifecycleEventScope
import com.viewcompose.lifecycle.AndroidViewSavedStateBindResult
import com.viewcompose.lifecycle.LifecycleAndroidViewAdapter
import com.viewcompose.lifecycle.ProvideLifecycleOwner
import com.viewcompose.lifecycle.ProvideSavedStateRegistryOwner
import com.viewcompose.lifecycle.bindAndroidViewSavedState
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
// DOCS_REGION_START(lifecycle-flow-composition)
fun UiTreeBuilder.collectStateFlowSample(source: StateFlow<String>): State<String> {
    return source.collectAsState()
}

/** Supplies the first-frame value required by a general flow. */
fun UiTreeBuilder.collectFlowSample(source: Flow<String>): State<String> {
    return source.collectAsState(initial = "Loading")
}
// DOCS_REGION_END(lifecycle-flow-composition)

/** Uses an explicit owner when the current host is not the intended lifecycle boundary. */
// DOCS_REGION_START(lifecycle-flow-aware)
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
// DOCS_REGION_END(lifecycle-flow-aware)

/** Installs a nested lifecycle boundary used by the default-owner overload. */
// DOCS_REGION_START(lifecycle-owner-boundary)
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
// DOCS_REGION_END(lifecycle-owner-boundary)

/** Starts and stops a synchronous tracker with the supplied Android lifecycle. */
// DOCS_REGION_START(lifecycle-effects)
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
// DOCS_REGION_END(lifecycle-effects)

/** Installs the saved-state owner used by SDK-specific committed Android View state. */
fun UiTreeBuilder.provideSavedStateRegistryOwnerSample(
    owner: SavedStateRegistryOwner,
    content: UiTreeBuilder.() -> Unit,
) {
    ProvideSavedStateRegistryOwner(owner, content)
}

// DOCS_REGION_START(lifecycle-android-view)
private data class LifecycleLabelState(
    val owner: LifecycleOwner,
    val text: String,
)

private object LifecycleLabelAdapter : LifecycleAndroidViewAdapter<TextView, LifecycleLabelState>() {
    override fun lifecycleOwner(state: LifecycleLabelState): LifecycleOwner = state.owner

    override fun create(scope: AndroidViewCreateScope): TextView = TextView(scope.context)

    override fun update(scope: AndroidViewUpdateScope<TextView>, state: LifecycleLabelState) {
        scope.view.text = state.text
    }

    override fun onLifecycleEvent(
        scope: AndroidViewLifecycleEventScope<TextView>,
        state: LifecycleLabelState,
        event: Lifecycle.Event,
    ) {
        when (event) {
            Lifecycle.Event.ON_START -> scope.view.isActivated = true
            Lifecycle.Event.ON_STOP,
            Lifecycle.Event.ON_DESTROY,
            -> scope.view.isActivated = false

            else -> Unit
        }
    }
}

/** Mounts a View whose AndroidX owner is caught up only after the View transaction commits. */
fun UiTreeBuilder.lifecycleAndroidViewAdapterSample(
    owner: LifecycleOwner,
    text: String,
) {
    AndroidView(
        adapter = LifecycleLabelAdapter,
        state = LifecycleLabelState(owner = owner, text = text),
        key = "lifecycle-label",
    )
}
// DOCS_REGION_END(lifecycle-android-view)

// DOCS_REGION_START(lifecycle-android-view-saved-state)
private data class SavedLabelState(
    val lifecycleOwner: LifecycleOwner,
    val savedStateOwner: SavedStateRegistryOwner,
    val text: String,
)

private object SavedLabelAdapter : LifecycleAndroidViewAdapter<TextView, SavedLabelState>() {
    override fun lifecycleOwner(state: SavedLabelState): LifecycleOwner = state.lifecycleOwner

    override fun create(scope: AndroidViewCreateScope): TextView = TextView(scope.context)

    override fun update(scope: AndroidViewUpdateScope<TextView>, state: SavedLabelState) {
        scope.view.text = state.text
    }

    override fun onViewCommit(scope: AndroidViewCommitScope<TextView>, state: SavedLabelState) {
        val result = scope.bindAndroidViewSavedState(
            owner = state.savedStateOwner,
            key = "saved-label",
            formatVersion = 1,
        ) {
            Bundle().apply { putString("text", view.text.toString()) }
        }
        if (result is AndroidViewSavedStateBindResult.Initial) {
            result.restoredState?.getString("text")?.let(scope.view::setText)
        }
    }

    override fun onLifecycleEvent(
        scope: AndroidViewLifecycleEventScope<TextView>,
        state: SavedLabelState,
        event: Lifecycle.Event,
    ) = Unit
}

/** Registers SDK Bundle state from commit and restores it before lifecycle catch-up. */
fun UiTreeBuilder.androidViewSavedStateBindingSample(
    lifecycleOwner: LifecycleOwner,
    savedStateOwner: SavedStateRegistryOwner,
    text: String,
) {
    AndroidView(
        adapter = SavedLabelAdapter,
        state = SavedLabelState(
            lifecycleOwner = lifecycleOwner,
            savedStateOwner = savedStateOwner,
            text = text,
        ),
        key = "saved-label",
    )
}
// DOCS_REGION_END(lifecycle-android-view-saved-state)
