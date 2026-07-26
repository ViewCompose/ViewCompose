package com.viewcompose.renderer.view.tree.patch

import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.viewcompose.ui.node.LazyListItem
import com.viewcompose.renderer.view.container.DeclarativeBoxLayout
import com.viewcompose.renderer.view.container.DeclarativeAnimatedSizeHostLayout
import com.viewcompose.renderer.view.container.DeclarativeAnimatedVisibilityHostLayout
import com.viewcompose.renderer.view.container.DeclarativeConstraintLayout
import com.viewcompose.renderer.view.container.DeclarativeFlowColumnLayout
import com.viewcompose.renderer.view.container.DeclarativeFlowRowLayout
import com.viewcompose.renderer.view.container.DeclarativeHorizontalPagerLayout
import com.viewcompose.renderer.view.container.DeclarativeLazyVerticalGridLayout
import com.viewcompose.renderer.view.container.DeclarativeLinearLayout
import com.viewcompose.renderer.view.container.DeclarativeNavigationBarLayout
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.viewcompose.renderer.view.container.DeclarativeScrollableColumnLayout
import com.viewcompose.renderer.view.container.DeclarativeScrollableRowLayout
import com.viewcompose.renderer.view.container.DeclarativeSegmentedControlLayout
import com.viewcompose.renderer.view.container.DeclarativeTabRowLayout
import com.viewcompose.renderer.view.container.DeclarativeVerticalPagerLayout
import com.viewcompose.renderer.view.tree.BoxNodePatch
import com.viewcompose.renderer.view.tree.AnimatedSizeHostNodePatch
import com.viewcompose.renderer.view.tree.AnimatedVisibilityHostNodePatch
import com.viewcompose.renderer.view.tree.CollectionViewBinder
import com.viewcompose.renderer.view.tree.ColumnNodePatch
import com.viewcompose.renderer.view.tree.ContainerViewBinder
import com.viewcompose.renderer.view.tree.ConstraintLayoutNodePatch
import com.viewcompose.renderer.view.tree.FlowColumnNodePatch
import com.viewcompose.renderer.view.tree.FlowRowNodePatch
import com.viewcompose.renderer.view.tree.HorizontalPagerNodePatch
import com.viewcompose.renderer.view.tree.LazyColumnNodePatch
import com.viewcompose.renderer.view.tree.LazyRowNodePatch
import com.viewcompose.renderer.view.tree.LazyVerticalGridNodePatch
import com.viewcompose.renderer.view.tree.NavigationBarNodePatch
import com.viewcompose.renderer.view.tree.PagerViewBinder
import com.viewcompose.renderer.view.tree.PullToRefreshNodePatch
import com.viewcompose.renderer.view.tree.RowNodePatch
import com.viewcompose.renderer.view.tree.ScrollableColumnNodePatch
import com.viewcompose.renderer.view.tree.ScrollableRowNodePatch
import com.viewcompose.renderer.view.tree.SegmentedControlNodePatch
import com.viewcompose.renderer.view.tree.TabRowNodePatch
import com.viewcompose.renderer.view.tree.ContainerViewSpecReader
import com.viewcompose.renderer.view.tree.VerticalPagerNodePatch
import com.viewcompose.renderer.view.lazy.adapter.LazyListAdapter
import com.viewcompose.renderer.view.lazy.adapter.LazyStickyHeaderDecoration
import com.viewcompose.renderer.view.lazy.focus.LazyFocusFollowLayoutMonitor
import com.viewcompose.renderer.view.lazy.focus.ScrollableFocusFollowLayoutMonitor
import com.viewcompose.renderer.view.lazy.reuse.FrameworkRecyclerViewDefaults
import com.viewcompose.renderer.view.lazy.state.UiLazyListConnector

internal object ContainerNodePatchApplier {
    fun applyRowPatch(
        view: DeclarativeLinearLayout,
        patch: RowNodePatch,
    ) {
        val previous = patch.previous
        val next = patch.next
        if (previous.spacing != next.spacing) {
            view.itemSpacing = next.spacing
        }
        if (previous.arrangement != next.arrangement) {
            view.mainAxisArrangement = next.arrangement
        }
        if (previous.verticalAlignment != next.verticalAlignment) {
            with(ContainerViewSpecReader) {
                view.gravity = next.verticalAlignment.toGravity()
            }
        }
    }

    fun applyColumnPatch(
        view: DeclarativeLinearLayout,
        patch: ColumnNodePatch,
    ) {
        val previous = patch.previous
        val next = patch.next
        if (previous.spacing != next.spacing) {
            view.itemSpacing = next.spacing
        }
        if (previous.arrangement != next.arrangement) {
            view.mainAxisArrangement = next.arrangement
        }
        if (previous.horizontalAlignment != next.horizontalAlignment) {
            with(ContainerViewSpecReader) {
                view.gravity = next.horizontalAlignment.toGravity()
            }
        }
    }

    fun applyBoxPatch(
        view: DeclarativeBoxLayout,
        patch: BoxNodePatch,
    ) {
        val previous = patch.previous
        val next = patch.next
        if (previous.contentAlignment != next.contentAlignment) {
            with(ContainerViewSpecReader) {
                view.contentGravity = next.contentAlignment.toGravity()
            }
        }
    }

    fun applyConstraintLayoutPatch(
        view: DeclarativeConstraintLayout,
        patch: ConstraintLayoutNodePatch,
    ) {
        ContainerViewBinder.bindConstraintLayout(
            view = view,
            spec = ContainerViewBinder.ConstraintLayoutSpec(
                decoupledConstraintSet = patch.next.constraintSet,
                inlineHelpers = patch.next.helpers,
            ),
        )
    }

    fun applyAnimatedVisibilityHostPatch(
        view: DeclarativeAnimatedVisibilityHostLayout,
        patch: AnimatedVisibilityHostNodePatch,
    ) {
        ContainerViewBinder.bindAnimatedVisibilityHost(
            view = view,
            spec = ContainerViewBinder.AnimatedVisibilityHostSpec(
                alpha = patch.next.alpha,
                widthScale = patch.next.widthScale,
                heightScale = patch.next.heightScale,
                clipToBounds = patch.next.clipToBounds,
            ),
        )
    }

    fun applyAnimatedSizeHostPatch(
        view: DeclarativeAnimatedSizeHostLayout,
        patch: AnimatedSizeHostNodePatch,
    ) {
        ContainerViewBinder.bindAnimatedSizeHost(
            view = view,
            spec = ContainerViewBinder.AnimatedSizeHostSpec(
                animationSpec = patch.next.animationSpec,
            ),
        )
    }

    fun applyLazyColumnPatch(
        view: RecyclerView,
        patch: LazyColumnNodePatch,
    ) {
        val previous = patch.previous
        val next = patch.next
        if (previous.reusePolicy != next.reusePolicy || previous.motionPolicy != next.motionPolicy) {
            FrameworkRecyclerViewDefaults.applyLazyColumnDefaults(
                recyclerView = view,
                sharePool = next.reusePolicy.sharePool,
                disableItemAnimator = next.motionPolicy.disableItemAnimator,
                animateInsert = next.motionPolicy.animateInsert,
                animateRemove = next.motionPolicy.animateRemove,
                animateMove = next.motionPolicy.animateMove,
                animateChange = next.motionPolicy.animateChange,
            )
        }
        if (previous.focusFollowKeyboard != next.focusFollowKeyboard) {
            LazyFocusFollowLayoutMonitor.apply(
                recyclerView = view,
                enabled = next.focusFollowKeyboard,
            )
        }
        if (
            previous.reverseLayout != next.reverseLayout ||
            previous.userScrollEnabled != next.userScrollEnabled ||
            previous.prefetchPolicy != next.prefetchPolicy
        ) {
            CollectionViewBinder.configureLazyListLayout(
                view = view,
                reverseLayout = next.reverseLayout,
                userScrollEnabled = next.userScrollEnabled,
                prefetchPolicy = next.prefetchPolicy,
            )
        }
        if (previous.contentPadding != next.contentPadding) {
            ContainerViewBinder.applyLazyListPadding(view, next.contentPadding)
        }
        if (previous.spacing != next.spacing) {
            ContainerViewBinder.applyLazyListSpacing(view, next.spacing, LinearLayoutManager.VERTICAL)
        }
        if (previous.items != next.items || previous.items.hasSessionIdentityChange(next.items)) {
            val adapter = view.adapter as? LazyListAdapter ?: LazyListAdapter().also {
                view.adapter = it
            }
            adapter.submitItems(next.items)
            LazyStickyHeaderDecoration.update(view, adapter)
        }
        if (previous.state !== next.state) {
            previous.state?.attach(null)
        }
        next.state?.attach(
            UiLazyListConnector(
                recyclerView = view,
                mainAxisItemSpacing = next.spacing,
            ),
        )
    }

    fun applyLazyRowPatch(
        view: RecyclerView,
        patch: LazyRowNodePatch,
    ) {
        val previous = patch.previous
        val next = patch.next
        if (previous.reusePolicy != next.reusePolicy || previous.motionPolicy != next.motionPolicy) {
            FrameworkRecyclerViewDefaults.applyLazyRowDefaults(
                recyclerView = view,
                sharePool = next.reusePolicy.sharePool,
                disableItemAnimator = next.motionPolicy.disableItemAnimator,
                animateInsert = next.motionPolicy.animateInsert,
                animateRemove = next.motionPolicy.animateRemove,
                animateMove = next.motionPolicy.animateMove,
                animateChange = next.motionPolicy.animateChange,
            )
        }
        LazyFocusFollowLayoutMonitor.apply(
            recyclerView = view,
            enabled = false,
        )
        if (
            previous.reverseLayout != next.reverseLayout ||
            previous.userScrollEnabled != next.userScrollEnabled ||
            previous.prefetchPolicy != next.prefetchPolicy
        ) {
            CollectionViewBinder.configureLazyListLayout(
                view = view,
                reverseLayout = next.reverseLayout,
                userScrollEnabled = next.userScrollEnabled,
                prefetchPolicy = next.prefetchPolicy,
            )
        }
        if (previous.contentPadding != next.contentPadding) {
            ContainerViewBinder.applyLazyListPadding(view, next.contentPadding)
        }
        if (previous.spacing != next.spacing) {
            ContainerViewBinder.applyLazyListSpacing(view, next.spacing, LinearLayoutManager.HORIZONTAL)
        }
        if (previous.items != next.items || previous.items.hasSessionIdentityChange(next.items)) {
            val adapter = view.adapter as? LazyListAdapter
                ?: LazyListAdapter(LinearLayoutManager.HORIZONTAL).also {
                    view.adapter = it
                }
            adapter.submitItems(next.items)
            LazyStickyHeaderDecoration.update(view, adapter)
        }
        if (previous.state !== next.state) {
            previous.state?.attach(null)
        }
        next.state?.attach(
            UiLazyListConnector(
                recyclerView = view,
                mainAxisItemSpacing = next.spacing,
            ),
        )
    }

    fun applySegmentedControlPatch(
        view: DeclarativeSegmentedControlLayout,
        patch: SegmentedControlNodePatch,
    ) {
        PagerViewBinder.bindSegmentedControl(
            view = view,
            spec = PagerViewBinder.SegmentedControlSpec(
                items = patch.next.items,
                selectedIndex = patch.next.selectedIndex,
                onSelectionChange = patch.next.onSelectionChange,
                enabled = patch.next.enabled,
                backgroundColor = patch.next.backgroundColor,
                indicatorColor = patch.next.indicatorColor,
                shape = patch.next.shape,
                textColor = patch.next.textColor,
                selectedTextColor = patch.next.selectedTextColor,
                rippleColor = patch.next.rippleColor,
                textSizeSp = patch.next.textSizeSp,
                fontWeight = patch.next.fontWeight,
                fontFamily = patch.next.fontFamily,
                letterSpacingEm = patch.next.letterSpacingEm,
                lineHeightSp = patch.next.lineHeightSp,
                includeFontPadding = patch.next.includeFontPadding,
                paddingHorizontal = patch.next.paddingHorizontal,
                paddingVertical = patch.next.paddingVertical,
            ),
        )
    }

    fun applyScrollableColumnPatch(
        view: DeclarativeScrollableColumnLayout,
        patch: ScrollableColumnNodePatch,
    ) {
        val previous = patch.previous
        val next = patch.next
        if (previous.spacing != next.spacing) {
            view.innerLayout.itemSpacing = next.spacing
        }
        if (previous.arrangement != next.arrangement) {
            view.innerLayout.mainAxisArrangement = next.arrangement
        }
        if (previous.horizontalAlignment != next.horizontalAlignment) {
            with(ContainerViewSpecReader) {
                view.innerLayout.gravity = next.horizontalAlignment.toGravity()
            }
        }
        if (previous.focusFollowKeyboard != next.focusFollowKeyboard) {
            ScrollableFocusFollowLayoutMonitor.apply(
                scrollView = view,
                enabled = next.focusFollowKeyboard,
            )
        }
    }

    fun applyScrollableRowPatch(
        view: DeclarativeScrollableRowLayout,
        patch: ScrollableRowNodePatch,
    ) {
        val previous = patch.previous
        val next = patch.next
        if (previous.spacing != next.spacing) {
            view.innerLayout.itemSpacing = next.spacing
        }
        if (previous.arrangement != next.arrangement) {
            view.innerLayout.mainAxisArrangement = next.arrangement
        }
        if (previous.verticalAlignment != next.verticalAlignment) {
            with(ContainerViewSpecReader) {
                view.innerLayout.gravity = next.verticalAlignment.toGravity()
            }
        }
    }

    fun applyFlowRowPatch(
        view: DeclarativeFlowRowLayout,
        patch: FlowRowNodePatch,
    ) {
        val previous = patch.previous
        val next = patch.next
        if (previous.horizontalSpacing != next.horizontalSpacing) {
            view.horizontalSpacing = next.horizontalSpacing
        }
        if (previous.verticalSpacing != next.verticalSpacing) {
            view.verticalSpacing = next.verticalSpacing
        }
        if (previous.maxItemsInEachRow != next.maxItemsInEachRow) {
            view.maxItemsInEachRow = next.maxItemsInEachRow
        }
    }

    fun applyFlowColumnPatch(
        view: DeclarativeFlowColumnLayout,
        patch: FlowColumnNodePatch,
    ) {
        val previous = patch.previous
        val next = patch.next
        if (previous.horizontalSpacing != next.horizontalSpacing) {
            view.horizontalSpacing = next.horizontalSpacing
        }
        if (previous.verticalSpacing != next.verticalSpacing) {
            view.verticalSpacing = next.verticalSpacing
        }
        if (previous.maxItemsInEachColumn != next.maxItemsInEachColumn) {
            view.maxItemsInEachColumn = next.maxItemsInEachColumn
        }
    }

    fun applyNavigationBarPatch(
        view: DeclarativeNavigationBarLayout,
        patch: NavigationBarNodePatch,
    ) {
        CollectionViewBinder.bindNavigationBar(
            view = view,
            spec = CollectionViewBinder.NavigationBarSpec(
                items = patch.next.items,
                selectedIndex = patch.next.selectedIndex,
                onItemSelected = patch.next.onItemSelected,
                containerColor = patch.next.containerColor,
                selectedIconColor = patch.next.selectedIconColor,
                unselectedIconColor = patch.next.unselectedIconColor,
                selectedLabelColor = patch.next.selectedLabelColor,
                unselectedLabelColor = patch.next.unselectedLabelColor,
                indicatorColor = patch.next.indicatorColor,
                rippleColor = patch.next.rippleColor,
                iconSize = patch.next.iconSize,
                labelSizeSp = patch.next.labelSizeSp,
                labelFontWeight = patch.next.labelFontWeight,
                labelFontFamily = patch.next.labelFontFamily,
                labelLetterSpacingEm = patch.next.labelLetterSpacingEm,
                labelLineHeightSp = patch.next.labelLineHeightSp,
                labelIncludeFontPadding = patch.next.labelIncludeFontPadding,
                badgeColor = patch.next.badgeColor,
                badgeTextColor = patch.next.badgeTextColor,
            ),
        )
    }

    fun applyHorizontalPagerPatch(
        view: DeclarativeHorizontalPagerLayout,
        patch: HorizontalPagerNodePatch,
    ) {
        PagerViewBinder.bindHorizontalPager(
            view = view,
            spec = PagerViewBinder.HorizontalPagerSpec(
                pages = patch.next.pages,
                currentPage = patch.next.currentPage,
                onPageChanged = patch.next.onPageChanged,
                offscreenPageLimit = patch.next.offscreenPageLimit,
                pagerState = patch.next.pagerState,
                userScrollEnabled = patch.next.userScrollEnabled,
                reusePolicy = patch.next.reusePolicy,
                motionPolicy = patch.next.motionPolicy,
            ),
        )
    }

    fun applyTabRowPatch(
        view: DeclarativeTabRowLayout,
        patch: TabRowNodePatch,
    ) {
        PagerViewBinder.bindTabRow(
            view = view,
            spec = PagerViewBinder.TabRowSpec(
                tabs = patch.next.tabs,
                selectedIndex = patch.next.selectedIndex,
                onTabSelected = patch.next.onTabSelected,
                pagerState = patch.next.pagerState,
                indicatorColor = patch.next.indicatorColor,
                indicatorHeight = patch.next.indicatorHeight,
                indicatorCornerRadius = patch.next.indicatorCornerRadius,
                indicatorPosition = patch.next.indicatorPosition,
                indicatorWidthMode = patch.next.indicatorWidthMode,
                indicatorFixedWidth = patch.next.indicatorFixedWidth,
                containerColor = patch.next.containerColor,
                scrollable = patch.next.scrollable,
                equalWidth = patch.next.equalWidth,
                rippleColor = patch.next.rippleColor,
                itemSpacing = patch.next.itemSpacing,
                itemPaddingHorizontal = patch.next.itemPaddingHorizontal,
                itemPaddingVertical = patch.next.itemPaddingVertical,
                minItemWidth = patch.next.minItemWidth,
            ),
        )
    }

    fun applyVerticalPagerPatch(
        view: DeclarativeVerticalPagerLayout,
        patch: VerticalPagerNodePatch,
    ) {
        PagerViewBinder.bindVerticalPager(
            view = view,
            spec = PagerViewBinder.VerticalPagerSpec(
                pages = patch.next.pages,
                currentPage = patch.next.currentPage,
                onPageChanged = patch.next.onPageChanged,
                offscreenPageLimit = patch.next.offscreenPageLimit,
                pagerState = patch.next.pagerState,
                userScrollEnabled = patch.next.userScrollEnabled,
                reusePolicy = patch.next.reusePolicy,
                motionPolicy = patch.next.motionPolicy,
                focusFollowKeyboard = patch.next.focusFollowKeyboard,
            ),
        )
    }

    fun applyLazyVerticalGridPatch(
        view: DeclarativeLazyVerticalGridLayout,
        patch: LazyVerticalGridNodePatch,
    ) {
        CollectionViewBinder.bindLazyVerticalGrid(
            view = view,
            spec = CollectionViewBinder.LazyVerticalGridSpec(
                spanCount = patch.next.spanCount,
                contentPadding = patch.next.contentPadding,
                horizontalSpacing = patch.next.horizontalSpacing,
                verticalSpacing = patch.next.verticalSpacing,
                items = patch.next.items,
                state = patch.next.state,
                reverseLayout = patch.next.reverseLayout,
                userScrollEnabled = patch.next.userScrollEnabled,
                prefetchPolicy = patch.next.prefetchPolicy,
                reusePolicy = patch.next.reusePolicy,
                motionPolicy = patch.next.motionPolicy,
                focusFollowKeyboard = patch.next.focusFollowKeyboard,
            ),
        )
    }

    fun applyPullToRefreshPatch(
        view: SwipeRefreshLayout,
        patch: PullToRefreshNodePatch,
    ) {
        val previous = patch.previous
        val next = patch.next
        if (previous.isRefreshing != next.isRefreshing) {
            view.isRefreshing = next.isRefreshing
        }
        if (previous.onRefresh !== next.onRefresh) {
            view.setOnRefreshListener { next.onRefresh?.invoke() }
        }
        if (previous.indicatorColor != next.indicatorColor) {
            view.setColorSchemeColors(next.indicatorColor)
        }
    }

    private fun List<LazyListItem>.hasSessionIdentityChange(next: List<LazyListItem>): Boolean {
        if (size != next.size) return true
        for (index in indices) {
            val previous = this[index]
            val current = next[index]
            if (previous.sessionFactory !== current.sessionFactory) return true
            if (previous.sessionUpdater !== current.sessionUpdater) return true
        }
        return false
    }
}
