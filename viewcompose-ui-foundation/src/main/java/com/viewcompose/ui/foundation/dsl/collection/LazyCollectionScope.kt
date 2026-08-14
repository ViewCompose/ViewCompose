package com.viewcompose.ui.foundation

import com.viewcompose.ui.node.LazyListItem
import com.viewcompose.ui.node.LazyListItemKind
import com.viewcompose.ui.node.LazyListItemSessionFactory

/**
 * Declares keyed lazy-list entries whose logical sessions are independent from recycled Views.
 *
 * Keys must be unique in one scope. Equal key, declared content revision, and framework-captured
 * environment revision skip item rendering. Changing ordinary captures must therefore be observed
 * State or participate in the content revision.
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
            span = 1,
            content = content,
        )
    }

    /**
     * Adds independently keyed and versioned lazy items from [items].
     *
     * @param T application item type
     * @param items immutable submission iterated in display order
     * @param key unique logical identity selector
     * @param contentType physical-tree compatibility selector
     * @param contentRevision semantic revision selector; immutable values default to themselves
     * @param itemContent declaration evaluated for the item when its logical session renders
     * @throws IllegalArgumentException when selected keys are not unique in this scope
     */
    fun <T> items(
        items: List<T>,
        key: (T) -> Any,
        contentType: (T) -> Any? = { null },
        contentRevision: (T) -> Any? = { it },
        itemContent: UiTreeBuilder.(T) -> Unit,
    ) {
        items.forEach { item ->
            collector.add(
                key = key(item),
                contentType = contentType(item),
                contentRevision = contentRevision(item),
                kind = LazyListItemKind.Item,
                span = 1,
                content = { itemContent(item) },
            )
        }
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
            span = Int.MAX_VALUE,
            content = content,
        )
    }
}

/**
 * Declares keyed lazy-grid entries with spans and separated logical and physical ownership.
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
     * @param span positive number of columns occupied by the item
     * @param content declaration evaluated when this logical item session renders
     * @throws IllegalArgumentException when [key] is duplicated or [span] is not positive
     */
    fun item(
        key: Any,
        contentType: Any? = null,
        contentRevision: Any? = key,
        span: Int = 1,
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
     * @param T application item type
     * @param items immutable submission iterated in display order
     * @param key unique logical identity selector
     * @param contentType physical-tree compatibility selector
     * @param contentRevision semantic revision selector; immutable values default to themselves
     * @param span positive column-span selector
     * @param itemContent declaration evaluated for the item when its logical session renders
     * @throws IllegalArgumentException when keys are duplicated or a selected span is not positive
     */
    fun <T> items(
        items: List<T>,
        key: (T) -> Any,
        contentType: (T) -> Any? = { null },
        contentRevision: (T) -> Any? = { it },
        span: (T) -> Int = { 1 },
        itemContent: UiTreeBuilder.(T) -> Unit,
    ) {
        items.forEach { item ->
            collector.add(
                key = key(item),
                contentType = contentType(item),
                contentRevision = contentRevision(item),
                kind = LazyListItemKind.Item,
                span = span(item),
                content = { itemContent(item) },
            )
        }
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
            span = Int.MAX_VALUE,
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
) {
    private val keys = linkedSetOf<Any>()
    private val items = mutableListOf<LazyListItem>()

    fun add(
        key: Any,
        contentType: Any?,
        contentRevision: Any?,
        kind: LazyListItemKind,
        span: Int,
        content: UiTreeBuilder.() -> Unit,
    ) {
        require(keys.add(key)) {
            "Lazy collection keys must be unique. Duplicate key: $key"
        }
        require(span > 0) { "Lazy item span must be greater than zero." }
        items += LazyListItem(
            key = key,
            contentRevision = contentRevision,
            environmentRevision = localSnapshot,
            contentType = contentType,
            kind = kind,
            span = span,
            sessionFactory = LazyListItemSessionFactory { container ->
                WidgetLazyListItemSession(
                    container = container,
                    localSnapshot = localSnapshot,
                    saveableStateHolder = saveableStateHolder,
                    saveableStateKey = key,
                    content = content,
                )
            },
            sessionUpdater = { session ->
                (session as WidgetLazyListItemSession).updateContent(
                    localSnapshot = localSnapshot,
                    content = content,
                )
            },
        )
    }

    fun build(): List<LazyListItem> {
        saveableStateHolder?.let { holder ->
            val committedKeys = keys.toSet()
            SideEffect {
                holder.retainKeys(committedKeys)
            }
        }
        return items.toList()
    }
}
