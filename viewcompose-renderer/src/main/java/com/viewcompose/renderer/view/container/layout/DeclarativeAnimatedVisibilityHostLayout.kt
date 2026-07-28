package com.viewcompose.renderer.view.container

import android.content.Context
import android.util.AttributeSet
import android.widget.FrameLayout
import com.viewcompose.renderer.view.tree.LayoutPassTracker
import kotlin.math.roundToInt

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
        clipChildren = true
        clipToPadding = true
    }

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
                // 中文：可见性收缩动画需要在 EXACTLY 约束下也能回传更小尺寸，
                // 否则横向/纵向 shrink 会被父约束“钉死”，表现成仅透明度变化。
                // English: Visibility shrink should be able to report a smaller measured size even
                // under EXACT constraints; otherwise horizontal/vertical shrink gets pinned by the
                // parent spec and degrades into alpha-only animation.
                animatedSize.coerceAtMost(specSize)
            }

            else -> animatedSize
        }
    }
}
