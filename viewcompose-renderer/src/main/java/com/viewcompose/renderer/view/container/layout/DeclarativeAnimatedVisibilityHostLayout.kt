package com.viewcompose.renderer.view.container

import android.content.Context
import android.graphics.Canvas
import android.util.AttributeSet
import android.view.View
import android.widget.FrameLayout
import com.viewcompose.renderer.view.tree.LayoutPassTracker
import com.viewcompose.shadow.android.DecorationChildDrawingOrder
import com.viewcompose.shadow.android.ShadowDecorationLayer
import kotlin.math.roundToInt

/**
 * AnimatedVisibility modifier 提升后的平台 host。
 * Platform host created from promoted AnimatedVisibility modifiers.
 */
internal class DeclarativeAnimatedVisibilityHostLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : FrameLayout(context, attrs) {
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
        isChildrenDrawingOrderEnabled = true
        clipChildren = true
        clipToPadding = true
    }

    override fun getChildDrawingOrder(childCount: Int, drawingPosition: Int): Int =
        DecorationChildDrawingOrder.getChildDrawingOrder(this, childCount, drawingPosition)

    override fun onMeasure(
        widthMeasureSpec: Int,
        heightMeasureSpec: Int,
    ) {
        val startNs = LayoutPassTracker.beginTiming()
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        val scaledWidth = (measuredWidth * widthScale).roundToInt().coerceAtLeast(0)
        val scaledHeight = (measuredHeight * heightScale).roundToInt().coerceAtLeast(0)
        setMeasuredDimension(
            resolveAnimatedDimension(scaledWidth, widthMeasureSpec),
            resolveAnimatedDimension(scaledHeight, heightMeasureSpec),
        )
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
        LayoutPassTracker.recordLayoutSince(javaClass, startNs)
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
                // 可见性收缩动画在 EXACTLY 约束下也需要回传更小尺寸，否则 shrink 会退化成仅透明度变化。
                // Visibility shrink must report a smaller size even under EXACT constraints, otherwise it degrades into alpha-only animation.
                animatedSize.coerceAtMost(specSize)
            }

            else -> animatedSize
        }
    }
}
