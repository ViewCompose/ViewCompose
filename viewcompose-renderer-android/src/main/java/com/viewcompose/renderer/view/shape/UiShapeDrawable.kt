package com.viewcompose.renderer.view.shape

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.LinearGradient
import android.graphics.Outline
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.SweepGradient
import android.graphics.drawable.Drawable
import android.view.View
import com.viewcompose.graphics.core.Brush
import com.viewcompose.ui.shape.UiCorner
import com.viewcompose.ui.shape.UiCornerFamily
import com.viewcompose.ui.shape.UiCornerSize
import com.viewcompose.ui.shape.UiShape
import com.viewcompose.ui.unit.UiDensity
import com.viewcompose.ui.unit.UiDp
import kotlin.math.min

/** Engine-owned Drawable for framework logical rounded and cut-corner shapes. */
internal class UiShapeDrawable(
    shape: UiShape?,
    private val layoutDirection: Int,
    private val density: UiDensity,
) : Drawable() {
    private var fillPath: Path? = null
    private var strokePath: Path? = null
    private var fillRoundRectRadius: Float? = null
    private var strokeRoundRectRadius: Float? = null
    private var strokeInset = 0f
    private var strokeColor = Color.TRANSPARENT
    private var geometryDirty = true
    private var drawableAlpha = 255
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.TRANSPARENT
    }
    private var strokePaint: Paint? = null
    private var currentFill: Brush = Brush.SolidColor(Color.TRANSPARENT)
    internal var currentShape: UiShape = shape ?: UiShape.rounded(UiDp.Zero)
        private set
    internal val currentFillColor: Int
        get() = (currentFill as? Brush.SolidColor)?.color ?: Color.TRANSPARENT
    internal val hasFillPathResource: Boolean
        get() = fillPath != null
    internal val hasStrokeResources: Boolean
        get() = strokePaint != null || strokePath != null

    fun setShape(shape: UiShape?) {
        val next = shape ?: UiShape.rounded(UiDp.Zero)
        if (currentShape == next) return
        currentShape = next
        geometryDirty = true
        invalidateSelf()
    }

    fun setFillColor(color: Int) {
        setFill(Brush.SolidColor(color))
    }

    fun setFill(fill: Brush) {
        if (currentFill == fill) return
        currentFill = fill
        rebuildFillPaint()
        invalidateSelf()
    }

    fun setStroke(width: Float, color: Int) {
        val existing = strokePaint
        val visible = width > 0f && width.isFinite() && Color.alpha(color) > 0
        if (!visible) {
            if (existing == null) return
            strokePaint = null
            strokePath = null
            strokeRoundRectRadius = null
            strokeInset = 0f
            geometryDirty = true
            invalidateSelf()
            return
        }
        if (existing?.strokeWidth == width && strokeColor == color) return
        val paint = existing ?: Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            colorFilter = fillPaint.colorFilter
        }
        paint.strokeWidth = width
        paint.color = color
        strokeColor = color
        applyStrokeAlpha(paint)
        strokePaint = paint
        geometryDirty = true
        invalidateSelf()
    }

    override fun onBoundsChange(bounds: android.graphics.Rect) {
        geometryDirty = true
        rebuildFillPaint()
    }

    override fun draw(canvas: Canvas) {
        ensureGeometry()
        if (bounds.isEmpty) return
        val fillRadius = fillRoundRectRadius
        if (fillRadius != null) {
            canvas.drawRoundRect(
                bounds.left.toFloat(),
                bounds.top.toFloat(),
                bounds.right.toFloat(),
                bounds.bottom.toFloat(),
                fillRadius,
                fillRadius,
                fillPaint,
            )
        } else {
            fillPath?.let { canvas.drawPath(it, fillPaint) }
        }
        strokePaint?.let { paint ->
            val strokeRadius = strokeRoundRectRadius
            if (strokeRadius != null) {
                canvas.drawRoundRect(
                    bounds.left + strokeInset,
                    bounds.top + strokeInset,
                    bounds.right - strokeInset,
                    bounds.bottom - strokeInset,
                    strokeRadius,
                    strokeRadius,
                    paint,
                )
            } else {
                strokePath?.let { canvas.drawPath(it, paint) }
            }
        }
    }

    override fun getOutline(outline: Outline) {
        ensureGeometry()
        val fillRadius = fillRoundRectRadius
        if (fillRadius != null) {
            outline.setRoundRect(bounds, fillRadius)
        } else {
            fillPath?.takeUnless(Path::isEmpty)?.let(outline::setConvexPath)
        }
    }

    override fun setAlpha(alpha: Int) {
        val next = alpha.coerceIn(0, 255)
        if (drawableAlpha == next) return
        drawableAlpha = next
        applyFillAlpha()
        strokePaint?.let(::applyStrokeAlpha)
        invalidateSelf()
    }
    override fun getAlpha(): Int = drawableAlpha

    override fun setColorFilter(colorFilter: ColorFilter?) {
        fillPaint.colorFilter = colorFilter
        strokePaint?.colorFilter = colorFilter
        invalidateSelf()
    }

    @Deprecated("Deprecated in Android")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT

    private fun ensureGeometry() {
        if (!geometryDirty) return
        geometryDirty = false
        fillRoundRectRadius = null
        strokeRoundRectRadius = null
        strokeInset = 0f
        if (bounds.isEmpty) {
            fillPath = null
            strokePath = null
            return
        }
        val fillCorners = currentShape.resolveCorners(
            layoutDirection = layoutDirection,
            density = density,
            width = bounds.width().toFloat(),
            height = bounds.height().toFloat(),
        )
        fillRoundRectRadius = fillCorners.uniformRoundedRadiusOrNull()
        fillPath = if (fillRoundRectRadius == null) {
            (fillPath ?: Path()).also { path ->
                rebuildShapePath(
                    target = path,
                    left = bounds.left.toFloat(),
                    top = bounds.top.toFloat(),
                    right = bounds.right.toFloat(),
                    bottom = bounds.bottom.toFloat(),
                    corners = fillCorners,
                )
            }
        } else {
            null
        }

        val paint = strokePaint ?: run {
            strokePath = null
            return
        }
        val maximumInset = min(bounds.width(), bounds.height()) / 2f
        strokeInset = (paint.strokeWidth / 2f).coerceAtMost(maximumInset)
        val strokeWidth = bounds.width() - strokeInset * 2f
        val strokeHeight = bounds.height() - strokeInset * 2f
        if (strokeWidth <= 0f || strokeHeight <= 0f) {
            strokePath = null
            return
        }
        val maximumStrokeCorner = min(strokeWidth, strokeHeight) / 2f
        val strokeCorners = fillCorners.inset(strokeInset, maximumStrokeCorner)
        strokeRoundRectRadius = strokeCorners.uniformRoundedRadiusOrNull()
        strokePath = if (strokeRoundRectRadius == null) {
            (strokePath ?: Path()).also { path ->
                rebuildShapePath(
                    target = path,
                    left = bounds.left + strokeInset,
                    top = bounds.top + strokeInset,
                    right = bounds.right - strokeInset,
                    bottom = bounds.bottom - strokeInset,
                    corners = strokeCorners,
                )
            }
        } else {
            null
        }
    }

    private fun rebuildFillPaint() {
        fillPaint.shader = null
        when (val fill = currentFill) {
            is Brush.SolidColor -> fillPaint.color = fill.color
            is Brush.LinearGradient -> {
                fillPaint.color = Color.WHITE
                fillPaint.shader = fill.colorStops.toShaderOrNull { colors, positions ->
                    LinearGradient(
                        bounds.left + fill.from.x,
                        bounds.top + fill.from.y,
                        bounds.left + fill.to.x,
                        bounds.top + fill.to.y,
                        colors,
                        positions,
                        Shader.TileMode.CLAMP,
                    )
                }
                if (fillPaint.shader == null) {
                    fillPaint.color = fill.colorStops.lastOrNull()?.color ?: Color.TRANSPARENT
                }
            }
            is Brush.RadialGradient -> {
                fillPaint.color = Color.WHITE
                if (fill.radius > 0f && fill.radius.isFinite()) {
                    fillPaint.shader = fill.colorStops.toShaderOrNull { colors, positions ->
                        RadialGradient(
                            bounds.left + fill.center.x,
                            bounds.top + fill.center.y,
                            fill.radius,
                            colors,
                            positions,
                            Shader.TileMode.CLAMP,
                        )
                    }
                }
                if (fillPaint.shader == null) {
                    fillPaint.color = fill.colorStops.lastOrNull()?.color ?: Color.TRANSPARENT
                }
            }
            is Brush.SweepGradient -> {
                fillPaint.color = Color.WHITE
                fillPaint.shader = fill.colorStops.toShaderOrNull { colors, positions ->
                    SweepGradient(
                        bounds.left + fill.center.x,
                        bounds.top + fill.center.y,
                        colors,
                        positions,
                    )
                }
                if (fillPaint.shader == null) {
                    fillPaint.color = fill.colorStops.lastOrNull()?.color ?: Color.TRANSPARENT
                }
            }
        }
        applyFillAlpha()
    }

    private fun applyFillAlpha() {
        fillPaint.alpha = when (val fill = currentFill) {
            is Brush.SolidColor -> combineAlpha(Color.alpha(fill.color), drawableAlpha)
            else -> drawableAlpha
        }
    }

    private fun applyStrokeAlpha(paint: Paint) {
        paint.alpha = combineAlpha(Color.alpha(strokeColor), drawableAlpha)
    }

    private inline fun List<com.viewcompose.graphics.core.ColorStop>.toShaderOrNull(
        create: (IntArray, FloatArray) -> Shader,
    ): Shader? {
        if (size < 2) return null
        val colors = IntArray(size)
        val positions = FloatArray(size)
        forEachIndexed { index, stop ->
            colors[index] = stop.color
            positions[index] = stop.offset
        }
        return create(colors, positions)
    }
}

private fun combineAlpha(colorAlpha: Int, drawableAlpha: Int): Int {
    return (colorAlpha * drawableAlpha + 127) / 255
}

internal data class ResolvedCorner(val family: UiCornerFamily, val size: Float)

internal data class ResolvedCorners(
    val topLeft: ResolvedCorner,
    val topRight: ResolvedCorner,
    val bottomRight: ResolvedCorner,
    val bottomLeft: ResolvedCorner,
)

internal fun ResolvedCorners.uniformRoundedRadiusOrNull(): Float? {
    val radius = topLeft.size
    return radius.takeIf {
        topLeft.family == UiCornerFamily.Rounded &&
            topRight.family == UiCornerFamily.Rounded &&
            bottomRight.family == UiCornerFamily.Rounded &&
            bottomLeft.family == UiCornerFamily.Rounded &&
            topRight.size == radius &&
            bottomRight.size == radius &&
            bottomLeft.size == radius
    }
}

internal fun rebuildShapePath(
    target: Path,
    left: Float,
    top: Float,
    right: Float,
    bottom: Float,
    corners: ResolvedCorners,
) {
    target.reset()
    target.moveTo(left + corners.topLeft.size, top)
    appendTopRight(target, right, top, corners.topRight)
    appendBottomRight(target, right, bottom, corners.bottomRight)
    appendBottomLeft(target, left, bottom, corners.bottomLeft)
    appendTopLeft(target, left, top, corners.topLeft)
    target.close()
}

private fun appendTopRight(
    target: Path,
    right: Float,
    top: Float,
    corner: ResolvedCorner,
) {
    val size = corner.size
    target.lineTo(right - size, top)
    when (corner.family) {
        UiCornerFamily.Rounded -> target.appendQuarterArc(
            left = right - size * 2f,
            top = top,
            right = right,
            bottom = top + size * 2f,
            startAngle = -90f,
        )
        UiCornerFamily.Continuous -> target.cubicTo(
            right - size + size * CONTINUOUS_CONTROL,
            top,
            right,
            top + size * (1f - CONTINUOUS_CONTROL),
            right,
            top + size,
        )
        UiCornerFamily.Cut -> target.lineTo(right, top + size)
    }
}

private fun appendBottomRight(
    target: Path,
    right: Float,
    bottom: Float,
    corner: ResolvedCorner,
) {
    val size = corner.size
    target.lineTo(right, bottom - size)
    when (corner.family) {
        UiCornerFamily.Rounded -> target.appendQuarterArc(
            left = right - size * 2f,
            top = bottom - size * 2f,
            right = right,
            bottom = bottom,
            startAngle = 0f,
        )
        UiCornerFamily.Continuous -> target.cubicTo(
            right,
            bottom - size + size * CONTINUOUS_CONTROL,
            right - size * (1f - CONTINUOUS_CONTROL),
            bottom,
            right - size,
            bottom,
        )
        UiCornerFamily.Cut -> target.lineTo(right - size, bottom)
    }
}

private fun appendBottomLeft(
    target: Path,
    left: Float,
    bottom: Float,
    corner: ResolvedCorner,
) {
    val size = corner.size
    target.lineTo(left + size, bottom)
    when (corner.family) {
        UiCornerFamily.Rounded -> target.appendQuarterArc(
            left = left,
            top = bottom - size * 2f,
            right = left + size * 2f,
            bottom = bottom,
            startAngle = 90f,
        )
        UiCornerFamily.Continuous -> target.cubicTo(
            left + size - size * CONTINUOUS_CONTROL,
            bottom,
            left,
            bottom - size * (1f - CONTINUOUS_CONTROL),
            left,
            bottom - size,
        )
        UiCornerFamily.Cut -> target.lineTo(left, bottom - size)
    }
}

private fun appendTopLeft(
    target: Path,
    left: Float,
    top: Float,
    corner: ResolvedCorner,
) {
    val size = corner.size
    target.lineTo(left, top + size)
    when (corner.family) {
        UiCornerFamily.Rounded -> target.appendQuarterArc(
            left = left,
            top = top,
            right = left + size * 2f,
            bottom = top + size * 2f,
            startAngle = 180f,
        )
        UiCornerFamily.Continuous -> target.cubicTo(
            left,
            top + size - size * CONTINUOUS_CONTROL,
            left + size * (1f - CONTINUOUS_CONTROL),
            top,
            left + size,
            top,
        )
        UiCornerFamily.Cut -> target.lineTo(left + size, top)
    }
}

private fun Path.appendQuarterArc(
    left: Float,
    top: Float,
    right: Float,
    bottom: Float,
    startAngle: Float,
) {
    if (right <= left || bottom <= top) return
    arcTo(left, top, right, bottom, startAngle, 90f, false)
}

private fun ResolvedCorners.inset(amount: Float, maximum: Float): ResolvedCorners {
    fun ResolvedCorner.insetCorner(): ResolvedCorner {
        return copy(size = (size - amount).coerceIn(0f, maximum))
    }
    return ResolvedCorners(
        topLeft = topLeft.insetCorner(),
        topRight = topRight.insetCorner(),
        bottomRight = bottomRight.insetCorner(),
        bottomLeft = bottomLeft.insetCorner(),
    )
}

internal fun UiShape.resolveCorners(
    layoutDirection: Int,
    density: UiDensity,
    bounds: RectF,
): ResolvedCorners = resolveCorners(
    layoutDirection = layoutDirection,
    density = density,
    width = bounds.width(),
    height = bounds.height(),
)

internal fun UiShape.resolveCorners(
    layoutDirection: Int,
    density: UiDensity,
    width: Float,
    height: Float,
): ResolvedCorners {
    val rtl = layoutDirection == View.LAYOUT_DIRECTION_RTL
    val physical = ResolvedCorners(
        topLeft = (if (rtl) topEnd else topStart).resolve(density, width, height),
        topRight = (if (rtl) topStart else topEnd).resolve(density, width, height),
        bottomRight = (if (rtl) bottomStart else bottomEnd).resolve(density, width, height),
        bottomLeft = (if (rtl) bottomEnd else bottomStart).resolve(density, width, height),
    )
    val maxCorner = min(width, height) / 2f
    return physical.copy(
        topLeft = physical.topLeft.coerce(maxCorner),
        topRight = physical.topRight.coerce(maxCorner),
        bottomRight = physical.bottomRight.coerce(maxCorner),
        bottomLeft = physical.bottomLeft.coerce(maxCorner),
    )
}

private fun UiCorner.resolve(
    density: UiDensity,
    width: Float,
    height: Float,
): ResolvedCorner {
    val size = when (val value = size) {
        is UiCornerSize.Absolute -> density.toPx(value.size)
        is UiCornerSize.Relative -> min(width, height) * value.fraction
    }
    return ResolvedCorner(family, size.coerceAtLeast(0f))
}

private fun ResolvedCorner.coerce(maximum: Float): ResolvedCorner = copy(size = size.coerceAtMost(maximum))

private const val CONTINUOUS_CONTROL = 0.78f
