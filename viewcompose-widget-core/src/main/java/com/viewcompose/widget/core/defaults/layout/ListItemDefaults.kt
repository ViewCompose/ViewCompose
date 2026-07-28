package com.viewcompose.widget.core

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

    fun minHeight(): Int = Theme.controls.listItem.minHeight

    fun horizontalPadding(): Int = Theme.controls.listItem.horizontalPadding

    fun verticalPadding(): Int = Theme.controls.listItem.verticalPadding

    fun leadingTrailingSpacing(): Int = Theme.controls.listItem.leadingTrailingSpacing

    fun textSpacing(): Int = Theme.controls.listItem.textSpacing
}
