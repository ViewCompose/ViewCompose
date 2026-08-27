package com.viewcompose.graphics.core.samples

import com.viewcompose.graphics.core.Brush
import com.viewcompose.graphics.core.DrawCache
import com.viewcompose.graphics.core.DrawCommand
import com.viewcompose.graphics.core.DrawPaint
import com.viewcompose.graphics.core.Rect
import com.viewcompose.graphics.core.drawScene
import com.viewcompose.graphics.core.path

fun drawCacheSample(): List<DrawCommand> {
    val cache = DrawCache<List<DrawCommand>>()
    return cache.getOrBuild(key = "100x48-dark") {
        drawSceneSample().commands
    }
}

// DOCS_REGION_START(graphics-core-path)
val triangle = path {
    moveTo(8f, 8f)
    lineTo(56f, 8f)
    lineTo(32f, 48f)
    close()
}
// DOCS_REGION_END(graphics-core-path)

fun pathSample() = triangle

// DOCS_REGION_START(graphics-core-scene)
val badge = drawScene {
    save()
    clipRect(Rect(0f, 0f, 64f, 48f))
    drawPath(triangle, DrawPaint(brush = Brush.SolidColor(0xFF6750A4.toInt())))
    restore()
}
// DOCS_REGION_END(graphics-core-scene)

fun drawSceneSample() = badge
