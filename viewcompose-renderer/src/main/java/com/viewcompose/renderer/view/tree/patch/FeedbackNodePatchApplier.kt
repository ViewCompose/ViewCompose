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
 * 反馈类节点的细粒度 patch 应用器。
 * Fine-grained patch applier for feedback nodes.
 */
internal object FeedbackNodePatchApplier {
    /**
     * 更新线性/圆形进度指示器的模式、颜色、轨道和进度值。
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
            // 使用 10000 作为内部 max，保留 Float progress 到平台 Int progress 的精度。
            // Use 10000 as internal max to preserve Float progress precision when mapping to platform Int progress.
            view.max = 10_000
            view.progress = (nextProgress.coerceIn(0f, 1f) * 10_000f).roundToInt()
        }
    }
}
