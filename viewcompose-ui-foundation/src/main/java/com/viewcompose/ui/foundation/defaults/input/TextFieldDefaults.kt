package com.viewcompose.ui.foundation

import com.viewcompose.ui.shape.UiShape
import com.viewcompose.ui.unit.UiDp

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

/** Resolves TextField appearance from the current theme and scoped [TextFieldOverrides]. */
object TextFieldDefaults {
    /** Resolves editable text typography for [size]. */
    fun textStyle(size: TextFieldSize = TextFieldSize.Medium): UiTextStyle =
        scopedOverrides().textStyle ?: semanticTextStyle(size)

    /** Resolves editable text color for enabled, disabled, and error state. */
    fun textColor(enabled: Boolean = true, isError: Boolean = false): Int {
        val overrides = scopedOverrides()
        return resolveTextFieldStateValue(
            enabled = enabled,
            isError = isError,
            enabledOverride = overrides.textColor,
            disabledOverride = overrides.disabledTextColor,
            errorOverride = overrides.errorTextColor,
            enabledDefault = semanticTextColor(enabled = true),
            disabledDefault = semanticTextColor(enabled = false),
            errorDefault = semanticTextColor(enabled),
        )
    }

    /** Resolves placeholder color for enabled, disabled, and error state. */
    fun hintColor(enabled: Boolean = true, isError: Boolean = false): Int {
        val overrides = scopedOverrides()
        return resolveTextFieldStateValue(
            enabled = enabled,
            isError = isError,
            enabledOverride = overrides.placeholderColor,
            disabledOverride = overrides.disabledPlaceholderColor,
            errorOverride = overrides.errorPlaceholderColor,
            enabledDefault = semanticPlaceholderColor(enabled = true),
            disabledDefault = semanticPlaceholderColor(enabled = false),
            errorDefault = semanticPlaceholderColor(enabled),
        )
    }

    /** Resolves label color for enabled, disabled, and error state. */
    fun labelColor(enabled: Boolean = true, isError: Boolean = false): Int {
        val overrides = scopedOverrides()
        return resolveTextFieldStateValue(
            enabled = enabled,
            isError = isError,
            enabledOverride = overrides.labelColor,
            disabledOverride = overrides.disabledLabelColor,
            errorOverride = overrides.errorLabelColor,
            enabledDefault = semanticLabelColor(enabled = true, isError = false),
            disabledDefault = semanticLabelColor(enabled = false, isError = false),
            errorDefault = semanticLabelColor(enabled, isError = true),
        )
    }

    /** Resolves supporting-text color independently from the label override slots. */
    fun supportingTextColor(enabled: Boolean = true, isError: Boolean = false): Int {
        val overrides = scopedOverrides()
        return resolveTextFieldStateValue(
            enabled = enabled,
            isError = isError,
            enabledOverride = overrides.supportingTextColor,
            disabledOverride = overrides.disabledSupportingTextColor,
            errorOverride = overrides.errorSupportingTextColor,
            enabledDefault = semanticLabelColor(enabled = true, isError = false),
            disabledDefault = semanticLabelColor(enabled = false, isError = false),
            errorDefault = semanticLabelColor(enabled, isError = true),
        )
    }

    /** Resolves label typography. */
    fun labelTextStyle(): UiTextStyle =
        scopedOverrides().labelTextStyle ?: TextDefaults.bodySmallStyle()

    /** Resolves supporting-text typography. */
    fun supportingTextStyle(): UiTextStyle =
        scopedOverrides().supportingTextStyle ?: TextDefaults.bodySmallStyle()

    /** Resolves container color for [variant], [enabled], and [isError] state. */
    fun containerColor(
        variant: TextFieldVariant = TextFieldVariant.Filled,
        enabled: Boolean = true,
        isError: Boolean = false,
    ): Int {
        val overrides = scopedOverrides()
        return resolveTextFieldStateValue(
            enabled = enabled,
            isError = isError,
            enabledOverride = overrides.containerColor,
            disabledOverride = overrides.disabledContainerColor,
            errorOverride = overrides.errorContainerColor,
            enabledDefault = semanticContainerColor(variant, enabled = true),
            disabledDefault = semanticContainerColor(variant, enabled = false),
            errorDefault = semanticContainerColor(variant, enabled),
        )
    }

    /** Resolves border color for [variant], [enabled], and [isError] state. */
    fun borderColor(
        variant: TextFieldVariant = TextFieldVariant.Filled,
        enabled: Boolean = true,
        isError: Boolean = false,
    ): Int {
        val overrides = scopedOverrides()
        return resolveTextFieldStateValue(
            enabled = enabled,
            isError = isError,
            enabledOverride = overrides.borderColor,
            disabledOverride = overrides.disabledBorderColor,
            errorOverride = overrides.errorBorderColor,
            enabledDefault = semanticBorderColor(variant, enabled = true, isError = false),
            disabledDefault = semanticBorderColor(variant, enabled = false, isError = false),
            errorDefault = semanticBorderColor(variant, enabled, isError = true),
        )
    }

    /** Resolves border width for [variant]. */
    fun borderWidth(variant: TextFieldVariant = TextFieldVariant.Filled): UiDp =
        scopedOverrides().borderWidth ?: semanticBorderWidth(variant)

    /** Resolves the editable container shape. */
    fun shape(): UiShape = scopedOverrides().shape ?: Theme.shapes.extraSmall

    /** Resolves minimum field height for [size]. */
    fun height(size: TextFieldSize = TextFieldSize.Medium): UiDp =
        scopedOverrides().minimumHeight ?: semanticHeight(size)

    /** Resolves start and end content padding for [size]. */
    fun horizontalPadding(size: TextFieldSize = TextFieldSize.Medium): UiDp =
        scopedOverrides().horizontalPadding ?: semanticHorizontalPadding(size)

    /** Resolves top and bottom content padding for [size]. */
    fun verticalPadding(size: TextFieldSize = TextFieldSize.Medium): UiDp =
        scopedOverrides().verticalPadding ?: semanticVerticalPadding(size)

    /** Returns the current pressed-state control highlight. */
    fun pressedColor(): Int = Theme.stateColors.controlHighlight.resolve(pressed = true)

    /** Resolves the activated cursor color. */
    fun cursorColor(): Int = scopedOverrides().cursorColor ?: Theme.stateColors.controlActivated.resolve()

    internal fun resolve(
        variant: TextFieldVariant,
        size: TextFieldSize,
        enabled: Boolean,
        isError: Boolean,
        instance: TextFieldOverrides,
    ): ResolvedTextFieldAppearance {
        val overrides = scopedOverrides().merge(instance)
        fun stateValue(
            enabledValue: Int?,
            disabledValue: Int?,
            errorValue: Int?,
            enabledDefault: Int,
            disabledDefault: Int,
            errorDefault: Int,
        ) = resolveTextFieldStateValue(
            enabled = enabled,
            isError = isError,
            enabledOverride = enabledValue,
            disabledOverride = disabledValue,
            errorOverride = errorValue,
            enabledDefault = enabledDefault,
            disabledDefault = disabledDefault,
            errorDefault = errorDefault,
        )
        return ResolvedTextFieldAppearance(
            containerColor = stateValue(
                overrides.containerColor,
                overrides.disabledContainerColor,
                overrides.errorContainerColor,
                semanticContainerColor(variant, enabled = true),
                semanticContainerColor(variant, enabled = false),
                semanticContainerColor(variant, enabled),
            ),
            textColor = stateValue(
                overrides.textColor,
                overrides.disabledTextColor,
                overrides.errorTextColor,
                semanticTextColor(enabled = true),
                semanticTextColor(enabled = false),
                semanticTextColor(enabled),
            ),
            placeholderColor = stateValue(
                overrides.placeholderColor,
                overrides.disabledPlaceholderColor,
                overrides.errorPlaceholderColor,
                semanticPlaceholderColor(enabled = true),
                semanticPlaceholderColor(enabled = false),
                semanticPlaceholderColor(enabled),
            ),
            labelColor = stateValue(
                overrides.labelColor,
                overrides.disabledLabelColor,
                overrides.errorLabelColor,
                semanticLabelColor(enabled = true, isError = false),
                semanticLabelColor(enabled = false, isError = false),
                semanticLabelColor(enabled, isError = true),
            ),
            supportingTextColor = stateValue(
                overrides.supportingTextColor,
                overrides.disabledSupportingTextColor,
                overrides.errorSupportingTextColor,
                semanticLabelColor(enabled = true, isError = false),
                semanticLabelColor(enabled = false, isError = false),
                semanticLabelColor(enabled, isError = true),
            ),
            borderColor = stateValue(
                overrides.borderColor,
                overrides.disabledBorderColor,
                overrides.errorBorderColor,
                semanticBorderColor(variant, enabled = true, isError = false),
                semanticBorderColor(variant, enabled = false, isError = false),
                semanticBorderColor(variant, enabled, isError = true),
            ),
            borderWidth = overrides.borderWidth ?: semanticBorderWidth(variant),
            cursorColor = overrides.cursorColor ?: Theme.stateColors.controlActivated.resolve(),
            shape = overrides.shape ?: Theme.shapes.extraSmall,
            textStyle = overrides.textStyle ?: semanticTextStyle(size),
            labelTextStyle = overrides.labelTextStyle ?: TextDefaults.bodySmallStyle(),
            supportingTextStyle = overrides.supportingTextStyle ?: TextDefaults.bodySmallStyle(),
            minimumHeight = overrides.minimumHeight ?: semanticHeight(size),
            horizontalPadding = overrides.horizontalPadding ?: semanticHorizontalPadding(size),
            verticalPadding = overrides.verticalPadding ?: semanticVerticalPadding(size),
        )
    }

    private fun semanticTextStyle(size: TextFieldSize): UiTextStyle = when (size) {
        TextFieldSize.Compact -> TextDefaults.labelSmallStyle()
        TextFieldSize.Medium,
        TextFieldSize.Large,
        -> TextDefaults.bodyLargeStyle()
    }

    private fun semanticTextColor(enabled: Boolean): Int = if (enabled) {
        Theme.stateColors.primaryText.resolve()
    } else {
        disabledContentColor()
    }

    private fun semanticPlaceholderColor(enabled: Boolean): Int = if (enabled) {
        Theme.stateColors.secondaryText.resolve()
    } else {
        disabledContentColor()
    }

    private fun semanticLabelColor(enabled: Boolean, isError: Boolean): Int = when {
        isError -> Theme.colors.error
        enabled -> Theme.colors.onSurfaceVariant
        else -> disabledContentColor()
    }

    private fun semanticContainerColor(variant: TextFieldVariant, enabled: Boolean): Int = when (variant) {
        TextFieldVariant.Outlined -> 0x00000000
        TextFieldVariant.Tonal -> if (enabled) {
            Theme.colors.surfaceContainerHigh
        } else {
            Theme.colors.surfaceContainerHighest
        }
        TextFieldVariant.Filled -> Theme.colors.surfaceContainerHighest
    }

    private fun semanticBorderColor(
        variant: TextFieldVariant,
        enabled: Boolean,
        isError: Boolean,
    ): Int = when {
        isError -> Theme.colors.error
        variant == TextFieldVariant.Outlined && enabled -> Theme.colors.outline
        variant == TextFieldVariant.Outlined -> colorWithAlpha(Theme.colors.onSurface, 0.12f)
        else -> 0x00000000
    }

    private fun semanticBorderWidth(variant: TextFieldVariant): UiDp = when (variant) {
        TextFieldVariant.Outlined -> 1.dp
        else -> 0.dp
    }

    private fun semanticHeight(size: TextFieldSize): UiDp = when (size) {
        TextFieldSize.Compact -> Theme.controls.textField.compactHeight
        TextFieldSize.Medium -> Theme.controls.textField.mediumHeight
        TextFieldSize.Large -> Theme.controls.textField.largeHeight
    }

    private fun semanticHorizontalPadding(size: TextFieldSize): UiDp = when (size) {
        TextFieldSize.Compact -> Theme.controls.textField.compactHorizontalPadding
        TextFieldSize.Medium -> Theme.controls.textField.mediumHorizontalPadding
        TextFieldSize.Large -> Theme.controls.textField.largeHorizontalPadding
    }

    private fun semanticVerticalPadding(size: TextFieldSize): UiDp = when (size) {
        TextFieldSize.Compact -> Theme.controls.textField.compactVerticalPadding
        TextFieldSize.Medium -> Theme.controls.textField.mediumVerticalPadding
        TextFieldSize.Large -> Theme.controls.textField.largeVerticalPadding
    }

    private fun scopedOverrides(): TextFieldOverrides = UiLocals.current(LocalTextFieldOverrides)
}

internal data class ResolvedTextFieldAppearance(
    val containerColor: Int,
    val textColor: Int,
    val placeholderColor: Int,
    val labelColor: Int,
    val supportingTextColor: Int,
    val borderColor: Int,
    val borderWidth: UiDp,
    val cursorColor: Int,
    val shape: UiShape,
    val textStyle: UiTextStyle,
    val labelTextStyle: UiTextStyle,
    val supportingTextStyle: UiTextStyle,
    val minimumHeight: UiDp,
    val horizontalPadding: UiDp,
    val verticalPadding: UiDp,
)

private fun resolveTextFieldStateValue(
    enabled: Boolean,
    isError: Boolean,
    enabledOverride: Int?,
    disabledOverride: Int?,
    errorOverride: Int?,
    enabledDefault: Int,
    disabledDefault: Int,
    errorDefault: Int,
): Int = when {
    isError -> errorOverride
        ?: (if (enabled) enabledOverride else disabledOverride)
        ?: errorDefault
    enabled -> enabledOverride ?: enabledDefault
    else -> disabledOverride ?: disabledDefault
}

private fun disabledContentColor(): Int = colorWithAlpha(Theme.colors.onSurface, 0.38f)
