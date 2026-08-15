package com.viewcompose.ui.foundation

import com.viewcompose.ui.node.UiStateLayerColors
import com.viewcompose.ui.unit.UiDp
import com.viewcompose.ui.unit.UiSp

/** Resolves NavigationBar appearance from theme tokens and scoped overrides. */
object NavigationBarDefaults {
    /** Returns the navigation-bar container color. */
    fun containerColor(): Int = scoped().containerColor ?: Theme.colors.surfaceContainer

    /** Returns the icon color for a selected destination. */
    fun selectedIconColor(): Int = scoped().selectedIconColor ?: Theme.colors.onSecondaryContainer

    /** Returns the icon color for an unselected destination. */
    fun unselectedIconColor(): Int = scoped().unselectedIconColor ?: Theme.colors.onSurfaceVariant

    /** Returns the label color for a selected destination. */
    fun selectedLabelColor(): Int = scoped().selectedLabelColor ?: Theme.colors.onSecondaryContainer

    /** Returns the label color for an unselected destination. */
    fun unselectedLabelColor(): Int = scoped().unselectedLabelColor ?: Theme.colors.onSurfaceVariant

    /** Returns the selection-indicator color. */
    fun indicatorColor(): Int = scoped().indicatorColor ?: Theme.colors.secondaryContainer

    /** Returns the navigation-bar height. */
    fun height(): UiDp = scoped().height ?: Theme.controls.navigationBar.height

    /** Returns the square size of a destination icon. */
    fun iconSize(): UiDp = scoped().iconSize ?: Theme.controls.navigationBar.iconSize

    /** Returns the destination-label text style. */
    fun labelStyle(): UiTextStyle = scoped().labelStyle ?: semanticLabelStyle()

    /** Returns the destination-label font size. */
    fun labelSizeSp(): UiSp = labelStyle().fontSizeSp

    /** Returns the notification-badge container color. */
    fun badgeColor(): Int = scoped().badgeColor ?: Theme.colors.error

    /** Returns the notification-badge text color. */
    fun badgeTextColor(): Int = scoped().badgeTextColor ?: Theme.colors.onError

    internal fun resolve(instance: NavigationBarOverrides): ResolvedNavigationBarAppearance {
        val overrides = scoped().merge(instance)
        return ResolvedNavigationBarAppearance(
            containerColor = overrides.containerColor ?: Theme.colors.surfaceContainer,
            selectedIconColor = overrides.selectedIconColor ?: Theme.colors.onSecondaryContainer,
            unselectedIconColor = overrides.unselectedIconColor ?: Theme.colors.onSurfaceVariant,
            selectedLabelColor = overrides.selectedLabelColor ?: Theme.colors.onSecondaryContainer,
            unselectedLabelColor = overrides.unselectedLabelColor ?: Theme.colors.onSurfaceVariant,
            indicatorColor = overrides.indicatorColor ?: Theme.colors.secondaryContainer,
            selectedStateLayerColors = overrides.selectedStateLayerColors
                ?: stateLayerColorsFor(overrides.selectedIconColor ?: Theme.colors.onSecondaryContainer),
            unselectedStateLayerColors = overrides.unselectedStateLayerColors
                ?: stateLayerColorsFor(overrides.unselectedIconColor ?: Theme.colors.onSurfaceVariant),
            iconSize = overrides.iconSize ?: Theme.controls.navigationBar.iconSize,
            labelStyle = overrides.labelStyle ?: semanticLabelStyle(),
            badgeColor = overrides.badgeColor ?: Theme.colors.error,
            badgeTextColor = overrides.badgeTextColor ?: Theme.colors.onError,
            height = overrides.height ?: Theme.controls.navigationBar.height,
        )
    }

    private fun semanticLabelStyle(): UiTextStyle = TextDefaults.labelSmallStyle().copy(
        fontSizeSp = Theme.controls.navigationBar.labelSizeSp,
    )

    private fun scoped() = UiLocals.current(LocalNavigationBarOverrides)
}

internal data class ResolvedNavigationBarAppearance(
    val containerColor: Int,
    val selectedIconColor: Int,
    val unselectedIconColor: Int,
    val selectedLabelColor: Int,
    val unselectedLabelColor: Int,
    val indicatorColor: Int,
    val selectedStateLayerColors: UiStateLayerColors,
    val unselectedStateLayerColors: UiStateLayerColors,
    val iconSize: UiDp,
    val labelStyle: UiTextStyle,
    val badgeColor: Int,
    val badgeTextColor: Int,
    val height: UiDp,
)
