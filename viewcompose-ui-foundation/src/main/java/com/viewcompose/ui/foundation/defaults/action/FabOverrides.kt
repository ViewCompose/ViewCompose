package com.viewcompose.ui.foundation

import com.viewcompose.ui.node.UiStateLayerColors
import com.viewcompose.ui.shape.UiShape
import com.viewcompose.ui.unit.UiDp

/**
 * Selectively replaces regular [FloatingActionButton] appearance.
 *
 * A `null` property inherits the nearest scoped value and then [FabDefaults]. The patch contains
 * no click behavior, semantic size tier, identity, modifier, or content.
 *
 * @property containerColor container ARGB color
 * @property contentColor default descendant content ARGB color
 * @property shape container shape after the selected [FabSize] recipe
 * @property elevation resting elevation in dp
 * @property rippleColor platform ripple ARGB color
 * @property stateLayerColors pressed, focused, and hovered ARGB colors
 * @throws IllegalArgumentException when [elevation] is negative
 */
data class FloatingActionButtonOverrides(
    val containerColor: Int? = null,
    val contentColor: Int? = null,
    val shape: UiShape? = null,
    val elevation: UiDp? = null,
    val rippleColor: Int? = null,
    val stateLayerColors: UiStateLayerColors? = null,
) {
    init {
        elevation.requireNonNegative("FloatingActionButtonOverrides.elevation")
    }

    /** Shared regular-FAB override values. */
    companion object {
        /** Shared empty patch used by the regular FAB no-override path. */
        val None: FloatingActionButtonOverrides = FloatingActionButtonOverrides()
    }
}

internal fun FloatingActionButtonOverrides.merge(
    nearest: FloatingActionButtonOverrides,
): FloatingActionButtonOverrides {
    if (nearest === FloatingActionButtonOverrides.None) return this
    if (this === FloatingActionButtonOverrides.None) return nearest
    return FloatingActionButtonOverrides(
        containerColor = nearest.containerColor ?: containerColor,
        contentColor = nearest.contentColor ?: contentColor,
        shape = nearest.shape ?: shape,
        elevation = nearest.elevation ?: elevation,
        rippleColor = nearest.rippleColor ?: rippleColor,
        stateLayerColors = nearest.stateLayerColors ?: stateLayerColors,
    )
}

internal val LocalFloatingActionButtonOverrides = uiLocalOf(
    debugName = "FloatingActionButtonOverrides",
    debugValueFormatter = FloatingActionButtonOverrides::toString,
) { FloatingActionButtonOverrides.None }

/**
 * Merges sparse regular-FAB [overrides] for [content].
 *
 * Nested scopes merge field by field, a [FloatingActionButton] instance wins over every scope, and
 * the previous value is restored when [content] returns or throws.
 *
 * @sample com.viewcompose.ui.foundation.samples.remainingComponentOverridesSample
 * @receiver active tree builder receiving the scoped content
 * @param overrides sparse appearance patch applied to descendant regular FABs
 * @param content subtree built synchronously with the merged patch
 */
fun UiTreeBuilder.ProvideFloatingActionButtonOverrides(
    overrides: FloatingActionButtonOverrides,
    content: UiTreeBuilder.() -> Unit,
) {
    ProvideLocal(
        LocalFloatingActionButtonOverrides,
        UiLocals.current(LocalFloatingActionButtonOverrides).merge(overrides),
        content,
    )
}

/**
 * Selectively replaces [ExtendedFloatingActionButton] appearance.
 *
 * Regular-FAB geometry is deliberately absent because extended FABs own a label, optional icon,
 * and independent horizontal layout. A `null` property inherits the nearest scope and then
 * [FabDefaults].
 *
 * @property containerColor container ARGB color
 * @property contentColor label and icon ARGB color
 * @property shape container shape
 * @property elevation resting elevation in dp
 * @property rippleColor platform ripple ARGB color
 * @property stateLayerColors pressed, focused, and hovered ARGB colors
 * @property textStyle label typography
 * @property iconSize square icon size in dp
 * @property height minimum container height in dp
 * @property horizontalPadding content padding on each horizontal edge in dp
 * @property iconSpacing gap between an icon and the label in dp
 * @throws IllegalArgumentException when a supplied dimension is negative
 */
data class ExtendedFloatingActionButtonOverrides(
    val containerColor: Int? = null,
    val contentColor: Int? = null,
    val shape: UiShape? = null,
    val elevation: UiDp? = null,
    val rippleColor: Int? = null,
    val stateLayerColors: UiStateLayerColors? = null,
    val textStyle: UiTextStyle? = null,
    val iconSize: UiDp? = null,
    val height: UiDp? = null,
    val horizontalPadding: UiDp? = null,
    val iconSpacing: UiDp? = null,
) {
    init {
        elevation.requireNonNegative("ExtendedFloatingActionButtonOverrides.elevation")
        iconSize.requireNonNegative("ExtendedFloatingActionButtonOverrides.iconSize")
        height.requireNonNegative("ExtendedFloatingActionButtonOverrides.height")
        horizontalPadding.requireNonNegative("ExtendedFloatingActionButtonOverrides.horizontalPadding")
        iconSpacing.requireNonNegative("ExtendedFloatingActionButtonOverrides.iconSpacing")
    }

    /** Shared extended-FAB override values. */
    companion object {
        /** Shared empty patch used by the extended-FAB no-override path. */
        val None: ExtendedFloatingActionButtonOverrides = ExtendedFloatingActionButtonOverrides()
    }
}

internal fun ExtendedFloatingActionButtonOverrides.merge(
    nearest: ExtendedFloatingActionButtonOverrides,
): ExtendedFloatingActionButtonOverrides {
    if (nearest === ExtendedFloatingActionButtonOverrides.None) return this
    if (this === ExtendedFloatingActionButtonOverrides.None) return nearest
    return ExtendedFloatingActionButtonOverrides(
        containerColor = nearest.containerColor ?: containerColor,
        contentColor = nearest.contentColor ?: contentColor,
        shape = nearest.shape ?: shape,
        elevation = nearest.elevation ?: elevation,
        rippleColor = nearest.rippleColor ?: rippleColor,
        stateLayerColors = nearest.stateLayerColors ?: stateLayerColors,
        textStyle = nearest.textStyle ?: textStyle,
        iconSize = nearest.iconSize ?: iconSize,
        height = nearest.height ?: height,
        horizontalPadding = nearest.horizontalPadding ?: horizontalPadding,
        iconSpacing = nearest.iconSpacing ?: iconSpacing,
    )
}

internal val LocalExtendedFloatingActionButtonOverrides = uiLocalOf(
    debugName = "ExtendedFloatingActionButtonOverrides",
    debugValueFormatter = ExtendedFloatingActionButtonOverrides::toString,
) { ExtendedFloatingActionButtonOverrides.None }

/**
 * Merges sparse extended-FAB [overrides] for [content].
 *
 * Nested scopes merge field by field and an [ExtendedFloatingActionButton] instance has the
 * highest precedence. Regular FABs are unaffected by this provider.
 *
 * @sample com.viewcompose.ui.foundation.samples.remainingComponentOverridesSample
 * @receiver active tree builder receiving the scoped content
 * @param overrides sparse appearance patch applied to descendant extended FABs
 * @param content subtree built synchronously with the merged patch
 */
fun UiTreeBuilder.ProvideExtendedFloatingActionButtonOverrides(
    overrides: ExtendedFloatingActionButtonOverrides,
    content: UiTreeBuilder.() -> Unit,
) {
    ProvideLocal(
        LocalExtendedFloatingActionButtonOverrides,
        UiLocals.current(LocalExtendedFloatingActionButtonOverrides).merge(overrides),
        content,
    )
}

private fun UiDp?.requireNonNegative(propertyName: String) {
    require(this == null || this >= UiDp.Zero) { "$propertyName must be non-negative." }
}
