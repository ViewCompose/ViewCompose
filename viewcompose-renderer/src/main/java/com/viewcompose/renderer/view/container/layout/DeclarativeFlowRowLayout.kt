package com.viewcompose.renderer.view.container

import android.content.Context
import android.graphics.Canvas
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import com.viewcompose.renderer.view.tree.LayoutPassTracker
import com.viewcompose.renderer.decoration.DecorationChildDrawingOrder
import com.viewcompose.renderer.decoration.DecorationDrawingOrderContainer
import com.viewcompose.renderer.decoration.ViewDecorationDrawing
import kotlin.math.max

/**
 * FlowRow 使用的自定义换行容器。
 * Custom wrapping container used by FlowRow.
 */
internal class DeclarativeFlowRowLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : ViewGroup(context, attrs), DecorationDrawingOrderContainer {
    private val decorationDrawing = ViewDecorationDrawing(this)

    init {
        clipChildren = false
        clipToPadding = false
    }

    override fun getChildDrawingOrder(childCount: Int, drawingPosition: Int): Int =
        DecorationChildDrawingOrder.getChildDrawingOrder(this, childCount, drawingPosition)

    override fun setDecorationDrawingOrderEnabled(enabled: Boolean) {
        isChildrenDrawingOrderEnabled = enabled
    }

    var horizontalSpacing: Int = 0
        set(value) {
            if (field == value) return
            field = value
            requestLayout()
        }

    var verticalSpacing: Int = 0
        set(value) {
            if (field == value) return
            field = value
            requestLayout()
        }

    var maxItemsInEachRow: Int = Int.MAX_VALUE
        set(value) {
            val resolved = value.coerceAtLeast(1)
            if (field == resolved) return
            field = resolved
            requestLayout()
        }

    override fun generateDefaultLayoutParams(): LayoutParams =
        MarginLayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT)

    override fun generateLayoutParams(attrs: AttributeSet?): LayoutParams =
        MarginLayoutParams(context, attrs)

    override fun generateLayoutParams(p: LayoutParams): LayoutParams =
        MarginLayoutParams(p)

    override fun checkLayoutParams(p: LayoutParams): Boolean =
        p is MarginLayoutParams

    override fun drawChild(
        canvas: Canvas,
        child: View,
        drawingTime: Long,
    ): Boolean {
        if (!decorationDrawing.hasDecoratedChildren) {
            return super.drawChild(canvas, child, drawingTime)
        }
        decorationDrawing.drawBehindChild(canvas, child)
        val drawn = super.drawChild(canvas, child, drawingTime)
        decorationDrawing.drawOverChild(canvas, child)
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

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val startNs = LayoutPassTracker.beginTiming()
        val widthMode = MeasureSpec.getMode(widthMeasureSpec)
        val availableWidth = if (widthMode == MeasureSpec.UNSPECIFIED) {
            Int.MAX_VALUE
        } else {
            (MeasureSpec.getSize(widthMeasureSpec) - paddingLeft - paddingRight).coerceAtLeast(0)
        }

        var currentRowWidth = 0
        var currentRowHeight = 0
        var totalHeight = 0
        var maxRowWidth = 0
        var itemsInCurrentRow = 0

        for (i in 0 until childCount) {
            val child = getChildAt(i)
            if (child.visibility == GONE) continue

            measureChildWithMargins(child, widthMeasureSpec, 0, heightMeasureSpec, totalHeight)

            val params = child.layoutParams as MarginLayoutParams
            val childWidth = child.measuredWidth + params.leftMargin + params.rightMargin
            val childHeight = child.measuredHeight + params.topMargin + params.bottomMargin
            val spacingNeeded = if (itemsInCurrentRow > 0) horizontalSpacing else 0

            if (currentRowWidth + spacingNeeded + childWidth > availableWidth && itemsInCurrentRow > 0) {
                maxRowWidth = max(maxRowWidth, currentRowWidth)
                totalHeight += currentRowHeight + verticalSpacing
                currentRowWidth = 0
                currentRowHeight = 0
                itemsInCurrentRow = 0
            }

            if (itemsInCurrentRow > 0) {
                currentRowWidth += horizontalSpacing
            }
            currentRowWidth += childWidth
            currentRowHeight = max(currentRowHeight, childHeight)
            itemsInCurrentRow++

            if (itemsInCurrentRow >= maxItemsInEachRow) {
                maxRowWidth = max(maxRowWidth, currentRowWidth)
                totalHeight += currentRowHeight + verticalSpacing
                currentRowWidth = 0
                currentRowHeight = 0
                itemsInCurrentRow = 0
            }
        }

        if (itemsInCurrentRow > 0) {
            maxRowWidth = max(maxRowWidth, currentRowWidth)
            totalHeight += currentRowHeight
        } else if (totalHeight > 0) {
            // 最后一行可能由 maxItemsInEachRow 截止，此时不应留下尾部间距。
            // The last row can close via maxItemsInEachRow and should not leave trailing spacing.
            totalHeight = (totalHeight - verticalSpacing).coerceAtLeast(0)
        }

        setMeasuredDimension(
            resolveSize(maxRowWidth + paddingLeft + paddingRight, widthMeasureSpec),
            resolveSize(totalHeight + paddingTop + paddingBottom, heightMeasureSpec),
        )
        LayoutPassTracker.recordMeasureSince(javaClass, startNs)
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        val startNs = LayoutPassTracker.beginTiming()
        val availableWidth = r - l - paddingLeft - paddingRight

        var currentX = paddingLeft
        var currentY = paddingTop
        var currentRowHeight = 0
        var itemsInCurrentRow = 0

        for (i in 0 until childCount) {
            val child = getChildAt(i)
            if (child.visibility == GONE) continue

            val params = child.layoutParams as MarginLayoutParams
            val childWidth = child.measuredWidth + params.leftMargin + params.rightMargin
            val childHeight = child.measuredHeight + params.topMargin + params.bottomMargin
            val spacingNeeded = if (itemsInCurrentRow > 0) horizontalSpacing else 0

            if (currentX - paddingLeft + spacingNeeded + childWidth > availableWidth && itemsInCurrentRow > 0) {
                currentX = paddingLeft
                currentY += currentRowHeight + verticalSpacing
                currentRowHeight = 0
                itemsInCurrentRow = 0
            }

            if (itemsInCurrentRow > 0) {
                currentX += horizontalSpacing
            }

            child.layout(
                currentX + params.leftMargin,
                currentY + params.topMargin,
                currentX + params.leftMargin + child.measuredWidth,
                currentY + params.topMargin + child.measuredHeight,
            )

            currentX += childWidth
            currentRowHeight = max(currentRowHeight, childHeight)
            itemsInCurrentRow++

            if (itemsInCurrentRow >= maxItemsInEachRow) {
                currentX = paddingLeft
                currentY += currentRowHeight + verticalSpacing
                currentRowHeight = 0
                itemsInCurrentRow = 0
            }
        }
        LayoutPassTracker.recordLayoutSince(javaClass, startNs)
    }
}
