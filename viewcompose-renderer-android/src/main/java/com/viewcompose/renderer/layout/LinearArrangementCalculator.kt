package com.viewcompose.renderer.layout

import com.viewcompose.ui.layout.MainAxisArrangement

/**
 * Calculates leading free space and item gaps along a linear layout's main axis.
 * Calculates leading space and item gap for a linear layout main axis.
 */
internal object LinearArrangementCalculator {
    /**
     * Resolves arrangement parameters from remaining space, policy, and weight usage.
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
            // Weighted children consume extra space, so Space* policies fall back to fixed spacing.
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
 * Result of main-axis arrangement calculation.
 * Result of main-axis arrangement calculation.
 */
internal data class ArrangementMetrics(
    val leadingSpace: Int,
    val gap: Int,
)
