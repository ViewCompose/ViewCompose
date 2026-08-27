package com.viewcompose.graphics.samples

import com.viewcompose.graphics.Canvas
import com.viewcompose.graphics.core.Brush
import com.viewcompose.graphics.core.DrawCommand
import com.viewcompose.graphics.core.DrawPaint
import com.viewcompose.graphics.core.Offset
import com.viewcompose.graphics.core.Rect
import com.viewcompose.graphics.core.path
import com.viewcompose.graphics.drawBehind
import com.viewcompose.graphics.drawWithCache
import com.viewcompose.graphics.drawWithContent
import com.viewcompose.ui.modifier.fillMaxWidth
import com.viewcompose.ui.modifier.height
import com.viewcompose.ui.modifier.Modifier
import com.viewcompose.ui.foundation.UiTreeBuilder
import com.viewcompose.ui.unit.dp

fun UiTreeBuilder.canvasSample() {
// DOCS_REGION_START(graphics-canvas)
Canvas(modifier = Modifier.fillMaxWidth().height(120.dp)) { context ->
    drawCircle(
        center = Offset(context.size.width / 2f, context.size.height / 2f),
        radius = minOf(context.size.width, context.size.height) / 2f,
        paint = DrawPaint(brush = Brush.SolidColor(0xFF6750A4.toInt())),
    )
}
// DOCS_REGION_END(graphics-canvas)
}

fun drawBehindSample(): Modifier {
    return Modifier.drawBehind { context ->
        drawRect(
            rect = Rect(0f, 0f, context.size.width, context.size.height),
            paint = DrawPaint(brush = Brush.SolidColor(0x1F6750A4)),
        )
    }
}

fun drawWithContentSample(): Modifier {
    return Modifier.drawWithContent { context ->
        drawContent()
        check(context.density > 0f)
    }
}

// DOCS_REGION_START(graphics-cache)
val modifier = Modifier.drawWithCache { context ->
    cache(key = context.size) {
        val outline = path {
            moveTo(0f, 0f)
            lineTo(context.size.width, 0f)
            lineTo(context.size.width, context.size.height)
            close()
        }
        listOf(DrawCommand.DrawPath(outline))
    }
}
// DOCS_REGION_END(graphics-cache)

fun drawWithCacheSample(): Modifier = modifier
