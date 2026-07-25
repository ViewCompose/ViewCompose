package com.viewcompose.renderer.view.container

import android.content.Context
import androidx.recyclerview.widget.RecyclerView
import com.viewcompose.renderer.R
import com.viewcompose.ui.node.LazyListItem
import com.viewcompose.renderer.view.lazy.adapter.LazyListAdapter
import com.viewcompose.renderer.view.lazy.focus.LazyGridLayoutManager
import com.viewcompose.renderer.view.lazy.layout.LazyGridSpacingDecoration
import com.viewcompose.renderer.view.lazy.focus.LazyFocusFollowLayoutMonitor
import com.viewcompose.renderer.view.lazy.reuse.FrameworkRecyclerViewDefaults
import com.viewcompose.ui.state.LazyListState
import com.viewcompose.renderer.view.tree.LayoutPassTracker
import com.viewcompose.renderer.view.lazy.state.UiLazyListConnector

internal class DeclarativeLazyVerticalGridLayout(
    context: Context,
) : RecyclerView(context) {
    private val gridAdapter = LazyListAdapter(RecyclerView.VERTICAL)
    private var listState: LazyListState? = null

    init {
        adapter = gridAdapter
        applyRecyclerDefaults()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val startNs = System.nanoTime()
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        LayoutPassTracker.recordMeasure(
            viewName = javaClass.simpleName,
            durationNs = System.nanoTime() - startNs,
        )
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        val startNs = System.nanoTime()
        super.onLayout(changed, left, top, right, bottom)
        LayoutPassTracker.recordLayout(
            viewName = javaClass.simpleName,
            durationNs = System.nanoTime() - startNs,
        )
    }

    fun bind(
        spanCount: Int,
        contentPadding: Int,
        horizontalSpacing: Int,
        verticalSpacing: Int,
        items: List<LazyListItem>,
        state: LazyListState?,
    ) {
        val lm = layoutManager as? LazyGridLayoutManager
        if (lm == null || lm.spanCount != spanCount) {
            layoutManager = LazyGridLayoutManager(
                context = context,
                spanCount = spanCount,
            )
        }
        updateSpacingDecoration(horizontalSpacing, verticalSpacing, spanCount)
        setPadding(contentPadding, contentPadding, contentPadding, contentPadding)
        clipToPadding = contentPadding == 0
        gridAdapter.submitItems(items)
        if (listState !== state) {
            listState?.attach(null)
            listState = state
        }
        listState?.attach(UiLazyListConnector(this))
    }

    fun dispose() {
        listState?.attach(null)
        listState = null
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

    private fun updateSpacingDecoration(
        horizontalSpacing: Int,
        verticalSpacing: Int,
        spanCount: Int,
    ) {
        val existing = getTag(R.id.viewcompose_lazy_grid_spacing_decoration)
            as? LazyGridSpacingDecoration
        if (existing != null) {
            existing.update(horizontalSpacing, verticalSpacing, spanCount)
            invalidateItemDecorations()
            return
        }
        val decoration = LazyGridSpacingDecoration(horizontalSpacing, verticalSpacing, spanCount)
        setTag(R.id.viewcompose_lazy_grid_spacing_decoration, decoration)
        addItemDecoration(decoration)
    }
}
