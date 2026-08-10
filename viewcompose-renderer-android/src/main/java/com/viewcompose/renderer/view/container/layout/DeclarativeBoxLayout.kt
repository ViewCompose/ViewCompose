package com.viewcompose.renderer.view.container

import android.content.Context
import android.graphics.Canvas
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import com.viewcompose.renderer.view.tree.LayoutPassTracker
import com.viewcompose.renderer.decoration.DecorationChildDrawingOrder
import com.viewcompose.renderer.decoration.DecorationDrawingOrderContainer
import com.viewcompose.renderer.decoration.ViewDecorationDrawing

/**
 * FrameLayout container used by Box and Surface.
 * FrameLayout container used by Box/Surface.
 *
 * Applies contentGravity when a child has no gravity and preserves shadow and overflow drawing.
 * It injects contentGravity for children without gravity and keeps shadows/overflow drawing visible.
 */
internal class DeclarativeBoxLayout(
    context: Context,
) : FrameLayout(context), DecorationDrawingOrderContainer {
    private val decorationDrawing = ViewDecorationDrawing(this)

    companion object {
        const val UNSET_GRAVITY: Int = -1
    }

    internal class LayoutParams(
        width: Int,
        height: Int,
        val inheritsContentGravity: Boolean,
    ) : FrameLayout.LayoutParams(width, height)

    var contentGravity: Int = Gravity.TOP or Gravity.START
        set(value) {
            if (field == value) return
            field = value
            updateInheritedChildGravity(value)
            requestLayout()
        }

    init {
        // Preserve shadows and overflow drawing to match declarative container behavior.
        // Keep elevation shadows and overflow visuals visible, similar to Compose container defaults.
        clipChildren = false
        clipToPadding = false
    }

    override fun getChildDrawingOrder(childCount: Int, drawingPosition: Int): Int =
        DecorationChildDrawingOrder.getChildDrawingOrder(this, childCount, drawingPosition)

    override fun setDecorationDrawingOrderEnabled(enabled: Boolean) {
        isChildrenDrawingOrderEnabled = enabled
    }

    override fun onMeasure(
        widthMeasureSpec: Int,
        heightMeasureSpec: Int,
    ) {
        val startNs = LayoutPassTracker.beginTiming()
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        LayoutPassTracker.recordMeasureSince(javaClass, startNs)
    }

    override fun onViewAdded(child: View) {
        super.onViewAdded(child)
        DecorationChildDrawingOrder.onViewAdded(this, child)
        decorationDrawing.onViewAdded(child)
        applyContentGravityOnAttach(child)
    }

    override fun onViewRemoved(child: View) {
        decorationDrawing.onViewRemoved(child)
        super.onViewRemoved(child)
        DecorationChildDrawingOrder.onViewRemoved(this, child)
    }

    override fun drawChild(
        canvas: Canvas,
        child: View,
        drawingTime: Long,
    ): Boolean {
        if (!decorationDrawing.hasDecoratedChildren) {
            return super.drawChild(canvas, child, drawingTime)
        }
        val decoration = decorationDrawing.decorationOrNull(child)
            ?: return super.drawChild(canvas, child, drawingTime)
        decorationDrawing.drawBehindChild(canvas, child, decoration)
        val drawn = super.drawChild(canvas, child, drawingTime)
        decorationDrawing.drawOverChild(canvas, child, decoration)
        return drawn
    }

    override fun onLayout(
        changed: Boolean,
        left: Int,
        top: Int,
        right: Int,
        bottom: Int,
    ) {
        val startNs = LayoutPassTracker.beginTiming()
        super.onLayout(changed, left, top, right, bottom)
        LayoutPassTracker.recordLayoutSince(javaClass, startNs)
    }

    private fun updateInheritedChildGravity(gravity: Int) {
        (0 until childCount).forEach { index ->
            val params = getChildAt(index).layoutParams as? LayoutParams ?: return@forEach
            if (params.inheritsContentGravity) {
                params.gravity = gravity
            }
        }
    }

    private fun applyContentGravityOnAttach(
        child: View,
    ) {
        val params = child.layoutParams as? FrameLayout.LayoutParams ?: return
        if (
            (params as? LayoutParams)?.inheritsContentGravity == true ||
            params.gravity == UNSET_GRAVITY
        ) {
            params.gravity = contentGravity
        }
    }
}
