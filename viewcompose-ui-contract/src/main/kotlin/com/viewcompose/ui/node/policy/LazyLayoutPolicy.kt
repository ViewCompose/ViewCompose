package com.viewcompose.ui.node.policy

data class LazyContentPadding(
    val start: Int = 0,
    val top: Int = 0,
    val end: Int = 0,
    val bottom: Int = 0,
) {
    init {
        require(start >= 0) { "Lazy content start padding must be non-negative." }
        require(top >= 0) { "Lazy content top padding must be non-negative." }
        require(end >= 0) { "Lazy content end padding must be non-negative." }
        require(bottom >= 0) { "Lazy content bottom padding must be non-negative." }
    }

    companion object {
        val None = LazyContentPadding()

        fun all(value: Int): LazyContentPadding {
            return LazyContentPadding(
                start = value,
                top = value,
                end = value,
                bottom = value,
            )
        }

        fun symmetric(
            horizontal: Int = 0,
            vertical: Int = 0,
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
