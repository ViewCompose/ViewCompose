package com.viewcompose.ui.foundation

import com.viewcompose.ui.unit.UiDp

/**
 * Selectively replaces [TopAppBar] appearance and visual spacing.
 *
 * Navigation and action colors are independent because a design system may assign them different
 * semantic roles. Content slots, title text, callbacks, identity, and scrolling behavior are not
 * carried by this patch.
 *
 * @property containerColor bar container ARGB color
 * @property titleColor title ARGB color
 * @property navigationIconColor default navigation-slot ARGB content color
 * @property actionIconColor default action-slot ARGB content color
 * @property titleStyle title typography
 * @property height bar height in dp
 * @property horizontalPadding outer padding on each horizontal edge in dp
 * @property titleStartPadding gap between navigation content and title in dp
 * @throws IllegalArgumentException when a supplied dimension is negative
 */
data class TopAppBarOverrides(
    val containerColor: Int? = null,
    val titleColor: Int? = null,
    val navigationIconColor: Int? = null,
    val actionIconColor: Int? = null,
    val titleStyle: UiTextStyle? = null,
    val height: UiDp? = null,
    val horizontalPadding: UiDp? = null,
    val titleStartPadding: UiDp? = null,
) {
    init {
        height.requireNonNegative("TopAppBarOverrides.height")
        horizontalPadding.requireNonNegative("TopAppBarOverrides.horizontalPadding")
        titleStartPadding.requireNonNegative("TopAppBarOverrides.titleStartPadding")
    }

    /** Shared TopAppBar override values. */
    companion object {
        /** Shared empty TopAppBar appearance patch. */
        val None: TopAppBarOverrides = TopAppBarOverrides()
    }
}

internal fun TopAppBarOverrides.merge(nearest: TopAppBarOverrides): TopAppBarOverrides {
    if (nearest === TopAppBarOverrides.None) return this
    if (this === TopAppBarOverrides.None) return nearest
    return TopAppBarOverrides(
        containerColor = nearest.containerColor ?: containerColor,
        titleColor = nearest.titleColor ?: titleColor,
        navigationIconColor = nearest.navigationIconColor ?: navigationIconColor,
        actionIconColor = nearest.actionIconColor ?: actionIconColor,
        titleStyle = nearest.titleStyle ?: titleStyle,
        height = nearest.height ?: height,
        horizontalPadding = nearest.horizontalPadding ?: horizontalPadding,
        titleStartPadding = nearest.titleStartPadding ?: titleStartPadding,
    )
}

internal val LocalTopAppBarOverrides = uiLocalOf(
    debugName = "TopAppBarOverrides",
    debugValueFormatter = TopAppBarOverrides::toString,
) { TopAppBarOverrides.None }

/**
 * Merges sparse TopAppBar [overrides] for [content].
 *
 * Nested scopes merge field by field and a TopAppBar instance retains the highest precedence.
 * Child IconButton instances may still override the bar-provided navigation or action color.
 *
 * @sample com.viewcompose.ui.foundation.samples.remainingComponentOverridesSample
 * @receiver active tree builder receiving the scoped content
 * @param overrides sparse appearance patch applied to descendant top app bars
 * @param content subtree built synchronously with the merged patch
 */
fun UiTreeBuilder.ProvideTopAppBarOverrides(
    overrides: TopAppBarOverrides,
    content: UiTreeBuilder.() -> Unit,
) {
    ProvideLocal(
        LocalTopAppBarOverrides,
        UiLocals.current(LocalTopAppBarOverrides).merge(overrides),
        content,
    )
}

/**
 * Selectively replaces [BottomAppBar] appearance and visual spacing.
 *
 * @property containerColor bar container ARGB color
 * @property contentColor default descendant content ARGB color
 * @property height bar height in dp
 * @property horizontalPadding outer padding on each horizontal edge in dp
 * @property elevation resting elevation in dp
 * @throws IllegalArgumentException when a supplied dimension is negative
 */
data class BottomAppBarOverrides(
    val containerColor: Int? = null,
    val contentColor: Int? = null,
    val height: UiDp? = null,
    val horizontalPadding: UiDp? = null,
    val elevation: UiDp? = null,
) {
    init {
        height.requireNonNegative("BottomAppBarOverrides.height")
        horizontalPadding.requireNonNegative("BottomAppBarOverrides.horizontalPadding")
        elevation.requireNonNegative("BottomAppBarOverrides.elevation")
    }

    /** Shared BottomAppBar override values. */
    companion object {
        /** Shared empty BottomAppBar appearance patch. */
        val None: BottomAppBarOverrides = BottomAppBarOverrides()
    }
}

internal fun BottomAppBarOverrides.merge(nearest: BottomAppBarOverrides): BottomAppBarOverrides {
    if (nearest === BottomAppBarOverrides.None) return this
    if (this === BottomAppBarOverrides.None) return nearest
    return BottomAppBarOverrides(
        containerColor = nearest.containerColor ?: containerColor,
        contentColor = nearest.contentColor ?: contentColor,
        height = nearest.height ?: height,
        horizontalPadding = nearest.horizontalPadding ?: horizontalPadding,
        elevation = nearest.elevation ?: elevation,
    )
}

internal val LocalBottomAppBarOverrides = uiLocalOf(
    debugName = "BottomAppBarOverrides",
    debugValueFormatter = BottomAppBarOverrides::toString,
) { BottomAppBarOverrides.None }

/**
 * Merges sparse BottomAppBar [overrides] for [content].
 *
 * Nested scopes merge field by field and a BottomAppBar instance has the highest precedence.
 *
 * @sample com.viewcompose.ui.foundation.samples.remainingComponentOverridesSample
 * @receiver active tree builder receiving the scoped content
 * @param overrides sparse appearance patch applied to descendant bottom app bars
 * @param content subtree built synchronously with the merged patch
 */
fun UiTreeBuilder.ProvideBottomAppBarOverrides(
    overrides: BottomAppBarOverrides,
    content: UiTreeBuilder.() -> Unit,
) {
    ProvideLocal(
        LocalBottomAppBarOverrides,
        UiLocals.current(LocalBottomAppBarOverrides).merge(overrides),
        content,
    )
}

private fun UiDp?.requireNonNegative(propertyName: String) {
    require(this == null || this >= UiDp.Zero) { "$propertyName must be non-negative." }
}
