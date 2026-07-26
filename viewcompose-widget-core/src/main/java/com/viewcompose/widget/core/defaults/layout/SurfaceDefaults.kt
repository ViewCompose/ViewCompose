package com.viewcompose.widget.core

import com.viewcompose.ui.shape.UiShape

enum class SurfaceVariant {
    Default,
    Variant,
}

object SurfaceDefaults {
    fun backgroundColor(
        variant: SurfaceVariant = SurfaceVariant.Default,
    ): Int {
        return when (variant) {
            SurfaceVariant.Default -> Theme.colors.surface
            SurfaceVariant.Variant -> Theme.colors.surfaceVariant
        }
    }

    fun variantBackgroundColor(): Int = Theme.colors.surfaceVariant

    fun shape(): UiShape = Theme.shapes.medium

    fun contentColor(
        variant: SurfaceVariant = SurfaceVariant.Default,
    ): Int {
        return when (variant) {
            SurfaceVariant.Default -> Theme.colors.onSurface
            SurfaceVariant.Variant -> Theme.colors.onSurfaceVariant
        }
    }

    fun variantContentColor(): Int = TextDefaults.secondaryColor()

    fun pressedColor(): Int = Theme.colors.ripple

    fun disabledAlpha(): Float = 0.72f
}
