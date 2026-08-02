package com.viewcompose.renderer.decoration

import android.content.Context
import android.graphics.Canvas
import android.util.AttributeSet
import android.view.View
import android.widget.FrameLayout

/**
 * Hosts renderer children that may use custom drawing planes or declarative sibling depth.
 *
 * The layout stays independent from any concrete decoration backend. Its no-decoration path adds
 * one branch per child and delegates directly to [FrameLayout.drawChild]; backend callbacks and
 * child lookups are performed only while at least one direct child is decorated. Children are not
 * wrapped in additional Views.
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

    /** Registers drawing metadata after the platform has attached [child]. */
    override fun onViewAdded(child: View) {
        super.onViewAdded(child)
        DecorationChildDrawingOrder.onViewAdded(this, child)
        decorationDrawing.onViewAdded(child)
    }

    /** Releases cached drawing metadata when the platform removes [child]. */
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
        val decoration = decorationDrawing.decorationOrNull(child)
            ?: return super.drawChild(canvas, child, drawingTime)
        decorationDrawing.drawBehindChild(canvas, child, decoration)
        val drawn = super.drawChild(canvas, child, drawingTime)
        decorationDrawing.drawOverChild(canvas, child, decoration)
        return drawn
    }
}
