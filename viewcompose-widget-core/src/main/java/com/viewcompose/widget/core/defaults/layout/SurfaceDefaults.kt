package com.viewcompose.widget.core

import com.viewcompose.ui.shape.UiShape

/**
 * Surface 的背景/内容色变体。
 * Background/content color variants for Surface.
 */
enum class SurfaceVariant {
    Default,
    Variant,
}

/**
 * Surface DSL 的默认背景、内容色、形状和交互反馈 token。
 * Default background, content color, shape, and interaction feedback tokens for the Surface DSL.
 */
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
