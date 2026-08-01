package com.viewcompose.renderer.view.container

import android.content.Context
import android.graphics.Canvas
import android.util.AttributeSet
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import com.viewcompose.renderer.view.tree.LayoutPassTracker
import com.viewcompose.renderer.decoration.DecorationChildDrawingOrder
import com.viewcompose.renderer.decoration.DecorationDrawingOrderContainer
import com.viewcompose.renderer.decoration.ViewDecorationDrawing

/**
 * Box/Surface 使用的 FrameLayout 容器。
 * FrameLayout container used by Box/Surface.
 *
 * 它在 child 未设置 gravity 时注入 contentGravity，并保留阴影/溢出绘制。
 * It injects contentGravity for children without gravity and keeps shadows/overflow drawing visible.
 */
internal class DeclarativeBoxLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : FrameLayout(context, attrs), DecorationDrawingOrderContainer {
    private val decorationDrawing = ViewDecorationDrawing(this)

    companion object {
        const val UNSET_GRAVITY: Int = -1
    }

    var contentGravity: Int = Gravity.TOP or Gravity.START
        set(value) {
            if (field == value) return
            field = value
            requestLayout()
        }

    init {
        // 保留阴影和溢出绘制，接近 Compose 容器默认行为。
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
        applyGravityToChild(child)
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
        decorationDrawing.drawBehindChild(canvas, child)
        val drawn = super.drawChild(canvas, child, drawingTime)
        decorationDrawing.drawOverChild(canvas, child)
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
        applyGravityToChildren()
        super.onLayout(changed, left, top, right, bottom)
        LayoutPassTracker.recordLayoutSince(javaClass, startNs)
    }

    private fun applyGravityToChildren() {
        (0 until childCount).forEach { index ->
            applyGravityToChild(getChildAt(index))
        }
    }

    private fun applyGravityToChild(
        child: View,
    ) {
        val params = child.layoutParams as? LayoutParams ?: return
        if (params.gravity == UNSET_GRAVITY) {
            params.gravity = contentGravity
            child.layoutParams = params
        }
    }
}
