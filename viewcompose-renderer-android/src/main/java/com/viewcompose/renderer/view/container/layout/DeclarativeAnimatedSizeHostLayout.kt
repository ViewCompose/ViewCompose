package com.viewcompose.renderer.view.container

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator
import android.widget.FrameLayout
import com.viewcompose.animation.core.AnimationConverter
import com.viewcompose.animation.core.AnimationVelocity
import com.viewcompose.animation.core.SpringSpec
import com.viewcompose.animation.core.TargetAnimation
import com.viewcompose.renderer.view.tree.LayoutPassTracker
import com.viewcompose.renderer.decoration.DecorationChildDrawingOrder
import com.viewcompose.renderer.decoration.DecorationDrawingOrderContainer
import com.viewcompose.renderer.decoration.ViewDecorationDrawing
import com.viewcompose.ui.modifier.ContentSizeAnimationSpecModel
import com.viewcompose.ui.modifier.ContentSizeDurationBasedAnimationSpecModel
import com.viewcompose.ui.modifier.ContentSizeSnapSpecModel
import com.viewcompose.ui.modifier.ContentSizeSpringSpecModel
import kotlin.math.roundToInt

/** Platform host created from promoted animateContentSize modifiers. */
internal class DeclarativeAnimatedSizeHostLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : FrameLayout(context, attrs), DecorationDrawingOrderContainer, ReusableLayoutAnimationHost {
    private val decorationDrawing = ViewDecorationDrawing(this)

    var animationSpec: ContentSizeAnimationSpecModel = ContentSizeSnapSpecModel
        set(value) {
            field = value
        }

    private var animatedWidthPx: Float = -1f
    private var animatedHeightPx: Float = -1f
    private var targetWidthPx: Int = 0
    private var targetHeightPx: Int = 0
    private var animatedVelocity = AnimatedSize(0f, 0f)
    private var sizeAnimator: ValueAnimator? = null

    init {
        clipChildren = true
        clipToPadding = true
    }

    override fun getChildDrawingOrder(childCount: Int, drawingPosition: Int): Int =
        DecorationChildDrawingOrder.getChildDrawingOrder(this, childCount, drawingPosition)

    override fun setDecorationDrawingOrderEnabled(enabled: Boolean) {
        isChildrenDrawingOrderEnabled = enabled
    }

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
            // Layout the single child with the host's animated bounds to avoid snap-to-end during collapse.
            getChildAt(0).layout(contentLeft, contentTop, contentRight, contentBottom)
        } else {
            super.onLayout(changed, left, top, right, bottom)
        }
        LayoutPassTracker.recordLayoutSince(javaClass, startNs)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        resetLayoutAnimationForReuse()
    }

    override fun resetLayoutAnimationForReuse() {
        sizeAnimator?.cancel()
        sizeAnimator = null
        animatedWidthPx = -1f
        animatedHeightPx = -1f
        targetWidthPx = 0
        targetHeightPx = 0
        animatedVelocity = AnimatedSize(0f, 0f)
    }

    override fun drawChild(
        canvas: Canvas,
        child: View,
        drawingTime: Long,
    ): Boolean {
        if (!decorationDrawing.hasDecoratedChildren) {
            return super.drawChild(canvas, child, drawingTime)
        }
        val decoration = decorationDrawing.decorationOrNull(child)
            ?: return super.drawChild(canvas, child, drawingTime)
        decorationDrawing.drawBehindChild(canvas, child, decoration)
        val drawn = super.drawChild(canvas, child, drawingTime)
        decorationDrawing.drawOverChild(canvas, child, decoration)
        return drawn
    }

    override fun onViewAdded(child: View) {
        super.onViewAdded(child)
        DecorationChildDrawingOrder.onViewAdded(this, child)
        decorationDrawing.onViewAdded(child)
    }

    override fun onViewRemoved(child: View) {
        decorationDrawing.onViewRemoved(child)
        super.onViewRemoved(child)
        DecorationChildDrawingOrder.onViewRemoved(this, child)
    }

    private fun startSizeAnimation() {
        sizeAnimator?.cancel()
        val startWidth = animatedWidthPx
        val startHeight = animatedHeightPx
        val endWidth = targetWidthPx.toFloat()
        val endHeight = targetHeightPx.toFloat()
        if (startWidth == endWidth && startHeight == endHeight) {
            animatedVelocity = AnimatedSize(0f, 0f)
            return
        }
        val spec = animationSpec
        if (spec is ContentSizeSpringSpecModel) {
            startPhysicalSpring(
                start = AnimatedSize(startWidth, startHeight),
                end = AnimatedSize(endWidth, endHeight),
                spec = spec,
            )
            return
        }
        animatedVelocity = AnimatedSize(0f, 0f)
        startDurationAnimation(
            start = AnimatedSize(startWidth, startHeight),
            end = AnimatedSize(endWidth, endHeight),
            spec = spec as ContentSizeDurationBasedAnimationSpecModel,
        )
    }

    private fun startDurationAnimation(
        start: AnimatedSize,
        end: AnimatedSize,
        spec: ContentSizeDurationBasedAnimationSpecModel,
    ) {
        val config = spec.resolveLayoutAnimationConfig()
        if (config.durationMillis <= 0L) {
            animatedWidthPx = end.width
            animatedHeightPx = end.height
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
                animatedWidthPx = lerp(start.width, end.width, fraction)
                animatedHeightPx = lerp(start.height, end.height, fraction)
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
                        animatedWidthPx = lerp(start.width, end.width, config.terminalFraction)
                        animatedHeightPx = lerp(start.height, end.height, config.terminalFraction)
                        animatedVelocity = AnimatedSize(0f, 0f)
                        requestLayout()
                    }
                },
            )
            start()
        }
    }

    private fun startPhysicalSpring(
        start: AnimatedSize,
        end: AnimatedSize,
        spec: ContentSizeSpringSpecModel,
    ) {
        val animation = TargetAnimation(
            initialValue = start,
            targetValue = end,
            animationSpec = SpringSpec(
                dampingRatio = spec.dampingRatio,
                stiffness = spec.stiffness,
                maxDurationMillis = spec.maxDurationMillis,
            ),
            converter = AnimatedSizeConverter,
            initialVelocity = AnimationVelocity(animatedVelocity),
        )
        if (animation.durationNanos == 0L) {
            val terminal = animation.stateAt(0L)
            animatedWidthPx = terminal.value.width
            animatedHeightPx = terminal.value.height
            animatedVelocity = terminal.velocity.valuePerSecond
            requestLayout()
            return
        }
        sizeAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = ((animation.durationNanos + 999_999L) / 1_000_000L).coerceAtLeast(1L)
            interpolator = LinearInterpolator()
            addUpdateListener { animator ->
                val playTimeNanos =
                    animator.currentPlayTime.coerceAtMost(duration) * 1_000_000L
                val state = animation.stateAt(playTimeNanos)
                if (state.value.width < 0f || state.value.height < 0f) {
                    // Android cannot commit negative geometry. End the whole segment at its valid
                    // target instead of retaining a zero-sized host whose accepted target is still
                    // positive and can no longer trigger a retarget.
                    animatedWidthPx = end.width.coerceAtLeast(0f)
                    animatedHeightPx = end.height.coerceAtLeast(0f)
                    animatedVelocity = AnimatedSize(0f, 0f)
                    requestLayout()
                    animator.cancel()
                } else {
                    animatedWidthPx = state.value.width
                    animatedHeightPx = state.value.height
                    animatedVelocity = state.velocity.valuePerSecond
                    requestLayout()
                }
            }
            var wasCancelled = false
            addListener(
                object : AnimatorListenerAdapter() {
                    override fun onAnimationCancel(animation: Animator) {
                        wasCancelled = true
                    }

                    override fun onAnimationEnd(platformAnimator: Animator) {
                        if (wasCancelled) return
                        val terminal = animation.stateAt(animation.durationNanos)
                        animatedWidthPx = terminal.value.width
                        animatedHeightPx = terminal.value.height
                        animatedVelocity = terminal.velocity.valuePerSecond
                        requestLayout()
                    }
                },
            )
            start()
        }
    }

    private fun lerp(
        start: Float,
        end: Float,
        fraction: Float,
    ): Float {
        return start + (end - start) * fraction
    }
}

private data class AnimatedSize(
    val width: Float,
    val height: Float,
)

private object AnimatedSizeConverter : AnimationConverter<AnimatedSize, AnimatedSize> {
    override val vectorSize: Int = 2
    override val zeroVelocity: AnimatedSize = AnimatedSize(0f, 0f)
    override val visibilityThreshold: AnimatedSize = AnimatedSize(1f, 1f)

    override fun convertToVector(value: AnimatedSize, destination: FloatArray) {
        require(destination.size == vectorSize)
        destination[0] = value.width
        destination[1] = value.height
    }

    override fun convertFromVector(vector: FloatArray): AnimatedSize {
        require(vector.size == vectorSize)
        return AnimatedSize(width = vector[0], height = vector[1])
    }

    override fun convertVelocityToVector(velocity: AnimatedSize, destination: FloatArray) {
        require(destination.size == vectorSize)
        destination[0] = velocity.width
        destination[1] = velocity.height
    }

    override fun convertVelocityFromVector(vector: FloatArray): AnimatedSize {
        require(vector.size == vectorSize)
        return AnimatedSize(width = vector[0], height = vector[1])
    }
}
