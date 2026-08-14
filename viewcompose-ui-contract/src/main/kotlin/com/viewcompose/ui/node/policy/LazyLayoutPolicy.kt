package com.viewcompose.ui.node.policy

import com.viewcompose.ui.unit.UiDp

/**
 * Defines non-negative logical content padding for a lazy container.
 *
 * Start and end are resolved by the renderer against the node's layout direction.
 *
 * @property start padding at the logical start edge
 * @property top padding at the physical top edge
 * @property end padding at the logical end edge
 * @property bottom padding at the physical bottom edge
 * @throws IllegalArgumentException if any edge is negative
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

    /** Creates commonly used immutable padding values. */
    companion object {
        /** Padding with every edge set to [UiDp.Zero]. */
        val None = LazyContentPadding()

        /**
         * Creates padding with [value] on every edge.
         *
         * @throws IllegalArgumentException if [value] is negative
         */
        fun all(value: UiDp): LazyContentPadding {
            return LazyContentPadding(
                start = value,
                top = value,
                end = value,
                bottom = value,
            )
        }

        /**
         * Creates padding with equal horizontal and equal vertical edges.
         *
         * @param horizontal value for start and end
         * @param vertical value for top and bottom
         * @throws IllegalArgumentException if either value is negative
         */
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

/**
 * Configures eager preparation and native view caching for a lazy layout.
 *
 * These values are performance hints. A renderer may clamp or ignore them when its platform does
 * not expose an equivalent capability; they must not affect semantic item content. The nested
 * prefetch count does not authorize unbounded synchronous preparation: a renderer may stage an
 * unknown or previously expensive type until attachment.
 *
 * @sample com.viewcompose.ui.samples.collectionPolicySample
 * @property nestedInitialPrefetchItemCount initial count used only when this list is nested
 * @property itemViewCacheSize number of detached item views retained by the native container
 * @throws IllegalArgumentException if either value is negative
 */
data class LazyLayoutPrefetchPolicy(
    val nestedInitialPrefetchItemCount: Int = 2,
    val itemViewCacheSize: Int = 2,
) {
    init {
        require(nestedInitialPrefetchItemCount >= 0) {
            "nestedInitialPrefetchItemCount must be non-negative."
        }
        require(itemViewCacheSize >= 0) {
            "itemViewCacheSize must be non-negative."
        }
    }
}
