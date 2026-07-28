package com.viewcompose.renderer.layout

import com.viewcompose.ui.layout.MainAxisArrangement
import kotlin.math.max

/**
 * 线性布局 child 在主轴上的测量输入。
 * Measured child input on a linear layout main axis.
 */
internal data class LinearChildSpec(
    val size: Int,
    val leadingMargin: Int,
    val trailingMargin: Int,
)

/**
 * 线性布局 child 在主轴上的放置结果。
 * Placement result for a child on a linear layout main axis.
 */
internal data class LinearChildPlacement(
    val leading: Int,
    val trailing: Int,
)

/**
 * 根据 child 尺寸、margin 和主轴排列计算放置坐标。
 * Calculates placement coordinates from child size, margins, and main-axis arrangement.
 */
internal object LinearPlacementCalculator {
    /**
     * 返回每个 child 的 leading/trailing 坐标。
     * Returns leading/trailing coordinates for every child.
     */
    fun calculate(
        containerSize: Int,
        arrangement: MainAxisArrangement,
        itemSpacing: Int,
        hasWeightedChildren: Boolean,
        children: List<LinearChildSpec>,
    ): List<LinearChildPlacement> {
        if (children.isEmpty()) {
            return emptyList()
        }
        val consumedSize = children.sumOf { child ->
            child.size + child.leadingMargin + child.trailingMargin
        }
        val baseSpacing = if (children.size > 1) itemSpacing * (children.size - 1) else 0
        val extraSpace = max(0, containerSize - consumedSize - baseSpacing)
        val arrangementMetrics = LinearArrangementCalculator.calculate(
            arrangement = arrangement,
            itemSpacing = itemSpacing,
            extraSpace = extraSpace,
            childCount = children.size,
            hasWeightedChildren = hasWeightedChildren,
        )

        var currentLeading = arrangementMetrics.leadingSpace
        return buildList {
            children.forEachIndexed { index, child ->
                currentLeading += child.leadingMargin
                val trailing = currentLeading + child.size
                add(
                    LinearChildPlacement(
                        leading = currentLeading,
                        trailing = trailing,
                    ),
                )
                currentLeading = trailing + child.trailingMargin
                if (index != children.lastIndex) {
                    currentLeading += arrangementMetrics.gap
                }
            }
        }
    }
}
