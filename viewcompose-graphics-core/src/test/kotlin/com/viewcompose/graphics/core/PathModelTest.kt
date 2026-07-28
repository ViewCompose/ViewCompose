package com.viewcompose.graphics.core

/*
 * 测试职责：覆盖 graphics core 中的 Path Model 行为，防止关键契约在后续重构中回退。
 * Test responsibility: covers Path Model behavior in graphics core and guards the contract against regressions.
 */

import org.junit.Assert.assertEquals
import org.junit.Test

class PathModelTest {
    @Test
    fun `path builder keeps command order and fill type`() {
        val built = path {
            fillType(PathFillType.EvenOdd)
            moveTo(0f, 0f)
            lineTo(10f, 10f)
            quadTo(12f, 13f, 20f, 21f)
            cubicTo(1f, 2f, 3f, 4f, 5f, 6f)
            arcTo(
                oval = Rect(0f, 0f, 10f, 10f),
                startAngleDegrees = 0f,
                sweepAngleDegrees = 90f,
            )
            close()
        }

        assertEquals(PathFillType.EvenOdd, built.fillType)
        assertEquals(6, built.commands.size)
        assertEquals(PathCommand.MoveTo(0f, 0f), built.commands[0])
        assertEquals(PathCommand.LineTo(10f, 10f), built.commands[1])
        assertEquals(PathCommand.Close, built.commands[5])
    }
}
