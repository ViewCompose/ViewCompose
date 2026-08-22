package com.viewcompose.renderer.view.tree

import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.viewcompose.renderer.view.PaddingPx
import com.viewcompose.renderer.view.container.DeclarativeLazyListView
import com.viewcompose.renderer.view.container.DeclarativeLazyVerticalGridLayout
import com.viewcompose.renderer.view.container.DeclarativeNavigationBarLayout
import com.viewcompose.renderer.view.container.LazyGridCellsPx
import com.viewcompose.renderer.view.lazy.adapter.LazyListAdapter
import com.viewcompose.renderer.view.lazy.adapter.LazyStickyHeaderDecoration
import com.viewcompose.renderer.view.lazy.reuse.FrameworkRecyclerViewDefaults
import com.viewcompose.renderer.view.resolvePadding
import com.viewcompose.renderer.view.roundToPx
import com.viewcompose.renderer.view.toPx
import com.viewcompose.ui.environment.UiEnvironmentValues
import com.viewcompose.ui.node.LazyListItem
import com.viewcompose.ui.node.NavigationBarItem
import com.viewcompose.ui.node.UiStateLayerColors
import com.viewcompose.ui.node.VNode
import com.viewcompose.ui.node.policy.CollectionMotionPolicy
import com.viewcompose.ui.node.policy.CollectionReusePolicy
import com.viewcompose.ui.node.policy.GridCells
import com.viewcompose.ui.node.policy.LazyLayoutPrefetchPolicy
import com.viewcompose.ui.node.spec.LazyColumnNodeProps
import com.viewcompose.ui.node.spec.LazyRowNodeProps
import com.viewcompose.ui.node.spec.LazyVerticalGridNodeProps
import com.viewcompose.ui.node.spec.NavigationBarNodeProps
import com.viewcompose.ui.node.spec.UiFontFamily
import com.viewcompose.ui.state.LazyListState

/**
 * Binds lazy lists, grids, and navigation bars by translating declarative collection specs into
 * stable RecyclerView/container updates.
 */
internal object CollectionViewBinder {
    data class LazyColumnSpec(
        val contentPadding: PaddingPx,
        val spacing: Int,
        val items: List<LazyListItem>,
        val state: LazyListState? = null,
        val reverseLayout: Boolean,
        val userScrollEnabled: Boolean,
        val prefetchPolicy: LazyLayoutPrefetchPolicy,
        val reusePolicy: CollectionReusePolicy,
        val motionPolicy: CollectionMotionPolicy,
    )

    data class NavigationBarSpec(
        val items: List<NavigationBarItem>,
        val selectedIndex: Int,
        val onItemSelected: ((Int) -> Unit)?,
        val containerColor: Int,
        val selectedIconColor: Int,
        val unselectedIconColor: Int,
        val selectedLabelColor: Int,
        val unselectedLabelColor: Int,
        val indicatorColor: Int,
        val selectedStateLayerColors: UiStateLayerColors,
        val unselectedStateLayerColors: UiStateLayerColors,
        val iconSize: Int,
        val labelSizePx: Float,
        val labelFontWeight: Int? = null,
        val labelFontFamily: UiFontFamily? = null,
        val labelLetterSpacingEm: Float? = null,
        val labelLineHeightPx: Int? = null,
        val labelIncludeFontPadding: Boolean = false,
        val badgeColor: Int,
        val badgeTextColor: Int,
    )

    data class LazyVerticalGridSpec(
        val cells: LazyGridCellsPx,
        val contentPadding: PaddingPx,
        val horizontalSpacing: Int,
        val verticalSpacing: Int,
        val items: List<LazyListItem>,
        val state: LazyListState?,
        val reverseLayout: Boolean,
        val userScrollEnabled: Boolean,
        val prefetchPolicy: LazyLayoutPrefetchPolicy,
        val reusePolicy: CollectionReusePolicy,
        val motionPolicy: CollectionMotionPolicy,
    )

    fun bindLazyColumn(
        view: RecyclerView,
        spec: LazyColumnSpec,
        submission: RetainedSessionSubmission = RetainedSessionSubmission.immediate(),
    ) {
        FrameworkRecyclerViewDefaults.applyLazyColumnDefaults(
            recyclerView = view,
            sharePool = spec.reusePolicy.sharePool,
            disableItemAnimator = spec.motionPolicy.disableItemAnimator,
            animateInsert = spec.motionPolicy.animateInsert,
            animateRemove = spec.motionPolicy.animateRemove,
            animateMove = spec.motionPolicy.animateMove,
            animateChange = spec.motionPolicy.animateChange,
        )
        configureLazyListLayout(
            view = view,
            reverseLayout = spec.reverseLayout,
            userScrollEnabled = spec.userScrollEnabled,
            prefetchPolicy = spec.prefetchPolicy,
        )
        val adapter = view.adapter as? LazyListAdapter ?: LazyListAdapter().also {
            view.adapter = it
        }
        adapter.configureMountedTreeCache(spec.reusePolicy.mountedTreeCacheSize)
        ContainerViewBinder.applyLazyListPadding(view, spec.contentPadding)
        ContainerViewBinder.applyLazyListSpacing(view, spec.spacing, LinearLayoutManager.VERTICAL)
        submission.publish {
            LazyStickyHeaderDecoration.submitItemsAndUpdate(
                recyclerView = view,
                adapter = adapter,
                items = spec.items,
                submissionRevision = submission.revision,
            )
        }
        submission.publish {
            adapter.bindState(
                recyclerView = view,
                state = spec.state,
                mainAxisItemSpacing = spec.spacing,
            )
        }
    }

    fun bindLazyRow(
        view: RecyclerView,
        spec: LazyColumnSpec,
        submission: RetainedSessionSubmission = RetainedSessionSubmission.immediate(),
    ) {
        FrameworkRecyclerViewDefaults.applyLazyRowDefaults(
            recyclerView = view,
            sharePool = spec.reusePolicy.sharePool,
            disableItemAnimator = spec.motionPolicy.disableItemAnimator,
            animateInsert = spec.motionPolicy.animateInsert,
            animateRemove = spec.motionPolicy.animateRemove,
            animateMove = spec.motionPolicy.animateMove,
            animateChange = spec.motionPolicy.animateChange,
        )
        configureLazyListLayout(
            view = view,
            reverseLayout = spec.reverseLayout,
            userScrollEnabled = spec.userScrollEnabled,
            prefetchPolicy = spec.prefetchPolicy,
        )
        val adapter = view.adapter as? LazyListAdapter
            ?: LazyListAdapter(LinearLayoutManager.HORIZONTAL).also {
                view.adapter = it
            }
        adapter.configureMountedTreeCache(spec.reusePolicy.mountedTreeCacheSize)
        ContainerViewBinder.applyLazyListPadding(view, spec.contentPadding)
        ContainerViewBinder.applyLazyListSpacing(view, spec.spacing, LinearLayoutManager.HORIZONTAL)
        submission.publish {
            LazyStickyHeaderDecoration.submitItemsAndUpdate(
                recyclerView = view,
                adapter = adapter,
                items = spec.items,
                submissionRevision = submission.revision,
            )
        }
        submission.publish {
            adapter.bindState(
                recyclerView = view,
                state = spec.state,
                mainAxisItemSpacing = spec.spacing,
            )
        }
    }

    fun bindNavigationBar(
        view: DeclarativeNavigationBarLayout,
        spec: NavigationBarSpec,
    ) {
        view.bind(
            items = spec.items,
            selectedIndex = spec.selectedIndex,
            onItemSelected = spec.onItemSelected,
            containerColor = spec.containerColor,
            selectedIconColor = spec.selectedIconColor,
            unselectedIconColor = spec.unselectedIconColor,
            selectedLabelColor = spec.selectedLabelColor,
            unselectedLabelColor = spec.unselectedLabelColor,
            indicatorColor = spec.indicatorColor,
            selectedStateLayerColors = spec.selectedStateLayerColors,
            unselectedStateLayerColors = spec.unselectedStateLayerColors,
            iconSize = spec.iconSize,
            labelSizePx = spec.labelSizePx,
            labelFontWeight = spec.labelFontWeight,
            labelFontFamily = spec.labelFontFamily,
            labelLetterSpacingEm = spec.labelLetterSpacingEm,
            labelLineHeightPx = spec.labelLineHeightPx,
            labelIncludeFontPadding = spec.labelIncludeFontPadding,
            badgeColor = spec.badgeColor,
            badgeTextColor = spec.badgeTextColor,
        )
    }

    fun bindLazyVerticalGrid(
        view: DeclarativeLazyVerticalGridLayout,
        spec: LazyVerticalGridSpec,
        submission: RetainedSessionSubmission = RetainedSessionSubmission.immediate(),
    ) {
        view.applyRecyclerDefaults(
            sharePool = spec.reusePolicy.sharePool,
            disableItemAnimator = spec.motionPolicy.disableItemAnimator,
            animateInsert = spec.motionPolicy.animateInsert,
            animateRemove = spec.motionPolicy.animateRemove,
            animateMove = spec.motionPolicy.animateMove,
            animateChange = spec.motionPolicy.animateChange,
        )
        view.bind(
            cells = spec.cells,
            contentPadding = spec.contentPadding,
            horizontalSpacing = spec.horizontalSpacing,
            verticalSpacing = spec.verticalSpacing,
            items = spec.items,
            state = spec.state,
            reverseLayout = spec.reverseLayout,
            userScrollEnabled = spec.userScrollEnabled,
            prefetchPolicy = spec.prefetchPolicy,
            mountedTreeCacheSize = spec.reusePolicy.mountedTreeCacheSize,
            submission = submission,
        )
    }

    fun readLazyColumnSpec(node: VNode): LazyColumnSpec {
        val spec = node.requireSpec<LazyColumnNodeProps>()
        return LazyColumnSpec(
            contentPadding = node.environment.resolvePadding(spec.contentPadding),
            spacing = node.environment.roundToPx(spec.spacing),
            items = spec.items,
            state = spec.state,
            reverseLayout = spec.reverseLayout,
            userScrollEnabled = spec.userScrollEnabled,
            prefetchPolicy = spec.prefetchPolicy,
            reusePolicy = spec.reusePolicy,
            motionPolicy = spec.motionPolicy,
        )
    }

    fun readLazyRowSpec(node: VNode): LazyColumnSpec {
        val spec = node.requireSpec<LazyRowNodeProps>()
        return LazyColumnSpec(
            contentPadding = node.environment.resolvePadding(spec.contentPadding),
            spacing = node.environment.roundToPx(spec.spacing),
            items = spec.items,
            state = spec.state,
            reverseLayout = spec.reverseLayout,
            userScrollEnabled = spec.userScrollEnabled,
            prefetchPolicy = spec.prefetchPolicy,
            reusePolicy = spec.reusePolicy,
            motionPolicy = spec.motionPolicy,
        )
    }

    fun readNavigationBarSpec(node: VNode): NavigationBarSpec {
        val spec = node.requireSpec<NavigationBarNodeProps>()
        return NavigationBarSpec(
            items = spec.items,
            selectedIndex = spec.selectedIndex,
            onItemSelected = spec.onItemSelected,
            containerColor = spec.containerColor,
            selectedIconColor = spec.selectedIconColor,
            unselectedIconColor = spec.unselectedIconColor,
            selectedLabelColor = spec.selectedLabelColor,
            unselectedLabelColor = spec.unselectedLabelColor,
            indicatorColor = spec.indicatorColor,
            selectedStateLayerColors = spec.selectedStateLayerColors,
            unselectedStateLayerColors = spec.unselectedStateLayerColors,
            iconSize = node.environment.roundToPx(spec.iconSize),
            labelSizePx = node.environment.toPx(spec.labelSizeSp),
            labelFontWeight = spec.labelFontWeight,
            labelFontFamily = spec.labelFontFamily,
            labelLetterSpacingEm = spec.labelLetterSpacingEm,
            labelLineHeightPx = spec.labelLineHeightSp?.let(node.environment.density::roundToPx),
            labelIncludeFontPadding = spec.labelIncludeFontPadding,
            badgeColor = spec.badgeColor,
            badgeTextColor = spec.badgeTextColor,
        )
    }

    fun readLazyVerticalGridSpec(node: VNode): LazyVerticalGridSpec {
        val spec = node.requireSpec<LazyVerticalGridNodeProps>()
        return LazyVerticalGridSpec(
            cells = spec.cells.toPixels(node.environment),
            contentPadding = node.environment.resolvePadding(spec.contentPadding),
            horizontalSpacing = node.environment.roundToPx(spec.horizontalSpacing),
            verticalSpacing = node.environment.roundToPx(spec.verticalSpacing),
            items = spec.items,
            state = spec.state,
            reverseLayout = spec.reverseLayout,
            userScrollEnabled = spec.userScrollEnabled,
            prefetchPolicy = spec.prefetchPolicy,
            reusePolicy = spec.reusePolicy,
            motionPolicy = spec.motionPolicy,
        )
    }

    internal fun GridCells.toPixels(environment: UiEnvironmentValues): LazyGridCellsPx = when (this) {
        is GridCells.Fixed -> LazyGridCellsPx.Fixed(count)
        is GridCells.Adaptive -> LazyGridCellsPx.Adaptive(
            environment.roundToPx(minSize).coerceAtLeast(1),
        )
    }

    internal fun configureLazyListLayout(
        view: RecyclerView,
        reverseLayout: Boolean,
        userScrollEnabled: Boolean,
        prefetchPolicy: LazyLayoutPrefetchPolicy,
    ) {
        (view.layoutManager as? LinearLayoutManager)?.apply {
            this.reverseLayout = reverseLayout
            initialPrefetchItemCount = prefetchPolicy.nestedInitialPrefetchItemCount
        }
        (view as? DeclarativeLazyListView)?.userScrollEnabled = userScrollEnabled
        view.setItemViewCacheSize(prefetchPolicy.itemViewCacheSize)
    }
}
