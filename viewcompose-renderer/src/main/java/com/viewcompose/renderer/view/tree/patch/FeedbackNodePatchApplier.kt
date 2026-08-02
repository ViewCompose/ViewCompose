package com.viewcompose.renderer.view.tree.patch

import android.content.res.ColorStateList
import android.widget.ProgressBar
import com.google.android.material.progressindicator.BaseProgressIndicator
import com.google.android.material.progressindicator.CircularProgressIndicator
import com.viewcompose.renderer.view.tree.ProgressIndicatorNodePatch
import com.viewcompose.renderer.view.requireUiEnvironment
import com.viewcompose.renderer.view.roundToPx
import kotlin.math.roundToInt

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
        view: ProgressBar,
        patch: ProgressIndicatorNodePatch,
    ) {
        val previous = patch.previous
        val next = patch.next
        val environment = view.requireUiEnvironment()
        if (previous.enabled != next.enabled) {
            view.isEnabled = next.enabled
        }
        if (previous.progress != next.progress) {
            view.isIndeterminate = next.progress == null
        }
        if (previous.indicatorColor != next.indicatorColor) {
            val tint = ColorStateList.valueOf(next.indicatorColor)
            view.progressTintList = tint
            view.indeterminateTintList = tint
        }
        if (view is BaseProgressIndicator<*>) {
            if (previous.trackColor != next.trackColor) {
                view.trackColor = next.trackColor
            }
            if (previous.trackThickness != next.trackThickness) {
                view.trackThickness = environment.roundToPx(next.trackThickness)
            }
            if (previous.indicatorColor != next.indicatorColor) {
                view.setIndicatorColor(next.indicatorColor)
            }
        } else {
            if (previous.trackColor != next.trackColor) {
                view.progressBackgroundTintList = ColorStateList.valueOf(next.trackColor)
            }
        }
        if (view is CircularProgressIndicator) {
            if (previous.indicatorSize != next.indicatorSize) {
                view.indicatorSize = environment.roundToPx(next.indicatorSize)
            }
        }
        val nextProgress = next.progress
        if (nextProgress != null && previous.progress != nextProgress) {
            // Use an internal max of 10000 to retain precision when mapping Float progress to platform Int progress.
            // Use 10000 as internal max to preserve Float progress precision when mapping to platform Int progress.
            view.max = 10_000
            view.progress = (nextProgress.coerceIn(0f, 1f) * 10_000f).roundToInt()
        }
    }
}
