package com.viewcompose.ui.foundation

import com.viewcompose.ui.unit.UiDp

import com.viewcompose.ui.shape.UiShape

/** Default visual, typography, and spacing tokens for alert dialogs. */
object AlertDialogDefaults {
    /** Returns the dialog container color. */
    fun containerColor(): Int = scoped().containerColor ?: Theme.colors.surfaceContainerHigh

    /** Returns the dialog title color. */
    fun titleColor(): Int = scoped().titleColor ?: Theme.colors.onSurface

    /** Returns the dialog supporting-text color. */
    fun textColor(): Int = scoped().textColor ?: Theme.colors.onSurfaceVariant

    /** Returns the tint applied to a leading dialog icon. */
    fun iconTint(): Int = scoped().iconTint ?: Theme.colors.primary

    /** Returns the dialog title text style. */
    fun titleStyle(): UiTextStyle = scoped().titleStyle ?: TextDefaults.headlineSmallStyle()

    /** Returns the dialog supporting-text style. */
    fun textStyle(): UiTextStyle = scoped().textStyle ?: TextDefaults.bodyMediumStyle()

    /** Returns the dialog container shape. */
    fun shape(): UiShape = scoped().shape ?: Theme.shapes.extraLarge

    /** Returns the padding between the dialog edge and its content. */
    fun contentPadding(): UiDp = scoped().contentPadding ?: 24.dp

    /** Returns the spacing between the title and supporting text. */
    fun titleToTextSpacing(): UiDp = scoped().titleToTextSpacing ?: 16.dp

    /** Returns the spacing between supporting text and action buttons. */
    fun textToButtonsSpacing(): UiDp = scoped().textToButtonsSpacing ?: 24.dp

    /** Returns the spacing between adjacent action buttons. */
    fun buttonSpacing(): UiDp = scoped().buttonSpacing ?: 8.dp

    /** Returns the spacing below a leading dialog icon. */
    fun iconBottomSpacing(): UiDp = scoped().iconBottomSpacing ?: 16.dp

    /** Returns the square size of a leading dialog icon. */
    fun iconSize(): UiDp = scoped().iconSize ?: 24.dp

    /** Returns the minimum dialog width. */
    fun minWidth(): UiDp = scoped().minWidth ?: 280.dp

    internal fun resolve(instance: AlertDialogOverrides): ResolvedAlertDialogAppearance {
        val overrides = scoped().merge(instance)
        return ResolvedAlertDialogAppearance(
            containerColor = overrides.containerColor ?: Theme.colors.surfaceContainerHigh,
            titleColor = overrides.titleColor ?: Theme.colors.onSurface,
            textColor = overrides.textColor ?: Theme.colors.onSurfaceVariant,
            iconTint = overrides.iconTint ?: Theme.colors.primary,
            titleStyle = overrides.titleStyle ?: TextDefaults.headlineSmallStyle(),
            textStyle = overrides.textStyle ?: TextDefaults.bodyMediumStyle(),
            shape = overrides.shape ?: Theme.shapes.extraLarge,
            contentPadding = overrides.contentPadding ?: 24.dp,
            titleToTextSpacing = overrides.titleToTextSpacing ?: 16.dp,
            textToButtonsSpacing = overrides.textToButtonsSpacing ?: 24.dp,
            buttonSpacing = overrides.buttonSpacing ?: 8.dp,
            iconBottomSpacing = overrides.iconBottomSpacing ?: 16.dp,
            iconSize = overrides.iconSize ?: 24.dp,
            minWidth = overrides.minWidth ?: 280.dp,
        )
    }

    private fun scoped(): AlertDialogOverrides = UiLocals.current(LocalAlertDialogOverrides)
}

internal data class ResolvedAlertDialogAppearance(
    val containerColor: Int,
    val titleColor: Int,
    val textColor: Int,
    val iconTint: Int,
    val titleStyle: UiTextStyle,
    val textStyle: UiTextStyle,
    val shape: UiShape,
    val contentPadding: UiDp,
    val titleToTextSpacing: UiDp,
    val textToButtonsSpacing: UiDp,
    val buttonSpacing: UiDp,
    val iconBottomSpacing: UiDp,
    val iconSize: UiDp,
    val minWidth: UiDp,
)
