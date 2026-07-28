package com.viewcompose.renderer.layout

/*
 * 测试职责：覆盖 renderer layout 中的 Linear Placement Calculator 行为，防止渲染和 patch 契约在后续重构中回退。
 * Test responsibility: covers Linear Placement Calculator behavior in renderer layout and guards render and patch contracts against regressions.
 */

import com.viewcompose.ui.layout.MainAxisArrangement
import org.junit.Assert.assertEquals
import org.junit.Test

class LinearPlacementCalculatorTest {
    @Test
    fun `single child space evenly centers placement`() {
        val result = LinearPlacementCalculator.calculate(
            containerSize = 120,
            arrangement = MainAxisArrangement.SpaceEvenly,
            itemSpacing = 8,
            hasWeightedChildren = false,
            children = listOf(
                LinearChildSpec(
                    size = 40,
                    leadingMargin = 0,
                    trailingMargin = 0,
                ),
            ),
        )

        assertEquals(
            listOf(
                LinearChildPlacement(
                    leading = 40,
                    trailing = 80,
                ),
            ),
            result,
        )
    }

    @Test
    fun `margin participates in start placement`() {
        val result = LinearPlacementCalculator.calculate(
            containerSize = 200,
            arrangement = MainAxisArrangement.Start,
            itemSpacing = 8,
            hasWeightedChildren = false,
            children = listOf(
                LinearChildSpec(
                    size = 40,
                    leadingMargin = 10,
                    trailingMargin = 6,
                ),
                LinearChildSpec(
                    size = 20,
                    leadingMargin = 4,
                    trailingMargin = 2,
                ),
            ),
        )

        assertEquals(
            listOf(
                LinearChildPlacement(
                    leading = 10,
                    trailing = 50,
                ),
                LinearChildPlacement(
                    leading = 68,
                    trailing = 88,
                ),
            ),
            result,
        )
    }

    @Test
    fun `weighted children disable distributive placement gaps`() {
        val result = LinearPlacementCalculator.calculate(
            containerSize = 200,
            arrangement = MainAxisArrangement.SpaceEvenly,
            itemSpacing = 8,
            hasWeightedChildren = true,
            children = listOf(
                LinearChildSpec(
                    size = 60,
                    leadingMargin = 0,
                    trailingMargin = 0,
                ),
                LinearChildSpec(
                    size = 60,
                    leadingMargin = 0,
                    trailingMargin = 0,
                ),
            ),
        )

        assertEquals(
            listOf(
                LinearChildPlacement(
                    leading = 0,
                    trailing = 60,
                ),
                LinearChildPlacement(
                    leading = 68,
                    trailing = 128,
                ),
            ),
            result,
        )
    }

    @Test
    fun `space around adds leading and inner gaps`() {
        val result = LinearPlacementCalculator.calculate(
            containerSize = 180,
            arrangement = MainAxisArrangement.SpaceAround,
            itemSpacing = 4,
            hasWeightedChildren = false,
            children = listOf(
                LinearChildSpec(
                    size = 20,
                    leadingMargin = 0,
                    trailingMargin = 0,
                ),
                LinearChildSpec(
                    size = 20,
                    leadingMargin = 0,
                    trailingMargin = 0,
                ),
            ),
        )

        assertEquals(
            listOf(
                LinearChildPlacement(
                    leading = 34,
                    trailing = 54,
                ),
                LinearChildPlacement(
                    leading = 126,
                    trailing = 146,
                ),
            ),
            result,
        )
    }
}
