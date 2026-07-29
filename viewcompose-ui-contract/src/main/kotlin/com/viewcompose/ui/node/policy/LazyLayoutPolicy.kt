package com.viewcompose.ui.node.policy
import com.viewcompose.ui.unit.UiDp

/**
 * Lazy 容器内容内边距的 renderer 中立模型。
 * Renderer-neutral model for lazy container content padding.
 */
data class LazyContentPadding(
    val start: UiDp = UiDp.Zero,
    val top: UiDp = UiDp.Zero,
    val end: UiDp = UiDp.Zero,
    val bottom: UiDp = UiDp.Zero,
) {
    init {
        require(start >= UiDp.Zero) { "Lazy content start padding must be non-negative." }
        require(top >= UiDp.Zero) { "Lazy content top padding must be non-negative." }
        require(end >= UiDp.Zero) { "Lazy content end padding must be non-negative." }
        require(bottom >= UiDp.Zero) { "Lazy content bottom padding must be non-negative." }
    }

    companion object {
        val None = LazyContentPadding()

        fun all(value: UiDp): LazyContentPadding {
            return LazyContentPadding(
                start = value,
                top = value,
                end = value,
                bottom = value,
            )
        }

        fun symmetric(
            horizontal: UiDp = UiDp.Zero,
            vertical: UiDp = UiDp.Zero,
        ): LazyContentPadding {
            return LazyContentPadding(
                start = horizontal,
                top = vertical,
                end = horizontal,
                bottom = vertical,
            )
        }
    }
}

data class LazyLayoutPrefetchPolicy(
    val initialPrefetchItemCount: Int = 2,
    val itemViewCacheSize: Int = 2,
) {
    init {
        require(initialPrefetchItemCount >= 0) {
            "initialPrefetchItemCount must be non-negative."
        }
        require(itemViewCacheSize >= 0) {
            "itemViewCacheSize must be non-negative."
        }
    }
}
