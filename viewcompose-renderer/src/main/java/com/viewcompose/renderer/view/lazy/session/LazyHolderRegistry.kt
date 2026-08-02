package com.viewcompose.renderer.view.lazy.session

/**
 * Tracks RecyclerView holder binding, attachment, and recycling state.
 * Tracks RecyclerView holder bound, attached, and recycled state.
 */
internal class LazyHolderRegistry<T : Any>(
    private val onDispose: (T) -> Unit,
) {
    private val boundHolders = LinkedHashSet<T>()
    private val attachedHolders = LinkedHashSet<T>()

    fun onBound(holder: T) {
        boundHolders += holder
    }

    fun onAttached(holder: T) {
        attachedHolders += holder
    }

    fun onDetached(holder: T) {
        attachedHolders -= holder
    }

    fun onRecycled(holder: T) {
        attachedHolders -= holder
        if (boundHolders.remove(holder)) {
            onDispose(holder)
        }
    }

    fun disposeAll() {
        boundHolders.toList().forEach { holder ->
            onDispose(holder)
        }
        boundHolders.clear()
        attachedHolders.clear()
    }

    fun forEachBound(action: (T) -> Unit) {
        boundHolders.toList().forEach(action)
    }
}
