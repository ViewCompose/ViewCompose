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
import com.viewcompose.renderer.view.lazy.focus.LazyFocusFollowLayoutMonitor
import com.viewcompose.renderer.view.lazy.session.LazyHolderRegistry
import com.viewcompose.renderer.view.lazy.session.LazyItemSessionController
import com.viewcompose.ui.state.PagerConnector
import com.viewcompose.ui.state.PagerState
import com.viewcompose.renderer.view.lazy.reuse.FrameworkRecyclerViewDefaults
import com.viewcompose.renderer.view.tree.LayoutPassTracker
import com.viewcompose.renderer.view.tree.RetainedSessionSubmission
import com.viewcompose.renderer.decoration.ViewDecorationHostLayout

/**
 * Android ViewPager2 host for a vertical pager.
 * Android ViewPager2 host for vertical Pager.
 *
 * Synchronizes page state and applies keyboard focus-follow behavior to the underlying RecyclerView.
 * Besides page state synchronization, it applies keyboard focus-follow policy to the backing RecyclerView.
 */
internal class DeclarativeVerticalPagerLayout(
    context: Context,
) : FrameLayout(context) {
    private val viewPager = ViewPager2(context)
    private val adapter = VerticalPagerAdapter()
    private var onPageChanged: ((Int) -> Unit)? = null
    private var pagerState: PagerState? = null
    // Suppress onPageChanged during programmatic selection to avoid bind-to-ViewPager2-to-state feedback.
    // Temporarily block onPageChanged during programmatic paging to avoid duplicate bind -> ViewPager2 -> state notifications.
    private var suppressCallback: Boolean = false
    private var focusFollowKeyboardEnabled: Boolean = false
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
        viewPager.orientation = ViewPager2.ORIENTATION_VERTICAL
        viewPager.adapter = adapter
        viewPager.registerOnPageChangeCallback(pageChangeCallback)
        applyRecyclerDefaults()
        applyFocusFollowPolicy()
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
        applyFocusFollowPolicy()
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
        applyFocusFollowPolicy(enabled = false)
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
            FrameworkRecyclerViewDefaults.applyVerticalPagerDefaults(
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

    fun setFocusFollowKeyboardEnabled(enabled: Boolean) {
        focusFollowKeyboardEnabled = enabled
        applyFocusFollowPolicy()
    }

    private fun applyFocusFollowPolicy(enabled: Boolean = focusFollowKeyboardEnabled) {
        resolvePagerRecyclerView()?.let { recyclerView ->
            LazyFocusFollowLayoutMonitor.apply(
                recyclerView = recyclerView,
                enabled = enabled,
            )
        }
    }

    private fun resolvePagerRecyclerView(): RecyclerView? {
        return viewPager.getChildAt(0) as? RecyclerView
    }
}

/**
 * Vertical-pager adapter that preserves session lifecycle during page reuse.
 * Page adapter for vertical Pager, keeping session lifetimes correct across page reuse.
 */
internal class VerticalPagerAdapter : RecyclerView.Adapter<VerticalPagerViewHolder>() {
    private var pages: List<LazyListItem> = emptyList()
    private var keyCounts: Map<Any, Int> = emptyMap()
    private val stableIds = linkedMapOf<Any, Long>()
    private val viewTypes = linkedMapOf<Pair<LazyListItemKind, Any?>, Int>()
    private var nextStableId = 0L
    private var nextViewType = 1
    private var currentSubmissionRevision = 0L
    private val holderRegistry = LazyHolderRegistry<VerticalPagerViewHolder> { holder ->
        holder.recycle()
    }

    init {
        setHasStableIds(true)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VerticalPagerViewHolder {
        val container = ViewDecorationHostLayout(parent.context).apply {
            layoutParams = RecyclerView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            )
        }
        return VerticalPagerViewHolder(container)
    }

    override fun onBindViewHolder(holder: VerticalPagerViewHolder, position: Int) {
        bindHolder(
            holder = holder,
            position = position,
            payload = null,
        )
    }

    override fun onBindViewHolder(
        holder: VerticalPagerViewHolder,
        position: Int,
        payloads: MutableList<Any>,
    ) {
        bindHolder(
            holder = holder,
            position = position,
            payload = payloads.lastOrNull(),
        )
    }

    override fun onViewAttachedToWindow(holder: VerticalPagerViewHolder) {
        super.onViewAttachedToWindow(holder)
        holderRegistry.onAttached(holder)
        holder.activate(currentSubmissionRevision)
        refreshHolder(holder, currentSubmissionRevision)
    }

    override fun onViewDetachedFromWindow(holder: VerticalPagerViewHolder) {
        super.onViewDetachedFromWindow(holder)
        holderRegistry.onDetached(holder)
    }

    override fun onViewRecycled(holder: VerticalPagerViewHolder) {
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
        holder: VerticalPagerViewHolder,
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
        holder: VerticalPagerViewHolder,
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
 * Holder that binds one declarative vertical-pager page to an Android container.
 * Holder for one vertical Pager page, binding declarative page content to an Android container.
 */
internal class VerticalPagerViewHolder(
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
