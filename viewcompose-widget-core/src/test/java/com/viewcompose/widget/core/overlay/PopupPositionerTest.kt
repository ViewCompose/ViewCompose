package com.viewcompose.widget.core

import com.viewcompose.ui.environment.UiLayoutDirection
/*
 * 测试职责：覆盖 widget-core overlay 中的 Popup Positioner 行为，防止 DSL、状态或主题契约在后续重构中回退。
 * Test responsibility: covers Popup Positioner behavior in widget-core overlay and guards DSL, state, or theme contracts against regressions.
 */

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PopupPositionerTest {
    private val viewport = PopupBounds(
        left = 0,
        top = 0,
        right = 400,
        bottom = 800,
    )

    @Test
    fun `places popup against exact anchor edge`() {
        val position = PopupPositioner.calculate(
            anchorBounds = PopupBounds(100, 200, 180, 240),
            popupSize = PopupSize(120, 80),
            viewportBounds = viewport,
            alignment = PopupAlignment.BelowEnd,
            windowMargin = 8,
            offsetX = 4,
            offsetY = 6,
        )

        assertEquals(64, position.x)
        assertEquals(246, position.y)
        assertEquals(PopupAlignment.BelowEnd, position.resolvedAlignment)
        assertFalse(position.wasClamped)
    }

    @Test
    fun `resolves start and end against rtl anchor`() {
        val position = PopupPositioner.calculate(
            anchorBounds = PopupBounds(100, 200, 180, 240),
            popupSize = PopupSize(120, 80),
            viewportBounds = viewport,
            alignment = PopupAlignment.BelowStart,
            layoutDirection = UiLayoutDirection.Rtl,
        )

        assertEquals(60, position.x)
        assertEquals(240, position.y)
    }

    @Test
    fun `flips below popup above when bottom space is insufficient`() {
        val position = PopupPositioner.calculate(
            anchorBounds = PopupBounds(100, 740, 180, 780),
            popupSize = PopupSize(120, 160),
            viewportBounds = viewport,
            alignment = PopupAlignment.BelowStart,
            overflowPolicy = PopupOverflowPolicy.FlipThenClamp,
            windowMargin = 8,
        )

        assertEquals(100, position.x)
        assertEquals(580, position.y)
        assertEquals(PopupAlignment.AboveStart, position.resolvedAlignment)
        assertFalse(position.wasClamped)
    }

    @Test
    fun `flips logical end popup to start near horizontal edge`() {
        val position = PopupPositioner.calculate(
            anchorBounds = PopupBounds(350, 200, 390, 240),
            popupSize = PopupSize(100, 80),
            viewportBounds = viewport,
            alignment = PopupAlignment.EndCenter,
            overflowPolicy = PopupOverflowPolicy.FlipThenClamp,
        )

        assertEquals(250, position.x)
        assertEquals(180, position.y)
        assertEquals(PopupAlignment.StartCenter, position.resolvedAlignment)
    }

    @Test
    fun `clamps oversized candidate into inset viewport`() {
        val position = PopupPositioner.calculate(
            anchorBounds = PopupBounds(0, 20, 40, 60),
            popupSize = PopupSize(500, 900),
            viewportBounds = viewport,
            alignment = PopupAlignment.AboveEnd,
            overflowPolicy = PopupOverflowPolicy.Clamp,
            windowMargin = 8,
        )

        assertEquals(8, position.x)
        assertEquals(8, position.y)
        assertTrue(position.wasClamped)
    }

    @Test
    fun `none policy preserves out of bounds coordinates`() {
        val position = PopupPositioner.calculate(
            anchorBounds = PopupBounds(350, 760, 390, 790),
            popupSize = PopupSize(120, 100),
            viewportBounds = viewport,
            alignment = PopupAlignment.BelowEnd,
            overflowPolicy = PopupOverflowPolicy.None,
        )

        assertEquals(270, position.x)
        assertEquals(790, position.y)
        assertEquals(PopupAlignment.BelowEnd, position.resolvedAlignment)
        assertFalse(position.wasClamped)
    }
}
