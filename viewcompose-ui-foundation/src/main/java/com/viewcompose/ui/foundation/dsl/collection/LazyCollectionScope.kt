package com.viewcompose.ui.foundation

import com.viewcompose.ui.node.LazyListItem
import com.viewcompose.ui.node.LazyListItemKind
import com.viewcompose.ui.node.LazyListItemSessionFactory

/**
 * Lazy-list content scope that collects item definitions and captures locals from declaration time.
 */
@UiDslMarker
class LazyListScope internal constructor(
    private val collector: LazyItemCollector,
    private val stickyHeadersAllowed: Boolean,
) {
    /**
     * Adds one lazy item.
     */
    fun item(
        key: Any,
        contentType: Any? = null,
        contentToken: Any? = key,
        content: UiTreeBuilder.() -> Unit,
    ) {
        collector.add(
            key = key,
            contentType = contentType,
            contentToken = contentToken,
            kind = LazyListItemKind.Item,
            span = 1,
            content = content,
        )
    }

    /**
     * Adds lazy items from a list.
     */
    fun <T> items(
        items: List<T>,
        key: (T) -> Any,
        contentType: (T) -> Any? = { null },
        itemContent: UiTreeBuilder.(T) -> Unit,
    ) {
        items.forEach { item ->
            collector.add(
                key = key(item),
                contentType = contentType(item),
                contentToken = item,
                kind = LazyListItemKind.Item,
                span = 1,
                content = { itemContent(item) },
            )
        }
    }

    /**
     * Adds a sticky header; currently supported only by LazyColumn.
     */
    fun stickyHeader(
        key: Any,
        contentType: Any? = null,
        contentToken: Any? = key,
        content: UiTreeBuilder.() -> Unit,
    ) {
        require(stickyHeadersAllowed) {
            "stickyHeader is supported by LazyColumn only. Use a normal item in LazyRow."
        }
        collector.add(
            key = key,
            contentType = contentType,
            contentToken = contentToken,
            kind = LazyListItemKind.StickyHeader,
            span = Int.MAX_VALUE,
            content = content,
        )
    }
}

/**
 * Lazy-grid content scope with item span support.
 */
@UiDslMarker
class LazyGridScope internal constructor(
    private val collector: LazyItemCollector,
) {
    /**
     * Adds one grid item.
     */
    fun item(
        key: Any,
        contentType: Any? = null,
        contentToken: Any? = key,
        span: Int = 1,
        content: UiTreeBuilder.() -> Unit,
    ) {
        collector.add(
            key = key,
            contentType = contentType,
            contentToken = contentToken,
            kind = LazyListItemKind.Item,
            span = span,
            content = content,
        )
    }

    /**
     * Adds grid items from a list.
     */
    fun <T> items(
        items: List<T>,
        key: (T) -> Any,
        contentType: (T) -> Any? = { null },
        span: (T) -> Int = { 1 },
        itemContent: UiTreeBuilder.(T) -> Unit,
    ) {
        items.forEach { item ->
            collector.add(
                key = key(item),
                contentType = contentType(item),
                contentToken = item,
                kind = LazyListItemKind.Item,
                span = span(item),
                content = { itemContent(item) },
            )
        }
    }

    /**
     * Adds a sticky header spanning the full row.
     */
    fun stickyHeader(
        key: Any,
        contentType: Any? = null,
        contentToken: Any? = key,
        content: UiTreeBuilder.() -> Unit,
    ) {
        collector.add(
            key = key,
            contentType = contentType,
            contentToken = contentToken,
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
        contentToken: Any?,
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
            contentToken = capturedLazyContentToken(
                contentToken = contentToken,
                localSnapshot = localSnapshot,
            ),
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
                (session as? WidgetLazyListItemSession)?.updateContent(
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
