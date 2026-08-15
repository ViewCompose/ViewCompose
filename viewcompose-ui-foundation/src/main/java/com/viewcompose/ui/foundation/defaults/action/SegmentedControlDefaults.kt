package com.viewcompose.ui.foundation

import com.viewcompose.ui.node.UiStateLayerColors
import com.viewcompose.ui.shape.UiShape
import com.viewcompose.ui.unit.UiDp

/** Interaction-density tier used to select segmented-control dimensions and typography. */
enum class SegmentedControlSize {
    Compact,
    Medium,
    Large,
}

/** Resolves segmented-control appearance from theme tokens and scoped overrides. */
object SegmentedControlDefaults {
    /** Resolves track background color for [enabled] state. */
    fun backgroundColor(enabled: Boolean = true): Int {
        val overrides = scoped()
        return resolveStateValue(
            enabled,
            overrides.containerColor,
            overrides.disabledContainerColor,
            Theme.colors.surface,
            Theme.colors.surface,
        )
    }

    /** Resolves selected-segment indicator color for [enabled] state. */
    fun indicatorColor(enabled: Boolean = true): Int {
        val overrides = scoped()
        return resolveStateValue(
            enabled,
            overrides.indicatorColor,
            overrides.disabledIndicatorColor,
            Theme.colors.secondaryContainer,
            Theme.stateColors.controlActivated.resolve(enabled = false),
        )
    }

    /** Resolves the track and indicator shape. */
    fun shape(): UiShape = scoped().shape ?: Theme.shapes.full

    /** Resolves unselected label color for [enabled] state. */
    fun textColor(enabled: Boolean = true): Int {
        val overrides = scoped()
        return resolveStateValue(
            enabled,
            overrides.contentColor,
            overrides.disabledContentColor,
            Theme.colors.onSurface,
            Theme.stateColors.secondaryText.resolve(enabled = false),
        )
    }

    /** Resolves selected label color for [enabled] state. */
    fun selectedTextColor(enabled: Boolean = true): Int {
        val overrides = scoped()
        return resolveStateValue(
            enabled,
            overrides.selectedContentColor,
            overrides.disabledSelectedContentColor,
            Theme.colors.onSecondaryContainer,
            Theme.colors.onSurfaceVariant,
        )
    }

    /** Resolves compatibility pressed feedback, or transparency while disabled. */
    fun rippleColor(enabled: Boolean = true): Int = when {
        !enabled -> 0x00000000
        else -> scoped().rippleColor ?: Theme.stateColors.controlHighlight.resolve(pressed = true)
    }

    /** Resolves interaction colors from the selected or unselected enabled label role. */
    internal fun stateLayerColors(selected: Boolean): UiStateLayerColors {
        val overrides = scoped()
        return if (selected) {
            overrides.selectedStateLayerColors
                ?: stateLayerColorsFor(overrides.selectedContentColor ?: Theme.colors.onSecondaryContainer)
        } else {
            overrides.unselectedStateLayerColors
                ?: stateLayerColorsFor(overrides.contentColor ?: Theme.colors.onSurface)
        }
    }

    /** Resolves label typography for [size]. */
    fun textStyle(size: SegmentedControlSize = SegmentedControlSize.Medium): UiTextStyle =
        scoped().textStyle ?: semanticTextStyle(size)

    /** Resolves minimum control height for [size]. */
    fun height(size: SegmentedControlSize = SegmentedControlSize.Medium): UiDp =
        scoped().minimumHeight ?: semanticHeight(size)

    /** Resolves horizontal segment content padding for [size]. */
    fun paddingHorizontal(size: SegmentedControlSize = SegmentedControlSize.Medium): UiDp =
        scoped().horizontalPadding ?: semanticHorizontalPadding(size)

    /** Resolves vertical segment content padding for [size]. */
    fun paddingVertical(size: SegmentedControlSize = SegmentedControlSize.Medium): UiDp =
        scoped().verticalPadding ?: semanticVerticalPadding(size)

    internal fun resolve(
        size: SegmentedControlSize,
        enabled: Boolean,
        instance: SegmentedControlOverrides,
    ): ResolvedSegmentedControlAppearance {
        val overrides = scoped().merge(instance)
        val contentColor = resolveStateValue(
            enabled,
            overrides.contentColor,
            overrides.disabledContentColor,
            Theme.colors.onSurface,
            Theme.stateColors.secondaryText.resolve(enabled = false),
        )
        val selectedContentColor = resolveStateValue(
            enabled,
            overrides.selectedContentColor,
            overrides.disabledSelectedContentColor,
            Theme.colors.onSecondaryContainer,
            Theme.colors.onSurfaceVariant,
        )
        return ResolvedSegmentedControlAppearance(
            containerColor = resolveStateValue(
                enabled,
                overrides.containerColor,
                overrides.disabledContainerColor,
                Theme.colors.surface,
                Theme.colors.surface,
            ),
            indicatorColor = resolveStateValue(
                enabled,
                overrides.indicatorColor,
                overrides.disabledIndicatorColor,
                Theme.colors.secondaryContainer,
                Theme.stateColors.controlActivated.resolve(enabled = false),
            ),
            contentColor = contentColor,
            selectedContentColor = selectedContentColor,
            shape = overrides.shape ?: Theme.shapes.full,
            textStyle = overrides.textStyle ?: semanticTextStyle(size),
            unselectedStateLayerColors = overrides.unselectedStateLayerColors
                ?: stateLayerColorsFor(overrides.contentColor ?: Theme.colors.onSurface),
            selectedStateLayerColors = overrides.selectedStateLayerColors
                ?: stateLayerColorsFor(overrides.selectedContentColor ?: Theme.colors.onSecondaryContainer),
            rippleColor = if (enabled) {
                overrides.rippleColor ?: Theme.stateColors.controlHighlight.resolve(pressed = true)
            } else {
                0x00000000
            },
            minimumHeight = overrides.minimumHeight ?: semanticHeight(size),
            horizontalPadding = overrides.horizontalPadding ?: semanticHorizontalPadding(size),
            verticalPadding = overrides.verticalPadding ?: semanticVerticalPadding(size),
        )
    }

    private fun semanticTextStyle(size: SegmentedControlSize): UiTextStyle = when (size) {
        SegmentedControlSize.Compact -> TextDefaults.labelMediumStyle()
        SegmentedControlSize.Medium -> TextDefaults.labelLargeStyle()
        SegmentedControlSize.Large -> TextDefaults.bodyLargeStyle()
    }

    private fun semanticHeight(size: SegmentedControlSize): UiDp = when (size) {
        SegmentedControlSize.Compact -> Theme.controls.segmentedControl.compactHeight
        SegmentedControlSize.Medium -> Theme.controls.segmentedControl.mediumHeight
        SegmentedControlSize.Large -> Theme.controls.segmentedControl.largeHeight
    }

    private fun semanticHorizontalPadding(size: SegmentedControlSize): UiDp = when (size) {
        SegmentedControlSize.Compact -> Theme.controls.segmentedControl.compactHorizontalPadding
        SegmentedControlSize.Medium -> Theme.controls.segmentedControl.mediumHorizontalPadding
        SegmentedControlSize.Large -> Theme.controls.segmentedControl.largeHorizontalPadding
    }

    private fun semanticVerticalPadding(size: SegmentedControlSize): UiDp = when (size) {
        SegmentedControlSize.Compact -> Theme.controls.segmentedControl.compactVerticalPadding
        SegmentedControlSize.Medium -> Theme.controls.segmentedControl.mediumVerticalPadding
        SegmentedControlSize.Large -> Theme.controls.segmentedControl.largeVerticalPadding
    }

    private fun scoped() = UiLocals.current(LocalSegmentedControlOverrides)
}

internal data class ResolvedSegmentedControlAppearance(
    val containerColor: Int,
    val indicatorColor: Int,
    val contentColor: Int,
    val selectedContentColor: Int,
    val shape: UiShape,
    val textStyle: UiTextStyle,
    val unselectedStateLayerColors: UiStateLayerColors,
    val selectedStateLayerColors: UiStateLayerColors,
    val rippleColor: Int,
    val minimumHeight: UiDp,
    val horizontalPadding: UiDp,
    val verticalPadding: UiDp,
)
