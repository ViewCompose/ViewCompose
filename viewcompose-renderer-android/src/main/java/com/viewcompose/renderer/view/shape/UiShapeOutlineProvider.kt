package com.viewcompose.renderer.view.shape

import android.graphics.Outline
import android.graphics.Path
import android.view.View
import android.view.ViewOutlineProvider
import com.viewcompose.ui.shape.UiShape
import com.viewcompose.ui.unit.UiDensity

/** Resolves a shape outline without retaining the paints and paths required by a Drawable. */
internal class UiShapeOutlineProvider(
    private val shape: UiShape,
    private val layoutDirection: Int,
    private val density: UiDensity,
    private val topInset: Int,
    private val bottomInset: Int,
) : ViewOutlineProvider() {
    private var resolvedWidth = -1
    private var resolvedHeight = -1
    private var resolvedTop = 0
    private var resolvedBottom = 0
    private var roundRectRadius: Float? = null
    private var path: Path? = null

    internal val hasPathResource: Boolean
        get() = path != null

    override fun getOutline(view: View, outline: Outline) {
        ensureGeometry(view.width, view.height)
        if (resolvedWidth <= 0 || resolvedBottom <= resolvedTop) {
            outline.setEmpty()
            return
        }
        val radius = roundRectRadius
        if (radius != null) {
            outline.setRoundRect(0, resolvedTop, resolvedWidth, resolvedBottom, radius)
        } else {
            path?.takeUnless { it.isEmpty }?.let(outline::setConvexPath) ?: outline.setEmpty()
        }
    }

    private fun ensureGeometry(width: Int, height: Int) {
        if (resolvedWidth == width && resolvedHeight == height) return
        resolvedWidth = width
        resolvedHeight = height
        resolvedTop = topInset.coerceIn(0, height)
        resolvedBottom = (height - bottomInset).coerceIn(resolvedTop, height)
        roundRectRadius = null
        if (width <= 0 || resolvedBottom <= resolvedTop) {
            path = null
            return
        }
        val corners = shape.resolveCorners(
            layoutDirection = layoutDirection,
            density = density,
            width = width.toFloat(),
            height = (resolvedBottom - resolvedTop).toFloat(),
        )
        roundRectRadius = corners.uniformRoundedRadiusOrNull()
        path = if (roundRectRadius == null) {
            (path ?: Path()).also { target ->
                rebuildShapePath(
                    target = target,
                    left = 0f,
                    top = resolvedTop.toFloat(),
                    right = width.toFloat(),
                    bottom = resolvedBottom.toFloat(),
                    corners = corners,
                )
            }
        } else {
            null
        }
    }
}
