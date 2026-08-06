package com.viewcompose.android

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner

/** Fails when an Android host lifecycle can no longer accept new UI content. */
internal fun requireActiveHost(
    owner: LifecycleOwner,
    hostName: String,
) {
    check(owner.lifecycle.currentState != Lifecycle.State.DESTROYED) {
        "$hostName.setUiContent cannot be called after the host lifecycle is destroyed."
    }
}
