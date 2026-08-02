package com.viewcompose.graphics.core.samples

import com.viewcompose.graphics.core.Brush
import com.viewcompose.graphics.core.ColorStop
import com.viewcompose.graphics.core.DrawCache
import com.viewcompose.graphics.core.DrawCommand
import com.viewcompose.graphics.core.DrawPaint
import com.viewcompose.graphics.core.Offset
import com.viewcompose.graphics.core.PathFillType
import com.viewcompose.graphics.core.Rect
import com.viewcompose.graphics.core.drawScene
import com.viewcompose.graphics.core.path

fun drawCacheSample(): List<DrawCommand> {
    val cache = DrawCache<List<DrawCommand>>()
    return cache.getOrBuild(key = "100x48-dark") {
        drawSceneSample().commands
    }
}

fun pathSample() = path {
    fillType(PathFillType.EvenOdd)
    moveTo(8f, 8f)
    lineTo(56f, 8f)
    lineTo(32f, 48f)
    close()
}

fun drawSceneSample() = drawScene {
    val gradient = Brush.LinearGradient(
        from = Offset(0f, 0f),
        to = Offset(64f, 48f),
        colorStops = listOf(
            ColorStop(0f, 0xFF6750A4.toInt()),
            ColorStop(1f, 0xFFD0BCFF.toInt()),
        ),
    )
    save()
    clipRect(Rect(0f, 0f, 64f, 48f))
    drawPath(pathSample(), DrawPaint(brush = gradient))
    restore()
}
