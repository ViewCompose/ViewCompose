package com.viewcompose.renderer.view.lazy.adapter

import com.viewcompose.ui.unit.dp

/*
 * 测试职责：覆盖 renderer view/lazy/adapter 中的 Lazy List Adapter 行为，防止渲染和 patch 契约在后续重构中回退。
 * Test responsibility: covers Lazy List Adapter behavior in renderer view/lazy/adapter and guards render and patch contracts against regressions.
 */

import com.viewcompose.ui.node.LazyListItem
import com.viewcompose.ui.node.LazyListItemKind
import com.viewcompose.ui.node.LazyListItemSession
import com.viewcompose.ui.node.LazyListItemSessionFactory
import com.viewcompose.renderer.reconcile.LazyListChangePayload
import android.widget.FrameLayout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
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

    @Test
    fun `new submission revision refreshes an attached holder with a stable updater instance`() {
        val context = RuntimeEnvironment.getApplication()
        val parent = FrameLayout(context)
        val events = mutableListOf<String>()
        val adapter = LazyListAdapter()
        val label = arrayOf("first")
        val updater: (LazyListItemSession) -> Unit = { session ->
            (session as RecordingSession).label = label.single()
            events += "update:${label.single()}"
        }
        adapter.submitItems(listOf(recordingItem(events = events, sessionUpdater = updater)))
        val holder = adapter.onCreateViewHolder(parent, adapter.getItemViewType(0))
        adapter.onBindViewHolder(holder, 0)
        adapter.onViewAttachedToWindow(holder)

        label[0] = "second"
        adapter.submitItems(listOf(recordingItem(events = events, sessionUpdater = updater)))

        assertEquals(
            listOf("update:first", "render:first", "update:second", "render:second"),
            events,
        )
    }

    @Test
    fun `content refresh is synchronous and queued payload does not render it twice`() {
        val context = RuntimeEnvironment.getApplication()
        val parent = FrameLayout(context)
        val events = mutableListOf<String>()
        val adapter = LazyListAdapter()
        adapter.submitItems(listOf(recordingItem("first", events, contentToken = 1)))
        val holder = adapter.onCreateViewHolder(parent, adapter.getItemViewType(0))
        adapter.onBindViewHolder(holder, 0)
        adapter.onViewAttachedToWindow(holder)

        adapter.submitItems(listOf(recordingItem("second", events, contentToken = 2)))
        adapter.onBindViewHolder(
            holder,
            0,
            mutableListOf(LazyListChangePayload.ContentTokenChanged(previous = 1, next = 2)),
        )

        assertEquals(
            listOf("update:first", "render:first", "update:second", "render:second"),
            events,
        )
    }

    @Test
    fun `reattaching a holder at its committed revision does not render again`() {
        val context = RuntimeEnvironment.getApplication()
        val parent = FrameLayout(context)
        val events = mutableListOf<String>()
        val adapter = LazyListAdapter()
        adapter.submitItems(listOf(recordingItem("first", events)))
        val holder = adapter.onCreateViewHolder(parent, adapter.getItemViewType(0))
        adapter.onBindViewHolder(holder, 0)
        adapter.onViewAttachedToWindow(holder)

        adapter.onViewDetachedFromWindow(holder)
        adapter.onViewAttachedToWindow(holder)

        assertEquals(listOf("update:first", "render:first"), events)
    }

    @Test
    fun `detached cached holder does not render a new submission until reattached`() {
        val context = RuntimeEnvironment.getApplication()
        val parent = FrameLayout(context)
        val events = mutableListOf<String>()
        val adapter = LazyListAdapter()
        adapter.submitItems(listOf(recordingItem("first", events)))
        val holder = adapter.onCreateViewHolder(parent, adapter.getItemViewType(0))
        adapter.onBindViewHolder(holder, 0)
        adapter.onViewAttachedToWindow(holder)
        adapter.onViewDetachedFromWindow(holder)

        adapter.submitItems(listOf(recordingItem("second", events)))

        assertEquals(listOf("update:first", "render:first"), events)
        adapter.onViewAttachedToWindow(holder)
        assertEquals(
            listOf("update:first", "render:first", "update:second", "render:second"),
            events,
        )
    }

    @Test
    fun `reattach resolves a unique key without scanning the item list`() {
        val context = RuntimeEnvironment.getApplication()
        val parent = FrameLayout(context)
        val events = mutableListOf<String>()
        val keys = List(512) { index -> CountingKey(index) }
        val adapter = LazyListAdapter()
        adapter.submitItems(keys.map { key -> recordingItem("first", events, key = key) })
        val holder = adapter.onCreateViewHolder(parent, adapter.getItemViewType(0))
        adapter.onBindViewHolder(holder, 0)
        adapter.onViewAttachedToWindow(holder)
        adapter.onViewDetachedFromWindow(holder)
        adapter.submitItems(keys.asReversed().map { key -> recordingItem("second", events, key = key) })
        CountingKey.equalityChecks = 0

        adapter.onViewAttachedToWindow(holder)

        assertTrue(
            "Expected indexed key lookup, equalityChecks=${CountingKey.equalityChecks}",
            CountingKey.equalityChecks <= 1,
        )
        assertEquals(
            listOf("update:first", "render:first", "update:second", "render:second"),
            events,
        )
        assertEquals(keys.lastIndex, holder.boundItemPosition)
    }

    @Test
    fun `duplicate keys never guess which attached holder owns the next item`() {
        val context = RuntimeEnvironment.getApplication()
        val parent = FrameLayout(context)
        val events = mutableListOf<String>()
        val adapter = LazyListAdapter()
        adapter.submitItems(
            listOf(
                recordingItem("first-A", events, key = "duplicate"),
                recordingItem("first-B", events, key = "duplicate"),
            ),
        )
        val firstHolder = adapter.onCreateViewHolder(parent, adapter.getItemViewType(0))
        val secondHolder = adapter.onCreateViewHolder(parent, adapter.getItemViewType(1))
        adapter.onBindViewHolder(firstHolder, 0)
        adapter.onBindViewHolder(secondHolder, 1)
        adapter.onViewAttachedToWindow(firstHolder)
        adapter.onViewAttachedToWindow(secondHolder)

        adapter.submitItems(
            listOf(
                recordingItem("second-A", events, key = "duplicate"),
                recordingItem("second-B", events, key = "duplicate"),
            ),
        )

        assertEquals(
            listOf(
                "update:first-A",
                "update:first-B",
                "render:first-A",
                "render:first-B",
            ),
            events,
        )
    }

    @Test
    fun `content type change waits for RecyclerView to replace incompatible holder`() {
        val context = RuntimeEnvironment.getApplication()
        val parent = FrameLayout(context)
        val events = mutableListOf<String>()
        val adapter = LazyListAdapter()
        adapter.submitItems(listOf(recordingItem("first", events, contentType = "row")))
        val holder = adapter.onCreateViewHolder(parent, adapter.getItemViewType(0))
        adapter.onBindViewHolder(holder, 0)
        adapter.onViewAttachedToWindow(holder)

        adapter.submitItems(listOf(recordingItem("second", events, contentType = "card")))

        assertEquals(listOf("update:first", "render:first"), events)
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

    private fun recordingItem(
        label: String,
        events: MutableList<String>,
        key: Any? = "stable",
        contentToken: Any? = "stable",
        contentType: Any? = null,
    ): LazyListItem {
        return LazyListItem(
            key = key,
            contentToken = contentToken,
            contentType = contentType,
            sessionFactory = LazyListItemSessionFactory {
                RecordingSession(events)
            },
            sessionUpdater = { session ->
                (session as RecordingSession).label = label
                events += "update:$label"
            },
        )
    }

    private fun recordingItem(
        events: MutableList<String>,
        sessionUpdater: (LazyListItemSession) -> Unit,
    ): LazyListItem {
        return LazyListItem(
            key = "stable",
            contentToken = "stable",
            sessionFactory = LazyListItemSessionFactory { RecordingSession(events) },
            sessionUpdater = sessionUpdater,
        )
    }

    private class RecordingSession(
        private val events: MutableList<String>,
    ) : LazyListItemSession {
        var label: String = ""

        override fun render() {
            events += "render:$label"
        }

        override fun dispose() = Unit
    }

    private data class CollidingKey(
        val value: String,
    ) {
        override fun hashCode(): Int = 1
    }

    private class CountingKey(
        private val value: Int,
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            equalityChecks += 1
            return other is CountingKey && value == other.value
        }

        override fun hashCode(): Int = value

        companion object {
            var equalityChecks: Int = 0
        }
    }
}
