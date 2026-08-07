package com.viewcompose.ui.foundation

import com.viewcompose.ui.unit.UiDp

import com.viewcompose.ui.shape.UiShape

/** Interaction-density tier used to select segmented-control dimensions and typography. */
enum class SegmentedControlSize {
    Compact,
    Medium,
    Large,
}

/**
 * Resolves segmented-control defaults from the current theme and scoped color overrides.
 *
 * Dimensions always come from `Theme.controls`; providers replace only explicit color slots.
 */
object SegmentedControlDefaults {
    /** Resolves track background color for [enabled] state. */
    fun backgroundColor(enabled: Boolean = true): Int {
        val override = UiLocals.current(LocalSegmentedControlColors)
        return if (enabled) {
            override?.background ?: Theme.colors.surface
        } else {
            override?.backgroundDisabled ?: Theme.colors.surface
        }
    }

    /** Resolves selected-segment indicator color for [enabled] state. */
    fun indicatorColor(enabled: Boolean = true): Int {
        val override = UiLocals.current(LocalSegmentedControlColors)
        return if (enabled) {
            override?.indicator ?: Theme.colors.secondaryContainer
        } else {
            override?.indicatorDisabled ?: Theme.stateColors.controlActivated.resolve(enabled = false)
        }
    }

    /** Returns the current full theme shape. */
    fun shape(): UiShape = Theme.shapes.full

    /** Resolves unselected label color for [enabled] state. */
    fun textColor(enabled: Boolean = true): Int {
        val override = UiLocals.current(LocalSegmentedControlColors)
        return if (enabled) {
            override?.text ?: Theme.colors.onSurface
        } else {
            override?.textDisabled ?: Theme.stateColors.secondaryText.resolve(enabled = false)
        }
    }

    /** Resolves selected label color for [enabled] state. */
    fun selectedTextColor(enabled: Boolean = true): Int {
        val override = UiLocals.current(LocalSegmentedControlColors)
        return if (enabled) {
            override?.selectedText ?: Theme.colors.onSecondaryContainer
        } else {
            override?.selectedTextDisabled ?: Theme.colors.onSurfaceVariant
        }
    }

    /** Resolves pressed feedback, or transparency while disabled. */
    fun rippleColor(enabled: Boolean = true): Int {
        return if (enabled) {
            Theme.stateColors.controlHighlight.resolve(pressed = true)
        } else {
            0x00000000
        }
    }

    /** Resolves label typography for [size]. */
    fun textStyle(
        size: SegmentedControlSize = SegmentedControlSize.Medium,
    ): UiTextStyle {
        return when (size) {
            SegmentedControlSize.Compact -> TextDefaults.labelMediumStyle()
            SegmentedControlSize.Medium -> TextDefaults.labelLargeStyle()
            SegmentedControlSize.Large -> TextDefaults.bodyLargeStyle()
        }
    }

    /** Resolves minimum control height for [size]. */
    fun height(
        size: SegmentedControlSize = SegmentedControlSize.Medium,
    ): UiDp {
        return when (size) {
            SegmentedControlSize.Compact -> Theme.controls.segmentedControl.compactHeight
            SegmentedControlSize.Medium -> Theme.controls.segmentedControl.mediumHeight
            SegmentedControlSize.Large -> Theme.controls.segmentedControl.largeHeight
        }
    }

    /** Resolves horizontal segment content padding for [size]. */
    fun paddingHorizontal(
        size: SegmentedControlSize = SegmentedControlSize.Medium,
    ): UiDp {
        return when (size) {
            SegmentedControlSize.Compact -> Theme.controls.segmentedControl.compactHorizontalPadding
            SegmentedControlSize.Medium -> Theme.controls.segmentedControl.mediumHorizontalPadding
            SegmentedControlSize.Large -> Theme.controls.segmentedControl.largeHorizontalPadding
        }
    }

    /** Resolves vertical segment content padding for [size]. */
    fun paddingVertical(
        size: SegmentedControlSize = SegmentedControlSize.Medium,
    ): UiDp {
        return when (size) {
            SegmentedControlSize.Compact -> Theme.controls.segmentedControl.compactVerticalPadding
            SegmentedControlSize.Medium -> Theme.controls.segmentedControl.mediumVerticalPadding
            SegmentedControlSize.Large -> Theme.controls.segmentedControl.largeVerticalPadding
        }
    }
}
