package com.viewcompose.animation

import com.viewcompose.runtime.Snapshot
import com.viewcompose.runtime.mutableStateOf

/**
 * Exposes an externally controlled target and observable visibility-transition status.
 *
 * Callers write [targetState]. [AnimatedVisibility] mirrors its internal transition into
 * [currentState] and [isIdle] while this object is supplied to the state overload. The object does
 * not run an animation by itself and is not saveable automatically; remember or otherwise retain it
 * for the lifetime whose transition status must be observed. The consuming framework publishes its
 * current, target, and idle mirror in one snapshot transaction; a caller write to [targetState]
 * remains an independent request.
 *
 * State is backed by the ViewCompose snapshot system. UI-facing reads and writes should follow the
 * owning composition's thread policy.
 *
 * @sample com.viewcompose.animation.samples.mutableTransitionStateSample
 *
 * @param S logical endpoint state type
 * @param initialState current and target state exposed before a transition host consumes the object
 */
class MutableTransitionState<S>(
    initialState: S,
) {
    private val currentStateHolder = mutableStateOf(initialState)
    private val targetStateHolder = mutableStateOf(initialState)
    private val idleHolder = mutableStateOf(true)

    /**
     * Returns the last state committed by the consuming transition.
     *
     * Only the animation runtime updates this property; it can differ from [targetState] while an
     * enter or exit segment is running.
     */
    var currentState: S
        get() = currentStateHolder.value
        internal set(value) {
            currentStateHolder.value = value
        }

    /**
     * Gets or replaces the state requested from the consuming transition.
     *
     * A change is observable and causes the next composition to retarget the transition.
     */
    var targetState: S
        get() = targetStateHolder.value
        set(value) {
            targetStateHolder.value = value
        }

    /**
     * Returns `true` when the consuming transition has committed [targetState].
     *
     * A newly constructed instance is idle. Only the animation runtime updates this property.
     */
    var isIdle: Boolean
        get() = idleHolder.value
        internal set(value) {
            idleHolder.value = value
        }

    /** Publishes one framework-owned transition mirror without exposing a mixed committed tuple. */
    internal fun syncFromTransition(
        currentState: S,
        targetState: S,
        isIdle: Boolean,
    ) {
        Snapshot.withMutableSnapshot {
            currentStateHolder.value = currentState
            targetStateHolder.value = targetState
            idleHolder.value = isIdle
        }
    }
}
