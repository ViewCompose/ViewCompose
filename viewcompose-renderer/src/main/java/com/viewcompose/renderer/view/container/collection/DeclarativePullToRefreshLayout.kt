package com.viewcompose.renderer.view.container

import android.content.Context
import android.graphics.Canvas
import android.view.View
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.viewcompose.renderer.decoration.ViewDecorationDrawing

/**
 * 支持 child Decoration Layer 的下拉刷新宿主。
 * Pull-to-refresh host participating in the child Decoration Layer protocol.
 */
internal class DeclarativePullToRefreshLayout(
    context: Context,
) : SwipeRefreshLayout(context) {
    private var decorationDrawing: ViewDecorationDrawing? = null

    init {
        decorationDrawing = ViewDecorationDrawing(this)
        clipChildren = false
        clipToPadding = false
    }

    override fun dispatchDraw(canvas: Canvas) {
        val saveCount = canvas.save()
        canvas.clipRect(0, 0, width, height)
        super.dispatchDraw(canvas)
        canvas.restoreToCount(saveCount)
    }

    override fun drawChild(
        canvas: Canvas,
        child: View,
        drawingTime: Long,
    ): Boolean {
        val drawing = decorationDrawing
        if (drawing == null || !drawing.hasDecoratedChildren) {
            return super.drawChild(canvas, child, drawingTime)
        }
        val decoration = drawing.decorationOrNull(child)
            ?: return super.drawChild(canvas, child, drawingTime)
        drawing.drawBehindChild(canvas, child, decoration)
        val drawn = super.drawChild(canvas, child, drawingTime)
        drawing.drawOverChild(canvas, child, decoration)
        return drawn
    }

    override fun onViewAdded(child: View) {
        super.onViewAdded(child)
        decorationDrawing?.onViewAdded(child)
    }

    override fun onViewRemoved(child: View) {
        decorationDrawing?.onViewRemoved(child)
        super.onViewRemoved(child)
    }
}
