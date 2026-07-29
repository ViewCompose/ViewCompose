package com.viewcompose.ui.node.spec

import com.viewcompose.ui.unit.UiDp

/**
 * ProgressIndicator 节点的进度、模式和颜色属性。
 * Progress, mode, and color properties for a ProgressIndicator node.
 */
data class ProgressIndicatorNodeProps(
    val enabled: Boolean,
    val progress: Float?,
    val indicatorColor: Int,
    val trackColor: Int,
    val trackThickness: UiDp,
    val indicatorSize: UiDp,
) : NodeSpec
