package com.viewcompose.ui.foundation

/**
 * Per-frame collector for overlay requests.
 */
internal class OverlayRequestStore {
    private val requests = mutableListOf<OverlayRequest>()

    /**
     * Clears previous-frame requests when a new frame begins.
     */
    fun beginRender() {
        requests.clear()
    }

    fun register(request: OverlayRequest) {
        requests += request
    }

    fun currentRequests(): List<OverlayRequest> = requests.toList()
}

/**
 * Thread-local context for overlay requests.
 */
internal object OverlayRequestContext {
    private val currentStore = ThreadLocal<OverlayRequestStore?>()

    fun <T> withStore(
        store: OverlayRequestStore,
        block: () -> T,
    ): T {
        val previous = currentStore.get()
        store.beginRender()
        currentStore.set(store)
        return try {
            block()
        } finally {
            currentStore.set(previous)
        }
    }

    fun currentStore(): OverlayRequestStore? = currentStore.get()
}

/**
 * Submits one overlay request; the request is ignored when no store is active.
 */
internal fun submitOverlayRequest(request: OverlayRequest) {
    OverlayRequestContext.currentStore()?.register(request)
}
