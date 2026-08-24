package com.viewcompose.paging

import com.viewcompose.ui.foundation.LazyColumn
import com.viewcompose.ui.foundation.UiTreeBuilder
import com.viewcompose.ui.modifier.Modifier
import com.viewcompose.ui.node.policy.CollectionMotionPolicy
import com.viewcompose.ui.node.policy.CollectionReusePolicy
import com.viewcompose.ui.node.policy.LazyLayoutPrefetchPolicy
import com.viewcompose.ui.state.LazyListState
import com.viewcompose.ui.unit.UiDp

/**
 * Displays loaded Paging items in ViewCompose's native virtualized vertical list.
 *
 * This overload requires placeholders to be disabled. It reads one coherent presentation, forwards
 * application keys, content types, and semantic revisions to the standard [LazyColumn], and sends a
 * Paging access hint only when the renderer activates that item's Session. The current presenter
 * index is folded into the internal content revision, so moving an unchanged key refreshes its load
 * routing without replacing the key-owned Session or its saveable state. Renderer reconciliation,
 * stable IDs, View reuse, scrolling, semantics, and item-state ownership remain unchanged.
 *
 * Each accepted presentation currently builds one loaded-item declaration and key table, so
 * composition and reconciliation are linear in [ViewComposePagingItems.loadedItemCount]. The later
 * placeholder slice replaces this full-table boundary with a compact neutral indexed contract.
 * [modifier] affects the list root in normal chain order; padding and spacing resolve through the
 * current density and layout direction, while [itemContent] inherits the captured environment and
 * must emit exactly one root node per item.
 *
 * @sample com.viewcompose.paging.samples.pagingLazyColumnSample
 * @param T non-null loaded item type
 * @receiver tree builder receiving the lazy-list node
 * @param items remembered Paging presentation collected in the same composition
 * @param key stable, unique logical identity for each loaded item
 * @param contentType physical-tree compatibility class used only for reset and reuse
 * @param contentRevision semantic revision of item content and every changing ordinary capture
 * @param contentPadding padding applied inside the scrolling viewport on all edges
 * @param spacing distance between adjacent loaded items
 * @param state optional caller-owned scroll state updated by the renderer
 * @param reverseLayout whether layout and scroll direction are reversed
 * @param userScrollEnabled whether user gestures may scroll the list
 * @param prefetchPolicy renderer View/Session preparation policy; it does not load repository data
 * @param reusePolicy physical item-presentation reuse limits
 * @param motionPolicy item-placement and change animation policy
 * @param modifier modifiers applied to the lazy-list root
 * @param itemContent delayed content for one loaded item; exactly one root node is required
 * @throws IllegalStateException when the presentation contains an unloaded slot or item content
 * emits zero or multiple roots
 * @throws IllegalArgumentException when [key] selects duplicate values
 */
fun <T : Any> UiTreeBuilder.PagingLazyColumn(
    items: ViewComposePagingItems<T>,
    key: (T) -> Any,
    contentType: (T) -> Any? = { null },
    contentRevision: (T) -> Any? = { it },
    contentPadding: UiDp = UiDp.Zero,
    spacing: UiDp = UiDp.Zero,
    state: LazyListState? = null,
    reverseLayout: Boolean = false,
    userScrollEnabled: Boolean = true,
    prefetchPolicy: LazyLayoutPrefetchPolicy = LazyLayoutPrefetchPolicy(),
    reusePolicy: CollectionReusePolicy = CollectionReusePolicy(),
    motionPolicy: CollectionMotionPolicy = CollectionMotionPolicy(),
    modifier: Modifier = Modifier,
    itemContent: UiTreeBuilder.(T) -> Unit,
) {
    LazyColumn(
        items = items.loadedItemsForLazyColumn(),
        key = { loaded -> key(loaded.value) },
        contentType = { loaded -> contentType(loaded.value) },
        contentRevision = { loaded ->
            PagingItemContentRevision(
                itemRevision = contentRevision(loaded.value),
                presenterIndex = loaded.index,
            )
        },
        contentPadding = contentPadding,
        spacing = spacing,
        state = state,
        reverseLayout = reverseLayout,
        userScrollEnabled = userScrollEnabled,
        prefetchPolicy = prefetchPolicy,
        reusePolicy = reusePolicy,
        motionPolicy = motionPolicy,
        modifier = modifier,
    ) { loaded ->
        items.requestLoadForActiveItem(loaded.index)
        itemContent(loaded.value)
    }
}

private data class PagingItemContentRevision(
    val itemRevision: Any?,
    val presenterIndex: Int,
)
