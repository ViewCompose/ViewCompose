package com.viewcompose.renderer.view.lazy.layout

import android.graphics.Rect
import android.view.View
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView

/**
 * Splits grid spacing by physical span bounds so single, fixed-span, and full-line items retain
 * equal usable cell widths in LTR and RTL.
 */
internal class LazyGridSpacingDecoration(
    private var horizontalSpacing: Int,
    private var verticalSpacing: Int,
    private var spanCount: Int,
) : RecyclerView.ItemDecoration() {

    fun update(
        horizontalSpacing: Int,
        verticalSpacing: Int,
        spanCount: Int,
    ): Boolean {
        if (
            this.horizontalSpacing == horizontalSpacing &&
            this.verticalSpacing == verticalSpacing &&
            this.spanCount == spanCount
        ) {
            return false
        }
        this.horizontalSpacing = horizontalSpacing
        this.verticalSpacing = verticalSpacing
        this.spanCount = spanCount
        return true
    }

    override fun getItemOffsets(
        outRect: Rect,
        view: View,
        parent: RecyclerView,
        state: RecyclerView.State,
    ) {
        if (horizontalSpacing <= 0 && verticalSpacing <= 0) {
            outRect.setEmpty()
            return
        }
        val position = parent.getChildAdapterPosition(view)
        if (position == RecyclerView.NO_POSITION) {
            outRect.setEmpty()
            return
        }
        val layoutManager = parent.layoutManager as? GridLayoutManager
        if (layoutManager == null) {
            outRect.setEmpty()
            return
        }
        val resolvedSpanCount = layoutManager.spanCount.coerceAtLeast(1)
        val spanLookup = layoutManager.spanSizeLookup
        val spanSize = spanLookup.getSpanSize(position).coerceIn(1, resolvedSpanCount)
        val spanIndex = spanLookup.getSpanIndex(position, resolvedSpanCount)
            .coerceIn(0, resolvedSpanCount - spanSize)
        val spanGroupIndex = spanLookup.getSpanGroupIndex(position, resolvedSpanCount)
        val offsets = calculateGridItemOffsets(
            horizontalSpacing = horizontalSpacing,
            verticalSpacing = verticalSpacing,
            spanCount = resolvedSpanCount,
            spanIndex = spanIndex,
            spanSize = spanSize,
            spanGroupIndex = spanGroupIndex,
            isRtl = parent.layoutDirection == View.LAYOUT_DIRECTION_RTL,
        )
        outRect.set(offsets.left, offsets.top, offsets.right, 0)
    }
}

internal data class LazyGridItemOffsets(
    val left: Int,
    val top: Int,
    val right: Int,
)

internal fun calculateGridItemOffsets(
    horizontalSpacing: Int,
    verticalSpacing: Int,
    spanCount: Int,
    spanIndex: Int,
    spanSize: Int,
    spanGroupIndex: Int,
    isRtl: Boolean,
): LazyGridItemOffsets {
    val physicalSpanIndex = if (isRtl) {
        spanCount - spanIndex - spanSize
    } else {
        spanIndex
    }
    val spacing = horizontalSpacing.toLong()
    return LazyGridItemOffsets(
        left = (spacing * physicalSpanIndex / spanCount).toInt(),
        top = if (spanGroupIndex > 0) verticalSpacing else 0,
        right = (
            spacing * (spanCount - physicalSpanIndex - spanSize) / spanCount
        ).toInt(),
    )
}
