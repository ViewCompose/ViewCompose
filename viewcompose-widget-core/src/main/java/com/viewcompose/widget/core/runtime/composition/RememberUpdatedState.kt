package com.viewcompose.widget.core

import com.viewcompose.runtime.State
import com.viewcompose.runtime.mutableStateOf

/**
 * Returns a stable State reference while writing the latest value on every composition.
 */
fun <T> rememberUpdatedState(
    newValue: T,
): State<T> {
    val state = remember {
        mutableStateOf(newValue)
    }
    // DisposableEffect starts in the commit phase after this composition snapshot closes, so effect callbacks observe this value.
    state.value = newValue
    return state
}
