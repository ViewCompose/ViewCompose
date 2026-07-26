package com.viewcompose.renderer.view.lazy.state

import android.view.View
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.OrientationHelper
import androidx.recyclerview.widget.RecyclerView
import com.viewcompose.renderer.view.lazy.adapter.LazyListAdapter
import com.viewcompose.ui.state.LazyListConnector
import com.viewcompose.ui.state.LazyListItemInfo
import com.viewcompose.ui.state.LazyListLayoutInfo
import com.viewcompose.ui.state.LazyListOrientation
import com.viewcompose.ui.state.LazyListStateSnapshot

internal class UiLazyListConnector(
    private val recyclerView: RecyclerView,
    private val mainAxisItemSpacing: Int = 0,
) : LazyListConnector {
    override val identity: Any
        get() = recyclerView

    private var snapshotListener: ((LazyListStateSnapshot) -> Unit)? = null
    private var lastScrolledBackward = false
    private var lastScrolledForward = false
    private var observedAdapter: RecyclerView.Adapter<*>? = null

    private val scrollListener = object : RecyclerView.OnScrollListener() {
        override fun onScrolled(
            recyclerView: RecyclerView,
            dx: Int,
            dy: Int,
        ) {
            val delta = if (resolveOrientation() == LazyListOrientation.Vertical) dy else dx
            if (delta != 0) {
                lastScrolledBackward = delta < 0
                lastScrolledForward = delta > 0
            }
            emitSnapshot()
        }

        override fun onScrollStateChanged(
            recyclerView: RecyclerView,
            newState: Int,
        ) {
            emitSnapshot()
        }
    }
    private val layoutChangeListener =
        View.OnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> emitSnapshot() }
    private val adapterObserver = object : RecyclerView.AdapterDataObserver() {
        override fun onChanged() = emitSnapshot()
        override fun onItemRangeChanged(positionStart: Int, itemCount: Int) = emitSnapshot()
        override fun onItemRangeInserted(positionStart: Int, itemCount: Int) = emitSnapshot()
        override fun onItemRangeRemoved(positionStart: Int, itemCount: Int) = emitSnapshot()
        override fun onItemRangeMoved(fromPosition: Int, toPosition: Int, itemCount: Int) =
            emitSnapshot()
    }

    override fun scrollToItem(
        index: Int,
        scrollOffset: Int,
        animated: Boolean,
    ) {
        if (animated) {
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

    override fun stopScroll() {
        recyclerView.stopScroll()
    }

    override fun currentSnapshot(): LazyListStateSnapshot? {
        val layoutManager = recyclerView.layoutManager as? LinearLayoutManager ?: return null
        val itemCount = recyclerView.adapter?.itemCount ?: 0
        val firstVisibleIndex = layoutManager.findFirstVisibleItemPosition()
        if (firstVisibleIndex == RecyclerView.NO_POSITION && itemCount > 0) {
            return null
        }

        val orientation = resolveOrientation()
        val orientationHelper = if (orientation == LazyListOrientation.Vertical) {
            OrientationHelper.createVerticalHelper(layoutManager)
        } else {
            OrientationHelper.createHorizontalHelper(layoutManager)
        }
        val resolvedFirstVisibleIndex =
            if (firstVisibleIndex == RecyclerView.NO_POSITION) 0 else firstVisibleIndex
        val firstVisibleView = layoutManager.findViewByPosition(resolvedFirstVisibleIndex)
        val firstVisibleScrollOffset = firstVisibleView?.let { view ->
            (
                orientationHelper.startAfterPadding -
                    orientationHelper.getDecoratedStart(view)
                ).coerceAtLeast(0)
        } ?: 0
        val adapter = recyclerView.adapter as? LazyListAdapter
        val gridLayoutManager = layoutManager as? GridLayoutManager
        val visibleItems = buildList {
            for (childIndex in 0 until recyclerView.childCount) {
                val child = recyclerView.getChildAt(childIndex)
                val position = recyclerView.getChildAdapterPosition(child)
                if (position == RecyclerView.NO_POSITION || position >= itemCount) {
                    continue
                }
                add(
                    LazyListItemInfo(
                        index = position,
                        key = adapter?.itemKeyAt(position) ?: position,
                        contentType = adapter?.itemContentTypeAt(position),
                        offset =
                            orientationHelper.getDecoratedStart(child) -
                                orientationHelper.startAfterPadding,
                        size = orientationHelper.getDecoratedMeasurement(child),
                        spanIndex = gridLayoutManager
                            ?.spanSizeLookup
                            ?.getSpanIndex(position, gridLayoutManager.spanCount)
                            ?: 0,
                        spanSize = gridLayoutManager
                            ?.spanSizeLookup
                            ?.getSpanSize(position)
                            ?: 1,
                    ),
                )
            }
        }.sortedBy { item -> item.offset }

        return LazyListStateSnapshot(
            firstVisibleItemIndex = resolvedFirstVisibleIndex,
            firstVisibleItemScrollOffset = firstVisibleScrollOffset,
            layoutInfo = LazyListLayoutInfo(
                visibleItemsInfo = visibleItems,
                viewportStartOffset = 0,
                viewportEndOffset = orientationHelper.totalSpace.coerceAtLeast(0),
                totalItemsCount = itemCount,
                beforeContentPadding =
                    if (orientation == LazyListOrientation.Vertical) {
                        recyclerView.paddingTop
                    } else {
                        recyclerView.paddingLeft
                    },
                afterContentPadding =
                    if (orientation == LazyListOrientation.Vertical) {
                        recyclerView.paddingBottom
                    } else {
                        recyclerView.paddingRight
                    },
                mainAxisItemSpacing = mainAxisItemSpacing,
                orientation = orientation,
                reverseLayout = layoutManager.reverseLayout,
            ),
            isScrollInProgress = recyclerView.scrollState != RecyclerView.SCROLL_STATE_IDLE,
            canScrollBackward = canScroll(direction = -1, orientation = orientation),
            canScrollForward = canScroll(direction = 1, orientation = orientation),
            lastScrolledBackward = lastScrolledBackward,
            lastScrolledForward = lastScrolledForward,
        )
    }

    override fun setOnSnapshotChangedListener(
        listener: ((LazyListStateSnapshot) -> Unit)?,
    ) {
        if (snapshotListener === listener) {
            return
        }
        detachObservers()
        snapshotListener = listener
        if (listener != null) {
            attachObservers()
            emitSnapshot()
        }
    }

    private fun resolveOrientation(): LazyListOrientation {
        val orientation = (recyclerView.layoutManager as? LinearLayoutManager)?.orientation
        return if (orientation == RecyclerView.HORIZONTAL) {
            LazyListOrientation.Horizontal
        } else {
            LazyListOrientation.Vertical
        }
    }

    private fun canScroll(
        direction: Int,
        orientation: LazyListOrientation,
    ): Boolean {
        return if (orientation == LazyListOrientation.Vertical) {
            recyclerView.canScrollVertically(direction)
        } else {
            recyclerView.canScrollHorizontally(direction)
        }
    }

    private fun attachObservers() {
        recyclerView.addOnScrollListener(scrollListener)
        recyclerView.addOnLayoutChangeListener(layoutChangeListener)
        observedAdapter = recyclerView.adapter?.also { adapter ->
            adapter.registerAdapterDataObserver(adapterObserver)
        }
    }

    private fun detachObservers() {
        recyclerView.removeOnScrollListener(scrollListener)
        recyclerView.removeOnLayoutChangeListener(layoutChangeListener)
        observedAdapter?.unregisterAdapterDataObserver(adapterObserver)
        observedAdapter = null
    }

    private fun emitSnapshot() {
        val listener = snapshotListener ?: return
        currentSnapshot()?.let(listener)
    }
}
