package com.viewcompose.renderer.view.container

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import com.viewcompose.renderer.view.tree.LayoutPassTracker
import kotlin.math.max

internal class DeclarativeFlowColumnLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : ViewGroup(context, attrs) {
    var horizontalSpacing: Int = 0
        set(value) {
            field = value
            requestLayout()
        }

    var verticalSpacing: Int = 0
        set(value) {
            field = value
            requestLayout()
        }

    var maxItemsInEachColumn: Int = Int.MAX_VALUE
        set(value) {
            field = value.coerceAtLeast(1)
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

            measureChildWithMargins(child, widthMeasureSpec, totalWidth, heightMeasureSpec, 0)

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
            // The last column can close via maxItemsInEachColumn and should not leave trailing spacing.
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

        var currentX = paddingLeft
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
                currentX += currentColumnWidth + horizontalSpacing
                currentY = paddingTop
                currentColumnWidth = 0
                itemsInCurrentColumn = 0
            }

            if (itemsInCurrentColumn > 0) {
                currentY += verticalSpacing
            }

            child.layout(
                currentX + params.leftMargin,
                currentY + params.topMargin,
                currentX + params.leftMargin + child.measuredWidth,
                currentY + params.topMargin + child.measuredHeight,
            )

            currentY += childHeight
            currentColumnWidth = max(currentColumnWidth, childWidth)
            itemsInCurrentColumn++

            if (itemsInCurrentColumn >= maxItemsInEachColumn) {
                currentX += currentColumnWidth + horizontalSpacing
                currentY = paddingTop
                currentColumnWidth = 0
                itemsInCurrentColumn = 0
            }
        }
        LayoutPassTracker.recordLayoutSince(javaClass, startNs)
    }
}
