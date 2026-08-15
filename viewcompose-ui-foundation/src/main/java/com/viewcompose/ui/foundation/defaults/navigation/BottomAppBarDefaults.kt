package com.viewcompose.ui.foundation

import com.viewcompose.ui.unit.UiDp

/** Default visual and size tokens for bottom app bars. */
object BottomAppBarDefaults {
    /** Returns the app-bar container color. */
    fun containerColor(): Int = scoped().containerColor ?: Theme.colors.surfaceContainer

    /** Returns the default descendant content color. */
    fun contentColor(): Int = scoped().contentColor ?: Theme.colors.onSurface

    /** Returns the app-bar height. */
    fun height(): UiDp = scoped().height ?: Theme.controls.appBar.bottomHeight

    /** Returns the horizontal padding around app-bar content. */
    fun horizontalPadding(): UiDp =
        scoped().horizontalPadding ?: Theme.controls.appBar.bottomHorizontalPadding

    /** Returns the app-bar elevation. */
    fun elevation(): UiDp = scoped().elevation ?: Theme.controls.appBar.bottomElevation

    internal fun resolve(instance: BottomAppBarOverrides): ResolvedBottomAppBarAppearance {
        val overrides = scoped().merge(instance)
        return ResolvedBottomAppBarAppearance(
            containerColor = overrides.containerColor ?: Theme.colors.surfaceContainer,
            contentColor = overrides.contentColor ?: Theme.colors.onSurface,
            height = overrides.height ?: Theme.controls.appBar.bottomHeight,
            horizontalPadding = overrides.horizontalPadding ?: Theme.controls.appBar.bottomHorizontalPadding,
            elevation = overrides.elevation ?: Theme.controls.appBar.bottomElevation,
        )
    }

    private fun scoped(): BottomAppBarOverrides = UiLocals.current(LocalBottomAppBarOverrides)
}

internal data class ResolvedBottomAppBarAppearance(
    val containerColor: Int,
    val contentColor: Int,
    val height: UiDp,
    val horizontalPadding: UiDp,
    val elevation: UiDp,
)
