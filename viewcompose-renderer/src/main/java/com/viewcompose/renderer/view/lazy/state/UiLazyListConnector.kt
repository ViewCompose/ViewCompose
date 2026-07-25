package com.viewcompose.renderer.view.lazy.state

import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.OrientationHelper
import androidx.recyclerview.widget.RecyclerView
import com.viewcompose.ui.state.LazyListConnector
import com.viewcompose.ui.state.LazyListPosition

internal class UiLazyListConnector(
    private val recyclerView: RecyclerView,
) : LazyListConnector {
    override val identity: Any
        get() = recyclerView

    override fun scrollToPosition(
        index: Int,
        smooth: Boolean,
    ) {
        if (smooth) {
            recyclerView.smoothScrollToPosition(index)
        } else {
            recyclerView.scrollToPosition(index)
        }
    }

    override fun scrollToPosition(
        index: Int,
        scrollOffset: Int,
        smooth: Boolean,
    ) {
        if (smooth) {
            recyclerView.smoothScrollToPosition(index)
            return
        }
        val layoutManager = recyclerView.layoutManager as? LinearLayoutManager
        if (layoutManager == null) {
            recyclerView.scrollToPosition(index)
            return
        }
        layoutManager.scrollToPositionWithOffset(
            index,
            -scrollOffset,
        )
    }

    override fun currentPosition(): LazyListPosition? {
        val layoutManager = recyclerView.layoutManager as? LinearLayoutManager
            ?: return null
        val index = layoutManager.findFirstVisibleItemPosition()
        if (index == RecyclerView.NO_POSITION) {
            return null
        }
        val itemView = layoutManager.findViewByPosition(index)
            ?: return LazyListPosition(
                index = index,
                scrollOffset = 0,
            )
        val orientationHelper = if (layoutManager.orientation == RecyclerView.VERTICAL) {
            OrientationHelper.createVerticalHelper(layoutManager)
        } else {
            OrientationHelper.createHorizontalHelper(layoutManager)
        }
        return LazyListPosition(
            index = index,
            scrollOffset = (
                orientationHelper.startAfterPadding -
                    orientationHelper.getDecoratedStart(itemView)
                ).coerceAtLeast(0),
        )
    }
}
