package com.viewcompose.renderer.view.container

import android.animation.TimeInterpolator
import android.animation.ValueAnimator
import android.view.animation.LinearInterpolator
import android.view.animation.PathInterpolator
import com.viewcompose.ui.modifier.ContentSizeDurationBasedAnimationSpecModel
import com.viewcompose.ui.modifier.ContentSizeEasingModel
import com.viewcompose.ui.modifier.ContentSizeKeyframeModel
import com.viewcompose.ui.modifier.ContentSizeKeyframesSpecModel
import com.viewcompose.ui.modifier.ContentSizeRepeatModeModel
import com.viewcompose.ui.modifier.ContentSizeRepeatableSpecModel
import com.viewcompose.ui.modifier.ContentSizeSnapSpecModel
import com.viewcompose.ui.modifier.ContentSizeTweenSpecModel

/** Clears transient layout-animation ownership before a native tree crosses logical owners. */
internal interface ReusableLayoutAnimationHost {
    fun resetLayoutAnimationForReuse()
}

/** Shared Android runtime projection for finite duration-based layout animation specifications. */
internal data class LayoutAnimationRuntimeConfig(
    val durationMillis: Long,
    val delayMillis: Long,
    val interpolator: TimeInterpolator,
    val repeatCount: Int,
    val repeatMode: Int,
    val terminalFraction: Float,
)

internal fun ContentSizeDurationBasedAnimationSpecModel.resolveLayoutAnimationConfig():
    LayoutAnimationRuntimeConfig {
    return when (this) {
        is ContentSizeTweenSpecModel -> LayoutAnimationRuntimeConfig(
            durationMillis = durationMillis.toLong().coerceAtLeast(1L),
            delayMillis = delayMillis.toLong().coerceAtLeast(0L),
            interpolator = easing.toLayoutInterpolator(),
            repeatCount = 0,
            repeatMode = ValueAnimator.RESTART,
            terminalFraction = 1f,
        )

        is ContentSizeKeyframesSpecModel -> LayoutAnimationRuntimeConfig(
            durationMillis = durationMillis.toLong().coerceAtLeast(1L),
            delayMillis = 0L,
            interpolator = LayoutKeyframesInterpolator(
                durationMillis = durationMillis.coerceAtLeast(1),
                keyframes = keyframes,
            ),
            repeatCount = 0,
            repeatMode = ValueAnimator.RESTART,
            terminalFraction = 1f,
        )

        ContentSizeSnapSpecModel -> LayoutAnimationRuntimeConfig(
            durationMillis = 0L,
            delayMillis = 0L,
            interpolator = LinearInterpolator(),
            repeatCount = 0,
            repeatMode = ValueAnimator.RESTART,
            terminalFraction = 1f,
        )

        is ContentSizeRepeatableSpecModel -> {
            val normalizedIterations = iterations.coerceAtLeast(0)
            if (normalizedIterations == 0) {
                return LayoutAnimationRuntimeConfig(
                    durationMillis = 0L,
                    delayMillis = 0L,
                    interpolator = LinearInterpolator(),
                    repeatCount = 0,
                    repeatMode = ValueAnimator.RESTART,
                    terminalFraction = 0f,
                )
            }
            val inner = animation.resolveLayoutAnimationConfig()
            inner.copy(
                repeatCount = (normalizedIterations - 1).coerceAtLeast(0),
                repeatMode = repeatMode.toAnimatorRepeatMode(),
                terminalFraction = repeatMode.terminalFraction(iterations = normalizedIterations),
            )
        }
    }
}

private fun ContentSizeRepeatModeModel.toAnimatorRepeatMode(): Int {
    return when (this) {
        ContentSizeRepeatModeModel.Restart -> ValueAnimator.RESTART
        ContentSizeRepeatModeModel.Reverse -> ValueAnimator.REVERSE
    }
}

private fun ContentSizeRepeatModeModel.terminalFraction(iterations: Int): Float {
    return if (this == ContentSizeRepeatModeModel.Reverse && iterations % 2 == 0) 0f else 1f
}

private fun ContentSizeEasingModel.toLayoutInterpolator(): TimeInterpolator {
    return when (this) {
        ContentSizeEasingModel.Linear -> LinearInterpolator()
        ContentSizeEasingModel.FastOutSlowIn -> TimeInterpolator { fraction ->
            val t = fraction.coerceIn(0f, 1f)
            (3f * t * t) - (2f * t * t * t)
        }
        ContentSizeEasingModel.LinearOutSlowIn -> TimeInterpolator { fraction ->
            val t = fraction.coerceIn(0f, 1f)
            1f - (1f - t) * (1f - t)
        }
        ContentSizeEasingModel.FastOutLinearIn -> TimeInterpolator { fraction ->
            val t = fraction.coerceIn(0f, 1f)
            t * t
        }
        is ContentSizeEasingModel.CubicBezier -> PathInterpolator(x1, y1, x2, y2)
    }
}

private class LayoutKeyframesInterpolator(
    private val durationMillis: Int,
    keyframes: List<ContentSizeKeyframeModel>,
) : TimeInterpolator {
    private val sortedKeyframes = keyframes.sortedBy { it.timeMillis }

    override fun getInterpolation(input: Float): Float {
        if (sortedKeyframes.isEmpty()) return input.coerceIn(0f, 1f)
        val time = (durationMillis * input.coerceIn(0f, 1f)).toInt()
        val before = sortedKeyframes.lastOrNull { it.timeMillis <= time }
            ?: ContentSizeKeyframeModel(0, 0f)
        val after = sortedKeyframes.firstOrNull { it.timeMillis >= time }
            ?: ContentSizeKeyframeModel(durationMillis, 1f)
        if (before.timeMillis == after.timeMillis) return before.valueFraction.coerceIn(0f, 1f)
        val local = ((time - before.timeMillis).toFloat() / (after.timeMillis - before.timeMillis))
            .coerceIn(0f, 1f)
        return (before.valueFraction + (after.valueFraction - before.valueFraction) * local)
            .coerceIn(0f, 1f)
    }
}
