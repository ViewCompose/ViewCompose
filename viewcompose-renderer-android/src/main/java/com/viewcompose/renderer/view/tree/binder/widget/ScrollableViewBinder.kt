package com.viewcompose.renderer.view.tree

import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.viewcompose.renderer.R
import com.viewcompose.renderer.view.container.DeclarativeScrollableColumnLayout
import com.viewcompose.renderer.view.container.DeclarativeScrollableRowLayout
import com.viewcompose.renderer.view.lazy.focus.ScrollableFocusFollowLayoutMonitor
import com.viewcompose.renderer.view.roundToPx
import com.viewcompose.ui.node.VNode
import com.viewcompose.ui.node.spec.PullToRefreshNodeProps
import com.viewcompose.ui.node.spec.ScrollableColumnNodeProps
import com.viewcompose.ui.node.spec.ScrollableRowNodeProps

/**
 * Binds scroll containers and pull-to-refresh nodes by reusing container binders and wiring
 * keyboard-follow scrolling policy.
 */
internal object ScrollableViewBinder {
    data class PullToRefreshSpec(
        val isRefreshing: Boolean,
        val onRefresh: (() -> Unit)?,
        val enabled: Boolean,
        val indicatorColor: Int,
    )

    data class ScrollableColumnSpec(
        val linearSpec: ContainerViewBinder.LinearSpec,
        val state: com.viewcompose.ui.state.ScrollState?,
        val userScrollEnabled: Boolean,
        val focusFollowKeyboard: Boolean,
    )

    data class ScrollableRowSpec(
        val linearSpec: ContainerViewBinder.LinearSpec,
        val state: com.viewcompose.ui.state.ScrollState?,
        val userScrollEnabled: Boolean,
    )

    fun bindScrollableColumn(
        view: DeclarativeScrollableColumnLayout,
        spec: ScrollableColumnSpec,
    ) {
        ContainerViewBinder.bindColumn(view.innerLayout, spec.linearSpec)
        view.bindScrollState(spec.state, spec.userScrollEnabled)
        ScrollableFocusFollowLayoutMonitor.apply(
            scrollView = view,
            enabled = spec.focusFollowKeyboard,
        )
    }

    fun bindScrollableRow(
        view: DeclarativeScrollableRowLayout,
        spec: ScrollableRowSpec,
    ) {
        ContainerViewBinder.bindRow(view.innerLayout, spec.linearSpec)
        view.bindScrollState(spec.state, spec.userScrollEnabled)
    }

    fun bindPullToRefresh(
        view: SwipeRefreshLayout,
        spec: PullToRefreshSpec,
    ) {
        view.isRefreshing = spec.isRefreshing
        view.isEnabled = spec.enabled
        val listener = (view.getTag(R.id.viewcompose_pull_refresh_listener) as? PullRefreshListenerBinding)
            ?: PullRefreshListenerBinding().also { binding ->
                view.setTag(R.id.viewcompose_pull_refresh_listener, binding)
                view.setOnRefreshListener(binding)
            }
        listener.enabled = spec.enabled
        listener.onRefresh = spec.onRefresh
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
            state = spec.state,
            userScrollEnabled = spec.userScrollEnabled,
            focusFollowKeyboard = spec.focusFollowKeyboard,
        )
    }

    fun readScrollableRowSpec(node: VNode): ScrollableRowSpec {
        val spec = node.requireSpec<ScrollableRowNodeProps>()
        return ScrollableRowSpec(
            linearSpec = ContainerViewBinder.LinearSpec(
                spacing = node.environment.roundToPx(spec.spacing),
                arrangement = spec.arrangement,
                gravity = with(ContainerViewSpecReader) { spec.verticalAlignment.toGravity() },
            ),
            state = spec.state,
            userScrollEnabled = spec.userScrollEnabled,
        )
    }

    fun readPullToRefreshSpec(node: VNode): PullToRefreshSpec {
        val spec = node.requireSpec<PullToRefreshNodeProps>()
        return PullToRefreshSpec(
            isRefreshing = spec.isRefreshing,
            onRefresh = spec.onRefresh,
            enabled = spec.enabled,
            indicatorColor = spec.indicatorColor,
        )
    }
}

private class PullRefreshListenerBinding : SwipeRefreshLayout.OnRefreshListener {
    var enabled: Boolean = true
    var onRefresh: (() -> Unit)? = null

    override fun onRefresh() {
        if (enabled) onRefresh?.invoke()
    }
}
