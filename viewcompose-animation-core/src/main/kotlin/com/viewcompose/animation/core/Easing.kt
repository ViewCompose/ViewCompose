package com.viewcompose.animation.core

/**
 * Maps linear time progress to visual animation progress.
 *
 * The animation engine supplies an input in `0f..1f` and clamps the returned progress to that range
 * before value interpolation. Direct callers are responsible for any input or output normalization
 * not documented by a particular implementation. Implementations may run once per animated value
 * per frame and should avoid blocking work and unnecessary allocation.
 */
fun interface Easing {
    /**
     * Transforms [fraction] into visual progress.
     *
     * @param fraction linear time progress, normally in `0f..1f`
     * @return transformed progress; [sampleAnimationValue] clamps it before interpolation
     */
    fun transform(fraction: Float): Float
}

/** Provides allocation-stable easing curves for common motion profiles. */
object EasingDefaults {
    /**
     * Preserves linear progress unchanged.
     *
     * Unlike the other presets, direct calls do not clamp the input.
     */
    val Linear: Easing = Easing { it }

    /**
     * Starts and ends slowly using a smoothstep polynomial.
     *
     * Direct calls clamp the input to `0f..1f`.
     */
    val FastOutSlowIn: Easing = Easing { fraction ->
        val t = fraction.coerceIn(0f, 1f)
        (3f * t * t) - (2f * t * t * t)
    }

    /**
     * Starts linearly and decelerates toward the target using a quadratic curve.
     *
     * Direct calls clamp the input to `0f..1f`.
     */
    val LinearOutSlowIn: Easing = Easing { fraction ->
        val t = fraction.coerceIn(0f, 1f)
        1f - (1f - t) * (1f - t)
    }

    /**
     * Accelerates away from the start using a quadratic curve.
     *
     * Direct calls clamp the input to `0f..1f`.
     */
    val FastOutLinearIn: Easing = Easing { fraction ->
        val t = fraction.coerceIn(0f, 1f)
        t * t
    }
}

/**
 * Evaluates a cubic Bézier easing from `(0, 0)` through two control points to `(1, 1)`.
 *
 * [transform] clamps its input and uses a fixed 16-step binary search to invert the curve's x axis.
 * Callers should keep [x1] and [x2] in `0f..1f` so x remains suitable for monotonic inversion. The
 * constructor does not validate control points. Y values may be outside `0f..1f`; direct calls can
 * therefore return overshoot, while the animation engine clamps sampled progress.
 *
 * @property x1 x coordinate of the first control point
 * @property y1 y coordinate of the first control point
 * @property x2 x coordinate of the second control point
 * @property y2 y coordinate of the second control point
 */
class CubicBezierEasing(
    val x1: Float,
    val y1: Float,
    val x2: Float,
    val y2: Float,
) : Easing {
    /**
     * Returns the curve's y coordinate for the supplied x progress.
     *
     * @param fraction x progress, clamped to `0f..1f`
     * @return the approximated y progress; the value is not clamped
     */
    override fun transform(fraction: Float): Float {
        val t = solveTForX(fraction.coerceIn(0f, 1f))
        return cubic(y1, y2, t)
    }

    private fun solveTForX(x: Float): Float {
        // A bounded binary search gives deterministic per-frame cost without allocating a solver.
        var low = 0f
        var high = 1f
        repeat(16) {
            val mid = (low + high) * 0.5f
            val midX = cubic(x1, x2, mid)
            if (midX < x) {
                low = mid
            } else {
                high = mid
            }
        }
        return (low + high) * 0.5f
    }

    private fun cubic(p1: Float, p2: Float, t: Float): Float {
        val u = 1f - t
        return 3f * u * u * t * p1 +
            3f * u * t * t * p2 +
            t * t * t
    }
}

/**
 * Creates a cubic Bézier easing from two control points.
 *
 * @sample com.viewcompose.animation.core.samples.cubicBezierEasingSample
 *
 * @param x1 x coordinate of the first control point; `0f..1f` is recommended
 * @param y1 y coordinate of the first control point
 * @param x2 x coordinate of the second control point; `0f..1f` is recommended
 * @param y2 y coordinate of the second control point
 * @return a new easing instance; control points are not validated
 */
fun cubicBezier(
    x1: Float,
    y1: Float,
    x2: Float,
    y2: Float,
): Easing = CubicBezierEasing(
    x1 = x1,
    y1 = y1,
    x2 = x2,
    y2 = y2,
)
