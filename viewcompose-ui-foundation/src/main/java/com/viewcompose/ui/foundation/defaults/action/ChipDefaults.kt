package com.viewcompose.ui.foundation

import com.viewcompose.ui.unit.UiDp

import com.viewcompose.ui.shape.UiShape

/** Semantic chip role used to select selected-container and border treatment. */
enum class ChipVariant {
    Assist,
    Filter,
    Input,
    Suggestion,
}

/** Resolves Chip colors, dimensions, typography, and feedback from the current theme. */
object ChipDefaults {
    /** Resolves the container color for [variant], [selected], and [enabled] state. */
    fun containerColor(
        variant: ChipVariant = ChipVariant.Assist,
        selected: Boolean = false,
        enabled: Boolean = true,
    ): Int {
        return when {
            !enabled -> colorWithAlpha(Theme.colors.onSurface, 0.12f)
            selected && variant == ChipVariant.Filter -> Theme.colors.secondaryContainer
            else -> 0x00000000 // transparent
        }
    }

    /** Resolves label and icon content color for the complete chip state. */
    fun contentColor(
        variant: ChipVariant = ChipVariant.Assist,
        selected: Boolean = false,
        enabled: Boolean = true,
    ): Int {
        return when {
            !enabled -> colorWithAlpha(Theme.colors.onSurface, 0.38f)
            selected -> Theme.colors.onSecondaryContainer
            else -> Theme.colors.onSurfaceVariant
        }
    }

    /** Resolves border color for the complete chip state. */
    fun borderColor(
        variant: ChipVariant = ChipVariant.Assist,
        selected: Boolean = false,
        enabled: Boolean = true,
    ): Int {
        return when {
            selected && variant == ChipVariant.Filter -> 0x00000000
            !enabled -> colorWithAlpha(Theme.colors.onSurface, 0.12f)
            else -> Theme.colors.outline
        }
    }

    /** Resolves zero width for a selected filter chip and one dp otherwise. */
    fun borderWidth(
        variant: ChipVariant = ChipVariant.Assist,
        selected: Boolean = false,
    ): UiDp {
        return when {
            selected && variant == ChipVariant.Filter -> 0.dp
            else -> 1.dp
        }
    }

    /** Returns the current small theme shape. */
    fun shape(): UiShape = Theme.shapes.small

    /** Returns the current chip height token. */
    fun height(): UiDp = Theme.controls.chip.height

    /** Returns the current default horizontal content padding. */
    fun horizontalPadding(): UiDp = Theme.controls.chip.horizontalPadding

    /** Returns leading-edge padding used when a leading icon is present. */
    fun leadingIconPadding(): UiDp = Theme.controls.chip.leadingIconPadding

    /** Returns the current leading icon size. */
    fun iconSize(): UiDp = Theme.controls.chip.iconSize

    /** Returns the current trailing icon size. */
    fun trailingIconSize(): UiDp = Theme.controls.chip.trailingIconSize

    /** Returns spacing between an icon and the label. */
    fun iconSpacing(): UiDp = Theme.controls.chip.iconSpacing

    /** Returns the medium label typography style. */
    fun textStyle(): UiTextStyle = TextDefaults.labelMediumStyle()

}
