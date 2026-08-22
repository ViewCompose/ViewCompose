package com.viewcompose.renderer.view.container

import android.content.Context
import android.graphics.Canvas
import android.util.AttributeSet
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.accessibility.AccessibilityEvent
import android.widget.FrameLayout
import com.viewcompose.renderer.view.tree.LayoutPassTracker
import com.viewcompose.renderer.decoration.DecorationChildDrawingOrder
import com.viewcompose.renderer.decoration.DecorationDrawingOrderContainer
import com.viewcompose.renderer.decoration.ViewDecorationDrawing
import kotlin.math.roundToInt

/** Owns native measurement, clipping, transforms, and participation for one visibility host. */
internal class DeclarativeAnimatedVisibilityHostLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : FrameLayout(context, attrs), DecorationDrawingOrderContainer {
    private val decorationDrawing = ViewDecorationDrawing(this)
    private var fullMeasuredWidth: Int = 0
    private var fullMeasuredHeight: Int = 0

    var visualScaleX: Float = 1f
        set(value) {
            require(value.isFinite()) { "Animated visibility X scale must be finite." }
            if (field == value) return
            field = value
            scaleX = value
        }

    var visualScaleY: Float = 1f
        set(value) {
            require(value.isFinite()) { "Animated visibility Y scale must be finite." }
            if (field == value) return
            field = value
            scaleY = value
        }

    var translationXFraction: Float = 0f
        set(value) {
            require(value.isFinite()) { "Animated visibility X translation fraction must be finite." }
            if (field == value) return
            field = value
            updateFractionalTranslation()
        }

    var translationYFraction: Float = 0f
        set(value) {
            require(value.isFinite()) { "Animated visibility Y translation fraction must be finite." }
            if (field == value) return
            field = value
            updateFractionalTranslation()
        }

    var pivotFractionX: Float = 0.5f
        set(value) {
            require(value.isFinite()) { "Animated visibility X pivot fraction must be finite." }
            if (field == value) return
            field = value
            updateTransformOrigin()
        }

    var pivotFractionY: Float = 0.5f
        set(value) {
            require(value.isFinite()) { "Animated visibility Y pivot fraction must be finite." }
            if (field == value) return
            field = value
            updateTransformOrigin()
        }

    var contentGravity: Int = Gravity.TOP or Gravity.START
        set(value) {
            if (field == value) return
            field = value
            requestLayout()
        }

    var contentActive: Boolean = true
        set(value) {
            if (field == value) return
            field = value
            applyContentParticipation()
        }

    var widthScale: Float = 1f
        set(value) {
            val clamped = value.coerceAtLeast(0f)
            if (field == clamped) return
            field = clamped
            requestLayout()
        }

    var heightScale: Float = 1f
        set(value) {
            val clamped = value.coerceAtLeast(0f)
            if (field == clamped) return
            field = clamped
            requestLayout()
        }

    var clipToBounds: Boolean = true
        set(value) {
            if (field == value) return
            field = value
            clipChildren = value
            clipToPadding = value
            invalidate()
        }

    init {
        clipChildren = true
        clipToPadding = true
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

    override fun onMeasure(
        widthMeasureSpec: Int,
        heightMeasureSpec: Int,
    ) {
        val startNs = LayoutPassTracker.beginTiming()
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        fullMeasuredWidth = measuredWidth
        fullMeasuredHeight = measuredHeight
        val scaledWidth = (fullMeasuredWidth * widthScale).roundToInt().coerceAtLeast(0)
        val scaledHeight = (fullMeasuredHeight * heightScale).roundToInt().coerceAtLeast(0)
        setMeasuredDimension(
            resolveAnimatedDimension(scaledWidth, widthMeasureSpec),
            resolveAnimatedDimension(scaledHeight, heightMeasureSpec),
        )
        updateFractionalTranslation()
        updateTransformOrigin()
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
        applyContentGravity()
        super.onLayout(changed, left, top, right, bottom)
        updateFractionalTranslation()
        updateTransformOrigin()
        LayoutPassTracker.recordLayoutSince(javaClass, startNs)
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

    override fun onViewAdded(child: View) {
        super.onViewAdded(child)
        applyContentGravity(child)
        DecorationChildDrawingOrder.onViewAdded(this, child)
        decorationDrawing.onViewAdded(child)
    }

    override fun onViewRemoved(child: View) {
        decorationDrawing.onViewRemoved(child)
        super.onViewRemoved(child)
        DecorationChildDrawingOrder.onViewRemoved(this, child)
    }

    private fun resolveAnimatedDimension(
        animatedSize: Int,
        measureSpec: Int,
    ): Int {
        val specMode = MeasureSpec.getMode(measureSpec)
        val specSize = MeasureSpec.getSize(measureSpec)
        return when (specMode) {
            MeasureSpec.UNSPECIFIED -> animatedSize
            MeasureSpec.AT_MOST -> animatedSize.coerceAtMost(specSize)
            MeasureSpec.EXACTLY -> {
                // Reporting the exact parent size here would degrade measured reveal into alpha-only motion.
                animatedSize.coerceAtMost(specSize)
            }

            else -> animatedSize
        }
    }

    private fun applyContentGravity() {
        (0 until childCount).forEach { index ->
            applyContentGravity(getChildAt(index))
        }
    }

    private fun applyContentGravity(child: View) {
        val params = child.layoutParams as? LayoutParams ?: return
        if (params.gravity != contentGravity) {
            params.gravity = contentGravity
        }
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
        translationX = fullMeasuredWidth * translationXFraction
        translationY = fullMeasuredHeight * translationYFraction
    }

    private fun updateTransformOrigin() {
        pivotX = fullMeasuredWidth * pivotFractionX
        pivotY = fullMeasuredHeight * pivotFractionY
    }
}
