package com.viewcompose.preview.catalog.domain

import com.viewcompose.graphics.Canvas
import com.viewcompose.graphics.drawBehind
import com.viewcompose.graphics.drawWithCache
import com.viewcompose.graphics.core.Brush
import com.viewcompose.graphics.core.ColorStop
import com.viewcompose.graphics.core.DrawCommand
import com.viewcompose.graphics.core.DrawPaint
import com.viewcompose.graphics.core.Offset
import com.viewcompose.graphics.core.Radius
import com.viewcompose.graphics.core.Rect
import com.viewcompose.graphics.core.RoundRect
import com.viewcompose.graphics.core.TextStyle
import com.viewcompose.preview.catalog.model.PreviewDomain
import com.viewcompose.preview.catalog.model.PreviewSpec
import com.viewcompose.ui.modifier.Modifier
import com.viewcompose.ui.modifier.fillMaxWidth
import com.viewcompose.ui.modifier.height
import com.viewcompose.ui.modifier.padding
import com.viewcompose.widget.core.Text
import com.viewcompose.widget.core.dp

/**
 * 提供图形绘制预览规格，验证 Canvas、drawBehind 和 drawWithCache 在预览宿主中的渲染链路。
 * Provides graphics preview specs that validate Canvas, drawBehind, and drawWithCache rendering inside the preview host.
 */
internal object GraphicsPreviewSpecs {
    val all: List<PreviewSpec> = listOf(
        PreviewSpec(
            id = "graphics-canvas-draw-pipeline",
            title = "Canvas + Draw Modifiers",
            domain = PreviewDomain.Graphics,
            content = {
                Text(text = "Canvas + drawWithCache preview")
                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp)
                        .drawBehind {
                            drawRoundRect(
                                roundRect = RoundRect(
                                    rect = Rect(0f, 0f, 1000f, 1000f),
                                    topLeft = Radius(24f, 24f),
                                    topRight = Radius(24f, 24f),
                                    bottomRight = Radius(24f, 24f),
                                    bottomLeft = Radius(24f, 24f),
                                ),
                                paint = DrawPaint(
                                    brush = Brush.SolidColor(0xFFE2E8F0.toInt()),
                                ),
                            )
                        }
                        .drawWithCache(key = "preview-graphics-cache") { context ->
                            cache(key = "preview-graphics-cache") {
                                listOf(
                                    DrawCommand.DrawRect(
                                        rect = Rect(20f, 24f, context.size.width - 20f, context.size.height - 24f),
                                        paint = DrawPaint(
                                            brush = Brush.LinearGradient(
                                                from = Offset(0f, 0f),
                                                to = Offset(context.size.width, context.size.height),
                                                colorStops = listOf(
                                                    ColorStop(0f, 0xFF93C5FD.toInt()),
                                                    ColorStop(1f, 0xFF34D399.toInt()),
                                                ),
                                            ),
                                        ),
                                    ),
                                )
                            }
                        }
                        .padding(8.dp),
                ) {
                    drawText(
                        text = "Preview Graphics",
                        origin = Offset(30f, 118f),
                        style = TextStyle(textSizePx = 30f, isBold = true),
                        paint = DrawPaint(brush = Brush.SolidColor(0xFF0F172A.toInt())),
                    )
                }
            },
        ),
    )
}
