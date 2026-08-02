package com.viewcompose.widget.core

import com.viewcompose.ui.unit.UiDp

import com.viewcompose.ui.shape.UiShape

/** Size tier used to select FAB bounds, icon size, and shape. */
enum class FabSize {
    Small,
    Medium,
    Large,
}

/**
 * Resolves FAB and extended-FAB defaults from the current theme.
 *
 * Regular FABs select bounds, icon size, and shape by [FabSize]. Extended FABs use a horizontal
 * icon-and-label treatment.
 */
object FabDefaults {
    /** Returns the current primary-container color. */
    fun containerColor(): Int = Theme.colors.primaryContainer

    /** Returns content color suitable for [containerColor]. */
    fun contentColor(): Int = Theme.colors.onPrimaryContainer

    /** Resolves square FAB bounds for [size]. */
    fun size(size: FabSize = FabSize.Medium): UiDp {
        return when (size) {
            FabSize.Small -> Theme.controls.fab.smallSize
            FabSize.Medium -> Theme.controls.fab.mediumSize
            FabSize.Large -> Theme.controls.fab.largeSize
        }
    }

    /** Resolves icon size for [size]. */
    fun iconSize(size: FabSize = FabSize.Medium): UiDp {
        return when (size) {
            FabSize.Small -> Theme.controls.fab.smallIconSize
            FabSize.Medium -> Theme.controls.fab.mediumIconSize
            FabSize.Large -> Theme.controls.fab.largeIconSize
        }
    }

    /** Resolves the framework FAB shape for [size]. */
    fun shape(size: FabSize = FabSize.Medium): UiShape {
        return when (size) {
            FabSize.Small -> UiShape.rounded(12.dp)
            FabSize.Medium -> UiShape.rounded(16.dp)
            FabSize.Large -> UiShape.rounded(28.dp)
        }
    }

    /** Returns resting FAB elevation. */
    fun elevation(): UiDp = Theme.controls.fab.elevation

    /** Returns minimum extended-FAB height. */
    fun extendedHeight(): UiDp = Theme.controls.fab.extendedHeight

    /** Returns the current large theme shape for an extended FAB. */
    fun extendedShape(): UiShape = Theme.shapes.large

    /** Returns horizontal content padding for an extended FAB. */
    fun extendedHorizontalPadding(): UiDp = Theme.controls.fab.extendedHorizontalPadding

    /** Returns spacing between extended-FAB icon and label. */
    fun extendedIconSpacing(): UiDp = Theme.controls.fab.extendedIconSpacing

    /** Returns the large label typography style. */
    fun extendedTextStyle(): UiTextStyle = TextDefaults.labelLargeStyle()

    /** Returns the current ripple color. */
    fun pressedColor(): Int = Theme.colors.ripple
}
