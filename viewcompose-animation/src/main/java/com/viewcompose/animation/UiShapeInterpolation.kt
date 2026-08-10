package com.viewcompose.animation

import com.viewcompose.ui.shape.UiCorner
import com.viewcompose.ui.shape.UiCornerSize
import com.viewcompose.ui.shape.UiShape
import com.viewcompose.ui.unit.UiDp

/** Describes how [interpolateUiShape] produced the resolved shape. */
enum class UiShapeInterpolationMode {
    /** Every corresponding corner used compatible family and size representations. */
    Compatible,

    /** Incompatible geometry selected one endpoint at the transition midpoint. */
    DiscreteFallback,
}

/**
 * Carries a resolved shape together with the interpolation or fallback path used to produce it.
 *
 * @property shape immutable renderer-neutral shape for the requested progress
 * @property mode whether geometry was interpolated or selected discretely
 */
data class UiShapeInterpolationResult(
    val shape: UiShape,
    val mode: UiShapeInterpolationMode,
)

/**
 * Interpolates compatible corner geometry and uses a deterministic endpoint fallback otherwise.
 *
 * Corresponding corners are compatible only when they use the same corner family and both sizes
 * are either absolute or relative. This function deliberately does not morph unrelated arbitrary
 * paths. Incompatible shapes retain [start] before the midpoint and select [end] at and after the
 * midpoint, allowing diagnostics to report [UiShapeInterpolationMode.DiscreteFallback].
 *
 * This Q2 function owns no clock or mutable state. Supply progress from `Animatable`,
 * `animateFloatAsState`, or `Transition` so cancellation and lifecycle ownership remain shared.
 *
 * @param start shape at progress zero
 * @param end shape at progress one
 * @param fraction finite transition progress; values outside `0f..1f` are clamped
 * @return the resolved shape and its interpolation mode
 * @throws IllegalArgumentException if [fraction] is not finite
 * @sample com.viewcompose.animation.samples.uiShapeInterpolationSample
 */
fun interpolateUiShape(
    start: UiShape,
    end: UiShape,
    fraction: Float,
): UiShapeInterpolationResult {
    require(fraction.isFinite()) { "Shape interpolation fraction must be finite." }
    val progress = fraction.coerceIn(0f, 1f)
    val topStart = start.topStart.interpolateCompatible(end.topStart, progress)
    val topEnd = start.topEnd.interpolateCompatible(end.topEnd, progress)
    val bottomEnd = start.bottomEnd.interpolateCompatible(end.bottomEnd, progress)
    val bottomStart = start.bottomStart.interpolateCompatible(end.bottomStart, progress)
    if (topStart == null || topEnd == null || bottomEnd == null || bottomStart == null) {
        return UiShapeInterpolationResult(
            shape = if (progress < 0.5f) start else end,
            mode = UiShapeInterpolationMode.DiscreteFallback,
        )
    }
    return UiShapeInterpolationResult(
        shape = UiShape(
            topStart = topStart,
            topEnd = topEnd,
            bottomEnd = bottomEnd,
            bottomStart = bottomStart,
        ),
        mode = UiShapeInterpolationMode.Compatible,
    )
}

private fun UiCorner.interpolateCompatible(other: UiCorner, fraction: Float): UiCorner? {
    if (family != other.family) return null
    val startSize = size
    val endSize = other.size
    val interpolatedSize = when {
        startSize is UiCornerSize.Absolute && endSize is UiCornerSize.Absolute -> {
            UiCornerSize.Absolute(
                UiDp(startSize.size.value.lerp(endSize.size.value, fraction)),
            )
        }
        startSize is UiCornerSize.Relative && endSize is UiCornerSize.Relative -> {
            UiCornerSize.Relative(startSize.fraction.lerp(endSize.fraction, fraction))
        }
        else -> return null
    }
    return UiCorner(family = family, size = interpolatedSize)
}

private fun Float.lerp(other: Float, fraction: Float): Float = this + (other - this) * fraction
