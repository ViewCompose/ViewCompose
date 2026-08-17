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
 * Custom column-wrapping container used by FlowColumn.
 */
internal class DeclarativeFlowColumnLayout @JvmOverloads constructor(
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

    var maxItemsInEachColumn: Int = Int.MAX_VALUE
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

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val startNs = LayoutPassTracker.beginTiming()
        val heightMode = MeasureSpec.getMode(heightMeasureSpec)
        val availableHeight = if (heightMode == MeasureSpec.UNSPECIFIED) {
            Int.MAX_VALUE
        } else {
            (MeasureSpec.getSize(heightMeasureSpec) - paddingTop - paddingBottom).coerceAtLeast(0)
        }

        var currentColumnHeight = 0
        var currentColumnWidth = 0
        var totalWidth = 0
        var maxColumnHeight = 0
        var itemsInCurrentColumn = 0

        for (i in 0 until childCount) {
            val child = getChildAt(i)
            if (child.visibility == GONE) continue

            // Completed columns affect this container's total width, not the constraints of later
            // columns. Passing totalWidth as widthUsed progressively compresses wrap-content children.
            measureChildWithMargins(child, widthMeasureSpec, 0, heightMeasureSpec, 0)

            val params = child.layoutParams as MarginLayoutParams
            val childWidth = child.measuredWidth + params.leftMargin + params.rightMargin
            val childHeight = child.measuredHeight + params.topMargin + params.bottomMargin
            val spacingNeeded = if (itemsInCurrentColumn > 0) verticalSpacing else 0

            if (currentColumnHeight + spacingNeeded + childHeight > availableHeight && itemsInCurrentColumn > 0) {
                maxColumnHeight = max(maxColumnHeight, currentColumnHeight)
                totalWidth += currentColumnWidth + horizontalSpacing
                currentColumnHeight = 0
                currentColumnWidth = 0
                itemsInCurrentColumn = 0
            }

            if (itemsInCurrentColumn > 0) {
                currentColumnHeight += verticalSpacing
            }
            currentColumnHeight += childHeight
            currentColumnWidth = max(currentColumnWidth, childWidth)
            itemsInCurrentColumn++

            if (itemsInCurrentColumn >= maxItemsInEachColumn) {
                maxColumnHeight = max(maxColumnHeight, currentColumnHeight)
                totalWidth += currentColumnWidth + horizontalSpacing
                currentColumnHeight = 0
                currentColumnWidth = 0
                itemsInCurrentColumn = 0
            }
        }

        if (itemsInCurrentColumn > 0) {
            maxColumnHeight = max(maxColumnHeight, currentColumnHeight)
            totalWidth += currentColumnWidth
        } else if (totalWidth > 0) {
            // A maxItemsInEachColumn boundary may end the final column, which must not retain a trailing gap.
            totalWidth = (totalWidth - horizontalSpacing).coerceAtLeast(0)
        }

        setMeasuredDimension(
            resolveSize(totalWidth + paddingLeft + paddingRight, widthMeasureSpec),
            resolveSize(maxColumnHeight + paddingTop + paddingBottom, heightMeasureSpec),
        )
        LayoutPassTracker.recordMeasureSince(javaClass, startNs)
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        val startNs = LayoutPassTracker.beginTiming()
        val availableHeight = b - t - paddingTop - paddingBottom
        val rtl = layoutDirection == LAYOUT_DIRECTION_RTL

        var completedColumnsWidth = 0
        var currentY = paddingTop
        var currentColumnWidth = 0
        var itemsInCurrentColumn = 0

        for (i in 0 until childCount) {
            val child = getChildAt(i)
            if (child.visibility == GONE) continue

            val params = child.layoutParams as MarginLayoutParams
            val childWidth = child.measuredWidth + params.leftMargin + params.rightMargin
            val childHeight = child.measuredHeight + params.topMargin + params.bottomMargin
            val spacingNeeded = if (itemsInCurrentColumn > 0) verticalSpacing else 0

            if (currentY - paddingTop + spacingNeeded + childHeight > availableHeight && itemsInCurrentColumn > 0) {
                completedColumnsWidth += currentColumnWidth + horizontalSpacing
                currentY = paddingTop
                currentColumnWidth = 0
                itemsInCurrentColumn = 0
            }

            if (itemsInCurrentColumn > 0) {
                currentY += verticalSpacing
            }

            val childLeft = if (rtl) {
                r - l - paddingRight - completedColumnsWidth - params.rightMargin - child.measuredWidth
            } else {
                paddingLeft + completedColumnsWidth + params.leftMargin
            }
            val childTop = currentY + params.topMargin
            child.layout(childLeft, childTop, childLeft + child.measuredWidth, childTop + child.measuredHeight)

            currentY += childHeight
            currentColumnWidth = max(currentColumnWidth, childWidth)
            itemsInCurrentColumn++

            if (itemsInCurrentColumn >= maxItemsInEachColumn) {
                completedColumnsWidth += currentColumnWidth + horizontalSpacing
                currentY = paddingTop
                currentColumnWidth = 0
                itemsInCurrentColumn = 0
            }
        }
        LayoutPassTracker.recordLayoutSince(javaClass, startNs)
    }
}
