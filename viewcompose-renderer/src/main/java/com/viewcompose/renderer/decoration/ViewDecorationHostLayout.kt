package com.viewcompose.renderer.decoration

import android.content.Context
import android.graphics.Canvas
import android.util.AttributeSet
import android.view.View
import android.widget.FrameLayout

/**
 * Generic RenderSession/collection host. It does not depend on or load a concrete decoration backend.
 */
open class ViewDecorationHostLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : FrameLayout(context, attrs) {
    private val decorationDrawing = ViewDecorationDrawing(this)

    init {
        clipChildren = false
        clipToPadding = false
    }

    override fun onViewAdded(child: View) {
        super.onViewAdded(child)
        DecorationChildDrawingOrder.onViewAdded(this, child)
        decorationDrawing.onViewAdded(child)
    }

    override fun onViewRemoved(child: View) {
        decorationDrawing.onViewRemoved(child)
        super.onViewRemoved(child)
        DecorationChildDrawingOrder.onViewRemoved(this, child)
    }

    override fun getChildDrawingOrder(childCount: Int, drawingPosition: Int): Int {
        return DecorationChildDrawingOrder.getChildDrawingOrder(this, childCount, drawingPosition)
    }

    internal fun setDecorationDrawingOrderEnabled(enabled: Boolean) {
        isChildrenDrawingOrderEnabled = enabled
    }

    override fun drawChild(canvas: Canvas, child: View, drawingTime: Long): Boolean {
        if (!decorationDrawing.hasDecoratedChildren) {
            return super.drawChild(canvas, child, drawingTime)
        }
        decorationDrawing.drawBehindChild(canvas, child)
        val drawn = super.drawChild(canvas, child, drawingTime)
        decorationDrawing.drawOverChild(canvas, child)
        return drawn
    }
}
