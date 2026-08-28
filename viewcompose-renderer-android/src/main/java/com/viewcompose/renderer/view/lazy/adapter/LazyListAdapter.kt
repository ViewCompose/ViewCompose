package com.viewcompose.renderer.view.lazy.adapter

import android.graphics.Rect
import android.os.Looper
import android.os.MessageQueue
import android.util.Log
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.annotation.DoNotInline
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.viewcompose.renderer.R
import com.viewcompose.ui.node.LazyListItem
import com.viewcompose.ui.node.LazyItemTable
import com.viewcompose.ui.node.LazyItemTableStickyHeaders
import com.viewcompose.ui.node.LazyItemTableUpdate
import com.viewcompose.ui.node.LazyListItemSession
import com.viewcompose.ui.node.asLazyItemTable
import com.viewcompose.ui.node.policy.GridItemSpan
import com.viewcompose.ui.node.LazyListItemKind
import com.viewcompose.renderer.interop.asRenderContainerHandle
import com.viewcompose.renderer.reconcile.LazyListAdapterChangedPayload
import com.viewcompose.renderer.reconcile.LazyListAdapterUpdatePlan
import com.viewcompose.renderer.reconcile.LazyListDiff
import com.viewcompose.renderer.reconcile.LazyListRotationDirection
import com.viewcompose.renderer.view.lazy.reuse.MountedTreeReuseCache
import com.viewcompose.renderer.view.lazy.reuse.LazyPreparationCostTracker
import com.viewcompose.renderer.view.lazy.session.LazyHolderRegistry
import com.viewcompose.renderer.view.lazy.session.LazyItemBindOutcome
import com.viewcompose.renderer.view.lazy.session.LazyItemSessionController
import com.viewcompose.renderer.view.lazy.session.LazyItemSessionHost
import com.viewcompose.renderer.view.lazy.state.UiLazyListConnector
import com.viewcompose.renderer.decoration.ViewDecorationHostLayout
import com.viewcompose.ui.state.LazyListState
import java.lang.ref.WeakReference

/** Owns list diffing, physical holders, logical item sessions, and sticky-header presentations. */
internal class LazyListAdapter(
    private val orientation: Int = LinearLayoutManager.VERTICAL,
    private val preparationCosts: LazyPreparationCostTracker = LazyPreparationCostTracker(),
) : RecyclerView.Adapter<LazyListViewHolder>() {
    private companion object {
        private const val ANCHOR_TAG = "UILazyAnchor"
        private const val MAX_DISTINCT_VIEW_TYPES = 1_024
        private const val MAX_RETAINED_POOL_SESSIONS = 1
        private const val IDLE_PREPARATION_DISTANCE = 2
    }

    private data class ScrollAnchor(
        val position: Int,
        val offset: Int,
    )

    private object EmptyItemTable : LazyItemTable {
        override val size: Int = 0
        override fun get(index: Int): LazyListItem = throw IndexOutOfBoundsException(
            "Lazy item index $index is outside an empty table.",
        )
        override fun indexOfKey(key: Any): Int = -1
        override fun updatesFrom(previous: LazyItemTable): List<LazyItemTableUpdate>? =
            if (previous.size == 0) emptyList() else null
    }

    private data class DeclaredTableUpdatePlan(
        val updates: List<LazyItemTableUpdate>,
        val reloadAll: Boolean,
    )

    private class CachedCyclicTransition(
        previous: LazyItemTable,
        next: LazyItemTable,
        val includesSemanticChanges: Boolean,
        val plan: LazyListAdapterUpdatePlan.CyclicRotation,
    ) {
        private val previousReference = WeakReference(previous)
        private val nextReference = WeakReference(next)

        fun matches(
            previous: LazyItemTable,
            next: LazyItemTable,
            includesSemanticChanges: Boolean,
        ): Boolean {
            return previousReference.get() === previous &&
                nextReference.get() === next &&
                this.includesSemanticChanges == includesSemanticChanges
        }
    }

    private var items: LazyItemTable = EmptyItemTable
    // The registry centralizes holder lifecycle across attach, detach, recycle, and final disposal.
    private val mountedTreeCache = MountedTreeReuseCache()
    private val holderRegistry = LazyHolderRegistry<LazyListViewHolder>(::recycleHolder)
    private val retainedPoolHolders = LinkedHashSet<LazyListViewHolder>()
    private var idlePreparationCandidate: LazyListViewHolder? = null
    private var idlePreparationDirection = 0
    private var idlePreparationRegistered = false
    private val idlePreparationHandler = MessageQueue.IdleHandler {
        preparePooledHolderWhileIdle()
    }
    private val localRecycledViewPool = object : RecyclerView.RecycledViewPool() {
        override fun putRecycledView(scrap: RecyclerView.ViewHolder) {
            if (scrap is LazyListViewHolder && captureIdlePreparationCandidate(scrap)) {
                return
            }
            super.putRecycledView(scrap)
        }

        fun putPreparedView(holder: LazyListViewHolder) {
            super.putRecycledView(holder)
        }
    }
    private val idlePreparationScrollListener = object : RecyclerView.OnScrollListener() {
        override fun onScrollStateChanged(
            recyclerView: RecyclerView,
            newState: Int,
        ) {
            if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                idlePreparationDirection = 0
                cancelIdlePreparation(returnCandidateToPool = true)
            }
        }

        override fun onScrolled(
            recyclerView: RecyclerView,
            dx: Int,
            dy: Int,
        ) {
            val delta = if (orientation == LinearLayoutManager.HORIZONTAL) dx else dy
            if (delta != 0) {
                idlePreparationDirection = if (delta > 0) 1 else -1
                scheduleIdlePreparation()
            }
        }
    }
    private var attachedRecyclerView: RecyclerView? = null
    private val viewTypes = ViewTypeRegistry(MAX_DISTINCT_VIEW_TYPES)
    private val stableIdsByKey = HashMap<Any, Long>()
    private var nextStableId = 0L
    private var nextViewType = 1
    private var itemsVersion = 0L
    private var currentSubmissionRevision = 0L
    private var listState: LazyListState? = null
    private var stickyHeaderDisposer: (() -> Unit)? = null
    private var latestCyclicTransition: CachedCyclicTransition? = null
    private var previousCyclicTransition: CachedCyclicTransition? = null

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
        if (
            usesLocalRecycledViewPool() &&
            retainedPoolHolders.isEmpty() &&
            holder.retainForCrossKeyReuse()
        ) {
            holderRegistry.onRecycled(holder, retainOwnership = true)
            retainedPoolHolders += holder
        } else {
            retainedPoolHolders.remove(holder)
            holderRegistry.onRecycled(holder)
        }
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
        recyclerView.setTag(R.id.viewcompose_local_recycled_view_pool, localRecycledViewPool)
        recyclerView.setRecycledViewPool(localRecycledViewPool)
        recyclerView.addOnScrollListener(idlePreparationScrollListener)
    }

    override fun onViewDetachedFromWindow(holder: LazyListViewHolder) {
        super.onViewDetachedFromWindow(holder)
        holderRegistry.onDetached(holder)
    }

    override fun onDetachedFromRecyclerView(recyclerView: RecyclerView) {
        recyclerView.removeOnScrollListener(idlePreparationScrollListener)
        cancelIdlePreparation(returnCandidateToPool = false)
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
        return stableIdsByKey.getOrPut(key) { nextStableId++ }
    }

    fun itemKeyAt(position: Int): Any? = itemAtOrNull(position)?.key

    fun itemContentTypeAt(position: Int): Any? = itemAtOrNull(position)?.contentType

    fun itemSpanAt(position: Int): GridItemSpan =
        itemAtOrNull(position)?.span ?: GridItemSpan.Single

    fun configureMountedTreeCache(size: Int) {
        mountedTreeCache.capacity = size
    }

    fun isStickyHeader(position: Int): Boolean {
        return itemAtOrNull(position)?.kind == LazyListItemKind.StickyHeader
    }

    fun hasStickyHeaders(): Boolean {
        return (items as? LazyItemTableStickyHeaders)?.hasStickyHeaders == true
    }

    fun findStickyHeaderPosition(itemPosition: Int): Int {
        if (itemPosition < 0 || items.size == 0) {
            return RecyclerView.NO_POSITION
        }
        val stickyHeaders = items as? LazyItemTableStickyHeaders
            ?: return RecyclerView.NO_POSITION
        return stickyHeaders.findStickyHeaderIndex(itemPosition.coerceAtMost(items.size - 1))
            .takeIf { it >= 0 }
            ?: RecyclerView.NO_POSITION
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
    ): Boolean = submitItems(items.asLazyItemTable(), submissionRevision)

    fun submitItems(
        items: LazyItemTable,
        submissionRevision: Long? = null,
    ): Boolean {
        val revision = submissionRevision ?: (currentSubmissionRevision + 1L)
        if (revision <= currentSubmissionRevision) return false
        val previousItems = this.items
        val includeSemanticChanges = attachedRecyclerView?.itemAnimator != null
        val declaredPlan = items.updatesFrom(previousItems)?.let { updates ->
            validateDeclaredUpdates(
                previousSize = previousItems.size,
                nextSize = items.size,
                updates = updates,
            )
        }
        val genericPlan = if (declaredPlan == null) {
            cachedCyclicTransition(
                previous = previousItems,
                next = items,
                includeSemanticChanges = includeSemanticChanges,
            ) ?: LazyListDiff.calculateAdapterUpdatePlan(
                previous = previousItems.materialize(),
                next = items.materialize(),
                supportsKeyedDiff = true,
                // With motion disabled, attached sessions commit semantic revisions synchronously
                // and detached holders reconcile on attach; only physical compatibility needs RV.
                includeSemanticChanges = includeSemanticChanges,
            ).also { plan ->
                if (plan is LazyListAdapterUpdatePlan.CyclicRotation) {
                    previousCyclicTransition = latestCyclicTransition
                    latestCyclicTransition = CachedCyclicTransition(
                        previous = previousItems,
                        next = items,
                        includesSemanticChanges = includeSemanticChanges,
                        plan = plan,
                    )
                }
            }
        } else {
            null
        }
        if (declaredPlan?.updates?.isEmpty() == true || genericPlan === LazyListAdapterUpdatePlan.NoChange) {
            return false
        }
        val reloadAll = declaredPlan?.reloadAll == true ||
            genericPlan === LazyListAdapterUpdatePlan.ReloadAll
        // Full invalidation preserves the first visible item anchor to reduce viewport jumps.
        val reloadAnchor = if (reloadAll) {
            captureScrollAnchor()
        } else {
            null
        }
        val synchronousRefreshRange = captureVisibleAdapterRange()
        cancelIdlePreparation(returnCandidateToPool = true)
        this.items = items
        stableIdsByKey.keys.removeAll { key -> items.indexOfKey(key) < 0 }
        currentSubmissionRevision = revision
        itemsVersion += 1
        disposeStaleDetachedHolders(items)
        if (declaredPlan != null) {
            dispatchDeclaredUpdates(
                plan = declaredPlan,
                includeSemanticChanges = includeSemanticChanges,
                reloadAnchor = reloadAnchor,
            )
        } else {
            dispatchUpdatePlan(checkNotNull(genericPlan), reloadAnchor)
        }
        refreshAttachedHolders(
            previousItems = previousItems,
            submissionRevision = revision,
            forceAll = reloadAll,
            retrySuppressedSemanticFailures = !includeSemanticChanges &&
                !reloadAll,
            synchronousRefreshRange = synchronousRefreshRange,
        )
        return true
    }

    private fun disposeStaleDetachedHolders(nextItems: LazyItemTable) {
        holderRegistry.disposeDetachedWhere { holder ->
            val key = holder.boundItemKey ?: return@disposeDetachedWhere false
            val nextPosition = nextItems.indexOfKey(key)
            if (nextPosition < 0) {
                true
            } else {
                val nextItem = nextItems[nextPosition]
                holder.boundContentType != nextItem.contentType ||
                    holder.boundItemKind != nextItem.kind
            }
        }
    }

    private fun validateDeclaredUpdates(
        previousSize: Int,
        nextSize: Int,
        updates: List<LazyItemTableUpdate>,
    ): DeclaredTableUpdatePlan {
        var currentSize = previousSize
        var reloadAll = false
        updates.forEachIndexed { operationIndex, update ->
            check(!reloadAll) {
                "Lazy item table ReloadAll must be the final and only update."
            }
            when (update) {
                is LazyItemTableUpdate.InsertRange -> {
                    require(update.count > 0) {
                        "Lazy item table insert count must be positive at operation $operationIndex."
                    }
                    require(update.index in 0..currentSize) {
                        "Lazy item table insert index ${update.index} is outside 0..$currentSize."
                    }
                    currentSize = Math.addExact(currentSize, update.count)
                }
                is LazyItemTableUpdate.RemoveRange -> {
                    require(update.count > 0) {
                        "Lazy item table remove count must be positive at operation $operationIndex."
                    }
                    require(
                        update.index >= 0 &&
                            update.index.toLong() + update.count.toLong() <= currentSize.toLong(),
                    ) {
                        "Lazy item table remove range ${update.index}..${update.index + update.count} " +
                            "exceeds current size $currentSize."
                    }
                    currentSize -= update.count
                }
                is LazyItemTableUpdate.Move -> {
                    require(update.fromIndex in 0 until currentSize) {
                        "Lazy item table move source ${update.fromIndex} is outside 0 until $currentSize."
                    }
                    require(update.toIndex in 0 until currentSize) {
                        "Lazy item table move target ${update.toIndex} is outside 0 until $currentSize."
                    }
                }
                is LazyItemTableUpdate.ChangeRange -> {
                    require(update.count > 0) {
                        "Lazy item table change count must be positive at operation $operationIndex."
                    }
                    require(
                        update.index >= 0 &&
                            update.index.toLong() + update.count.toLong() <= currentSize.toLong(),
                    ) {
                        "Lazy item table change range ${update.index}..${update.index + update.count} " +
                            "exceeds current size $currentSize."
                    }
                }
                LazyItemTableUpdate.ReloadAll -> {
                    require(updates.size == 1) {
                        "Lazy item table ReloadAll must be the only update."
                    }
                    reloadAll = true
                    currentSize = nextSize
                }
            }
        }
        require(currentSize == nextSize) {
            "Lazy item table updates produce size $currentSize but candidate size is $nextSize."
        }
        return DeclaredTableUpdatePlan(
            updates = updates,
            reloadAll = reloadAll,
        )
    }

    @DoNotInline
    private fun dispatchDeclaredUpdates(
        plan: DeclaredTableUpdatePlan,
        includeSemanticChanges: Boolean,
        reloadAnchor: ScrollAnchor?,
    ) {
        plan.updates.forEach { update ->
            when (update) {
                is LazyItemTableUpdate.InsertRange -> notifyItemRangeInserted(
                    update.index,
                    update.count,
                )
                is LazyItemTableUpdate.RemoveRange -> notifyItemRangeRemoved(
                    update.index,
                    update.count,
                )
                is LazyItemTableUpdate.Move -> notifyItemMoved(
                    update.fromIndex,
                    update.toIndex,
                )
                is LazyItemTableUpdate.ChangeRange -> if (includeSemanticChanges) {
                    notifyItemRangeChanged(
                        update.index,
                        update.count,
                        LazyListAdapterChangedPayload,
                    )
                }
                LazyItemTableUpdate.ReloadAll -> {
                    notifyDataSetChanged()
                    restoreScrollAnchor(reloadAnchor)
                }
            }
        }
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
        previousItems: LazyItemTable,
        submissionRevision: Long,
        forceAll: Boolean,
        retrySuppressedSemanticFailures: Boolean,
        synchronousRefreshRange: IntRange?,
    ) {
        var retryPositions: LinkedHashSet<Int>? = null
        var bindingFailure: Throwable? = null
        holderRegistry.forEachAttached { holder ->
            val boundKey = holder.boundItemKey
            var previousItem: LazyListItem? = null
            var previousPosition = RecyclerView.NO_POSITION
            val position = if (boundKey != null) {
                previousPosition = previousItems.indexOfKey(boundKey)
                val nextPosition = items.indexOfKey(boundKey)
                if (previousPosition >= 0 && nextPosition >= 0) {
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
            if (position !in 0 until items.size) return@forEachAttached
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
            if (
                hasSuppressedSemanticChange &&
                boundKey != null &&
                previousPosition != position &&
                synchronousRefreshRange != null &&
                position !in synchronousRefreshRange
            ) {
                // The holder is leaving the viewport because its stable key moved. A change
                // payload dispatched in the same RecyclerView update batch is resolved through
                // pre-layout coordinates and immediately rebinds that departing holder. Leaving
                // its committed revision behind makes detach/reattach perform the semantic bind
                // only if the key becomes relevant again.
                return@forEachAttached
            }
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

    private fun captureVisibleAdapterRange(): IntRange? {
        val layoutManager = attachedRecyclerView?.layoutManager as? LinearLayoutManager ?: return null
        val first = layoutManager.findFirstVisibleItemPosition()
        val last = layoutManager.findLastVisibleItemPosition()
        if (first == RecyclerView.NO_POSITION || last == RecyclerView.NO_POSITION) return null
        return minOf(first, last)..maxOf(first, last)
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
            items.indexOfKey(boundKey).takeIf { it >= 0 }
                ?: return LazyItemBindOutcome.NotCommitted
        } else {
            holder.boundItemPosition
        }
        if (position !in 0 until items.size) return LazyItemBindOutcome.NotCommitted
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
        } finally {
            retainedPoolHolders.clear()
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
        items = EmptyItemTable
        latestCyclicTransition = null
        previousCyclicTransition = null
        stableIdsByKey.clear()
        itemsVersion += 1
        failure?.let { throw it }
    }

    private fun itemAtOrNull(index: Int): LazyListItem? {
        return if (index in 0 until items.size) items[index] else null
    }

    private fun LazyItemTable.materialize(): List<LazyListItem> {
        return toList()
    }

    private fun cachedCyclicTransition(
        previous: LazyItemTable,
        next: LazyItemTable,
        includeSemanticChanges: Boolean,
    ): LazyListAdapterUpdatePlan.CyclicRotation? {
        latestCyclicTransition?.takeIf { cached ->
            cached.matches(previous, next, includeSemanticChanges)
        }?.let { return it.plan }
        previousCyclicTransition?.takeIf { cached ->
            cached.matches(previous, next, includeSemanticChanges)
        }?.let { return it.plan }
        return null
    }

    private fun bindHolder(
        holder: LazyListViewHolder,
        position: Int,
        payload: Any?,
        forcePreparation: Boolean = false,
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
        retainedPoolHolders.remove(holder)
        holderRegistry.onBound(holder)
        val reuseKey = MountedTreeReuseCache.ReuseKey(item.kind, item.contentType)
        val active = holderRegistry.isAttached(holder)
        val prepare = !active && (
            forcePreparation || preparationCosts.shouldPrepare(reuseKey)
        )
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
            if (previousKey == nextKey && holder.canReuseFor(item)) {
                return
            }
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

    private fun usesLocalRecycledViewPool(): Boolean {
        val recyclerView = attachedRecyclerView ?: return false
        val localPool = recyclerView.getTag(R.id.viewcompose_local_recycled_view_pool)
        return localPool != null && recyclerView.recycledViewPool === localPool
    }

    private fun trimRetainedPoolHolders() {
        while (retainedPoolHolders.size > MAX_RETAINED_POOL_SESSIONS) {
            val oldest = retainedPoolHolders.first()
            retainedPoolHolders.remove(oldest)
            holderRegistry.dispose(oldest)
        }
    }

    private fun captureIdlePreparationCandidate(holder: LazyListViewHolder): Boolean {
        if (
            idlePreparationCandidate != null ||
            idlePreparationDirection == 0 ||
            attachedRecyclerView == null ||
            !holder.hasBinding
        ) {
            return false
        }
        idlePreparationCandidate = holder
        scheduleIdlePreparation()
        return true
    }

    private fun scheduleIdlePreparation() {
        if (idlePreparationRegistered || idlePreparationDirection == 0) return
        val recyclerView = attachedRecyclerView ?: return
        if (!recyclerView.isAttachedToWindow ||
            recyclerView.scrollState == RecyclerView.SCROLL_STATE_IDLE
        ) {
            return
        }
        idlePreparationRegistered = true
        Looper.myQueue().addIdleHandler(idlePreparationHandler)
    }

    private fun cancelIdlePreparation(returnCandidateToPool: Boolean) {
        if (idlePreparationRegistered) {
            Looper.myQueue().removeIdleHandler(idlePreparationHandler)
            idlePreparationRegistered = false
        }
        val holder = idlePreparationCandidate
        idlePreparationCandidate = null
        if (holder != null) {
            if (returnCandidateToPool) {
                localRecycledViewPool.putPreparedView(holder)
            } else {
                retainedPoolHolders.remove(holder)
                holderRegistry.dispose(holder)
            }
        }
    }

    private fun preparePooledHolderWhileIdle(): Boolean {
        idlePreparationRegistered = false
        val holder = idlePreparationCandidate
        idlePreparationCandidate = null
        val recyclerView = attachedRecyclerView
        if (
            holder == null ||
            recyclerView == null ||
            !recyclerView.isAttachedToWindow ||
            recyclerView.scrollState == RecyclerView.SCROLL_STATE_IDLE
        ) {
            holder?.let(localRecycledViewPool::putPreparedView)
            return false
        }
        val position = nextIdlePreparationPosition(recyclerView)
        if (position == RecyclerView.NO_POSITION) {
            localRecycledViewPool.putPreparedView(holder)
            return false
        }
        if (getItemViewType(position) != holder.itemViewType) {
            localRecycledViewPool.putPreparedView(holder)
            return false
        }
        try {
            bindHolder(
                holder = holder,
                position = position,
                payload = null,
                forcePreparation = true,
            )
            retainedPoolHolders += holder
            trimRetainedPoolHolders()
            localRecycledViewPool.putPreparedView(holder)
        } catch (error: Exception) {
            retainedPoolHolders.remove(holder)
            try {
                holderRegistry.dispose(holder)
            } catch (cleanupError: Throwable) {
                error.addSuppressed(cleanupError)
                throw error
            }
            // Preparation is speculative. Return the cleared physical holder so the ordinary bind
            // path can retry and report an application failure only if this position is requested.
            localRecycledViewPool.putPreparedView(holder)
        }
        return false
    }

    private fun nextIdlePreparationPosition(recyclerView: RecyclerView): Int {
        val layoutManager = recyclerView.layoutManager as? LinearLayoutManager
            ?: return RecyclerView.NO_POSITION
        return resolveIdlePreparationPosition(
            firstVisiblePosition = layoutManager.findFirstVisibleItemPosition(),
            lastVisiblePosition = layoutManager.findLastVisibleItemPosition(),
            direction = idlePreparationDirection,
            itemCount = itemCount,
            distance = IDLE_PREPARATION_DISTANCE,
        )
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
        return recyclerView.findFocus() != null
    }

    private inline fun debugFocusLog(message: () -> String) {
        if (!Log.isLoggable(ANCHOR_TAG, Log.DEBUG)) {
            return
        }
        Log.d(ANCHOR_TAG, message())
    }
}

internal fun resolveIdlePreparationPosition(
    firstVisiblePosition: Int,
    lastVisiblePosition: Int,
    direction: Int,
    itemCount: Int,
    distance: Int = 2,
): Int {
    if (direction == 0 || itemCount <= 0 || distance <= 0) return RecyclerView.NO_POSITION
    val anchor = if (direction > 0) lastVisiblePosition else firstVisiblePosition
    if (anchor == RecyclerView.NO_POSITION) return RecyclerView.NO_POSITION
    // LinearLayoutManager's adjacent prefetch owns the immediate off-screen position. The
    // recycled pool is queried for the following compatible holder, so prepare that position.
    val normalizedDirection = if (direction > 0) 1 else -1
    return (anchor + normalizedDirection * distance)
        .takeIf { position -> position in 0 until itemCount }
        ?: RecyclerView.NO_POSITION
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

    fun retainForCrossKeyReuse(): Boolean = controller.retainForCrossKeyReuse()

    fun canReuseFor(item: LazyListItem): Boolean = controller.canReuseFor(item)

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
