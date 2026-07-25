package com.viewcompose.renderer.view.tree

import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.viewcompose.renderer.view.lazy.focus.LazyFocusFollowLayoutMonitor
import com.viewcompose.renderer.view.lazy.reuse.FrameworkRecyclerViewDefaults
import com.viewcompose.ui.node.LazyListItem
import com.viewcompose.ui.node.NavigationBarItem
import com.viewcompose.ui.node.VNode
import com.viewcompose.ui.node.policy.CollectionMotionPolicy
import com.viewcompose.ui.node.policy.CollectionReusePolicy
import com.viewcompose.ui.node.spec.LazyColumnNodeProps
import com.viewcompose.ui.node.spec.LazyRowNodeProps
import com.viewcompose.ui.node.spec.LazyVerticalGridNodeProps
import com.viewcompose.ui.node.spec.NavigationBarNodeProps
import com.viewcompose.ui.node.spec.UiFontFamily
import com.viewcompose.renderer.view.container.DeclarativeLazyVerticalGridLayout
import com.viewcompose.renderer.view.container.DeclarativeNavigationBarLayout
import com.viewcompose.ui.state.LazyListState
import com.viewcompose.renderer.view.lazy.adapter.LazyListAdapter
import com.viewcompose.renderer.view.lazy.state.UiLazyListConnector

internal object CollectionViewBinder {
    data class LazyColumnSpec(
        val contentPadding: Int,
        val spacing: Int,
        val items: List<LazyListItem>,
        val state: LazyListState? = null,
        val reusePolicy: CollectionReusePolicy,
        val motionPolicy: CollectionMotionPolicy,
        val focusFollowKeyboard: Boolean,
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
        val rippleColor: Int,
        val iconSize: Int,
        val labelSizeSp: Int,
        val labelFontWeight: Int? = null,
        val labelFontFamily: UiFontFamily? = null,
        val labelLetterSpacingEm: Float? = null,
        val labelLineHeightSp: Int? = null,
        val labelIncludeFontPadding: Boolean = false,
        val badgeColor: Int,
        val badgeTextColor: Int,
    )

    data class LazyVerticalGridSpec(
        val spanCount: Int,
        val contentPadding: Int,
        val horizontalSpacing: Int,
        val verticalSpacing: Int,
        val items: List<LazyListItem>,
        val state: LazyListState?,
        val reusePolicy: CollectionReusePolicy,
        val motionPolicy: CollectionMotionPolicy,
        val focusFollowKeyboard: Boolean,
    )

    fun bindLazyColumn(
        view: RecyclerView,
        spec: LazyColumnSpec,
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
        LazyFocusFollowLayoutMonitor.apply(
            recyclerView = view,
            enabled = spec.focusFollowKeyboard,
        )
        val adapter = view.adapter as? LazyListAdapter ?: LazyListAdapter().also {
            view.adapter = it
        }
        ContainerViewBinder.applyLazyListPadding(view, spec.contentPadding)
        ContainerViewBinder.applyLazyListSpacing(view, spec.spacing, LinearLayoutManager.VERTICAL)
        adapter.submitItems(spec.items)
        spec.state?.attach(
            UiLazyListConnector(
                recyclerView = view,
                mainAxisItemSpacing = spec.spacing,
            ),
        )
    }

    fun bindLazyRow(
        view: RecyclerView,
        spec: LazyColumnSpec,
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
        LazyFocusFollowLayoutMonitor.apply(
            recyclerView = view,
            enabled = false,
        )
        val adapter = view.adapter as? LazyListAdapter
            ?: LazyListAdapter(LinearLayoutManager.HORIZONTAL).also {
                view.adapter = it
            }
        ContainerViewBinder.applyLazyListPadding(view, spec.contentPadding)
        ContainerViewBinder.applyLazyListSpacing(view, spec.spacing, LinearLayoutManager.HORIZONTAL)
        adapter.submitItems(spec.items)
        spec.state?.attach(
            UiLazyListConnector(
                recyclerView = view,
                mainAxisItemSpacing = spec.spacing,
            ),
        )
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
            rippleColor = spec.rippleColor,
            iconSize = spec.iconSize,
            labelSizeSp = spec.labelSizeSp,
            labelFontWeight = spec.labelFontWeight,
            labelFontFamily = spec.labelFontFamily,
            labelLetterSpacingEm = spec.labelLetterSpacingEm,
            labelLineHeightSp = spec.labelLineHeightSp,
            labelIncludeFontPadding = spec.labelIncludeFontPadding,
            badgeColor = spec.badgeColor,
            badgeTextColor = spec.badgeTextColor,
        )
    }

    fun bindLazyVerticalGrid(
        view: DeclarativeLazyVerticalGridLayout,
        spec: LazyVerticalGridSpec,
    ) {
        view.applyRecyclerDefaults(
            sharePool = spec.reusePolicy.sharePool,
            disableItemAnimator = spec.motionPolicy.disableItemAnimator,
            animateInsert = spec.motionPolicy.animateInsert,
            animateRemove = spec.motionPolicy.animateRemove,
            animateMove = spec.motionPolicy.animateMove,
            animateChange = spec.motionPolicy.animateChange,
        )
        view.setFocusFollowKeyboardEnabled(spec.focusFollowKeyboard)
        view.bind(
            spanCount = spec.spanCount,
            contentPadding = spec.contentPadding,
            horizontalSpacing = spec.horizontalSpacing,
            verticalSpacing = spec.verticalSpacing,
            items = spec.items,
            state = spec.state,
        )
    }

    fun readLazyColumnSpec(node: VNode): LazyColumnSpec {
        val spec = node.requireSpec<LazyColumnNodeProps>()
        return LazyColumnSpec(
            contentPadding = spec.contentPadding,
            spacing = spec.spacing,
            items = spec.items,
            state = spec.state,
            reusePolicy = spec.reusePolicy,
            motionPolicy = spec.motionPolicy,
            focusFollowKeyboard = spec.focusFollowKeyboard,
        )
    }

    fun readLazyRowSpec(node: VNode): LazyColumnSpec {
        val spec = node.requireSpec<LazyRowNodeProps>()
        return LazyColumnSpec(
            contentPadding = spec.contentPadding,
            spacing = spec.spacing,
            items = spec.items,
            state = spec.state,
            reusePolicy = spec.reusePolicy,
            motionPolicy = spec.motionPolicy,
            focusFollowKeyboard = false,
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
            rippleColor = spec.rippleColor,
            iconSize = spec.iconSize,
            labelSizeSp = spec.labelSizeSp,
            labelFontWeight = spec.labelFontWeight,
            labelFontFamily = spec.labelFontFamily,
            labelLetterSpacingEm = spec.labelLetterSpacingEm,
            labelLineHeightSp = spec.labelLineHeightSp,
            labelIncludeFontPadding = spec.labelIncludeFontPadding,
            badgeColor = spec.badgeColor,
            badgeTextColor = spec.badgeTextColor,
        )
    }

    fun readLazyVerticalGridSpec(node: VNode): LazyVerticalGridSpec {
        val spec = node.requireSpec<LazyVerticalGridNodeProps>()
        return LazyVerticalGridSpec(
            spanCount = spec.spanCount,
            contentPadding = spec.contentPadding,
            horizontalSpacing = spec.horizontalSpacing,
            verticalSpacing = spec.verticalSpacing,
            items = spec.items,
            state = spec.state,
            reusePolicy = spec.reusePolicy,
            motionPolicy = spec.motionPolicy,
            focusFollowKeyboard = spec.focusFollowKeyboard,
        )
    }
}
