package com.viewcompose.runtime.composition

/**
 * Receives lifecycle callbacks when a remembered value enters or leaves a committed composition.
 *
 * Values created by an unsuccessful composition receive [onAbandoned] instead of [onRemembered].
 */
interface RememberObserver {
    fun onRemembered()

    fun onForgotten()

    fun onAbandoned()
}
