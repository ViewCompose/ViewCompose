package com.viewcompose.ui.foundation

import com.viewcompose.ui.node.UiStateLayerColors
import com.viewcompose.ui.shape.UiShape
import com.viewcompose.ui.unit.UiDp

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
 * Resolves Button appearance from the current theme and scoped [ButtonOverrides].
 *
 * Public queries include scoped overrides. [Button] additionally merges its instance patch once
 * and resolves a complete immutable snapshot before emitting a node.
 */
object ButtonDefaults {
    /** Resolves the container color for [variant] and [enabled] state. */
    fun containerColor(
        variant: ButtonVariant = ButtonVariant.Primary,
        enabled: Boolean = true,
    ): Int {
        val overrides = scopedOverrides()
        return resolveStateValue(
            enabled = enabled,
            enabledOverride = overrides.containerColor,
            disabledOverride = overrides.disabledContainerColor,
            enabledDefault = semanticContainerColor(variant, enabled = true),
            disabledDefault = semanticContainerColor(variant, enabled = false),
        )
    }

    /** Resolves label and icon content color for [variant] and [enabled] state. */
    fun contentColor(
        variant: ButtonVariant = ButtonVariant.Primary,
        enabled: Boolean = true,
    ): Int {
        val overrides = scopedOverrides()
        return resolveStateValue(
            enabled = enabled,
            enabledOverride = overrides.contentColor,
            disabledOverride = overrides.disabledContentColor,
            enabledDefault = semanticContentColor(variant, enabled = true),
            disabledDefault = semanticContentColor(variant, enabled = false),
        )
    }

    /** Resolves the border color, including explicit overrides for non-outlined variants. */
    fun borderColor(
        variant: ButtonVariant = ButtonVariant.Primary,
        enabled: Boolean = true,
    ): Int {
        val overrides = scopedOverrides()
        return resolveStateValue(
            enabled = enabled,
            enabledOverride = overrides.borderColor,
            disabledOverride = overrides.disabledBorderColor,
            enabledDefault = semanticBorderColor(variant, enabled = true),
            disabledDefault = semanticBorderColor(variant, enabled = false),
        )
    }

    /** Resolves an overridden border width or the semantic width for [variant]. */
    fun borderWidth(variant: ButtonVariant = ButtonVariant.Primary): UiDp =
        scopedOverrides().borderWidth ?: semanticBorderWidth(variant)

    /** Resolves an overridden Button shape or the current full theme shape. */
    fun shape(): UiShape = scopedOverrides().shape ?: Theme.shapes.full

    /** Resolves the minimum effective height for [size]. */
    fun height(size: ButtonSize = ButtonSize.Medium): UiDp =
        scopedOverrides().minimumHeight ?: semanticHeight(size)

    /** Returns the visible container height centered inside the effective target for [size]. */
    fun visualHeight(size: ButtonSize = ButtonSize.Medium): UiDp =
        scopedOverrides().visualHeight ?: semanticVisualHeight(size)

    /** Resolves start and end content padding for [size] and [variant]. */
    fun horizontalPadding(
        size: ButtonSize = ButtonSize.Medium,
        variant: ButtonVariant = ButtonVariant.Primary,
    ): UiDp = scopedOverrides().horizontalPadding ?: semanticHorizontalPadding(size, variant)

    /** Resolves top and bottom content padding for [size]. */
    fun verticalPadding(size: ButtonSize = ButtonSize.Medium): UiDp =
        scopedOverrides().verticalPadding ?: semanticVerticalPadding(size)

    /** Resolves the label typography tier for [size]. */
    fun textStyle(size: ButtonSize = ButtonSize.Medium): UiTextStyle =
        scopedOverrides().textStyle ?: semanticTextStyle(size)

    /** Resolves leading and trailing icon size for [size]. */
    fun iconSize(size: ButtonSize = ButtonSize.Medium): UiDp =
        scopedOverrides().iconSize ?: semanticIconSize(size)

    /** Resolves spacing between an icon and label for [size]. */
    fun iconSpacing(size: ButtonSize = ButtonSize.Medium): UiDp =
        scopedOverrides().iconSpacing ?: semanticIconSpacing(size)

    /** Resolves the current pressed-state control highlight. */
    fun pressedColor(): Int = Theme.stateColors.controlHighlight.resolve(pressed = true)

    /** Resolves transient interaction colors from the effective enabled content role. */
    fun stateLayerColors(variant: ButtonVariant = ButtonVariant.Primary): UiStateLayerColors {
        val scoped = scopedOverrides()
        return scoped.stateLayerColors ?: stateLayerColorsFor(
            scoped.contentColor ?: semanticContentColor(variant, enabled = true),
        )
    }

    internal fun resolve(
        variant: ButtonVariant,
        size: ButtonSize,
        enabled: Boolean,
        instance: ButtonOverrides,
    ): ResolvedButtonAppearance {
        val overrides = scopedOverrides().merge(instance)
        val contentColor = resolveStateValue(
            enabled = enabled,
            enabledOverride = overrides.contentColor,
            disabledOverride = overrides.disabledContentColor,
            enabledDefault = semanticContentColor(variant, enabled = true),
            disabledDefault = semanticContentColor(variant, enabled = false),
        )
        return ResolvedButtonAppearance(
            containerColor = resolveStateValue(
                enabled = enabled,
                enabledOverride = overrides.containerColor,
                disabledOverride = overrides.disabledContainerColor,
                enabledDefault = semanticContainerColor(variant, enabled = true),
                disabledDefault = semanticContainerColor(variant, enabled = false),
            ),
            contentColor = contentColor,
            borderColor = resolveStateValue(
                enabled = enabled,
                enabledOverride = overrides.borderColor,
                disabledOverride = overrides.disabledBorderColor,
                enabledDefault = semanticBorderColor(variant, enabled = true),
                disabledDefault = semanticBorderColor(variant, enabled = false),
            ),
            borderWidth = overrides.borderWidth ?: semanticBorderWidth(variant),
            shape = overrides.shape ?: Theme.shapes.full,
            stateLayerColors = overrides.stateLayerColors ?: stateLayerColorsFor(
                overrides.contentColor ?: semanticContentColor(variant, enabled = true),
            ),
            textStyle = overrides.textStyle ?: semanticTextStyle(size),
            minimumHeight = overrides.minimumHeight ?: semanticHeight(size),
            visualHeight = overrides.visualHeight ?: semanticVisualHeight(size),
            horizontalPadding = overrides.horizontalPadding ?: semanticHorizontalPadding(size, variant),
            verticalPadding = overrides.verticalPadding ?: semanticVerticalPadding(size),
            iconSize = overrides.iconSize ?: semanticIconSize(size),
            iconSpacing = overrides.iconSpacing ?: semanticIconSpacing(size),
        )
    }

    internal fun semanticContainerColor(variant: ButtonVariant, enabled: Boolean): Int = when (variant) {
        ButtonVariant.Primary -> if (enabled) Theme.colors.primary else disabledContainerColor()
        ButtonVariant.Secondary -> if (enabled) Theme.colors.secondary else disabledContainerColor()
        ButtonVariant.Tonal -> if (enabled) Theme.colors.secondaryContainer else disabledContainerColor()
        ButtonVariant.Outlined,
        ButtonVariant.Text,
        -> 0x00000000
    }

    internal fun semanticContentColor(variant: ButtonVariant, enabled: Boolean): Int {
        if (!enabled) return disabledContentColor()
        return when (variant) {
            ButtonVariant.Primary -> Theme.colors.onPrimary
            ButtonVariant.Secondary -> Theme.colors.onSecondary
            ButtonVariant.Tonal -> Theme.colors.onSecondaryContainer
            ButtonVariant.Outlined,
            ButtonVariant.Text,
            -> Theme.colors.primary
        }
    }

    internal fun semanticBorderColor(variant: ButtonVariant, enabled: Boolean): Int = when (variant) {
        ButtonVariant.Outlined -> if (enabled) Theme.colors.outline else disabledContainerColor()
        else -> 0x00000000
    }

    internal fun semanticBorderWidth(variant: ButtonVariant): UiDp = when (variant) {
        ButtonVariant.Outlined -> 1.dp
        else -> 0.dp
    }

    internal fun semanticHeight(size: ButtonSize): UiDp = when (size) {
        ButtonSize.Compact -> Theme.controls.button.compactHeight
        ButtonSize.Medium -> Theme.controls.button.mediumHeight
        ButtonSize.Large -> Theme.controls.button.largeHeight
    }

    internal fun semanticVisualHeight(size: ButtonSize): UiDp = when (size) {
        ButtonSize.Compact -> Theme.controls.button.compactVisualHeight
        ButtonSize.Medium -> Theme.controls.button.mediumVisualHeight
        ButtonSize.Large -> Theme.controls.button.largeVisualHeight
    }

    internal fun semanticHorizontalPadding(size: ButtonSize, variant: ButtonVariant): UiDp {
        if (variant == ButtonVariant.Text) {
            return when (size) {
                ButtonSize.Compact,
                ButtonSize.Medium,
                -> 12.dp
                ButtonSize.Large -> 16.dp
            }
        }
        return when (size) {
            ButtonSize.Compact -> Theme.controls.button.compactHorizontalPadding
            ButtonSize.Medium -> Theme.controls.button.mediumHorizontalPadding
            ButtonSize.Large -> Theme.controls.button.largeHorizontalPadding
        }
    }

    internal fun semanticVerticalPadding(size: ButtonSize): UiDp = when (size) {
        ButtonSize.Compact -> Theme.controls.button.compactVerticalPadding
        ButtonSize.Medium -> Theme.controls.button.mediumVerticalPadding
        ButtonSize.Large -> Theme.controls.button.largeVerticalPadding
    }

    internal fun semanticTextStyle(size: ButtonSize): UiTextStyle = when (size) {
        ButtonSize.Compact -> TextDefaults.labelMediumStyle()
        ButtonSize.Medium -> TextDefaults.labelLargeStyle()
        ButtonSize.Large -> TextDefaults.bodyLargeStyle()
    }

    internal fun semanticIconSize(size: ButtonSize): UiDp = when (size) {
        ButtonSize.Compact -> 16.dp
        ButtonSize.Medium -> 18.dp
        ButtonSize.Large -> 20.dp
    }

    internal fun semanticIconSpacing(size: ButtonSize): UiDp = when (size) {
        ButtonSize.Compact -> 6.dp
        ButtonSize.Medium -> 8.dp
        ButtonSize.Large -> 10.dp
    }

    private fun scopedOverrides(): ButtonOverrides = UiLocals.current(LocalButtonOverrides)
}

internal data class ResolvedButtonAppearance(
    val containerColor: Int,
    val contentColor: Int,
    val borderColor: Int,
    val borderWidth: UiDp,
    val shape: UiShape,
    val stateLayerColors: UiStateLayerColors,
    val textStyle: UiTextStyle,
    val minimumHeight: UiDp,
    val visualHeight: UiDp,
    val horizontalPadding: UiDp,
    val verticalPadding: UiDp,
    val iconSize: UiDp,
    val iconSpacing: UiDp,
)

internal fun resolveStateValue(
    enabled: Boolean,
    enabledOverride: Int?,
    disabledOverride: Int?,
    enabledDefault: Int,
    disabledDefault: Int,
): Int = if (enabled) enabledOverride ?: enabledDefault else disabledOverride ?: disabledDefault

private fun disabledContainerColor(): Int = colorWithAlpha(Theme.colors.onSurface, 0.12f)

private fun disabledContentColor(): Int = colorWithAlpha(Theme.colors.onSurface, 0.38f)
