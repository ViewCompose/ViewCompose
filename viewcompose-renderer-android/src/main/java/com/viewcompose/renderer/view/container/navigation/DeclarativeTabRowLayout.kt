package com.viewcompose.renderer.view.container

import android.content.Context
import android.graphics.Canvas
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.view.ViewGroup
import android.widget.HorizontalScrollView
import com.viewcompose.ui.node.collection.TabIndicatorPosition
import com.viewcompose.ui.node.collection.TabIndicatorWidthMode
import com.viewcompose.ui.state.PagerState
import com.viewcompose.ui.state.PagerStateSnapshot
import com.viewcompose.renderer.view.tree.LayoutPassTracker
import com.viewcompose.renderer.view.tree.RetainedSessionSubmission

/** Scrollable Android host for eager TabRow children and a PagerState-driven indicator. */
internal class DeclarativeTabRowLayout(
    context: Context,
) : HorizontalScrollView(context), ChildHostViewGroup {

    private val tabContainer = TabRowContainer(context)
    private var selectedIndex: Int = 0
    private var pagerState: PagerState? = null
    private var pagerStateListener: ((PagerStateSnapshot) -> Unit)? = null

    // Indicator properties.
    private var indicatorColor: Int = 0
    private var indicatorHeightPx: Int = 0
    private var indicatorCornerRadiusPx: Int = 0
    private var indicatorPosition: TabIndicatorPosition = TabIndicatorPosition.Bottom
    private var indicatorWidthMode: TabIndicatorWidthMode = TabIndicatorWidthMode.MatchItem
    private var indicatorFixedWidthPx: Int = 0

    // Item layout and interaction properties.
    private var itemSpacingPx: Int = 0
    private var minItemWidthPx: Int = 0
    private var equalWidth: Boolean = true
    private var containerColorState: Int? = null

    override val childHost: ViewGroup
        get() = tabContainer

    init {
        isHorizontalScrollBarEnabled = false
        isFillViewport = true
        clipChildren = false
        clipToPadding = false
        addView(tabContainer, LayoutParams(
            LayoutParams.WRAP_CONTENT,
            LayoutParams.MATCH_PARENT,
        ))
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val startNs = LayoutPassTracker.beginTiming()
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        LayoutPassTracker.recordMeasureSince(javaClass, startNs)
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        val startNs = LayoutPassTracker.beginTiming()
        super.onLayout(changed, l, t, r, b)
        LayoutPassTracker.recordLayoutSince(javaClass, startNs)
    }

    override fun dispatchDraw(canvas: Canvas) {
        val saveCount = canvas.save()
        canvas.clipRect(0, 0, width, height)
        super.dispatchDraw(canvas)
        canvas.restoreToCount(saveCount)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w > 0 && oldw > 0 && w != oldw && tabContainer.childCount > 0) {
            post { scrollToSelectedTab(animate = false) }
        }
    }

    fun bind(
        selectedIndex: Int,
        pagerState: PagerState?,
        indicatorColor: Int,
        indicatorHeight: Int,
        indicatorCornerRadius: Int,
        indicatorPosition: TabIndicatorPosition,
        indicatorWidthMode: TabIndicatorWidthMode,
        indicatorFixedWidth: Int,
        containerColor: Int,
        scrollable: Boolean,
        equalWidth: Boolean,
        itemSpacing: Int,
        itemPaddingHorizontal: Int,
        itemPaddingVertical: Int,
        minItemWidth: Int,
        submission: RetainedSessionSubmission = RetainedSessionSubmission.immediate(),
    ) {
        val itemLayoutStyleChanged =
            this.itemSpacingPx != itemSpacing ||
                this.minItemWidthPx != minItemWidth ||
                this.equalWidth != equalWidth
        this.indicatorColor = indicatorColor
        this.indicatorHeightPx = indicatorHeight
        this.indicatorCornerRadiusPx = indicatorCornerRadius
        this.indicatorPosition = indicatorPosition
        this.indicatorWidthMode = indicatorWidthMode
        this.indicatorFixedWidthPx = indicatorFixedWidth
        this.itemSpacingPx = itemSpacing
        this.minItemWidthPx = minItemWidth
        this.equalWidth = equalWidth

        tabContainer.indicatorColor = indicatorColor
        tabContainer.indicatorHeightPx = indicatorHeight
        tabContainer.indicatorCornerRadiusPx = indicatorCornerRadius
        tabContainer.indicatorPosition = indicatorPosition
        tabContainer.indicatorWidthMode = indicatorWidthMode
        tabContainer.indicatorFixedWidthPx = indicatorFixedWidth
        tabContainer.equalWidth = equalWidth
        tabContainer.itemSpacingPx = itemSpacing
        tabContainer.minItemWidthPx = minItemWidth

        if (containerColorState != containerColor) {
            containerColorState = containerColor
            setBackgroundColor(containerColor)
        }
        isScrollEnabled = scrollable

        // The parent is bound before eager child patches are applied. Preserve the requested logical
        // index here; TabRowContainer clamps it after the children have been laid out.
        val resolvedSelectedIndex = selectedIndex.coerceAtLeast(0)
        val selectedChanged = this.selectedIndex != resolvedSelectedIndex
        this.selectedIndex = resolvedSelectedIndex

        submission.publish {
            if (selectedChanged || itemLayoutStyleChanged) {
                scrollToSelectedTab(animate = true)
            }
        }

        observePagerState(pagerState)
        if (this.pagerState == null) {
            tabContainer.updateIndicatorPosition(this.selectedIndex, 0f)
        }
    }

    private var isScrollEnabled: Boolean = true

    override fun onInterceptTouchEvent(ev: android.view.MotionEvent?): Boolean {
        return if (isScrollEnabled) super.onInterceptTouchEvent(ev) else false
    }

    override fun onTouchEvent(ev: android.view.MotionEvent?): Boolean {
        return if (isScrollEnabled) super.onTouchEvent(ev) else false
    }

    private fun observePagerState(newState: PagerState?) {
        if (this.pagerState === newState) return
        val existingListener = pagerStateListener
        if (existingListener != null) {
            this.pagerState?.removeOnSnapshotChangedListener(existingListener)
        }
        pagerStateListener = null
        this.pagerState = newState
        if (newState != null) {
            val listener: (PagerStateSnapshot) -> Unit = { snapshot ->
                tabContainer.updateIndicatorPosition(snapshot.currentPage, snapshot.pageOffset)
            }
            pagerStateListener = listener
            newState.addOnSnapshotChangedListener(listener)
            tabContainer.updateIndicatorPosition(newState.currentPage, newState.pageOffset)
        }
    }

    private fun scrollToSelectedTab(
        animate: Boolean = true,
    ) {
        if (width == 0) {
            post { scrollToSelectedTab(animate) }
            return
        }
        val child = tabContainer.getChildAt(selectedIndex) ?: return
        val scrollTarget = child.left + child.width / 2 - width / 2
        val resolvedTarget = scrollTarget.coerceIn(
            minimumValue = 0,
            maximumValue = (tabContainer.width - width).coerceAtLeast(0),
        )
        if (scrollX == resolvedTarget) {
            return
        }
        if (animate) {
            smoothScrollTo(resolvedTarget, 0)
        } else {
            scrollTo(resolvedTarget, 0)
        }
    }

    fun dispose() {
        val listener = pagerStateListener
        if (listener != null) {
            pagerState?.removeOnSnapshotChangedListener(listener)
        }
        pagerStateListener = null
        pagerState = null
    }
}

/**
 * Internal horizontal container that hosts tab items and draws the indicator.
 */
internal class TabRowContainer(context: Context) : ViewGroup(context) {

    var indicatorColor: Int = 0
        set(value) {
            if (field == value) return
            field = value
            indicatorDrawable.setColor(value)
            invalidate()
        }
    var indicatorHeightPx: Int = 0
        set(value) {
            if (field == value) return
            field = value
            invalidate()
        }
    var indicatorCornerRadiusPx: Int = 0
        set(value) {
            if (field == value) return
            field = value
            indicatorDrawable.cornerRadius = value.toFloat()
            invalidate()
        }
    var indicatorPosition: TabIndicatorPosition = TabIndicatorPosition.Bottom
        set(value) {
            if (field == value) return
            field = value
            invalidate()
        }
    var indicatorWidthMode: TabIndicatorWidthMode = TabIndicatorWidthMode.MatchItem
        set(value) {
            if (field == value) return
            field = value
            updateIndicatorPosition(indicatorCurrentIndex, indicatorCurrentOffset)
        }
    var indicatorFixedWidthPx: Int = 0
        set(value) {
            if (field == value) return
            field = value
            if (indicatorWidthMode == TabIndicatorWidthMode.Fixed) {
                updateIndicatorPosition(indicatorCurrentIndex, indicatorCurrentOffset)
            }
        }
    var equalWidth: Boolean = true
        set(value) {
            if (field == value) return
            field = value
            requestLayout()
        }
    var itemSpacingPx: Int = 0
        set(value) {
            if (field == value) return
            field = value
            requestLayout()
        }
    var minItemWidthPx: Int = 0
        set(value) {
            if (field == value) return
            field = value
            requestLayout()
        }

    private val indicatorDrawable = GradientDrawable()
    private var indicatorLeft = 0f
    private var indicatorRight = 0f
    private var indicatorCurrentIndex = 0
    private var indicatorCurrentOffset = 0f

    init {
        setWillNotDraw(false)
        clipChildren = false
        clipToPadding = false
        indicatorDrawable.shape = GradientDrawable.RECTANGLE
        indicatorDrawable.setColor(indicatorColor)
    }

    fun updateIndicatorPosition(currentIndex: Int, offset: Float) {
        indicatorCurrentIndex = currentIndex
        indicatorCurrentOffset = offset
        if (childCount == 0) {
            clearIndicator()
            return
        }
        val safeIndex = currentIndex.coerceIn(0, childCount - 1)
        val safeOffset = offset.coerceIn(0f, 1f)
        val fromChild = getChildAt(safeIndex) ?: run {
            clearIndicator()
            return
        }
        val toIndex = (safeIndex + if (safeOffset > 0f) 1 else 0).coerceAtMost(childCount - 1)
        val toChild = getChildAt(toIndex) ?: fromChild

        val fromLeft: Float
        val fromRight: Float
        val toLeft: Float
        val toRight: Float

        when (indicatorWidthMode) {
            TabIndicatorWidthMode.MatchItem -> {
                fromLeft = fromChild.left.toFloat()
                fromRight = fromChild.right.toFloat()
                toLeft = toChild.left.toFloat()
                toRight = toChild.right.toFloat()
            }
            TabIndicatorWidthMode.Fixed -> {
                val halfFixed = indicatorFixedWidthPx / 2f
                val fromCenter = (fromChild.left + fromChild.right) / 2f
                val toCenter = (toChild.left + toChild.right) / 2f
                fromLeft = fromCenter - halfFixed
                fromRight = fromCenter + halfFixed
                toLeft = toCenter - halfFixed
                toRight = toCenter + halfFixed
            }
        }

        val nextLeft = lerp(fromLeft, toLeft, safeOffset)
        val nextRight = lerp(fromRight, toRight, safeOffset)
        if (indicatorLeft == nextLeft && indicatorRight == nextRight) {
            return
        }
        indicatorLeft = nextLeft
        indicatorRight = nextRight
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val parentWidth = MeasureSpec.getSize(widthMeasureSpec)
        val count = childCount
        if (count == 0) {
            setMeasuredDimension(0, 0)
            return
        }

        val totalSpacing = itemSpacingPx * (count - 1).coerceAtLeast(0)

        if (equalWidth && parentWidth > 0) {
            val itemWidth = ((parentWidth - totalSpacing) / count).coerceAtLeast(minItemWidthPx)
            var maxHeight = 0
            for (i in 0 until count) {
                val child = getChildAt(i)
                child.measure(
                    MeasureSpec.makeMeasureSpec(itemWidth, MeasureSpec.EXACTLY),
                    MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED),
                )
                maxHeight = maxOf(maxHeight, child.measuredHeight)
            }
            // Remeasure to a uniform height so differing content heights do not misalign indicators or hit areas.
            for (i in 0 until count) {
                val child = getChildAt(i)
                child.measure(
                    MeasureSpec.makeMeasureSpec(itemWidth, MeasureSpec.EXACTLY),
                    MeasureSpec.makeMeasureSpec(maxHeight, MeasureSpec.EXACTLY),
                )
            }
            val totalWidth = itemWidth * count + totalSpacing
            setMeasuredDimension(totalWidth, maxHeight)
        } else {
            var totalWidth = totalSpacing
            var maxHeight = 0
            for (i in 0 until count) {
                val child = getChildAt(i)
                child.measure(
                    MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED),
                    MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED),
                )
                val childWidth = child.measuredWidth.coerceAtLeast(minItemWidthPx)
                child.measure(
                    MeasureSpec.makeMeasureSpec(childWidth, MeasureSpec.EXACTLY),
                    MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED),
                )
                totalWidth += childWidth
                maxHeight = maxOf(maxHeight, child.measuredHeight)
            }
            // Keep a uniform height even with unconstrained label widths so scrollable TabRow height remains stable.
            for (i in 0 until count) {
                val child = getChildAt(i)
                child.measure(
                    MeasureSpec.makeMeasureSpec(child.measuredWidth, MeasureSpec.EXACTLY),
                    MeasureSpec.makeMeasureSpec(maxHeight, MeasureSpec.EXACTLY),
                )
            }
            setMeasuredDimension(totalWidth, maxHeight)
        }
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        if (layoutDirection == LAYOUT_DIRECTION_RTL) {
            var right = r - l
            for (i in 0 until childCount) {
                val child = getChildAt(i)
                child.layout(right - child.measuredWidth, 0, right, child.measuredHeight)
                right -= child.measuredWidth + itemSpacingPx
            }
        } else {
            var left = 0
            for (i in 0 until childCount) {
                val child = getChildAt(i)
                child.layout(left, 0, left + child.measuredWidth, child.measuredHeight)
                left += child.measuredWidth + itemSpacingPx
            }
        }
        if (childCount > 0) {
            // Recalculate from final child bounds so the first frame has a valid indicator.
            updateIndicatorPosition(indicatorCurrentIndex, indicatorCurrentOffset)
        }
    }

    override fun dispatchDraw(canvas: Canvas) {
        drawIndicator(canvas)
        super.dispatchDraw(canvas)
    }

    private fun drawIndicator(canvas: Canvas) {
        if (indicatorHeightPx <= 0 || childCount == 0 || indicatorRight <= indicatorLeft) return

        val top: Int
        val bottom: Int
        when (indicatorPosition) {
            TabIndicatorPosition.Bottom -> {
                bottom = height
                top = bottom - indicatorHeightPx
            }
            TabIndicatorPosition.Top -> {
                top = 0
                bottom = indicatorHeightPx
            }
        }

        indicatorDrawable.setBounds(
            indicatorLeft.toInt(),
            top,
            indicatorRight.toInt(),
            bottom,
        )
        indicatorDrawable.draw(canvas)
    }

    private fun clearIndicator() {
        if (indicatorLeft == 0f && indicatorRight == 0f) {
            return
        }
        indicatorLeft = 0f
        indicatorRight = 0f
        invalidate()
    }

    companion object {
        private fun lerp(start: Float, stop: Float, fraction: Float): Float {
            return start + (stop - start) * fraction
        }
    }
}
