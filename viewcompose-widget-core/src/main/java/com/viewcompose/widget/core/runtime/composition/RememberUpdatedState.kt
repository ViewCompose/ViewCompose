package com.viewcompose.widget.core

import com.viewcompose.runtime.State
import com.viewcompose.runtime.mutableStateOf

fun <T> rememberUpdatedState(
    newValue: T,
): State<T> {
    val state = remember {
        mutableStateOf(newValue)
    }
    // DisposableEffect starts in the commit phase, after this composition snapshot has closed,
    // so effect callbacks observe the value written here.
    state.value = newValue
    return state
}
