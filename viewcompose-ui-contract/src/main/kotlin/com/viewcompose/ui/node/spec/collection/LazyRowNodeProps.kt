package com.viewcompose.ui.node.spec

import com.viewcompose.ui.node.LazyListItem
import com.viewcompose.ui.node.policy.CollectionMotionPolicy
import com.viewcompose.ui.node.policy.CollectionReusePolicy
import com.viewcompose.ui.node.policy.LazyContentPadding
import com.viewcompose.ui.node.policy.LazyLayoutPrefetchPolicy
import com.viewcompose.ui.state.LazyListState
import com.viewcompose.ui.unit.UiDp

/**
 * LazyRow 节点的 item、复用、内边距和滚动状态属性。
 * Item, reuse, padding, and scroll-state properties for a LazyRow node.
 */
data class LazyRowNodeProps(
    val contentPadding: LazyContentPadding,
    val spacing: UiDp,
    val items: List<LazyListItem>,
    val state: LazyListState? = null,
    val reverseLayout: Boolean = false,
    val userScrollEnabled: Boolean = true,
    val prefetchPolicy: LazyLayoutPrefetchPolicy = LazyLayoutPrefetchPolicy(),
    val reusePolicy: CollectionReusePolicy = CollectionReusePolicy(),
    val motionPolicy: CollectionMotionPolicy = CollectionMotionPolicy(),
) : NodeSpec
