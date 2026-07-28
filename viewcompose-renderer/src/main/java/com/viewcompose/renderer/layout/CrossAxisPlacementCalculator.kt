package com.viewcompose.renderer.layout

import com.viewcompose.ui.layout.HorizontalAlignment
import com.viewcompose.ui.layout.VerticalAlignment
import kotlin.math.max

/**
 * 计算 child 在交叉轴上的起始坐标。
 * Calculates the child's leading coordinate on the cross axis.
 */
internal object CrossAxisPlacementCalculator {
    /**
     * 计算水平交叉轴位置。
     * Calculates horizontal cross-axis placement.
     */
    fun calculateHorizontal(
        containerSize: Int,
        childSize: Int,
        leadingMargin: Int,
        trailingMargin: Int,
        alignment: HorizontalAlignment,
    ): Int {
        return calculate(
            containerSize = containerSize,
            childSize = childSize,
            leadingMargin = leadingMargin,
            trailingMargin = trailingMargin,
            centerAligned = alignment == HorizontalAlignment.Center,
            endAligned = alignment == HorizontalAlignment.End,
        )
    }

    /**
     * 计算垂直交叉轴位置。
     * Calculates vertical cross-axis placement.
     */
    fun calculateVertical(
        containerSize: Int,
        childSize: Int,
        leadingMargin: Int,
        trailingMargin: Int,
        alignment: VerticalAlignment,
    ): Int {
        return calculate(
            containerSize = containerSize,
            childSize = childSize,
            leadingMargin = leadingMargin,
            trailingMargin = trailingMargin,
            centerAligned = alignment == VerticalAlignment.Center,
            endAligned = alignment == VerticalAlignment.Bottom,
        )
    }

    private fun calculate(
        containerSize: Int,
        childSize: Int,
        leadingMargin: Int,
        trailingMargin: Int,
        centerAligned: Boolean,
        endAligned: Boolean,
    ): Int {
        val consumedSize = childSize + leadingMargin + trailingMargin
        val extra = max(0, containerSize - consumedSize)
        return when {
            centerAligned -> extra / 2 + leadingMargin
            endAligned -> extra + leadingMargin
            else -> leadingMargin
        }
    }
}
