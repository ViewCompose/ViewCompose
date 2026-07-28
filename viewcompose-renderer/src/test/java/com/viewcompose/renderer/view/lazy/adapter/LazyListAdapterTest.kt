package com.viewcompose.renderer.view.lazy.adapter

/*
 * 测试职责：覆盖 renderer view/lazy/adapter 中的 Lazy List Adapter 行为，防止渲染和 patch 契约在后续重构中回退。
 * Test responsibility: covers Lazy List Adapter behavior in renderer view/lazy/adapter and guards render and patch contracts against regressions.
 */

import com.viewcompose.ui.node.LazyListItem
import com.viewcompose.ui.node.LazyListItemKind
import com.viewcompose.ui.node.LazyListItemSession
import com.viewcompose.ui.node.LazyListItemSessionFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class LazyListAdapterTest {
    @Test
    fun `spacing decoration reports whether item offsets changed`() {
        val decoration = LazyListSpacingDecoration(
            spacing = 8,
            orientation = androidx.recyclerview.widget.LinearLayoutManager.VERTICAL,
        )

        assertFalse(
            decoration.update(
                spacing = 8,
                orientation = androidx.recyclerview.widget.LinearLayoutManager.VERTICAL,
            ),
        )
        assertTrue(
            decoration.update(
                spacing = 12,
                orientation = androidx.recyclerview.widget.LinearLayoutManager.VERTICAL,
            ),
        )
        assertTrue(
            decoration.update(
                spacing = 12,
                orientation = androidx.recyclerview.widget.LinearLayoutManager.HORIZONTAL,
            ),
        )
    }

    @Test
    fun `stable ids do not collide when keys have equal hash codes`() {
        val firstKey = CollidingKey("first")
        val secondKey = CollidingKey("second")
        val adapter = LazyListAdapter()
        adapter.submitItems(
            listOf(
                item(key = firstKey),
                item(key = secondKey),
            ),
        )
        val firstId = adapter.getItemId(0)
        val secondId = adapter.getItemId(1)

        adapter.submitItems(
            listOf(
                item(key = secondKey),
                item(key = firstKey),
            ),
        )

        assertNotEquals(firstId, secondId)
        assertEquals(secondId, adapter.getItemId(0))
        assertEquals(firstId, adapter.getItemId(1))
    }

    @Test
    fun `view types partition content types and sticky headers`() {
        val adapter = LazyListAdapter()
        adapter.submitItems(
            listOf(
                item(key = "row-1", contentType = "row"),
                item(key = "row-2", contentType = "row"),
                item(key = "card", contentType = "card"),
                item(
                    key = "header",
                    contentType = "row",
                    kind = LazyListItemKind.StickyHeader,
                ),
            ),
        )

        assertEquals(adapter.getItemViewType(0), adapter.getItemViewType(1))
        assertNotEquals(adapter.getItemViewType(0), adapter.getItemViewType(2))
        assertNotEquals(adapter.getItemViewType(0), adapter.getItemViewType(3))
        assertEquals(3, adapter.findStickyHeaderPosition(3))
        assertEquals(3, adapter.findStickyHeaderPosition(adapter.itemCount - 1))
    }

    private fun item(
        key: Any,
        contentType: Any? = null,
        kind: LazyListItemKind = LazyListItemKind.Item,
    ): LazyListItem {
        return LazyListItem(
            key = key,
            contentToken = key,
            contentType = contentType,
            kind = kind,
            sessionFactory = LazyListItemSessionFactory {
                object : LazyListItemSession {
                    override fun render() = Unit
                    override fun dispose() = Unit
                }
            },
        )
    }

    private data class CollidingKey(
        val value: String,
    ) {
        override fun hashCode(): Int = 1
    }
}
