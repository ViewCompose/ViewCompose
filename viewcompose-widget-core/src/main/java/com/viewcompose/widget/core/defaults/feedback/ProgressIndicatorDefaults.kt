package com.viewcompose.widget.core

import com.viewcompose.ui.unit.UiDp

/**
 * Resolves default colors and dimensions for linear and circular progress indicators.
 *
 * Color methods honor the nearest [ProvideProgressIndicatorColors] provider. Dimensions always resolve
 * from the current theme.
 */
object ProgressIndicatorDefaults {
    /** Returns the active linear-indicator color. */
    fun linearIndicatorColor(): Int {
        val override = UiLocals.current(LocalProgressIndicatorColors)
        return override?.linearIndicator ?: Theme.colors.primary
    }

    /** Returns the inactive linear-track color. */
    fun linearTrackColor(): Int {
        val override = UiLocals.current(LocalProgressIndicatorColors)
        return override?.linearTrack ?: Theme.colors.outlineVariant
    }

    /** Returns the thickness of a linear indicator and its track. */
    fun linearTrackThickness(): UiDp = Theme.controls.progressIndicator.linearTrackThickness

    /** Returns the active circular-indicator color. */
    fun circularIndicatorColor(): Int {
        val override = UiLocals.current(LocalProgressIndicatorColors)
        return override?.circularIndicator ?: Theme.colors.primary
    }

    /** Returns the inactive circular-track color. */
    fun circularTrackColor(): Int {
        val override = UiLocals.current(LocalProgressIndicatorColors)
        return override?.circularTrack ?: Theme.colors.outlineVariant
    }

    /** Returns the default square bounds of a circular indicator. */
    fun circularSize(): UiDp = Theme.controls.progressIndicator.circularSize

    /** Returns the stroke thickness of a circular indicator and its track. */
    fun circularTrackThickness(): UiDp = Theme.controls.progressIndicator.circularTrackThickness
}
