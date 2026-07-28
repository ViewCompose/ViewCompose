package com.viewcompose.graphics.core

/**
 * 单键绘制缓存，用于缓存 `drawWithCache` 这类依赖显式 key 的构建结果。
 * Single-key draw cache for build results that depend on an explicit key, such as `drawWithCache`.
 *
 * 该缓存不做线程同步，调用方应在渲染线程或受控构建流程中使用。
 * This cache is not synchronized; callers should use it from the render thread or a controlled build flow.
 */
class DrawCache<T> {
    private var cachedKey: Any? = null
    private var cachedValue: T? = null

    /**
     * 清空当前缓存值，下一次读取会重新构建。
     * Clears the current cached value so the next read rebuilds it.
     */
    fun clear() {
        cachedKey = null
        cachedValue = null
    }

    /**
     * 当 key 未变化且已有缓存值时复用结果，否则调用 builder 重建。
     * Reuses the cached value when the key is unchanged; otherwise invokes builder and stores the result.
     */
    fun getOrBuild(
        key: Any?,
        builder: () -> T,
    ): T {
        val cached = cachedValue
        if (cached != null && cachedKey == key) {
            return cached
        }
        val rebuilt = builder()
        cachedKey = key
        cachedValue = rebuilt
        return rebuilt
    }
}
