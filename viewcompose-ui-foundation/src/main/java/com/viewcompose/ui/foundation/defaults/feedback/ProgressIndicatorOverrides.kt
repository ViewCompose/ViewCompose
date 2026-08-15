package com.viewcompose.ui.foundation

import com.viewcompose.ui.unit.UiDp

/**
 * Selectively replaces linear progress-indicator appearance.
 *
 * @property indicatorColor active indicator ARGB color
 * @property trackColor inactive track ARGB color
 * @property trackThickness indicator and track thickness in dp
 * @throws IllegalArgumentException when a supplied dimension is negative
 */
data class LinearProgressIndicatorOverrides(
    val indicatorColor: Int? = null,
    val trackColor: Int? = null,
    val trackThickness: UiDp? = null,
) {
    init {
        trackThickness.requireNonNegative("LinearProgressIndicatorOverrides.trackThickness")
    }

    /** Shared linear progress override values. */
    companion object {
        /** Shared empty linear progress appearance patch. */
        val None: LinearProgressIndicatorOverrides = LinearProgressIndicatorOverrides()
    }
}

internal fun LinearProgressIndicatorOverrides.merge(
    nearest: LinearProgressIndicatorOverrides,
): LinearProgressIndicatorOverrides {
    if (nearest === LinearProgressIndicatorOverrides.None) return this
    if (this === LinearProgressIndicatorOverrides.None) return nearest
    return LinearProgressIndicatorOverrides(
        indicatorColor = nearest.indicatorColor ?: indicatorColor,
        trackColor = nearest.trackColor ?: trackColor,
        trackThickness = nearest.trackThickness ?: trackThickness,
    )
}

internal val LocalLinearProgressIndicatorOverrides = uiLocalOf(
    debugName = "LinearProgressIndicatorOverrides",
    debugValueFormatter = LinearProgressIndicatorOverrides::toString,
) { LinearProgressIndicatorOverrides.None }

/**
 * Merges sparse [overrides] into linear progress defaults for [content].
 *
 * Nested scopes merge field by field and instance patches retain the highest precedence.
 *
 * @sample com.viewcompose.ui.foundation.samples.componentOverridesSample
 * @receiver active tree builder receiving the scoped content
 * @param overrides sparse appearance patch applied to descendant linear indicators
 * @param content subtree built synchronously with the merged patch
 */
fun UiTreeBuilder.ProvideLinearProgressIndicatorOverrides(
    overrides: LinearProgressIndicatorOverrides,
    content: UiTreeBuilder.() -> Unit,
) {
    ProvideLocal(
        LocalLinearProgressIndicatorOverrides,
        UiLocals.current(LocalLinearProgressIndicatorOverrides).merge(overrides),
        content,
    )
}

/**
 * Selectively replaces circular progress-indicator appearance.
 *
 * @property indicatorColor active indicator ARGB color
 * @property trackColor inactive track ARGB color
 * @property size square indicator bounds in dp
 * @property trackThickness indicator and track stroke thickness in dp
 * @throws IllegalArgumentException when a supplied dimension is negative
 */
data class CircularProgressIndicatorOverrides(
    val indicatorColor: Int? = null,
    val trackColor: Int? = null,
    val size: UiDp? = null,
    val trackThickness: UiDp? = null,
) {
    init {
        size.requireNonNegative("CircularProgressIndicatorOverrides.size")
        trackThickness.requireNonNegative("CircularProgressIndicatorOverrides.trackThickness")
    }

    /** Shared circular progress override values. */
    companion object {
        /** Shared empty circular progress appearance patch. */
        val None: CircularProgressIndicatorOverrides = CircularProgressIndicatorOverrides()
    }
}

internal fun CircularProgressIndicatorOverrides.merge(
    nearest: CircularProgressIndicatorOverrides,
): CircularProgressIndicatorOverrides {
    if (nearest === CircularProgressIndicatorOverrides.None) return this
    if (this === CircularProgressIndicatorOverrides.None) return nearest
    return CircularProgressIndicatorOverrides(
        indicatorColor = nearest.indicatorColor ?: indicatorColor,
        trackColor = nearest.trackColor ?: trackColor,
        size = nearest.size ?: size,
        trackThickness = nearest.trackThickness ?: trackThickness,
    )
}

internal val LocalCircularProgressIndicatorOverrides = uiLocalOf(
    debugName = "CircularProgressIndicatorOverrides",
    debugValueFormatter = CircularProgressIndicatorOverrides::toString,
) { CircularProgressIndicatorOverrides.None }

/**
 * Merges sparse [overrides] into circular progress defaults for [content].
 *
 * Nested scopes merge field by field and instance patches retain the highest precedence.
 *
 * @sample com.viewcompose.ui.foundation.samples.componentOverridesSample
 * @receiver active tree builder receiving the scoped content
 * @param overrides sparse appearance patch applied to descendant circular indicators
 * @param content subtree built synchronously with the merged patch
 */
fun UiTreeBuilder.ProvideCircularProgressIndicatorOverrides(
    overrides: CircularProgressIndicatorOverrides,
    content: UiTreeBuilder.() -> Unit,
) {
    ProvideLocal(
        LocalCircularProgressIndicatorOverrides,
        UiLocals.current(LocalCircularProgressIndicatorOverrides).merge(overrides),
        content,
    )
}

private fun UiDp?.requireNonNegative(propertyName: String) {
    require(this == null || this >= UiDp.Zero) { "$propertyName must be non-negative." }
}
