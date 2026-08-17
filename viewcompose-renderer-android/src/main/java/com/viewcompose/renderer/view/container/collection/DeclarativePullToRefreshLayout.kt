package com.viewcompose.renderer.view.container

import android.content.Context
import android.graphics.Canvas
import android.view.View
import android.view.ViewGroup
import android.widget.ScrollView
import androidx.core.widget.NestedScrollView
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.viewcompose.renderer.decoration.ViewDecorationDrawing

/**
 * Pull-to-refresh host that supports child decoration drawing planes.
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

    override fun canChildScrollUp(): Boolean {
        // SwipeRefreshLayout inserts its progress indicator alongside the declarative content, so
        // the first platform child is not guaranteed to be the rendered scrollable hierarchy.
        val scrollable = (0 until childCount).firstNotNullOfOrNull { index ->
            getChildAt(index).findVerticalScrollableDescendant()
        }
        return scrollable?.canScrollVertically(-1) ?: super.canChildScrollUp()
    }

    private fun View.findVerticalScrollableDescendant(): View? {
        if (
            this is RecyclerView ||
            this is NestedScrollView ||
            this is ScrollView ||
            canScrollVertically(-1) ||
            canScrollVertically(1)
        ) {
            return this
        }
        if (this is ViewGroup) {
            for (index in 0 until childCount) {
                getChildAt(index).findVerticalScrollableDescendant()?.let { return it }
            }
        }
        return null
    }
}
