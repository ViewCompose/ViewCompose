package com.viewcompose.lifecycle

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import com.viewcompose.runtime.State
import com.viewcompose.ui.foundation.CompositionEffectContext
import com.viewcompose.ui.foundation.DisposableEffect
import com.viewcompose.ui.foundation.DisposableEffectResult
import com.viewcompose.ui.foundation.produceState

private const val LifecycleStartEffectNoKeyMessage =
    "LifecycleStartEffect requires at least one key that identifies its paired work."
private const val LifecycleResumeEffectNoKeyMessage =
    "LifecycleResumeEffect requires at least one key that identifies its paired work."

/**
 * Builds the cleanup required when [LifecycleStartEffect] stops or leaves composition.
 *
 * @sample com.viewcompose.lifecycle.samples.lifecycleStartEffectSample
 */
class LifecycleStartStopEffectScope internal constructor() {
    /**
     * Returns the cleanup paired with the current started period.
     *
     * @param onStopOrDispose cleanup invoked at most once when the lifecycle falls below `STARTED`
     * or the effect leaves composition
     * @return runtime-owned terminal cleanup result
     */
    fun onStopOrDispose(onStopOrDispose: () -> Unit): LifecycleStopOrDisposeEffectResult =
        LifecycleStopOrDisposeEffectResult(onStopOrDispose)
}

/** Holds one cleanup operation produced by [LifecycleStartStopEffectScope.onStopOrDispose]. */
fun interface LifecycleStopOrDisposeEffectResult {
    /** Performs terminal cleanup for one entered started period when invoked by the runtime. */
    fun dispose()
}

/**
 * Builds the cleanup required when [LifecycleResumeEffect] pauses or leaves composition.
 *
 * @sample com.viewcompose.lifecycle.samples.lifecycleResumeEffectSample
 */
class LifecycleResumePauseEffectScope internal constructor() {
    /**
     * Returns the cleanup paired with the current resumed period.
     *
     * @param onPauseOrDispose cleanup invoked at most once when the lifecycle falls below `RESUMED`
     * or the effect leaves composition
     * @return runtime-owned terminal cleanup result
     */
    fun onPauseOrDispose(onPauseOrDispose: () -> Unit): LifecyclePauseOrDisposeEffectResult =
        LifecyclePauseOrDisposeEffectResult(onPauseOrDispose)
}

/** Holds one cleanup operation produced by [LifecycleResumePauseEffectScope.onPauseOrDispose]. */
fun interface LifecyclePauseOrDisposeEffectResult {
    /** Performs terminal cleanup for one entered resumed period when invoked by the runtime. */
    fun dispose()
}

/**
 * Rejects a start effect without an explicit composition identity.
 *
 * @param lifecycleOwner owner that would control the rejected effect
 * @param effects unused setup block; add at least one identity key
 */
@Deprecated(
    message = LifecycleStartEffectNoKeyMessage,
    level = DeprecationLevel.ERROR,
)
@Suppress("UNUSED_PARAMETER")
fun LifecycleStartEffect(
    lifecycleOwner: LifecycleOwner = currentLifecycleOwnerOrThrow(),
    effects: LifecycleStartStopEffectScope.() -> LifecycleStopOrDisposeEffectResult,
): Unit = error(LifecycleStartEffectNoKeyMessage)

/**
 * Runs paired synchronous work while [lifecycleOwner] is at least `STARTED`.
 *
 * Setup begins only after a successful composition commit and whenever the lifecycle enters
 * `STARTED`. Cleanup runs before a new key or owner setup, when the lifecycle stops or is destroyed,
 * or when the call leaves composition. Each successful setup must finish with
 * [LifecycleStartStopEffectScope.onStopOrDispose]. An aborted candidate does not replace the active
 * observer or setup.
 *
 * Callbacks run synchronously on the lifecycle dispatch thread and must not block it. A throwing
 * setup detaches that observer and is not retried until the effect identity changes. A throwing
 * cleanup is terminal and is not retried. Resolve composition locals while declaring the effect;
 * reading a missing provider from either callback fails with a named diagnostic.
 *
 * @sample com.viewcompose.lifecycle.samples.lifecycleStartEffectSample
 * @param key1 value compared by structural equality as part of composition effect identity
 * @param lifecycleOwner owner whose lifecycle controls the active period; defaults to the nearest
 * [LocalLifecycleOwner]
 * @param effects setup that returns mandatory cleanup for one started period
 */
fun LifecycleStartEffect(
    key1: Any?,
    lifecycleOwner: LifecycleOwner = currentLifecycleOwnerOrThrow(),
    effects: LifecycleStartStopEffectScope.() -> LifecycleStopOrDisposeEffectResult,
) {
    lifecycleThresholdEffect(
        keys = arrayOf(key1),
        lifecycle = lifecycleOwner.lifecycle,
        minActiveState = Lifecycle.State.STARTED,
        setup = {
            val result = LifecycleStartStopEffectScope().effects()
            DisposableEffectResult(result::dispose)
        },
    )
}

/**
 * Runs paired synchronous work while a lifecycle is at least `STARTED`, using non-empty keys.
 *
 * Prefer the one-key overload when possible. The lifecycle, rollback, threading, and failure
 * contract is identical to that overload.
 *
 * @sample com.viewcompose.lifecycle.samples.lifecycleStartEffectSample
 * @param keys non-empty values forming the composition effect identity
 * @param lifecycleOwner owner whose lifecycle controls the active period
 * @param effects setup that returns mandatory cleanup for one started period
 * @throws IllegalArgumentException when [keys] is empty
 */
fun LifecycleStartEffect(
    vararg keys: Any?,
    lifecycleOwner: LifecycleOwner = currentLifecycleOwnerOrThrow(),
    effects: LifecycleStartStopEffectScope.() -> LifecycleStopOrDisposeEffectResult,
) {
    require(keys.isNotEmpty()) { LifecycleStartEffectNoKeyMessage }
    lifecycleThresholdEffect(
        keys = keys,
        lifecycle = lifecycleOwner.lifecycle,
        minActiveState = Lifecycle.State.STARTED,
        setup = {
            val result = LifecycleStartStopEffectScope().effects()
            DisposableEffectResult(result::dispose)
        },
    )
}

/**
 * Rejects a resume effect without an explicit composition identity.
 *
 * @param lifecycleOwner owner that would control the rejected effect
 * @param effects unused setup block; add at least one identity key
 */
@Deprecated(
    message = LifecycleResumeEffectNoKeyMessage,
    level = DeprecationLevel.ERROR,
)
@Suppress("UNUSED_PARAMETER")
fun LifecycleResumeEffect(
    lifecycleOwner: LifecycleOwner = currentLifecycleOwnerOrThrow(),
    effects: LifecycleResumePauseEffectScope.() -> LifecyclePauseOrDisposeEffectResult,
): Unit = error(LifecycleResumeEffectNoKeyMessage)

/**
 * Runs paired synchronous work while [lifecycleOwner] is at least `RESUMED`.
 *
 * Setup begins only after composition commit and each resume. Cleanup runs on pause, destruction,
 * key or owner replacement, and composition exit. Each successful setup must finish with
 * [LifecycleResumePauseEffectScope.onPauseOrDispose]. Abort, callback threading, and failure
 * behavior match [LifecycleStartEffect].
 *
 * @sample com.viewcompose.lifecycle.samples.lifecycleResumeEffectSample
 * @param key1 value compared by structural equality as part of composition effect identity
 * @param lifecycleOwner owner whose lifecycle controls the active period
 * @param effects setup that returns mandatory cleanup for one resumed period
 */
fun LifecycleResumeEffect(
    key1: Any?,
    lifecycleOwner: LifecycleOwner = currentLifecycleOwnerOrThrow(),
    effects: LifecycleResumePauseEffectScope.() -> LifecyclePauseOrDisposeEffectResult,
) {
    lifecycleThresholdEffect(
        keys = arrayOf(key1),
        lifecycle = lifecycleOwner.lifecycle,
        minActiveState = Lifecycle.State.RESUMED,
        setup = {
            val result = LifecycleResumePauseEffectScope().effects()
            DisposableEffectResult(result::dispose)
        },
    )
}

/**
 * Runs paired synchronous work while a lifecycle is at least `RESUMED`, using non-empty keys.
 *
 * @sample com.viewcompose.lifecycle.samples.lifecycleResumeEffectSample
 * @param keys non-empty values forming the composition effect identity
 * @param lifecycleOwner owner whose lifecycle controls the active period
 * @param effects setup that returns mandatory cleanup for one resumed period
 * @throws IllegalArgumentException when [keys] is empty
 */
fun LifecycleResumeEffect(
    vararg keys: Any?,
    lifecycleOwner: LifecycleOwner = currentLifecycleOwnerOrThrow(),
    effects: LifecycleResumePauseEffectScope.() -> LifecyclePauseOrDisposeEffectResult,
) {
    require(keys.isNotEmpty()) { LifecycleResumeEffectNoKeyMessage }
    lifecycleThresholdEffect(
        keys = keys,
        lifecycle = lifecycleOwner.lifecycle,
        minActiveState = Lifecycle.State.RESUMED,
        setup = {
            val result = LifecycleResumePauseEffectScope().effects()
            DisposableEffectResult(result::dispose)
        },
    )
}

/**
 * Returns observable state synchronized with this lifecycle's current state.
 *
 * The initial value is read during composition. Observation starts after commit, immediately
 * reconciles any intervening lifecycle change, and is removed when the call leaves composition.
 * The returned state holder is stable at the positional call site.
 *
 * @sample com.viewcompose.lifecycle.samples.lifecycleCurrentStateSample
 * @receiver lifecycle observed for state transitions
 * @return stable composition-owned state containing the latest lifecycle state
 */
fun Lifecycle.currentStateAsState(): State<Lifecycle.State> {
    return produceState(
        initialValue = currentState,
        this,
    ) {
        val observer = LifecycleEventObserver { _, _ ->
            value = currentState
        }
        addObserver(observer)
        value = currentState
        awaitDispose {
            removeObserver(observer)
        }
    }
}

private fun lifecycleThresholdEffect(
    keys: Array<out Any?>,
    lifecycle: Lifecycle,
    minActiveState: Lifecycle.State,
    setup: () -> DisposableEffectResult,
) {
    val effectKeys = arrayOfNulls<Any?>(keys.size + 1)
    effectKeys[0] = lifecycle
    keys.copyInto(effectKeys, destinationOffset = 1)
    DisposableEffect(*effectKeys) {
        val observer = ThresholdEffectObserver(
            lifecycle = lifecycle,
            minActiveState = minActiveState,
            setup = setup,
        )
        observer.start()
        onDispose(observer::dispose)
    }
}

/** Serializes one threshold setup/cleanup pair against lifecycle dispatch and composition exit. */
private class ThresholdEffectObserver(
    private val lifecycle: Lifecycle,
    private val minActiveState: Lifecycle.State,
    private val setup: () -> DisposableEffectResult,
) : LifecycleEventObserver {
    private var entered = false
    private var disposed = false
    private var cleanup: DisposableEffectResult? = null

    fun start() {
        check(!disposed) { "Lifecycle effect observer is already disposed." }
        try {
            lifecycle.addObserver(this)
            reconcile()
        } catch (error: Throwable) {
            failAndDetach(error)
        }
    }

    override fun onStateChanged(source: LifecycleOwner, event: Lifecycle.Event) {
        try {
            reconcile()
        } catch (error: Throwable) {
            failAndDetach(error)
        }
    }

    fun dispose() {
        if (disposed) return
        disposed = true
        var failure: Throwable? = null
        try {
            lifecycle.removeObserver(this)
        } catch (error: Throwable) {
            failure = error
        }
        try {
            leave()
        } catch (error: Throwable) {
            val currentFailure = failure
            if (currentFailure == null) {
                failure = error
            } else {
                currentFailure.addSuppressed(error)
            }
        }
        failure?.let { throw it }
    }

    private fun reconcile() {
        if (disposed) return
        val active = lifecycle.currentState != Lifecycle.State.DESTROYED &&
            lifecycle.currentState.isAtLeast(minActiveState)
        when {
            active && !entered -> {
                entered = true
                cleanup = CompositionEffectContext.run(setup)
            }

            !active && entered -> leave()
        }
    }

    private fun leave() {
        if (!entered) return
        entered = false
        val current = cleanup
        cleanup = null
        current?.let { result ->
            CompositionEffectContext.run(result::dispose)
        }
    }

    private fun failAndDetach(error: Throwable): Nothing {
        if (!disposed) {
            disposed = true
            try {
                lifecycle.removeObserver(this)
            } catch (detachError: Throwable) {
                error.addSuppressed(detachError)
            }
            try {
                leave()
            } catch (cleanupError: Throwable) {
                error.addSuppressed(cleanupError)
            }
        }
        throw error
    }
}
