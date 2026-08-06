package com.viewcompose.ui.foundation

import com.viewcompose.ui.unit.UiDp

import com.viewcompose.ui.shape.UiShape

/** Visual hierarchy used to select Button colors, border, and container treatment. */
enum class ButtonVariant {
    Primary,
    Secondary,
    Tonal,
    Outlined,
    Text,
}

/** Interaction-density tier used to select Button dimensions and typography. */
enum class ButtonSize {
    Compact,
    Medium,
    Large,
}

/**
 * Resolves Button colors, dimensions, typography, and feedback from the current theme.
 *
 * Scoped `ButtonColorOverride` values take precedence over theme-derived colors.
 */
object ButtonDefaults {
    /** Resolves the container color for [variant] and [enabled] state. */
    fun containerColor(
        variant: ButtonVariant = ButtonVariant.Primary,
        enabled: Boolean = true,
    ): Int {
        val override = UiLocals.current(LocalButtonColors)
        return when (variant) {
            ButtonVariant.Primary -> if (enabled) {
                override?.primaryContainer ?: Theme.colors.primary
            } else {
                override?.primaryDisabledContainer ?: Theme.colors.outlineVariant
            }

            ButtonVariant.Secondary -> if (enabled) {
                override?.secondaryContainer ?: Theme.colors.secondary
            } else {
                override?.secondaryDisabledContainer ?: Theme.colors.outlineVariant
            }

            ButtonVariant.Tonal -> if (enabled) {
                override?.tonalContainer ?: Theme.colors.secondaryContainer
            } else {
                override?.tonalDisabledContainer ?: Theme.colors.surfaceVariant
            }

            ButtonVariant.Outlined -> 0x00000000

            ButtonVariant.Text -> 0x00000000
        }
    }

    /** Resolves label and icon content color for [variant] and [enabled] state. */
    fun contentColor(
        variant: ButtonVariant = ButtonVariant.Primary,
        enabled: Boolean = true,
    ): Int {
        val override = UiLocals.current(LocalButtonColors)
        return when (variant) {
            ButtonVariant.Primary -> if (enabled) {
                override?.primaryContent ?: Theme.colors.onPrimary
            } else {
                override?.primaryDisabledContent ?: Theme.stateColors.primaryText.resolve(enabled = false)
            }

            ButtonVariant.Secondary -> if (enabled) {
                override?.secondaryContent ?: Theme.colors.onSecondary
            } else {
                override?.secondaryDisabledContent ?: Theme.stateColors.primaryText.resolve(enabled = false)
            }

            ButtonVariant.Tonal -> if (enabled) {
                override?.tonalContent ?: Theme.colors.onSecondaryContainer
            } else {
                override?.tonalDisabledContent ?: Theme.stateColors.primaryText.resolve(enabled = false)
            }

            ButtonVariant.Outlined -> if (enabled) {
                override?.outlinedContent ?: Theme.stateColors.primaryText.resolve()
            } else {
                override?.outlinedDisabledContent ?: Theme.stateColors.primaryText.resolve(enabled = false)
            }

            ButtonVariant.Text -> if (enabled) {
                Theme.colors.primary
            } else {
                Theme.stateColors.primaryText.resolve(enabled = false)
            }
        }
    }

    /** Resolves the outlined border color, or transparency for non-outlined variants. */
    fun borderColor(
        variant: ButtonVariant = ButtonVariant.Primary,
        enabled: Boolean = true,
    ): Int {
        val override = UiLocals.current(LocalButtonColors)
        return when (variant) {
            ButtonVariant.Outlined -> if (enabled) {
                override?.outlinedBorder ?: Theme.colors.outline
            } else {
                override?.outlinedDisabledBorder ?: Theme.colors.outlineVariant
            }

            else -> 0x00000000
        }
    }

    /** Resolves a one-dp outlined border or zero width for other variants. */
    fun borderWidth(
        variant: ButtonVariant = ButtonVariant.Primary,
    ): UiDp {
        return when (variant) {
            ButtonVariant.Outlined -> 1.dp
            else -> 0.dp
        }
    }

    /** Returns the current small theme shape. */
    fun shape(): UiShape = Theme.shapes.small

    /** Resolves the minimum height for [size]. */
    fun height(
        size: ButtonSize = ButtonSize.Medium,
    ): UiDp {
        return when (size) {
            ButtonSize.Compact -> Theme.controls.button.compactHeight
            ButtonSize.Medium -> Theme.controls.button.mediumHeight
            ButtonSize.Large -> Theme.controls.button.largeHeight
        }
    }

    /** Resolves start and end content padding for [size]. */
    fun horizontalPadding(
        size: ButtonSize = ButtonSize.Medium,
    ): UiDp {
        return when (size) {
            ButtonSize.Compact -> Theme.controls.button.compactHorizontalPadding
            ButtonSize.Medium -> Theme.controls.button.mediumHorizontalPadding
            ButtonSize.Large -> Theme.controls.button.largeHorizontalPadding
        }
    }

    /** Resolves top and bottom content padding for [size]. */
    fun verticalPadding(
        size: ButtonSize = ButtonSize.Medium,
    ): UiDp {
        return when (size) {
            ButtonSize.Compact -> Theme.controls.button.compactVerticalPadding
            ButtonSize.Medium -> Theme.controls.button.mediumVerticalPadding
            ButtonSize.Large -> Theme.controls.button.largeVerticalPadding
        }
    }

    /** Resolves the label typography tier for [size]. */
    fun textStyle(
        size: ButtonSize = ButtonSize.Medium,
    ): UiTextStyle {
        return when (size) {
            ButtonSize.Compact -> TextDefaults.labelMediumStyle()
            ButtonSize.Medium -> TextDefaults.labelLargeStyle()
            ButtonSize.Large -> TextDefaults.bodyLargeStyle()
        }
    }

    /** Resolves leading and trailing icon size for [size]. */
    fun iconSize(
        size: ButtonSize = ButtonSize.Medium,
    ): UiDp {
        return when (size) {
            ButtonSize.Compact -> 16.dp
            ButtonSize.Medium -> 18.dp
            ButtonSize.Large -> 20.dp
        }
    }

    /** Resolves spacing between an icon and label for [size]. */
    fun iconSpacing(
        size: ButtonSize = ButtonSize.Medium,
    ): UiDp {
        return when (size) {
            ButtonSize.Compact -> 6.dp
            ButtonSize.Medium -> 8.dp
            ButtonSize.Large -> 10.dp
        }
    }

    /** Resolves the current pressed-state control highlight. */
    fun pressedColor(): Int = Theme.stateColors.controlHighlight.resolve(pressed = true)
}
