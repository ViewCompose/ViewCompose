package com.viewcompose.ui.foundation

import com.viewcompose.ui.unit.UiDp

import com.viewcompose.ui.shape.UiShape

/** Visual treatment used to select TextField container and border defaults. */
enum class TextFieldVariant {
    Filled,
    Tonal,
    Outlined,
}

/** Interaction-density tier used to select TextField dimensions and typography. */
enum class TextFieldSize {
    Compact,
    Medium,
    Large,
}

/**
 * Resolves TextField colors, typography, shape, and dimensions from the current theme.
 *
 * Scoped `TextFieldColorOverride` values replace container and border slots only.
 */
object TextFieldDefaults {
    /** Resolves editable text typography for [size]. */
    fun textStyle(
        size: TextFieldSize = TextFieldSize.Medium,
    ): UiTextStyle {
        return when (size) {
            TextFieldSize.Compact -> TextDefaults.labelSmallStyle()
            TextFieldSize.Medium -> TextDefaults.bodyLargeStyle()
            TextFieldSize.Large -> TextDefaults.bodyLargeStyle()
        }
    }

    /** Resolves editable text color without replacing content color in the error state. */
    fun textColor(
        enabled: Boolean = true,
        isError: Boolean = false,
    ): Int {
        return when {
            enabled -> Theme.stateColors.primaryText.resolve()
            else -> colorWithAlpha(Theme.colors.onSurface, 0.38f)
        }
    }

    /** Resolves placeholder color without replacing content color in the error state. */
    fun hintColor(
        enabled: Boolean = true,
        isError: Boolean = false,
    ): Int {
        return when {
            enabled -> Theme.stateColors.secondaryText.resolve()
            else -> colorWithAlpha(Theme.colors.onSurface, 0.38f)
        }
    }

    /** Resolves label color using placeholder color semantics. */
    fun labelColor(
        enabled: Boolean = true,
        isError: Boolean = false,
    ): Int {
        return when {
            isError -> Theme.colors.error
            enabled -> Theme.colors.onSurfaceVariant
            else -> colorWithAlpha(Theme.colors.onSurface, 0.38f)
        }
    }

    /** Resolves supporting text color using placeholder color semantics. */
    fun supportingTextColor(
        enabled: Boolean = true,
        isError: Boolean = false,
    ): Int = labelColor(enabled = enabled, isError = isError)

    /** Returns small body typography. */
    fun labelTextStyle(): UiTextStyle = TextDefaults.bodySmallStyle()

    /** Returns small body typography for supporting text. */
    fun supportingTextStyle(): UiTextStyle = TextDefaults.bodySmallStyle()

    /** Resolves container color for [variant], [enabled], and [isError] state. */
    fun containerColor(
        variant: TextFieldVariant = TextFieldVariant.Filled,
        enabled: Boolean = true,
        isError: Boolean = false,
    ): Int {
        val override = UiLocals.current(LocalTextFieldColors)
        return when {
            variant == TextFieldVariant.Outlined -> 0x00000000
            variant == TextFieldVariant.Tonal && enabled ->
                override?.tonalContainer ?: Theme.colors.surfaceContainerHigh

            variant == TextFieldVariant.Tonal ->
                override?.tonalDisabledContainer ?: Theme.colors.surfaceContainerHighest

            enabled ->
                override?.filledContainer ?: Theme.colors.surfaceContainerHighest

            else ->
                override?.filledDisabledContainer ?: Theme.colors.surfaceContainerHighest
        }
    }

    /** Resolves border color for [variant], [enabled], and [isError] state. */
    fun borderColor(
        variant: TextFieldVariant = TextFieldVariant.Filled,
        enabled: Boolean = true,
        isError: Boolean = false,
    ): Int {
        val override = UiLocals.current(LocalTextFieldColors)
        return when {
            isError ->
                override?.outlinedErrorBorder ?: Theme.colors.error

            variant == TextFieldVariant.Outlined && enabled ->
                override?.outlinedBorder ?: Theme.colors.outline

            variant == TextFieldVariant.Outlined ->
                override?.outlinedDisabledBorder ?: colorWithAlpha(Theme.colors.onSurface, 0.12f)

            else -> 0x00000000
        }
    }

    /** Resolves one-dp width for outlined fields and zero for other variants. */
    fun borderWidth(
        variant: TextFieldVariant = TextFieldVariant.Filled,
    ): UiDp {
        return when (variant) {
            TextFieldVariant.Outlined -> 1.dp
            else -> 0.dp
        }
    }

    /** Returns the current extra-small theme shape. */
    fun shape(): UiShape = Theme.shapes.extraSmall

    /** Resolves minimum field height for [size]. */
    fun height(
        size: TextFieldSize = TextFieldSize.Medium,
    ): UiDp {
        return when (size) {
            TextFieldSize.Compact -> Theme.controls.textField.compactHeight
            TextFieldSize.Medium -> Theme.controls.textField.mediumHeight
            TextFieldSize.Large -> Theme.controls.textField.largeHeight
        }
    }

    /** Resolves start and end content padding for [size]. */
    fun horizontalPadding(
        size: TextFieldSize = TextFieldSize.Medium,
    ): UiDp {
        return when (size) {
            TextFieldSize.Compact -> Theme.controls.textField.compactHorizontalPadding
            TextFieldSize.Medium -> Theme.controls.textField.mediumHorizontalPadding
            TextFieldSize.Large -> Theme.controls.textField.largeHorizontalPadding
        }
    }

    /** Resolves top and bottom content padding for [size]. */
    fun verticalPadding(
        size: TextFieldSize = TextFieldSize.Medium,
    ): UiDp {
        return when (size) {
            TextFieldSize.Compact -> Theme.controls.textField.compactVerticalPadding
            TextFieldSize.Medium -> Theme.controls.textField.mediumVerticalPadding
            TextFieldSize.Large -> Theme.controls.textField.largeVerticalPadding
        }
    }

    /** Returns the current pressed-state control highlight. */
    fun pressedColor(): Int = Theme.stateColors.controlHighlight.resolve(pressed = true)

    /** Returns the current activated control color for the text cursor. */
    fun cursorColor(): Int = Theme.stateColors.controlActivated.resolve()
}
