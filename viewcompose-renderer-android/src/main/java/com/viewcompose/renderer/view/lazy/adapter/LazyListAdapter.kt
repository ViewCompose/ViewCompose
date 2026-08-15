package com.viewcompose.renderer.view.lazy.adapter

import android.util.Log
import android.graphics.Rect
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.viewcompose.ui.node.LazyListItem
import com.viewcompose.ui.node.policy.GridItemSpan
import com.viewcompose.ui.node.LazyListItemKind
import com.viewcompose.renderer.interop.asRenderContainerHandle
import com.viewcompose.renderer.reconcile.LazyListDiff
import com.viewcompose.renderer.reconcile.LazyListIdentityInspector
import com.viewcompose.renderer.view.lazy.focus.LazyFocusFollowLayoutMonitor
import com.viewcompose.renderer.view.lazy.reuse.MountedTreeReuseCache
import com.viewcompose.renderer.view.lazy.reuse.LazyPreparationCostTracker
import com.viewcompose.renderer.view.lazy.session.LazyHolderRegistry
import com.viewcompose.renderer.view.lazy.session.LazyItemSessionController
import com.viewcompose.renderer.view.lazy.state.UiLazyListConnector
import com.viewcompose.renderer.decoration.ViewDecorationHostLayout
import com.viewcompose.ui.state.LazyListState

/** Owns list diffing, physical holders, logical item sessions, and sticky-header presentations. */
internal class LazyListAdapter(
    private val orientation: Int = LinearLayoutManager.VERTICAL,
) : RecyclerView.Adapter<LazyListViewHolder>() {
    private companion object {
        private const val FOCUS_TAG = "UIFocusFollow"
    }

    private data class ScrollAnchor(
        val position: Int,
        val offset: Int,
    )

    private data class KeyIndex(
        val counts: Map<Any, Int>,
        val uniquePositions: Map<Any, Int>,
    )

    private var items: List<LazyListItem> = emptyList()
    private var keyIndex = KeyIndex(emptyMap(), emptyMap())
    // The registry centralizes holder lifecycle across attach, detach, recycle, and final disposal.
    private val mountedTreeCache = MountedTreeReuseCache()
    private val preparationCosts = LazyPreparationCostTracker()
    private val holderRegistry = LazyHolderRegistry<LazyListViewHolder>(::recycleHolder)
    private var lastIdentityWarning: String? = null
    private var attachedRecyclerView: RecyclerView? = null
    private val stableIds = linkedMapOf<Any, Long>()
    private val viewTypes = linkedMapOf<Any, Int>()
    private var nextStableId = 0L
    private var nextViewType = 1
    private var itemsVersion = 0L
    private var currentSubmissionRevision = 0L
    private var listState: LazyListState? = null
    private var stickyHeaderDisposer: (() -> Unit)? = null
    private var stickyHeaderPositions: IntArray = intArrayOf()

    init {
        setHasStableIds(true)
    }

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int,
    ): LazyListViewHolder {
        val container = ViewDecorationHostLayout(parent.context).apply {
            layoutParams = if (orientation == LinearLayoutManager.HORIZONTAL) {
                RecyclerView.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                )
            } else {
                RecyclerView.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                )
            }
        }
        return LazyListViewHolder(container)
    }

    override fun onBindViewHolder(
        holder: LazyListViewHolder,
        position: Int,
    ) {
        bindHolder(
            holder = holder,
            position = position,
            payload = null,
        )
    }

    override fun onBindViewHolder(
        holder: LazyListViewHolder,
        position: Int,
        payloads: MutableList<Any>,
    ) {
        bindHolder(
            holder = holder,
            position = position,
            payload = payloads.lastOrNull(),
        )
    }

    override fun onViewRecycled(holder: LazyListViewHolder) {
        holderRegistry.onRecycled(holder)
    }

    override fun onViewAttachedToWindow(holder: LazyListViewHolder) {
        super.onViewAttachedToWindow(holder)
        holderRegistry.onAttached(holder)
        // A staged or already-current holder has installed this exact submission. Rebinding here
        // would only revisit key lookup and the controller's duplicate-revision gate.
        if (!holder.activate(currentSubmissionRevision)) {
            refreshHolder(holder, currentSubmissionRevision)
        }
    }

    override fun onAttachedToRecyclerView(recyclerView: RecyclerView) {
        super.onAttachedToRecyclerView(recyclerView)
        attachedRecyclerView = recyclerView
    }

    override fun onViewDetachedFromWindow(holder: LazyListViewHolder) {
        super.onViewDetachedFromWindow(holder)
        holderRegistry.onDetached(holder)
    }

    override fun onDetachedFromRecyclerView(recyclerView: RecyclerView) {
        super.onDetachedFromRecyclerView(recyclerView)
        attachedRecyclerView = null
        disposeAll()
    }

    override fun getItemCount(): Int = items.size

    override fun getItemViewType(position: Int): Int {
        val item = items[position]
        val typeKey = item.kind to item.contentType
        return viewTypes.getOrPut(typeKey) { nextViewType++ }
    }

    override fun getItemId(position: Int): Long {
        val key = items[position].key
        if (keyIndex.uniquePositions[key] == null) return Long.MIN_VALUE + position
        return stableIds.getOrPut(key) { nextStableId++ }
    }

    fun itemKeyAt(position: Int): Any? = items.getOrNull(position)?.key

    fun itemContentTypeAt(position: Int): Any? = items.getOrNull(position)?.contentType

    fun itemSpanAt(position: Int): GridItemSpan =
        items.getOrNull(position)?.span ?: GridItemSpan.Single

    fun configureMountedTreeCache(size: Int) {
        mountedTreeCache.capacity = size
    }

    fun isStickyHeader(position: Int): Boolean {
        return items.getOrNull(position)?.kind == LazyListItemKind.StickyHeader
    }

    fun hasStickyHeaders(): Boolean = stickyHeaderPositions.isNotEmpty()

    fun findStickyHeaderPosition(itemPosition: Int): Int {
        if (itemPosition < 0 || items.isEmpty()) {
            return RecyclerView.NO_POSITION
        }
        val target = itemPosition.coerceAtMost(items.lastIndex)
        val insertion = stickyHeaderPositions.binarySearch(target)
        if (insertion >= 0) return stickyHeaderPositions[insertion]
        val preceding = -insertion - 2
        if (preceding >= 0) {
            return stickyHeaderPositions[preceding]
        }
        return RecyclerView.NO_POSITION
    }

    fun currentItemsVersion(): Long = itemsVersion

    fun createDetachedHolder(
        parent: ViewGroup,
        position: Int,
    ): LazyListViewHolder {
        return onCreateViewHolder(parent, getItemViewType(position)).also { holder ->
            holder.bind(
                item = items[position],
                submissionRevision = currentSubmissionRevision,
                position = position,
                active = true,
            )
        }
    }

    fun rebindDetachedHolder(
        holder: LazyListViewHolder,
        position: Int,
    ) {
        holder.bind(
            item = items[position],
            submissionRevision = currentSubmissionRevision,
            position = position,
            active = true,
        )
    }

    fun recycleDetachedHolder(holder: LazyListViewHolder) {
        recycleHolder(holder)
    }

    fun setStickyHeaderDisposer(disposer: (() -> Unit)?) {
        stickyHeaderDisposer = disposer
    }

    fun submitItems(
        items: List<LazyListItem>,
        submissionRevision: Long? = null,
    ): Boolean {
        warnAboutIdentityIssues(items)
        val previousItems = this.items
        if (previousItems == items) return false
        val previousKeyCounts = keyIndex.counts
        val revision = submissionRevision ?: (currentSubmissionRevision + 1L)
        if (revision <= currentSubmissionRevision) return false
        val result = LazyListDiff.calculate(
            previous = previousItems,
            next = items,
        )
        // When incremental diff is unavailable, preserve the first visible item anchor to reduce jump after notifyDataSetChanged.
        val reloadAnchor = if (result.diffResult == null) {
            captureScrollAnchor()
        } else {
            null
        }
        this.items = result.items
        keyIndex = buildKeyIndex(result.items)
        stickyHeaderPositions = buildStickyHeaderPositions(result.items)
        stableIds.keys.retainAll(keyIndex.counts.keys)
        currentSubmissionRevision = revision
        itemsVersion += 1
        if (result.diffResult != null) {
            result.diffResult.dispatchUpdatesTo(this)
        } else {
            notifyDataSetChanged()
            restoreScrollAnchor(reloadAnchor)
        }
        val reloadAll = result.diffResult == null
        val changedKeys = if (reloadAll) emptySet() else changedKeys(previousItems, result.items)
        refreshAttachedHolders(
            previousKeyCounts = previousKeyCounts,
            changedKeys = changedKeys,
            submissionRevision = revision,
            forceAll = reloadAll,
        )
        return true
    }

    fun bindState(
        recyclerView: RecyclerView,
        state: LazyListState?,
        mainAxisItemSpacing: Int,
    ) {
        if (listState !== state) {
            listState?.attach(null)
            listState = state
        }
        listState?.attach(
            UiLazyListConnector(
                recyclerView = recyclerView,
                mainAxisItemSpacing = mainAxisItemSpacing,
            ),
        )
    }

    private fun refreshAttachedHolders(
        previousKeyCounts: Map<Any, Int>,
        changedKeys: Set<Any>,
        submissionRevision: Long,
        forceAll: Boolean,
    ) {
        holderRegistry.forEachAttached { holder ->
            val boundKey = holder.boundItemKey
            if (!forceAll && boundKey !in changedKeys) return@forEachAttached
            val position = if (boundKey != null) {
                if (previousKeyCounts[boundKey] == 1 && keyIndex.counts[boundKey] == 1) {
                    keyIndex.uniquePositions[boundKey] ?: return@forEachAttached
                } else if (forceAll) {
                    holder.boundItemPosition
                } else {
                    return@forEachAttached
                }
            } else {
                holder.boundItemPosition
            }
            if (position !in items.indices) return@forEachAttached
            val nextItem = items[position]
            if (
                holder.boundContentType != nextItem.contentType ||
                holder.boundItemKind != nextItem.kind
            ) {
                // RecyclerView must replace structurally incompatible holders through its queued
                // change notification; rebinding the old holder would violate contentType reuse.
                return@forEachAttached
            }
            holder.bind(
                nextItem,
                submissionRevision = submissionRevision,
                position = position,
                active = true,
            )
        }
    }

    private fun changedKeys(
        previous: List<LazyListItem>,
        next: List<LazyListItem>,
    ): Set<Any> {
        val previousByKey = previous.associateBy(LazyListItem::key)
        val nextByKey = next.associateBy(LazyListItem::key)
        return (previousByKey.keys + nextByKey.keys).filterTo(linkedSetOf()) { key ->
            previousByKey[key] != nextByKey[key]
        }
    }

    private fun refreshHolder(
        holder: LazyListViewHolder,
        submissionRevision: Long,
    ) {
        val boundKey = holder.boundItemKey
        val position = if (boundKey != null) {
            keyIndex.uniquePositions[boundKey] ?: return
        } else {
            holder.boundItemPosition
        }
        if (position !in items.indices) return
        val nextItem = items[position]
        if (holder.boundContentType != nextItem.contentType || holder.boundItemKind != nextItem.kind) return
        holder.bind(
            nextItem,
            submissionRevision = submissionRevision,
            position = position,
            active = true,
        )
    }

    private fun warnAboutIdentityIssues(items: List<LazyListItem>) {
        val warning = LazyListIdentityInspector
            .analyze(items)
            .warningMessage(listName = "items")
        if (warning == null) {
            lastIdentityWarning = null
            return
        }
        if (warning == lastIdentityWarning) {
            return
        }
        lastIdentityWarning = warning
        Log.w("ViewCompose", warning)
    }

    fun disposeAll() {
        var failure: Throwable? = null
        val disposeStickyHeader = stickyHeaderDisposer
        stickyHeaderDisposer = null
        try {
            disposeStickyHeader?.invoke()
        } catch (disposeError: Throwable) {
            failure = disposeError
        }
        try {
            holderRegistry.disposeAll()
        } catch (disposeError: Throwable) {
            if (failure == null) failure = disposeError else failure.addSuppressed(disposeError)
        }
        try {
            mountedTreeCache.clear()
        } catch (releaseError: Throwable) {
            if (failure == null) failure = releaseError else failure.addSuppressed(releaseError)
        }
        preparationCosts.clear()
        try {
            listState?.attach(null)
        } catch (detachError: Throwable) {
            if (failure == null) failure = detachError else failure.addSuppressed(detachError)
        }
        listState = null
        items = emptyList()
        keyIndex = KeyIndex(emptyMap(), emptyMap())
        stickyHeaderPositions = intArrayOf()
        itemsVersion += 1
        failure?.let { throw it }
    }

    private fun buildKeyIndex(items: List<LazyListItem>): KeyIndex {
        val counts = HashMap<Any, Int>(items.size)
        val uniquePositions = HashMap<Any, Int>(items.size)
        items.forEachIndexed { position, item ->
            val key = item.key
            val nextCount = (counts[key] ?: 0) + 1
            counts[key] = nextCount
            if (nextCount == 1) {
                uniquePositions[key] = position
            } else {
                uniquePositions.remove(key)
            }
        }
        return KeyIndex(counts, uniquePositions)
    }

    private fun buildStickyHeaderPositions(items: List<LazyListItem>): IntArray {
        return items.indices
            .filter { position -> items[position].kind == LazyListItemKind.StickyHeader }
            .toIntArray()
    }

    private fun bindHolder(
        holder: LazyListViewHolder,
        position: Int,
        payload: Any?,
    ) {
        ensureContainerLayoutParams(holder)
        holderRegistry.onBound(holder)
        preparePhysicalPresentation(holder, items[position])
        val item = items[position]
        val reuseKey = MountedTreeReuseCache.ReuseKey(item.kind, item.contentType)
        val active = holderRegistry.isAttached(holder)
        val startedAt = if (active) System.nanoTime() else 0L
        holder.bind(
            item = item,
            payload = payload,
            submissionRevision = currentSubmissionRevision,
            position = position,
            active = active,
            prepare = !active && preparationCosts.shouldPrepare(reuseKey),
        )
        if (active) preparationCosts.record(reuseKey, System.nanoTime() - startedAt)
    }

    private fun preparePhysicalPresentation(
        holder: LazyListViewHolder,
        item: LazyListItem,
    ) {
        val nextKey = MountedTreeReuseCache.ReuseKey(item.kind, item.contentType)
        if (holder.hasBinding && holder.boundItemKey != item.key) {
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

    private fun recycleHolder(holder: LazyListViewHolder) {
        val reuseKey = holder.reuseKey()
        holder.detachForReuse()?.let { presentation ->
            if (reuseKey != null) {
                mountedTreeCache.offer(reuseKey, presentation)
            } else {
                presentation.release()
            }
        }
        holder.clearBinding()
    }

    private fun ensureContainerLayoutParams(holder: LazyListViewHolder) {
        val expectedWidth = if (orientation == LinearLayoutManager.HORIZONTAL) {
            ViewGroup.LayoutParams.WRAP_CONTENT
        } else {
            ViewGroup.LayoutParams.MATCH_PARENT
        }
        val expectedHeight = if (orientation == LinearLayoutManager.HORIZONTAL) {
            ViewGroup.LayoutParams.MATCH_PARENT
        } else {
            ViewGroup.LayoutParams.WRAP_CONTENT
        }
        val current = holder.itemView.layoutParams as? RecyclerView.LayoutParams
        if (current?.width == expectedWidth && current.height == expectedHeight) {
            return
        }
        holder.itemView.layoutParams = RecyclerView.LayoutParams(expectedWidth, expectedHeight)
    }

    private fun captureScrollAnchor(): ScrollAnchor? {
        val recyclerView = attachedRecyclerView ?: return null
        val layoutManager = recyclerView.layoutManager as? LinearLayoutManager ?: return null
        val position = layoutManager.findFirstVisibleItemPosition()
        if (position == RecyclerView.NO_POSITION) {
            return null
        }
        val anchorView = layoutManager.findViewByPosition(position)
        val offset = if (layoutManager.orientation == RecyclerView.HORIZONTAL) {
            (anchorView?.left ?: recyclerView.paddingLeft) - recyclerView.paddingLeft
        } else {
            (anchorView?.top ?: recyclerView.paddingTop) - recyclerView.paddingTop
        }
        return ScrollAnchor(position, offset)
    }

    private fun restoreScrollAnchor(anchor: ScrollAnchor?) {
        val recyclerView = attachedRecyclerView ?: return
        val layoutManager = recyclerView.layoutManager as? LinearLayoutManager ?: return
        if (anchor == null || itemCount == 0) {
            return
        }
        if (shouldDeferAnchorRestore(recyclerView, layoutManager)) {
            debugFocusLog {
                "defer anchor restore rv=${recyclerView.hashCode()} " +
                    "anchorPos=${anchor.position} anchorOffset=${anchor.offset} " +
                    "focused=${recyclerView.findFocus()?.javaClass?.simpleName}"
            }
            return
        }
        val targetPosition = anchor.position.coerceIn(0, itemCount - 1)
        recyclerView.post {
            debugFocusLog {
                "restore anchor rv=${recyclerView.hashCode()} " +
                    "targetPos=$targetPosition anchorOffset=${anchor.offset}"
            }
            layoutManager.scrollToPositionWithOffset(targetPosition, anchor.offset)
        }
    }

    private fun shouldDeferAnchorRestore(
        recyclerView: RecyclerView,
        layoutManager: LinearLayoutManager,
    ): Boolean {
        if (!layoutManager.canScrollVertically()) {
            return false
        }
        if (!LazyFocusFollowLayoutMonitor.isEnabled(recyclerView)) {
            return false
        }
        return recyclerView.findFocus() != null
    }

    private inline fun debugFocusLog(message: () -> String) {
        if (!Log.isLoggable(FOCUS_TAG, Log.DEBUG)) {
            return
        }
        Log.d(FOCUS_TAG, message())
    }
}

/** Adds main-axis spacing before non-first items without changing list-edge padding. */
internal class LazyListSpacingDecoration(
    private var spacing: Int,
    private var orientation: Int = LinearLayoutManager.VERTICAL,
) : RecyclerView.ItemDecoration() {
    fun update(
        spacing: Int,
        orientation: Int,
    ): Boolean {
        if (this.spacing == spacing && this.orientation == orientation) {
            return false
        }
        this.spacing = spacing
        this.orientation = orientation
        return true
    }

    override fun getItemOffsets(
        outRect: Rect,
        view: android.view.View,
        parent: RecyclerView,
        state: RecyclerView.State,
    ) {
        if (spacing <= 0) {
            outRect.setEmpty()
            return
        }
        val position = parent.getChildAdapterPosition(view)
        if (position == RecyclerView.NO_POSITION) {
            outRect.setEmpty()
            return
        }
        if (position == 0) {
            outRect.setEmpty()
            return
        }
        if (orientation == LinearLayoutManager.HORIZONTAL) {
            outRect.left = spacing
        } else {
            outRect.top = spacing
        }
    }
}

/** Physical holder shell that delegates logical ownership to an isolated item-session controller. */
internal class LazyListViewHolder(
    private val container: FrameLayout,
) : RecyclerView.ViewHolder(container) {
    var hasBinding: Boolean = false
        private set
    var boundItemKey: Any? = null
        private set
    var boundItemPosition: Int = RecyclerView.NO_POSITION
        private set
    var boundContentType: Any? = null
        private set
    var boundItemKind: LazyListItemKind? = null
        private set
    val hasPendingPresentation: Boolean
        get() = controller.hasPendingPresentation

    private val controller = LazyItemSessionController(
        createSession = { item ->
            item.sessionFactory.create(container.asRenderContainerHandle())
        },
        clearContainer = container::removeAllViews,
    )

    fun bind(
        item: LazyListItem,
        payload: Any? = null,
        submissionRevision: Long,
        position: Int,
        active: Boolean,
        prepare: Boolean = true,
    ) {
        hasBinding = true
        boundItemKey = item.key
        boundItemPosition = position
        boundContentType = item.contentType
        boundItemKind = item.kind
        if (active) {
            controller.bind(
                item = item,
                payload = payload,
                submissionRevision = submissionRevision,
            )
        } else if (prepare) {
            controller.prepare(
                item = item,
                payload = payload,
                submissionRevision = submissionRevision,
            )
        } else {
            controller.stage(
                item = item,
                payload = payload,
                submissionRevision = submissionRevision,
            )
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
        val kind = boundItemKind ?: return null
        return MountedTreeReuseCache.ReuseKey(kind, boundContentType)
    }

    fun clearBinding() {
        hasBinding = false
        boundItemKey = null
        boundItemPosition = RecyclerView.NO_POSITION
        boundContentType = null
        boundItemKind = null
    }

    fun activate(submissionRevision: Long): Boolean {
        controller.commit(submissionRevision)
        return controller.hasCommitted(submissionRevision)
    }
}
