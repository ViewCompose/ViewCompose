package com.viewcompose.android

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner

/**
 * Binds a one-shot disposal callback to a replaceable lifecycle owner.
 *
 * Fragment View lifecycles can change repeatedly, so rebinding detaches the previous observer and
 * explicit clearing detaches without invoking the disposal callback.
 */
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
            // A destroyed owner cannot retain an observer, so dispose immediately.
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
        // Detach without disposing when the host replaces or explicitly clears the binding.
        owner?.let { currentOwner ->
            observer?.let(currentOwner.lifecycle::removeObserver)
        }
        owner = null
        observer = null
    }
}
