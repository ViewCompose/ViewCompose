package com.viewcompose.paging

import com.viewcompose.ui.foundation.LazyItemContentFactory
import com.viewcompose.ui.foundation.SideEffect
import com.viewcompose.ui.foundation.UiTreeBuilder
import com.viewcompose.ui.foundation.lazyItemContentFactory
import com.viewcompose.ui.modifier.Modifier
import com.viewcompose.ui.node.LazyItemTable
import com.viewcompose.ui.node.LazyItemTableUpdate
import com.viewcompose.ui.node.LazyListItem
import com.viewcompose.ui.node.NodeType
import com.viewcompose.ui.node.policy.CollectionMotionPolicy
import com.viewcompose.ui.node.policy.CollectionReusePolicy
import com.viewcompose.ui.node.policy.LazyContentPadding
import com.viewcompose.ui.node.policy.LazyLayoutPrefetchPolicy
import com.viewcompose.ui.node.spec.LazyColumnNodeProps
import com.viewcompose.ui.state.LazyListState
import com.viewcompose.ui.unit.UiDp

/**
 * Displays a placeholder-disabled Paging presentation in ViewCompose's native lazy column.
 *
 * Loaded metadata remains proportional to loaded items, renderer inspection never triggers
 * AndroidX loading, and access hints run only after an item Session commits. This overload rejects
 * a presentation containing unloaded slots; use the explicit placeholder overload instead.
 *
 * @sample com.viewcompose.paging.samples.pagingLazyColumnSample
 * @param T non-null loaded item type
 * @receiver tree builder receiving the lazy-list node
 * @param items remembered Paging presentation collected in the same composition
 * @param key stable, unique logical identity for each loaded item
 * @param contentType physical-tree compatibility class used only for reset and reuse
 * @param contentRevision semantic revision of item content and every changing ordinary capture
 * @param contentPadding padding applied inside the scrolling viewport on all edges
 * @param spacing distance between adjacent presented slots
 * @param state optional caller-owned scroll state updated by the renderer
 * @param reverseLayout whether layout and scroll direction are reversed
 * @param userScrollEnabled whether user gestures may scroll the list
 * @param prefetchPolicy renderer View/Session preparation policy; it does not load repository data
 * @param reusePolicy physical item-presentation reuse limits
 * @param motionPolicy item-placement and change animation policy
 * @param modifier modifiers applied to the lazy-list root
 * @param itemContent delayed content for one loaded item
 * @throws IllegalStateException when the presentation contains an unloaded slot or [itemContent]
 * emits zero or multiple roots
 * @throws IllegalArgumentException when [key] selects duplicate loaded-item identities
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
    emitPagingLazyColumn(
        items = items,
        key = key,
        contentType = contentType,
        contentRevision = contentRevision,
        placeholderContentRevision = null,
        placeholderContentType = null,
        placeholderContent = null,
        contentPadding = contentPadding,
        spacing = spacing,
        state = state,
        reverseLayout = reverseLayout,
        userScrollEnabled = userScrollEnabled,
        prefetchPolicy = prefetchPolicy,
        reusePolicy = reusePolicy,
        motionPolicy = motionPolicy,
        modifier = modifier,
        itemContent = itemContent,
    )
}

/**
 * Displays loaded Paging items and positional placeholders in a native lazy column.
 *
 * One coherent presentation becomes a compact [LazyItemTable]: metadata is proportional to loaded
 * items, while unloaded leading and trailing slots are computed by position. Loaded items own
 * Sessions and saveable state by application [key]. A placeholder owns generation-and-position
 * identity but retains no saveable state, so loading or dropping a page cannot transfer state
 * between placeholder and data. Accepted Paging events expose bounded range updates; unusual count
 * estimate changes use full adapter invalidation without materializing placeholder objects.
 *
 * The framework supplies no placeholder visual, wording, shimmer, or retry policy.
 * [placeholderContentRevision] must change with every changing ordinary value captured by
 * [placeholderContent]. Both content callbacks must emit exactly one root node.
 *
 * @sample com.viewcompose.paging.samples.pagingLazyColumnSample
 * @param T non-null loaded item type
 * @receiver tree builder receiving the lazy-list node
 * @param items remembered Paging presentation collected in the same composition
 * @param key stable, unique logical identity for each loaded item
 * @param placeholderContentRevision semantic revision of ordinary placeholder captures
 * @param placeholderContent delayed content for one unloaded presented index
 * @param contentType physical-tree compatibility class for loaded items
 * @param contentRevision semantic revision of loaded content and ordinary captures
 * @param placeholderContentType physical-tree compatibility class for placeholders
 * @param contentPadding padding applied inside the scrolling viewport on all edges
 * @param spacing distance between adjacent presented slots
 * @param state optional caller-owned scroll state updated by the renderer
 * @param reverseLayout whether layout and scroll direction are reversed
 * @param userScrollEnabled whether user gestures may scroll the list
 * @param prefetchPolicy renderer View/Session preparation policy; it does not load repository data
 * @param reusePolicy physical item-presentation reuse limits
 * @param motionPolicy item-placement and change animation policy
 * @param modifier modifiers applied to the lazy-list root
 * @param itemContent delayed content for one loaded item
 * @throws IllegalStateException when either content callback emits zero or multiple roots
 * @throws IllegalArgumentException when [key] selects duplicate loaded-item identities
 */
fun <T : Any> UiTreeBuilder.PagingLazyColumn(
    items: ViewComposePagingItems<T>,
    key: (T) -> Any,
    placeholderContentRevision: Any,
    placeholderContent: UiTreeBuilder.(index: Int) -> Unit,
    contentType: (T) -> Any? = { null },
    contentRevision: (T) -> Any? = { it },
    placeholderContentType: Any? = null,
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
    emitPagingLazyColumn(
        items = items,
        key = key,
        contentType = contentType,
        contentRevision = contentRevision,
        placeholderContentRevision = placeholderContentRevision,
        placeholderContentType = placeholderContentType,
        placeholderContent = placeholderContent,
        contentPadding = contentPadding,
        spacing = spacing,
        state = state,
        reverseLayout = reverseLayout,
        userScrollEnabled = userScrollEnabled,
        prefetchPolicy = prefetchPolicy,
        reusePolicy = reusePolicy,
        motionPolicy = motionPolicy,
        modifier = modifier,
        itemContent = itemContent,
    )
}

private fun <T : Any> UiTreeBuilder.emitPagingLazyColumn(
    items: ViewComposePagingItems<T>,
    key: (T) -> Any,
    contentType: (T) -> Any?,
    contentRevision: (T) -> Any?,
    placeholderContentRevision: Any?,
    placeholderContentType: Any?,
    placeholderContent: (UiTreeBuilder.(index: Int) -> Unit)?,
    contentPadding: UiDp,
    spacing: UiDp,
    state: LazyListState?,
    reverseLayout: Boolean,
    userScrollEnabled: Boolean,
    prefetchPolicy: LazyLayoutPrefetchPolicy,
    reusePolicy: CollectionReusePolicy,
    motionPolicy: CollectionMotionPolicy,
    modifier: Modifier,
    itemContent: UiTreeBuilder.(T) -> Unit,
) {
    val presentation = items.presentationForLazyColumn()
    val hasPlaceholders = presentation.placeholdersBefore > 0 || presentation.placeholdersAfter > 0
    check(!hasPlaceholders || placeholderContent != null) {
        "PagingLazyColumn requires placeholderContent when the presentation contains unloaded slots."
    }
    val loadedSubmission = buildLoadedSubmission(
        owner = items,
        presentation = presentation,
        key = key,
        contentType = contentType,
        contentRevision = contentRevision,
    )
    val itemFactory = lazyItemContentFactory<PagingLazyItemPayload<T>>(
        retainedKeys = loadedSubmission.positionsByKey.keys,
    ) { payload ->
        SideEffect {
            items.requestLoadForActiveItem(payload.presenterIndex)
        }
        when (payload) {
            is PagingLazyItemPayload.Loaded -> itemContent(payload.value)
            is PagingLazyItemPayload.Placeholder -> checkNotNull(placeholderContent)(
                payload.presenterIndex,
            )
        }
    }
    val table = PagingLazyItemTable(
        owner = items,
        presentation = presentation,
        loadedSubmission = loadedSubmission,
        itemFactory = itemFactory,
        placeholderContentRevision = placeholderContentRevision,
        placeholderContentType = placeholderContentType,
    )
    emit(
        type = NodeType.LazyColumn,
        spec = LazyColumnNodeProps(
            contentPadding = LazyContentPadding.all(contentPadding),
            spacing = spacing,
            items = table,
            state = state,
            reverseLayout = reverseLayout,
            userScrollEnabled = userScrollEnabled,
            prefetchPolicy = prefetchPolicy,
            reusePolicy = reusePolicy,
            motionPolicy = motionPolicy,
        ),
        modifier = modifier,
    )
}

private class PagingLazyItemTable<T : Any>(
    private val owner: ViewComposePagingItems<T>,
    private val presentation: PagingPresentation<T>,
    private val loadedSubmission: PagingLoadedSubmission<T>,
    private val itemFactory: LazyItemContentFactory<PagingLazyItemPayload<T>>,
    private val placeholderContentRevision: Any?,
    private val placeholderContentType: Any?,
) : LazyItemTable {
    override val size: Int
        get() = presentation.itemCount

    override fun get(index: Int): LazyListItem {
        if (index !in 0 until size) {
            throw IndexOutOfBoundsException(
                "Paging lazy item index $index is outside 0 until $size.",
            )
        }
        val loadedIndex = index - presentation.placeholdersBefore
        return if (loadedIndex in loadedSubmission.items.indices) {
            val loaded = loadedSubmission.items[loadedIndex]
            itemFactory.createItem(
                key = loaded.key,
                contentRevision = PagingItemContentRevision(
                    itemRevision = loaded.contentRevision,
                    presenterIndex = index,
                ),
                contentType = loaded.contentType,
                payload = PagingLazyItemPayload.Loaded(
                    presenterIndex = index,
                    value = loaded.value,
                ),
            )
        } else {
            val placeholderKey = PagingPlaceholderKey(
                owner = owner,
                generation = presentation.generation,
                presenterIndex = index,
            )
            itemFactory.createItem(
                key = placeholderKey,
                contentRevision = placeholderContentRevision,
                contentType = PagingPlaceholderContentType(placeholderContentType),
                payload = PagingLazyItemPayload.Placeholder(index),
            )
        }
    }

    override fun indexOfKey(key: Any): Int {
        loadedSubmission.positionsByKey[key]?.let { return it }
        val placeholder = key as? PagingPlaceholderKey ?: return -1
        if (placeholder.owner !== owner || placeholder.generation != presentation.generation) {
            return -1
        }
        val index = placeholder.presenterIndex
        val loadedStart = presentation.placeholdersBefore
        val loadedEnd = loadedStart + loadedSubmission.items.size
        return if (index in 0 until size && index !in loadedStart until loadedEnd) index else -1
    }

    override fun updatesFrom(previous: LazyItemTable): List<LazyItemTableUpdate>? {
        val old = previous as? PagingLazyItemTable<*> ?: return null
        if (old.owner !== owner) return null
        if (old.presentation.itemRevision == presentation.itemRevision) {
            return if (hasSameDeclarationSemantics(old)) {
                emptyList()
            } else {
                listOf(LazyItemTableUpdate.ReloadAll)
            }
        }
        if (old.presentation.itemRevision == presentation.previousItemRevision) {
            if (!hasSameConfiguration(old) || hasChangedRetainedLoadedMetadata(old)) {
                return listOf(LazyItemTableUpdate.ReloadAll)
            }
            return presentation.itemUpdates
        }
        return listOf(LazyItemTableUpdate.ReloadAll)
    }

    private fun hasSameDeclarationSemantics(old: PagingLazyItemTable<*>): Boolean {
        if (!hasSameConfiguration(old)) return false
        if (old.loadedSubmission.items.size != loadedSubmission.items.size) return false
        return loadedSubmission.items.indices.all { index ->
            val previous = old.loadedSubmission.items[index]
            val next = loadedSubmission.items[index]
            previous.key == next.key &&
                previous.contentType == next.contentType &&
                previous.contentRevision == next.contentRevision
        }
    }

    private fun hasSameConfiguration(old: PagingLazyItemTable<*>): Boolean {
        return old.itemFactory.environmentRevision == itemFactory.environmentRevision &&
            old.placeholderContentRevision == placeholderContentRevision &&
            old.placeholderContentType == placeholderContentType
    }

    private fun hasChangedRetainedLoadedMetadata(old: PagingLazyItemTable<*>): Boolean {
        loadedSubmission.items.forEach { next ->
            val oldPosition = old.loadedSubmission.positionsByKey[next.key] ?: return@forEach
            val oldLoadedIndex = oldPosition - old.presentation.placeholdersBefore
            val previous = old.loadedSubmission.items.getOrNull(oldLoadedIndex) ?: return true
            if (
                previous.contentType != next.contentType ||
                previous.contentRevision != next.contentRevision
            ) {
                return true
            }
        }
        return false
    }
}

private data class PagingLoadedSubmission<T : Any>(
    val items: List<PagingLoadedMetadata<T>>,
    val positionsByKey: Map<Any, Int>,
)

private data class PagingLoadedMetadata<T : Any>(
    val value: T,
    val key: Any,
    val contentType: Any?,
    val contentRevision: Any?,
)

private sealed interface PagingLazyItemPayload<out T : Any> {
    val presenterIndex: Int

    data class Loaded<T : Any>(
        override val presenterIndex: Int,
        val value: T,
    ) : PagingLazyItemPayload<T>

    data class Placeholder(
        override val presenterIndex: Int,
    ) : PagingLazyItemPayload<Nothing>
}

private data class PagingItemContentRevision(
    val itemRevision: Any?,
    val presenterIndex: Int,
)

private data class PagingPlaceholderKey(
    val owner: Any,
    val generation: Long,
    val presenterIndex: Int,
)

private data class PagingLoadedKey(
    val owner: Any,
    val applicationKey: Any,
)

private data class PagingPlaceholderContentType(
    val applicationType: Any?,
)

private fun <T : Any> buildLoadedSubmission(
    owner: ViewComposePagingItems<T>,
    presentation: PagingPresentation<T>,
    key: (T) -> Any,
    contentType: (T) -> Any?,
    contentRevision: (T) -> Any?,
): PagingLoadedSubmission<T> {
    val metadata = ArrayList<PagingLoadedMetadata<T>>(presentation.items.size)
    val positionsByKey = HashMap<Any, Int>(mapCapacity(presentation.items.size))
    val applicationKeys = HashSet<Any>(mapCapacity(presentation.items.size))
    presentation.items.forEachIndexed { loadedIndex, value ->
        val applicationKey = key(value)
        val presenterIndex = presentation.placeholdersBefore + loadedIndex
        require(applicationKeys.add(applicationKey)) {
            "PagingLazyColumn keys must be unique. Duplicate key: $applicationKey"
        }
        val itemKey = PagingLoadedKey(owner, applicationKey)
        positionsByKey[itemKey] = presenterIndex
        metadata += PagingLoadedMetadata(
            value = value,
            key = itemKey,
            contentType = contentType(value),
            contentRevision = contentRevision(value),
        )
    }
    return PagingLoadedSubmission(
        items = metadata,
        positionsByKey = positionsByKey,
    )
}

private fun mapCapacity(size: Int): Int {
    if (size <= 0) return 0
    return ((size / 0.75f) + 1).toInt()
}
