package com.viewcompose.ui.foundation

/*
 * 测试职责：覆盖 widget-core widget/collection 中的 Lazy Column 行为，防止 DSL、状态或主题契约在后续重构中回退。
 * Test responsibility: covers Lazy Column behavior in widget-core widget/collection and guards DSL, state, or theme contracts against regressions.
 */

import com.viewcompose.ui.node.NodeType
import com.viewcompose.ui.node.LazyListItemKind
import com.viewcompose.ui.node.policy.GridItemSpan
import com.viewcompose.ui.node.policy.LazyContentPadding
import com.viewcompose.ui.node.policy.LazyLayoutPrefetchPolicy
import com.viewcompose.ui.node.spec.LazyColumnNodeProps
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LazyColumnTest {
    private data class MutableRow(
        val id: String,
        var version: Int,
    )

    @Test
    fun `lazy column emits content padding and spacing props`() {
        val tree = buildVNodeTree {
            LazyColumn(
                contentPadding = LazyContentPadding.all(12.dp),
                spacing = 8.dp,
            ) {
                items(
                    items = listOf("A", "B"),
                    key = { item -> item },
                ) { item ->
                    Text(item)
                }
            }
        }

        val node = tree.single()
        val spec = node.spec as LazyColumnNodeProps

        assertEquals(NodeType.LazyColumn, node.type)
        assertEquals(LazyContentPadding.all(12.dp), spec.contentPadding)
        assertEquals(8.dp, spec.spacing)
        assertTrue(node.spec is LazyColumnNodeProps)
    }

    @Test
    fun `structured lazy scope emits sticky header content types and policies`() {
        val padding = LazyContentPadding(
            start = 4.dp,
            top = 8.dp,
            end = 12.dp,
            bottom = 16.dp,
        )
        val prefetch = LazyLayoutPrefetchPolicy(
            nestedInitialPrefetchItemCount = 5,
            itemViewCacheSize = 7,
        )
        val tree = buildVNodeTree {
            LazyColumn(
                contentPadding = padding,
                reverseLayout = true,
                userScrollEnabled = false,
                prefetchPolicy = prefetch,
            ) {
                stickyHeader(
                    key = "header",
                    contentRevision = StaticContentRevision,
                    contentType = "header-type",
                ) {
                    Text("Header")
                }
                items(
                    items = listOf("A", "B"),
                    key = { item -> item },
                    contentType = { "row-type" },
                ) { item ->
                    Text(item)
                }
            }
        }

        val spec = tree.single().spec as LazyColumnNodeProps
        assertEquals(padding, spec.contentPadding)
        assertTrue(spec.reverseLayout)
        assertTrue(!spec.userScrollEnabled)
        assertEquals(prefetch, spec.prefetchPolicy)
        assertEquals(
            listOf("header", "A", "B"),
            spec.items.map { item -> item.key },
        )
        assertEquals(LazyListItemKind.StickyHeader, spec.items[0].kind)
        assertEquals(GridItemSpan.FullLine, spec.items[0].span)
        assertEquals("header-type", spec.items[0].contentType)
        assertEquals("row-type", spec.items[1].contentType)
    }

    @Test
    fun `structured lazy scope rejects duplicate keys`() {
        val error = runCatching {
            buildVNodeTree {
                LazyColumn {
                    item(key = "duplicate", contentRevision = StaticContentRevision) { Text("A") }
                    item(key = "duplicate", contentRevision = StaticContentRevision) { Text("B") }
                }
            }
        }.exceptionOrNull()

        assertTrue(error is IllegalArgumentException)
        assertTrue(error?.message.orEmpty().contains("Duplicate key"))
    }

    @Test
    fun `bulk items expose explicit semantic revision for mutable models`() {
        val row = MutableRow(id = "row", version = 7)
        val tree = buildVNodeTree {
            LazyColumn(
                items = listOf(row),
                key = MutableRow::id,
                contentType = { "row" },
                contentRevision = MutableRow::version,
            ) { item ->
                Text(item.id)
            }
        }

        val item = (tree.single().spec as LazyColumnNodeProps).items.single()
        assertEquals("row", item.key)
        assertEquals("row", item.contentType)
        assertEquals(7, item.contentRevision)
    }
}
