package com.viewcompose.renderer.view.container

import android.content.Context
import android.graphics.Canvas
import android.view.MotionEvent
import androidx.recyclerview.widget.RecyclerView
import com.viewcompose.renderer.R
import com.viewcompose.renderer.view.PaddingPx
import com.viewcompose.renderer.view.lazy.adapter.LazyListAdapter
import com.viewcompose.renderer.view.lazy.adapter.LazyStickyHeaderDecoration
import com.viewcompose.renderer.view.lazy.focus.LazyGridLayoutManager
import com.viewcompose.renderer.view.lazy.focus.LazyFocusFollowLayoutMonitor
import com.viewcompose.renderer.view.lazy.layout.LazyGridSpacingDecoration
import com.viewcompose.renderer.view.lazy.reuse.FrameworkRecyclerViewDefaults
import com.viewcompose.renderer.view.lazy.state.UiLazyListConnector
import com.viewcompose.renderer.view.tree.LayoutPassTracker
import com.viewcompose.renderer.view.tree.ModifierInsetsApplier
import com.viewcompose.renderer.view.tree.RetainedSessionSubmission
import com.viewcompose.ui.node.LazyListItem
import com.viewcompose.ui.node.policy.GridItemSpan
import com.viewcompose.ui.node.policy.LazyLayoutPrefetchPolicy
import com.viewcompose.ui.state.LazyListState

/**
 * Android rendering container for LazyVerticalGrid.
 *
 * Owns the GridLayoutManager, spacing decoration, sticky headers, reuse pool, and LazyListState
 * connection.
 */
internal class DeclarativeLazyVerticalGridLayout(
    context: Context,
) : RecyclerView(context) {
    private val parentInterceptArbitrator = ParentInterceptGestureArbitrator(this) {
        ParentInterceptGestureArbitrator.Axis.Vertical
    }
    private val gridAdapter = LazyListAdapter(RecyclerView.VERTICAL)
    private var listState: LazyListState? = null
    private var cells: LazyGridCellsPx = LazyGridCellsPx.Fixed(1)
    private var horizontalSpacingPx: Int = 0
    private var verticalSpacingPx: Int = 0
    private var reverseLayoutState: Boolean = false
    private var resolvedSpanCount: Int = 1
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
        if (MeasureSpec.getMode(widthMeasureSpec) != MeasureSpec.UNSPECIFIED) {
            updateResolvedSpanCount(
                MeasureSpec.getSize(widthMeasureSpec) - paddingLeft - paddingRight,
            )
        }
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        if (MeasureSpec.getMode(widthMeasureSpec) == MeasureSpec.UNSPECIFIED) {
            updateResolvedSpanCount(measuredWidth - paddingLeft - paddingRight)
        }
        LayoutPassTracker.recordMeasureSince(javaClass, startNs)
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        val startNs = LayoutPassTracker.beginTiming()
        super.onLayout(changed, left, top, right, bottom)
        LayoutPassTracker.recordLayoutSince(javaClass, startNs)
    }

    fun bind(
        cells: LazyGridCellsPx,
        contentPadding: PaddingPx,
        horizontalSpacing: Int,
        verticalSpacing: Int,
        items: List<LazyListItem>,
        state: LazyListState?,
        reverseLayout: Boolean,
        userScrollEnabled: Boolean,
        prefetchPolicy: LazyLayoutPrefetchPolicy,
        mountedTreeCacheSize: Int,
        submission: RetainedSessionSubmission = RetainedSessionSubmission.immediate(),
    ) {
        this.cells = cells
        horizontalSpacingPx = horizontalSpacing
        verticalSpacingPx = verticalSpacing
        reverseLayoutState = reverseLayout
        updateResolvedSpanCount(width - contentPadding.left - contentPadding.right)
        ensureLayoutManager()
        val gridLayoutManager = checkNotNull(layoutManager as? LazyGridLayoutManager)
        gridLayoutManager.initialPrefetchItemCount = prefetchPolicy.nestedInitialPrefetchItemCount
        installSpanSizeLookup(gridLayoutManager)
        setItemViewCacheSize(prefetchPolicy.itemViewCacheSize)
        gridAdapter.configureMountedTreeCache(mountedTreeCacheSize)
        this.userScrollEnabled = userScrollEnabled
        if (!userScrollEnabled) parentInterceptArbitrator.release()
        updateSpacingDecoration(horizontalSpacing, verticalSpacing, resolvedSpanCount)
        ModifierInsetsApplier.applyLazyContentPadding(this, contentPadding)
        clipToPadding =
            contentPadding.left == 0 && contentPadding.top == 0 &&
                contentPadding.right == 0 && contentPadding.bottom == 0
        submission.publish {
            LazyStickyHeaderDecoration.submitItemsAndUpdate(
                recyclerView = this,
                adapter = gridAdapter,
                items = items,
                submissionRevision = submission.revision,
            )
        }
        submission.publish {
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

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        parentInterceptArbitrator.onDispatchTouchEvent(event, userScrollEnabled)
        return super.dispatchTouchEvent(event)
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

    private fun updateResolvedSpanCount(availableInnerWidth: Int) {
        val next = when (val policy = cells) {
            is LazyGridCellsPx.Fixed -> policy.count
            is LazyGridCellsPx.Adaptive -> {
                val available = availableInnerWidth.coerceAtLeast(0).toLong()
                val spacing = horizontalSpacingPx.coerceAtLeast(0).toLong()
                ((available + spacing) / (policy.minSize.toLong() + spacing))
                    .coerceAtLeast(1L)
                    .coerceAtMost(Int.MAX_VALUE.toLong())
                    .toInt()
            }
        }
        if (resolvedSpanCount == next) return
        resolvedSpanCount = next
        ensureLayoutManager()
        updateSpacingDecoration(horizontalSpacingPx, verticalSpacingPx, resolvedSpanCount)
        invalidateItemDecorations()
    }

    private fun ensureLayoutManager() {
        val current = layoutManager as? LazyGridLayoutManager
        if (current != null && current.reverseLayout == reverseLayoutState) {
            if (current.spanCount != resolvedSpanCount) {
                current.spanCount = resolvedSpanCount
                installSpanSizeLookup(current)
            }
            return
        }
        layoutManager = LazyGridLayoutManager(
            context = context,
            spanCount = resolvedSpanCount,
            reverseLayout = reverseLayoutState,
        ).also(::installSpanSizeLookup)
    }

    private fun installSpanSizeLookup(layoutManager: LazyGridLayoutManager) {
        layoutManager.spanSizeLookup = object : androidx.recyclerview.widget.GridLayoutManager.SpanSizeLookup() {
            override fun getSpanSize(position: Int): Int {
                return when (val span = gridAdapter.itemSpanAt(position)) {
                    GridItemSpan.Single -> 1
                    is GridItemSpan.Fixed -> span.count.coerceAtMost(resolvedSpanCount)
                    GridItemSpan.FullLine -> resolvedSpanCount
                }
            }
        }
    }
}

internal sealed interface LazyGridCellsPx {
    data class Fixed(val count: Int) : LazyGridCellsPx
    data class Adaptive(val minSize: Int) : LazyGridCellsPx
}
