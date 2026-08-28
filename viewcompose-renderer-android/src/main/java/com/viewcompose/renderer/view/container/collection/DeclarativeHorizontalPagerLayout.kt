package com.viewcompose.renderer.view.container

import android.content.Context
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.viewcompose.renderer.R
import com.viewcompose.ui.node.LazyListItem
import com.viewcompose.ui.node.LazyListItemKind
import com.viewcompose.ui.node.LazyListItemSession
import com.viewcompose.renderer.interop.asRenderContainerHandle
import com.viewcompose.renderer.reconcile.LazyListDiff
import com.viewcompose.renderer.view.lazy.session.LazyHolderRegistry
import com.viewcompose.renderer.view.lazy.session.LazyItemSessionController
import com.viewcompose.renderer.view.lazy.session.LazyItemSessionHost
import com.viewcompose.ui.state.PagerState
import com.viewcompose.renderer.view.lazy.reuse.FrameworkRecyclerViewDefaults
import com.viewcompose.renderer.view.lazy.reuse.MountedTreeReuseCache
import com.viewcompose.renderer.view.tree.LayoutPassTracker
import com.viewcompose.renderer.view.tree.RetainedSessionSubmission

/**
 * Android RecyclerView host for a horizontal pager.
 *
 * Binds declarative pages to the snapping viewport and synchronizes PagerState and callbacks.
 */
internal class DeclarativeHorizontalPagerLayout(
    context: Context,
) : FrameLayout(context) {
    private val pagerViewport = DeclarativePagerRecyclerView(
        context = context,
        orientation = LinearLayoutManager.HORIZONTAL,
    )
    private val adapter = HorizontalPagerAdapter()
    private var onPageChanged: ((Int) -> Unit)? = null
    private var pagerState: PagerState? = null
    private val stateCoordinator = PagerStateCoordinator(
        currentViewportPage = pagerViewport::currentPage,
        moveViewportToPage = pagerViewport::moveToPage,
        pageCount = adapter::getItemCount,
        onSettledPageChanged = { onPageChanged },
    )

    init {
        pagerViewport.layoutParams = LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT,
        )
        pagerViewport.adapter = adapter
        pagerViewport.viewportListener = stateCoordinator
        applyRecyclerDefaults()
        addView(pagerViewport)
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
        mountedTreeCacheSize: Int,
        submission: RetainedSessionSubmission = RetainedSessionSubmission.immediate(),
    ) {
        this.onPageChanged = onPageChanged
        if (this.pagerState !== pagerState) {
            this.pagerState?.attach(null)
        }
        this.pagerState = pagerState
        pagerState?.attach(stateCoordinator)
        pagerViewport.setOffscreenPageLimit(offscreenPageLimit)
        pagerViewport.setUserScrollEnabled(userScrollEnabled)
        adapter.configureMountedTreeCache(mountedTreeCacheSize)
        submission.publish {
            if (adapter.submitPages(pages, submission.revision) == PagerSubmissionResult.Rejected) {
                return@publish
            }
            stateCoordinator.onPageCountChanged()
            if (pages.isNotEmpty()) {
                stateCoordinator.applyControlledPage(currentPage)
            }
        }
    }

    fun dispose() {
        pagerState?.attach(null)
        pagerViewport.release()
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
        FrameworkRecyclerViewDefaults.applyHorizontalPagerDefaults(
            recyclerView = pagerViewport,
            sharePool = sharePool,
            disableItemAnimator = disableItemAnimator,
            animateInsert = animateInsert,
            animateRemove = animateRemove,
            animateMove = animateMove,
            animateChange = animateChange,
        )
    }
}

/**
 * Pager page adapter reusing LazyListItem session creation and diff logic.
 */
internal class HorizontalPagerAdapter : RecyclerView.Adapter<HorizontalPagerViewHolder>() {
    private var pages: List<LazyListItem> = emptyList()
    private var keyCounts: Map<Any, Int> = emptyMap()
    private var uniqueKeyPositions: Map<Any, Int> = emptyMap()
    private val stableIds = linkedMapOf<Any, Long>()
    private val viewTypes = linkedMapOf<Pair<LazyListItemKind, Any?>, Int>()
    private var nextStableId = 0L
    private var nextViewType = 1
    private var currentSubmissionRevision = 0L
    private val mountedTreeCache = MountedTreeReuseCache()
    private val holderRegistry = LazyHolderRegistry<HorizontalPagerViewHolder> { holder ->
        recycleHolder(holder)
    }

    init {
        setHasStableIds(true)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HorizontalPagerViewHolder {
        val container = PagerPageHostLayout(parent.context).apply {
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
        if (!holder.activate(currentSubmissionRevision)) {
            refreshHolder(holder, currentSubmissionRevision)
        }
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
        val key = pages[position].key
        if (keyCounts[key] != 1) return Long.MIN_VALUE + position
        return stableIds.getOrPut(key) { nextStableId++ }
    }

    fun configureMountedTreeCache(size: Int) {
        mountedTreeCache.capacity = size
    }

    fun submitPages(
        newPages: List<LazyListItem>,
        submissionRevision: Long? = null,
    ): PagerSubmissionResult {
        val revision = submissionRevision ?: (currentSubmissionRevision + 1L)
        if (revision <= currentSubmissionRevision) return PagerSubmissionResult.Rejected
        if (pages == newPages) {
            currentSubmissionRevision = revision
            return PagerSubmissionResult.Unchanged
        }
        val previousPages = pages
        val previousKeyCounts = keyCounts
        val result = LazyListDiff.calculate(
            previous = this.pages,
            next = newPages,
        )
        this.pages = result.items
        val keyIndex = buildKeyIndex(pages)
        keyCounts = keyIndex.first
        uniqueKeyPositions = keyIndex.second
        stableIds.keys.retainAll(keyCounts.keys)
        currentSubmissionRevision = revision
        if (result.diffResult != null) {
            result.diffResult.dispatchUpdatesTo(this)
        } else {
            notifyDataSetChanged()
        }
        val reloadAll = result.diffResult == null
        val previousByKey = if (reloadAll) emptyMap() else previousPages.associateBy(LazyListItem::key)
        val nextByKey = if (reloadAll) emptyMap() else pages.associateBy(LazyListItem::key)
        val changedKeys = if (reloadAll) {
            emptySet()
        } else {
            (previousByKey.keys + nextByKey.keys).filterTo(linkedSetOf()) { key ->
                previousByKey[key] != nextByKey[key]
            }
        }
        holderRegistry.forEachAttached { holder ->
            val key = holder.boundPageKey
            if (!reloadAll && key !in changedKeys) return@forEachAttached
            val position = if (key != null) {
                if (previousKeyCounts[key] != 1 || keyCounts[key] != 1) {
                    if (reloadAll) holder.boundPagePosition else return@forEachAttached
                } else {
                    uniqueKeyPositions[key] ?: return@forEachAttached
                }
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
        return PagerSubmissionResult.Changed
    }

    fun disposeAll() {
        var failure: Throwable? = null
        try {
            holderRegistry.disposeAll()
        } catch (disposeError: Throwable) {
            failure = disposeError
        }
        try {
            mountedTreeCache.clear()
        } catch (releaseError: Throwable) {
            if (failure == null) failure = releaseError else failure.addSuppressed(releaseError)
        }
        pages = emptyList()
        keyCounts = emptyMap()
        uniqueKeyPositions = emptyMap()
        notifyDataSetChanged()
        failure?.let { throw it }
    }

    private fun bindHolder(
        holder: HorizontalPagerViewHolder,
        position: Int,
        payload: Any?,
    ) {
        holderRegistry.onBound(holder)
        preparePhysicalPresentation(holder, pages[position])
        holder.bind(
            item = pages[position],
            payload = payload,
            submissionRevision = currentSubmissionRevision,
            position = position,
            active = holderRegistry.isAttached(holder),
        )
    }

    private fun preparePhysicalPresentation(
        holder: HorizontalPagerViewHolder,
        item: LazyListItem,
    ) {
        val nextKey = MountedTreeReuseCache.ReuseKey(item.kind, item.contentType)
        if (holder.hasBinding && holder.boundPageKey != item.key) {
            val previousKey = holder.reuseKey()
            holder.detachForReuse()?.let { presentation ->
                if (previousKey == nextKey) {
                    holder.adoptForNextSession(presentation)
                } else if (previousKey != null) {
                    mountedTreeCache.offer(previousKey, presentation)
                } else {
                    presentation.release()
                }
            }
            holder.clearBinding()
        }
        if (!holder.hasBinding && !holder.hasPendingPresentation) {
            mountedTreeCache.take(nextKey)?.let(holder::adoptForNextSession)
        }
    }

    private fun recycleHolder(holder: HorizontalPagerViewHolder) {
        val reuseKey = holder.reuseKey()
        holder.detachForReuse()?.let { presentation ->
            if (reuseKey != null) mountedTreeCache.offer(reuseKey, presentation)
            else presentation.release()
        }
        holder.clearBinding()
    }

    private fun refreshHolder(
        holder: HorizontalPagerViewHolder,
        submissionRevision: Long,
    ) {
        val key = holder.boundPageKey
        val position = when {
            key != null && keyCounts[key] == 1 -> uniqueKeyPositions[key] ?: return
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

    private fun buildKeyIndex(items: List<LazyListItem>): Pair<Map<Any, Int>, Map<Any, Int>> {
        val counts = HashMap<Any, Int>(items.size)
        val positions = HashMap<Any, Int>(items.size)
        items.forEachIndexed { position, item ->
            val nextCount = (counts[item.key] ?: 0) + 1
            counts[item.key] = nextCount
            if (nextCount == 1) positions[item.key] = position else positions.remove(item.key)
        }
        return counts to positions
    }
}

/** Distinguishes a stale pager submission from an accepted snapshot that needs no adapter diff. */
internal enum class PagerSubmissionResult {
    Rejected,
    Unchanged,
    Changed,
}

/**
 * Holder that renders one horizontal-pager page into an isolated FrameLayout.
 * Holder for one horizontal Pager page, rendering page content into an isolated FrameLayout.
 */
internal class HorizontalPagerViewHolder(
    private val container: FrameLayout,
) : RecyclerView.ViewHolder(container), LazyItemSessionHost {
    var hasBinding: Boolean = false
        private set
    var boundPageKey: Any? = null
        private set
    var boundPagePosition: Int = RecyclerView.NO_POSITION
        private set
    var boundContentType: Any? = null
        private set
    var boundPageKind: LazyListItemKind? = null
        private set
    val hasPendingPresentation: Boolean
        get() = controller.hasPendingPresentation
    private val renderContainer = container.asRenderContainerHandle()
    private val controller = LazyItemSessionController(this)

    override fun createSession(item: LazyListItem): LazyListItemSession {
        return item.createSession(renderContainer)
    }

    override fun clearContainer() {
        container.removeAllViews()
    }

    override fun beginLogicalOwnerTransfer() {
        container.setTag(R.id.viewcompose_lazy_logical_owner_transfer, true)
    }

    override fun endLogicalOwnerTransfer() {
        container.setTag(R.id.viewcompose_lazy_logical_owner_transfer, null)
    }

    fun bind(
        item: LazyListItem,
        payload: Any? = null,
        submissionRevision: Long,
        position: Int,
        active: Boolean = true,
    ) {
        hasBinding = true
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
        controller.recycle()
        clearBinding()
    }

    fun detachForReuse() = controller.detachForReuse()

    fun adoptForNextSession(presentation: com.viewcompose.ui.node.ReusableItemPresentation) {
        controller.adoptForNextSession(presentation)
    }

    fun reuseKey(): MountedTreeReuseCache.ReuseKey? {
        val kind = boundPageKind ?: return null
        return MountedTreeReuseCache.ReuseKey(kind, boundContentType)
    }

    fun clearBinding() {
        hasBinding = false
        boundPageKey = null
        boundPagePosition = RecyclerView.NO_POSITION
        boundContentType = null
        boundPageKind = null
    }

    fun activate(submissionRevision: Long): Boolean {
        controller.commit(submissionRevision)
        return controller.hasCommitted(submissionRevision)
    }
}
