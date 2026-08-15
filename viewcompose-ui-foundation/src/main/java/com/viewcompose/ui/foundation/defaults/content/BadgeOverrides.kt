package com.viewcompose.ui.foundation

import com.viewcompose.ui.shape.UiShape
import com.viewcompose.ui.unit.UiDp

/**
 * Selectively replaces [Badge] appearance and visual geometry.
 *
 * Count presence, omission for non-positive values, and display-text truncation remain Badge
 * semantics rather than appearance fields.
 *
 * @property containerColor badge container ARGB color
 * @property contentColor labeled-badge text ARGB color
 * @property textStyle labeled-badge typography
 * @property shape dot and labeled-pill container shape
 * @property dotSize width and height of a badge without text in dp
 * @property pillHeight labeled-badge height in dp
 * @property pillMinWidth labeled-badge minimum width in dp
 * @property pillHorizontalPadding text padding on each horizontal edge in dp
 * @throws IllegalArgumentException when a supplied dimension is negative
 */
data class BadgeOverrides(
    val containerColor: Int? = null,
    val contentColor: Int? = null,
    val textStyle: UiTextStyle? = null,
    val shape: UiShape? = null,
    val dotSize: UiDp? = null,
    val pillHeight: UiDp? = null,
    val pillMinWidth: UiDp? = null,
    val pillHorizontalPadding: UiDp? = null,
) {
    init {
        dotSize.requireNonNegative("BadgeOverrides.dotSize")
        pillHeight.requireNonNegative("BadgeOverrides.pillHeight")
        pillMinWidth.requireNonNegative("BadgeOverrides.pillMinWidth")
        pillHorizontalPadding.requireNonNegative("BadgeOverrides.pillHorizontalPadding")
    }

    /** Shared Badge override values. */
    companion object {
        /** Shared empty Badge appearance patch. */
        val None: BadgeOverrides = BadgeOverrides()
    }
}

internal fun BadgeOverrides.merge(nearest: BadgeOverrides): BadgeOverrides {
    if (nearest === BadgeOverrides.None) return this
    if (this === BadgeOverrides.None) return nearest
    return BadgeOverrides(
        containerColor = nearest.containerColor ?: containerColor,
        contentColor = nearest.contentColor ?: contentColor,
        textStyle = nearest.textStyle ?: textStyle,
        shape = nearest.shape ?: shape,
        dotSize = nearest.dotSize ?: dotSize,
        pillHeight = nearest.pillHeight ?: pillHeight,
        pillMinWidth = nearest.pillMinWidth ?: pillMinWidth,
        pillHorizontalPadding = nearest.pillHorizontalPadding ?: pillHorizontalPadding,
    )
}

internal val LocalBadgeOverrides = uiLocalOf(
    debugName = "BadgeOverrides",
    debugValueFormatter = BadgeOverrides::toString,
) { BadgeOverrides.None }

/**
 * Merges sparse Badge [overrides] for [content].
 *
 * Nested scopes merge field by field and a Badge instance has the highest precedence.
 *
 * @sample com.viewcompose.ui.foundation.samples.remainingComponentOverridesSample
 * @receiver active tree builder receiving the scoped content
 * @param overrides sparse appearance patch applied to descendant badges
 * @param content subtree built synchronously with the merged patch
 */
fun UiTreeBuilder.ProvideBadgeOverrides(
    overrides: BadgeOverrides,
    content: UiTreeBuilder.() -> Unit,
) {
    ProvideLocal(LocalBadgeOverrides, UiLocals.current(LocalBadgeOverrides).merge(overrides), content)
}

private fun UiDp?.requireNonNegative(propertyName: String) {
    require(this == null || this >= UiDp.Zero) { "$propertyName must be non-negative." }
}
