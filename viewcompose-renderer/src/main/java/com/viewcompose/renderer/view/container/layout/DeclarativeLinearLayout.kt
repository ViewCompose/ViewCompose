package com.viewcompose.renderer.view.container

import android.content.Context
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.View
import android.widget.LinearLayout
import com.viewcompose.ui.layout.MainAxisArrangement
import com.viewcompose.renderer.layout.CrossAxisPlacementCalculator
import com.viewcompose.renderer.layout.LinearArrangementCalculator
import com.viewcompose.renderer.layout.LinearCrossAxisAlignmentResolver
import com.viewcompose.renderer.view.tree.LayoutPassTracker
import com.viewcompose.shadow.android.ShadowDecorationLayer
import kotlin.math.max

/**
 * Row/Column 使用的线性容器。
 * Linear container used by Row/Column.
 *
 * 在原生 LinearLayout 测量基础上重写 layout，支持声明式 arrangement、spacing 和交叉轴对齐。
 * It keeps native LinearLayout measurement and overrides layout to support declarative arrangement, spacing, and cross-axis alignment.
 */
internal class DeclarativeLinearLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : LinearLayout(context, attrs) {
    companion object {
        private const val UNSPECIFIED_CHILD_GRAVITY: Int = -1
    }

    var itemSpacing: Int = 0
        set(value) {
            if (field == value) return
            field = value
            updateSpacingDivider()
            requestLayout()
        }

    var mainAxisArrangement: MainAxisArrangement = MainAxisArrangement.Start
        set(value) {
            if (field == value) return
            field = value
            requestLayout()
        }

    init {
        clipChildren = false
        clipToPadding = false
    }

    override fun onMeasure(
        widthMeasureSpec: Int,
        heightMeasureSpec: Int,
    ) {
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
        val visibleChildCount = countVisibleChildren()
        if (visibleChildCount == 0) {
            LayoutPassTracker.recordLayoutSince(javaClass, startNs)
            return
        }
        if (orientation == HORIZONTAL) {
            layoutHorizontally(visibleChildCount)
        } else {
            layoutVertically(visibleChildCount)
        }
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
        return super.drawChild(canvas, child, drawingTime)
    }

    private fun layoutHorizontally(
        visibleChildCount: Int,
    ) {
        val innerWidth = width - paddingLeft - paddingRight
        val innerHeight = height - paddingTop - paddingBottom
        var hasWeightedChildren = false
        var consumedSize = 0
        for (index in 0 until childCount) {
            val child = getChildAt(index)
            if (child.visibility == View.GONE) continue
            val params = child.layoutParams as MarginLayoutParams
            if (((child.layoutParams as? LayoutParams)?.weight ?: 0f) > 0f) {
                hasWeightedChildren = true
            }
            consumedSize += child.measuredWidth + params.leftMargin + params.rightMargin
        }
        val baseSpacing = itemSpacing * (visibleChildCount - 1)
        val metrics = LinearArrangementCalculator.calculate(
            arrangement = mainAxisArrangement,
            itemSpacing = itemSpacing,
            extraSpace = max(0, innerWidth - consumedSize - baseSpacing),
            childCount = visibleChildCount,
            hasWeightedChildren = hasWeightedChildren,
        )
        var currentLeading = metrics.leadingSpace
        var visibleIndex = 0
        for (index in 0 until childCount) {
            val child = getChildAt(index)
            if (child.visibility == View.GONE) continue
            val params = child.layoutParams as MarginLayoutParams
            val childTop = paddingTop + resolveVerticalGravity(
                child = child,
                params = params,
                innerHeight = innerHeight,
            )
            currentLeading += params.leftMargin
            val childLeft = paddingLeft + currentLeading
            val childRight = childLeft + child.measuredWidth
            val childBottom = childTop + child.measuredHeight
            child.layout(childLeft, childTop, childRight, childBottom)
            currentLeading += child.measuredWidth + params.rightMargin
            visibleIndex += 1
            if (visibleIndex < visibleChildCount) {
                currentLeading += metrics.gap
            }
        }
    }

    private fun layoutVertically(
        visibleChildCount: Int,
    ) {
        val innerWidth = width - paddingLeft - paddingRight
        val innerHeight = height - paddingTop - paddingBottom
        var hasWeightedChildren = false
        var consumedSize = 0
        for (index in 0 until childCount) {
            val child = getChildAt(index)
            if (child.visibility == View.GONE) continue
            val params = child.layoutParams as MarginLayoutParams
            if (((child.layoutParams as? LayoutParams)?.weight ?: 0f) > 0f) {
                hasWeightedChildren = true
            }
            consumedSize += child.measuredHeight + params.topMargin + params.bottomMargin
        }
        val baseSpacing = itemSpacing * (visibleChildCount - 1)
        val metrics = LinearArrangementCalculator.calculate(
            arrangement = mainAxisArrangement,
            itemSpacing = itemSpacing,
            extraSpace = max(0, innerHeight - consumedSize - baseSpacing),
            childCount = visibleChildCount,
            hasWeightedChildren = hasWeightedChildren,
        )
        var currentLeading = metrics.leadingSpace
        var visibleIndex = 0
        for (index in 0 until childCount) {
            val child = getChildAt(index)
            if (child.visibility == View.GONE) continue
            val params = child.layoutParams as MarginLayoutParams
            val childLeft = paddingLeft + resolveHorizontalGravity(
                child = child,
                params = params,
                innerWidth = innerWidth,
            )
            val childRight = childLeft + child.measuredWidth
            currentLeading += params.topMargin
            val childTop = paddingTop + currentLeading
            val childBottom = childTop + child.measuredHeight
            child.layout(childLeft, childTop, childRight, childBottom)
            currentLeading += child.measuredHeight + params.bottomMargin
            visibleIndex += 1
            if (visibleIndex < visibleChildCount) {
                currentLeading += metrics.gap
            }
        }
    }

    private fun resolveVerticalGravity(
        child: View,
        params: MarginLayoutParams,
        innerHeight: Int,
    ): Int {
        return CrossAxisPlacementCalculator.calculateVertical(
            containerSize = innerHeight,
            childSize = child.measuredHeight,
            leadingMargin = params.topMargin,
            trailingMargin = params.bottomMargin,
            alignment = LinearCrossAxisAlignmentResolver.resolveVertical(
                containerGravity = gravity,
                childGravity = readChildGravity(params),
            ),
        )
    }

    private fun resolveHorizontalGravity(
        child: View,
        params: MarginLayoutParams,
        innerWidth: Int,
    ): Int {
        return CrossAxisPlacementCalculator.calculateHorizontal(
            containerSize = innerWidth,
            childSize = child.measuredWidth,
            leadingMargin = params.leftMargin,
            trailingMargin = params.rightMargin,
            alignment = LinearCrossAxisAlignmentResolver.resolveHorizontal(
                containerGravity = gravity,
                childGravity = readChildGravity(params),
            ),
        )
    }

    private fun readChildGravity(params: MarginLayoutParams): Int? {
        val gravity = (params as? LayoutParams)?.gravity ?: UNSPECIFIED_CHILD_GRAVITY
        return gravity.takeUnless { it == UNSPECIFIED_CHILD_GRAVITY }
    }

    private fun countVisibleChildren(): Int {
        var result = 0
        for (index in 0 until childCount) {
            if (getChildAt(index).visibility != View.GONE) {
                result += 1
            }
        }
        return result
    }

    private fun updateSpacingDivider() {
        if (itemSpacing <= 0) {
            showDividers = SHOW_DIVIDER_NONE
            dividerDrawable = null
            return
        }
        showDividers = SHOW_DIVIDER_MIDDLE
        dividerDrawable = if (orientation == HORIZONTAL) {
            SpacingDrawable(
                width = itemSpacing,
                height = 0,
            )
        } else {
            SpacingDrawable(
                width = 0,
                height = itemSpacing,
            )
        }
    }

    private class SpacingDrawable(
        private val width: Int,
        private val height: Int,
    ) : Drawable() {
        override fun draw(canvas: Canvas) = Unit

        override fun setAlpha(alpha: Int) = Unit

        override fun setColorFilter(colorFilter: ColorFilter?) = Unit

        @Suppress("OVERRIDE_DEPRECATION")
        override fun getOpacity(): Int = PixelFormat.TRANSPARENT

        override fun getIntrinsicWidth(): Int = width

        override fun getIntrinsicHeight(): Int = height
    }
}
