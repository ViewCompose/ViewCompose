package com.viewcompose.ui.node.policy

/**
 * 集合容器复用策略，控制离屏 item 的保留规模。
 * Collection container reuse policy controlling how many off-screen items are retained.
 */
data class CollectionReusePolicy(
    val sharePool: Boolean = false,
)

data class CollectionMotionPolicy(
    val disableItemAnimator: Boolean = false,
    val animateInsert: Boolean = true,
    val animateRemove: Boolean = true,
    val animateMove: Boolean = true,
    val animateChange: Boolean = true,
)
