package com.viewcompose.renderer.reconcile

/*
 * 测试职责：覆盖 renderer reconcile 中的 Lazy List Diff 行为，防止渲染和 patch 契约在后续重构中回退。
 * Test responsibility: covers Lazy List Diff behavior in renderer reconcile and guards render and patch contracts against regressions.
 */

import com.viewcompose.ui.modifier.Modifier
import com.viewcompose.ui.node.LazyListItem
import com.viewcompose.ui.node.LazyListItemSession
import com.viewcompose.ui.node.LazyListItemSessionFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class LazyListDiffTest {
    @Test
    fun `produces move updates for keyed reorder`() {
        val result = LazyListDiff.calculate(
            previous = listOf(item("A"), item("B"), item("C")),
            next = listOf(item("C"), item("B"), item("A")),
        )

        assertEquals(
            listOf(
                LazyListUpdate.Move(1, 2),
                LazyListUpdate.Move(0, 2),
            ),
            result.updates,
        )
        assertEquals(listOf("C", "B", "A"), result.items.map { it.key })
    }

    @Test
    fun `produces insert and remove updates`() {
        val result = LazyListDiff.calculate(
            previous = listOf(item("A"), item("B")),
            next = listOf(item("B"), item("C")),
        )

        assertEquals(
            listOf(
                LazyListUpdate.Insert(2),
                LazyListUpdate.Remove(0),
            ),
            result.updates,
        )
        assertEquals(listOf("B", "C"), result.items.map { it.key })
    }

    @Test
    fun `falls back to reload when keys are missing`() {
        val result = LazyListDiff.calculate(
            previous = listOf(item(null)),
            next = listOf(item(null)),
        )

        assertEquals(listOf(LazyListUpdate.ReloadAll), result.updates)
    }

    @Test
    fun `keeps latest lazy item instances when keyed diff produces no updates`() {
        val previous = item("A", contentToken = "stable")
        val next = item("A", contentToken = "stable")

        val result = LazyListDiff.calculate(
            previous = listOf(previous),
            next = listOf(next),
        )

        assertEquals(emptyList<LazyListUpdate>(), result.updates)
        assertSame(next, result.items.first())
    }

    @Test
    fun `keeps latest page instances when pager pages are structurally stable`() {
        val previous = item("page-1", contentToken = "stable")
        val next = item("page-1", contentToken = "stable")

        val result = LazyListDiff.calculate(
            previous = listOf(previous),
            next = listOf(next),
        )

        assertEquals(emptyList<LazyListUpdate>(), result.updates)
        assertSame(next, result.items.first())
    }

    @Test
    fun `keeps latest grid item instances when grid rows are structurally stable`() {
        val previous = item("grid-1", contentToken = "stable")
        val next = item("grid-1", contentToken = "stable")

        val result = LazyListDiff.calculate(
            previous = listOf(previous),
            next = listOf(next),
        )

        assertEquals(emptyList<LazyListUpdate>(), result.updates)
        assertSame(next, result.items.first())
    }

    @Test
    fun `emits content token payload on change updates`() {
        val result = LazyListDiff.calculate(
            previous = listOf(item("A", contentToken = 1)),
            next = listOf(item("A", contentToken = 2)),
        )

        assertEquals(1, result.updates.size)
        val update = result.updates.first()
        assertTrue(update is LazyListUpdate.Change)
        val payload = (update as LazyListUpdate.Change).payload
        assertTrue(payload is LazyListChangePayload.ContentTokenChanged)
        payload as LazyListChangePayload.ContentTokenChanged
        assertEquals(1, payload.previous)
        assertEquals(2, payload.next)
    }

    private fun item(
        key: String?,
        contentToken: Any? = key,
    ): LazyListItem {
        return LazyListItem(
            key = key,
            contentToken = contentToken,
            sessionFactory = LazyListItemSessionFactory {
                object : LazyListItemSession {
                    override fun render() = Unit

                    override fun dispose() = Unit
                }
            },
        )
    }
}
