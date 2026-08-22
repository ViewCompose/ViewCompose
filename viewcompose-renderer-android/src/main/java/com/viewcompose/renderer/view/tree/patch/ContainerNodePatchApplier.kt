package com.viewcompose.renderer.view.tree.patch

import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.viewcompose.renderer.view.container.DeclarativeAnimatedSizeHostLayout
import com.viewcompose.renderer.view.container.DeclarativeAnimatedContentHostLayout
import com.viewcompose.renderer.view.container.DeclarativeAnimatedContentItemLayout
import com.viewcompose.renderer.view.container.DeclarativeAnimatedVisibilityHostLayout
import com.viewcompose.renderer.view.container.DeclarativeBoxLayout
import com.viewcompose.renderer.view.container.DeclarativeConstraintLayout
import com.viewcompose.renderer.view.container.DeclarativeFlowColumnLayout
import com.viewcompose.renderer.view.container.DeclarativeFlowRowLayout
import com.viewcompose.renderer.view.container.DeclarativeHorizontalPagerLayout
import com.viewcompose.renderer.view.container.DeclarativeLazyVerticalGridLayout
import com.viewcompose.renderer.view.container.DeclarativeLayoutConstraintHost
import com.viewcompose.renderer.view.container.DeclarativeLinearLayout
import com.viewcompose.renderer.view.container.DeclarativeNavigationBarLayout
import com.viewcompose.renderer.view.container.DeclarativeScrollableColumnLayout
import com.viewcompose.renderer.view.container.DeclarativeScrollableRowLayout
import com.viewcompose.renderer.view.container.DeclarativeSegmentedControlLayout
import com.viewcompose.renderer.view.container.DeclarativeTabRowLayout
import com.viewcompose.renderer.view.container.DeclarativeVerticalPagerLayout
import com.viewcompose.renderer.view.lazy.adapter.LazyListAdapter
import com.viewcompose.renderer.view.lazy.adapter.LazyStickyHeaderDecoration
import com.viewcompose.renderer.view.lazy.reuse.FrameworkRecyclerViewDefaults
import com.viewcompose.renderer.view.tree.AnimatedSizeHostNodePatch
import com.viewcompose.renderer.view.tree.AnimatedContentHostNodePatch
import com.viewcompose.renderer.view.tree.AnimatedContentItemNodePatch
import com.viewcompose.renderer.view.tree.AnimatedVisibilityHostNodePatch
import com.viewcompose.renderer.view.tree.BoxNodePatch
import com.viewcompose.renderer.view.tree.CollectionViewBinder
import com.viewcompose.renderer.view.tree.ColumnNodePatch
import com.viewcompose.renderer.view.tree.ContainerViewBinder
import com.viewcompose.renderer.view.tree.ContainerViewSpecReader
import com.viewcompose.renderer.view.tree.ConstraintLayoutNodePatch
import com.viewcompose.renderer.view.tree.FlowColumnNodePatch
import com.viewcompose.renderer.view.tree.FlowRowNodePatch
import com.viewcompose.renderer.view.tree.HorizontalPagerNodePatch
import com.viewcompose.renderer.view.tree.LazyColumnNodePatch
import com.viewcompose.renderer.view.tree.LazyRowNodePatch
import com.viewcompose.renderer.view.tree.LazyVerticalGridNodePatch
import com.viewcompose.renderer.view.tree.LayoutConstraintHostNodePatch
import com.viewcompose.renderer.view.tree.NavigationBarNodePatch
import com.viewcompose.renderer.view.tree.PagerViewBinder
import com.viewcompose.renderer.view.tree.PullToRefreshNodePatch
import com.viewcompose.renderer.view.tree.RetainedSessionSubmission
import com.viewcompose.renderer.view.tree.RowNodePatch
import com.viewcompose.renderer.view.tree.ScrollableColumnNodePatch
import com.viewcompose.renderer.view.tree.ScrollableRowNodePatch
import com.viewcompose.renderer.view.tree.ScrollableViewBinder
import com.viewcompose.renderer.view.tree.SegmentedControlNodePatch
import com.viewcompose.renderer.view.tree.TabRowNodePatch
import com.viewcompose.renderer.view.tree.VerticalPagerNodePatch
import com.viewcompose.renderer.view.requireUiEnvironment
import com.viewcompose.renderer.view.resolvePadding
import com.viewcompose.renderer.view.roundToPx
import com.viewcompose.renderer.view.toPx

/** Fine-grained patch applier for container, collection, and navigation nodes. */
internal object ContainerNodePatchApplier {
    /** Updates Row spacing, main-axis arrangement, and cross-axis alignment. */
    fun applyRowPatch(
        view: DeclarativeLinearLayout,
        patch: RowNodePatch,
    ) {
        val previous = patch.previous
        val next = patch.next
        if (previous.spacing != next.spacing) {
            view.itemSpacing = view.requireUiEnvironment().roundToPx(next.spacing)
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
            view.itemSpacing = view.requireUiEnvironment().roundToPx(next.spacing)
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

    fun applyAnimatedContentHostPatch(
        view: DeclarativeAnimatedContentHostLayout,
        patch: AnimatedContentHostNodePatch,
    ) {
        ContainerViewBinder.bindAnimatedContentHost(
            view = view,
            spec = ContainerViewBinder.AnimatedContentHostSpec(
                segmentId = patch.next.segmentId,
                sizeProgress = patch.next.sizeProgress,
                sizeTransformEnabled = patch.next.sizeTransformEnabled,
                clipToBounds = patch.next.clipToBounds,
                contentGravity = with(ContainerViewSpecReader) {
                    patch.next.contentAlignment.toGravity()
                },
            ),
        )
    }

    fun applyAnimatedContentItemPatch(
        view: DeclarativeAnimatedContentItemLayout,
        patch: AnimatedContentItemNodePatch,
    ) {
        val next = patch.next
        ContainerViewBinder.bindAnimatedContentItem(
            view = view,
            spec = ContainerViewBinder.AnimatedContentItemSpec(
                alpha = next.alpha,
                scaleX = next.scaleX,
                scaleY = next.scaleY,
                translationXFraction = next.translationXFraction,
                translationYFraction = next.translationYFraction,
                revealWidthFraction = next.revealWidthFraction,
                revealHeightFraction = next.revealHeightFraction,
                pivotFractionX = next.transformOrigin.pivotFractionX,
                pivotFractionY = next.transformOrigin.pivotFractionY,
                active = next.active,
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

    fun applyLayoutConstraintHostPatch(
        view: DeclarativeLayoutConstraintHost,
        patch: LayoutConstraintHostNodePatch,
    ) {
        val environment = view.requireUiEnvironment()
        ContainerViewBinder.bindLayoutConstraintHost(
            view = view,
            spec = ContainerViewBinder.LayoutConstraintHostSpec(
                maxWidthPx = patch.next.maxWidth?.let(environment::roundToPx),
                maxHeightPx = patch.next.maxHeight?.let(environment::roundToPx),
                aspectRatio = patch.next.aspectRatio,
                matchHeightConstraintsFirst = patch.next.matchHeightConstraintsFirst,
                fillWidth = patch.next.fillWidth,
                fillHeight = patch.next.fillHeight,
            ),
        )
    }

    /** Updates lazy-column RecyclerView policies, padding, items, and state connection. */
    fun applyLazyColumnPatch(
        view: RecyclerView,
        patch: LazyColumnNodePatch,
        submission: RetainedSessionSubmission = RetainedSessionSubmission.immediate(),
    ) {
        val previous = patch.previous
        val next = patch.next
        val environment = view.requireUiEnvironment()
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
            ContainerViewBinder.applyLazyListPadding(view, environment.resolvePadding(next.contentPadding))
        }
        if (previous.spacing != next.spacing) {
            ContainerViewBinder.applyLazyListSpacing(
                view,
                environment.roundToPx(next.spacing),
                LinearLayoutManager.VERTICAL,
            )
        }
        if (previous.items != next.items) {
            val adapter = view.adapter as? LazyListAdapter ?: LazyListAdapter().also {
                view.adapter = it
            }
            submission.publish {
                LazyStickyHeaderDecoration.submitItemsAndUpdate(
                    recyclerView = view,
                    adapter = adapter,
                    items = next.items,
                    submissionRevision = submission.revision,
                )
            }
        }
        val adapter = view.adapter as? LazyListAdapter ?: LazyListAdapter().also {
            view.adapter = it
        }
        adapter.configureMountedTreeCache(next.reusePolicy.mountedTreeCacheSize)
        submission.publish {
            adapter.bindState(
                recyclerView = view,
                state = next.state,
                mainAxisItemSpacing = environment.roundToPx(next.spacing),
            )
        }
    }

    fun applyLazyRowPatch(
        view: RecyclerView,
        patch: LazyRowNodePatch,
        submission: RetainedSessionSubmission = RetainedSessionSubmission.immediate(),
    ) {
        val previous = patch.previous
        val next = patch.next
        val environment = view.requireUiEnvironment()
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
            ContainerViewBinder.applyLazyListPadding(view, environment.resolvePadding(next.contentPadding))
        }
        if (previous.spacing != next.spacing) {
            ContainerViewBinder.applyLazyListSpacing(
                view,
                environment.roundToPx(next.spacing),
                LinearLayoutManager.HORIZONTAL,
            )
        }
        if (previous.items != next.items) {
            val adapter = view.adapter as? LazyListAdapter
                ?: LazyListAdapter(LinearLayoutManager.HORIZONTAL).also {
                    view.adapter = it
                }
            submission.publish {
                LazyStickyHeaderDecoration.submitItemsAndUpdate(
                    recyclerView = view,
                    adapter = adapter,
                    items = next.items,
                    submissionRevision = submission.revision,
                )
            }
        }
        val adapter = view.adapter as? LazyListAdapter
            ?: LazyListAdapter(LinearLayoutManager.HORIZONTAL).also {
                view.adapter = it
            }
        adapter.configureMountedTreeCache(next.reusePolicy.mountedTreeCacheSize)
        submission.publish {
            adapter.bindState(
                recyclerView = view,
                state = next.state,
                mainAxisItemSpacing = environment.roundToPx(next.spacing),
            )
        }
    }

    fun applySegmentedControlPatch(
        view: DeclarativeSegmentedControlLayout,
        patch: SegmentedControlNodePatch,
    ) {
        val environment = view.requireUiEnvironment()
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
                unselectedStateLayerColors = patch.next.unselectedStateLayerColors,
                selectedStateLayerColors = patch.next.selectedStateLayerColors,
                textSizePx = environment.toPx(patch.next.textSizeSp),
                fontWeight = patch.next.fontWeight,
                fontFamily = patch.next.fontFamily,
                letterSpacingEm = patch.next.letterSpacingEm,
                lineHeightPx = patch.next.lineHeightSp?.let(environment.density::roundToPx),
                includeFontPadding = patch.next.includeFontPadding,
                paddingHorizontal = environment.roundToPx(patch.next.paddingHorizontal),
                paddingVertical = environment.roundToPx(patch.next.paddingVertical),
                density = environment.density,
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
            view.innerLayout.itemSpacing = view.requireUiEnvironment().roundToPx(next.spacing)
        }
        if (previous.arrangement != next.arrangement) {
            view.innerLayout.mainAxisArrangement = next.arrangement
        }
        if (previous.horizontalAlignment != next.horizontalAlignment) {
            with(ContainerViewSpecReader) {
                view.innerLayout.gravity = next.horizontalAlignment.toGravity()
            }
        }
        if (previous.state !== next.state || previous.userScrollEnabled != next.userScrollEnabled) {
            view.bindScrollState(next.state, next.userScrollEnabled)
        }
    }

    fun applyScrollableRowPatch(
        view: DeclarativeScrollableRowLayout,
        patch: ScrollableRowNodePatch,
    ) {
        val previous = patch.previous
        val next = patch.next
        if (previous.spacing != next.spacing) {
            view.innerLayout.itemSpacing = view.requireUiEnvironment().roundToPx(next.spacing)
        }
        if (previous.arrangement != next.arrangement) {
            view.innerLayout.mainAxisArrangement = next.arrangement
        }
        if (previous.verticalAlignment != next.verticalAlignment) {
            with(ContainerViewSpecReader) {
                view.innerLayout.gravity = next.verticalAlignment.toGravity()
            }
        }
        if (previous.state !== next.state || previous.userScrollEnabled != next.userScrollEnabled) {
            view.bindScrollState(next.state, next.userScrollEnabled)
        }
    }

    fun applyFlowRowPatch(
        view: DeclarativeFlowRowLayout,
        patch: FlowRowNodePatch,
    ) {
        val previous = patch.previous
        val next = patch.next
        val environment = view.requireUiEnvironment()
        if (previous.horizontalSpacing != next.horizontalSpacing) {
            view.horizontalSpacing = environment.roundToPx(next.horizontalSpacing)
        }
        if (previous.verticalSpacing != next.verticalSpacing) {
            view.verticalSpacing = environment.roundToPx(next.verticalSpacing)
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
        val environment = view.requireUiEnvironment()
        if (previous.horizontalSpacing != next.horizontalSpacing) {
            view.horizontalSpacing = environment.roundToPx(next.horizontalSpacing)
        }
        if (previous.verticalSpacing != next.verticalSpacing) {
            view.verticalSpacing = environment.roundToPx(next.verticalSpacing)
        }
        if (previous.maxItemsInEachColumn != next.maxItemsInEachColumn) {
            view.maxItemsInEachColumn = next.maxItemsInEachColumn
        }
    }

    fun applyNavigationBarPatch(
        view: DeclarativeNavigationBarLayout,
        patch: NavigationBarNodePatch,
    ) {
        val environment = view.requireUiEnvironment()
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
                selectedStateLayerColors = patch.next.selectedStateLayerColors,
                unselectedStateLayerColors = patch.next.unselectedStateLayerColors,
                iconSize = environment.roundToPx(patch.next.iconSize),
                labelSizePx = environment.toPx(patch.next.labelSizeSp),
                labelFontWeight = patch.next.labelFontWeight,
                labelFontFamily = patch.next.labelFontFamily,
                labelLetterSpacingEm = patch.next.labelLetterSpacingEm,
                labelLineHeightPx = patch.next.labelLineHeightSp?.let(environment.density::roundToPx),
                labelIncludeFontPadding = patch.next.labelIncludeFontPadding,
                badgeColor = patch.next.badgeColor,
                badgeTextColor = patch.next.badgeTextColor,
            ),
        )
    }

    fun applyHorizontalPagerPatch(
        view: DeclarativeHorizontalPagerLayout,
        patch: HorizontalPagerNodePatch,
        submission: RetainedSessionSubmission = RetainedSessionSubmission.immediate(),
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
            submission = submission,
        )
    }

    fun applyTabRowPatch(
        view: DeclarativeTabRowLayout,
        patch: TabRowNodePatch,
        submission: RetainedSessionSubmission = RetainedSessionSubmission.immediate(),
    ) {
        val environment = view.requireUiEnvironment()
        PagerViewBinder.bindTabRow(
            view = view,
            spec = PagerViewBinder.TabRowSpec(
                selectedIndex = patch.next.selectedIndex,
                pagerState = patch.next.pagerState,
                indicatorColor = patch.next.indicatorColor,
                indicatorHeight = environment.roundToPx(patch.next.indicatorHeight),
                indicatorCornerRadius = environment.roundToPx(patch.next.indicatorCornerRadius),
                indicatorPosition = patch.next.indicatorPosition,
                indicatorWidthMode = patch.next.indicatorWidthMode,
                indicatorFixedWidth = environment.roundToPx(patch.next.indicatorFixedWidth),
                containerColor = patch.next.containerColor,
                scrollable = patch.next.scrollable,
                equalWidth = patch.next.equalWidth,
                itemSpacing = environment.roundToPx(patch.next.itemSpacing),
                itemPaddingHorizontal = environment.roundToPx(patch.next.itemPaddingHorizontal),
                itemPaddingVertical = environment.roundToPx(patch.next.itemPaddingVertical),
                minItemWidth = environment.roundToPx(patch.next.minItemWidth),
            ),
            submission = submission,
        )
    }

    fun applyVerticalPagerPatch(
        view: DeclarativeVerticalPagerLayout,
        patch: VerticalPagerNodePatch,
        submission: RetainedSessionSubmission = RetainedSessionSubmission.immediate(),
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
            ),
            submission = submission,
        )
    }

    fun applyLazyVerticalGridPatch(
        view: DeclarativeLazyVerticalGridLayout,
        patch: LazyVerticalGridNodePatch,
        submission: RetainedSessionSubmission = RetainedSessionSubmission.immediate(),
    ) {
        val environment = view.requireUiEnvironment()
        CollectionViewBinder.bindLazyVerticalGrid(
            view = view,
            spec = CollectionViewBinder.LazyVerticalGridSpec(
                cells = with(CollectionViewBinder) {
                    patch.next.cells.toPixels(environment)
                },
                contentPadding = environment.resolvePadding(patch.next.contentPadding),
                horizontalSpacing = environment.roundToPx(patch.next.horizontalSpacing),
                verticalSpacing = environment.roundToPx(patch.next.verticalSpacing),
                items = patch.next.items,
                state = patch.next.state,
                reverseLayout = patch.next.reverseLayout,
                userScrollEnabled = patch.next.userScrollEnabled,
                prefetchPolicy = patch.next.prefetchPolicy,
                reusePolicy = patch.next.reusePolicy,
                motionPolicy = patch.next.motionPolicy,
            ),
            submission = submission,
        )
    }

    fun applyPullToRefreshPatch(
        view: SwipeRefreshLayout,
        patch: PullToRefreshNodePatch,
    ) {
        val next = patch.next
        ScrollableViewBinder.bindPullToRefresh(
            view = view,
            spec = ScrollableViewBinder.PullToRefreshSpec(
                isRefreshing = next.isRefreshing,
                onRefresh = next.onRefresh,
                enabled = next.enabled,
                indicatorColor = next.indicatorColor,
            ),
        )
    }

}
