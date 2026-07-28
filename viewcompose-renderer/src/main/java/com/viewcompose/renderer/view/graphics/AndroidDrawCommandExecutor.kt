package com.viewcompose.renderer.view.graphics

import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.SweepGradient
import android.graphics.drawable.Drawable
import android.os.Build
import com.viewcompose.graphics.core.BlendMode
import com.viewcompose.graphics.core.Brush
import com.viewcompose.graphics.core.ColorFilterModel
import com.viewcompose.graphics.core.DrawCommand
import com.viewcompose.graphics.core.DrawPaint
import com.viewcompose.graphics.core.DrawStyle
import com.viewcompose.graphics.core.ImageFilterModel
import com.viewcompose.graphics.core.PathCommand
import com.viewcompose.graphics.core.PathFillType
import com.viewcompose.graphics.core.PathModel
import com.viewcompose.graphics.core.RoundRect
import com.viewcompose.graphics.core.StrokeCap
import com.viewcompose.graphics.core.StrokeJoin
import com.viewcompose.graphics.core.TextStyle
import kotlin.math.max
import kotlin.math.sqrt

/**
 * 在 Android Canvas 上执行框架 draw command。
 * Executes framework draw commands on an Android Canvas.
 */
internal object AndroidDrawCommandExecutor {
    /**
     * 按顺序执行一组绘制命令。
     * Executes a list of draw commands in order.
     */
    fun execute(
        canvas: Canvas,
        commands: List<DrawCommand>,
    ) {
        for (command in commands) {
            when (command) {
                DrawCommand.Save -> canvas.save()
                DrawCommand.Restore -> canvas.restore()
                is DrawCommand.SaveLayer -> {
                    val bounds = command.bounds?.let {
                        RectF(it.left, it.top, it.right, it.bottom)
                    }
                    canvas.saveLayer(
                        bounds,
                        command.paint.toAndroidPaint(),
                    )
                }
                is DrawCommand.Translate -> canvas.translate(command.dx, command.dy)
                is DrawCommand.Scale -> canvas.scale(
                    command.sx,
                    command.sy,
                    command.pivot.x,
                    command.pivot.y,
                )
                is DrawCommand.Rotate -> canvas.rotate(
                    command.degrees,
                    command.pivot.x,
                    command.pivot.y,
                )
                is DrawCommand.Skew -> canvas.skew(command.kx, command.ky)
                is DrawCommand.Concat -> {
                    canvas.concat(
                        Matrix().apply {
                            setValues(command.matrix.values)
                        },
                    )
                }
                is DrawCommand.ClipRect -> canvas.clipRect(
                    command.rect.left,
                    command.rect.top,
                    command.rect.right,
                    command.rect.bottom,
                )
                is DrawCommand.ClipPath -> canvas.clipPath(command.path.toAndroidPath())
                is DrawCommand.DrawScene -> drawScene(
                    canvas = canvas,
                    command = command,
                )
                is DrawCommand.DrawLine -> canvas.drawLine(
                    command.from.x,
                    command.from.y,
                    command.to.x,
                    command.to.y,
                    command.paint.toAndroidPaint(),
                )
                is DrawCommand.DrawRect -> canvas.drawRect(
                    command.rect.left,
                    command.rect.top,
                    command.rect.right,
                    command.rect.bottom,
                    command.paint.toAndroidPaint(),
                )
                is DrawCommand.DrawRoundRect -> {
                    val rect = RectF(
                        command.roundRect.rect.left,
                        command.roundRect.rect.top,
                        command.roundRect.rect.right,
                        command.roundRect.rect.bottom,
                    )
                    val paint = command.paint.toAndroidPaint()
                    val radii = roundRectRadii(command.roundRect)
                    if (radii.isUniformCornerRadii()) {
                        canvas.drawRoundRect(rect, radii[0], radii[1], paint)
                    } else {
                        val path = Path().apply {
                            addRoundRect(rect, radii, Path.Direction.CW)
                        }
                        canvas.drawPath(path, paint)
                    }
                }
                is DrawCommand.DrawCircle -> canvas.drawCircle(
                    command.center.x,
                    command.center.y,
                    command.radius,
                    command.paint.toAndroidPaint(),
                )
                is DrawCommand.DrawOval -> canvas.drawOval(
                    RectF(
                        command.rect.left,
                        command.rect.top,
                        command.rect.right,
                        command.rect.bottom,
                    ),
                    command.paint.toAndroidPaint(),
                )
                is DrawCommand.DrawArc -> canvas.drawArc(
                    RectF(
                        command.oval.left,
                        command.oval.top,
                        command.oval.right,
                        command.oval.bottom,
                    ),
                    command.startAngleDegrees,
                    command.sweepAngleDegrees,
                    command.useCenter,
                    command.paint.toAndroidPaint(),
                )
                is DrawCommand.DrawPath -> canvas.drawPath(
                    command.path.toAndroidPath(),
                    command.paint.toAndroidPaint(),
                )
                is DrawCommand.DrawImage -> drawImage(
                    canvas = canvas,
                    command = command,
                )
                is DrawCommand.DrawText -> drawText(
                    canvas = canvas,
                    command = command,
                )
            }
        }
    }

    private fun drawScene(
        canvas: Canvas,
        command: DrawCommand.DrawScene,
    ) {
        val checkpoint = canvas.save()
        try {
            canvas.concat(
                Matrix().apply {
                    setValues(command.transform.values)
                },
            )
            command.clip?.let { clip ->
                canvas.clipRect(
                    clip.left,
                    clip.top,
                    clip.right,
                    clip.bottom,
                )
            }
            execute(
                canvas = canvas,
                commands = command.scene.commands,
            )
        } finally {
            canvas.restoreToCount(checkpoint)
        }
    }

    private fun drawImage(
        canvas: Canvas,
        command: DrawCommand.DrawImage,
    ) {
        val paint = command.paint.toAndroidPaint()
        val srcRect = command.src?.let {
            android.graphics.Rect(
                it.left.toInt(),
                it.top.toInt(),
                it.right.toInt(),
                it.bottom.toInt(),
            )
        }
        val dstRect = command.dst?.let {
            android.graphics.Rect(
                it.left.toInt(),
                it.top.toInt(),
                it.right.toInt(),
                it.bottom.toInt(),
            )
        }
        when (val source = command.image.stableId) {
            is Bitmap -> {
                if (srcRect != null || dstRect != null) {
                    canvas.drawBitmap(
                        source,
                        srcRect,
                        dstRect ?: android.graphics.Rect(
                            0,
                            0,
                            source.width,
                            source.height,
                        ),
                        paint,
                    )
                } else {
                    canvas.drawBitmap(source, 0f, 0f, paint)
                }
            }
            is Drawable -> {
                val prevBounds = android.graphics.Rect(source.bounds)
                if (dstRect != null) {
                    source.bounds = dstRect
                } else {
                    source.setBounds(0, 0, command.image.width, command.image.height)
                }
                val layerBounds = RectF(source.bounds)
                val checkpoint = canvas.saveLayer(
                    layerBounds,
                    command.paint.toDrawableCompositePaint(),
                )
                source.draw(canvas)
                canvas.restoreToCount(checkpoint)
                source.bounds = prevBounds
            }
        }
    }

    private fun drawText(
        canvas: Canvas,
        command: DrawCommand.DrawText,
    ) {
        val paint = command.paint.toAndroidPaint().applyTextStyle(command.style)
        canvas.drawText(
            command.text,
            command.origin.x,
            command.origin.y,
            paint,
        )
    }

    private fun DrawPaint.toAndroidPaint(): Paint {
        return Paint().apply {
            isAntiAlias = antiAlias
            alpha = (this@toAndroidPaint.alpha.coerceIn(0f, 1f) * 255).toInt()
            shader = brush.toShader()
            color = (brush as? Brush.SolidColor)?.color ?: Color.TRANSPARENT
            style = when (val drawStyle = this@toAndroidPaint.style) {
                DrawStyle.Fill -> Paint.Style.FILL
                is DrawStyle.Stroke -> {
                    strokeWidth = drawStyle.width
                    strokeCap = when (drawStyle.cap) {
                        StrokeCap.Butt -> Paint.Cap.BUTT
                        StrokeCap.Round -> Paint.Cap.ROUND
                        StrokeCap.Square -> Paint.Cap.SQUARE
                    }
                    strokeJoin = when (drawStyle.join) {
                        StrokeJoin.Miter -> Paint.Join.MITER
                        StrokeJoin.Round -> Paint.Join.ROUND
                        StrokeJoin.Bevel -> Paint.Join.BEVEL
                    }
                    strokeMiter = drawStyle.miterLimit
                    Paint.Style.STROKE
                }
            }
            setBlendModeCompat(this@toAndroidPaint.blendMode)
            colorFilter = this@toAndroidPaint.colorFilter.toAndroidColorFilter()
            applyImageFilter(this@toAndroidPaint.imageFilter)
        }
    }

    private fun DrawPaint.toDrawableCompositePaint(): Paint {
        return Paint().apply {
            isAntiAlias = antiAlias
            alpha = (this@toDrawableCompositePaint.alpha.coerceIn(0f, 1f) * 255).toInt()
            setBlendModeCompat(this@toDrawableCompositePaint.blendMode)
            colorFilter = this@toDrawableCompositePaint.colorFilter.toAndroidColorFilter()
            applyImageFilter(this@toDrawableCompositePaint.imageFilter)
        }
    }

    private fun Paint.applyImageFilter(filter: ImageFilterModel?) {
        val blurRadii = combinedBlurRadii(filter)
        maskFilter = if (blurRadii == null) {
            null
        } else {
            BlurMaskFilter(max(blurRadii.first, blurRadii.second), BlurMaskFilter.Blur.NORMAL)
        }
    }

    private fun Paint.setBlendModeCompat(mode: BlendMode) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            blendMode = when (mode) {
                BlendMode.SrcOver -> android.graphics.BlendMode.SRC_OVER
                BlendMode.SrcIn -> android.graphics.BlendMode.SRC_IN
                BlendMode.SrcOut -> android.graphics.BlendMode.SRC_OUT
                BlendMode.SrcAtop -> android.graphics.BlendMode.SRC_ATOP
                BlendMode.DstOver -> android.graphics.BlendMode.DST_OVER
                BlendMode.DstIn -> android.graphics.BlendMode.DST_IN
                BlendMode.DstOut -> android.graphics.BlendMode.DST_OUT
                BlendMode.DstAtop -> android.graphics.BlendMode.DST_ATOP
                BlendMode.Multiply -> android.graphics.BlendMode.MULTIPLY
                BlendMode.Screen -> android.graphics.BlendMode.SCREEN
                BlendMode.Overlay -> android.graphics.BlendMode.OVERLAY
                BlendMode.Darken -> android.graphics.BlendMode.DARKEN
                BlendMode.Lighten -> android.graphics.BlendMode.LIGHTEN
                BlendMode.Plus -> android.graphics.BlendMode.PLUS
            }
        } else {
            @Suppress("DEPRECATION")
            xfermode = PorterDuffXfermode(
                when (mode) {
                    BlendMode.SrcOver -> PorterDuff.Mode.SRC_OVER
                    BlendMode.SrcIn -> PorterDuff.Mode.SRC_IN
                    BlendMode.SrcOut -> PorterDuff.Mode.SRC_OUT
                    BlendMode.SrcAtop -> PorterDuff.Mode.SRC_ATOP
                    BlendMode.DstOver -> PorterDuff.Mode.DST_OVER
                    BlendMode.DstIn -> PorterDuff.Mode.DST_IN
                    BlendMode.DstOut -> PorterDuff.Mode.DST_OUT
                    BlendMode.DstAtop -> PorterDuff.Mode.DST_ATOP
                    BlendMode.Multiply -> PorterDuff.Mode.MULTIPLY
                    BlendMode.Screen -> PorterDuff.Mode.SCREEN
                    BlendMode.Overlay -> PorterDuff.Mode.OVERLAY
                    BlendMode.Darken -> PorterDuff.Mode.DARKEN
                    BlendMode.Lighten -> PorterDuff.Mode.LIGHTEN
                    BlendMode.Plus -> PorterDuff.Mode.ADD
                },
            )
        }
    }

    private fun ColorFilterModel?.toAndroidColorFilter(): android.graphics.ColorFilter? {
        return when (this) {
            is ColorFilterModel.Tint -> PorterDuffColorFilter(
                color,
                when (blendMode) {
                    BlendMode.SrcOver -> PorterDuff.Mode.SRC_OVER
                    BlendMode.SrcIn -> PorterDuff.Mode.SRC_IN
                    BlendMode.SrcOut -> PorterDuff.Mode.SRC_OUT
                    BlendMode.SrcAtop -> PorterDuff.Mode.SRC_ATOP
                    BlendMode.DstOver -> PorterDuff.Mode.DST_OVER
                    BlendMode.DstIn -> PorterDuff.Mode.DST_IN
                    BlendMode.DstOut -> PorterDuff.Mode.DST_OUT
                    BlendMode.DstAtop -> PorterDuff.Mode.DST_ATOP
                    BlendMode.Multiply -> PorterDuff.Mode.MULTIPLY
                    BlendMode.Screen -> PorterDuff.Mode.SCREEN
                    BlendMode.Overlay -> PorterDuff.Mode.OVERLAY
                    BlendMode.Darken -> PorterDuff.Mode.DARKEN
                    BlendMode.Lighten -> PorterDuff.Mode.LIGHTEN
                    BlendMode.Plus -> PorterDuff.Mode.ADD
                },
            )
            is ColorFilterModel.ColorMatrix -> ColorMatrixColorFilter(ColorMatrix(values))
            null -> null
        }
    }

    private fun Brush.toShader(): Shader? {
        return when (this) {
            is Brush.SolidColor -> null
            is Brush.LinearGradient -> LinearGradient(
                from.x,
                from.y,
                to.x,
                to.y,
                colorStops.map { it.color }.toIntArray(),
                colorStops.map { it.offset }.toFloatArray(),
                Shader.TileMode.CLAMP,
            )
            is Brush.RadialGradient -> RadialGradient(
                center.x,
                center.y,
                radius,
                colorStops.map { it.color }.toIntArray(),
                colorStops.map { it.offset }.toFloatArray(),
                Shader.TileMode.CLAMP,
            )
            is Brush.SweepGradient -> SweepGradient(
                center.x,
                center.y,
                colorStops.map { it.color }.toIntArray(),
                colorStops.map { it.offset }.toFloatArray(),
            )
        }
    }

    private fun PathModel.toAndroidPath(): Path {
        return Path().apply {
            fillType = when (this@toAndroidPath.fillType) {
                PathFillType.NonZero -> Path.FillType.WINDING
                PathFillType.EvenOdd -> Path.FillType.EVEN_ODD
            }
            for (command in this@toAndroidPath.commands) {
                when (command) {
                    is PathCommand.MoveTo -> moveTo(command.x, command.y)
                    is PathCommand.LineTo -> lineTo(command.x, command.y)
                    is PathCommand.QuadTo -> quadTo(command.x1, command.y1, command.x2, command.y2)
                    is PathCommand.CubicTo -> cubicTo(
                        command.x1,
                        command.y1,
                        command.x2,
                        command.y2,
                        command.x3,
                        command.y3,
                    )
                    is PathCommand.ArcTo -> arcTo(
                        RectF(
                            command.oval.left,
                            command.oval.top,
                            command.oval.right,
                            command.oval.bottom,
                        ),
                        command.startAngleDegrees,
                        command.sweepAngleDegrees,
                        command.forceMoveTo,
                    )
                    PathCommand.Close -> close()
                }
            }
        }
    }

    private fun Paint.applyTextStyle(style: TextStyle): Paint {
        textSize = style.textSizePx
        isFakeBoldText = style.isBold
        textSkewX = if (style.isItalic) -0.25f else 0f
        return this
    }

    internal fun roundRectRadii(roundRect: RoundRect): FloatArray {
        return floatArrayOf(
            roundRect.topLeft.x.coerceAtLeast(0f),
            roundRect.topLeft.y.coerceAtLeast(0f),
            roundRect.topRight.x.coerceAtLeast(0f),
            roundRect.topRight.y.coerceAtLeast(0f),
            roundRect.bottomRight.x.coerceAtLeast(0f),
            roundRect.bottomRight.y.coerceAtLeast(0f),
            roundRect.bottomLeft.x.coerceAtLeast(0f),
            roundRect.bottomLeft.y.coerceAtLeast(0f),
        )
    }

    internal fun combinedBlurRadii(filter: ImageFilterModel?): Pair<Float, Float>? {
        if (filter == null) return null
        var sumXSquare = 0f
        var sumYSquare = 0f
        var hasBlur = false

        fun collect(node: ImageFilterModel) {
            when (node) {
                is ImageFilterModel.Blur -> {
                    sumXSquare += node.radiusX * node.radiusX
                    sumYSquare += node.radiusY * node.radiusY
                    hasBlur = true
                }
                is ImageFilterModel.Chain -> {
                    collect(node.inner)
                    collect(node.outer)
                }
            }
        }

        collect(filter)
        if (!hasBlur) return null
        return sqrt(sumXSquare) to sqrt(sumYSquare)
    }

    private fun FloatArray.isUniformCornerRadii(): Boolean {
        if (size != 8) return false
        val x = this[0]
        val y = this[1]
        return this[2] == x &&
            this[3] == y &&
            this[4] == x &&
            this[5] == y &&
            this[6] == x &&
            this[7] == y
    }
}
