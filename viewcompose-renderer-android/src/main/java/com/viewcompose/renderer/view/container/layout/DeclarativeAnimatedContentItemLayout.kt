package com.viewcompose.renderer.view.container

import android.content.Context
import android.graphics.Canvas
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.accessibility.AccessibilityEvent
import android.widget.FrameLayout
import com.viewcompose.renderer.decoration.DecorationChildDrawingOrder
import com.viewcompose.renderer.decoration.DecorationDrawingOrderContainer
import com.viewcompose.renderer.decoration.ViewDecorationDrawing
import com.viewcompose.renderer.view.tree.LayoutPassTracker

/** Android host for one renderable content item and its exclusive interaction ownership. */
internal class DeclarativeAnimatedContentItemLayout(
    context: Context,
) : FrameLayout(context), DecorationDrawingOrderContainer {
    private val decorationDrawing = ViewDecorationDrawing(this)

    var translationXFraction: Float = 0f
        set(value) {
            require(value.isFinite()) { "Animated content X translation fraction must be finite." }
            if (field == value) return
            field = value
            updateFractionalTranslation()
        }

    var translationYFraction: Float = 0f
        set(value) {
            require(value.isFinite()) { "Animated content Y translation fraction must be finite." }
            if (field == value) return
            field = value
            updateFractionalTranslation()
        }

    var revealWidthFraction: Float = 1f
        set(value) {
            require(value.isFinite()) { "Animated content width reveal fraction must be finite." }
            val normalized = value.coerceAtLeast(0f)
            if (field == normalized) return
            field = normalized
            invalidate()
        }

    var revealHeightFraction: Float = 1f
        set(value) {
            require(value.isFinite()) { "Animated content height reveal fraction must be finite." }
            val normalized = value.coerceAtLeast(0f)
            if (field == normalized) return
            field = normalized
            invalidate()
        }

    var pivotFractionX: Float = 0.5f
        set(value) {
            require(value.isFinite()) { "Animated content X pivot fraction must be finite." }
            if (field == value) return
            field = value
            updateTransformOrigin()
        }

    var pivotFractionY: Float = 0.5f
        set(value) {
            require(value.isFinite()) { "Animated content Y pivot fraction must be finite." }
            if (field == value) return
            field = value
            updateTransformOrigin()
        }

    var contentActive: Boolean = true
        set(value) {
            if (field == value) return
            field = value
            applyContentParticipation()
        }

    init {
        clipChildren = false
        clipToPadding = false
        applyContentParticipation()
    }

    override fun getChildDrawingOrder(childCount: Int, drawingPosition: Int): Int =
        DecorationChildDrawingOrder.getChildDrawingOrder(this, childCount, drawingPosition)

    override fun setDecorationDrawingOrderEnabled(enabled: Boolean) {
        isChildrenDrawingOrderEnabled = enabled
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        return contentActive && super.dispatchTouchEvent(event)
    }

    override fun dispatchHoverEvent(event: MotionEvent): Boolean {
        return contentActive && super.dispatchHoverEvent(event)
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        return contentActive && super.dispatchKeyEvent(event)
    }

    override fun requestSendAccessibilityEvent(child: View, event: AccessibilityEvent): Boolean {
        return contentActive && super.requestSendAccessibilityEvent(child, event)
    }

    override fun addFocusables(views: ArrayList<View>, direction: Int, focusableMode: Int) {
        if (contentActive) {
            super.addFocusables(views, direction, focusableMode)
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val startNs = LayoutPassTracker.beginTiming()
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        LayoutPassTracker.recordMeasureSince(javaClass, startNs)
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
        updateFractionalTranslation()
        updateTransformOrigin()
        LayoutPassTracker.recordLayoutSince(javaClass, startNs)
    }

    override fun dispatchDraw(canvas: Canvas) {
        val revealWidth = (width * revealWidthFraction).coerceAtLeast(0f)
        val revealHeight = (height * revealHeightFraction).coerceAtLeast(0f)
        val checkpoint = canvas.save()
        canvas.clipRect(0f, 0f, revealWidth, revealHeight)
        super.dispatchDraw(canvas)
        canvas.restoreToCount(checkpoint)
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

    private fun applyContentParticipation() {
        if (contentActive) {
            descendantFocusability = ViewGroup.FOCUS_AFTER_DESCENDANTS
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_AUTO
        } else {
            clearDescendantFocus(this)
            descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        }
    }

    private fun clearDescendantFocus(parent: ViewGroup) {
        (0 until parent.childCount).forEach { index ->
            val child = parent.getChildAt(index)
            if (child is ViewGroup) {
                clearDescendantFocus(child)
            }
            child.clearFocus()
        }
        parent.clearFocus()
    }

    private fun updateFractionalTranslation() {
        translationX = width * translationXFraction
        translationY = height * translationYFraction
    }

    private fun updateTransformOrigin() {
        pivotX = width * pivotFractionX
        pivotY = height * pivotFractionY
    }
}
