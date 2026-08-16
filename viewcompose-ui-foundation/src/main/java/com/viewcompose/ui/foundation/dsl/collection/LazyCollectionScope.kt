package com.viewcompose.ui.foundation

import com.viewcompose.ui.node.LazyListItem
import com.viewcompose.ui.node.LazyListItemKind
import com.viewcompose.ui.node.LazyListItemSession
import com.viewcompose.ui.node.LazyListItemSessionFactory
import com.viewcompose.ui.node.RenderContainerHandle
import com.viewcompose.ui.node.policy.GridItemSpan

/**
 * Declares keyed lazy-list entries whose logical sessions are independent from recycled Views.
 *
 * Keys must be unique in one scope. Equal key, declared content revision, framework environment,
 * content type, kind, and span reuse the canonical item and skip its rendering. Changing ordinary
 * captures must therefore be observed State or participate in the content revision. Typed [items]
 * declarations evaluate their selectors on every parent declaration pass before applying that
 * reuse rule.
 *
 * @sample com.viewcompose.ui.foundation.samples.lazyCollectionRevisionSample
 */
@UiDslMarker
class LazyListScope internal constructor(
    private val collector: LazyItemCollector,
    private val stickyHeadersAllowed: Boolean,
) {
    /**
     * Adds one independently versioned lazy item.
     *
     * @param key unique logical identity that owns remember, saveable state, and effects
     * @param contentType physical-tree compatibility class; equal values promise reset-safe structure
     * @param contentRevision semantic version of every non-State value captured by [content]
     * @param content declaration evaluated when this logical item session renders
     * @throws IllegalArgumentException when [key] duplicates another declaration in this scope
     */
    fun item(
        key: Any,
        contentType: Any? = null,
        contentRevision: Any? = key,
        content: UiTreeBuilder.() -> Unit,
    ) {
        collector.add(
            key = key,
            contentType = contentType,
            contentRevision = contentRevision,
            kind = LazyListItemKind.Item,
            span = GridItemSpan.Single,
            content = content,
        )
    }

    /**
     * Adds independently keyed and versioned lazy items from [items].
     *
     * Selectors run on every parent declaration pass so order, membership, type, and revision
     * changes cannot be hidden behind an inaccurate aggregate token. Equal key, selected revision,
     * framework environment, type, kind, and span reuse the canonical logical item and its session
     * binding. Observable State read by [itemContent] remains independently invalidated.
     *
     * @param T application item type
     * @param items immutable submission iterated in display order
     * @param key unique logical identity selector
     * @param contentType physical-tree compatibility selector
     * @param contentRevision semantic revision selector; immutable values default to themselves
     * @param itemContent declaration evaluated for the item when its logical session renders
     * @throws IllegalArgumentException when selected keys are not unique
     */
    fun <T> items(
        items: List<T>,
        key: (T) -> Any,
        contentType: (T) -> Any? = { null },
        contentRevision: (T) -> Any? = { it },
        itemContent: UiTreeBuilder.(T) -> Unit,
    ) {
        collector.addTypedItems(
            items = items,
            key = key,
            contentType = contentType,
            contentRevision = contentRevision,
            kind = LazyListItemKind.Item,
            span = { GridItemSpan.Single },
            itemContent = itemContent,
        )
    }

    /**
     * Adds one independently versioned sticky header to a vertical list.
     *
     * @param key unique logical identity that owns header state and effects
     * @param contentType physical-tree compatibility class for renderer reuse
     * @param contentRevision semantic version of every non-State value captured by [content]
     * @param content declaration evaluated when the header session renders
     * @throws IllegalArgumentException when used by `LazyRow` or when [key] is duplicated
     */
    fun stickyHeader(
        key: Any,
        contentType: Any? = null,
        contentRevision: Any? = key,
        content: UiTreeBuilder.() -> Unit,
    ) {
        require(stickyHeadersAllowed) {
            "stickyHeader is supported by LazyColumn only. Use a normal item in LazyRow."
        }
        collector.add(
            key = key,
            contentType = contentType,
            contentRevision = contentRevision,
            kind = LazyListItemKind.StickyHeader,
            span = GridItemSpan.FullLine,
            content = content,
        )
    }
}

/**
 * Declares keyed lazy-grid entries with spans and separated logical and physical ownership.
 *
 * Typed [items] declarations evaluate their selectors on every parent declaration pass. Equal item
 * identity and revisions still reuse canonical logical items and their sessions.
 *
 * @sample com.viewcompose.ui.foundation.samples.lazyCollectionRevisionSample
 */
@UiDslMarker
class LazyGridScope internal constructor(
    private val collector: LazyItemCollector,
) {
    /**
     * Adds one independently versioned grid item.
     *
     * @param key unique logical identity that owns remember, saveable state, and effects
     * @param contentType physical-tree compatibility class for renderer reuse
     * @param contentRevision semantic version of every non-State value captured by [content]
     * @param span renderer-neutral cell-span policy
     * @param content declaration evaluated when this logical item session renders
     * @throws IllegalArgumentException when [key] is duplicated
     */
    fun item(
        key: Any,
        contentType: Any? = null,
        contentRevision: Any? = key,
        span: GridItemSpan = GridItemSpan.Single,
        content: UiTreeBuilder.() -> Unit,
    ) {
        collector.add(
            key = key,
            contentType = contentType,
            contentRevision = contentRevision,
            kind = LazyListItemKind.Item,
            span = span,
            content = content,
        )
    }

    /**
     * Adds independently keyed and versioned grid items from [items].
     *
     * Selectors run on every parent declaration pass. Equal key, selected revision, framework
     * environment, type, kind, and span reuse the canonical logical item and its session binding.
     *
     * @param T application item type
     * @param items immutable submission iterated in display order
     * @param key unique logical identity selector
     * @param contentType physical-tree compatibility selector
     * @param contentRevision semantic revision selector; immutable values default to themselves
     * @param span renderer-neutral cell-span selector
     * @param itemContent declaration evaluated for the item when its logical session renders
     * @throws IllegalArgumentException when keys are duplicated
     */
    fun <T> items(
        items: List<T>,
        key: (T) -> Any,
        contentType: (T) -> Any? = { null },
        contentRevision: (T) -> Any? = { it },
        span: (T) -> GridItemSpan = { GridItemSpan.Single },
        itemContent: UiTreeBuilder.(T) -> Unit,
    ) {
        collector.addTypedItems(
            items = items,
            key = key,
            contentType = contentType,
            contentRevision = contentRevision,
            kind = LazyListItemKind.Item,
            span = span,
            itemContent = itemContent,
        )
    }

    /**
     * Adds one independently versioned sticky header spanning the full grid row.
     *
     * @param key unique logical identity that owns header state and effects
     * @param contentType physical-tree compatibility class for renderer reuse
     * @param contentRevision semantic version of every non-State value captured by [content]
     * @param content declaration evaluated when the header session renders
     * @throws IllegalArgumentException when [key] duplicates another declaration in this scope
     */
    fun stickyHeader(
        key: Any,
        contentType: Any? = null,
        contentRevision: Any? = key,
        content: UiTreeBuilder.() -> Unit,
    ) {
        collector.add(
            key = key,
            contentType = contentType,
            contentRevision = contentRevision,
            kind = LazyListItemKind.StickyHeader,
            span = GridItemSpan.FullLine,
            content = content,
        )
    }
}

/**
 * Lazy-item collector responsible for key uniqueness, locals capture, and item session factory creation.
 */
internal class LazyItemCollector(
    private val localSnapshot: LocalSnapshot,
    private val saveableStateHolder: SaveableStateHolder?,
    private val reuseCache: LazyItemCanonicalReuseCache,
) {
    private val baselineGeneration = reuseCache.generation
    private val baselineHasCommittedSubmission = reuseCache.hasCommittedSubmission
    private var mutableItemsByKey: HashMap<Any, LazyListItem>? = null
    private var mutableItems: ArrayList<LazyListItem>? = null
    private var displacedCommittedItemsByKey: HashMap<Any, LazyListItem>? = null
    private var containsNewKey = false
    private var matchesCommittedOrder = baselineHasCommittedSubmission

    fun prepareForAdditionalItems(count: Int) {
        if (count <= 0) return
        val items = mutableItems()
        items.ensureCapacity(items.size + count)
        if (mutableItemsByKey == null) {
            mutableItemsByKey = HashMap(((count / HASH_SET_LOAD_FACTOR) + 1).toInt())
        }
    }

    fun add(
        key: Any,
        contentType: Any?,
        contentRevision: Any?,
        kind: LazyListItemKind,
        span: GridItemSpan,
        content: UiTreeBuilder.() -> Unit,
    ) {
        addCanonical(
            key = key,
            contentType = contentType,
            contentRevision = contentRevision,
            kind = kind,
            span = span,
            contentFactory = { content },
        )
    }

    fun <T> addTypedItems(
        items: List<T>,
        key: (T) -> Any,
        contentType: (T) -> Any?,
        contentRevision: (T) -> Any?,
        kind: LazyListItemKind,
        span: (T) -> GridItemSpan,
        itemContent: UiTreeBuilder.(T) -> Unit,
    ) {
        prepareForAdditionalItems(items.size)
        items.forEach { item ->
            addCanonical(
                key = key(item),
                contentType = contentType(item),
                contentRevision = contentRevision(item),
                kind = kind,
                span = span(item),
                contentFactory = { { itemContent(item) } },
            )
        }
    }

    private inline fun addCanonical(
        key: Any,
        contentType: Any?,
        contentRevision: Any?,
        kind: LazyListItemKind,
        span: GridItemSpan,
        contentFactory: () -> UiTreeBuilder.() -> Unit,
    ) {
        val canonicalSpan = span.canonical()
        val committedItem = reuseCache.committedItem(key)
        val lazyItem = reuseCache.findReusable(
            committedItem = committedItem,
            key = key,
            contentRevision = contentRevision,
            environmentRevision = localSnapshot,
            contentType = contentType,
            kind = kind,
            span = canonicalSpan,
        ) ?: run {
            val sessionBinding = WidgetLazyItemSessionBinding(
                localSnapshot = localSnapshot,
                saveableStateHolder = saveableStateHolder,
                saveableStateKey = key,
                content = contentFactory(),
            )
            LazyListItem(
                key = key,
                contentRevision = contentRevision,
                environmentRevision = localSnapshot,
                contentType = contentType,
                kind = kind,
                span = canonicalSpan,
                sessionFactory = sessionBinding,
                sessionUpdater = sessionBinding,
            )
        }
        val itemsByKey = mutableItemsByKey()
        require(itemsByKey.putIfAbsent(key, lazyItem) == null) {
            "Lazy collection keys must be unique. Duplicate key: $key"
        }
        val items = mutableItems()
        if (committedItem == null) {
            containsNewKey = true
        } else if (lazyItem !== committedItem) {
            val displacedItems = displacedCommittedItemsByKey
                ?: HashMap<Any, LazyListItem>().also { displacedCommittedItemsByKey = it }
            displacedItems[key] = committedItem
        }
        if (matchesCommittedOrder && lazyItem !== reuseCache.committedItemAt(items.size)) {
            matchesCommittedOrder = false
        }
        items.add(lazyItem)
    }

    fun build(): List<LazyListItem> {
        val committedItemsByKey = mutableItemsByKey.orEmpty()
        val candidateItems = mutableItems.orEmpty()
        val matchesCommittedSubmission = matchesCommittedOrder &&
            candidateItems.size == reuseCache.committedSize
        val committedItems = if (matchesCommittedSubmission) {
            reuseCache.committedItems
        } else {
            candidateItems
        }
        if (ComposerContext.currentComposer() != null) {
            val keyMembershipChanged = !baselineHasCommittedSubmission ||
                containsNewKey || committedItemsByKey.size != reuseCache.committedSize
            val displacedItems = displacedCommittedItemsByKey.orEmpty()
            SideEffect {
                if (matchesCommittedSubmission && baselineGeneration == reuseCache.generation) {
                    return@SideEffect
                }
                val keyMembershipChangedAtCommit = reuseCache.hasKeyMembershipChanged(
                    baselineGeneration = baselineGeneration,
                    precomputedResult = keyMembershipChanged,
                    itemsByKey = committedItemsByKey,
                )
                if (keyMembershipChangedAtCommit) {
                    saveableStateHolder?.retainKeys(committedItemsByKey.keys)
                }
                reuseCache.commit(
                    baselineGeneration = baselineGeneration,
                    items = committedItems,
                    itemsByKey = committedItemsByKey,
                    displacedCommittedItemsByKey = displacedItems,
                    keyMembershipChanged = keyMembershipChangedAtCommit,
                )
            }
        }
        // The collector is frame-local and receives no writes after build, so the read-only view is
        // already an immutable submission without copying every item and key a second time.
        return committedItems
    }

    private fun mutableItemsByKey(): HashMap<Any, LazyListItem> {
        return mutableItemsByKey ?: HashMap<Any, LazyListItem>().also { mutableItemsByKey = it }
    }

    private fun mutableItems(): ArrayList<LazyListItem> {
        return mutableItems ?: ArrayList<LazyListItem>().also { mutableItems = it }
    }

    private companion object {
        private const val HASH_SET_LOAD_FACTOR = 0.75f
    }
}

/** Retains the committed generation and at most one previous semantic variant for each current key. */
internal class LazyItemCanonicalReuseCache {
    var committedItems: List<LazyListItem> = emptyList()
        private set
    private var committedItemsByKey: Map<Any, LazyListItem> = emptyMap()
    private val previousVariantsByKey = HashMap<Any, LazyListItem>()
    var hasCommittedSubmission: Boolean = false
        private set
    var generation: Long = 0L
        private set

    val committedSize: Int
        get() = committedItemsByKey.size

    fun committedItem(key: Any): LazyListItem? = committedItemsByKey[key]

    fun committedItemAt(index: Int): LazyListItem? = committedItems.getOrNull(index)

    fun findReusable(
        committedItem: LazyListItem?,
        key: Any,
        contentRevision: Any?,
        environmentRevision: Any?,
        contentType: Any?,
        kind: LazyListItemKind,
        span: GridItemSpan,
    ): LazyListItem? {
        return committedItem
            ?.takeIf { item -> item.matches(contentRevision, environmentRevision, contentType, kind, span) }
            ?: previousVariantsByKey[key]?.takeIf { item ->
                item.matches(contentRevision, environmentRevision, contentType, kind, span)
            }
    }

    private fun LazyListItem.matches(
        contentRevision: Any?,
        environmentRevision: Any?,
        contentType: Any?,
        kind: LazyListItemKind,
        span: GridItemSpan,
    ): Boolean {
        return this.contentRevision == contentRevision &&
            this.environmentRevision == environmentRevision &&
            this.contentType == contentType &&
            this.kind == kind &&
            this.span == span
    }

    fun hasKeyMembershipChanged(
        baselineGeneration: Long,
        precomputedResult: Boolean,
        itemsByKey: Map<Any, LazyListItem>,
    ): Boolean {
        if (baselineGeneration == generation) return precomputedResult
        if (!hasCommittedSubmission || itemsByKey.size != committedItemsByKey.size) return true
        return itemsByKey.keys.any { key -> !committedItemsByKey.containsKey(key) }
    }

    fun commit(
        baselineGeneration: Long,
        items: List<LazyListItem>,
        itemsByKey: Map<Any, LazyListItem>,
        displacedCommittedItemsByKey: Map<Any, LazyListItem>,
        keyMembershipChanged: Boolean,
    ) {
        if (baselineGeneration == generation) {
            applyPreparedPreviousVariants(
                itemsByKey = itemsByKey,
                displacedCommittedItemsByKey = displacedCommittedItemsByKey,
                keyMembershipChanged = keyMembershipChanged,
            )
        } else {
            val rebuiltPreviousVariants = rebuildPreviousVariants(itemsByKey)
            previousVariantsByKey.clear()
            previousVariantsByKey.putAll(rebuiltPreviousVariants)
        }
        committedItems = items
        committedItemsByKey = itemsByKey
        hasCommittedSubmission = true
        generation += 1
    }

    private fun applyPreparedPreviousVariants(
        itemsByKey: Map<Any, LazyListItem>,
        displacedCommittedItemsByKey: Map<Any, LazyListItem>,
        keyMembershipChanged: Boolean,
    ) {
        displacedCommittedItemsByKey.forEach { (key, item) ->
            previousVariantsByKey[key] = item
        }
        if (keyMembershipChanged) {
            previousVariantsByKey.keys.retainAll(itemsByKey.keys)
        }
    }

    private fun rebuildPreviousVariants(
        itemsByKey: Map<Any, LazyListItem>,
    ): HashMap<Any, LazyListItem> {
        var rebuilt: HashMap<Any, LazyListItem>? = null
        itemsByKey.forEach { (key, nextItem) ->
            val currentItem = committedItemsByKey[key]
            val previousItem = when {
                currentItem == null -> null
                nextItem !== currentItem -> currentItem
                else -> previousVariantsByKey[key]
            }
            if (previousItem != null) {
                val variants = rebuilt ?: HashMap<Any, LazyListItem>().also { rebuilt = it }
                variants[key] = previousItem
            }
        }
        return rebuilt ?: HashMap()
    }
}

private class WidgetLazyItemSessionBinding(
    private val localSnapshot: LocalSnapshot,
    private val saveableStateHolder: SaveableStateHolder?,
    private val saveableStateKey: Any,
    private val content: UiTreeBuilder.() -> Unit,
) : LazyListItemSessionFactory, (LazyListItemSession) -> Unit {
    override fun create(container: RenderContainerHandle) =
        WidgetLazyListItemSession(
            container = container,
            localSnapshot = localSnapshot,
            saveableStateHolder = saveableStateHolder,
            saveableStateKey = saveableStateKey,
            content = content,
        )

    override fun invoke(session: LazyListItemSession) {
        (session as WidgetLazyListItemSession).updateContent(
            localSnapshot = localSnapshot,
            content = content,
        )
    }
}

private fun GridItemSpan.canonical(): GridItemSpan {
    return if (this is GridItemSpan.Fixed && count == 1) {
        GridItemSpan.Single
    } else {
        this
    }
}
