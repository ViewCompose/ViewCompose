package com.viewcompose.widget.core

import com.viewcompose.ui.unit.UiDp

import com.viewcompose.ui.shape.UiShape

/**
 * 按按钮视觉层级划分的默认样式变体。
 * Default style variants grouped by button visual hierarchy.
 */
enum class ButtonVariant {
    Primary,
    Secondary,
    Tonal,
    Outlined,
    Text,
}

/**
 * 按交互密度划分的按钮尺寸档位。
 * Button size tiers grouped by interaction density.
 */
enum class ButtonSize {
    Compact,
    Medium,
    Large,
}

/**
 * Button DSL 的默认颜色、尺寸、字体和反馈 token。
 * Default color, size, typography, and feedback tokens for the Button DSL.
 *
 * 颜色优先读取局部 override，再回退到当前 Theme token。
 * Colors read scoped overrides first and then fall back to current Theme tokens.
 */
object ButtonDefaults {
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

    fun borderWidth(
        variant: ButtonVariant = ButtonVariant.Primary,
    ): UiDp {
        return when (variant) {
            ButtonVariant.Outlined -> 1.dp
            else -> 0.dp
        }
    }

    fun shape(): UiShape = Theme.shapes.small

    fun height(
        size: ButtonSize = ButtonSize.Medium,
    ): UiDp {
        return when (size) {
            ButtonSize.Compact -> Theme.controls.button.compactHeight
            ButtonSize.Medium -> Theme.controls.button.mediumHeight
            ButtonSize.Large -> Theme.controls.button.largeHeight
        }
    }

    fun horizontalPadding(
        size: ButtonSize = ButtonSize.Medium,
    ): UiDp {
        return when (size) {
            ButtonSize.Compact -> Theme.controls.button.compactHorizontalPadding
            ButtonSize.Medium -> Theme.controls.button.mediumHorizontalPadding
            ButtonSize.Large -> Theme.controls.button.largeHorizontalPadding
        }
    }

    fun verticalPadding(
        size: ButtonSize = ButtonSize.Medium,
    ): UiDp {
        return when (size) {
            ButtonSize.Compact -> Theme.controls.button.compactVerticalPadding
            ButtonSize.Medium -> Theme.controls.button.mediumVerticalPadding
            ButtonSize.Large -> Theme.controls.button.largeVerticalPadding
        }
    }

    fun textStyle(
        size: ButtonSize = ButtonSize.Medium,
    ): UiTextStyle {
        return when (size) {
            ButtonSize.Compact -> TextDefaults.labelMediumStyle()
            ButtonSize.Medium -> TextDefaults.labelLargeStyle()
            ButtonSize.Large -> TextDefaults.bodyLargeStyle()
        }
    }

    fun iconSize(
        size: ButtonSize = ButtonSize.Medium,
    ): UiDp {
        return when (size) {
            ButtonSize.Compact -> 16.dp
            ButtonSize.Medium -> 18.dp
            ButtonSize.Large -> 20.dp
        }
    }

    fun iconSpacing(
        size: ButtonSize = ButtonSize.Medium,
    ): UiDp {
        return when (size) {
            ButtonSize.Compact -> 6.dp
            ButtonSize.Medium -> 8.dp
            ButtonSize.Large -> 10.dp
        }
    }

    fun pressedColor(): Int = Theme.stateColors.controlHighlight.resolve(pressed = true)
}
