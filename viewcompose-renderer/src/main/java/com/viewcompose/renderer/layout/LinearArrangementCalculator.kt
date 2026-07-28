package com.viewcompose.renderer.layout

import com.viewcompose.ui.layout.MainAxisArrangement

/**
 * 计算线性布局主轴的起始空白和 item 间距。
 * Calculates leading space and item gap for a linear layout main axis.
 */
internal object LinearArrangementCalculator {
    /**
     * 根据剩余空间、排列方式和 weight 情况生成排列参数。
     * Builds arrangement metrics from extra space, arrangement mode, and weighted-child state.
     */
    fun calculate(
        arrangement: MainAxisArrangement,
        itemSpacing: Int,
        extraSpace: Int,
        childCount: Int,
        hasWeightedChildren: Boolean,
    ): ArrangementMetrics {
        if (childCount <= 1) {
            return ArrangementMetrics(
                leadingSpace = when (arrangement) {
                    MainAxisArrangement.Center -> extraSpace / 2
                    MainAxisArrangement.End -> extraSpace
                    MainAxisArrangement.SpaceAround,
                    MainAxisArrangement.SpaceEvenly,
                    -> extraSpace / 2
                    MainAxisArrangement.Start,
                    MainAxisArrangement.SpaceBetween,
                    -> 0
                },
                gap = itemSpacing,
            )
        }

        if (hasWeightedChildren) {
            // 有 weight 时额外空间已被权重消费，Space* 策略退化为固定 spacing。
            // With weighted children, extra space is consumed by weights, so Space* modes fall back to fixed spacing.
            return when (arrangement) {
                MainAxisArrangement.Start,
                MainAxisArrangement.SpaceBetween,
                MainAxisArrangement.SpaceAround,
                MainAxisArrangement.SpaceEvenly,
                -> ArrangementMetrics(
                    leadingSpace = 0,
                    gap = itemSpacing,
                )

                MainAxisArrangement.Center -> ArrangementMetrics(
                    leadingSpace = extraSpace / 2,
                    gap = itemSpacing,
                )

                MainAxisArrangement.End -> ArrangementMetrics(
                    leadingSpace = extraSpace,
                    gap = itemSpacing,
                )
            }
        }

        return when (arrangement) {
            MainAxisArrangement.Start -> ArrangementMetrics(
                leadingSpace = 0,
                gap = itemSpacing,
            )
            MainAxisArrangement.Center -> ArrangementMetrics(
                leadingSpace = extraSpace / 2,
                gap = itemSpacing,
            )
            MainAxisArrangement.End -> ArrangementMetrics(
                leadingSpace = extraSpace,
                gap = itemSpacing,
            )
            MainAxisArrangement.SpaceBetween -> ArrangementMetrics(
                leadingSpace = 0,
                gap = itemSpacing + extraSpace / (childCount - 1),
            )
            MainAxisArrangement.SpaceAround -> {
                val unit = extraSpace / (childCount * 2)
                ArrangementMetrics(
                    leadingSpace = unit,
                    gap = itemSpacing + unit * 2,
                )
            }
            MainAxisArrangement.SpaceEvenly -> {
                val unit = extraSpace / (childCount + 1)
                ArrangementMetrics(
                    leadingSpace = unit,
                    gap = itemSpacing + unit,
                )
            }
        }
    }
}

/**
 * 主轴排列计算结果。
 * Result of main-axis arrangement calculation.
 */
internal data class ArrangementMetrics(
    val leadingSpace: Int,
    val gap: Int,
)
