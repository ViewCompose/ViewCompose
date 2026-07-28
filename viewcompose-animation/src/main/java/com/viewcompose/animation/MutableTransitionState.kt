package com.viewcompose.animation

import com.viewcompose.runtime.mutableStateOf

/**
 * 可由外部持有的 transition 状态，暴露当前值、目标值和 idle 状态。
 * Externally held transition state that exposes current value, target value, and idle status.
 */
class MutableTransitionState<S>(
    initialState: S,
) {
    private val currentStateHolder = mutableStateOf(initialState)
    private val targetStateHolder = mutableStateOf(initialState)
    private val idleHolder = mutableStateOf(true)

    var currentState: S
        get() = currentStateHolder.value
        internal set(value) {
            currentStateHolder.value = value
        }

    var targetState: S
        get() = targetStateHolder.value
        set(value) {
            targetStateHolder.value = value
        }

    var isIdle: Boolean
        get() = idleHolder.value
        internal set(value) {
            idleHolder.value = value
        }
}
