package com.viewcompose.renderer.view.feedback

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.view.View
import android.view.accessibility.AccessibilityNodeInfo
import android.view.animation.LinearInterpolator
import kotlin.math.max

/** Engine-owned determinate and indeterminate progress rendering for both supported geometries. */
internal class DeclarativeProgressIndicatorView(
    context: Context,
    val mode: Mode,
) : View(context) {
    enum class Mode { Linear, Circular }

    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val indicatorPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private var progressState: Float? = null
    private var trackThicknessState = 0
    private var indicatorSizeState = 0
    private var animationFraction = 0f
    private var animator: ValueAnimator? = null
    private var hasSpec = false

    init {
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
    }

    fun bind(
        enabled: Boolean,
        progress: Float?,
        indicatorColor: Int,
        trackColor: Int,
        trackThickness: Int,
        indicatorSize: Int,
    ) {
        hasSpec = true
        isEnabled = enabled
        progressState = progress?.coerceIn(0f, 1f)
        indicatorPaint.color = indicatorColor
        trackPaint.color = trackColor
        trackThicknessState = trackThickness.coerceAtLeast(1)
        indicatorSizeState = indicatorSize.coerceAtLeast(trackThicknessState)
        trackPaint.strokeWidth = trackThicknessState.toFloat()
        indicatorPaint.strokeWidth = trackThicknessState.toFloat()
        updateAnimator()
        requestLayout()
        invalidate()
        sendAccessibilityEvent(android.view.accessibility.AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val desiredWidth = if (mode == Mode.Circular) indicatorSizeState else max(suggestedMinimumWidth, trackThicknessState * 24)
        val desiredHeight = if (mode == Mode.Circular) indicatorSizeState else max(suggestedMinimumHeight, trackThicknessState)
        setMeasuredDimension(
            resolveSize(desiredWidth + paddingLeft + paddingRight, widthMeasureSpec),
            resolveSize(desiredHeight + paddingTop + paddingBottom, heightMeasureSpec),
        )
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (mode == Mode.Linear) drawLinear(canvas) else drawCircular(canvas)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        updateAnimator()
    }

    override fun onDetachedFromWindow() {
        animator?.cancel()
        animator = null
        super.onDetachedFromWindow()
    }

    override fun onVisibilityAggregated(isVisible: Boolean) {
        super.onVisibilityAggregated(isVisible)
        updateAnimator()
    }

    override fun onInitializeAccessibilityNodeInfo(info: AccessibilityNodeInfo) {
        super.onInitializeAccessibilityNodeInfo(info)
        info.className = android.widget.ProgressBar::class.java.name
        val progress = progressState
        if (progress != null) {
            info.rangeInfo = AccessibilityNodeInfo.RangeInfo.obtain(
                AccessibilityNodeInfo.RangeInfo.RANGE_TYPE_FLOAT,
                0f,
                1f,
                progress,
            )
        }
    }

    private fun drawLinear(canvas: Canvas) {
        val y = height / 2f
        val start = paddingLeft + trackThicknessState / 2f
        val end = width - paddingRight - trackThicknessState / 2f
        val available = (end - start).coerceAtLeast(0f)
        canvas.drawLine(start, y, end, y, trackPaint)
        val progress = progressState
        if (progress != null) {
            canvas.drawLine(start, y, start + available * progress, y, indicatorPaint)
        } else {
            val segment = available * 0.3f
            val leading = start + (available + segment) * animationFraction - segment
            canvas.drawLine(leading.coerceAtLeast(start), y, (leading + segment).coerceAtMost(end), y, indicatorPaint)
        }
    }

    private fun drawCircular(canvas: Canvas) {
        val halfStroke = trackThicknessState / 2f
        val size = max(0f, minOf(width - paddingLeft - paddingRight, height - paddingTop - paddingBottom).toFloat())
        val left = paddingLeft + (width - paddingLeft - paddingRight - size) / 2f + halfStroke
        val top = paddingTop + (height - paddingTop - paddingBottom - size) / 2f + halfStroke
        val diameter = (size - trackThicknessState).coerceAtLeast(0f)
        val oval = RectF(left, top, left + diameter, top + diameter)
        canvas.drawArc(oval, 0f, 360f, false, trackPaint)
        val progress = progressState
        val start = if (progress == null) animationFraction * 360f - 90f else -90f
        val sweep = if (progress == null) 100f else progress * 360f
        canvas.drawArc(oval, start, sweep, false, indicatorPaint)
    }

    private fun updateAnimator() {
        val shouldAnimate = hasSpec && progressState == null && isAttachedToWindow && isShown
        if (!shouldAnimate) {
            animator?.cancel()
            animator = null
            return
        }
        if (animator != null) return
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 1_200L
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            addUpdateListener { animation ->
                animationFraction = animation.animatedValue as Float
                invalidate()
            }
            start()
        }
    }
}
