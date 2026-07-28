package com.viewcompose.host.android

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner

/**
 * 将一次性释放回调绑定到可变 LifecycleOwner。
 * Binds a one-shot disposal callback to a mutable LifecycleOwner.
 *
 * Fragment view lifecycle 会在同一 Fragment 生命周期内多次变化，因此这里支持重新绑定和显式解绑。
 * Fragment view lifecycles can change multiple times within one Fragment, so this helper supports rebinding and explicit detach.
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
            // 已销毁的 owner 不能再保存 observer，直接触发释放。
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
        // 解绑 observer 但不调用释放，用于 host 主动替换或清理绑定。
        // Detach the observer without disposing, used when the host replaces or clears the binding.
        owner?.let { currentOwner ->
            observer?.let(currentOwner.lifecycle::removeObserver)
        }
        owner = null
        observer = null
    }
}
