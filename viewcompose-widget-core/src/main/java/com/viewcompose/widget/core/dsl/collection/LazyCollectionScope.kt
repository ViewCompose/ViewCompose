package com.viewcompose.widget.core

import com.viewcompose.ui.node.LazyListItem
import com.viewcompose.ui.node.LazyListItemKind
import com.viewcompose.ui.node.LazyListItemSessionFactory

/**
 * Lazy list 内容 scope，收集 item 定义并捕获声明时的 locals。
 * Lazy-list content scope that collects item definitions and captures locals from declaration time.
 */
@UiDslMarker
class LazyListScope internal constructor(
    private val collector: LazyItemCollector,
    private val stickyHeadersAllowed: Boolean,
) {
    /**
     * 添加单个 lazy item。
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
     * 批量添加 lazy items。
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
     * 添加 sticky header；当前仅 LazyColumn 支持。
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
 * Lazy grid 内容 scope，支持 item span。
 * Lazy-grid content scope with item span support.
 */
@UiDslMarker
class LazyGridScope internal constructor(
    private val collector: LazyItemCollector,
) {
    /**
     * 添加单个 grid item。
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
     * 批量添加 grid items。
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
     * 添加跨整行的 sticky header。
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
 * lazy item 收集器，负责 key 去重、locals 捕获和 item session 工厂创建。
 * Lazy-item collector responsible for key uniqueness, locals capture, and item session factory creation.
 */
internal class LazyItemCollector(
    private val localSnapshot: LocalSnapshot,
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

    fun build(): List<LazyListItem> = items.toList()
}
