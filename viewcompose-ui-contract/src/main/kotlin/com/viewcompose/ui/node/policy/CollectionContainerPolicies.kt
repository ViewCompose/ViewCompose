package com.viewcompose.ui.node.policy

/**
 * Controls whether compatible collection containers may reuse the same renderer-owned view pool.
 *
 * Sharing is an optimization boundary only. Renderers must still fully rebind every reused item and
 * must not use the shared pool as application state. [mountedTreeCacheSize] applies to each
 * collection's framework-owned cache; `0` disables cross-key mounted-tree retention. A retained
 * tree is reset and rebound only for the same `contentType`, and deterministic eviction releases
 * its native resources.
 *
 * @sample com.viewcompose.ui.samples.collectionPolicySample
 * @property sharePool whether the renderer may share empty holder shells across compatible containers
 * @property mountedTreeCacheSize maximum reset native trees retained by each collection
 * @throws IllegalArgumentException when [mountedTreeCacheSize] is negative
 */
data class CollectionReusePolicy(
    val sharePool: Boolean = false,
    val mountedTreeCacheSize: Int = 2,
) {
    init {
        require(mountedTreeCacheSize >= 0) { "mountedTreeCacheSize must be non-negative." }
    }
}

/**
 * Selects which native collection item transitions a renderer may run.
 *
 * [disableItemAnimator] is the authoritative master switch. When it is `true`, the individual
 * operation flags are retained in the model but must not re-enable native item animation.
 *
 * @property disableItemAnimator whether all native item animations are disabled
 * @property animateInsert whether newly inserted items may animate when the animator is enabled
 * @property animateRemove whether removed items may animate when the animator is enabled
 * @property animateMove whether reordered items may animate when the animator is enabled
 * @property animateChange whether content changes may animate when the animator is enabled
 */
data class CollectionMotionPolicy(
    val disableItemAnimator: Boolean = false,
    val animateInsert: Boolean = true,
    val animateRemove: Boolean = true,
    val animateMove: Boolean = true,
    val animateChange: Boolean = true,
)
