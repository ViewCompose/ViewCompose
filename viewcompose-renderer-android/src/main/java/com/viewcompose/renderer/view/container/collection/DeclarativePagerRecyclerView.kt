package com.viewcompose.renderer.view.container

import android.content.Context
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.PagerSnapHelper
import androidx.recyclerview.widget.RecyclerView
import kotlin.math.abs

/** Scroll lifecycle shared by the pager viewport and portable state coordinator. */
internal enum class PagerScrollState {
    Idle,
    Dragging,
    Settling,
}

/** Receives physical pager motion without inheriting RecyclerView's focus policy. */
internal interface PagerViewportListener {
    fun onPageScrolled(position: Int, offset: Float)

    fun onPageSelected(position: Int)

    fun onScrollStateChanged(state: PagerScrollState)
}

/**
 * RecyclerView pager backend whose selection and focus semantics are owned by ViewCompose.
 *
 * PagerSnapHelper supplies native gesture and fling physics. Unlike ViewPager2, an idle relayout is
 * not interpreted as a new selection, so opening an IME cannot clear focus from the current page.
 */
internal class DeclarativePagerRecyclerView(
    context: Context,
    orientation: Int,
) : RecyclerView(context) {
    private val pagerLayoutManager = PagerLinearLayoutManager(
        context = context,
        orientation = orientation,
        isUserScrollEnabled = { userScrollEnabled },
    )
    private val snapHelper = PagerSnapHelper()
    private val parentInterceptArbitrator = ParentInterceptGestureArbitrator(this) {
        if (pagerOrientation == HORIZONTAL) {
            ParentInterceptGestureArbitrator.Axis.Horizontal
        } else {
            ParentInterceptGestureArbitrator.Axis.Vertical
        }
    }
    private var settledPosition = 0
    private var userScrollEnabled = true

    var viewportListener: PagerViewportListener? = null

    val pagerOrientation: Int
        get() = pagerLayoutManager.orientation

    val currentPage: Int
        get() = settledPosition

    val isUserScrollEnabled: Boolean
        get() = userScrollEnabled

    init {
        layoutManager = pagerLayoutManager
        snapHelper.attachToRecyclerView(this)
        setScrollingTouchSlop(TOUCH_SLOP_PAGING)
        addOnScrollListener(
            object : OnScrollListener() {
                override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                    resolvePageMotion()?.let { motion ->
                        viewportListener?.onPageScrolled(motion.position, motion.offset)
                    }
                }

                override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                    val state = when (newState) {
                        SCROLL_STATE_DRAGGING -> PagerScrollState.Dragging
                        SCROLL_STATE_SETTLING -> PagerScrollState.Settling
                        else -> PagerScrollState.Idle
                    }
                    if (state == PagerScrollState.Idle) {
                        val selected = resolveSnappedPosition() ?: settledPosition
                        if (selected != settledPosition) {
                            clearOutgoingPageFocus(settledPosition)
                            settledPosition = selected
                            viewportListener?.onPageSelected(selected)
                        }
                    }
                    viewportListener?.onScrollStateChanged(state)
                }
            },
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

    fun setUserScrollEnabled(enabled: Boolean) {
        userScrollEnabled = enabled
        if (!enabled) {
            parentInterceptArbitrator.release()
            stopScroll()
        }
    }

    fun setOffscreenPageLimit(limit: Int) {
        require(limit == DEFAULT_OFFSCREEN_PAGE_LIMIT || limit >= 1) {
            "offscreenPageLimit must be $DEFAULT_OFFSCREEN_PAGE_LIMIT or at least 1."
        }
        pagerLayoutManager.offscreenPageLimit = limit
        pagerLayoutManager.initialPrefetchItemCount = if (limit == DEFAULT_OFFSCREEN_PAGE_LIMIT) {
            1
        } else {
            limit * 2 + 1
        }
        requestLayout()
    }

    fun moveToPage(position: Int, animated: Boolean) {
        if (position == settledPosition && !animated) return
        if (animated) {
            smoothScrollToPosition(position)
        } else {
            clearOutgoingPageFocus(settledPosition)
            settledPosition = position
            pagerLayoutManager.scrollToPositionWithOffset(position, 0)
        }
    }

    fun release() {
        stopScroll()
        parentInterceptArbitrator.release()
        viewportListener = null
        clearOnScrollListeners()
        adapter = null
    }

    private fun clearOutgoingPageFocus(position: Int) {
        findViewHolderForAdapterPosition(position)?.itemView?.findFocus()?.clearFocus()
    }

    private fun resolveSnappedPosition(): Int? {
        val view = snapHelper.findSnapView(pagerLayoutManager) ?: return null
        return getChildAdapterPosition(view).takeUnless { it == NO_POSITION }
    }

    private fun resolvePageMotion(): PageMotion? {
        val viewportStart = if (pagerOrientation == HORIZONTAL) paddingLeft else paddingTop
        val viewportEnd = if (pagerOrientation == HORIZONTAL) {
            width - paddingRight
        } else {
            height - paddingBottom
        }
        val viewportCenter = (viewportStart + viewportEnd) / 2f
        val visiblePages = buildList {
            for (index in 0 until childCount) {
                val child = getChildAt(index)
                val childStart = decoratedStart(child)
                val childEnd = decoratedEnd(child)
                if (childEnd <= viewportStart || childStart >= viewportEnd) continue
                val position = getChildAdapterPosition(child)
                if (position != NO_POSITION) add(position to child)
            }
        }
        if (visiblePages.isEmpty()) return null
        val (position, page) = visiblePages.minBy { it.first }
        val pageSize = (decoratedEnd(page) - decoratedStart(page)).coerceAtLeast(1)
        val pageCenter = (decoratedStart(page) + decoratedEnd(page)) / 2f
        val offset = (abs(pageCenter - viewportCenter) / pageSize).coerceIn(0f, 1f)
        return PageMotion(position, offset)
    }

    private fun decoratedStart(view: View): Int = if (pagerOrientation == HORIZONTAL) {
        pagerLayoutManager.getDecoratedLeft(view)
    } else {
        pagerLayoutManager.getDecoratedTop(view)
    }

    private fun decoratedEnd(view: View): Int = if (pagerOrientation == HORIZONTAL) {
        pagerLayoutManager.getDecoratedRight(view)
    } else {
        pagerLayoutManager.getDecoratedBottom(view)
    }

    private data class PageMotion(
        val position: Int,
        val offset: Float,
    )

    private class PagerLinearLayoutManager(
        context: Context,
        orientation: Int,
        private val isUserScrollEnabled: () -> Boolean,
    ) : LinearLayoutManager(context, orientation, false) {
        var offscreenPageLimit: Int = DEFAULT_OFFSCREEN_PAGE_LIMIT

        override fun performAccessibilityAction(
            recycler: Recycler,
            state: State,
            action: Int,
            args: Bundle?,
        ): Boolean {
            if (!isUserScrollEnabled() && action.isScrollAccessibilityAction()) return false
            return super.performAccessibilityAction(recycler, state, action, args)
        }

        override fun onInitializeAccessibilityNodeInfo(
            recycler: Recycler,
            state: State,
            info: AccessibilityNodeInfoCompat,
        ) {
            super.onInitializeAccessibilityNodeInfo(recycler, state, info)
            if (!isUserScrollEnabled()) {
                info.removeAction(AccessibilityNodeInfoCompat.AccessibilityActionCompat.ACTION_SCROLL_BACKWARD)
                info.removeAction(AccessibilityNodeInfoCompat.AccessibilityActionCompat.ACTION_SCROLL_FORWARD)
                info.isScrollable = false
            }
        }

        override fun calculateExtraLayoutSpace(state: State, extraLayoutSpace: IntArray) {
            if (offscreenPageLimit == DEFAULT_OFFSCREEN_PAGE_LIMIT) {
                super.calculateExtraLayoutSpace(state, extraLayoutSpace)
                return
            }
            val pageSize = if (orientation == HORIZONTAL) {
                width - paddingLeft - paddingRight
            } else {
                height - paddingTop - paddingBottom
            }
            val extra = pageSize.coerceAtLeast(0) * offscreenPageLimit
            extraLayoutSpace[0] = extra
            extraLayoutSpace[1] = extra
        }

        private fun Int.isScrollAccessibilityAction(): Boolean {
            return this == AccessibilityNodeInfoCompat.ACTION_SCROLL_BACKWARD ||
                this == AccessibilityNodeInfoCompat.ACTION_SCROLL_FORWARD
        }
    }

    companion object {
        const val DEFAULT_OFFSCREEN_PAGE_LIMIT: Int = -1
    }
}
