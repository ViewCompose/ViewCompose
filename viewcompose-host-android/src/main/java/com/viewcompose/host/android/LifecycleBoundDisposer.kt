package com.viewcompose.host.android

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner

internal class LifecycleBoundDisposer(
    private val onLifecycleDestroyed: () -> Unit,
) {
    private var owner: LifecycleOwner? = null
    private var observer: DefaultLifecycleObserver? = null

    fun bind(
        lifecycleOwner: LifecycleOwner,
    ) {
        if (owner === lifecycleOwner) {
            return
        }
        clearObserver()
        if (lifecycleOwner.lifecycle.currentState == Lifecycle.State.DESTROYED) {
            onLifecycleDestroyed()
            return
        }
        val nextObserver = object : DefaultLifecycleObserver {
            override fun onDestroy(owner: LifecycleOwner) {
                if (this@LifecycleBoundDisposer.owner !== owner) {
                    return
                }
                clearObserver()
                onLifecycleDestroyed()
            }
        }
        owner = lifecycleOwner
        observer = nextObserver
        lifecycleOwner.lifecycle.addObserver(nextObserver)
    }

    fun clearObserver() {
        owner?.let { currentOwner ->
            observer?.let(currentOwner.lifecycle::removeObserver)
        }
        owner = null
        observer = null
    }
}
