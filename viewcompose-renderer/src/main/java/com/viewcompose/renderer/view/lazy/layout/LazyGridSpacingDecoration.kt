package com.viewcompose.renderer.view.lazy.layout

import android.graphics.Rect
import android.view.View
import androidx.recyclerview.widget.RecyclerView

/**
 * LazyVerticalGrid 的等比分摊间距装饰。
 * Proportional spacing decoration for LazyVerticalGrid.
 *
 * 横向间距按列号分配到左右 offset，保证每列可用宽度一致。
 * Horizontal spacing is split by column index so every column keeps the same usable width.
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
        val column = position % spanCount
        outRect.left = horizontalSpacing * column / spanCount
        outRect.right = horizontalSpacing * (spanCount - 1 - column) / spanCount
        if (position >= spanCount) {
            outRect.top = verticalSpacing
        }
    }
}
