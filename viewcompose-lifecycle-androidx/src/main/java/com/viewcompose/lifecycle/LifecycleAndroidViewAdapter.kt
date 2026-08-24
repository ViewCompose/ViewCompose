package com.viewcompose.lifecycle

import android.view.View
import androidx.annotation.MainThread
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import com.viewcompose.host.android.AndroidViewAdapter
import com.viewcompose.host.android.AndroidViewCommitScope
import com.viewcompose.host.android.AndroidViewLifecycleMode
import com.viewcompose.host.android.AndroidViewResetReason
import com.viewcompose.host.android.AndroidViewResetScope
import com.viewcompose.ui.environment.UiEnvironmentValues

/**
 * Supplies one committed Android View lifecycle transition to a typed adapter.
 *
 * The immutable scope is valid only for the callback. [lifecycleOwner] is the nearest owner captured
 * in the adapter's committed state, including a navigation destination owner when one is installed.
 *
 * @param V exact Android View type receiving lifecycle work
 * @property view committed renderer-owned View
 * @property environment immutable environment from the latest successful adapter commit
 * @property lifecycleOwner owner whose transition is being delivered
 */
class AndroidViewLifecycleEventScope<V : View> internal constructor(
    val view: V,
    val environment: UiEnvironmentValues,
    val lifecycleOwner: LifecycleOwner,
)

/**
 * Serializes a renderer-owned Android View against a replaceable AndroidX [LifecycleOwner].
 *
 * Subclasses keep construction and replay-safe configuration in [create][AndroidViewAdapter.create]
 * and [update][AndroidViewAdapter.update]. [onViewCommit] and lifecycle observation begin only after
 * the Android View transaction commits. Initial attachment catches up through `ON_CREATE`,
 * `ON_START`, and `ON_RESUME` in Android order. Owner replacement and final cleanup first deliver
 * `ON_PAUSE`, `ON_STOP`, and `ON_DESTROY` as applicable, detach the old observer, and only then bind
 * the next owner, so active owners never overlap.
 *
 * [onViewReset] and [onViewRelease] are protected terminal hooks because this base class must detach
 * lifecycle work and any [bindAndroidViewSavedState] registration before adapter cleanup. A lifecycle
 * callback failure is terminal for that binding: remaining downward cleanup is attempted, the
 * observer is removed, and the error is rethrown. A later successful commit may bind again. Callbacks
 * run synchronously on the Android main thread and must not retain scopes, block lifecycle dispatch,
 * or issue application-owned lifecycle commands.
 *
 * @sample com.viewcompose.lifecycle.samples.lifecycleAndroidViewAdapterSample
 * @param V exact Android View type created and managed by this adapter
 * @param S committed adapter state containing the intended lifecycle owner
 */
abstract class LifecycleAndroidViewAdapter<V : View, S> : AndroidViewAdapter<V, S> {
    /** Reports adapter-owned lifecycle coordination to bounded diagnostics. */
    final override val lifecycleMode: AndroidViewLifecycleMode
        get() = AndroidViewLifecycleMode.AdapterManaged

    /** Returns the nearest lifecycle owner captured in [state]. */
    protected abstract fun lifecycleOwner(state: S): LifecycleOwner

    /**
     * Publishes non-lifecycle commit work before the new owner is attached or caught up.
     *
     * When the owner changes, the previous owner has already completed downward cleanup. For the
     * same owner, the existing observer remains installed and still owns the preceding committed
     * state until this callback returns successfully. Implementations keep their own work
     * failure-atomic; if this callback throws, the base class clears its lifecycle and saved-state
     * bindings before propagating the failure.
     */
    protected open fun onViewCommit(scope: AndroidViewCommitScope<V>, state: S) = Unit

    /** Handles one synthesized or observed concrete Android lifecycle [event]. */
    protected abstract fun onLifecycleEvent(
        scope: AndroidViewLifecycleEventScope<V>,
        state: S,
        event: Lifecycle.Event,
    )

    /** Performs adapter-specific reset after lifecycle observation has been cleared. */
    protected open fun onViewReset(
        scope: AndroidViewResetScope<V>,
        reason: AndroidViewResetReason,
    ) = Unit

    /** Performs adapter-specific permanent cleanup after lifecycle observation has been cleared. */
    protected open fun onViewRelease(view: V) = Unit

    /**
     * Replaces the committed callback state and serially binds its captured owner after commit.
     */
    @MainThread
    final override fun onCommit(scope: AndroidViewCommitScope<V>, state: S) {
        try {
            val owner = lifecycleOwner(state)
            val binding = AndroidViewLifecycleBindingStore.bindingFor(scope.view)
            binding.prepareOwner(owner)
            onViewCommit(scope, state)
            binding.commit(
                owner = owner,
                callback = { event ->
                    onLifecycleEvent(
                        scope = AndroidViewLifecycleEventScope(
                            view = scope.view,
                            environment = scope.environment,
                            lifecycleOwner = owner,
                        ),
                        state = state,
                        event = event,
                    )
                },
            )
        } catch (error: Throwable) {
            var failure: Throwable? = error
            failure = captureFailure(failure) {
                AndroidViewLifecycleBindingStore.remove(scope.view)?.dispose()
            }
            failure = captureFailure(failure) {
                scope.view.clearAndroidViewSavedStateBinding()
            }
            throw checkNotNull(failure)
        }
    }

    /**
     * Clears lifecycle and saved-state bindings before invoking the adapter-specific reset hook.
     */
    @MainThread
    final override fun onReset(
        scope: AndroidViewResetScope<V>,
        reason: AndroidViewResetReason,
    ) {
        runTerminalCallbacks(
            { AndroidViewLifecycleBindingStore.remove(scope.view)?.dispose() },
            { scope.view.clearAndroidViewSavedStateBinding() },
            { onViewReset(scope, reason) },
        )
    }

    /**
     * Clears lifecycle and saved-state bindings before invoking permanent adapter cleanup.
     */
    @MainThread
    final override fun onRelease(view: V) {
        runTerminalCallbacks(
            { AndroidViewLifecycleBindingStore.remove(view)?.dispose() },
            { view.clearAndroidViewSavedStateBinding() },
            { onViewRelease(view) },
        )
    }
}

private object AndroidViewLifecycleBindingStore {
    fun bindingFor(view: View): AndroidViewLifecycleBinding {
        val existing = view.getTag(R.id.viewcompose_android_view_lifecycle_binding)
        check(existing == null || existing is AndroidViewLifecycleBinding) {
            "Android View lifecycle binding tag is owned by an incompatible value."
        }
        return (existing as? AndroidViewLifecycleBinding)
            ?: AndroidViewLifecycleBinding().also { binding ->
                view.setTag(R.id.viewcompose_android_view_lifecycle_binding, binding)
            }
    }

    fun remove(view: View): AndroidViewLifecycleBinding? {
        val existing = view.getTag(R.id.viewcompose_android_view_lifecycle_binding)
        check(existing == null || existing is AndroidViewLifecycleBinding) {
            "Android View lifecycle binding tag is owned by an incompatible value."
        }
        view.setTag(R.id.viewcompose_android_view_lifecycle_binding, null)
        return existing as? AndroidViewLifecycleBinding
    }
}

/** Owns one replaceable observer and the View-side lifecycle state driven through it. */
internal class AndroidViewLifecycleBinding {
    private var owner: LifecycleOwner? = null
    private var observer: LifecycleEventObserver? = null
    private var callback: ((Lifecycle.Event) -> Unit)? = null
    private var stage = LifecycleStage.Initialized
    private var reconciling = false
    private var reconcileRequested = false

    fun prepareOwner(nextOwner: LifecycleOwner) {
        requireUsableOwner(nextOwner)
        if (owner !== null && owner !== nextOwner) {
            dispose()
        }
    }

    fun commit(
        owner: LifecycleOwner,
        callback: (Lifecycle.Event) -> Unit,
    ) {
        requireUsableOwner(owner)
        if (this.owner === owner) {
            this.callback = callback
            reconcile()
            return
        }
        check(this.owner == null) {
            "Android View lifecycle owner replacement must detach the previous owner first."
        }
        val nextObserver = LifecycleEventObserver { source, _ ->
            if (this.owner !== source) return@LifecycleEventObserver
            try {
                reconcile()
            } catch (error: Throwable) {
                failAndDetach(error)
            }
        }
        this.owner = owner
        this.observer = nextObserver
        this.callback = callback
        stage = LifecycleStage.Initialized
        try {
            owner.lifecycle.addObserver(nextObserver)
            reconcile()
        } catch (error: Throwable) {
            failAndDetach(error)
        }
    }

    fun dispose() {
        val currentOwner = owner ?: return
        val currentObserver = observer
        owner = null
        observer = null
        var failure: Throwable? = null
        if (currentObserver != null) {
            failure = captureFailure(failure) {
                currentOwner.lifecycle.removeObserver(currentObserver)
            }
        }
        failure = captureFailure(failure) {
            driveDownToDestroyed()
        }
        callback = null
        stage = LifecycleStage.Initialized
        failure?.let { throw it }
    }

    private fun reconcile() {
        if (reconciling) {
            reconcileRequested = true
            return
        }
        reconciling = true
        try {
            do {
                reconcileRequested = false
                val currentOwner = owner ?: return
                when (currentOwner.lifecycle.currentState) {
                    Lifecycle.State.DESTROYED -> {
                        dispose()
                        return
                    }

                    Lifecycle.State.INITIALIZED -> Unit
                    Lifecycle.State.CREATED -> driveTo(LifecycleStage.Created)
                    Lifecycle.State.STARTED -> driveTo(LifecycleStage.Started)
                    Lifecycle.State.RESUMED -> driveTo(LifecycleStage.Resumed)
                }
            } while (reconcileRequested || !matchesOwnerState())
        } finally {
            reconciling = false
        }
    }

    private fun matchesOwnerState(): Boolean {
        return when (owner?.lifecycle?.currentState) {
            null, Lifecycle.State.DESTROYED -> true
            Lifecycle.State.INITIALIZED -> stage == LifecycleStage.Initialized
            Lifecycle.State.CREATED -> stage == LifecycleStage.Created
            Lifecycle.State.STARTED -> stage == LifecycleStage.Started
            Lifecycle.State.RESUMED -> stage == LifecycleStage.Resumed
        }
    }

    private fun driveTo(target: LifecycleStage) {
        if (stage.rank < target.rank) {
            driveUpOneStep()
        } else if (stage.rank > target.rank) {
            driveDownOneStep()
        }
    }

    private fun driveUpOneStep() {
        val (nextStage, event) = when (stage) {
            LifecycleStage.Initialized -> LifecycleStage.Created to Lifecycle.Event.ON_CREATE
            LifecycleStage.Created -> LifecycleStage.Started to Lifecycle.Event.ON_START
            LifecycleStage.Started -> LifecycleStage.Resumed to Lifecycle.Event.ON_RESUME
            LifecycleStage.Resumed -> error("Lifecycle is already resumed.")
        }
        stage = nextStage
        callback?.invoke(event)
    }

    private fun driveDownOneStep() {
        val (nextStage, event) = when (stage) {
            LifecycleStage.Resumed -> LifecycleStage.Started to Lifecycle.Event.ON_PAUSE
            LifecycleStage.Started -> LifecycleStage.Created to Lifecycle.Event.ON_STOP
            LifecycleStage.Created -> LifecycleStage.Initialized to Lifecycle.Event.ON_DESTROY
            LifecycleStage.Initialized -> error("Lifecycle is already initialized.")
        }
        stage = nextStage
        callback?.invoke(event)
    }

    private fun driveDownToDestroyed() {
        var failure: Throwable? = null
        while (stage != LifecycleStage.Initialized) {
            failure = captureFailure(failure) {
                driveDownOneStep()
            }
        }
        failure?.let { throw it }
    }

    private fun failAndDetach(error: Throwable): Nothing {
        try {
            dispose()
        } catch (cleanupError: Throwable) {
            if (cleanupError !== error) {
                error.addSuppressed(cleanupError)
            }
        }
        throw error
    }

    private fun requireUsableOwner(owner: LifecycleOwner) {
        check(owner.lifecycle.currentState != Lifecycle.State.DESTROYED) {
            "Android View cannot bind to a destroyed LifecycleOwner."
        }
    }
}

private enum class LifecycleStage(
    val rank: Int,
) {
    Initialized(0),
    Created(1),
    Started(2),
    Resumed(3),
}

private fun runTerminalCallbacks(vararg callbacks: () -> Unit) {
    var failure: Throwable? = null
    callbacks.forEach { callback ->
        failure = captureFailure(failure, callback)
    }
    failure?.let { throw it }
}

internal inline fun captureFailure(
    current: Throwable?,
    block: () -> Unit,
): Throwable? {
    return try {
        block()
        current
    } catch (error: Throwable) {
        if (current == null) {
            error
        } else {
            if (error !== current) {
                current.addSuppressed(error)
            }
            current
        }
    }
}
