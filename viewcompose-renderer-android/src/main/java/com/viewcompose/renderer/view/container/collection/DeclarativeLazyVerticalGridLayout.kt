package com.viewcompose.renderer.view.container

import android.content.Context
import android.graphics.Canvas
import androidx.recyclerview.widget.RecyclerView
import com.viewcompose.renderer.R
import com.viewcompose.ui.node.LazyListItem
import com.viewcompose.renderer.view.lazy.adapter.LazyListAdapter
import com.viewcompose.renderer.view.lazy.adapter.LazyStickyHeaderDecoration
import com.viewcompose.renderer.view.lazy.focus.LazyGridLayoutManager
import com.viewcompose.renderer.view.lazy.layout.LazyGridSpacingDecoration
import com.viewcompose.renderer.view.lazy.focus.LazyFocusFollowLayoutMonitor
import com.viewcompose.renderer.view.lazy.reuse.FrameworkRecyclerViewDefaults
import com.viewcompose.ui.state.LazyListState
import com.viewcompose.renderer.view.PaddingPx
import com.viewcompose.ui.node.policy.LazyLayoutPrefetchPolicy
import android.view.MotionEvent
import com.viewcompose.renderer.view.tree.LayoutPassTracker
import com.viewcompose.renderer.view.lazy.state.UiLazyListConnector

/**
 * Android rendering container for LazyVerticalGrid.
 * Android rendering container for LazyVerticalGrid.
 *
 * Owns the GridLayoutManager, spacing decoration, sticky headers, reuse pool, and LazyListState connection.
 * It coordinates GridLayoutManager, spacing decoration, sticky headers, reuse pools, and LazyListState attachment.
 */
internal class DeclarativeLazyVerticalGridLayout(
    context: Context,
) : RecyclerView(context) {
    private val gridAdapter = LazyListAdapter(RecyclerView.VERTICAL)
    private var listState: LazyListState? = null
    internal var userScrollEnabled: Boolean = true
        private set

    init {
        adapter = gridAdapter
        applyRecyclerDefaults()
    }

    override fun dispatchDraw(canvas: Canvas) {
        val saveCount = canvas.save()
        canvas.clipRect(0, 0, width, height)
        super.dispatchDraw(canvas)
        canvas.restoreToCount(saveCount)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val startNs = LayoutPassTracker.beginTiming()
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        LayoutPassTracker.recordMeasureSince(javaClass, startNs)
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        val startNs = LayoutPassTracker.beginTiming()
        super.onLayout(changed, left, top, right, bottom)
        LayoutPassTracker.recordLayoutSince(javaClass, startNs)
    }

    fun bind(
        spanCount: Int,
        contentPadding: PaddingPx,
        horizontalSpacing: Int,
        verticalSpacing: Int,
        items: List<LazyListItem>,
        state: LazyListState?,
        reverseLayout: Boolean,
        userScrollEnabled: Boolean,
        prefetchPolicy: LazyLayoutPrefetchPolicy,
    ) {
        val lm = layoutManager as? LazyGridLayoutManager
        if (
            lm == null ||
            lm.spanCount != spanCount ||
            lm.reverseLayout != reverseLayout
        ) {
            layoutManager = LazyGridLayoutManager(
                context = context,
                spanCount = spanCount,
                reverseLayout = reverseLayout,
            )
        }
        val gridLayoutManager = checkNotNull(layoutManager as? LazyGridLayoutManager)
        gridLayoutManager.initialPrefetchItemCount = prefetchPolicy.initialPrefetchItemCount
        gridLayoutManager.spanSizeLookup = object : androidx.recyclerview.widget.GridLayoutManager.SpanSizeLookup() {
            override fun getSpanSize(position: Int): Int {
                return gridAdapter.itemSpanAt(position).coerceAtMost(spanCount)
            }
        }
        setItemViewCacheSize(prefetchPolicy.itemViewCacheSize)
        this.userScrollEnabled = userScrollEnabled
        updateSpacingDecoration(horizontalSpacing, verticalSpacing, spanCount)
        setPaddingRelative(
            contentPadding.left,
            contentPadding.top,
            contentPadding.right,
            contentPadding.bottom,
        )
        clipToPadding =
            contentPadding.left == 0 && contentPadding.top == 0 &&
                contentPadding.right == 0 && contentPadding.bottom == 0
        gridAdapter.submitItems(items)
        LazyStickyHeaderDecoration.update(this, gridAdapter)
        if (listState !== state) {
            listState?.attach(null)
            listState = state
        }
        listState?.attach(
            UiLazyListConnector(
                recyclerView = this,
                mainAxisItemSpacing = verticalSpacing,
            ),
        )
    }

    fun dispose() {
        listState?.attach(null)
        listState = null
        LazyStickyHeaderDecoration.dispose(this)
        gridAdapter.disposeAll()
    }

    fun applyRecyclerDefaults(
        sharePool: Boolean = false,
        disableItemAnimator: Boolean = false,
        animateInsert: Boolean = true,
        animateRemove: Boolean = true,
        animateMove: Boolean = true,
        animateChange: Boolean = true,
    ) {
        FrameworkRecyclerViewDefaults.applyLazyGridDefaults(
            recyclerView = this,
            sharePool = sharePool,
            disableItemAnimator = disableItemAnimator,
            animateInsert = animateInsert,
            animateRemove = animateRemove,
            animateMove = animateMove,
            animateChange = animateChange,
        )
    }

    fun setFocusFollowKeyboardEnabled(enabled: Boolean) {
        LazyFocusFollowLayoutMonitor.apply(
            recyclerView = this,
            enabled = enabled,
        )
    }

    override fun onInterceptTouchEvent(event: MotionEvent): Boolean {
        return userScrollEnabled && super.onInterceptTouchEvent(event)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        return userScrollEnabled && super.onTouchEvent(event)
    }

    private fun updateSpacingDecoration(
        horizontalSpacing: Int,
        verticalSpacing: Int,
        spanCount: Int,
    ) {
        val existing = getTag(R.id.viewcompose_lazy_grid_spacing_decoration)
            as? LazyGridSpacingDecoration
        if (existing != null) {
            if (existing.update(horizontalSpacing, verticalSpacing, spanCount)) {
                invalidateItemDecorations()
            }
            return
        }
        val decoration = LazyGridSpacingDecoration(horizontalSpacing, verticalSpacing, spanCount)
        setTag(R.id.viewcompose_lazy_grid_spacing_decoration, decoration)
        addItemDecoration(decoration)
    }
}
