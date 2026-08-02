package com.viewcompose.runtime.composition

/**
 * Receives lifecycle callbacks when a remembered value enters or leaves committed composition.
 *
 * A value created by an aborted or failed attempt receives [onAbandoned] instead of
 * [onRemembered]. Replacing or removing a committed value invokes [onForgotten]. Callbacks execute
 * synchronously on the thread that commits, aborts, or disposes the owning [ComposerLite]. The
 * runtime continues cleanup after a callback failure and rethrows the first failure with later
 * failures suppressed.
 */
interface RememberObserver {
    /** Called once after this value first enters a successfully committed composition. */
    fun onRemembered()

    /** Called after this remembered value leaves committed composition or its composer is disposed. */
    fun onForgotten()

    /** Called when this newly created value is discarded before it becomes committed. */
    fun onAbandoned()
}
