package com.viewcompose.ui.foundation

import com.viewcompose.ui.node.collection.TabIndicatorPosition
import com.viewcompose.ui.node.collection.TabIndicatorWidthMode
import com.viewcompose.ui.node.UiStateLayerColors
import com.viewcompose.ui.unit.UiDp

/** Resolves TabRow appearance from theme tokens and scoped overrides. */
object TabRowDefaults {
    /** Returns the tab-row container color. */
    fun containerColor(): Int = scoped().containerColor ?: Theme.colors.surface

    /** Returns the active-tab indicator color. */
    fun indicatorColor(): Int = scoped().indicatorColor ?: Theme.colors.primary

    /** Returns the content color for inactive tabs. */
    fun inactiveContentColor(): Int = Theme.colors.onSurfaceVariant

    /** Returns the active-tab indicator height. */
    fun indicatorHeight(): UiDp = scoped().indicatorHeight ?: 3.dp

    /** Returns the active-tab indicator corner radius. */
    fun indicatorCornerRadius(): UiDp = scoped().indicatorCornerRadius ?: 2.dp

    /** Returns the horizontal content padding for each tab. */
    fun itemPaddingHorizontal(): UiDp = scoped().itemPaddingHorizontal ?: 16.dp

    /** Returns the vertical content padding for each tab. */
    fun itemPaddingVertical(): UiDp = scoped().itemPaddingVertical ?: 12.dp

    /** Returns the minimum width allocated to a tab. */
    fun minItemWidth(): UiDp = scoped().minimumItemWidth ?: 48.dp

    internal fun resolve(instance: TabRowOverrides): ResolvedTabRowAppearance {
        val overrides = scoped().merge(instance)
        return ResolvedTabRowAppearance(
            indicatorColor = overrides.indicatorColor ?: Theme.colors.primary,
            indicatorHeight = overrides.indicatorHeight ?: 3.dp,
            indicatorCornerRadius = overrides.indicatorCornerRadius ?: 2.dp,
            indicatorPosition = overrides.indicatorPosition ?: TabIndicatorPosition.Bottom,
            indicatorWidthMode = overrides.indicatorWidthMode ?: TabIndicatorWidthMode.MatchItem,
            indicatorFixedWidth = overrides.indicatorFixedWidth ?: UiDp.Zero,
            containerColor = overrides.containerColor ?: Theme.colors.surface,
            scrollable = overrides.scrollable ?: false,
            equalWidth = overrides.equalWidth ?: true,
            unselectedStateLayerColors = overrides.unselectedStateLayerColors
                ?: stateLayerColorsFor(Theme.colors.onSurfaceVariant),
            selectedStateLayerColors = overrides.selectedStateLayerColors
                ?: stateLayerColorsFor(Theme.colors.primary),
            itemSpacing = overrides.itemSpacing ?: UiDp.Zero,
            itemPaddingHorizontal = overrides.itemPaddingHorizontal ?: 16.dp,
            itemPaddingVertical = overrides.itemPaddingVertical ?: 12.dp,
            minimumItemWidth = overrides.minimumItemWidth ?: 48.dp,
        )
    }

    private fun scoped() = UiLocals.current(LocalTabRowOverrides)
}

internal data class ResolvedTabRowAppearance(
    val indicatorColor: Int,
    val indicatorHeight: UiDp,
    val indicatorCornerRadius: UiDp,
    val indicatorPosition: TabIndicatorPosition,
    val indicatorWidthMode: TabIndicatorWidthMode,
    val indicatorFixedWidth: UiDp,
    val containerColor: Int,
    val scrollable: Boolean,
    val equalWidth: Boolean,
    val unselectedStateLayerColors: UiStateLayerColors,
    val selectedStateLayerColors: UiStateLayerColors,
    val itemSpacing: UiDp,
    val itemPaddingHorizontal: UiDp,
    val itemPaddingVertical: UiDp,
    val minimumItemWidth: UiDp,
)
