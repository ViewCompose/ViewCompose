package com.viewcompose.ui.foundation

import com.viewcompose.ui.unit.UiDp

/** Default visual, typography, and size tokens for top app bars. */
object TopAppBarDefaults {
    /** Returns the app-bar container color. */
    fun containerColor(): Int = scoped().containerColor ?: Theme.colors.surface

    /** Returns the app-bar title color. */
    fun titleColor(): Int = scoped().titleColor ?: Theme.colors.onSurface

    /** Returns the default navigation-slot content color. */
    fun navigationIconColor(): Int = scoped().navigationIconColor ?: Theme.colors.onSurface

    /** Returns the default action-slot content color. */
    fun actionIconColor(): Int = scoped().actionIconColor ?: Theme.colors.onSurfaceVariant

    /** Returns the app-bar title text style. */
    fun titleStyle(): UiTextStyle = scoped().titleStyle ?: TextDefaults.titleMediumStyle()

    /** Returns the app-bar height. */
    fun height(): UiDp = scoped().height ?: Theme.controls.appBar.topHeight

    /** Returns the horizontal padding around app-bar content. */
    fun horizontalPadding(): UiDp =
        scoped().horizontalPadding ?: Theme.controls.appBar.topHorizontalPadding

    /** Returns the spacing between navigation content and the title. */
    fun titleStartPadding(): UiDp =
        scoped().titleStartPadding ?: Theme.controls.appBar.topTitleStartPadding

    internal fun resolve(instance: TopAppBarOverrides): ResolvedTopAppBarAppearance {
        val overrides = scoped().merge(instance)
        return ResolvedTopAppBarAppearance(
            containerColor = overrides.containerColor ?: Theme.colors.surface,
            titleColor = overrides.titleColor ?: Theme.colors.onSurface,
            navigationIconColor = overrides.navigationIconColor ?: Theme.colors.onSurface,
            actionIconColor = overrides.actionIconColor ?: Theme.colors.onSurfaceVariant,
            titleStyle = overrides.titleStyle ?: TextDefaults.titleMediumStyle(),
            height = overrides.height ?: Theme.controls.appBar.topHeight,
            horizontalPadding = overrides.horizontalPadding ?: Theme.controls.appBar.topHorizontalPadding,
            titleStartPadding = overrides.titleStartPadding ?: Theme.controls.appBar.topTitleStartPadding,
        )
    }

    private fun scoped(): TopAppBarOverrides = UiLocals.current(LocalTopAppBarOverrides)
}

internal data class ResolvedTopAppBarAppearance(
    val containerColor: Int,
    val titleColor: Int,
    val navigationIconColor: Int,
    val actionIconColor: Int,
    val titleStyle: UiTextStyle,
    val height: UiDp,
    val horizontalPadding: UiDp,
    val titleStartPadding: UiDp,
)
