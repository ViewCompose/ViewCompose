package com.viewcompose.widget.core

import com.viewcompose.ui.shape.UiShape

enum class SegmentedControlSize {
    Compact,
    Medium,
    Large,
}

object SegmentedControlDefaults {
    fun backgroundColor(enabled: Boolean = true): Int {
        val override = UiLocals.current(LocalSegmentedControlColors)
        return if (enabled) {
            override?.background ?: Theme.colors.surfaceVariant
        } else {
            override?.backgroundDisabled ?: Theme.colors.surface
        }
    }

    fun indicatorColor(enabled: Boolean = true): Int {
        val override = UiLocals.current(LocalSegmentedControlColors)
        return if (enabled) {
            override?.indicator ?: Theme.stateColors.controlActivated.resolve(selected = true)
        } else {
            override?.indicatorDisabled ?: Theme.stateColors.controlActivated.resolve(enabled = false)
        }
    }

    fun shape(): UiShape = Theme.shapes.small

    fun textColor(enabled: Boolean = true): Int {
        val override = UiLocals.current(LocalSegmentedControlColors)
        return if (enabled) {
            override?.text ?: Theme.stateColors.secondaryText.resolve()
        } else {
            override?.textDisabled ?: Theme.stateColors.secondaryText.resolve(enabled = false)
        }
    }

    fun selectedTextColor(enabled: Boolean = true): Int {
        val override = UiLocals.current(LocalSegmentedControlColors)
        return if (enabled) {
            override?.selectedText ?: Theme.colors.onPrimary
        } else {
            override?.selectedTextDisabled ?: Theme.colors.onSurfaceVariant
        }
    }

    fun rippleColor(enabled: Boolean = true): Int {
        return if (enabled) {
            Theme.stateColors.controlHighlight.resolve(pressed = true)
        } else {
            0x00000000
        }
    }

    fun textStyle(
        size: SegmentedControlSize = SegmentedControlSize.Medium,
    ): UiTextStyle {
        return when (size) {
            SegmentedControlSize.Compact -> TextDefaults.labelMediumStyle()
            SegmentedControlSize.Medium -> TextDefaults.labelLargeStyle()
            SegmentedControlSize.Large -> TextDefaults.bodyLargeStyle()
        }
    }

    fun height(
        size: SegmentedControlSize = SegmentedControlSize.Medium,
    ): Int {
        return when (size) {
            SegmentedControlSize.Compact -> Theme.controls.segmentedControl.compactHeight
            SegmentedControlSize.Medium -> Theme.controls.segmentedControl.mediumHeight
            SegmentedControlSize.Large -> Theme.controls.segmentedControl.largeHeight
        }
    }

    fun paddingHorizontal(
        size: SegmentedControlSize = SegmentedControlSize.Medium,
    ): Int {
        return when (size) {
            SegmentedControlSize.Compact -> Theme.controls.segmentedControl.compactHorizontalPadding
            SegmentedControlSize.Medium -> Theme.controls.segmentedControl.mediumHorizontalPadding
            SegmentedControlSize.Large -> Theme.controls.segmentedControl.largeHorizontalPadding
        }
    }

    fun paddingVertical(
        size: SegmentedControlSize = SegmentedControlSize.Medium,
    ): Int {
        return when (size) {
            SegmentedControlSize.Compact -> Theme.controls.segmentedControl.compactVerticalPadding
            SegmentedControlSize.Medium -> Theme.controls.segmentedControl.mediumVerticalPadding
            SegmentedControlSize.Large -> Theme.controls.segmentedControl.largeVerticalPadding
        }
    }
}
