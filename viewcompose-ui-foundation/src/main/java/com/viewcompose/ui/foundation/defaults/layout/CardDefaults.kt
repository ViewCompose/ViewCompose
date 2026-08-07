package com.viewcompose.ui.foundation

import com.viewcompose.ui.unit.UiDp

import com.viewcompose.ui.shape.UiShape

/** Visual hierarchy variants for card components. */
enum class CardVariant {
    /** A tonal card without elevation or an outline. */
    Filled,
    /** A card distinguished from its background by elevation. */
    Elevated,
    /** A card distinguished from its background by an outline. */
    Outlined,
}

/** Default visual and interaction tokens for card components. */
object CardDefaults {
    /** Returns the container color for [variant]. */
    fun containerColor(
        variant: CardVariant = CardVariant.Filled,
    ): Int {
        return when (variant) {
            CardVariant.Filled -> Theme.colors.surfaceContainerHighest
            CardVariant.Elevated -> Theme.colors.surfaceContainerLow
            CardVariant.Outlined -> Theme.colors.surface
        }
    }

    /** Returns the default content color inside a card. */
    fun contentColor(): Int = Theme.colors.onSurface

    /** Returns the card container shape. */
    fun shape(): UiShape = Theme.shapes.medium

    /** Returns the elevation for [variant]. */
    fun elevation(
        variant: CardVariant = CardVariant.Filled,
    ): UiDp {
        return when (variant) {
            CardVariant.Elevated -> 1.dp
            else -> 0.dp
        }
    }

    /** Returns the outline width for [variant], or zero when no outline is drawn. */
    fun borderWidth(
        variant: CardVariant = CardVariant.Filled,
    ): UiDp {
        return when (variant) {
            CardVariant.Outlined -> 1.dp
            else -> 0.dp
        }
    }

    /** Returns the outline color for [variant], or transparent when no outline is drawn. */
    fun borderColor(
        variant: CardVariant = CardVariant.Filled,
    ): Int {
        return when (variant) {
            CardVariant.Outlined -> Theme.colors.outlineVariant
            else -> 0x00000000
        }
    }

    /** Returns the interaction feedback color for a pressed card. */
    fun pressedColor(): Int = Theme.colors.ripple
}
