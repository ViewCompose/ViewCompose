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
 * Keys must be unique in one scope. Equal key, declared content revision, and framework-captured
 * environment revision skip item rendering. Changing ordinary captures must therefore be observed
 * State or participate in the content revision. Typed [items] declarations may additionally use
 * an explicit complete-snapshot revision to skip declaration evaluation.
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
     * A non-null [snapshotRevision] is an authoritative revision for this entire declaration, not
     * a hint. Equal revision and environment values reuse the exact previously committed item list
     * without invoking [key], [contentType], or [contentRevision]. ViewCompose retains a bounded
     * two-snapshot window. A failed composition does not publish a candidate. Prefer an immutable,
     * constant-time comparable scalar or data version. Every typed declaration in one scope must
     * use a distinct namespaced value, such as a pair of declaration identity and data revision;
     * duplicate non-null values fail before cache lookup. Observable State read by [itemContent]
     * remains independently invalidated.
     *
     * @param T application item type
     * @param items immutable submission iterated in display order
     * @param key unique logical identity selector
     * @param contentType physical-tree compatibility selector
     * @param contentRevision semantic revision selector; immutable values default to themselves
     * @param snapshotRevision optional revision for the complete typed declaration. A non-null value
     * authorizes reuse of the exact logical item snapshot without invoking any selector. It must
     * change when item order, membership, selector results, or non-State captures change. Changing
     * it reevaluates selectors but does not replace an item whose [contentRevision] remains equal,
     * so non-State values read by [itemContent] must also participate in [contentRevision]. `null`
     * disables complete-snapshot reuse and evaluates every selector on every declaration pass
     * @param itemContent declaration evaluated for the item when its logical session renders
     * @throws IllegalArgumentException when selected keys are not unique or another typed
     * declaration in this scope uses the same non-null [snapshotRevision]
     */
    fun <T> items(
        items: List<T>,
        key: (T) -> Any,
        contentType: (T) -> Any? = { null },
        contentRevision: (T) -> Any? = { it },
        snapshotRevision: Any? = null,
        itemContent: UiTreeBuilder.(T) -> Unit,
    ) {
        collector.addTypedItems(
            items = items,
            key = key,
            contentType = contentType,
            contentRevision = contentRevision,
            snapshotRevision = snapshotRevision,
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
 * Typed [items] declarations may supply an authoritative complete-snapshot revision. The revision
 * also covers span selector results; framework environment changes invalidate cached snapshots.
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
     * A non-null [snapshotRevision] is authoritative for the full declaration. Equal revision and
     * environment values reuse the exact committed item list and skip every selector. ViewCompose
     * retains a bounded two-snapshot window and publishes only after successful composition.
     * Prefer an immutable, constant-time comparable scalar or data version. Every typed declaration
     * in one grid scope must use a distinct namespaced value, such as a pair of declaration identity
     * and data revision; duplicate non-null values fail before cache lookup.
     *
     * @param T application item type
     * @param items immutable submission iterated in display order
     * @param key unique logical identity selector
     * @param contentType physical-tree compatibility selector
     * @param contentRevision semantic revision selector; immutable values default to themselves
     * @param span renderer-neutral cell-span selector
     * @param snapshotRevision optional revision for the complete typed declaration. A non-null value
     * authorizes reuse of the exact logical item snapshot without invoking any selector. It must
     * change when item order, membership, selector results, span results, or non-State captures
     * change. Changing it reevaluates selectors but does not replace an item whose
     * [contentRevision] remains equal, so non-State values read by [itemContent] must also
     * participate in [contentRevision]. `null` disables complete-snapshot reuse and evaluates every
     * selector on every pass
     * @param itemContent declaration evaluated for the item when its logical session renders
     * @throws IllegalArgumentException when keys are duplicated or another typed declaration in
     * this scope uses the same non-null [snapshotRevision]
     */
    fun <T> items(
        items: List<T>,
        key: (T) -> Any,
        contentType: (T) -> Any? = { null },
        contentRevision: (T) -> Any? = { it },
        span: (T) -> GridItemSpan = { GridItemSpan.Single },
        snapshotRevision: Any? = null,
        itemContent: UiTreeBuilder.(T) -> Unit,
    ) {
        collector.addTypedItems(
            items = items,
            key = key,
            contentType = contentType,
            contentRevision = contentRevision,
            snapshotRevision = snapshotRevision,
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
    private val reuseCache: LazyItemSnapshotReuseCache,
) {
    private var mutableItemsByKey: HashMap<Any, LazyListItem>? = null
    private var mutableItems: ArrayList<LazyListItem>? = null
    private var firstUsedTypedSnapshot: TypedLazyItemSnapshot? = null
    private var additionalUsedTypedSnapshots: ArrayList<TypedLazyItemSnapshot>? = null
    private var directTypedSnapshot: TypedLazyItemSnapshot? = null
    private var firstDeclaredSnapshotRevision: Any? = null
    private var additionalDeclaredSnapshotRevisions: HashSet<Any>? = null
    private var duplicateDeclaredSnapshotRevision: Any? = null

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
        materializeDirectTypedSnapshot()
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
        snapshotRevision: Any?,
        kind: LazyListItemKind,
        span: (T) -> GridItemSpan,
        itemContent: UiTreeBuilder.(T) -> Unit,
    ) {
        if (snapshotRevision != null) {
            requireUniqueSnapshotRevision(snapshotRevision)
        }
        val cachedSnapshot = snapshotRevision?.let { revision ->
            reuseCache.findTypedSnapshot(
                snapshotRevision = revision,
                environmentRevision = localSnapshot,
            )
        }
        val typedSnapshot = cachedSnapshot ?: buildTypedSnapshot(
            sourceItems = items,
            snapshotRevision = snapshotRevision,
            key = key,
            contentType = contentType,
            contentRevision = contentRevision,
            kind = kind,
            span = span,
            itemContent = itemContent,
        )
        appendTypedSnapshot(typedSnapshot)
        if (typedSnapshot.cacheable) {
            recordUsedTypedSnapshot(typedSnapshot)
        }
    }

    private fun <T> buildTypedSnapshot(
        sourceItems: List<T>,
        snapshotRevision: Any?,
        key: (T) -> Any,
        contentType: (T) -> Any?,
        contentRevision: (T) -> Any?,
        kind: LazyListItemKind,
        span: (T) -> GridItemSpan,
        itemContent: UiTreeBuilder.(T) -> Unit,
    ): TypedLazyItemSnapshot {
        val typedItems = ArrayList<LazyListItem>(sourceItems.size)
        val typedItemsByKey = HashMap<Any, LazyListItem>(
            ((sourceItems.size / HASH_SET_LOAD_FACTOR) + 1).toInt(),
        )
        sourceItems.forEach { item ->
            val lazyItem = createCanonical(
                key = key(item),
                contentType = contentType(item),
                contentRevision = contentRevision(item),
                kind = kind,
                span = span(item),
                contentFactory = { { itemContent(item) } },
            )
            require(typedItemsByKey.putIfAbsent(lazyItem.key, lazyItem) == null) {
                "Lazy collection keys must be unique. Duplicate key: ${lazyItem.key}"
            }
            typedItems += lazyItem
        }
        val retainedKeys = if (snapshotRevision == null) {
            typedItemsByKey.keys
        } else {
            reuseCache.canonicalizeKeySet(typedItemsByKey.keys)
        }
        return TypedLazyItemSnapshot(
            snapshotRevision = snapshotRevision,
            environmentRevision = localSnapshot,
            items = typedItems,
            itemsByKey = typedItemsByKey,
            retainedKeys = retainedKeys,
        )
    }

    private fun appendTypedSnapshot(snapshot: TypedLazyItemSnapshot) {
        if (directTypedSnapshot == null && mutableItems.isNullOrEmpty()) {
            directTypedSnapshot = snapshot
            return
        }
        materializeDirectTypedSnapshot()
        val itemsByKey = mutableItemsByKey()
        val duplicateKey = snapshot.itemsByKey.keys.firstOrNull(itemsByKey::containsKey)
        require(duplicateKey == null) {
            "Lazy collection keys must be unique. Duplicate key: $duplicateKey"
        }
        itemsByKey.putAll(snapshot.itemsByKey)
        mutableItems().addAll(snapshot.items)
    }

    private fun materializeDirectTypedSnapshot() {
        val snapshot = directTypedSnapshot ?: return
        directTypedSnapshot = null
        val items = mutableItems()
        items.ensureCapacity(items.size + snapshot.items.size)
        mutableItemsByKey().putAll(snapshot.itemsByKey)
        items.addAll(snapshot.items)
    }

    private inline fun addCanonical(
        key: Any,
        contentType: Any?,
        contentRevision: Any?,
        kind: LazyListItemKind,
        span: GridItemSpan,
        contentFactory: () -> UiTreeBuilder.() -> Unit,
    ) {
        val lazyItem = createCanonical(
            key = key,
            contentType = contentType,
            contentRevision = contentRevision,
            kind = kind,
            span = span,
            contentFactory = contentFactory,
        )
        val itemsByKey = mutableItemsByKey()
        require(itemsByKey.putIfAbsent(key, lazyItem) == null) {
            "Lazy collection keys must be unique. Duplicate key: $key"
        }
        mutableItems().add(lazyItem)
    }

    private inline fun createCanonical(
        key: Any,
        contentType: Any?,
        contentRevision: Any?,
        kind: LazyListItemKind,
        span: GridItemSpan,
        contentFactory: () -> UiTreeBuilder.() -> Unit,
    ): LazyListItem {
        val canonicalSpan = span.canonical()
        return reuseCache.findReusable(
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
    }

    fun build(): List<LazyListItem> {
        duplicateDeclaredSnapshotRevision?.let { duplicateRevision ->
            throw IllegalArgumentException(duplicateSnapshotRevisionMessage(duplicateRevision))
        }
        val directSnapshot = directTypedSnapshot
        val committedItemsByKey = directSnapshot?.itemsByKey ?: mutableItemsByKey.orEmpty()
        val committedItems = directSnapshot?.items ?: mutableItems.orEmpty()
        if (ComposerContext.currentComposer() != null) {
            val directCommitSnapshot = directSnapshot?.takeIf { snapshot ->
                snapshot.cacheable && additionalUsedTypedSnapshots == null &&
                    firstUsedTypedSnapshot === snapshot
            }
            val committedTypedSnapshots = if (directCommitSnapshot == null) {
                committedTypedSnapshots()
            } else {
                emptyList()
            }
            // Keep one stable effect group shape while using the snapshot identity to suppress an
            // unchanged direct commit. A frame-local collector key keeps aggregate commits eager.
            SideEffect(directCommitSnapshot ?: this) {
                if (directCommitSnapshot != null) {
                    val keyMembershipChanged =
                        reuseCache.commitDirectTypedSnapshot(directCommitSnapshot)
                    if (keyMembershipChanged) {
                        saveableStateHolder?.retainKeys(directCommitSnapshot.retainedKeys)
                    }
                } else {
                    reuseCache.commitAggregate(
                        itemsByKey = committedItemsByKey,
                        typedSnapshots = committedTypedSnapshots,
                    )
                    saveableStateHolder?.retainKeys(committedItemsByKey.keys)
                }
            }
        }
        // The collector is frame-local and receives no writes after build, so the read-only view is
        // already an immutable submission without copying every item and key a second time.
        return committedItems
    }

    private fun recordUsedTypedSnapshot(snapshot: TypedLazyItemSnapshot) {
        val first = firstUsedTypedSnapshot
        if (first == null) {
            firstUsedTypedSnapshot = snapshot
            return
        }
        val additional = additionalUsedTypedSnapshots ?: ArrayList<TypedLazyItemSnapshot>().also {
            additionalUsedTypedSnapshots = it
        }
        additional += snapshot
    }

    private fun requireUniqueSnapshotRevision(snapshotRevision: Any) {
        val firstRevision = firstDeclaredSnapshotRevision
        if (firstRevision == null) {
            firstDeclaredSnapshotRevision = snapshotRevision
            return
        }
        if (firstRevision == snapshotRevision) {
            duplicateDeclaredSnapshotRevision = snapshotRevision
            throw IllegalArgumentException(duplicateSnapshotRevisionMessage(snapshotRevision))
        }
        val additionalRevisions = additionalDeclaredSnapshotRevisions
            ?: hashSetOf(firstRevision).also { revisions ->
                additionalDeclaredSnapshotRevisions = revisions
            }
        if (!additionalRevisions.add(snapshotRevision)) {
            duplicateDeclaredSnapshotRevision = snapshotRevision
            throw IllegalArgumentException(duplicateSnapshotRevisionMessage(snapshotRevision))
        }
    }

    private fun duplicateSnapshotRevisionMessage(snapshotRevision: Any): String {
        return "Typed declarations in one lazy scope require distinct namespaced " +
            "snapshotRevision values. Duplicate value: $snapshotRevision"
    }

    private fun committedTypedSnapshots(): List<TypedLazyItemSnapshot> {
        val first = firstUsedTypedSnapshot ?: return emptyList()
        val additional = additionalUsedTypedSnapshots ?: return listOf(first)
        return buildList(1 + additional.size) {
            add(first)
            addAll(additional)
        }
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

/** One immutable, fully evaluated typed declaration eligible for exact-list reuse. */
internal class TypedLazyItemSnapshot(
    val snapshotRevision: Any?,
    val environmentRevision: Any?,
    val items: List<LazyListItem>,
    val itemsByKey: Map<Any, LazyListItem>,
    val retainedKeys: Set<Any>,
) {
    val cacheable: Boolean
        get() = snapshotRevision != null
}

/** Retains two complete typed snapshots plus item variants needed by an immediate reset. */
internal class LazyItemSnapshotReuseCache {
    private var committedItemsByKey: Map<Any, LazyListItem> = emptyMap()
    private var previousVariantsByKey: Map<Any, LazyListItem> = emptyMap()
    private var committedRetainedKeys: Set<Any>? = null
    private val typedSnapshots = ArrayList<TypedLazyItemSnapshot>(TYPED_SNAPSHOT_CAPACITY)

    fun findReusable(
        key: Any,
        contentRevision: Any?,
        environmentRevision: Any?,
        contentType: Any?,
        kind: LazyListItemKind,
        span: GridItemSpan,
    ): LazyListItem? {
        return committedItemsByKey[key]
            ?.takeIf { item -> item.matches(contentRevision, environmentRevision, contentType, kind, span) }
            ?: previousVariantsByKey[key]?.takeIf { item ->
                item.matches(contentRevision, environmentRevision, contentType, kind, span)
            }
    }

    fun findTypedSnapshot(
        snapshotRevision: Any,
        environmentRevision: Any?,
    ): TypedLazyItemSnapshot? {
        return typedSnapshots.firstOrNull { snapshot ->
            snapshot.snapshotRevision == snapshotRevision &&
                snapshot.environmentRevision == environmentRevision
        }
    }

    fun canonicalizeKeySet(keys: Set<Any>): Set<Any> {
        committedRetainedKeys?.takeIf { candidate -> candidate.hasSameKeysAs(keys) }?.let {
            return it
        }
        typedSnapshots.firstOrNull { snapshot -> snapshot.retainedKeys.hasSameKeysAs(keys) }
            ?.let { snapshot -> return snapshot.retainedKeys }
        return keys
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

    fun commitDirectTypedSnapshot(snapshot: TypedLazyItemSnapshot): Boolean {
        val keyMembershipChanged = committedRetainedKeys !== snapshot.retainedKeys
        if (committedItemsByKey !== snapshot.itemsByKey) {
            // Both maps are already retained by the bounded complete-snapshot cache. Swapping their
            // roles preserves immediate per-key reset reuse without rescanning every item at commit.
            previousVariantsByKey = committedItemsByKey
            committedItemsByKey = snapshot.itemsByKey
        }
        committedRetainedKeys = snapshot.retainedKeys
        touchTypedSnapshot(snapshot)
        return keyMembershipChanged
    }

    fun commitAggregate(
        itemsByKey: Map<Any, LazyListItem>,
        typedSnapshots: List<TypedLazyItemSnapshot>,
    ) {
        commitItemsByKey(itemsByKey)
        committedRetainedKeys = null
        typedSnapshots.forEach(::touchTypedSnapshot)
    }

    private fun commitItemsByKey(itemsByKey: Map<Any, LazyListItem>) {
        var previousVariants: HashMap<Any, LazyListItem>? = null
        committedItemsByKey.forEach { (key, previousItem) ->
            val nextItem = itemsByKey[key]
            if (nextItem != null && nextItem !== previousItem) {
                val variants = previousVariants ?: HashMap<Any, LazyListItem>().also {
                    previousVariants = it
                }
                variants[key] = previousItem
            }
        }
        previousVariantsByKey = previousVariants ?: emptyMap()
        committedItemsByKey = itemsByKey
    }

    private fun touchTypedSnapshot(snapshot: TypedLazyItemSnapshot) {
        val existingIndex = typedSnapshots.indexOfFirst { candidate ->
            candidate.snapshotRevision == snapshot.snapshotRevision &&
                candidate.environmentRevision == snapshot.environmentRevision
        }
        if (existingIndex >= 0) {
            typedSnapshots.removeAt(existingIndex)
        }
        typedSnapshots.add(0, snapshot)
        if (typedSnapshots.size > TYPED_SNAPSHOT_CAPACITY) {
            typedSnapshots.removeAt(typedSnapshots.lastIndex)
        }
    }

    private fun Set<Any>.hasSameKeysAs(other: Set<Any>): Boolean {
        return size == other.size && containsAll(other)
    }

    private companion object {
        private const val TYPED_SNAPSHOT_CAPACITY = 2
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
