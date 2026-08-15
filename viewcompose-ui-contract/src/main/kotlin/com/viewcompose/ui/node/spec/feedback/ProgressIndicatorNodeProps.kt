package com.viewcompose.ui.node.spec

import com.viewcompose.ui.unit.UiDp

/**
 * Immutable renderer properties for linear or circular progress indicators.
 *
 * @property progress determinate progress value, or `null` for indeterminate mode
 * @property indicatorColor active indicator color
 * @property trackColor inactive track color
 * @property trackThickness requested track thickness
 * @property indicatorSize requested circular indicator width and height
 */
data class ProgressIndicatorNodeProps(
    val progress: Float?,
    val indicatorColor: Int,
    val trackColor: Int,
    val trackThickness: UiDp,
    val indicatorSize: UiDp,
) : NodeSpec
