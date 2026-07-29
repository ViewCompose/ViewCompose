package com.viewcompose.renderer.view.container

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.TimeInterpolator
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator
import android.view.animation.PathInterpolator
import android.widget.FrameLayout
import com.viewcompose.renderer.view.tree.LayoutPassTracker
import com.viewcompose.shadow.android.DecorationChildDrawingOrder
import com.viewcompose.shadow.android.ShadowDecorationLayer
import com.viewcompose.ui.modifier.ContentSizeAnimationSpecModel
import com.viewcompose.ui.modifier.ContentSizeEasingModel
import com.viewcompose.ui.modifier.ContentSizeInfiniteRepeatableSpecModel
import com.viewcompose.ui.modifier.ContentSizeKeyframeModel
import com.viewcompose.ui.modifier.ContentSizeKeyframesSpecModel
import com.viewcompose.ui.modifier.ContentSizeRepeatModeModel
import com.viewcompose.ui.modifier.ContentSizeRepeatableSpecModel
import com.viewcompose.ui.modifier.ContentSizeSnapSpecModel
import com.viewcompose.ui.modifier.ContentSizeSpringSpecModel
import com.viewcompose.ui.modifier.ContentSizeTweenSpecModel
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.roundToInt

/**
 * animateContentSize modifier 提升后的平台 host。
 * Platform host created from promoted animateContentSize modifiers.
 */
internal class DeclarativeAnimatedSizeHostLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : FrameLayout(context, attrs) {
    var animationSpec: ContentSizeAnimationSpecModel = ContentSizeSnapSpecModel
        set(value) {
            field = value
        }

    private var animatedWidthPx: Float = -1f
    private var animatedHeightPx: Float = -1f
    private var targetWidthPx: Int = 0
    private var targetHeightPx: Int = 0
    private var sizeAnimator: ValueAnimator? = null

    init {
        isChildrenDrawingOrderEnabled = true
        clipChildren = true
        clipToPadding = true
    }

    override fun getChildDrawingOrder(childCount: Int, drawingPosition: Int): Int =
        DecorationChildDrawingOrder.getChildDrawingOrder(this, childCount, drawingPosition)

    override fun onMeasure(
        widthMeasureSpec: Int,
        heightMeasureSpec: Int,
    ) {
        val startNs = LayoutPassTracker.beginTiming()
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        val desiredWidth = measuredWidth
        val desiredHeight = measuredHeight
        if (animatedWidthPx < 0f || animatedHeightPx < 0f) {
            animatedWidthPx = desiredWidth.toFloat()
            animatedHeightPx = desiredHeight.toFloat()
            targetWidthPx = desiredWidth
            targetHeightPx = desiredHeight
        } else if (desiredWidth != targetWidthPx || desiredHeight != targetHeightPx) {
            targetWidthPx = desiredWidth
            targetHeightPx = desiredHeight
            startSizeAnimation()
        }
        setMeasuredDimension(
            resolveSize(animatedWidthPx.roundToInt().coerceAtLeast(0), widthMeasureSpec),
            resolveSize(animatedHeightPx.roundToInt().coerceAtLeast(0), heightMeasureSpec),
        )
        LayoutPassTracker.recordMeasureSince(javaClass, startNs)
    }

    override fun onLayout(
        changed: Boolean,
        left: Int,
        top: Int,
        right: Int,
        bottom: Int,
    ) {
        val startNs = LayoutPassTracker.beginTiming()
        val contentLeft = paddingLeft
        val contentTop = paddingTop
        val contentRight = (right - left - paddingRight).coerceAtLeast(contentLeft)
        val contentBottom = (bottom - top - paddingBottom).coerceAtLeast(contentTop)
        if (childCount == 1) {
            // 将唯一子节点按 host 当前动画尺寸布局，避免收起时子节点先跳到末端尺寸。
            // Layout the single child with the host's animated bounds to avoid snap-to-end during collapse.
            getChildAt(0).layout(contentLeft, contentTop, contentRight, contentBottom)
        } else {
            super.onLayout(changed, left, top, right, bottom)
        }
        LayoutPassTracker.recordLayoutSince(javaClass, startNs)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        sizeAnimator?.cancel()
        sizeAnimator = null
    }

    override fun drawChild(
        canvas: Canvas,
        child: View,
        drawingTime: Long,
    ): Boolean {
        ShadowDecorationLayer.drawBehindChild(
            canvas = canvas,
            parent = this,
            child = child,
        )
        return super.drawChild(canvas, child, drawingTime)
    }

    private fun startSizeAnimation() {
        sizeAnimator?.cancel()
        val startWidth = animatedWidthPx
        val startHeight = animatedHeightPx
        val endWidth = targetWidthPx.toFloat()
        val endHeight = targetHeightPx.toFloat()
        if (startWidth == endWidth && startHeight == endHeight) {
            return
        }
        val config = animationSpec.resolveConfig()
        if (config.durationMillis <= 0L) {
            animatedWidthPx = endWidth
            animatedHeightPx = endHeight
            requestLayout()
            return
        }
        sizeAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = config.durationMillis
            startDelay = config.delayMillis
            interpolator = config.interpolator
            repeatCount = config.repeatCount
            repeatMode = config.repeatMode
            addUpdateListener { animator ->
                val fraction = animator.animatedValue as Float
                animatedWidthPx = lerp(startWidth, endWidth, fraction)
                animatedHeightPx = lerp(startHeight, endHeight, fraction)
                requestLayout()
            }
            var wasCancelled = false
            addListener(
                object : AnimatorListenerAdapter() {
                    override fun onAnimationCancel(animation: Animator) {
                        wasCancelled = true
                    }

                    override fun onAnimationEnd(animation: Animator) {
                        if (wasCancelled) return
                        animatedWidthPx = lerp(startWidth, endWidth, config.terminalFraction)
                        animatedHeightPx = lerp(startHeight, endHeight, config.terminalFraction)
                        requestLayout()
                    }
                },
            )
            start()
        }
    }

    private data class AnimationRuntimeConfig(
        val durationMillis: Long,
        val delayMillis: Long,
        val interpolator: TimeInterpolator,
        val repeatCount: Int,
        val repeatMode: Int,
        val terminalFraction: Float,
    )

    private fun ContentSizeAnimationSpecModel.resolveConfig(): AnimationRuntimeConfig {
        return when (this) {
            is ContentSizeTweenSpecModel -> AnimationRuntimeConfig(
                durationMillis = durationMillis.toLong().coerceAtLeast(1L),
                delayMillis = delayMillis.toLong().coerceAtLeast(0L),
                interpolator = easing.toInterpolator(),
                repeatCount = 0,
                repeatMode = ValueAnimator.RESTART,
                terminalFraction = 1f,
            )

            is ContentSizeSpringSpecModel -> AnimationRuntimeConfig(
                durationMillis = durationMillis.toLong().coerceAtLeast(1L),
                delayMillis = 0L,
                interpolator = SpringApproximationInterpolator(
                    dampingRatio = dampingRatio,
                    stiffness = stiffness,
                ),
                repeatCount = 0,
                repeatMode = ValueAnimator.RESTART,
                terminalFraction = 1f,
            )

            is ContentSizeKeyframesSpecModel -> AnimationRuntimeConfig(
                durationMillis = durationMillis.toLong().coerceAtLeast(1L),
                delayMillis = 0L,
                interpolator = KeyframesInterpolator(
                    durationMillis = durationMillis.coerceAtLeast(1),
                    keyframes = keyframes,
                ),
                repeatCount = 0,
                repeatMode = ValueAnimator.RESTART,
                terminalFraction = 1f,
            )

            ContentSizeSnapSpecModel -> AnimationRuntimeConfig(
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
                    return AnimationRuntimeConfig(
                        durationMillis = 0L,
                        delayMillis = 0L,
                        interpolator = LinearInterpolator(),
                        repeatCount = 0,
                        repeatMode = ValueAnimator.RESTART,
                        terminalFraction = 0f,
                    )
                }
                val inner = animation.resolveConfig()
                inner.copy(
                    repeatCount = (normalizedIterations - 1).coerceAtLeast(0),
                    repeatMode = repeatMode.toAnimatorRepeatMode(),
                    terminalFraction = repeatMode.terminalFraction(iterations = normalizedIterations),
                )
            }

            is ContentSizeInfiniteRepeatableSpecModel -> animation.resolveConfig().copy(
                repeatCount = ValueAnimator.INFINITE,
                repeatMode = repeatMode.toAnimatorRepeatMode(),
                terminalFraction = 1f,
            )
        }
    }

    private fun lerp(
        start: Float,
        end: Float,
        fraction: Float,
    ): Float {
        return start + (end - start) * fraction
    }

    private fun ContentSizeRepeatModeModel.toAnimatorRepeatMode(): Int {
        return when (this) {
            ContentSizeRepeatModeModel.Restart -> ValueAnimator.RESTART
            ContentSizeRepeatModeModel.Reverse -> ValueAnimator.REVERSE
        }
    }

    private fun ContentSizeRepeatModeModel.terminalFraction(iterations: Int): Float {
        return if (this == ContentSizeRepeatModeModel.Reverse && iterations % 2 == 0) {
            0f
        } else {
            1f
        }
    }

    private fun ContentSizeEasingModel.toInterpolator(): TimeInterpolator {
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

    private class SpringApproximationInterpolator(
        private val dampingRatio: Float,
        private val stiffness: Float,
    ) : TimeInterpolator {
        override fun getInterpolation(input: Float): Float {
            val t = input.coerceIn(0f, 1f)
            val damping = exp((-dampingRatio * 6f * t).toDouble()).toFloat()
            val oscillation = cos((stiffness * 0.06f * t).toDouble()).toFloat()
            return (1f - damping * oscillation).coerceIn(0f, 1f)
        }
    }

    private class KeyframesInterpolator(
        private val durationMillis: Int,
        private val keyframes: List<ContentSizeKeyframeModel>,
    ) : TimeInterpolator {
        override fun getInterpolation(input: Float): Float {
            if (keyframes.isEmpty()) {
                return input.coerceIn(0f, 1f)
            }
            val clamped = input.coerceIn(0f, 1f)
            val time = (durationMillis * clamped).toInt()
            val sorted = keyframes.sortedBy { it.timeMillis }
            val before = sorted.lastOrNull { it.timeMillis <= time } ?: ContentSizeKeyframeModel(0, 0f)
            val after = sorted.firstOrNull { it.timeMillis >= time } ?: ContentSizeKeyframeModel(durationMillis, 1f)
            if (before.timeMillis == after.timeMillis) {
                return before.valueFraction.coerceIn(0f, 1f)
            }
            val local = ((time - before.timeMillis).toFloat() / (after.timeMillis - before.timeMillis).toFloat())
                .coerceIn(0f, 1f)
            return localLerp(before.valueFraction, after.valueFraction, local).coerceIn(0f, 1f)
        }

        private fun localLerp(
            start: Float,
            end: Float,
            fraction: Float,
        ): Float {
            return start + (end - start) * fraction
        }
    }
}
