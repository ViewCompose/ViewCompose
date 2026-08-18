package com.viewcompose.renderer.view.lazy.adapter

import android.util.Log
import android.graphics.Rect
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.annotation.DoNotInline
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.viewcompose.ui.node.LazyListItem
import com.viewcompose.ui.node.LazyListItemSession
import com.viewcompose.ui.node.policy.GridItemSpan
import com.viewcompose.ui.node.LazyListItemKind
import com.viewcompose.renderer.interop.asRenderContainerHandle
import com.viewcompose.renderer.reconcile.LazyListAdapterChangedPayload
import com.viewcompose.renderer.reconcile.LazyListAdapterUpdatePlan
import com.viewcompose.renderer.reconcile.LazyListDiff
import com.viewcompose.renderer.reconcile.LazyListIdentityAnalysis
import com.viewcompose.renderer.reconcile.LazyListRotationDirection
import com.viewcompose.renderer.view.lazy.focus.LazyFocusFollowLayoutMonitor
import com.viewcompose.renderer.view.lazy.reuse.MountedTreeReuseCache
import com.viewcompose.renderer.view.lazy.reuse.LazyPreparationCostTracker
import com.viewcompose.renderer.view.lazy.session.LazyHolderRegistry
import com.viewcompose.renderer.view.lazy.session.LazyItemBindOutcome
import com.viewcompose.renderer.view.lazy.session.LazyItemSessionController
import com.viewcompose.renderer.view.lazy.session.LazyItemSessionHost
import com.viewcompose.renderer.view.lazy.state.UiLazyListConnector
import com.viewcompose.renderer.decoration.ViewDecorationHostLayout
import com.viewcompose.ui.state.LazyListState

/** Owns list diffing, physical holders, logical item sessions, and sticky-header presentations. */
internal class LazyListAdapter(
    private val orientation: Int = LinearLayoutManager.VERTICAL,
    private val preparationCosts: LazyPreparationCostTracker = LazyPreparationCostTracker(),
) : RecyclerView.Adapter<LazyListViewHolder>() {
    private companion object {
        private const val FOCUS_TAG = "UIFocusFollow"
        private const val DUPLICATE_KEY_POSITION = -1
        private const val INITIAL_STICKY_POSITION_CAPACITY = 4
        private const val MAX_DISTINCT_VIEW_TYPES = 1_024
    }

    private data class ScrollAnchor(
        val position: Int,
        val offset: Int,
    )

    private class KeyIndex(
        private val keys: Array<Any?>,
        private val positions: IntArray,
        private val stableIds: LongArray,
        val duplicateKeys: List<Any>,
        val stickyHeaderPositions: IntArray,
    ) {
        val supportsKeyedDiff: Boolean
            get() = duplicateKeys.isEmpty()

        fun uniquePosition(key: Any): Int? {
            val slot = findSlot(key)
            if (slot < 0) return null
            return positions[slot].takeIf { it >= 0 }
        }

        fun stableId(key: Any): Long? {
            val slot = findSlot(key)
            if (slot < 0 || positions[slot] < 0) return null
            return stableIds[slot]
        }

        fun retainedStableId(key: Any): Long? {
            val slot = findSlot(key)
            return if (slot >= 0) stableIds[slot] else null
        }

        private fun findSlot(key: Any): Int {
            if (keys.isEmpty()) return -1
            val mask = keys.lastIndex
            var slot = mixedHash(key) and mask
            while (true) {
                val candidate = keys[slot] ?: return -1
                if (candidate == key) return slot
                slot = (slot + 1) and mask
            }
        }

        companion object {
            val Empty = KeyIndex(
                keys = emptyArray(),
                positions = intArrayOf(),
                stableIds = longArrayOf(),
                duplicateKeys = emptyList(),
                stickyHeaderPositions = intArrayOf(),
            )

            fun tableCapacity(itemCount: Int): Int {
                if (itemCount == 0) return 0
                require(itemCount <= (1 shl 29)) {
                    "Lazy collection item count is too large: $itemCount"
                }
                val required = itemCount + (itemCount ushr 1) + 1
                return Integer.highestOneBit(required - 1).shl(1).coerceAtLeast(2)
            }

            fun mixedHash(key: Any): Int {
                val hash = key.hashCode()
                return hash xor (hash ushr 16)
            }
        }
    }

    private var items: List<LazyListItem> = emptyList()
    private var keyIndex = KeyIndex.Empty
    // The registry centralizes holder lifecycle across attach, detach, recycle, and final disposal.
    private val mountedTreeCache = MountedTreeReuseCache()
    private val holderRegistry = LazyHolderRegistry<LazyListViewHolder>(::recycleHolder)
    private var lastIdentityWarning: String? = null
    private var attachedRecyclerView: RecyclerView? = null
    private val viewTypes = ViewTypeRegistry(MAX_DISTINCT_VIEW_TYPES)
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
        if (holder.hasCommitted(currentSubmissionRevision)) return
        val reuseKey = holder.reuseKey()
        val startedAt = System.nanoTime()
        var outcome = holder.activate(currentSubmissionRevision)
        if (!outcome.satisfiesSubmission) {
            outcome = refreshHolder(holder, currentSubmissionRevision)
        }
        recordSessionCost(reuseKey, outcome, startedAt)
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
        return viewTypes.getOrPut(item.kind, item.contentType) { nextViewType++ }
    }

    override fun getItemId(position: Int): Long {
        val key = items[position].key
        return keyIndex.stableId(key) ?: (Long.MIN_VALUE + position)
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
        val revision = submissionRevision ?: (currentSubmissionRevision + 1L)
        if (revision <= currentSubmissionRevision) return false
        val previousItems = this.items
        // Equal submissions are common for retained grid/policy patches. Canonical item snapshots
        // make this scan reference-cheap, and it avoids rebuilding indexes plus a second plan scan.
        if (previousItems == items) return false
        val previousKeyIndex = keyIndex
        val nextKeyIndex = buildKeyIndex(items)
        val includeSemanticChanges = attachedRecyclerView?.itemAnimator != null
        val updatePlan = LazyListDiff.calculateAdapterUpdatePlan(
            previous = previousItems,
            next = items,
            supportsKeyedDiff = previousKeyIndex.supportsKeyedDiff &&
                nextKeyIndex.supportsKeyedDiff,
            // With motion disabled, attached sessions commit semantic revisions synchronously and
            // detached holders reconcile on attach; only physical compatibility still needs RV.
            includeSemanticChanges = includeSemanticChanges,
        )
        if (updatePlan === LazyListAdapterUpdatePlan.NoChange) return false
        warnAboutIdentityIssues(nextKeyIndex)
        // When incremental diff is unavailable, preserve the first visible item anchor to reduce jump after notifyDataSetChanged.
        val reloadAnchor = if (updatePlan === LazyListAdapterUpdatePlan.ReloadAll) {
            captureScrollAnchor()
        } else {
            null
        }
        this.items = items
        keyIndex = nextKeyIndex
        stickyHeaderPositions = nextKeyIndex.stickyHeaderPositions
        currentSubmissionRevision = revision
        itemsVersion += 1
        dispatchUpdatePlan(updatePlan, reloadAnchor)
        refreshAttachedHolders(
            previousItems = previousItems,
            previousKeyIndex = previousKeyIndex,
            submissionRevision = revision,
            forceAll = updatePlan === LazyListAdapterUpdatePlan.ReloadAll,
            retrySuppressedSemanticFailures = !includeSemanticChanges &&
                updatePlan !== LazyListAdapterUpdatePlan.ReloadAll,
        )
        return true
    }

    @DoNotInline
    private fun dispatchUpdatePlan(
        updatePlan: LazyListAdapterUpdatePlan,
        reloadAnchor: ScrollAnchor?,
    ) {
        when (updatePlan) {
            LazyListAdapterUpdatePlan.NoChange -> error("No-change plan must return before publication")
            LazyListAdapterUpdatePlan.ReloadAll -> {
                notifyDataSetChanged()
                restoreScrollAnchor(reloadAnchor)
            }
            is LazyListAdapterUpdatePlan.SameKeyOrderChanges -> {
                updatePlan.ranges.forEach { range ->
                    notifyItemRangeChanged(
                        range.positionStart,
                        range.itemCount,
                        LazyListAdapterChangedPayload,
                    )
                }
            }
            is LazyListAdapterUpdatePlan.CyclicRotation -> {
                val lastPosition = itemCount - 1
                when (updatePlan.direction) {
                    LazyListRotationDirection.Left -> repeat(updatePlan.moveCount) {
                        notifyItemMoved(0, lastPosition)
                    }
                    LazyListRotationDirection.Right -> repeat(updatePlan.moveCount) {
                        notifyItemMoved(lastPosition, 0)
                    }
                }
                updatePlan.changedRanges.forEach { range ->
                    notifyItemRangeChanged(
                        range.positionStart,
                        range.itemCount,
                        LazyListAdapterChangedPayload,
                    )
                }
            }
            is LazyListAdapterUpdatePlan.StructuralDiff -> {
                updatePlan.result.dispatchUpdatesTo(this)
            }
        }
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
        previousItems: List<LazyListItem>,
        previousKeyIndex: KeyIndex,
        submissionRevision: Long,
        forceAll: Boolean,
        retrySuppressedSemanticFailures: Boolean,
    ) {
        var retryPositions: LinkedHashSet<Int>? = null
        var bindingFailure: Throwable? = null
        holderRegistry.forEachAttached { holder ->
            val boundKey = holder.boundItemKey
            var previousItem: LazyListItem? = null
            val position = if (boundKey != null) {
                val previousPosition = previousKeyIndex.uniquePosition(boundKey)
                val nextPosition = keyIndex.uniquePosition(boundKey)
                if (previousPosition != null && nextPosition != null) {
                    previousItem = previousItems[previousPosition]
                    if (!forceAll && previousItems[previousPosition] == items[nextPosition]) {
                        return@forEachAttached
                    }
                    nextPosition
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
            val hasSuppressedSemanticChange = retrySuppressedSemanticFailures &&
                previousItem?.hasSuppressedSemanticOnlyChange(nextItem) == true
            val outcome = try {
                holder.bind(
                    nextItem,
                    submissionRevision = submissionRevision,
                    position = position,
                    active = true,
                )
            } catch (error: Throwable) {
                if (hasSuppressedSemanticChange) {
                    val positions = retryPositions ?: LinkedHashSet<Int>().also {
                        retryPositions = it
                    }
                    positions += position
                }
                val firstFailure = bindingFailure
                if (firstFailure == null) {
                    bindingFailure = error
                } else {
                    firstFailure.addSuppressed(error)
                }
                return@forEachAttached
            }
            if (hasSuppressedSemanticChange && !outcome.satisfiesSubmission) {
                val positions = retryPositions ?: LinkedHashSet<Int>().also {
                    retryPositions = it
                }
                positions += position
            }
        }
        retryPositions?.forEach { position ->
            try {
                notifyItemChanged(position, LazyListAdapterChangedPayload)
            } catch (error: Throwable) {
                val firstFailure = bindingFailure
                if (firstFailure == null) {
                    bindingFailure = error
                } else if (firstFailure !== error) {
                    firstFailure.addSuppressed(error)
                }
            }
        }
        bindingFailure?.let { throw it }
    }

    private fun LazyListItem.hasSuppressedSemanticOnlyChange(next: LazyListItem): Boolean {
        val semanticChanged = contentRevision != next.contentRevision ||
            environmentRevision != next.environmentRevision
        return semanticChanged &&
            contentType == next.contentType &&
            kind == next.kind &&
            span == next.span
    }

    private fun refreshHolder(
        holder: LazyListViewHolder,
        submissionRevision: Long,
    ): LazyItemBindOutcome {
        val boundKey = holder.boundItemKey
        val position = if (boundKey != null) {
            keyIndex.uniquePosition(boundKey) ?: return LazyItemBindOutcome.NotCommitted
        } else {
            holder.boundItemPosition
        }
        if (position !in items.indices) return LazyItemBindOutcome.NotCommitted
        val nextItem = items[position]
        if (holder.boundContentType != nextItem.contentType || holder.boundItemKind != nextItem.kind) {
            return LazyItemBindOutcome.NotCommitted
        }
        return holder.bind(
            nextItem,
            submissionRevision = submissionRevision,
            position = position,
            active = true,
        )
    }

    private fun warnAboutIdentityIssues(index: KeyIndex) {
        if (index.duplicateKeys.isEmpty()) {
            lastIdentityWarning = null
            return
        }
        val warning = LazyListIdentityAnalysis(index.duplicateKeys)
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
        keyIndex = KeyIndex.Empty
        stickyHeaderPositions = intArrayOf()
        itemsVersion += 1
        failure?.let { throw it }
    }

    private fun buildKeyIndex(items: List<LazyListItem>): KeyIndex {
        val capacity = KeyIndex.tableCapacity(items.size)
        val keys = arrayOfNulls<Any>(capacity)
        val positions = IntArray(capacity)
        val stableIds = LongArray(capacity)
        val mask = capacity - 1
        var duplicateKeys: ArrayList<Any>? = null
        var stickyPositions: IntArray? = null
        var stickyCount = 0
        items.forEachIndexed { position, item ->
            val key = item.key
            var slot = KeyIndex.mixedHash(key) and mask
            while (keys[slot] != null && keys[slot] != key) {
                slot = (slot + 1) and mask
            }
            val previousPosition = if (keys[slot] == null) null else positions[slot]
            if (previousPosition == null) {
                keys[slot] = key
                positions[slot] = position
                stableIds[slot] = keyIndex.retainedStableId(key) ?: nextStableId++
            } else if (previousPosition >= 0) {
                positions[slot] = DUPLICATE_KEY_POSITION
                if (duplicateKeys == null) duplicateKeys = ArrayList()
                duplicateKeys?.add(key)
            }
            if (item.kind == LazyListItemKind.StickyHeader) {
                var target = stickyPositions
                if (target == null) {
                    target = IntArray(minOf(items.size, INITIAL_STICKY_POSITION_CAPACITY))
                    stickyPositions = target
                } else if (stickyCount == target.size) {
                    target = target.copyOf(minOf(items.size, target.size * 2))
                    stickyPositions = target
                }
                target[stickyCount++] = position
            }
        }
        return KeyIndex(
            keys = keys,
            positions = positions,
            stableIds = stableIds,
            duplicateKeys = duplicateKeys ?: emptyList(),
            stickyHeaderPositions = stickyPositions?.copyOf(stickyCount) ?: intArrayOf(),
        )
    }

    private fun bindHolder(
        holder: LazyListViewHolder,
        position: Int,
        payload: Any?,
    ) {
        val item = items[position]
        if (
            payload != null &&
            holder.acknowledgeCommittedBinding(
                item = item,
                submissionRevision = currentSubmissionRevision,
                position = position,
            )
        ) {
            return
        }
        ensureContainerLayoutParams(holder)
        holderRegistry.onBound(holder)
        val reuseKey = MountedTreeReuseCache.ReuseKey(item.kind, item.contentType)
        val active = holderRegistry.isAttached(holder)
        val prepare = !active && preparationCosts.shouldPrepare(reuseKey)
        preparePhysicalPresentation(holder, item)
        val startedAt = if (active || prepare) System.nanoTime() else 0L
        val outcome = holder.bind(
            item = item,
            payload = payload,
            submissionRevision = currentSubmissionRevision,
            position = position,
            active = active,
            prepare = prepare,
        )
        recordSessionCost(reuseKey, outcome, startedAt)
    }

    private fun recordSessionCost(
        reuseKey: MountedTreeReuseCache.ReuseKey?,
        outcome: LazyItemBindOutcome,
        startedAt: Long,
    ) {
        if (reuseKey == null) return
        when (outcome) {
            LazyItemBindOutcome.ActivatedNewSession -> {
                // Cold activation is a conservative bootstrap ceiling only: it contains commit and
                // effect work that is absent from detached preparation.
                preparationCosts.recordBootstrapUpperBound(
                    reuseKey,
                    System.nanoTime() - startedAt,
                )
            }
            LazyItemBindOutcome.PreparedNewSession -> {
                preparationCosts.recordPreparation(
                    reuseKey,
                    System.nanoTime() - startedAt,
                )
            }
            else -> Unit
        }
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

/** Stable view-type registry without Pair keys, map nodes, or boxed IDs on RecyclerView lookups. */
private class ViewTypeRegistry(
    private val maximumSize: Int,
) {
    private var occupied = BooleanArray(INITIAL_CAPACITY)
    private var kinds = arrayOfNulls<LazyListItemKind>(INITIAL_CAPACITY)
    private var contentTypes = arrayOfNulls<Any>(INITIAL_CAPACITY)
    private var values = IntArray(INITIAL_CAPACITY)
    private var size = 0

    inline fun getOrPut(
        kind: LazyListItemKind,
        contentType: Any?,
        create: () -> Int,
    ): Int {
        var slot = findSlot(kind, contentType)
        if (occupied[slot]) return values[slot]
        require(size < maximumSize) {
            "Lazy collection contentType must use at most $maximumSize distinct " +
                "kind/type combinations per mounted container."
        }
        if ((size + 1) * LOAD_FACTOR_DENOMINATOR > occupied.size * LOAD_FACTOR_NUMERATOR) {
            grow()
            slot = findSlot(kind, contentType)
        }
        val value = create()
        occupied[slot] = true
        kinds[slot] = kind
        contentTypes[slot] = contentType
        values[slot] = value
        size += 1
        return value
    }

    private fun findSlot(
        kind: LazyListItemKind,
        contentType: Any?,
    ): Int {
        val mask = occupied.lastIndex
        var slot = mixedHash(kind, contentType) and mask
        while (occupied[slot]) {
            if (kinds[slot] == kind && contentTypes[slot] == contentType) return slot
            slot = (slot + 1) and mask
        }
        return slot
    }

    private fun grow() {
        val previousOccupied = occupied
        val previousKinds = kinds
        val previousContentTypes = contentTypes
        val previousValues = values
        val nextCapacity = previousOccupied.size shl 1
        occupied = BooleanArray(nextCapacity)
        kinds = arrayOfNulls(nextCapacity)
        contentTypes = arrayOfNulls(nextCapacity)
        values = IntArray(nextCapacity)
        previousOccupied.indices.forEach { index ->
            if (!previousOccupied[index]) return@forEach
            val kind = checkNotNull(previousKinds[index])
            val slot = findSlot(kind, previousContentTypes[index])
            occupied[slot] = true
            kinds[slot] = kind
            contentTypes[slot] = previousContentTypes[index]
            values[slot] = previousValues[index]
        }
    }

    private fun mixedHash(
        kind: LazyListItemKind,
        contentType: Any?,
    ): Int {
        val hash = 31 * kind.hashCode() + (contentType?.hashCode() ?: 0)
        return hash xor (hash ushr 16)
    }

    private companion object {
        private const val INITIAL_CAPACITY = 4
        private const val LOAD_FACTOR_NUMERATOR = 3
        private const val LOAD_FACTOR_DENOMINATOR = 4
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
) : RecyclerView.ViewHolder(container), LazyItemSessionHost {
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

    private val renderContainer = container.asRenderContainerHandle()
    private val controller = LazyItemSessionController(this)

    override fun createSession(item: LazyListItem): LazyListItemSession {
        return item.createSession(renderContainer)
    }

    override fun clearContainer() {
        container.removeAllViews()
    }

    fun bind(
        item: LazyListItem,
        payload: Any? = null,
        submissionRevision: Long,
        position: Int,
        active: Boolean,
        prepare: Boolean = true,
    ): LazyItemBindOutcome {
        hasBinding = true
        boundItemKey = item.key
        boundItemPosition = position
        boundContentType = item.contentType
        boundItemKind = item.kind
        return if (active) {
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
            LazyItemBindOutcome.Staged
        }
    }

    fun acknowledgeCommittedBinding(
        item: LazyListItem,
        submissionRevision: Long,
        position: Int,
    ): Boolean {
        if (
            !hasBinding ||
            !controller.hasCommittedExact(item, submissionRevision)
        ) {
            return false
        }
        boundItemPosition = position
        return true
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

    fun activate(submissionRevision: Long): LazyItemBindOutcome =
        controller.commit(submissionRevision)

    fun hasCommitted(submissionRevision: Long): Boolean =
        controller.hasCommitted(submissionRevision)
}
