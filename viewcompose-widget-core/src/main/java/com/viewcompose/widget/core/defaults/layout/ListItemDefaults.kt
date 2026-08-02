package com.viewcompose.widget.core

import com.viewcompose.ui.unit.UiDp

/** Default typography, color, size, and spacing tokens for list items. */
object ListItemDefaults {
    /** Returns the headline text color. */
    fun headlineColor(): Int = Theme.colors.onSurface

    /** Returns the supporting-text color. */
    fun supportingColor(): Int = Theme.colors.onSurfaceVariant

    /** Returns the overline text color. */
    fun overlineColor(): Int = Theme.colors.onSurfaceVariant

    /** Returns the headline text style. */
    fun headlineStyle(): UiTextStyle = TextDefaults.bodyLargeStyle()

    /** Returns the supporting-text style. */
    fun supportingStyle(): UiTextStyle = TextDefaults.labelMediumStyle()

    /** Returns the overline text style. */
    fun overlineStyle(): UiTextStyle = TextDefaults.labelSmallStyle()

    /** Returns the minimum list-item height. */
    fun minHeight(): UiDp = Theme.controls.listItem.minHeight

    /** Returns the horizontal padding between the item edge and its content. */
    fun horizontalPadding(): UiDp = Theme.controls.listItem.horizontalPadding

    /** Returns the vertical padding between the item edge and its content. */
    fun verticalPadding(): UiDp = Theme.controls.listItem.verticalPadding

    /** Returns the gap between leading or trailing content and the text column. */
    fun leadingTrailingSpacing(): UiDp = Theme.controls.listItem.leadingTrailingSpacing

    /** Returns the vertical gap between text lines in the item. */
    fun textSpacing(): UiDp = Theme.controls.listItem.textSpacing
}
