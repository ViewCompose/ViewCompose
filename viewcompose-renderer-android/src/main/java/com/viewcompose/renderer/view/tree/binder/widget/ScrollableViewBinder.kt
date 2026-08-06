package com.viewcompose.renderer.view.tree

import com.viewcompose.ui.node.VNode
import com.viewcompose.ui.node.spec.ScrollableColumnNodeProps
import com.viewcompose.ui.node.spec.ScrollableRowNodeProps
import com.viewcompose.ui.node.spec.PullToRefreshNodeProps
import com.viewcompose.renderer.view.container.DeclarativeScrollableColumnLayout
import com.viewcompose.renderer.view.container.DeclarativeScrollableRowLayout
import com.viewcompose.renderer.view.lazy.focus.ScrollableFocusFollowLayoutMonitor
import com.viewcompose.renderer.view.roundToPx
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout

/**
 * Binds ordinary scrolling and pull-to-refresh nodes while reusing container binding and keyboard focus-follow policy.
 * Binds scroll containers and pull-to-refresh nodes by reusing container binders and wiring
 * keyboard-follow scrolling policy.
 */
internal object ScrollableViewBinder {
    data class PullToRefreshSpec(
        val isRefreshing: Boolean,
        val onRefresh: (() -> Unit)?,
        val indicatorColor: Int,
    )

    data class ScrollableColumnSpec(
        val linearSpec: ContainerViewBinder.LinearSpec,
        val focusFollowKeyboard: Boolean,
    )

    fun bindScrollableColumn(
        view: DeclarativeScrollableColumnLayout,
        spec: ScrollableColumnSpec,
    ) {
        ContainerViewBinder.bindColumn(view.innerLayout, spec.linearSpec)
        ScrollableFocusFollowLayoutMonitor.apply(
            scrollView = view,
            enabled = spec.focusFollowKeyboard,
        )
    }

    fun bindScrollableRow(
        view: DeclarativeScrollableRowLayout,
        spec: ContainerViewBinder.LinearSpec,
    ) {
        ContainerViewBinder.bindRow(view.innerLayout, spec)
    }

    fun bindPullToRefresh(
        view: SwipeRefreshLayout,
        spec: PullToRefreshSpec,
    ) {
        view.isRefreshing = spec.isRefreshing
        view.setOnRefreshListener { spec.onRefresh?.invoke() }
        view.setColorSchemeColors(spec.indicatorColor)
    }

    fun readScrollableColumnSpec(node: VNode): ScrollableColumnSpec {
        val spec = node.requireSpec<ScrollableColumnNodeProps>()
        return ScrollableColumnSpec(
            linearSpec = ContainerViewBinder.LinearSpec(
                spacing = node.environment.roundToPx(spec.spacing),
                arrangement = spec.arrangement,
                gravity = with(ContainerViewSpecReader) { spec.horizontalAlignment.toGravity() },
            ),
            focusFollowKeyboard = spec.focusFollowKeyboard,
        )
    }

    fun readScrollableRowSpec(node: VNode): ContainerViewBinder.LinearSpec {
        val spec = node.requireSpec<ScrollableRowNodeProps>()
        return ContainerViewBinder.LinearSpec(
            spacing = node.environment.roundToPx(spec.spacing),
            arrangement = spec.arrangement,
            gravity = with(ContainerViewSpecReader) { spec.verticalAlignment.toGravity() },
        )
    }

    fun readPullToRefreshSpec(node: VNode): PullToRefreshSpec {
        val spec = node.requireSpec<PullToRefreshNodeProps>()
        return PullToRefreshSpec(
            isRefreshing = spec.isRefreshing,
            onRefresh = spec.onRefresh,
            indicatorColor = spec.indicatorColor,
        )
    }
}
