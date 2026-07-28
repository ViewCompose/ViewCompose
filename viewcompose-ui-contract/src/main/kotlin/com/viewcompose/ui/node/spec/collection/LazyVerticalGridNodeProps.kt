package com.viewcompose.ui.node.spec

import com.viewcompose.ui.node.LazyListItem
import com.viewcompose.ui.node.policy.CollectionMotionPolicy
import com.viewcompose.ui.node.policy.CollectionReusePolicy
import com.viewcompose.ui.node.policy.LazyContentPadding
import com.viewcompose.ui.node.policy.LazyLayoutPrefetchPolicy
import com.viewcompose.ui.state.LazyListState

/**
 * LazyVerticalGrid 节点的网格、item 和复用策略属性。
 * Grid, item, and reuse-policy properties for a LazyVerticalGrid node.
 */
data class LazyVerticalGridNodeProps(
    val spanCount: Int,
    val contentPadding: LazyContentPadding,
    val horizontalSpacing: Int,
    val verticalSpacing: Int,
    val items: List<LazyListItem>,
    val state: LazyListState?,
    val reverseLayout: Boolean = false,
    val userScrollEnabled: Boolean = true,
    val prefetchPolicy: LazyLayoutPrefetchPolicy = LazyLayoutPrefetchPolicy(),
    val reusePolicy: CollectionReusePolicy = CollectionReusePolicy(),
    val motionPolicy: CollectionMotionPolicy = CollectionMotionPolicy(),
    val focusFollowKeyboard: Boolean = false,
) : NodeSpec
