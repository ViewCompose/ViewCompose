package com.viewcompose.ui.foundation

import com.viewcompose.ui.node.UiStateLayerColors
import com.viewcompose.ui.shape.UiShape
import com.viewcompose.ui.unit.UiDp

/**
 * Selectively replaces low-frequency appearance values resolved by [Button].
 *
 * A `null` property inherits the value from the surrounding [ProvideButtonOverrides] scope or,
 * when no scope supplies it, from [ButtonDefaults]. Instance overrides are merged after scoped
 * values. Appearance fields apply every [ButtonVariant]; use an instance patch when only one
 * variant should differ. The model contains no enabled state, callbacks, content, identity, or
 * input policy.
 *
 * @property containerColor enabled container ARGB color
 * @property disabledContainerColor disabled container ARGB color
 * @property contentColor enabled label and decorative-icon ARGB color
 * @property disabledContentColor disabled label and decorative-icon ARGB color
 * @property borderColor enabled border ARGB color
 * @property disabledBorderColor disabled border ARGB color
 * @property borderWidth border thickness in dp
 * @property shape container shape
 * @property stateLayerColors pressed, focused, and hovered ARGB colors
 * @property textStyle label typography
 * @property minimumHeight minimum effective action height in dp
 * @property visualHeight visible container height centered inside the effective bounds
 * @property horizontalPadding content padding on each horizontal edge in dp
 * @property verticalPadding content padding on each vertical edge in dp
 * @property iconSize square decorative-icon size in dp
 * @property iconSpacing gap between an icon and the label in dp
 * @throws IllegalArgumentException when a supplied dimension is negative
 */
data class ButtonOverrides(
    val containerColor: Int? = null,
    val disabledContainerColor: Int? = null,
    val contentColor: Int? = null,
    val disabledContentColor: Int? = null,
    val borderColor: Int? = null,
    val disabledBorderColor: Int? = null,
    val borderWidth: UiDp? = null,
    val shape: UiShape? = null,
    val stateLayerColors: UiStateLayerColors? = null,
    val textStyle: UiTextStyle? = null,
    val minimumHeight: UiDp? = null,
    val visualHeight: UiDp? = null,
    val horizontalPadding: UiDp? = null,
    val verticalPadding: UiDp? = null,
    val iconSize: UiDp? = null,
    val iconSpacing: UiDp? = null,
) {
    init {
        borderWidth.requireNonNegative("ButtonOverrides.borderWidth")
        minimumHeight.requireNonNegative("ButtonOverrides.minimumHeight")
        visualHeight.requireNonNegative("ButtonOverrides.visualHeight")
        horizontalPadding.requireNonNegative("ButtonOverrides.horizontalPadding")
        verticalPadding.requireNonNegative("ButtonOverrides.verticalPadding")
        iconSize.requireNonNegative("ButtonOverrides.iconSize")
        iconSpacing.requireNonNegative("ButtonOverrides.iconSpacing")
    }

    /** Shared Button override values. */
    companion object {
        /** Shared empty patch used by component defaults without allocating during rendering. */
        val None: ButtonOverrides = ButtonOverrides()
    }
}

internal fun ButtonOverrides.merge(nearest: ButtonOverrides): ButtonOverrides {
    if (nearest === ButtonOverrides.None) return this
    if (this === ButtonOverrides.None) return nearest
    return ButtonOverrides(
        containerColor = nearest.containerColor ?: containerColor,
        disabledContainerColor = nearest.disabledContainerColor ?: disabledContainerColor,
        contentColor = nearest.contentColor ?: contentColor,
        disabledContentColor = nearest.disabledContentColor ?: disabledContentColor,
        borderColor = nearest.borderColor ?: borderColor,
        disabledBorderColor = nearest.disabledBorderColor ?: disabledBorderColor,
        borderWidth = nearest.borderWidth ?: borderWidth,
        shape = nearest.shape ?: shape,
        stateLayerColors = nearest.stateLayerColors ?: stateLayerColors,
        textStyle = nearest.textStyle ?: textStyle,
        minimumHeight = nearest.minimumHeight ?: minimumHeight,
        visualHeight = nearest.visualHeight ?: visualHeight,
        horizontalPadding = nearest.horizontalPadding ?: horizontalPadding,
        verticalPadding = nearest.verticalPadding ?: verticalPadding,
        iconSize = nearest.iconSize ?: iconSize,
        iconSpacing = nearest.iconSpacing ?: iconSpacing,
    )
}

internal val LocalButtonOverrides = uiLocalOf(
    debugName = "ButtonOverrides",
    debugValueFormatter = ButtonOverrides::toString,
) { ButtonOverrides.None }

/**
 * Merges sparse [overrides] into Button defaults for [content].
 *
 * Nested providers merge field by field; an unspecified inner property preserves the nearest
 * outer value. A [Button] instance can replace either value through its own `overrides` argument.
 * The previous scope is restored after [content] returns, including when building throws.
 *
 * @sample com.viewcompose.ui.foundation.samples.componentOverridesSample
 * @receiver active tree builder receiving the scoped content
 * @param overrides sparse appearance patch applied to descendant Button components
 * @param content subtree built synchronously with the merged patch
 */
fun UiTreeBuilder.ProvideButtonOverrides(
    overrides: ButtonOverrides,
    content: UiTreeBuilder.() -> Unit,
) {
    ProvideLocal(
        local = LocalButtonOverrides,
        value = UiLocals.current(LocalButtonOverrides).merge(overrides),
        content = content,
    )
}

/**
 * Selectively replaces low-frequency appearance values resolved by [IconButton].
 *
 * IconButton has an independent scope so a Button customization cannot accidentally recolor
 * icon-only actions. A `null` property inherits the scoped or [IconButtonDefaults] value.
 *
 * @property containerColor enabled container ARGB color
 * @property disabledContainerColor disabled container ARGB color
 * @property contentColor enabled icon ARGB color
 * @property disabledContentColor disabled icon ARGB color
 * @property borderColor enabled border ARGB color
 * @property disabledBorderColor disabled border ARGB color
 * @property borderWidth border thickness in dp
 * @property shape container shape
 * @property stateLayerColors pressed, focused, and hovered ARGB colors
 * @property size square effective bounds in dp
 * @property contentPadding uniform icon inset in dp
 * @throws IllegalArgumentException when a supplied dimension is negative
 */
data class IconButtonOverrides(
    val containerColor: Int? = null,
    val disabledContainerColor: Int? = null,
    val contentColor: Int? = null,
    val disabledContentColor: Int? = null,
    val borderColor: Int? = null,
    val disabledBorderColor: Int? = null,
    val borderWidth: UiDp? = null,
    val shape: UiShape? = null,
    val stateLayerColors: UiStateLayerColors? = null,
    val size: UiDp? = null,
    val contentPadding: UiDp? = null,
) {
    init {
        borderWidth.requireNonNegative("IconButtonOverrides.borderWidth")
        size.requireNonNegative("IconButtonOverrides.size")
        contentPadding.requireNonNegative("IconButtonOverrides.contentPadding")
    }

    /** Shared IconButton override values. */
    companion object {
        /** Shared empty patch used by component defaults without allocating during rendering. */
        val None: IconButtonOverrides = IconButtonOverrides()
    }
}

internal fun IconButtonOverrides.merge(nearest: IconButtonOverrides): IconButtonOverrides {
    if (nearest === IconButtonOverrides.None) return this
    if (this === IconButtonOverrides.None) return nearest
    return IconButtonOverrides(
        containerColor = nearest.containerColor ?: containerColor,
        disabledContainerColor = nearest.disabledContainerColor ?: disabledContainerColor,
        contentColor = nearest.contentColor ?: contentColor,
        disabledContentColor = nearest.disabledContentColor ?: disabledContentColor,
        borderColor = nearest.borderColor ?: borderColor,
        disabledBorderColor = nearest.disabledBorderColor ?: disabledBorderColor,
        borderWidth = nearest.borderWidth ?: borderWidth,
        shape = nearest.shape ?: shape,
        stateLayerColors = nearest.stateLayerColors ?: stateLayerColors,
        size = nearest.size ?: size,
        contentPadding = nearest.contentPadding ?: contentPadding,
    )
}

internal val LocalIconButtonOverrides = uiLocalOf(
    debugName = "IconButtonOverrides",
    debugValueFormatter = IconButtonOverrides::toString,
) { IconButtonOverrides.None }

/**
 * Merges sparse [overrides] into IconButton defaults for [content].
 *
 * Nested providers merge field by field and the previous scope is restored after [content]
 * returns. Instance overrides retain the highest precedence.
 *
 * @sample com.viewcompose.ui.foundation.samples.componentOverridesSample
 * @receiver active tree builder receiving the scoped content
 * @param overrides sparse appearance patch applied to descendant IconButton components
 * @param content subtree built synchronously with the merged patch
 */
fun UiTreeBuilder.ProvideIconButtonOverrides(
    overrides: IconButtonOverrides,
    content: UiTreeBuilder.() -> Unit,
) {
    ProvideLocal(
        local = LocalIconButtonOverrides,
        value = UiLocals.current(LocalIconButtonOverrides).merge(overrides),
        content = content,
    )
}

private fun UiDp?.requireNonNegative(propertyName: String) {
    require(this == null || this >= UiDp.Zero) { "$propertyName must be non-negative." }
}
