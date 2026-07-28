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
import com.viewcompose.renderer.layout.LinearCrossAxisAlignmentResolver
import com.viewcompose.renderer.layout.LinearChildSpec
import com.viewcompose.renderer.layout.LinearPlacementCalculator
import com.viewcompose.renderer.view.tree.LayoutPassTracker

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
        val visibleChildren = collectVisibleChildren()
        if (visibleChildren.isEmpty()) {
            LayoutPassTracker.recordLayoutSince(javaClass, startNs)
            return
        }
        if (orientation == HORIZONTAL) {
            layoutHorizontally(visibleChildren)
        } else {
            layoutVertically(visibleChildren)
        }
        LayoutPassTracker.recordLayoutSince(javaClass, startNs)
    }

    private fun layoutHorizontally(
        children: List<View>,
    ) {
        val innerWidth = width - paddingLeft - paddingRight
        val innerHeight = height - paddingTop - paddingBottom
        var hasWeightedChildren = false
        val childSpecs = ArrayList<LinearChildSpec>(children.size)
        children.forEach { child ->
            val params = child.layoutParams as MarginLayoutParams
            if (((child.layoutParams as? LayoutParams)?.weight ?: 0f) > 0f) {
                hasWeightedChildren = true
            }
            childSpecs += LinearChildSpec(
                size = child.measuredWidth,
                leadingMargin = params.leftMargin,
                trailingMargin = params.rightMargin,
            )
        }
        val placements = LinearPlacementCalculator.calculate(
            containerSize = innerWidth,
            arrangement = mainAxisArrangement,
            itemSpacing = itemSpacing,
            hasWeightedChildren = hasWeightedChildren,
            children = childSpecs,
        )
        children.forEachIndexed { index, child ->
            val params = child.layoutParams as MarginLayoutParams
            val childTop = paddingTop + resolveVerticalGravity(
                child = child,
                params = params,
                innerHeight = innerHeight,
            )
            val childLeft = paddingLeft + placements[index].leading
            val childRight = paddingLeft + placements[index].trailing
            val childBottom = childTop + child.measuredHeight
            child.layout(childLeft, childTop, childRight, childBottom)
        }
    }

    private fun layoutVertically(
        children: List<View>,
    ) {
        val innerWidth = width - paddingLeft - paddingRight
        val innerHeight = height - paddingTop - paddingBottom
        var hasWeightedChildren = false
        val childSpecs = ArrayList<LinearChildSpec>(children.size)
        children.forEach { child ->
            val params = child.layoutParams as MarginLayoutParams
            if (((child.layoutParams as? LayoutParams)?.weight ?: 0f) > 0f) {
                hasWeightedChildren = true
            }
            childSpecs += LinearChildSpec(
                size = child.measuredHeight,
                leadingMargin = params.topMargin,
                trailingMargin = params.bottomMargin,
            )
        }
        val placements = LinearPlacementCalculator.calculate(
            containerSize = innerHeight,
            arrangement = mainAxisArrangement,
            itemSpacing = itemSpacing,
            hasWeightedChildren = hasWeightedChildren,
            children = childSpecs,
        )
        children.forEachIndexed { index, child ->
            val params = child.layoutParams as MarginLayoutParams
            val childLeft = paddingLeft + resolveHorizontalGravity(
                child = child,
                params = params,
                innerWidth = innerWidth,
            )
            val childRight = childLeft + child.measuredWidth
            val childTop = paddingTop + placements[index].leading
            val childBottom = paddingTop + placements[index].trailing
            child.layout(childLeft, childTop, childRight, childBottom)
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

    private fun collectVisibleChildren(): List<View> {
        if (childCount == 0) {
            return emptyList()
        }
        val result = ArrayList<View>(childCount)
        for (index in 0 until childCount) {
            val child = getChildAt(index)
            if (child.visibility != View.GONE) {
                result += child
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
