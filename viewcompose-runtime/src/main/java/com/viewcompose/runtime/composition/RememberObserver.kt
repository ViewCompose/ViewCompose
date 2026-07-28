package com.viewcompose.runtime.composition

/**
 * remembered 值进入或离开已提交 composition 时接收生命周期回调。
 * Receives lifecycle callbacks when a remembered value enters or leaves a committed composition.
 *
 * 未成功提交的 composition 中创建的值会收到 [onAbandoned]，不会收到 [onRemembered]。
 * Values created by an unsuccessful composition receive [onAbandoned] instead of [onRemembered].
 */
interface RememberObserver {
    /**
     * 值已进入已提交 composition。
     * The value has entered a committed composition.
     */
    fun onRemembered()

    /**
     * 值已从已提交 composition 中移除。
     * The value has left a committed composition.
     */
    fun onForgotten()

    /**
     * 值在 composition 尝试失败或放弃时被清理。
     * The value is cleaned up after a failed or abandoned composition attempt.
     */
    fun onAbandoned()
}
