package com.viewcompose.animation.core

/**
 * 将线性时间进度映射为视觉进度的函数。
 * Function that maps linear time progress to visual progress.
 */
fun interface Easing {
    fun transform(fraction: Float): Float
}

/**
 * 常用 easing 预设。
 * Common easing presets.
 */
object EasingDefaults {
    val Linear: Easing = Easing { it }
    val FastOutSlowIn: Easing = Easing { fraction ->
        val t = fraction.coerceIn(0f, 1f)
        (3f * t * t) - (2f * t * t * t)
    }
    val LinearOutSlowIn: Easing = Easing { fraction ->
        val t = fraction.coerceIn(0f, 1f)
        1f - (1f - t) * (1f - t)
    }
    val FastOutLinearIn: Easing = Easing { fraction ->
        val t = fraction.coerceIn(0f, 1f)
        t * t
    }
}

/**
 * 由两个控制点定义的三次贝塞尔 easing。
 * Cubic Bezier easing defined by two control points.
 */
class CubicBezierEasing(
    val x1: Float,
    val y1: Float,
    val x2: Float,
    val y2: Float,
) : Easing {
    override fun transform(fraction: Float): Float {
        val t = solveTForX(fraction.coerceIn(0f, 1f))
        return cubic(y1, y2, t)
    }

    private fun solveTForX(x: Float): Float {
        // 通过二分求解 x 对应的参数 t，避免要求控制点 x 单调之外的解析反解。
        // Solve parameter t for x with binary search instead of relying on an analytic inverse.
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
