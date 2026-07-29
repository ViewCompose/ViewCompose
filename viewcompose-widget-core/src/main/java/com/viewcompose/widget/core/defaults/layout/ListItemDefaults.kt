package com.viewcompose.widget.core

import com.viewcompose.ui.unit.UiDp

/**
 * ListItem DSL 的默认文本、颜色和间距 token。
 * Default text, color, and spacing tokens for the ListItem DSL.
 */
object ListItemDefaults {
    fun headlineColor(): Int = Theme.colors.onSurface

    fun supportingColor(): Int = Theme.colors.onSurfaceVariant

    fun overlineColor(): Int = Theme.colors.onSurfaceVariant

    fun headlineStyle(): UiTextStyle = TextDefaults.bodyLargeStyle()

    fun supportingStyle(): UiTextStyle = TextDefaults.labelMediumStyle()

    fun overlineStyle(): UiTextStyle = TextDefaults.labelSmallStyle()

    fun minHeight(): UiDp = Theme.controls.listItem.minHeight

    fun horizontalPadding(): UiDp = Theme.controls.listItem.horizontalPadding

    fun verticalPadding(): UiDp = Theme.controls.listItem.verticalPadding

    fun leadingTrailingSpacing(): UiDp = Theme.controls.listItem.leadingTrailingSpacing

    fun textSpacing(): UiDp = Theme.controls.listItem.textSpacing
}
