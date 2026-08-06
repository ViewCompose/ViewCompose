package com.viewcompose.renderer.view.shape

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Outline
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.view.View
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
    private val path = Path()
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.TRANSPARENT
    }
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    internal var currentShape: UiShape = shape ?: UiShape.rounded(UiDp.Zero)
        private set
    internal val currentFillColor: Int
        get() = fillPaint.color

    fun setShape(shape: UiShape?) {
        val next = shape ?: UiShape.rounded(UiDp.Zero)
        if (currentShape == next) return
        currentShape = next
        invalidateSelf()
    }

    fun setFillColor(color: Int) {
        if (fillPaint.color == color) return
        fillPaint.color = color
        invalidateSelf()
    }

    fun setStroke(width: Float, color: Int) {
        if (strokePaint.strokeWidth == width && strokePaint.color == color) return
        strokePaint.strokeWidth = width
        strokePaint.color = color
        invalidateSelf()
    }

    override fun onBoundsChange(bounds: android.graphics.Rect) {
        rebuildPath()
    }

    override fun draw(canvas: Canvas) {
        if (path.isEmpty) rebuildPath()
        canvas.drawPath(path, fillPaint)
        if (strokePaint.strokeWidth > 0f && Color.alpha(strokePaint.color) > 0) {
            canvas.drawPath(path, strokePaint)
        }
    }

    override fun getOutline(outline: Outline) {
        if (path.isEmpty) rebuildPath()
        if (!path.isEmpty) outline.setConvexPath(path)
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

    private fun rebuildPath() {
        path.reset()
        if (bounds.isEmpty) return
        val frame = RectF(bounds)
        val corners = currentShape.resolveCorners(layoutDirection, density, frame)
        path.moveTo(frame.left + corners.topLeft.size, frame.top)
        appendTopRight(frame, corners.topRight)
        appendBottomRight(frame, corners.bottomRight)
        appendBottomLeft(frame, corners.bottomLeft)
        appendTopLeft(frame, corners.topLeft)
        path.close()
    }

    private fun appendTopRight(frame: RectF, corner: ResolvedCorner) {
        path.lineTo(frame.right - corner.size, frame.top)
        when (corner.family) {
            UiCornerFamily.Rounded -> path.quadTo(frame.right, frame.top, frame.right, frame.top + corner.size)
            UiCornerFamily.Cut -> path.lineTo(frame.right, frame.top + corner.size)
        }
    }

    private fun appendBottomRight(frame: RectF, corner: ResolvedCorner) {
        path.lineTo(frame.right, frame.bottom - corner.size)
        when (corner.family) {
            UiCornerFamily.Rounded -> path.quadTo(frame.right, frame.bottom, frame.right - corner.size, frame.bottom)
            UiCornerFamily.Cut -> path.lineTo(frame.right - corner.size, frame.bottom)
        }
    }

    private fun appendBottomLeft(frame: RectF, corner: ResolvedCorner) {
        path.lineTo(frame.left + corner.size, frame.bottom)
        when (corner.family) {
            UiCornerFamily.Rounded -> path.quadTo(frame.left, frame.bottom, frame.left, frame.bottom - corner.size)
            UiCornerFamily.Cut -> path.lineTo(frame.left, frame.bottom - corner.size)
        }
    }

    private fun appendTopLeft(frame: RectF, corner: ResolvedCorner) {
        path.lineTo(frame.left, frame.top + corner.size)
        when (corner.family) {
            UiCornerFamily.Rounded -> path.quadTo(frame.left, frame.top, frame.left + corner.size, frame.top)
            UiCornerFamily.Cut -> path.lineTo(frame.left + corner.size, frame.top)
        }
    }
}

internal data class ResolvedCorner(val family: UiCornerFamily, val size: Float)

internal data class ResolvedCorners(
    val topLeft: ResolvedCorner,
    val topRight: ResolvedCorner,
    val bottomRight: ResolvedCorner,
    val bottomLeft: ResolvedCorner,
)

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
