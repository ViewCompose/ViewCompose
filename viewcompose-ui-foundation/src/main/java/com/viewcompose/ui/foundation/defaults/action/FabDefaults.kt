package com.viewcompose.ui.foundation

import com.viewcompose.ui.unit.UiDp

import com.viewcompose.ui.shape.UiShape

/** Size tier used to select FAB bounds, icon size, and shape. */
enum class FabSize {
    Small,
    Medium,
    Large,
}

/**
 * Resolves FAB and extended-FAB defaults from the current theme.
 *
 * Regular FABs select bounds, icon size, and shape by [FabSize]. Extended FABs use a horizontal
 * icon-and-label treatment.
 */
object FabDefaults {
    /** Returns the current primary-container color. */
    fun containerColor(): Int = Theme.colors.primaryContainer

    /** Returns content color suitable for [containerColor]. */
    fun contentColor(): Int = Theme.colors.onPrimaryContainer

    /** Resolves square FAB bounds for [size]. */
    fun size(size: FabSize = FabSize.Medium): UiDp {
        return when (size) {
            FabSize.Small -> Theme.controls.fab.smallSize
            FabSize.Medium -> Theme.controls.fab.mediumSize
            FabSize.Large -> Theme.controls.fab.largeSize
        }
    }

    /** Resolves icon size for [size]. */
    fun iconSize(size: FabSize = FabSize.Medium): UiDp {
        return when (size) {
            FabSize.Small -> Theme.controls.fab.smallIconSize
            FabSize.Medium -> Theme.controls.fab.mediumIconSize
            FabSize.Large -> Theme.controls.fab.largeIconSize
        }
    }

    /** Resolves the semantic theme shape for [size]. */
    fun shape(size: FabSize = FabSize.Medium): UiShape {
        return when (size) {
            FabSize.Small -> Theme.shapes.medium
            FabSize.Medium -> Theme.shapes.large
            FabSize.Large -> Theme.shapes.extraLarge
        }
    }

    /** Returns resting FAB elevation. */
    fun elevation(): UiDp = Theme.controls.fab.elevation

    /** Returns minimum extended-FAB height. */
    fun extendedHeight(): UiDp = Theme.controls.fab.extendedHeight

    /** Returns the current large theme shape for an extended FAB. */
    fun extendedShape(): UiShape = Theme.shapes.large

    /** Returns horizontal content padding for an extended FAB. */
    fun extendedHorizontalPadding(): UiDp = Theme.controls.fab.extendedHorizontalPadding

    /** Returns spacing between extended-FAB icon and label. */
    fun extendedIconSpacing(): UiDp = Theme.controls.fab.extendedIconSpacing

    /** Returns the large label typography style. */
    fun extendedTextStyle(): UiTextStyle = TextDefaults.labelLargeStyle()

    /** Returns the current ripple color. */
    fun pressedColor(): Int = Theme.colors.ripple

    internal fun resolve(
        size: FabSize,
        instance: FloatingActionButtonOverrides,
    ): ResolvedFloatingActionButtonAppearance {
        val overrides = UiLocals.current(LocalFloatingActionButtonOverrides).merge(instance)
        val contentColor = overrides.contentColor ?: contentColor()
        return ResolvedFloatingActionButtonAppearance(
            containerColor = overrides.containerColor ?: containerColor(),
            contentColor = contentColor,
            size = size(size),
            shape = overrides.shape ?: shape(size),
            elevation = overrides.elevation ?: elevation(),
            rippleColor = overrides.rippleColor ?: pressedColor(),
            stateLayerColors = overrides.stateLayerColors ?: stateLayerColorsFor(contentColor),
        )
    }

    internal fun resolveExtended(
        instance: ExtendedFloatingActionButtonOverrides,
    ): ResolvedExtendedFloatingActionButtonAppearance {
        val overrides = UiLocals.current(LocalExtendedFloatingActionButtonOverrides).merge(instance)
        val contentColor = overrides.contentColor ?: contentColor()
        return ResolvedExtendedFloatingActionButtonAppearance(
            containerColor = overrides.containerColor ?: containerColor(),
            contentColor = contentColor,
            shape = overrides.shape ?: extendedShape(),
            elevation = overrides.elevation ?: elevation(),
            rippleColor = overrides.rippleColor ?: pressedColor(),
            stateLayerColors = overrides.stateLayerColors ?: stateLayerColorsFor(contentColor),
            textStyle = overrides.textStyle ?: extendedTextStyle(),
            iconSize = overrides.iconSize ?: iconSize(FabSize.Medium),
            height = overrides.height ?: extendedHeight(),
            horizontalPadding = overrides.horizontalPadding ?: extendedHorizontalPadding(),
            iconSpacing = overrides.iconSpacing ?: extendedIconSpacing(),
        )
    }
}

internal data class ResolvedFloatingActionButtonAppearance(
    val containerColor: Int,
    val contentColor: Int,
    val size: UiDp,
    val shape: UiShape,
    val elevation: UiDp,
    val rippleColor: Int,
    val stateLayerColors: com.viewcompose.ui.node.UiStateLayerColors,
)

internal data class ResolvedExtendedFloatingActionButtonAppearance(
    val containerColor: Int,
    val contentColor: Int,
    val shape: UiShape,
    val elevation: UiDp,
    val rippleColor: Int,
    val stateLayerColors: com.viewcompose.ui.node.UiStateLayerColors,
    val textStyle: UiTextStyle,
    val iconSize: UiDp,
    val height: UiDp,
    val horizontalPadding: UiDp,
    val iconSpacing: UiDp,
)
