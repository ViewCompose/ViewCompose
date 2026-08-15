package com.viewcompose.ui.foundation

import com.viewcompose.ui.node.collection.TabIndicatorPosition
import com.viewcompose.ui.node.collection.TabIndicatorWidthMode
import com.viewcompose.ui.unit.UiDp

/**
 * Selectively replaces TabRow appearance and visual layout policy.
 *
 * Selection, callbacks, pager synchronization, tab identity, and tab content remain explicit
 * [TabRow] contracts and are not carried by this patch.
 *
 * @property indicatorColor selected-tab indicator ARGB color
 * @property indicatorHeight indicator thickness in dp
 * @property indicatorCornerRadius indicator corner radius in dp
 * @property indicatorPosition indicator placement within the tab row
 * @property indicatorWidthMode rule used to derive indicator width
 * @property indicatorFixedWidth fixed indicator width in dp when selected by [indicatorWidthMode]
 * @property containerColor row container ARGB color
 * @property scrollable whether overflowing items use horizontal scrolling
 * @property equalWidth whether available width is distributed equally between items
 * @property rippleColor item interaction-feedback ARGB color
 * @property itemSpacing gap between adjacent items in dp
 * @property itemPaddingHorizontal item padding on each horizontal edge in dp
 * @property itemPaddingVertical item padding on each vertical edge in dp
 * @property minimumItemWidth minimum item width in dp
 * @throws IllegalArgumentException when a supplied dimension is negative
 */
data class TabRowOverrides(
    val indicatorColor: Int? = null,
    val indicatorHeight: UiDp? = null,
    val indicatorCornerRadius: UiDp? = null,
    val indicatorPosition: TabIndicatorPosition? = null,
    val indicatorWidthMode: TabIndicatorWidthMode? = null,
    val indicatorFixedWidth: UiDp? = null,
    val containerColor: Int? = null,
    val scrollable: Boolean? = null,
    val equalWidth: Boolean? = null,
    val rippleColor: Int? = null,
    val itemSpacing: UiDp? = null,
    val itemPaddingHorizontal: UiDp? = null,
    val itemPaddingVertical: UiDp? = null,
    val minimumItemWidth: UiDp? = null,
) {
    init {
        indicatorHeight.requireNonNegative("TabRowOverrides.indicatorHeight")
        indicatorCornerRadius.requireNonNegative("TabRowOverrides.indicatorCornerRadius")
        indicatorFixedWidth.requireNonNegative("TabRowOverrides.indicatorFixedWidth")
        itemSpacing.requireNonNegative("TabRowOverrides.itemSpacing")
        itemPaddingHorizontal.requireNonNegative("TabRowOverrides.itemPaddingHorizontal")
        itemPaddingVertical.requireNonNegative("TabRowOverrides.itemPaddingVertical")
        minimumItemWidth.requireNonNegative("TabRowOverrides.minimumItemWidth")
    }

    /** Shared TabRow override values. */
    companion object {
        /** Shared empty TabRow appearance patch. */
        val None: TabRowOverrides = TabRowOverrides()
    }
}

internal fun TabRowOverrides.merge(nearest: TabRowOverrides): TabRowOverrides {
    if (nearest === TabRowOverrides.None) return this
    if (this === TabRowOverrides.None) return nearest
    return TabRowOverrides(
        indicatorColor = nearest.indicatorColor ?: indicatorColor,
        indicatorHeight = nearest.indicatorHeight ?: indicatorHeight,
        indicatorCornerRadius = nearest.indicatorCornerRadius ?: indicatorCornerRadius,
        indicatorPosition = nearest.indicatorPosition ?: indicatorPosition,
        indicatorWidthMode = nearest.indicatorWidthMode ?: indicatorWidthMode,
        indicatorFixedWidth = nearest.indicatorFixedWidth ?: indicatorFixedWidth,
        containerColor = nearest.containerColor ?: containerColor,
        scrollable = nearest.scrollable ?: scrollable,
        equalWidth = nearest.equalWidth ?: equalWidth,
        rippleColor = nearest.rippleColor ?: rippleColor,
        itemSpacing = nearest.itemSpacing ?: itemSpacing,
        itemPaddingHorizontal = nearest.itemPaddingHorizontal ?: itemPaddingHorizontal,
        itemPaddingVertical = nearest.itemPaddingVertical ?: itemPaddingVertical,
        minimumItemWidth = nearest.minimumItemWidth ?: minimumItemWidth,
    )
}

internal val LocalTabRowOverrides = uiLocalOf(
    debugName = "TabRowOverrides",
    debugValueFormatter = TabRowOverrides::toString,
) { TabRowOverrides.None }

/**
 * Merges sparse [overrides] into TabRow defaults for [content].
 *
 * Nested scopes merge field by field, instance patches retain the highest precedence, and tab
 * identity, selection, and content remain explicit [TabRow] arguments.
 *
 * @sample com.viewcompose.ui.foundation.samples.componentOverridesSample
 * @receiver active tree builder receiving the scoped content
 * @param overrides sparse appearance patch applied to descendant TabRow components
 * @param content subtree built synchronously with the merged patch
 */
fun UiTreeBuilder.ProvideTabRowOverrides(
    overrides: TabRowOverrides,
    content: UiTreeBuilder.() -> Unit,
) {
    ProvideLocal(LocalTabRowOverrides, UiLocals.current(LocalTabRowOverrides).merge(overrides), content)
}

private fun UiDp?.requireNonNegative(propertyName: String) {
    require(this == null || this >= UiDp.Zero) { "$propertyName must be non-negative." }
}
