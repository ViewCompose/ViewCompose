package com.viewcompose.renderer.view.container

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import kotlin.math.roundToInt

/** One-child measurement boundary for portable maximum-size and aspect-ratio modifiers. */
internal class DeclarativeLayoutConstraintHost @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : ViewGroup(context, attrs, defStyleAttr) {
    private var maxWidthPx: Int? = null
    private var maxHeightPx: Int? = null
    private var aspectRatio: Float? = null
    private var matchHeightConstraintsFirst: Boolean = false
    private var fillWidth: Boolean = false
    private var fillHeight: Boolean = false

    fun bind(
        maxWidthPx: Int?,
        maxHeightPx: Int?,
        aspectRatio: Float?,
        matchHeightConstraintsFirst: Boolean,
        fillWidth: Boolean,
        fillHeight: Boolean,
    ) {
        if (
            this.maxWidthPx == maxWidthPx &&
            this.maxHeightPx == maxHeightPx &&
            this.aspectRatio == aspectRatio &&
            this.matchHeightConstraintsFirst == matchHeightConstraintsFirst &&
            this.fillWidth == fillWidth &&
            this.fillHeight == fillHeight
        ) {
            return
        }
        this.maxWidthPx = maxWidthPx
        this.maxHeightPx = maxHeightPx
        this.aspectRatio = aspectRatio
        this.matchHeightConstraintsFirst = matchHeightConstraintsFirst
        this.fillWidth = fillWidth
        this.fillHeight = fillHeight
        requestLayout()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val widthLimit = resolveLimit(widthMeasureSpec, maxWidthPx)
        val heightLimit = resolveLimit(heightMeasureSpec, maxHeightPx)
        val child = getChildAt(0)
        if (child == null) {
            setMeasuredDimension(
                emptyAxisSize(widthLimit, suggestedMinimumWidth, fillWidth),
                emptyAxisSize(heightLimit, suggestedMinimumHeight, fillHeight),
            )
            return
        }

        child.measure(
            initialChildSpec(widthLimit, fillWidth),
            initialChildSpec(heightLimit, fillHeight),
        )
        val ratio = aspectRatio
        if (ratio == null) {
            val width = chooseAxisSize(
                measuredChild = child.measuredWidth,
                limit = widthLimit,
                minimum = suggestedMinimumWidth,
                fill = fillWidth,
            )
            val height = chooseAxisSize(
                measuredChild = child.measuredHeight,
                limit = heightLimit,
                minimum = suggestedMinimumHeight,
                fill = fillHeight,
            )
            setMeasuredDimension(width, height)
            return
        }

        val target = resolveAspectSize(
            desiredWidth = child.measuredWidth,
            desiredHeight = child.measuredHeight,
            widthLimit = widthLimit,
            heightLimit = heightLimit,
            minimumWidth = suggestedMinimumWidth,
            minimumHeight = suggestedMinimumHeight,
            ratio = ratio,
        )
        child.measure(
            MeasureSpec.makeMeasureSpec(target.width, MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(target.height, MeasureSpec.EXACTLY),
        )
        setMeasuredDimension(target.width, target.height)
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        getChildAt(0)?.let { child ->
            child.layout(0, 0, child.measuredWidth, child.measuredHeight)
        }
    }

    override fun generateDefaultLayoutParams(): LayoutParams =
        LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT)

    override fun generateLayoutParams(attrs: AttributeSet?): LayoutParams = LayoutParams(context, attrs)

    override fun generateLayoutParams(params: LayoutParams?): LayoutParams = LayoutParams(params)

    override fun checkLayoutParams(params: LayoutParams?): Boolean = params != null

    private fun resolveAspectSize(
        desiredWidth: Int,
        desiredHeight: Int,
        widthLimit: AxisLimit,
        heightLimit: AxisLimit,
        minimumWidth: Int,
        minimumHeight: Int,
        ratio: Float,
    ): IntSize {
        val widthMinimum = widthLimit.effectiveMinimum(minimumWidth)
        val heightMinimum = heightLimit.effectiveMinimum(minimumHeight)
        val lowerWidth = maxOf(widthMinimum.toFloat(), heightMinimum * ratio)
        val upperWidth = minOf(
            widthLimit.maximum?.toFloat() ?: Float.POSITIVE_INFINITY,
            heightLimit.maximum?.times(ratio) ?: Float.POSITIVE_INFINITY,
        )
        if (lowerWidth <= upperWidth) {
            val preferredWidth = if (matchHeightConstraintsFirst) {
                val preferredHeight = preferredAxisSize(
                    limit = heightLimit,
                    desired = desiredHeight,
                    minimum = minimumHeight,
                    fill = fillHeight,
                )
                if (preferredHeight == 0 && desiredWidth > 0) {
                    desiredWidth.toFloat()
                } else {
                    preferredHeight * ratio
                }
            } else {
                val width = preferredAxisSize(
                    limit = widthLimit,
                    desired = desiredWidth,
                    minimum = minimumWidth,
                    fill = fillWidth,
                )
                if (width == 0 && desiredHeight > 0) desiredHeight * ratio else width.toFloat()
            }
            val width = widthLimit.constrain(
                preferredWidth.coerceIn(lowerWidth, upperWidth).roundToInt(),
            )
            val height = heightLimit.constrain((width / ratio).roundToInt())
            return IntSize(width = width, height = height)
        }

        // An exact parent can make the requested ratio impossible. Android's incoming measurement
        // contract wins, while each axis remains independently constrained.
        return IntSize(
            width = chooseAxisSize(desiredWidth, widthLimit, minimumWidth, fillWidth),
            height = chooseAxisSize(desiredHeight, heightLimit, minimumHeight, fillHeight),
        )
    }

    private data class IntSize(val width: Int, val height: Int)

    private data class AxisLimit(
        val minimum: Int,
        val maximum: Int?,
    ) {
        fun constrain(value: Int): Int {
            val atLeastMinimum = value.coerceAtLeast(minimum)
            return maximum?.let(atLeastMinimum::coerceAtMost) ?: atLeastMinimum
        }

        fun effectiveMinimum(declaredMinimum: Int): Int {
            return constrain(declaredMinimum.coerceAtLeast(minimum))
        }
    }

    private companion object {
        fun resolveLimit(measureSpec: Int, declaredMaximum: Int?): AxisLimit {
            val parentSize = MeasureSpec.getSize(measureSpec)
            return when (MeasureSpec.getMode(measureSpec)) {
                MeasureSpec.EXACTLY -> AxisLimit(
                    minimum = parentSize,
                    maximum = parentSize,
                )
                MeasureSpec.AT_MOST -> AxisLimit(
                    minimum = 0,
                    maximum = minOf(parentSize, declaredMaximum ?: parentSize),
                )
                else -> AxisLimit(
                    minimum = 0,
                    maximum = declaredMaximum,
                )
            }
        }

        fun initialChildSpec(limit: AxisLimit, fill: Boolean): Int = when {
            limit.maximum == null -> MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)
            fill || limit.minimum == limit.maximum ->
                MeasureSpec.makeMeasureSpec(limit.maximum, MeasureSpec.EXACTLY)
            else -> MeasureSpec.makeMeasureSpec(limit.maximum, MeasureSpec.AT_MOST)
        }

        fun preferredAxisSize(
            limit: AxisLimit,
            desired: Int,
            minimum: Int,
            fill: Boolean,
        ): Int = chooseAxisSize(desired, limit, minimum, fill)

        fun chooseAxisSize(
            measuredChild: Int,
            limit: AxisLimit,
            minimum: Int,
            fill: Boolean,
        ): Int {
            val effectiveMinimum = limit.effectiveMinimum(minimum)
            val desired = if (fill) {
                limit.maximum ?: maxOf(measuredChild, effectiveMinimum)
            } else {
                maxOf(measuredChild, effectiveMinimum)
            }
            return limit.constrain(desired)
        }

        fun emptyAxisSize(limit: AxisLimit, minimum: Int, fill: Boolean): Int =
            chooseAxisSize(measuredChild = 0, limit = limit, minimum = minimum, fill = fill)
    }
}
