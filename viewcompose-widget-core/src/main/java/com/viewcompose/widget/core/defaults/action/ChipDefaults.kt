package com.viewcompose.widget.core

import com.viewcompose.ui.unit.UiDp

import com.viewcompose.ui.shape.UiShape

/**
 * Chip 的语义变体，影响选中态容器和边框默认值。
 * Semantic chip variants that affect selected container and border defaults.
 */
enum class ChipVariant {
    Assist,
    Filter,
    Input,
    Suggestion,
}

/**
 * Chip DSL 的默认颜色、尺寸和交互反馈 token。
 * Default color, sizing, and interaction feedback tokens for the Chip DSL.
 */
object ChipDefaults {
    fun containerColor(
        variant: ChipVariant = ChipVariant.Assist,
        selected: Boolean = false,
        enabled: Boolean = true,
    ): Int {
        return when {
            !enabled -> Theme.colors.surface
            selected && variant == ChipVariant.Filter -> Theme.colors.surfaceVariant
            else -> 0x00000000 // transparent
        }
    }

    fun contentColor(
        variant: ChipVariant = ChipVariant.Assist,
        selected: Boolean = false,
        enabled: Boolean = true,
    ): Int {
        return when {
            !enabled -> Theme.colors.onSurfaceVariant
            selected -> Theme.colors.primary
            else -> Theme.colors.onSurface
        }
    }

    fun borderColor(
        variant: ChipVariant = ChipVariant.Assist,
        selected: Boolean = false,
        enabled: Boolean = true,
    ): Int {
        return when {
            selected && variant == ChipVariant.Filter -> 0x00000000
            else -> Theme.colors.outline
        }
    }

    fun borderWidth(
        variant: ChipVariant = ChipVariant.Assist,
        selected: Boolean = false,
    ): UiDp {
        return when {
            selected && variant == ChipVariant.Filter -> 0.dp
            else -> 1.dp
        }
    }

    fun shape(): UiShape = Theme.shapes.small

    fun height(): UiDp = Theme.controls.chip.height

    fun horizontalPadding(): UiDp = Theme.controls.chip.horizontalPadding

    fun leadingIconPadding(): UiDp = Theme.controls.chip.leadingIconPadding

    fun iconSize(): UiDp = Theme.controls.chip.iconSize

    fun trailingIconSize(): UiDp = Theme.controls.chip.trailingIconSize

    fun iconSpacing(): UiDp = Theme.controls.chip.iconSpacing

    fun textStyle(): UiTextStyle = TextDefaults.labelMediumStyle()

    fun pressedColor(): Int = Theme.colors.ripple
}
