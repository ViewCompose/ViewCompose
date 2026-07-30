package com.viewcompose.renderer.view.container

import android.content.Context
import android.graphics.Canvas
import android.view.View
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.viewcompose.shadow.android.ShadowDecorationLayer

/**
 * 支持 child Decoration Layer 的下拉刷新宿主。
 * Pull-to-refresh host participating in the child Decoration Layer protocol.
 */
internal class DeclarativePullToRefreshLayout(
    context: Context,
) : SwipeRefreshLayout(context) {
    init {
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
        ShadowDecorationLayer.drawBehindChild(
            canvas = canvas,
            parent = this,
            child = child,
        )
        val drawn = super.drawChild(canvas, child, drawingTime)
        ShadowDecorationLayer.drawOverChild(
            canvas = canvas,
            parent = this,
            child = child,
        )
        return drawn
    }
}
