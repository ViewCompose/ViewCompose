package com.viewcompose.renderer.view.lazy.adapter

import android.util.Log
import android.graphics.Rect
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.viewcompose.ui.node.LazyListItem
import com.viewcompose.ui.node.LazyListItemKind
import com.viewcompose.renderer.interop.asRenderContainerHandle
import com.viewcompose.renderer.reconcile.LazyListDiff
import com.viewcompose.renderer.reconcile.LazyListIdentityInspector
import com.viewcompose.renderer.view.lazy.focus.LazyFocusFollowLayoutMonitor
import com.viewcompose.renderer.view.lazy.session.LazyHolderRegistry
import com.viewcompose.renderer.view.lazy.session.LazyItemSessionController
import com.viewcompose.renderer.decoration.ViewDecorationHostLayout

/**
 * RecyclerView adapter shared by LazyColumn, LazyRow, and lazy grids.
 * Shared RecyclerView adapter for LazyColumn, LazyRow, and Grid.
 *
 * Owns item diffing, stable IDs and view types, holder sessions, and detached holders for sticky headers.
 * It handles item diffing, stable ids/view types, holder session lifetime, and detached holder support for sticky headers.
 */
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

    private var items: List<LazyListItem> = emptyList()
    // The registry tracks holder lifecycle across attach, detach, recycle, and dispose entry points.
    // Holder lifetimes are tracked centrally by the registry across attach, detach, recycle, and dispose paths.
    private val holderRegistry = LazyHolderRegistry<LazyListViewHolder> { holder ->
        holder.recycle()
    }
    private var lastIdentityWarning: String? = null
    private var attachedRecyclerView: RecyclerView? = null
    private val stableIds = linkedMapOf<Any, Long>()
    private val viewTypes = linkedMapOf<Any, Int>()
    private var nextStableId = 0L
    private var nextViewType = 1
    private var itemsVersion = 0L
    private var stickyHeaderDisposer: (() -> Unit)? = null

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
        val key = items[position].key ?: return Long.MIN_VALUE + position
        return stableIds.getOrPut(key) { nextStableId++ }
    }

    fun itemKeyAt(position: Int): Any? = items.getOrNull(position)?.key

    fun itemContentTypeAt(position: Int): Any? = items.getOrNull(position)?.contentType

    fun itemSpanAt(position: Int): Int = items.getOrNull(position)?.span ?: 1

    fun isStickyHeader(position: Int): Boolean {
        return items.getOrNull(position)?.kind == LazyListItemKind.StickyHeader
    }

    fun hasStickyHeaders(): Boolean = items.any { item ->
        item.kind == LazyListItemKind.StickyHeader
    }

    fun findStickyHeaderPosition(itemPosition: Int): Int {
        if (itemPosition < 0 || items.isEmpty()) {
            return RecyclerView.NO_POSITION
        }
        for (position in itemPosition.coerceAtMost(items.lastIndex) downTo 0) {
            if (isStickyHeader(position)) {
                return position
            }
        }
        return RecyclerView.NO_POSITION
    }

    fun currentItemsVersion(): Long = itemsVersion

    fun createDetachedHolder(
        parent: ViewGroup,
        position: Int,
    ): LazyListViewHolder {
        return onCreateViewHolder(parent, getItemViewType(position)).also { holder ->
            holder.bind(items[position])
        }
    }

    fun rebindDetachedHolder(
        holder: LazyListViewHolder,
        position: Int,
    ) {
        holder.bind(items[position])
    }

    fun recycleDetachedHolder(holder: LazyListViewHolder) {
        holder.recycle()
    }

    fun setStickyHeaderDisposer(disposer: (() -> Unit)?) {
        stickyHeaderDisposer = disposer
    }

    fun submitItems(items: List<LazyListItem>) {
        warnAboutIdentityIssues(items)
        val result = LazyListDiff.calculate(
            previous = this.items,
            next = items,
        )
        // Preserve the first visible-item anchor when diffing falls back, reducing jumps after notifyDataSetChanged.
        // When incremental diff is unavailable, preserve the first visible item anchor to reduce jump after notifyDataSetChanged.
        val reloadAnchor = if (result.diffResult == null) {
            captureScrollAnchor()
        } else {
            null
        }
        this.items = result.items
        itemsVersion += 1
        if (result.diffResult != null) {
            result.diffResult.dispatchUpdatesTo(this)
        } else {
            notifyDataSetChanged()
            restoreScrollAnchor(reloadAnchor)
        }
        if (result.updates.isEmpty()) {
            // Rebind visible holders even when structure is stable so external state reaches item content.
            // When structure is unchanged, still rebind visible holders so external state changes reach item content.
            holderRegistry.forEachBound { holder ->
                val position = holder.bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION && position < this.items.size) {
                    holder.bind(this.items[position])
                }
            }
        }
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
        val disposeStickyHeader = stickyHeaderDisposer
        stickyHeaderDisposer = null
        disposeStickyHeader?.invoke()
        holderRegistry.disposeAll()
        items = emptyList()
        itemsVersion += 1
    }

    private fun bindHolder(
        holder: LazyListViewHolder,
        position: Int,
        payload: Any?,
    ) {
        ensureContainerLayoutParams(holder)
        holderRegistry.onBound(holder)
        holder.bind(
            item = items[position],
            payload = payload,
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

/**
 * Linear item-spacing decoration for LazyColumn and LazyRow.
 * Linear spacing decoration for LazyColumn/LazyRow.
 *
 * Adds spacing only before non-first items so list-edge padding semantics remain unchanged.
 * Spacing is added only before non-first items so list edge padding semantics stay unchanged.
 */
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

/**
 * ViewHolder that binds each lazy item to an isolated render session.
 * ViewHolder for lazy items, binding each item to an isolated render session.
 */
internal class LazyListViewHolder(
    private val container: FrameLayout,
) : RecyclerView.ViewHolder(container) {
    private val controller = LazyItemSessionController(
        createSession = { item ->
            item.sessionFactory.create(container.asRenderContainerHandle())
        },
        clearContainer = container::removeAllViews,
    )

    fun bind(
        item: LazyListItem,
        payload: Any? = null,
    ) {
        controller.bind(
            item = item,
            payload = payload,
        )
    }

    fun recycle() {
        controller.recycle()
    }
}
