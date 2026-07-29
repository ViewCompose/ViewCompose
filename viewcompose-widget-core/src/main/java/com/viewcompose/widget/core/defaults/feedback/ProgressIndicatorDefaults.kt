package com.viewcompose.widget.core

import com.viewcompose.ui.unit.UiDp

/**
 * 线性和圆形进度指示器的默认颜色与尺寸 token。
 * Default color and sizing tokens for linear and circular progress indicators.
 *
 * 颜色支持局部 override；尺寸始终从当前 Theme.controls.progressIndicator 读取。
 * Colors support scoped overrides; sizing is always read from current Theme.controls.progressIndicator.
 */
object ProgressIndicatorDefaults {
    fun linearIndicatorColor(): Int {
        val override = UiLocals.current(LocalProgressIndicatorColors)
        return override?.linearIndicator ?: Theme.colors.primary
    }

    fun linearTrackColor(): Int {
        val override = UiLocals.current(LocalProgressIndicatorColors)
        return override?.linearTrack ?: Theme.colors.outlineVariant
    }

    fun linearTrackThickness(): UiDp = Theme.controls.progressIndicator.linearTrackThickness

    fun circularIndicatorColor(): Int {
        val override = UiLocals.current(LocalProgressIndicatorColors)
        return override?.circularIndicator ?: Theme.colors.primary
    }

    fun circularTrackColor(): Int {
        val override = UiLocals.current(LocalProgressIndicatorColors)
        return override?.circularTrack ?: Theme.colors.outlineVariant
    }

    fun circularSize(): UiDp = Theme.controls.progressIndicator.circularSize

    fun circularTrackThickness(): UiDp = Theme.controls.progressIndicator.circularTrackThickness
}
