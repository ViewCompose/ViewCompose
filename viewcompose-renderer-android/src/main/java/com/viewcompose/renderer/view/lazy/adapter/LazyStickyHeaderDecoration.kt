package com.viewcompose.renderer.view.lazy.adapter

import android.graphics.Canvas
import android.graphics.Rect
import android.view.MotionEvent
import android.view.View
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.viewcompose.renderer.R
import com.viewcompose.ui.node.LazyListItem

/**
 * Draws and dispatches pointer input to a sticky-header holder detached from RecyclerView children.
 * Draws and dispatches pointer input to a detached, session-backed header holder.
 *
 * Keeps the pinned copy outside RecyclerView children to avoid conflicting with LayoutManager recycling.
 * Keeping the pinned copy outside RecyclerView's child set avoids fighting LayoutManager recycling.
 *
 * The in-list header remains the semantics and accessibility source; the pinned copy is only a visual and pointer surface.
 * The ordinary in-list header remains the semantic/accessibility source while this copy owns the
 * pinned visual and pointer surface.
 */
internal class LazyStickyHeaderDecoration private constructor(
    private val recyclerView: RecyclerView,
    private val adapter: LazyListAdapter,
) : RecyclerView.ItemDecoration(), RecyclerView.OnItemTouchListener {
    private var headerHolder: LazyListViewHolder? = null
    private var headerPosition = RecyclerView.NO_POSITION
    private var boundItemsVersion = Long.MIN_VALUE
    private val headerBounds = Rect()
    private var handlingHeaderTouch = false
    private var disposed = false

    override fun onDrawOver(
        canvas: Canvas,
        parent: RecyclerView,
        state: RecyclerView.State,
    ) {
        if (disposed || adapter.itemCount == 0) {
            clearHeader()
            return
        }
        val layoutManager = parent.layoutManager as? LinearLayoutManager ?: run {
            clearHeader()
            return
        }
        val firstVisiblePosition = layoutManager.findFirstVisibleItemPosition()
        val stickyPosition = adapter.findStickyHeaderPosition(firstVisiblePosition)
        if (stickyPosition == RecyclerView.NO_POSITION) {
            clearHeader()
            return
        }

        val holder = obtainHeader(stickyPosition)
        val header = holder.itemView
        measureAndLayoutHeader(header, parent)
        val left = parent.paddingLeft
        val naturalTop = parent.paddingTop
        // Push the current pinned header out as the next header reaches its position.
        // When the next header reaches the pinned slot, push the current pinned header out.
        val nextHeaderTop = findNextHeaderTop(
            parent = parent,
            currentHeaderPosition = stickyPosition,
        )
        val top = if (nextHeaderTop == null) {
            naturalTop
        } else {
            minOf(naturalTop, nextHeaderTop - header.measuredHeight)
        }
        headerBounds.set(
            left,
            top,
            left + header.measuredWidth,
            top + header.measuredHeight,
        )

        val checkpoint = canvas.save()
        canvas.translate(left.toFloat(), top.toFloat())
        header.draw(canvas)
        canvas.restoreToCount(checkpoint)
    }

    override fun onInterceptTouchEvent(
        recyclerView: RecyclerView,
        event: MotionEvent,
    ): Boolean {
        if (disposed || headerBounds.isEmpty) {
            return false
        }
        if (event.actionMasked == MotionEvent.ACTION_DOWN) {
            handlingHeaderTouch = headerBounds.contains(event.x.toInt(), event.y.toInt())
        }
        if (!handlingHeaderTouch) {
            return false
        }
        dispatchToHeader(event)
        if (event.actionMasked == MotionEvent.ACTION_UP ||
            event.actionMasked == MotionEvent.ACTION_CANCEL
        ) {
            handlingHeaderTouch = false
        }
        return true
    }

    override fun onTouchEvent(
        recyclerView: RecyclerView,
        event: MotionEvent,
    ) {
        if (!handlingHeaderTouch) {
            return
        }
        dispatchToHeader(event)
        if (event.actionMasked == MotionEvent.ACTION_UP ||
            event.actionMasked == MotionEvent.ACTION_CANCEL
        ) {
            handlingHeaderTouch = false
        }
    }

    override fun onRequestDisallowInterceptTouchEvent(disallowIntercept: Boolean) = Unit

    fun dispose() {
        if (disposed) {
            return
        }
        disposed = true
        recyclerView.removeItemDecoration(this)
        recyclerView.removeOnItemTouchListener(this)
        if (recyclerView.getTag(R.id.viewcompose_lazy_sticky_header_decoration) === this) {
            recyclerView.setTag(R.id.viewcompose_lazy_sticky_header_decoration, null)
        }
        adapter.setStickyHeaderDisposer(null)
        clearHeader()
    }

    private fun obtainHeader(position: Int): LazyListViewHolder {
        val current = headerHolder
        if (current == null || headerPosition != position) {
            clearHeader()
            return adapter.createDetachedHolder(
                parent = recyclerView,
                position = position,
            ).also { holder ->
                headerHolder = holder
                headerPosition = position
                boundItemsVersion = adapter.currentItemsVersion()
            }
        }
        if (boundItemsVersion != adapter.currentItemsVersion()) {
            adapter.rebindDetachedHolder(current, position)
            boundItemsVersion = adapter.currentItemsVersion()
        }
        return current
    }

    private fun clearHeader() {
        headerHolder?.let(adapter::recycleDetachedHolder)
        headerHolder = null
        headerPosition = RecyclerView.NO_POSITION
        boundItemsVersion = Long.MIN_VALUE
        headerBounds.setEmpty()
        handlingHeaderTouch = false
    }

    private fun measureAndLayoutHeader(
        header: View,
        parent: RecyclerView,
    ) {
        val availableWidth =
            (parent.width - parent.paddingLeft - parent.paddingRight).coerceAtLeast(0)
        header.measure(
            View.MeasureSpec.makeMeasureSpec(availableWidth, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
        )
        header.layout(0, 0, header.measuredWidth, header.measuredHeight)
    }

    private fun findNextHeaderTop(
        parent: RecyclerView,
        currentHeaderPosition: Int,
    ): Int? {
        var nextTop: Int? = null
        for (childIndex in 0 until parent.childCount) {
            val child = parent.getChildAt(childIndex)
            val position = parent.getChildAdapterPosition(child)
            if (
                position == RecyclerView.NO_POSITION ||
                position <= currentHeaderPosition ||
                !adapter.isStickyHeader(position)
            ) {
                continue
            }
            val decoratedTop = parent.layoutManager?.getDecoratedTop(child) ?: child.top
            nextTop = nextTop?.let { current -> minOf(current, decoratedTop) } ?: decoratedTop
        }
        return nextTop
    }

    private fun dispatchToHeader(event: MotionEvent) {
        val header = headerHolder?.itemView ?: return
        val transformed = MotionEvent.obtain(event)
        // Convert RecyclerView coordinates to detached-header local coordinates before dispatch.
        // Convert RecyclerView coordinates into the detached header's local coordinates before dispatching.
        transformed.offsetLocation(
            -headerBounds.left.toFloat(),
            -headerBounds.top.toFloat(),
        )
        header.dispatchTouchEvent(transformed)
        transformed.recycle()
    }

    companion object {
        fun submitItemsAndUpdate(
            recyclerView: RecyclerView,
            adapter: LazyListAdapter,
            items: List<LazyListItem>,
            submissionRevision: Long,
        ) {
            var failure: Throwable? = null
            try {
                adapter.submitItems(items, submissionRevision)
            } catch (error: Throwable) {
                failure = error
            }
            try {
                update(recyclerView, adapter)
            } catch (error: Throwable) {
                val firstFailure = failure
                if (firstFailure == null) {
                    failure = error
                } else if (firstFailure !== error) {
                    firstFailure.addSuppressed(error)
                }
            }
            failure?.let { throw it }
        }

        fun update(
            recyclerView: RecyclerView,
            adapter: LazyListAdapter,
        ) {
            val existing = recyclerView.getTag(R.id.viewcompose_lazy_sticky_header_decoration)
                as? LazyStickyHeaderDecoration
            if (!adapter.hasStickyHeaders()) {
                existing?.dispose()
                return
            }
            if (existing != null) {
                recyclerView.invalidateItemDecorations()
                return
            }
            val decoration = LazyStickyHeaderDecoration(
                recyclerView = recyclerView,
                adapter = adapter,
            )
            recyclerView.setTag(R.id.viewcompose_lazy_sticky_header_decoration, decoration)
            recyclerView.addItemDecoration(decoration)
            recyclerView.addOnItemTouchListener(decoration)
            adapter.setStickyHeaderDisposer(decoration::dispose)
        }

        fun dispose(recyclerView: RecyclerView) {
            val existing = recyclerView.getTag(R.id.viewcompose_lazy_sticky_header_decoration)
                as? LazyStickyHeaderDecoration
            existing?.dispose()
        }
    }
}
