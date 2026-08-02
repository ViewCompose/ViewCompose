package com.viewcompose.ui.node.spec

import com.viewcompose.ui.node.LazyListItem
import com.viewcompose.ui.node.policy.CollectionMotionPolicy
import com.viewcompose.ui.node.policy.CollectionReusePolicy
import com.viewcompose.ui.node.policy.LazyContentPadding
import com.viewcompose.ui.node.policy.LazyLayoutPrefetchPolicy
import com.viewcompose.ui.state.LazyListState
import com.viewcompose.ui.unit.UiDp

/**
 * Immutable renderer properties for a vertically scrolling lazy list.
 *
 * @property contentPadding logical padding inside the scrollable content
 * @property spacing main-axis spacing between adjacent items
 * @property items ordered keyed item models
 * @property state optional command and observation state attached to the native list
 * @property reverseLayout whether item placement and scroll direction start from the opposite edge
 * @property userScrollEnabled whether direct user scrolling is accepted
 * @property prefetchPolicy eager preparation and native view-cache hints
 * @property reusePolicy native item-view pool policy
 * @property motionPolicy native item mutation animation policy
 * @property focusFollowKeyboard whether focus navigation may scroll the focused item into view
 */
data class LazyColumnNodeProps(
    val contentPadding: LazyContentPadding,
    val spacing: UiDp,
    val items: List<LazyListItem>,
    val state: LazyListState? = null,
    val reverseLayout: Boolean = false,
    val userScrollEnabled: Boolean = true,
    val prefetchPolicy: LazyLayoutPrefetchPolicy = LazyLayoutPrefetchPolicy(),
    val reusePolicy: CollectionReusePolicy = CollectionReusePolicy(),
    val motionPolicy: CollectionMotionPolicy = CollectionMotionPolicy(),
    val focusFollowKeyboard: Boolean = false,
) : NodeSpec
