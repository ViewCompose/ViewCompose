package com.viewcompose.ui.node.spec

import com.viewcompose.ui.node.LazyListItem
import com.viewcompose.ui.node.policy.CollectionMotionPolicy
import com.viewcompose.ui.node.policy.CollectionReusePolicy
import com.viewcompose.ui.node.policy.LazyContentPadding
import com.viewcompose.ui.node.policy.LazyLayoutPrefetchPolicy
import com.viewcompose.ui.state.LazyListState

data class LazyColumnNodeProps(
    val contentPadding: LazyContentPadding,
    val spacing: Int,
    val items: List<LazyListItem>,
    val state: LazyListState? = null,
    val reverseLayout: Boolean = false,
    val userScrollEnabled: Boolean = true,
    val prefetchPolicy: LazyLayoutPrefetchPolicy = LazyLayoutPrefetchPolicy(),
    val reusePolicy: CollectionReusePolicy = CollectionReusePolicy(),
    val motionPolicy: CollectionMotionPolicy = CollectionMotionPolicy(),
    val focusFollowKeyboard: Boolean = false,
) : NodeSpec
