package com.viewcompose.renderer.view.container

import android.content.Context
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.viewcompose.ui.node.LazyListItem
import com.viewcompose.ui.node.LazyListItemKind
import com.viewcompose.renderer.interop.asRenderContainerHandle
import com.viewcompose.ui.tooling.UiSourceSessionRole
import com.viewcompose.renderer.reconcile.LazyListDiff
import com.viewcompose.renderer.view.lazy.session.LazyHolderRegistry
import com.viewcompose.renderer.view.lazy.session.LazyItemSessionController
import com.viewcompose.ui.state.PagerConnector
import com.viewcompose.ui.state.PagerState
import com.viewcompose.renderer.view.lazy.reuse.FrameworkRecyclerViewDefaults
import com.viewcompose.renderer.view.tree.LayoutPassTracker
import com.viewcompose.renderer.view.tree.RetainedSessionSubmission
import com.viewcompose.renderer.decoration.ViewDecorationHostLayout

/**
 * Android ViewPager2 host for a horizontal pager.
 * Android ViewPager2 host for horizontal Pager.
 *
 * Binds declarative pages to the ViewPager2 adapter and synchronizes PagerState and callbacks.
 * It binds declarative page items to the ViewPager2 adapter and synchronizes PagerState with callbacks.
 */
internal class DeclarativeHorizontalPagerLayout(
    context: Context,
) : FrameLayout(context) {
    private val viewPager = ViewPager2(context)
    private val adapter = HorizontalPagerAdapter()
    private var onPageChanged: ((Int) -> Unit)? = null
    private var pagerState: PagerState? = null
    // Suppress external callbacks during programmatic selection to avoid a state write-back loop.
    // Suppress external callbacks during programmatic page changes to avoid feedback loops.
    private var suppressCallback: Boolean = false
    private val pageChangeCallback = object : ViewPager2.OnPageChangeCallback() {
        override fun onPageSelected(position: Int) {
            pagerState?.updateFromPager(
                currentPage = position,
                pageOffset = 0f,
            )
            if (!suppressCallback) {
                onPageChanged?.invoke(position)
            }
        }

        override fun onPageScrolled(
            position: Int,
            positionOffset: Float,
            positionOffsetPixels: Int,
        ) {
            pagerState?.updateFromPager(
                currentPage = position,
                pageOffset = positionOffset,
            )
        }
    }

    init {
        viewPager.layoutParams = LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT,
        )
        viewPager.adapter = adapter
        viewPager.registerOnPageChangeCallback(pageChangeCallback)
        applyRecyclerDefaults()
        addView(viewPager)
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
        pages: List<LazyListItem>,
        currentPage: Int,
        onPageChanged: ((Int) -> Unit)?,
        offscreenPageLimit: Int,
        pagerState: PagerState?,
        userScrollEnabled: Boolean,
        submission: RetainedSessionSubmission = RetainedSessionSubmission.immediate(),
    ) {
        this.onPageChanged = onPageChanged
        if (this.pagerState !== pagerState) {
            this.pagerState?.attach(null)
        }
        this.pagerState = pagerState
        pagerState?.attach(
            object : PagerConnector {
                override fun scrollToPage(page: Int) {
                    viewPager.setCurrentItem(page, true)
                }
            },
        )
        viewPager.offscreenPageLimit = offscreenPageLimit.coerceAtLeast(1)
        viewPager.isUserInputEnabled = userScrollEnabled
        submission.publish {
            if (!adapter.submitPages(pages, submission.revision)) return@publish
            if (pages.isEmpty()) return@publish
            val resolvedPage = currentPage.coerceIn(0, pages.lastIndex)
            if (viewPager.currentItem != resolvedPage) {
                suppressCallback = true
                viewPager.setCurrentItem(resolvedPage, false)
                suppressCallback = false
            }
            pagerState?.updateFromPager(
                currentPage = viewPager.currentItem,
                pageOffset = 0f,
            )
        }
    }

    fun dispose() {
        pagerState?.attach(null)
        viewPager.unregisterOnPageChangeCallback(pageChangeCallback)
        adapter.disposeAll()
    }

    fun applyRecyclerDefaults(
        sharePool: Boolean = false,
        disableItemAnimator: Boolean = false,
        animateInsert: Boolean = true,
        animateRemove: Boolean = true,
        animateMove: Boolean = true,
        animateChange: Boolean = true,
    ) {
        resolvePagerRecyclerView()?.let { recyclerView ->
            FrameworkRecyclerViewDefaults.applyHorizontalPagerDefaults(
                recyclerView = recyclerView,
                sharePool = sharePool,
                disableItemAnimator = disableItemAnimator,
                animateInsert = animateInsert,
                animateRemove = animateRemove,
                animateMove = animateMove,
                animateChange = animateChange,
            )
        }
    }

    private fun resolvePagerRecyclerView(): RecyclerView? {
        return viewPager.getChildAt(0) as? RecyclerView
    }
}

/**
 * Pager adapter that reuses LazyListItem session creation and diffing.
 * Pager page adapter reusing LazyListItem session creation and diff logic.
 */
internal class HorizontalPagerAdapter : RecyclerView.Adapter<HorizontalPagerViewHolder>() {
    private var pages: List<LazyListItem> = emptyList()
    private var keyCounts: Map<Any, Int> = emptyMap()
    private val stableIds = linkedMapOf<Any, Long>()
    private val viewTypes = linkedMapOf<Pair<LazyListItemKind, Any?>, Int>()
    private var nextStableId = 0L
    private var nextViewType = 1
    private var currentSubmissionRevision = 0L
    private val holderRegistry = LazyHolderRegistry<HorizontalPagerViewHolder> { holder ->
        holder.recycle()
    }

    init {
        setHasStableIds(true)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HorizontalPagerViewHolder {
        val container = ViewDecorationHostLayout(parent.context).apply {
            layoutParams = RecyclerView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            )
        }
        return HorizontalPagerViewHolder(container)
    }

    override fun onBindViewHolder(holder: HorizontalPagerViewHolder, position: Int) {
        bindHolder(
            holder = holder,
            position = position,
            payload = null,
        )
    }

    override fun onBindViewHolder(
        holder: HorizontalPagerViewHolder,
        position: Int,
        payloads: MutableList<Any>,
    ) {
        bindHolder(
            holder = holder,
            position = position,
            payload = payloads.lastOrNull(),
        )
    }

    override fun onViewAttachedToWindow(holder: HorizontalPagerViewHolder) {
        super.onViewAttachedToWindow(holder)
        holderRegistry.onAttached(holder)
        holder.activate(currentSubmissionRevision)
        refreshHolder(holder, currentSubmissionRevision)
    }

    override fun onViewDetachedFromWindow(holder: HorizontalPagerViewHolder) {
        super.onViewDetachedFromWindow(holder)
        holderRegistry.onDetached(holder)
    }

    override fun onViewRecycled(holder: HorizontalPagerViewHolder) {
        holderRegistry.onRecycled(holder)
    }

    override fun getItemCount(): Int = pages.size

    override fun getItemViewType(position: Int): Int {
        val page = pages[position]
        return viewTypes.getOrPut(page.kind to page.contentType) { nextViewType++ }
    }

    override fun getItemId(position: Int): Long {
        val key = pages[position].key ?: return Long.MIN_VALUE + position
        if (keyCounts[key] != 1) return Long.MIN_VALUE + position
        return stableIds.getOrPut(key) { nextStableId++ }
    }

    fun submitPages(
        newPages: List<LazyListItem>,
        submissionRevision: Long? = null,
    ): Boolean {
        val revision = submissionRevision ?: (currentSubmissionRevision + 1L)
        if (revision <= currentSubmissionRevision) return false
        val previousKeyCounts = keyCounts
        val result = LazyListDiff.calculate(
            previous = this.pages,
            next = newPages,
        )
        this.pages = result.items
        keyCounts = pages.mapNotNull(LazyListItem::key).groupingBy { key -> key }.eachCount()
        stableIds.keys.retainAll(keyCounts.keys)
        currentSubmissionRevision = revision
        if (result.diffResult != null) {
            result.diffResult.dispatchUpdatesTo(this)
        } else {
            notifyDataSetChanged()
        }
        holderRegistry.forEachAttached { holder ->
            val key = holder.boundPageKey
            val position = if (key != null) {
                if (previousKeyCounts[key] != 1 || keyCounts[key] != 1) {
                    return@forEachAttached
                }
                pages.indexOfFirst { page -> page.key == key }
            } else {
                holder.boundPagePosition
            }
            if (position in pages.indices) {
                val nextPage = pages[position]
                if (
                    holder.boundContentType == nextPage.contentType &&
                    holder.boundPageKind == nextPage.kind
                ) {
                    holder.bind(
                        item = nextPage,
                        submissionRevision = revision,
                        position = position,
                    )
                }
            }
        }
        return true
    }

    fun disposeAll() {
        holderRegistry.disposeAll()
        pages = emptyList()
        keyCounts = emptyMap()
        notifyDataSetChanged()
    }

    private fun bindHolder(
        holder: HorizontalPagerViewHolder,
        position: Int,
        payload: Any?,
    ) {
        holderRegistry.onBound(holder)
        holder.bind(
            item = pages[position],
            payload = payload,
            submissionRevision = currentSubmissionRevision,
            position = position,
            active = holderRegistry.isAttached(holder),
        )
    }

    private fun refreshHolder(
        holder: HorizontalPagerViewHolder,
        submissionRevision: Long,
    ) {
        val key = holder.boundPageKey
        val position = when {
            key != null && keyCounts[key] == 1 -> pages.indexOfFirst { page -> page.key == key }
            key == null -> holder.boundPagePosition
            else -> return
        }
        if (position !in pages.indices) return
        val nextPage = pages[position]
        if (holder.boundContentType != nextPage.contentType || holder.boundPageKind != nextPage.kind) return
        holder.bind(
            item = nextPage,
            submissionRevision = submissionRevision,
            position = position,
            active = true,
        )
    }
}

/**
 * Holder that renders one horizontal-pager page into an isolated FrameLayout.
 * Holder for one horizontal Pager page, rendering page content into an isolated FrameLayout.
 */
internal class HorizontalPagerViewHolder(
    private val container: FrameLayout,
) : RecyclerView.ViewHolder(container) {
    var boundPageKey: Any? = null
        private set
    var boundPagePosition: Int = RecyclerView.NO_POSITION
        private set
    var boundContentType: Any? = null
        private set
    var boundPageKind: LazyListItemKind? = null
        private set
    private val controller = LazyItemSessionController(
        createSession = { item ->
            item.sessionFactory.create(
                container.asRenderContainerHandle(UiSourceSessionRole.Page),
            )
        },
        clearContainer = container::removeAllViews,
    )

    fun bind(
        item: LazyListItem,
        payload: Any? = null,
        submissionRevision: Long,
        position: Int,
        active: Boolean = true,
    ) {
        boundPageKey = item.key
        boundPagePosition = position
        boundContentType = item.contentType
        boundPageKind = item.kind
        if (active) {
            controller.bind(item, payload, submissionRevision)
        } else {
            controller.stage(item, payload, submissionRevision)
        }
    }

    fun recycle() {
        boundPageKey = null
        boundPagePosition = RecyclerView.NO_POSITION
        boundContentType = null
        boundPageKind = null
        controller.recycle()
    }

    fun activate(submissionRevision: Long) {
        controller.commit(submissionRevision)
    }
}
