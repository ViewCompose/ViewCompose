package com.viewcompose.runtime.composition

/**
 * Receives lifecycle callbacks when a remembered value enters or leaves committed composition.
 *
 * A candidate discarded before activation receives [onAbandoned] instead of [onRemembered].
 * Replacing or removing an active value invokes [onForgotten]. Callbacks execute synchronously on
 * the thread that commits, aborts, or disposes the owning [ComposerLite]. The runtime continues
 * cleanup after a callback failure and rethrows the first failure with later failures suppressed.
 *
 * A throwing [onRemembered] attempt does not activate the value. A later successful composition
 * commit retries that callback while already activated sibling values remain unchanged. If the
 * value leaves composition before one attempt completes, it receives [onAbandoned] instead of
 * [onForgotten]. Implementations that can throw must leave a failed attempt safe to invoke again;
 * the runtime does not call a cleanup callback between attempts.
 *
 * @sample com.viewcompose.runtime.samples.rememberObserverRetrySample
 */
interface RememberObserver {
    /**
     * Activates this value after it enters committed composition.
     *
     * A successful invocation occurs once. An invocation that throws can run again after a later
     * composition commit and must therefore be retry-safe.
     */
    fun onRemembered()

    /** Called after this remembered value leaves committed composition or its composer is disposed. */
    fun onForgotten()

    /** Called when this newly created value is discarded before it becomes active. */
    fun onAbandoned()
}
