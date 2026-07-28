package com.viewcompose.widget.core

/**
 * 单帧 overlay 请求收集器。
 * Per-frame collector for overlay requests.
 */
internal class OverlayRequestStore {
    private val requests = mutableListOf<OverlayRequest>()

    /**
     * 开始新一帧渲染时清空上一帧请求。
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
 * overlay 请求的线程局部上下文。
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
 * 提交一条 overlay 请求；没有活跃 store 时请求会被忽略。
 * Submits one overlay request; the request is ignored when no store is active.
 */
internal fun submitOverlayRequest(request: OverlayRequest) {
    OverlayRequestContext.currentStore()?.register(request)
}
