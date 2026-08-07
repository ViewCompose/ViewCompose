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
    private val fillPath = Path()
    private val strokePath = Path()
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
        rebuildPaths()
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
        rebuildPaths()
        invalidateSelf()
    }

    override fun onBoundsChange(bounds: android.graphics.Rect) {
        rebuildPaths()
    }

    override fun draw(canvas: Canvas) {
        if (fillPath.isEmpty) rebuildPaths()
        canvas.drawPath(fillPath, fillPaint)
        if (strokePaint.strokeWidth > 0f && Color.alpha(strokePaint.color) > 0) {
            canvas.drawPath(strokePath, strokePaint)
        }
    }

    override fun getOutline(outline: Outline) {
        if (fillPath.isEmpty) rebuildPaths()
        if (!fillPath.isEmpty) outline.setConvexPath(fillPath)
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
        if (bounds.isEmpty) return
        val fillFrame = RectF(bounds)
        val fillCorners = currentShape.resolveCorners(layoutDirection, density, fillFrame)
        rebuildPath(fillPath, fillFrame, fillCorners)

        if (strokePaint.strokeWidth <= 0f) return
        val maximumInset = min(fillFrame.width(), fillFrame.height()) / 2f
        val strokeInset = (strokePaint.strokeWidth / 2f).coerceAtMost(maximumInset)
        val strokeFrame = RectF(fillFrame).apply {
            inset(strokeInset, strokeInset)
        }
        if (strokeFrame.isEmpty) return
        val maximumStrokeCorner = min(strokeFrame.width(), strokeFrame.height()) / 2f
        rebuildPath(
            target = strokePath,
            frame = strokeFrame,
            corners = fillCorners.inset(strokeInset, maximumStrokeCorner),
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
            UiCornerFamily.Cut -> target.lineTo(frame.left + corner.size, frame.top)
        }
    }

    private fun Path.appendArc(frame: RectF, startAngle: Float) {
        if (frame.width() <= 0f || frame.height() <= 0f) return
        arcTo(frame, startAngle, 90f, false)
    }
}

internal data class ResolvedCorner(val family: UiCornerFamily, val size: Float)

internal data class ResolvedCorners(
    val topLeft: ResolvedCorner,
    val topRight: ResolvedCorner,
    val bottomRight: ResolvedCorner,
    val bottomLeft: ResolvedCorner,
)

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
