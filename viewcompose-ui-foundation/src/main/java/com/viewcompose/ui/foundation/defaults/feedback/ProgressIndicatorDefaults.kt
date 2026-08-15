package com.viewcompose.ui.foundation

import com.viewcompose.ui.unit.UiDp

/** Resolves linear and circular progress appearance from theme tokens and independent overrides. */
object ProgressIndicatorDefaults {
    /** Returns the active linear-indicator color. */
    fun linearIndicatorColor(): Int =
        linearOverrides().indicatorColor ?: Theme.colors.primary

    /** Returns the inactive linear-track color. */
    fun linearTrackColor(): Int =
        linearOverrides().trackColor ?: Theme.colors.secondaryContainer

    /** Returns linear indicator and track thickness. */
    fun linearTrackThickness(): UiDp = linearOverrides().trackThickness
        ?: Theme.controls.progressIndicator.linearTrackThickness

    /** Returns the active circular-indicator color. */
    fun circularIndicatorColor(): Int =
        circularOverrides().indicatorColor ?: Theme.colors.primary

    /** Returns the inactive circular-track color. */
    fun circularTrackColor(): Int =
        circularOverrides().trackColor ?: Theme.colors.secondaryContainer

    /** Returns circular indicator square bounds. */
    fun circularSize(): UiDp = circularOverrides().size
        ?: Theme.controls.progressIndicator.circularSize

    /** Returns circular indicator and track stroke thickness. */
    fun circularTrackThickness(): UiDp = circularOverrides().trackThickness
        ?: Theme.controls.progressIndicator.circularTrackThickness

    internal fun resolveLinear(instance: LinearProgressIndicatorOverrides): ResolvedLinearProgressAppearance {
        val overrides = linearOverrides().merge(instance)
        return ResolvedLinearProgressAppearance(
            indicatorColor = overrides.indicatorColor ?: Theme.colors.primary,
            trackColor = overrides.trackColor ?: Theme.colors.secondaryContainer,
            trackThickness = overrides.trackThickness
                ?: Theme.controls.progressIndicator.linearTrackThickness,
        )
    }

    internal fun resolveCircular(instance: CircularProgressIndicatorOverrides): ResolvedCircularProgressAppearance {
        val overrides = circularOverrides().merge(instance)
        return ResolvedCircularProgressAppearance(
            indicatorColor = overrides.indicatorColor ?: Theme.colors.primary,
            trackColor = overrides.trackColor ?: Theme.colors.secondaryContainer,
            size = overrides.size ?: Theme.controls.progressIndicator.circularSize,
            trackThickness = overrides.trackThickness
                ?: Theme.controls.progressIndicator.circularTrackThickness,
        )
    }

    private fun linearOverrides() = UiLocals.current(LocalLinearProgressIndicatorOverrides)
    private fun circularOverrides() = UiLocals.current(LocalCircularProgressIndicatorOverrides)
}

internal data class ResolvedLinearProgressAppearance(
    val indicatorColor: Int,
    val trackColor: Int,
    val trackThickness: UiDp,
)

internal data class ResolvedCircularProgressAppearance(
    val indicatorColor: Int,
    val trackColor: Int,
    val size: UiDp,
    val trackThickness: UiDp,
)
