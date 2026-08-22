package com.viewcompose.renderer.view.container

import android.content.Context
import android.graphics.Canvas
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import com.viewcompose.renderer.decoration.DecorationChildDrawingOrder
import com.viewcompose.renderer.decoration.DecorationDrawingOrderContainer
import com.viewcompose.renderer.decoration.ViewDecorationDrawing
import com.viewcompose.renderer.view.tree.LayoutPassTracker
import kotlin.math.roundToInt

/** Android host that measures and places the bounded outgoing/incoming content pair. */
internal class DeclarativeAnimatedContentHostLayout(
    context: Context,
) : FrameLayout(context), DecorationDrawingOrderContainer {
    private val decorationDrawing = ViewDecorationDrawing(this)
    private var measuredSegmentId: Long = Long.MIN_VALUE
    private var segmentStartWidth: Int = 0
    private var segmentStartHeight: Int = 0

    var segmentId: Long = 0L
        set(value) {
            if (field == value) return
            field = value
            requestLayout()
        }

    var sizeProgress: Float = 1f
        set(value) {
            require(value.isFinite()) { "Animated content size progress must be finite." }
            if (field == value) return
            field = value
            requestLayout()
        }

    var sizeTransformEnabled: Boolean = false
        set(value) {
            if (field == value) return
            field = value
            requestLayout()
        }

    var clipToBounds: Boolean = false
        set(value) {
            if (field == value) return
            field = value
            clipChildren = value
            clipToPadding = value
            invalidate()
        }

    var contentGravity: Int = Gravity.TOP or Gravity.START
        set(value) {
            if (field == value) return
            field = value
            updateChildGravity()
            requestLayout()
        }

    init {
        clipChildren = false
        clipToPadding = false
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
        val previouslyMeasuredWidth = measuredWidth
        val previouslyMeasuredHeight = measuredHeight
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        require(childCount <= MAX_CONTENT_ITEMS) {
            "AnimatedContentHost accepts at most two content items, but received $childCount."
        }
        if (sizeTransformEnabled && childCount > 0) {
            if (measuredSegmentId != segmentId) {
                val outgoingSize = childSizeWithMargins(getChildAt(0))
                segmentStartWidth = previouslyMeasuredWidth.takeIf { it > 0 } ?: outgoingSize.first
                segmentStartHeight = previouslyMeasuredHeight.takeIf { it > 0 } ?: outgoingSize.second
                measuredSegmentId = segmentId
            }
            val targetSize = childSizeWithMargins(getChildAt(childCount - 1))
            val animatedWidth = interpolateDimension(segmentStartWidth, targetSize.first, sizeProgress)
            val animatedHeight = interpolateDimension(segmentStartHeight, targetSize.second, sizeProgress)
            setMeasuredDimension(
                resolveAnimatedDimension(animatedWidth, widthMeasureSpec),
                resolveAnimatedDimension(animatedHeight, heightMeasureSpec),
            )
        } else {
            measuredSegmentId = segmentId
            segmentStartWidth = measuredWidth
            segmentStartHeight = measuredHeight
        }
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
        super.onLayout(changed, left, top, right, bottom)
        LayoutPassTracker.recordLayoutSince(javaClass, startNs)
    }

    override fun onViewAdded(child: View) {
        super.onViewAdded(child)
        DecorationChildDrawingOrder.onViewAdded(this, child)
        decorationDrawing.onViewAdded(child)
        applyContentGravity(child)
    }

    override fun onViewRemoved(child: View) {
        decorationDrawing.onViewRemoved(child)
        super.onViewRemoved(child)
        DecorationChildDrawingOrder.onViewRemoved(this, child)
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

    private fun childSizeWithMargins(child: View): Pair<Int, Int> {
        val params = child.layoutParams as? MarginLayoutParams
        val horizontalMargins = (params?.leftMargin ?: 0) + (params?.rightMargin ?: 0)
        val verticalMargins = (params?.topMargin ?: 0) + (params?.bottomMargin ?: 0)
        return Pair(
            (child.measuredWidth + horizontalMargins + paddingLeft + paddingRight)
                .coerceAtLeast(suggestedMinimumWidth),
            (child.measuredHeight + verticalMargins + paddingTop + paddingBottom)
                .coerceAtLeast(suggestedMinimumHeight),
        )
    }

    private fun updateChildGravity() {
        (0 until childCount).forEach { index ->
            applyContentGravity(getChildAt(index))
        }
    }

    private fun applyContentGravity(child: View) {
        val params = child.layoutParams as? LayoutParams ?: return
        if (params.gravity != contentGravity) {
            params.gravity = contentGravity
        }
    }

    private fun resolveAnimatedDimension(animatedSize: Int, measureSpec: Int): Int {
        val specMode = MeasureSpec.getMode(measureSpec)
        val specSize = MeasureSpec.getSize(measureSpec)
        return when (specMode) {
            MeasureSpec.UNSPECIFIED -> animatedSize
            MeasureSpec.AT_MOST -> animatedSize.coerceAtMost(specSize)
            MeasureSpec.EXACTLY -> specSize
            else -> animatedSize
        }
    }

    private fun interpolateDimension(start: Int, target: Int, progress: Float): Int {
        return (start + (target - start) * progress).roundToInt().coerceAtLeast(0)
    }

    private companion object {
        const val MAX_CONTENT_ITEMS = 2
    }
}
