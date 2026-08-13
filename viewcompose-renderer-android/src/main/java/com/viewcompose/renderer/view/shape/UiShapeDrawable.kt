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
    private val fillPath = Path()
    private val strokePath = Path()
    private val fillFrame = RectF()
    private val strokeFrame = RectF()
    private var fillRoundRectRadius: Float? = null
    private var strokeRoundRectRadius: Float? = null
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.TRANSPARENT
    }
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private var currentFill: Brush = Brush.SolidColor(Color.TRANSPARENT)
    internal var currentShape: UiShape = shape ?: UiShape.rounded(UiDp.Zero)
        private set
    internal val currentFillColor: Int
        get() = (currentFill as? Brush.SolidColor)?.color ?: Color.TRANSPARENT

    fun setShape(shape: UiShape?) {
        val next = shape ?: UiShape.rounded(UiDp.Zero)
        if (currentShape == next) return
        currentShape = next
        rebuildPaths()
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
        if (strokePaint.strokeWidth == width && strokePaint.color == color) return
        strokePaint.strokeWidth = width
        strokePaint.color = color
        rebuildPaths()
        invalidateSelf()
    }

    override fun onBoundsChange(bounds: android.graphics.Rect) {
        rebuildPaths()
        rebuildFillPaint()
    }

    override fun draw(canvas: Canvas) {
        if (fillPath.isEmpty) rebuildPaths()
        val fillRadius = fillRoundRectRadius
        if (fillRadius != null) {
            canvas.drawRoundRect(fillFrame, fillRadius, fillRadius, fillPaint)
        } else {
            canvas.drawPath(fillPath, fillPaint)
        }
        if (strokePaint.strokeWidth > 0f && Color.alpha(strokePaint.color) > 0) {
            val strokeRadius = strokeRoundRectRadius
            if (strokeRadius != null) {
                canvas.drawRoundRect(strokeFrame, strokeRadius, strokeRadius, strokePaint)
            } else {
                canvas.drawPath(strokePath, strokePaint)
            }
        }
    }

    override fun getOutline(outline: Outline) {
        if (fillPath.isEmpty) rebuildPaths()
        val fillRadius = fillRoundRectRadius
        if (fillRadius != null) {
            outline.setRoundRect(bounds, fillRadius)
        } else if (!fillPath.isEmpty) {
            outline.setConvexPath(fillPath)
        }
    }

    override fun setAlpha(alpha: Int) {
        fillPaint.alpha = alpha
        strokePaint.alpha = alpha
        invalidateSelf()
    }

    override fun getAlpha(): Int = fillPaint.alpha

    override fun setColorFilter(colorFilter: ColorFilter?) {
        fillPaint.colorFilter = colorFilter
        strokePaint.colorFilter = colorFilter
        invalidateSelf()
    }

    @Deprecated("Deprecated in Android")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT

    private fun rebuildPaths() {
        fillPath.reset()
        strokePath.reset()
        fillRoundRectRadius = null
        strokeRoundRectRadius = null
        fillFrame.setEmpty()
        strokeFrame.setEmpty()
        if (bounds.isEmpty) return
        fillFrame.set(bounds)
        val fillCorners = currentShape.resolveCorners(layoutDirection, density, fillFrame)
        fillRoundRectRadius = fillCorners.uniformRoundedRadiusOrNull()
        rebuildPath(fillPath, fillFrame, fillCorners)

        if (strokePaint.strokeWidth <= 0f) return
        val maximumInset = min(fillFrame.width(), fillFrame.height()) / 2f
        val strokeInset = (strokePaint.strokeWidth / 2f).coerceAtMost(maximumInset)
        strokeFrame.set(fillFrame)
        strokeFrame.inset(strokeInset, strokeInset)
        if (strokeFrame.isEmpty) return
        val maximumStrokeCorner = min(strokeFrame.width(), strokeFrame.height()) / 2f
        val strokeCorners = fillCorners.inset(strokeInset, maximumStrokeCorner)
        strokeRoundRectRadius = strokeCorners.uniformRoundedRadiusOrNull()
        rebuildPath(
            target = strokePath,
            frame = strokeFrame,
            corners = strokeCorners,
        )
    }

    private fun rebuildPath(
        target: Path,
        frame: RectF,
        corners: ResolvedCorners,
    ) {
        target.moveTo(frame.left + corners.topLeft.size, frame.top)
        appendTopRight(target, frame, corners.topRight)
        appendBottomRight(target, frame, corners.bottomRight)
        appendBottomLeft(target, frame, corners.bottomLeft)
        appendTopLeft(target, frame, corners.topLeft)
        target.close()
    }

    private fun appendTopRight(target: Path, frame: RectF, corner: ResolvedCorner) {
        target.lineTo(frame.right - corner.size, frame.top)
        when (corner.family) {
            UiCornerFamily.Rounded -> target.appendArc(
                frame = RectF(
                    frame.right - corner.size * 2f,
                    frame.top,
                    frame.right,
                    frame.top + corner.size * 2f,
                ),
                startAngle = -90f,
            )
            UiCornerFamily.Continuous -> target.cubicTo(
                frame.right - corner.size + corner.size * ContinuousControl,
                frame.top,
                frame.right,
                frame.top + corner.size * (1f - ContinuousControl),
                frame.right,
                frame.top + corner.size,
            )
            UiCornerFamily.Cut -> target.lineTo(frame.right, frame.top + corner.size)
        }
    }

    private fun appendBottomRight(target: Path, frame: RectF, corner: ResolvedCorner) {
        target.lineTo(frame.right, frame.bottom - corner.size)
        when (corner.family) {
            UiCornerFamily.Rounded -> target.appendArc(
                frame = RectF(
                    frame.right - corner.size * 2f,
                    frame.bottom - corner.size * 2f,
                    frame.right,
                    frame.bottom,
                ),
                startAngle = 0f,
            )
            UiCornerFamily.Continuous -> target.cubicTo(
                frame.right,
                frame.bottom - corner.size + corner.size * ContinuousControl,
                frame.right - corner.size * (1f - ContinuousControl),
                frame.bottom,
                frame.right - corner.size,
                frame.bottom,
            )
            UiCornerFamily.Cut -> target.lineTo(frame.right - corner.size, frame.bottom)
        }
    }

    private fun appendBottomLeft(target: Path, frame: RectF, corner: ResolvedCorner) {
        target.lineTo(frame.left + corner.size, frame.bottom)
        when (corner.family) {
            UiCornerFamily.Rounded -> target.appendArc(
                frame = RectF(
                    frame.left,
                    frame.bottom - corner.size * 2f,
                    frame.left + corner.size * 2f,
                    frame.bottom,
                ),
                startAngle = 90f,
            )
            UiCornerFamily.Continuous -> target.cubicTo(
                frame.left + corner.size - corner.size * ContinuousControl,
                frame.bottom,
                frame.left,
                frame.bottom - corner.size * (1f - ContinuousControl),
                frame.left,
                frame.bottom - corner.size,
            )
            UiCornerFamily.Cut -> target.lineTo(frame.left, frame.bottom - corner.size)
        }
    }

    private fun appendTopLeft(target: Path, frame: RectF, corner: ResolvedCorner) {
        target.lineTo(frame.left, frame.top + corner.size)
        when (corner.family) {
            UiCornerFamily.Rounded -> target.appendArc(
                frame = RectF(
                    frame.left,
                    frame.top,
                    frame.left + corner.size * 2f,
                    frame.top + corner.size * 2f,
                ),
                startAngle = 180f,
            )
            UiCornerFamily.Continuous -> target.cubicTo(
                frame.left,
                frame.top + corner.size - corner.size * ContinuousControl,
                frame.left + corner.size * (1f - ContinuousControl),
                frame.top,
                frame.left + corner.size,
                frame.top,
            )
            UiCornerFamily.Cut -> target.lineTo(frame.left + corner.size, frame.top)
        }
    }

    private fun Path.appendArc(frame: RectF, startAngle: Float) {
        if (frame.width() <= 0f || frame.height() <= 0f) return
        arcTo(frame, startAngle, 90f, false)
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

    private companion object {
        const val ContinuousControl = 0.78f
    }
}

internal data class ResolvedCorner(val family: UiCornerFamily, val size: Float)

internal data class ResolvedCorners(
    val topLeft: ResolvedCorner,
    val topRight: ResolvedCorner,
    val bottomRight: ResolvedCorner,
    val bottomLeft: ResolvedCorner,
)

private fun ResolvedCorners.uniformRoundedRadiusOrNull(): Float? {
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
): ResolvedCorners {
    val rtl = layoutDirection == View.LAYOUT_DIRECTION_RTL
    val physical = ResolvedCorners(
        topLeft = (if (rtl) topEnd else topStart).resolve(density, bounds),
        topRight = (if (rtl) topStart else topEnd).resolve(density, bounds),
        bottomRight = (if (rtl) bottomStart else bottomEnd).resolve(density, bounds),
        bottomLeft = (if (rtl) bottomEnd else bottomStart).resolve(density, bounds),
    )
    val maxCorner = min(bounds.width(), bounds.height()) / 2f
    return physical.copy(
        topLeft = physical.topLeft.coerce(maxCorner),
        topRight = physical.topRight.coerce(maxCorner),
        bottomRight = physical.bottomRight.coerce(maxCorner),
        bottomLeft = physical.bottomLeft.coerce(maxCorner),
    )
}

private fun UiCorner.resolve(density: UiDensity, bounds: RectF): ResolvedCorner {
    val size = when (val value = size) {
        is UiCornerSize.Absolute -> density.toPx(value.size)
        is UiCornerSize.Relative -> min(bounds.width(), bounds.height()) * value.fraction
    }
    return ResolvedCorner(family, size.coerceAtLeast(0f))
}

private fun ResolvedCorner.coerce(maximum: Float): ResolvedCorner = copy(size = size.coerceAtMost(maximum))
