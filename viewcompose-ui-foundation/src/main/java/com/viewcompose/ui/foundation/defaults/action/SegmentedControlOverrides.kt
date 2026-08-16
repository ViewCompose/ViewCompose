package com.viewcompose.ui.foundation

import com.viewcompose.ui.node.UiStateLayerColors
import com.viewcompose.ui.shape.UiShape
import com.viewcompose.ui.unit.UiDp

/**
 * Selectively replaces SegmentedControl appearance resolved by [SegmentedControlDefaults].
 *
 * @property containerColor enabled track ARGB color
 * @property disabledContainerColor disabled track ARGB color
 * @property indicatorColor enabled selected-segment ARGB color
 * @property disabledIndicatorColor disabled selected-segment ARGB color
 * @property contentColor enabled unselected-label ARGB color
 * @property disabledContentColor disabled unselected-label ARGB color
 * @property selectedContentColor enabled selected-label ARGB color
 * @property disabledSelectedContentColor disabled selected-label ARGB color
 * @property shape track and selected-indicator shape
 * @property textStyle label typography
 * @property unselectedStateLayerColors unselected pressed, focused, and hovered colors
 * @property selectedStateLayerColors selected pressed, focused, and hovered colors
 * @property minimumHeight minimum control height in dp
 * @property horizontalPadding segment padding on each horizontal edge in dp
 * @property verticalPadding segment padding on each vertical edge in dp
 * @throws IllegalArgumentException when a supplied dimension is negative
 */
data class SegmentedControlOverrides(
    val containerColor: Int? = null,
    val disabledContainerColor: Int? = null,
    val indicatorColor: Int? = null,
    val disabledIndicatorColor: Int? = null,
    val contentColor: Int? = null,
    val disabledContentColor: Int? = null,
    val selectedContentColor: Int? = null,
    val disabledSelectedContentColor: Int? = null,
    val shape: UiShape? = null,
    val textStyle: UiTextStyle? = null,
    val unselectedStateLayerColors: UiStateLayerColors? = null,
    val selectedStateLayerColors: UiStateLayerColors? = null,
    val minimumHeight: UiDp? = null,
    val horizontalPadding: UiDp? = null,
    val verticalPadding: UiDp? = null,
) {
    init {
        minimumHeight.requireNonNegative("SegmentedControlOverrides.minimumHeight")
        horizontalPadding.requireNonNegative("SegmentedControlOverrides.horizontalPadding")
        verticalPadding.requireNonNegative("SegmentedControlOverrides.verticalPadding")
    }

    /** Shared SegmentedControl override values. */
    companion object {
        /** Shared empty SegmentedControl appearance patch. */
        val None: SegmentedControlOverrides = SegmentedControlOverrides()
    }
}

internal fun SegmentedControlOverrides.merge(nearest: SegmentedControlOverrides): SegmentedControlOverrides {
    if (nearest === SegmentedControlOverrides.None) return this
    if (this === SegmentedControlOverrides.None) return nearest
    return SegmentedControlOverrides(
        containerColor = nearest.containerColor ?: containerColor,
        disabledContainerColor = nearest.disabledContainerColor ?: disabledContainerColor,
        indicatorColor = nearest.indicatorColor ?: indicatorColor,
        disabledIndicatorColor = nearest.disabledIndicatorColor ?: disabledIndicatorColor,
        contentColor = nearest.contentColor ?: contentColor,
        disabledContentColor = nearest.disabledContentColor ?: disabledContentColor,
        selectedContentColor = nearest.selectedContentColor ?: selectedContentColor,
        disabledSelectedContentColor = nearest.disabledSelectedContentColor ?: disabledSelectedContentColor,
        shape = nearest.shape ?: shape,
        textStyle = nearest.textStyle ?: textStyle,
        unselectedStateLayerColors = nearest.unselectedStateLayerColors ?: unselectedStateLayerColors,
        selectedStateLayerColors = nearest.selectedStateLayerColors ?: selectedStateLayerColors,
        minimumHeight = nearest.minimumHeight ?: minimumHeight,
        horizontalPadding = nearest.horizontalPadding ?: horizontalPadding,
        verticalPadding = nearest.verticalPadding ?: verticalPadding,
    )
}

internal val LocalSegmentedControlOverrides = uiLocalOf(
    debugName = "SegmentedControlOverrides",
    debugValueFormatter = SegmentedControlOverrides::toString,
) { SegmentedControlOverrides.None }

/**
 * Merges sparse [overrides] into SegmentedControl defaults for [content].
 *
 * Nested providers merge field by field and instance overrides retain the highest precedence.
 *
 * @sample com.viewcompose.ui.foundation.samples.componentOverridesSample
 * @receiver active tree builder receiving the scoped content
 * @param overrides sparse appearance patch applied to descendant SegmentedControl components
 * @param content subtree built synchronously with the merged patch
 */
fun UiTreeBuilder.ProvideSegmentedControlOverrides(
    overrides: SegmentedControlOverrides,
    content: UiTreeBuilder.() -> Unit,
) {
    ProvideLocal(
        local = LocalSegmentedControlOverrides,
        value = UiLocals.current(LocalSegmentedControlOverrides).merge(overrides),
        content = content,
    )
}

private fun UiDp?.requireNonNegative(propertyName: String) {
    require(this == null || this >= UiDp.Zero) { "$propertyName must be non-negative." }
}
