package com.viewcompose.ui.node.spec

import com.viewcompose.ui.node.LazyListItem
import com.viewcompose.ui.node.policy.CollectionMotionPolicy
import com.viewcompose.ui.node.policy.CollectionReusePolicy
import com.viewcompose.ui.state.PagerState

/**
 * Immutable renderer properties for a horizontal pager.
 *
 * Page indexes remain logical in RTL. The pager owns discrete selection only; descendant focus
 * visibility belongs to a scroll owner declared inside the page.
 *
 * @property pages ordered keyed page models
 * @property currentPage externally selected page index
 * @property onPageChanged callback for a settled user- or renderer-driven page change
 * @property offscreenPageLimit adjacent-page residency limit, or `-1` for renderer defaults
 * @property pagerState optional command and observation state attached to the native pager
 * @property userScrollEnabled whether direct pointer and accessibility paging is accepted
 * @property reusePolicy native page-view reuse policy
 * @property motionPolicy native page change and collection mutation animation policy
 * @throws IllegalArgumentException when [offscreenPageLimit] is neither `-1` nor positive
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
) : NodeSpec {
    init {
        require(offscreenPageLimit == -1 || offscreenPageLimit >= 1) {
            "offscreenPageLimit must be -1 or at least 1."
        }
    }
}
