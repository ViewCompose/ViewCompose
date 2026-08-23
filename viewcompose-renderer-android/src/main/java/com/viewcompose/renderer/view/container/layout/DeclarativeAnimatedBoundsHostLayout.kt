package com.viewcompose.renderer.view.container

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Rect
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator
import android.widget.FrameLayout
import com.viewcompose.animation.core.AnimationConverter
import com.viewcompose.animation.core.AnimationVelocity
import com.viewcompose.animation.core.SpringSpec
import com.viewcompose.animation.core.TargetAnimation
import com.viewcompose.renderer.decoration.DecorationChildDrawingOrder
import com.viewcompose.renderer.decoration.DecorationDrawingOrderContainer
import com.viewcompose.renderer.decoration.ViewDecorationDrawing
import com.viewcompose.renderer.view.tree.LayoutPassTracker
import com.viewcompose.ui.modifier.ContentSizeDurationBasedAnimationSpecModel
import com.viewcompose.ui.modifier.ContentSizeSnapSpecModel
import com.viewcompose.ui.modifier.ContentSizeSpringSpecModel
import com.viewcompose.ui.modifier.LayoutAnimationSpecModel
import kotlin.math.roundToInt

/** Android host that commits every sampled bounds frame through real View layout geometry. */
internal class DeclarativeAnimatedBoundsHostLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : FrameLayout(context, attrs), DecorationDrawingOrderContainer, ReusableLayoutAnimationHost {
    private val decorationDrawing = ViewDecorationDrawing(this)

    var animationSpec: LayoutAnimationSpecModel = ContentSizeSnapSpecModel

    private var animatedBounds: AnimatedLayoutBounds? = null
    private var targetBounds: AnimatedLayoutBounds? = null
    private var animatedVelocity = AnimatedLayoutBounds.Zero
    private var boundsAnimator: ValueAnimator? = null

    init {
        clipChildren = true
        clipToPadding = true
    }

    override fun getChildDrawingOrder(childCount: Int, drawingPosition: Int): Int =
        DecorationChildDrawingOrder.getChildDrawingOrder(this, childCount, drawingPosition)

    override fun setDecorationDrawingOrderEnabled(enabled: Boolean) {
        isChildrenDrawingOrderEnabled = enabled
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val startNs = LayoutPassTracker.beginTiming()
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        LayoutPassTracker.recordMeasureSince(javaClass, startNs)
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        val startNs = LayoutPassTracker.beginTiming()
        val candidate = AnimatedLayoutBounds(
            left = left.toFloat(),
            top = top.toFloat(),
            right = right.toFloat(),
            bottom = bottom.toFloat(),
        )
        val current = animatedBounds
        if (current == null) {
            animatedBounds = candidate
            targetBounds = candidate
            animatedVelocity = AnimatedLayoutBounds.Zero
        } else if (candidate != targetBounds) {
            targetBounds = candidate
            startBoundsAnimation(start = current, end = candidate)
        }
        applyAnimatedBounds()
        LayoutPassTracker.recordLayoutSince(javaClass, startNs)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        resetLayoutAnimationForReuse()
    }

    override fun resetLayoutAnimationForReuse() {
        boundsAnimator?.cancel()
        boundsAnimator = null
        animatedBounds = null
        targetBounds = null
        animatedVelocity = AnimatedLayoutBounds.Zero
    }

    override fun drawChild(canvas: Canvas, child: View, drawingTime: Long): Boolean {
        if (!decorationDrawing.hasDecoratedChildren) return super.drawChild(canvas, child, drawingTime)
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

    private fun startBoundsAnimation(start: AnimatedLayoutBounds, end: AnimatedLayoutBounds) {
        boundsAnimator?.cancel()
        boundsAnimator = null
        if (start == end) {
            animatedVelocity = AnimatedLayoutBounds.Zero
            return
        }
        when (val spec = animationSpec) {
            is ContentSizeSpringSpecModel -> startPhysicalSpring(start, end, spec)
            is ContentSizeDurationBasedAnimationSpecModel -> {
                animatedVelocity = AnimatedLayoutBounds.Zero
                startDurationAnimation(start, end, spec)
            }
        }
    }

    private fun startDurationAnimation(
        start: AnimatedLayoutBounds,
        end: AnimatedLayoutBounds,
        spec: ContentSizeDurationBasedAnimationSpecModel,
    ) {
        val config = spec.resolveLayoutAnimationConfig()
        if (config.durationMillis <= 0L) {
            animatedBounds = if (config.terminalFraction == 0f) start else end
            applyAnimatedBounds()
            return
        }
        val animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = config.durationMillis
            startDelay = config.delayMillis
            interpolator = config.interpolator
            repeatCount = config.repeatCount
            repeatMode = config.repeatMode
            addUpdateListener { animator ->
                animatedBounds = start.lerp(end, animator.animatedValue as Float)
                applyAnimatedBounds()
            }
            var wasCancelled = false
            addListener(
                object : AnimatorListenerAdapter() {
                    override fun onAnimationCancel(animation: Animator) {
                        wasCancelled = true
                    }

                    override fun onAnimationEnd(animation: Animator) {
                        if (boundsAnimator === animation) boundsAnimator = null
                        if (wasCancelled) return
                        animatedBounds = start.lerp(end, config.terminalFraction)
                        animatedVelocity = AnimatedLayoutBounds.Zero
                        applyAnimatedBounds()
                    }
                },
            )
        }
        boundsAnimator = animator
        animator.start()
    }

    private fun startPhysicalSpring(
        start: AnimatedLayoutBounds,
        end: AnimatedLayoutBounds,
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
            converter = AnimatedLayoutBoundsConverter,
            initialVelocity = AnimationVelocity(animatedVelocity),
        )
        if (animation.durationNanos == 0L) {
            val terminal = animation.stateAt(0L)
            animatedBounds = terminal.value
            animatedVelocity = terminal.velocity.valuePerSecond
            applyAnimatedBounds()
            return
        }
        val animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = ((animation.durationNanos + 999_999L) / 1_000_000L).coerceAtLeast(1L)
            interpolator = LinearInterpolator()
            addUpdateListener { animator ->
                val playTimeNanos = animator.currentPlayTime.coerceAtMost(duration) * 1_000_000L
                val state = animation.stateAt(playTimeNanos)
                if (!state.value.hasValidSize) {
                    animatedBounds = end
                    animatedVelocity = AnimatedLayoutBounds.Zero
                    applyAnimatedBounds()
                    animator.cancel()
                } else {
                    animatedBounds = state.value
                    animatedVelocity = state.velocity.valuePerSecond
                    applyAnimatedBounds()
                }
            }
            var wasCancelled = false
            addListener(
                object : AnimatorListenerAdapter() {
                    override fun onAnimationCancel(animation: Animator) {
                        wasCancelled = true
                    }

                    override fun onAnimationEnd(platformAnimator: Animator) {
                        if (boundsAnimator === platformAnimator) boundsAnimator = null
                        if (wasCancelled) return
                        val terminal = animation.stateAt(animation.durationNanos)
                        animatedBounds = terminal.value
                        animatedVelocity = terminal.velocity.valuePerSecond
                        applyAnimatedBounds()
                    }
                },
            )
        }
        boundsAnimator = animator
        animator.start()
    }

    private fun applyAnimatedBounds() {
        val value = animatedBounds ?: return
        val roundedLeft = value.left.roundToInt()
        val roundedTop = value.top.roundToInt()
        val roundedRight = value.right.roundToInt().coerceAtLeast(roundedLeft)
        val roundedBottom = value.bottom.roundToInt().coerceAtLeast(roundedTop)
        setLeftTopRightBottom(roundedLeft, roundedTop, roundedRight, roundedBottom)
        val contentLeft = paddingLeft
        val contentTop = paddingTop
        val contentRight = (roundedRight - roundedLeft - paddingRight).coerceAtLeast(contentLeft)
        val contentBottom = (roundedBottom - roundedTop - paddingBottom).coerceAtLeast(contentTop)
        if (childCount == 1) {
            getChildAt(0).layout(contentLeft, contentTop, contentRight, contentBottom)
        }
    }

    internal fun currentBoundsForTest(): Rect = Rect(left, top, right, bottom)

    internal fun targetBoundsForTest(): Rect? = targetBounds?.toRect()

    internal fun animatorForTest(): ValueAnimator? = boundsAnimator

    internal fun velocityForTest(): FloatArray = floatArrayOf(
        animatedVelocity.left,
        animatedVelocity.top,
        animatedVelocity.right,
        animatedVelocity.bottom,
    )
}

private data class AnimatedLayoutBounds(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
) {
    val hasValidSize: Boolean
        get() = right >= left && bottom >= top

    fun lerp(end: AnimatedLayoutBounds, fraction: Float): AnimatedLayoutBounds =
        AnimatedLayoutBounds(
            left = left + (end.left - left) * fraction,
            top = top + (end.top - top) * fraction,
            right = right + (end.right - right) * fraction,
            bottom = bottom + (end.bottom - bottom) * fraction,
        )

    fun toRect(): Rect = Rect(
        left.roundToInt(),
        top.roundToInt(),
        right.roundToInt(),
        bottom.roundToInt(),
    )

    companion object {
        val Zero = AnimatedLayoutBounds(0f, 0f, 0f, 0f)
    }
}

private object AnimatedLayoutBoundsConverter : AnimationConverter<AnimatedLayoutBounds, AnimatedLayoutBounds> {
    override val vectorSize: Int = 4
    override val zeroVelocity: AnimatedLayoutBounds = AnimatedLayoutBounds.Zero
    override val visibilityThreshold: AnimatedLayoutBounds = AnimatedLayoutBounds(1f, 1f, 1f, 1f)

    override fun convertToVector(value: AnimatedLayoutBounds, destination: FloatArray) {
        require(destination.size == vectorSize)
        destination[0] = value.left
        destination[1] = value.top
        destination[2] = value.right
        destination[3] = value.bottom
    }

    override fun convertFromVector(vector: FloatArray): AnimatedLayoutBounds {
        require(vector.size == vectorSize)
        return AnimatedLayoutBounds(vector[0], vector[1], vector[2], vector[3])
    }

    override fun convertVelocityToVector(velocity: AnimatedLayoutBounds, destination: FloatArray) {
        convertToVector(velocity, destination)
    }

    override fun convertVelocityFromVector(vector: FloatArray): AnimatedLayoutBounds =
        convertFromVector(vector)
}
