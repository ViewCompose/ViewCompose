package com.viewcompose.ui.node.spec

import com.viewcompose.ui.node.LazyListItem
import com.viewcompose.ui.node.policy.CollectionMotionPolicy
import com.viewcompose.ui.node.policy.CollectionReusePolicy
import com.viewcompose.ui.state.PagerState

/**
 * Immutable renderer properties for a horizontal pager.
 *
 * @property pages ordered keyed page models
 * @property currentPage externally selected page index
 * @property onPageChanged callback for a settled user- or renderer-driven page change
 * @property offscreenPageLimit number of adjacent pages the renderer should retain
 * @property pagerState optional command and observation state attached to the native pager
 * @property userScrollEnabled whether direct user paging gestures are accepted
 * @property reusePolicy native page-view reuse policy
 * @property motionPolicy native page change and collection mutation animation policy
 */
data class HorizontalPagerNodeProps(
    val pages: List<LazyListItem>,
    val currentPage: Int,
    val onPageChanged: ((Int) -> Unit)?,
    val offscreenPageLimit: Int,
    val pagerState: PagerState?,
    val userScrollEnabled: Boolean,
    val reusePolicy: CollectionReusePolicy = CollectionReusePolicy(),
    val motionPolicy: CollectionMotionPolicy = CollectionMotionPolicy(),
) : NodeSpec
