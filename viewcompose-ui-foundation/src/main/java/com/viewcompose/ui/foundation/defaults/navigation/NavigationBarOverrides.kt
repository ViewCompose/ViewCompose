package com.viewcompose.ui.foundation

import com.viewcompose.ui.node.UiStateLayerColors
import com.viewcompose.ui.unit.UiDp

/**
 * Selectively replaces NavigationBar appearance without carrying selection or destination content.
 *
 * @property containerColor bar container ARGB color
 * @property selectedIconColor selected icon ARGB color
 * @property unselectedIconColor unselected icon ARGB color
 * @property selectedLabelColor selected label ARGB color
 * @property unselectedLabelColor unselected label ARGB color
 * @property indicatorColor selection-indicator ARGB color
 * @property selectedStateLayerColors selected-destination pressed, focused, and hovered colors
 * @property unselectedStateLayerColors unselected-destination pressed, focused, and hovered colors
 * @property iconSize square destination-icon size in dp
 * @property labelStyle destination-label typography
 * @property badgeColor badge container ARGB color
 * @property badgeTextColor badge text ARGB color
 * @property height bar height in dp
 * @throws IllegalArgumentException when a supplied dimension is negative
 */
data class NavigationBarOverrides(
    val containerColor: Int? = null,
    val selectedIconColor: Int? = null,
    val unselectedIconColor: Int? = null,
    val selectedLabelColor: Int? = null,
    val unselectedLabelColor: Int? = null,
    val indicatorColor: Int? = null,
    val selectedStateLayerColors: UiStateLayerColors? = null,
    val unselectedStateLayerColors: UiStateLayerColors? = null,
    val iconSize: UiDp? = null,
    val labelStyle: UiTextStyle? = null,
    val badgeColor: Int? = null,
    val badgeTextColor: Int? = null,
    val height: UiDp? = null,
) {
    init {
        iconSize.requireNonNegative("NavigationBarOverrides.iconSize")
        height.requireNonNegative("NavigationBarOverrides.height")
    }

    /** Shared NavigationBar override values. */
    companion object {
        /** Shared empty NavigationBar appearance patch. */
        val None: NavigationBarOverrides = NavigationBarOverrides()
    }
}

internal fun NavigationBarOverrides.merge(nearest: NavigationBarOverrides): NavigationBarOverrides {
    if (nearest === NavigationBarOverrides.None) return this
    if (this === NavigationBarOverrides.None) return nearest
    return NavigationBarOverrides(
        containerColor = nearest.containerColor ?: containerColor,
        selectedIconColor = nearest.selectedIconColor ?: selectedIconColor,
        unselectedIconColor = nearest.unselectedIconColor ?: unselectedIconColor,
        selectedLabelColor = nearest.selectedLabelColor ?: selectedLabelColor,
        unselectedLabelColor = nearest.unselectedLabelColor ?: unselectedLabelColor,
        indicatorColor = nearest.indicatorColor ?: indicatorColor,
        selectedStateLayerColors = nearest.selectedStateLayerColors ?: selectedStateLayerColors,
        unselectedStateLayerColors = nearest.unselectedStateLayerColors ?: unselectedStateLayerColors,
        iconSize = nearest.iconSize ?: iconSize,
        labelStyle = nearest.labelStyle ?: labelStyle,
        badgeColor = nearest.badgeColor ?: badgeColor,
        badgeTextColor = nearest.badgeTextColor ?: badgeTextColor,
        height = nearest.height ?: height,
    )
}

internal val LocalNavigationBarOverrides = uiLocalOf(
    debugName = "NavigationBarOverrides",
    debugValueFormatter = NavigationBarOverrides::toString,
) { NavigationBarOverrides.None }

/**
 * Merges sparse [overrides] into NavigationBar defaults for [content].
 *
 * Nested scopes merge field by field, instance patches retain the highest precedence, and
 * destination identity, content, callbacks, and selection remain explicit component arguments.
 *
 * @sample com.viewcompose.ui.foundation.samples.componentOverridesSample
 * @receiver active tree builder receiving the scoped content
 * @param overrides sparse appearance patch applied to descendant NavigationBar components
 * @param content subtree built synchronously with the merged patch
 */
fun UiTreeBuilder.ProvideNavigationBarOverrides(
    overrides: NavigationBarOverrides,
    content: UiTreeBuilder.() -> Unit,
) {
    ProvideLocal(
        LocalNavigationBarOverrides,
        UiLocals.current(LocalNavigationBarOverrides).merge(overrides),
        content,
    )
}

private fun UiDp?.requireNonNegative(propertyName: String) {
    require(this == null || this >= UiDp.Zero) { "$propertyName must be non-negative." }
}
