package com.viewcompose.ui.foundation

import com.viewcompose.runtime.State

/**
 * Returns a stable state holder that publishes [newValue] only when the candidate frame commits.
 *
 * The current composition sees its candidate value immediately. Previously committed effects keep
 * reading the committed value until commit, and abort discards the candidate. Publication happens
 * before outgoing and incoming effect lifecycle callbacks, allowing a long-lived effect to use the
 * latest callback without restarting for callback identity changes.
 *
 * @sample com.viewcompose.ui.foundation.samples.rememberUpdatedStateSample
 * @param T type of value retained by the holder
 * @param newValue candidate value for this composition attempt
 * @return the stable positional state holder
 */
fun <T> rememberUpdatedState(
    newValue: T,
): State<T> = ComposerContext
    .requireCurrentComposer("rememberUpdatedState")
    .rememberUpdatedState(newValue)
