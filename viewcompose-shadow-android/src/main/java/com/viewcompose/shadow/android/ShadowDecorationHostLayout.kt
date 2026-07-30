package com.viewcompose.shadow.android

import android.content.Context
import android.graphics.Canvas
import android.util.AttributeSet
import android.view.View
import android.widget.FrameLayout

/**
 * RenderSession 根部使用的可嵌套 Decoration Host。
 * Nestable decoration host used at a RenderSession root.
 *
 * 每个 session 只需要一个该宿主；节点阴影不会创建额外 View。
 * Each session needs only one host; individual node shadows create no additional Views.
 */
class ShadowDecorationHostLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : FrameLayout(context, attrs) {
    init {
        clipChildren = false
        clipToPadding = false
        isChildrenDrawingOrderEnabled = true
    }

    override fun onViewAdded(child: View) {
        super.onViewAdded(child)
        DecorationChildDrawingOrder.invalidate(this)
    }

    override fun onViewRemoved(child: View) {
        super.onViewRemoved(child)
        DecorationChildDrawingOrder.invalidate(this)
    }

    override fun getChildDrawingOrder(
        childCount: Int,
        drawingPosition: Int,
    ): Int {
        return DecorationChildDrawingOrder.getChildDrawingOrder(
            parent = this,
            childCount = childCount,
            drawingPosition = drawingPosition,
        )
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
