package com.viewcompose.ui.node

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class LazyItemTableTest {
    @Test
    fun `finite wrapper validates keys and exposes constant-time lookup`() {
        val table = listOf(
            item(key = "header", kind = LazyListItemKind.StickyHeader),
            item(key = "row"),
        ).asLazyItemTable()

        assertEquals(2, table.size)
        assertEquals(0, table.indexOfKey("header"))
        assertEquals(1, table.indexOfKey("row"))
        assertEquals(-1, table.indexOfKey("missing"))
        val sticky = table as LazyItemTableStickyHeaders
        assertTrue(sticky.hasStickyHeaders)
        assertEquals(0, sticky.findStickyHeaderIndex(1))
    }

    @Test
    fun `finite wrapper rejects duplicate keys before publication`() {
        val failure = assertThrows(IllegalArgumentException::class.java) {
            listOf(item("duplicate"), item("duplicate")).asLazyItemTable()
        }

        assertTrue(failure.message.orEmpty().contains("Duplicate key: duplicate"))
    }

    @Test
    fun `equal finite revisions report no updates and changed revisions request fallback`() {
        val item = item("row")
        val backing = listOf(item)
        val first = backing.asLazyItemTable()
        val equal = listOf(item).asLazyItemTable()
        val changed = listOf(item("other")).asLazyItemTable()

        assertEquals(emptyList<LazyItemTableUpdate>(), equal.updatesFrom(first))
        assertEquals(first, equal)
        assertSame(backing, first.toList())
        assertEquals(null, changed.updatesFrom(first))
        assertFalse((changed as LazyItemTableStickyHeaders).hasStickyHeaders)
    }

    private fun item(
        key: Any,
        kind: LazyListItemKind = LazyListItemKind.Item,
    ): LazyListItem {
        return LazyListItem(
            key = key,
            contentRevision = key,
            kind = kind,
            sessionStrategy = lazyListItemSessionStrategy(
                create = { error("Session creation is not part of this contract test.") },
                update = {},
            ),
        )
    }
}
