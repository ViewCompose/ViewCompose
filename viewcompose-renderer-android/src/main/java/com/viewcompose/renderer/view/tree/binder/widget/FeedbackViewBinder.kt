package com.viewcompose.renderer.view.tree

import com.viewcompose.renderer.view.feedback.DeclarativeProgressIndicatorView
import com.viewcompose.ui.node.VNode
import com.viewcompose.ui.node.spec.ProgressIndicatorNodeProps
import com.viewcompose.renderer.view.roundToPx

/**
 * Binds progress-feedback nodes and centralizes determinate state, indeterminate state, colors, and visibility.
 * Binds progress feedback nodes and normalizes determinate/indeterminate progress, color, and
 * visibility state.
 */
internal object FeedbackViewBinder {
    data class ProgressSpec(
        val enabled: Boolean,
        val progress: Float?,
        val indicatorColor: Int,
        val trackColor: Int,
        val trackThickness: Int,
        val indicatorSize: Int,
    )

    fun bindProgressIndicator(
        view: DeclarativeProgressIndicatorView,
        spec: ProgressSpec,
    ) {
        view.bind(
            enabled = spec.enabled,
            progress = spec.progress,
            indicatorColor = spec.indicatorColor,
            trackColor = spec.trackColor,
            trackThickness = spec.trackThickness,
            indicatorSize = spec.indicatorSize,
        )
    }

    fun readProgressSpec(node: VNode): ProgressSpec {
        val spec = node.requireSpec<ProgressIndicatorNodeProps>()
        return ProgressSpec(
            enabled = spec.enabled,
            progress = spec.progress,
            indicatorColor = spec.indicatorColor,
            trackColor = spec.trackColor,
            trackThickness = node.environment.roundToPx(spec.trackThickness),
            indicatorSize = node.environment.roundToPx(spec.indicatorSize),
        )
    }
}
