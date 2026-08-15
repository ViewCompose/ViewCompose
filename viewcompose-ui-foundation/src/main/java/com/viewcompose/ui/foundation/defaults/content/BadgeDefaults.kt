package com.viewcompose.ui.foundation

import com.viewcompose.ui.unit.UiDp
import com.viewcompose.ui.shape.UiShape

/** Default color, size, and typography tokens for badge components. */
object BadgeDefaults {
    /** Returns the badge container color for the current theme. */
    fun containerColor(): Int = scoped().containerColor ?: Theme.colors.error

    /** Returns the content color rendered on the badge container. */
    fun contentColor(): Int = scoped().contentColor ?: Theme.colors.onError

    /** Returns the diameter of a badge that has no textual content. */
    fun dotSize(): UiDp = scoped().dotSize ?: Theme.controls.badge.dotSize

    /** Returns the height of a badge that contains text. */
    fun pillHeight(): UiDp = scoped().pillHeight ?: Theme.controls.badge.pillHeight

    /** Returns the minimum width of a badge that contains text. */
    fun pillMinWidth(): UiDp = scoped().pillMinWidth ?: Theme.controls.badge.pillMinWidth

    /** Returns the horizontal padding applied around badge text. */
    fun pillHorizontalPadding(): UiDp =
        scoped().pillHorizontalPadding ?: Theme.controls.badge.pillHorizontalPadding

    /** Returns the text style used for badge content. */
    fun textStyle(): UiTextStyle = scoped().textStyle ?: TextDefaults.labelSmallStyle()

    /** Returns the current full shape used by dot and labeled badges. */
    fun shape(): UiShape = scoped().shape ?: Theme.shapes.full

    internal fun resolve(instance: BadgeOverrides): ResolvedBadgeAppearance {
        val overrides = scoped().merge(instance)
        return ResolvedBadgeAppearance(
            containerColor = overrides.containerColor ?: Theme.colors.error,
            contentColor = overrides.contentColor ?: Theme.colors.onError,
            textStyle = overrides.textStyle ?: TextDefaults.labelSmallStyle(),
            shape = overrides.shape ?: Theme.shapes.full,
            dotSize = overrides.dotSize ?: Theme.controls.badge.dotSize,
            pillHeight = overrides.pillHeight ?: Theme.controls.badge.pillHeight,
            pillMinWidth = overrides.pillMinWidth ?: Theme.controls.badge.pillMinWidth,
            pillHorizontalPadding =
                overrides.pillHorizontalPadding ?: Theme.controls.badge.pillHorizontalPadding,
        )
    }

    private fun scoped(): BadgeOverrides = UiLocals.current(LocalBadgeOverrides)
}

internal data class ResolvedBadgeAppearance(
    val containerColor: Int,
    val contentColor: Int,
    val textStyle: UiTextStyle,
    val shape: UiShape,
    val dotSize: UiDp,
    val pillHeight: UiDp,
    val pillMinWidth: UiDp,
    val pillHorizontalPadding: UiDp,
)
