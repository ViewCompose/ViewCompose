package com.viewcompose.renderer.view.tree.patch

import com.viewcompose.renderer.view.feedback.DeclarativeProgressIndicatorView
import com.viewcompose.renderer.view.requireUiEnvironment
import com.viewcompose.renderer.view.roundToPx
import com.viewcompose.renderer.view.tree.ProgressIndicatorNodePatch

/**
 * Targeted patch applier for feedback nodes.
 * Fine-grained patch applier for feedback nodes.
 */
internal object FeedbackNodePatchApplier {
    /**
     * Updates linear or circular progress mode, colors, track, and progress value.
     * Updates mode, colors, track, and progress value for linear/circular progress indicators.
     */
    fun applyProgressIndicatorPatch(
        view: DeclarativeProgressIndicatorView,
        patch: ProgressIndicatorNodePatch,
    ) {
        val next = patch.next
        val environment = view.requireUiEnvironment()
        view.bind(
            enabled = next.enabled,
            progress = next.progress,
            indicatorColor = next.indicatorColor,
            trackColor = next.trackColor,
            trackThickness = environment.roundToPx(next.trackThickness),
            indicatorSize = environment.roundToPx(next.indicatorSize),
        )
    }
}
