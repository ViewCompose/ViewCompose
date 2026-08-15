package com.viewcompose.ui.node.spec

/**
 * Immutable renderer properties for a pull-to-refresh container.
 *
 * @property isRefreshing externally controlled indicator state
 * @property onRefresh callback invoked after the native gesture crosses its trigger threshold
 * @property enabled whether the container may intercept the refresh gesture; descendant input
 * remains enabled
 * @property indicatorColor refresh indicator color
 */
data class PullToRefreshNodeProps(
    val isRefreshing: Boolean,
    val onRefresh: (() -> Unit)?,
    val enabled: Boolean = true,
    val indicatorColor: Int,
) : NodeSpec
