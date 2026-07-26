package com.viewcompose.graphics.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DrawRecorderTest {
    @Test
    fun `recorder emits expected commands`() {
        val recorder = DrawRecorder()
        val paint = DrawPaint(
            brush = Brush.SolidColor(0xFFFF0000.toInt()),
        )
        recorder.save()
        recorder.translate(4f, 8f)
        recorder.drawRect(
            rect = Rect(0f, 0f, 20f, 10f),
            paint = paint,
        )
        recorder.restore()

        val commands = recorder.toCommands()
        assertEquals(4, commands.size)
        assertEquals(DrawCommand.Save, commands[0])
        assertEquals(DrawCommand.Translate(4f, 8f), commands[1])
        assertEquals(
            DrawCommand.DrawRect(
                rect = Rect(0f, 0f, 20f, 10f),
                paint = paint,
            ),
            commands[2],
        )
        assertEquals(DrawCommand.Restore, commands[3])
    }

    @Test
    fun `group records immutable reusable scene with transform and clip`() {
        val recorder = DrawRecorder()
        val transform = Matrix3(
            floatArrayOf(
                1f, 0f, 20f,
                0f, 1f, 10f,
                0f, 0f, 1f,
            ),
        )
        recorder.group(
            transform = transform,
            clip = Rect(0f, 0f, 8f, 8f),
        ) {
            drawCircle(
                center = Offset(4f, 4f),
                radius = 4f,
            )
        }

        val command = recorder.toCommands().single() as DrawCommand.DrawScene
        assertEquals(transform, command.transform)
        assertEquals(Rect(0f, 0f, 8f, 8f), command.clip)
        assertEquals(1, command.scene.commands.size)

        recorder.clear()
        assertEquals(1, command.scene.commands.size)
    }

    @Test
    fun `scene rejects unbalanced save restore commands`() {
        val recorder = DrawRecorder()
        recorder.restore()

        val result = runCatching { recorder.toScene() }

        assertTrue(result.exceptionOrNull() is IllegalArgumentException)
    }

    @Test
    fun `balanced nested scene is reusable in multiple parents`() {
        val reusable = drawScene {
            save()
            translate(2f, 3f)
            drawRect(Rect(0f, 0f, 4f, 5f))
            restore()
        }
        val first = DrawRecorder().apply { drawScene(reusable) }.toScene()
        val second = DrawRecorder().apply { drawScene(reusable) }.toScene()

        assertEquals(first, second)
        assertTrue(
            (first.commands.single() as DrawCommand.DrawScene).scene === reusable,
        )
    }
}
