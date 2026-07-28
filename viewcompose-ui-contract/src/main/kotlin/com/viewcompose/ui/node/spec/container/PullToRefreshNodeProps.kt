package com.viewcompose.ui.node.spec

/**
 * PullToRefresh 节点的刷新状态和触发回调属性。
 * Refresh state and trigger-callback properties for a PullToRefresh node.
 */
data class PullToRefreshNodeProps(
    val isRefreshing: Boolean,
    val onRefresh: (() -> Unit)?,
    val indicatorColor: Int,
) : NodeSpec
