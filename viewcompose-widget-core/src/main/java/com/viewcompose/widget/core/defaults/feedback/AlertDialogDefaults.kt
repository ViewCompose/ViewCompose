package com.viewcompose.widget.core

import com.viewcompose.ui.unit.UiDp

import com.viewcompose.ui.shape.UiShape

/** Default visual, typography, and spacing tokens for alert dialogs. */
object AlertDialogDefaults {
    /** Returns the dialog container color. */
    fun containerColor(): Int = Theme.colors.surface

    /** Returns the dialog title color. */
    fun titleColor(): Int = Theme.colors.onSurface

    /** Returns the dialog supporting-text color. */
    fun textColor(): Int = Theme.colors.onSurfaceVariant

    /** Returns the tint applied to a leading dialog icon. */
    fun iconTint(): Int = Theme.colors.primary

    /** Returns the dialog title text style. */
    fun titleStyle(): UiTextStyle = TextDefaults.titleLargeStyle()

    /** Returns the dialog supporting-text style. */
    fun textStyle(): UiTextStyle = TextDefaults.bodyMediumStyle()

    /** Returns the dialog container shape. */
    fun shape(): UiShape = Theme.shapes.medium

    /** Returns the padding between the dialog edge and its content. */
    fun contentPadding(): UiDp = 24.dp

    /** Returns the spacing between the title and supporting text. */
    fun titleToTextSpacing(): UiDp = 16.dp

    /** Returns the spacing between supporting text and action buttons. */
    fun textToButtonsSpacing(): UiDp = 24.dp

    /** Returns the spacing between adjacent action buttons. */
    fun buttonSpacing(): UiDp = 8.dp

    /** Returns the spacing below a leading dialog icon. */
    fun iconBottomSpacing(): UiDp = 16.dp

    /** Returns the square size of a leading dialog icon. */
    fun iconSize(): UiDp = 24.dp

    /** Returns the minimum dialog width. */
    fun minWidth(): UiDp = 280.dp
}
