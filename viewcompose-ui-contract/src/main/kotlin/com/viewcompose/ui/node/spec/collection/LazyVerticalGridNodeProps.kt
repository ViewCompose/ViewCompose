package com.viewcompose.ui.node.spec
import com.viewcompose.ui.unit.UiDp

import com.viewcompose.ui.node.LazyListItem
import com.viewcompose.ui.node.policy.CollectionMotionPolicy
import com.viewcompose.ui.node.policy.CollectionReusePolicy
import com.viewcompose.ui.node.policy.LazyContentPadding
import com.viewcompose.ui.node.policy.LazyLayoutPrefetchPolicy
import com.viewcompose.ui.state.LazyListState

/**
 * Immutable renderer properties for a vertically scrolling fixed-span grid.
 *
 * @property spanCount number of cells across the horizontal axis
 * @property contentPadding logical padding inside the scrollable content
 * @property horizontalSpacing spacing between adjacent columns
 * @property verticalSpacing spacing between adjacent rows
 * @property items ordered keyed item models
 * @property state optional command and observation state attached to the native grid
 * @property reverseLayout whether row placement and scrolling start from the opposite edge
 * @property userScrollEnabled whether direct user scrolling is accepted
 * @property prefetchPolicy eager preparation and native view-cache hints
 * @property reusePolicy native item-view pool policy
 * @property motionPolicy native item mutation animation policy
 * @property focusFollowKeyboard whether focus navigation may scroll the focused item into view
 */
data class LazyVerticalGridNodeProps(
    val spanCount: Int,
    val contentPadding: LazyContentPadding,
    val horizontalSpacing: UiDp,
    val verticalSpacing: UiDp,
    val items: List<LazyListItem>,
    val state: LazyListState?,
    val reverseLayout: Boolean = false,
    val userScrollEnabled: Boolean = true,
    val prefetchPolicy: LazyLayoutPrefetchPolicy = LazyLayoutPrefetchPolicy(),
    val reusePolicy: CollectionReusePolicy = CollectionReusePolicy(),
    val motionPolicy: CollectionMotionPolicy = CollectionMotionPolicy(),
    val focusFollowKeyboard: Boolean = false,
) : NodeSpec
