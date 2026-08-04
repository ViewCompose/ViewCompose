package com.viewcompose.renderer.view.container

import android.content.Context
import android.graphics.Canvas
import android.util.AttributeSet
import android.view.View
import android.widget.LinearLayout
import com.viewcompose.ui.layout.MainAxisArrangement
import com.viewcompose.renderer.layout.CrossAxisPlacementCalculator
import com.viewcompose.renderer.layout.LinearArrangementCalculator
import com.viewcompose.renderer.layout.LinearCrossAxisAlignmentResolver
import com.viewcompose.renderer.view.tree.LayoutPassTracker
import com.viewcompose.renderer.decoration.DecorationChildDrawingOrder
import com.viewcompose.renderer.decoration.DecorationDrawingOrderContainer
import com.viewcompose.renderer.decoration.ViewDecorationDrawing
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Linear container used by Row and Column.
 * Linear container used by Row/Column.
 *
 * Reuses LinearLayout measurement while overriding placement for declarative arrangement, spacing, and cross-axis alignment.
 * It keeps native LinearLayout measurement and overrides layout to support declarative arrangement, spacing, and cross-axis alignment.
 */
internal class DeclarativeLinearLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : LinearLayout(context, attrs), DecorationDrawingOrderContainer {
    private val decorationDrawing = ViewDecorationDrawing(this)

    companion object {
        private const val UNSPECIFIED_CHILD_GRAVITY: Int = -1
    }

    var itemSpacing: Int = 0
        set(value) {
            if (field == value) return
            field = value
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
        val marginOverrides = applyItemSpacingForMeasurement()
        try {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        } finally {
            restoreMeasurementMargins(marginOverrides)
        }
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
        DecorationChildDrawingOrder.onViewAdded(this, child)
        decorationDrawing.onViewAdded(child)
    }

    override fun onViewRemoved(child: View) {
        decorationDrawing.onViewRemoved(child)
        super.onViewRemoved(child)
        DecorationChildDrawingOrder.onViewRemoved(this, child)
    }

    private fun layoutHorizontally(
        visibleChildCount: Int,
    ) {
        val innerWidth = width - paddingLeft - paddingRight
        val innerHeight = height - paddingTop - paddingBottom
        val gapSpacings = calculateGapSpacings()
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
        val baseSpacing = gapSpacings.sum()
        val metrics = LinearArrangementCalculator.calculate(
            arrangement = mainAxisArrangement,
            itemSpacing = 0,
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
                currentLeading += metrics.gap + gapSpacings[visibleIndex - 1]
            }
        }
    }

    private fun layoutVertically(
        visibleChildCount: Int,
    ) {
        val innerWidth = width - paddingLeft - paddingRight
        val innerHeight = height - paddingTop - paddingBottom
        val gapSpacings = calculateGapSpacings()
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
        val baseSpacing = gapSpacings.sum()
        val metrics = LinearArrangementCalculator.calculate(
            arrangement = mainAxisArrangement,
            itemSpacing = 0,
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
                currentLeading += metrics.gap + gapSpacings[visibleIndex - 1]
            }
        }
    }

    private fun applyItemSpacingForMeasurement(): List<MeasurementMarginOverride> {
        if (itemSpacing == 0) return emptyList()
        val gapSpacings = calculateGapSpacings()
        if (gapSpacings.isEmpty()) return emptyList()

        val overrides = ArrayList<MeasurementMarginOverride>(gapSpacings.size)
        var visibleIndex = 0
        for (index in 0 until childCount) {
            val child = getChildAt(index)
            if (child.visibility == View.GONE) continue
            if (visibleIndex > 0) {
                val params = child.layoutParams as MarginLayoutParams
                val originalMargin = if (orientation == HORIZONTAL) params.leftMargin else params.topMargin
                overrides += MeasurementMarginOverride(params, originalMargin)
                if (orientation == HORIZONTAL) {
                    params.leftMargin = originalMargin + gapSpacings[visibleIndex - 1]
                } else {
                    params.topMargin = originalMargin + gapSpacings[visibleIndex - 1]
                }
            }
            visibleIndex += 1
        }
        return overrides
    }

    private fun restoreMeasurementMargins(overrides: List<MeasurementMarginOverride>) {
        for (override in overrides) {
            if (orientation == HORIZONTAL) {
                override.params.leftMargin = override.originalMargin
            } else {
                override.params.topMargin = override.originalMargin
            }
        }
    }

    private fun calculateGapSpacings(): IntArray {
        val participations = ArrayList<Float>(childCount)
        for (index in 0 until childCount) {
            val child = getChildAt(index)
            if (child.visibility != View.GONE) {
                participations += mainAxisParticipation(child)
            }
        }
        if (participations.size <= 1) return IntArray(0)

        // AnimatedVisibility keeps a zero-sized native host during the transition. Its spacing must
        // grow with the host, while the preceding maximum preserves the existing gap between stable
        // siblings when one or more intermediate hosts are fully collapsed.
        var precedingParticipation = participations.first()
        return IntArray(participations.size - 1) { gapIndex ->
            val currentParticipation = participations[gapIndex + 1]
            val gapParticipation = min(precedingParticipation, currentParticipation)
            precedingParticipation = max(precedingParticipation, currentParticipation)
            (itemSpacing * gapParticipation).roundToInt()
        }
    }

    private fun mainAxisParticipation(child: View): Float {
        val animatedHost = child as? DeclarativeAnimatedVisibilityHostLayout ?: return 1f
        val scale = if (orientation == HORIZONTAL) animatedHost.widthScale else animatedHost.heightScale
        return scale.coerceIn(0f, 1f)
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

    private data class MeasurementMarginOverride(
        val params: MarginLayoutParams,
        val originalMargin: Int,
    )
}
