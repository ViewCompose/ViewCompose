package com.viewcompose.ui.foundation

import com.viewcompose.ui.shape.UiShape

/** Semantic color variants for surface components. */
enum class SurfaceVariant {
    /** The primary surface and on-surface color pair. */
    Default,
    /** The tonal surface-variant and on-surface-variant color pair. */
    Variant,
}

/** Default visual and interaction tokens for surface components. */
object SurfaceDefaults {
    /** Returns the background color for [variant]. */
    fun backgroundColor(
        variant: SurfaceVariant = SurfaceVariant.Default,
    ): Int {
        return when (variant) {
            SurfaceVariant.Default -> Theme.colors.surface
            SurfaceVariant.Variant -> Theme.colors.surfaceVariant
        }
    }

    /** Returns the tonal variant background color. */
    fun variantBackgroundColor(): Int = Theme.colors.surfaceVariant

    /** Returns the default surface shape. */
    fun shape(): UiShape = Theme.shapes.medium

    /** Returns the content color paired with [variant]. */
    fun contentColor(
        variant: SurfaceVariant = SurfaceVariant.Default,
    ): Int {
        return when (variant) {
            SurfaceVariant.Default -> Theme.colors.onSurface
            SurfaceVariant.Variant -> Theme.colors.onSurfaceVariant
        }
    }

    /** Returns the tonal variant content color. */
    fun variantContentColor(): Int = TextDefaults.secondaryColor()

    /** Returns the interaction feedback color for a pressed surface. */
    fun pressedColor(): Int = Theme.colors.ripple

    /** Returns the alpha applied to disabled surface content. */
    fun disabledAlpha(): Float = 0.72f
}
