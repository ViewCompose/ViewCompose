package com.viewcompose.widget.core

import com.viewcompose.ui.shape.UiShape

/**
 * FloatingActionButton 的尺寸档位。
 * Size tiers for FloatingActionButton.
 */
enum class FabSize {
    Small,
    Medium,
    Large,
}

/**
 * FAB 与 Extended FAB 的默认 token。
 * Default tokens for FAB and Extended FAB.
 *
 * 普通 FAB 按 size 选择独立形状和图标尺寸，Extended FAB 使用横向文本按钮语义。
 * Regular FAB selects shape and icon size by size tier, while Extended FAB follows horizontal text-button semantics.
 */
object FabDefaults {
    fun containerColor(): Int = Theme.colors.primaryContainer

    fun contentColor(): Int = Theme.colors.onPrimaryContainer

    fun size(size: FabSize = FabSize.Medium): Int {
        return when (size) {
            FabSize.Small -> Theme.controls.fab.smallSize
            FabSize.Medium -> Theme.controls.fab.mediumSize
            FabSize.Large -> Theme.controls.fab.largeSize
        }
    }

    fun iconSize(size: FabSize = FabSize.Medium): Int {
        return when (size) {
            FabSize.Small -> Theme.controls.fab.smallIconSize
            FabSize.Medium -> Theme.controls.fab.mediumIconSize
            FabSize.Large -> Theme.controls.fab.largeIconSize
        }
    }

    fun shape(size: FabSize = FabSize.Medium): UiShape {
        return when (size) {
            FabSize.Small -> UiShape.rounded(12.dp)
            FabSize.Medium -> UiShape.rounded(16.dp)
            FabSize.Large -> UiShape.rounded(28.dp)
        }
    }

    fun elevation(): Int = Theme.controls.fab.elevation

    fun extendedHeight(): Int = Theme.controls.fab.extendedHeight

    fun extendedShape(): UiShape = Theme.shapes.large

    fun extendedHorizontalPadding(): Int = Theme.controls.fab.extendedHorizontalPadding

    fun extendedIconSpacing(): Int = Theme.controls.fab.extendedIconSpacing

    fun extendedTextStyle(): UiTextStyle = TextDefaults.labelLargeStyle()

    fun pressedColor(): Int = Theme.colors.ripple
}
