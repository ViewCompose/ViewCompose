package com.viewcompose.renderer.view.lazy.session

/** Tracks RecyclerView holder binding, attachment, and recycling state. */
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
        val ownedHolders = boundHolders.toList()
        boundHolders.clear()
        attachedHolders.clear()
        var failure: Throwable? = null
        ownedHolders.forEach { holder ->
            try {
                onDispose(holder)
            } catch (disposeError: Throwable) {
                if (failure == null) failure = disposeError else failure.addSuppressed(disposeError)
            }
        }
        failure?.let { throw it }
    }

    fun forEachAttached(action: (T) -> Unit) {
        attachedHolders.toList().forEach(action)
    }

    fun isAttached(holder: T): Boolean = holder in attachedHolders
}
