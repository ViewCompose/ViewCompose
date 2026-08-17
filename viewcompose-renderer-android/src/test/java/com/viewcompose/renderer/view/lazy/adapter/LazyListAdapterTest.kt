package com.viewcompose.renderer.view.lazy.adapter

import com.viewcompose.ui.unit.dp

/*
 * 测试职责：覆盖 renderer view/lazy/adapter 中的 Lazy List Adapter 行为，防止渲染和 patch 契约在后续重构中回退。
 * Test responsibility: covers Lazy List Adapter behavior in renderer view/lazy/adapter and guards render and patch contracts against regressions.
 */

import com.viewcompose.ui.node.LazyListItem
import com.viewcompose.ui.node.LazyListItemKind
import com.viewcompose.ui.node.LazyListItemSession
import com.viewcompose.ui.node.lazyListItemSessionStrategy
import com.viewcompose.renderer.reconcile.LazyListChangePayload
import com.viewcompose.renderer.reconcile.LazyListAdapterChangedPayload
import com.viewcompose.renderer.reconcile.LazyListPresentationChangedPayload
import com.viewcompose.renderer.view.lazy.reuse.LazyPreparationCostTracker
import com.viewcompose.renderer.view.lazy.reuse.MountedTreeReuseCache
import com.viewcompose.ui.node.nativeContainer
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
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
    fun `view types survive disappearance and reject an unbounded compatibility taxonomy`() {
        val adapter = LazyListAdapter()
        adapter.submitItems(listOf(item(key = "first", contentType = "row")))
        val rowType = adapter.getItemViewType(0)

        adapter.submitItems(listOf(item(key = "replacement", contentType = "card")))
        val cardType = adapter.getItemViewType(0)
        adapter.submitItems(listOf(item(key = "returned", contentType = "row")))

        assertNotEquals(rowType, cardType)
        assertEquals(rowType, adapter.getItemViewType(0))

        val excessiveTypes = LazyListAdapter()
        excessiveTypes.submitItems(
            List(1_025) { index -> item(key = index, contentType = "type-$index") },
        )
        repeat(1_024) { position -> excessiveTypes.getItemViewType(position) }

        val failure = assertThrows(IllegalArgumentException::class.java) {
            excessiveTypes.getItemViewType(1_024)
        }
        assertTrue(failure.message.orEmpty().contains("at most 1024"))
    }

    @Test
    fun `new submission revision skips an attached holder with stable item revisions`() {
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
            listOf("update:first", "render:first"),
            events,
        )
    }

    @Test
    fun `content refresh is synchronous and queued payload does not render it twice`() {
        val context = RuntimeEnvironment.getApplication()
        val events = mutableListOf<String>()
        val adapter = LazyListAdapter()
        val recyclerView = androidx.recyclerview.widget.RecyclerView(context).apply {
            itemAnimator = null
            this.adapter = adapter
        }
        adapter.submitItems(listOf(recordingItem("first", events, contentRevision = 1)))
        val holder = adapter.onCreateViewHolder(recyclerView, adapter.getItemViewType(0))
        adapter.onBindViewHolder(holder, 0)
        adapter.onViewAttachedToWindow(holder)
        val observer = RecordingAdapterObserver()
        adapter.registerAdapterDataObserver(observer)

        adapter.submitItems(listOf(recordingItem("second", events, contentRevision = 2)))
        assertEquals(emptyList<String>(), observer.operations)
        adapter.onBindViewHolder(
            holder,
            0,
            mutableListOf(
                LazyListChangePayload.RevisionChanged(
                    previousContent = 1,
                    nextContent = 2,
                    previousEnvironment = null,
                    nextEnvironment = null,
                ),
            ),
        )

        assertEquals(
            listOf("update:first", "render:first", "update:second", "render:second"),
            events,
        )
        assertEquals(emptyList<String>(), observer.operations)
    }

    @Test
    fun `motion-off semantic failure schedules one payload retry that can commit`() {
        val context = RuntimeEnvironment.getApplication()
        val events = mutableListOf<String>()
        val failNextRender = booleanArrayOf(false)
        val adapter = LazyListAdapter()
        val recyclerView = androidx.recyclerview.widget.RecyclerView(context).apply {
            itemAnimator = null
            this.adapter = adapter
        }
        adapter.submitItems(
            listOf(retryingItem("first", events, failNextRender, contentRevision = 1)),
        )
        val holder = adapter.onCreateViewHolder(recyclerView, adapter.getItemViewType(0))
        adapter.onBindViewHolder(holder, 0)
        adapter.onViewAttachedToWindow(holder)
        val observer = RecordingAdapterObserver()
        adapter.registerAdapterDataObserver(observer)

        failNextRender[0] = true
        adapter.submitItems(
            listOf(retryingItem("second", events, failNextRender, contentRevision = 2)),
        )

        assertEquals(listOf("change:0:1"), observer.operations)
        assertEquals(
            listOf(
                "update:first",
                "render:first:true",
                "update:second",
                "render:second:false",
            ),
            events,
        )

        adapter.onBindViewHolder(
            holder,
            0,
            mutableListOf(LazyListAdapterChangedPayload),
        )
        adapter.onBindViewHolder(
            holder,
            0,
            mutableListOf(LazyListAdapterChangedPayload),
        )

        assertEquals(listOf("change:0:1"), observer.operations)
        assertEquals(
            listOf(
                "update:first",
                "render:first:true",
                "update:second",
                "render:second:false",
                "update:second",
                "render:second:true",
            ),
            events,
        )
    }

    @Test
    fun `motion-off semantic exception schedules one payload retry before propagating`() {
        val context = RuntimeEnvironment.getApplication()
        val events = mutableListOf<String>()
        val throwNextRender = booleanArrayOf(false)
        val adapter = LazyListAdapter()
        val recyclerView = androidx.recyclerview.widget.RecyclerView(context).apply {
            itemAnimator = null
            this.adapter = adapter
        }
        adapter.submitItems(
            listOf(throwingItem("first", events, throwNextRender, contentRevision = 1)),
        )
        val holder = adapter.onCreateViewHolder(recyclerView, adapter.getItemViewType(0))
        adapter.onBindViewHolder(holder, 0)
        adapter.onViewAttachedToWindow(holder)
        val observer = RecordingAdapterObserver()
        adapter.registerAdapterDataObserver(observer)

        throwNextRender[0] = true
        val failure = runCatching {
            adapter.submitItems(
                listOf(throwingItem("second", events, throwNextRender, contentRevision = 2)),
            )
        }.exceptionOrNull()

        assertTrue(failure is IllegalStateException)
        assertEquals("render failed:second", failure?.message)
        assertEquals(listOf("change:0:1"), observer.operations)
        assertEquals(
            listOf(
                "update:first",
                "render:first:true",
                "update:second",
                "render:second:throw",
            ),
            events,
        )

        adapter.onBindViewHolder(
            holder,
            0,
            mutableListOf(LazyListAdapterChangedPayload),
        )
        adapter.onBindViewHolder(
            holder,
            0,
            mutableListOf(LazyListAdapterChangedPayload),
        )

        assertEquals(listOf("change:0:1"), observer.operations)
        assertEquals(
            listOf(
                "update:first",
                "render:first:true",
                "update:second",
                "render:second:throw",
                "update:second",
                "render:second:true",
            ),
            events,
        )
    }

    @Test
    fun `motion-off semantic exception does not block another attached holder refresh`() {
        val context = RuntimeEnvironment.getApplication()
        val failingEvents = mutableListOf<String>()
        val healthyEvents = mutableListOf<String>()
        val throwNextRender = booleanArrayOf(false)
        val adapter = LazyListAdapter()
        val recyclerView = androidx.recyclerview.widget.RecyclerView(context).apply {
            itemAnimator = null
            this.adapter = adapter
        }
        adapter.submitItems(
            listOf(
                throwingItem(
                    label = "failing-first",
                    events = failingEvents,
                    throwNextRender = throwNextRender,
                    contentRevision = 1,
                    key = "failing",
                ),
                recordingItem(
                    label = "healthy-first",
                    events = healthyEvents,
                    key = "healthy",
                    contentRevision = 1,
                ),
            ),
        )
        val failingHolder = adapter.onCreateViewHolder(recyclerView, adapter.getItemViewType(0))
        val healthyHolder = adapter.onCreateViewHolder(recyclerView, adapter.getItemViewType(1))
        adapter.onBindViewHolder(failingHolder, 0)
        adapter.onViewAttachedToWindow(failingHolder)
        adapter.onBindViewHolder(healthyHolder, 1)
        adapter.onViewAttachedToWindow(healthyHolder)
        val observer = RecordingAdapterObserver()
        adapter.registerAdapterDataObserver(observer)

        throwNextRender[0] = true
        val failure = runCatching {
            adapter.submitItems(
                listOf(
                    throwingItem(
                        label = "failing-second",
                        events = failingEvents,
                        throwNextRender = throwNextRender,
                        contentRevision = 2,
                        key = "failing",
                    ),
                    recordingItem(
                        label = "healthy-second",
                        events = healthyEvents,
                        key = "healthy",
                        contentRevision = 2,
                    ),
                ),
            )
        }.exceptionOrNull()

        assertTrue(failure is IllegalStateException)
        assertEquals(listOf("change:0:1"), observer.operations)
        assertEquals(
            listOf(
                "update:healthy-first",
                "render:healthy-first",
                "update:healthy-second",
                "render:healthy-second",
            ),
            healthyEvents,
        )

        adapter.onBindViewHolder(
            failingHolder,
            0,
            mutableListOf(LazyListAdapterChangedPayload),
        )
        assertEquals(
            listOf(
                "update:failing-first",
                "render:failing-first:true",
                "update:failing-second",
                "render:failing-second:throw",
                "update:failing-second",
                "render:failing-second:true",
            ),
            failingEvents,
        )
    }

    @Test
    fun `motion-off retry notification failure is suppressed under session failure`() {
        val context = RuntimeEnvironment.getApplication()
        val events = mutableListOf<String>()
        val throwNextRender = booleanArrayOf(false)
        val adapter = LazyListAdapter()
        val recyclerView = androidx.recyclerview.widget.RecyclerView(context).apply {
            itemAnimator = null
            this.adapter = adapter
        }
        adapter.submitItems(
            listOf(throwingItem("first", events, throwNextRender, contentRevision = 1)),
        )
        val holder = adapter.onCreateViewHolder(recyclerView, adapter.getItemViewType(0))
        adapter.onBindViewHolder(holder, 0)
        adapter.onViewAttachedToWindow(holder)
        adapter.registerAdapterDataObserver(ThrowingAdapterObserver())

        throwNextRender[0] = true
        val failure = runCatching {
            adapter.submitItems(
                listOf(throwingItem("second", events, throwNextRender, contentRevision = 2)),
            )
        }.exceptionOrNull()

        assertTrue(failure is IllegalStateException)
        assertEquals("render failed:second", failure?.message)
        assertEquals(1, failure?.suppressed?.size)
        assertEquals("observer failed", failure?.suppressed?.single()?.message)
    }

    @Test
    fun `merged payload after consecutive submissions acknowledges only exact committed semantics`() {
        val context = RuntimeEnvironment.getApplication()
        val parent = FrameLayout(context)
        val events = mutableListOf<String>()
        val adapter = LazyListAdapter()
        adapter.submitItems(listOf(recordingItem("first", events, contentRevision = 1)))
        val holder = adapter.onCreateViewHolder(parent, adapter.getItemViewType(0))
        adapter.onBindViewHolder(holder, 0)
        adapter.onViewAttachedToWindow(holder)

        adapter.submitItems(listOf(recordingItem("second", events, contentRevision = 2)))
        adapter.submitItems(listOf(recordingItem("third", events, contentRevision = 3)))

        assertFalse(
            holder.acknowledgeCommittedBinding(
                item = recordingItem("uncommitted", events, contentRevision = 4),
                submissionRevision = 3L,
                position = 0,
            ),
        )
        assertFalse(
            holder.acknowledgeCommittedBinding(
                item = recordingItem(
                    "uncommitted-environment",
                    events,
                    contentRevision = 3,
                    environmentRevision = "dark",
                ),
                submissionRevision = 3L,
                position = 0,
            ),
        )
        adapter.onBindViewHolder(
            holder,
            0,
            mutableListOf(
                LazyListChangePayload.RevisionChanged(
                    previousContent = 1,
                    nextContent = 2,
                    previousEnvironment = null,
                    nextEnvironment = null,
                ),
                LazyListChangePayload.RevisionChanged(
                    previousContent = 2,
                    nextContent = 3,
                    previousEnvironment = null,
                    nextEnvironment = null,
                ),
            ),
        )

        assertEquals(
            listOf(
                "update:first",
                "render:first",
                "update:second",
                "render:second",
                "update:third",
                "render:third",
            ),
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

        adapter.submitItems(listOf(recordingItem("second", events, contentRevision = "second")))

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
            listOf("update:first", "render:first"),
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
                "render:first-A",
                "update:first-B",
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

    @Test
    fun `span change requests layout without rendering stable item content`() {
        val context = RuntimeEnvironment.getApplication()
        val parent = FrameLayout(context)
        val events = mutableListOf<String>()
        val adapter = LazyListAdapter()
        adapter.submitItems(listOf(recordingItem("first", events)))
        val holder = adapter.onCreateViewHolder(parent, adapter.getItemViewType(0))
        adapter.onBindViewHolder(holder, 0)
        adapter.onViewAttachedToWindow(holder)
        val observer = RecordingAdapterObserver()
        adapter.registerAdapterDataObserver(observer)

        adapter.submitItems(
            listOf(
                recordingItem(
                    "second",
                    events,
                    span = com.viewcompose.ui.node.policy.GridItemSpan.Fixed(2),
                ),
            ),
        )
        adapter.onBindViewHolder(
            holder,
            0,
            mutableListOf(LazyListPresentationChangedPayload),
        )

        assertEquals(listOf("update:first", "render:first"), events)
        assertEquals(com.viewcompose.ui.node.policy.GridItemSpan.Fixed(2), adapter.itemSpanAt(0))
        assertEquals(listOf("change:0:1"), observer.operations)
    }

    @Test
    fun `cold activation bootstrap is sampled and committed payload acknowledgement does not resample it`() {
        val context = RuntimeEnvironment.getApplication()
        val parent = FrameLayout(context)
        val events = mutableListOf<String>()
        val costs = LazyPreparationCostTracker()
        val reuseKey = MountedTreeReuseCache.ReuseKey(LazyListItemKind.Item, "row")
        val adapter = LazyListAdapter(preparationCosts = costs)
        adapter.submitItems(
            listOf(
                recordingItem(
                    label = "first",
                    events = events,
                    contentRevision = 1,
                    contentType = "row",
                ),
            ),
        )
        val holder = adapter.onCreateViewHolder(parent, adapter.getItemViewType(0))
        adapter.onBindViewHolder(holder, 0)

        assertEquals(null, costs.estimatedCostNanos(reuseKey))
        adapter.onViewAttachedToWindow(holder)
        val coldActivationCost = checkNotNull(costs.estimatedCostNanos(reuseKey))

        adapter.submitItems(
            listOf(
                recordingItem(
                    label = "second",
                    events = events,
                    contentRevision = 2,
                    contentType = "row",
                ),
            ),
        )
        repeat(8) {
            adapter.onBindViewHolder(
                holder,
                0,
                mutableListOf(LazyListAdapterChangedPayload),
            )
        }

        assertEquals(coldActivationCost, costs.estimatedCostNanos(reuseKey))
        assertEquals(
            listOf("update:first", "render:first", "update:second", "render:second"),
            events,
        )
    }

    @Test
    fun `same key order batches adjacent changes without structural notifications`() {
        val adapter = LazyListAdapter()
        val recyclerView = androidx.recyclerview.widget.RecyclerView(
            RuntimeEnvironment.getApplication(),
        ).apply {
            itemAnimator = androidx.recyclerview.widget.DefaultItemAnimator()
            this.adapter = adapter
        }
        adapter.submitItems(
            listOf(
                item(key = "A", contentRevision = 1),
                item(key = "B", contentRevision = 1),
                item(key = "C", contentRevision = 1),
            ),
        )
        val observer = RecordingAdapterObserver()
        adapter.registerAdapterDataObserver(observer)

        assertTrue(
            adapter.submitItems(
                listOf(
                    item(key = "A", contentRevision = 2),
                    item(key = "B", contentRevision = 2),
                    item(key = "C", contentRevision = 1),
                ),
            ),
        )

        assertEquals(listOf("change:0:2"), observer.operations)
        assertEquals(adapter, recyclerView.adapter)
    }

    @Test
    fun `left cyclic rotation dispatches minimum moves before changed ranges`() {
        val adapter = LazyListAdapter()
        val recyclerView = androidx.recyclerview.widget.RecyclerView(
            RuntimeEnvironment.getApplication(),
        ).apply {
            itemAnimator = androidx.recyclerview.widget.DefaultItemAnimator()
            this.adapter = adapter
        }
        adapter.submitItems(
            listOf(
                item(key = "A", contentRevision = 1),
                item(key = "B", contentRevision = 1),
                item(key = "C", contentRevision = 1),
                item(key = "D", contentRevision = 1),
                item(key = "E", contentRevision = 1),
            ),
        )
        val observer = RecordingAdapterObserver()
        adapter.registerAdapterDataObserver(observer)

        adapter.submitItems(
            listOf(
                item(key = "C", contentRevision = 2),
                item(key = "D", contentRevision = 2),
                item(key = "E", contentRevision = 1),
                item(key = "A", contentRevision = 1),
                item(key = "B", contentRevision = 1),
            ),
        )

        assertEquals(
            listOf(
                "move:0:4:1",
                "move:0:4:1",
                "change:0:2",
            ),
            observer.operations,
        )
        assertEquals(adapter, recyclerView.adapter)
    }

    @Test
    fun `right cyclic rotation dispatches minimum moves`() {
        val adapter = LazyListAdapter()
        adapter.submitItems(
            listOf(
                item(key = "A"),
                item(key = "B"),
                item(key = "C"),
                item(key = "D"),
                item(key = "E"),
            ),
        )
        val observer = RecordingAdapterObserver()
        adapter.registerAdapterDataObserver(observer)

        adapter.submitItems(
            listOf(
                item(key = "D"),
                item(key = "E"),
                item(key = "A"),
                item(key = "B"),
                item(key = "C"),
            ),
        )

        assertEquals(
            listOf(
                "move:4:0:1",
                "move:4:0:1",
            ),
            observer.operations,
        )
    }

    @Test
    fun `all cyclic adapter dispatches produce target order and final change ranges`() {
        for (size in 2..128) {
            val base = List(size) { index -> item(key = "$size:$index") }
            val baseKeys = base.map(LazyListItem::key)
            val adapter = LazyListAdapter()
            adapter.submitItems(base)
            val observer = ModelApplyingAdapterObserver()
            adapter.registerAdapterDataObserver(observer)

            for (leftRotation in 1 until size) {
                val rotated = base.drop(leftRotation) + base.take(leftRotation)
                val changedPositions = BooleanArray(size) { position ->
                    position == 0 || position == size - 1 || position % 13 == 4
                }
                val target = rotated.mapIndexed { position, oldItem ->
                    if (changedPositions[position]) {
                        item(
                            key = oldItem.key,
                            span = com.viewcompose.ui.node.policy.GridItemSpan.Fixed(2),
                        )
                    } else {
                        oldItem
                    }
                }
                val targetKeys = target.map(LazyListItem::key)
                val case = "size=$size leftRotation=$leftRotation"

                observer.begin(baseKeys)
                assertTrue(case, adapter.submitItems(target))
                assertEquals(case, targetKeys, observer.keys)
                assertEquals(case, minOf(leftRotation, size - leftRotation), observer.moveCount)
                assertEquals(case, expectedChangedRangeRecords(changedPositions), observer.changes)
                assertEquals(case, emptyList<String>(), observer.unexpectedOperations)

                observer.begin(targetKeys)
                assertTrue("$case reset", adapter.submitItems(base))
                assertEquals("$case reset", baseKeys, observer.keys)
                assertEquals(
                    "$case reset",
                    minOf(leftRotation, size - leftRotation),
                    observer.moveCount,
                )
                assertEquals("$case reset", emptyList<String>(), observer.unexpectedOperations)
            }
        }
    }

    @Test
    fun `laid out RecyclerView preserves holder and item session identity across rotations`() {
        val context = RuntimeEnvironment.getApplication()
        val keys = List(8) { index -> "row-$index" }
        val sessionCreations = mutableMapOf<String, Int>()
        val adapter = LazyListAdapter()
        val recyclerView = androidx.recyclerview.widget.RecyclerView(context).apply {
            layoutManager = androidx.recyclerview.widget.LinearLayoutManager(context)
            itemAnimator = null
            this.adapter = adapter
        }
        val base = keys.map { key -> identityItem(key, sessionCreations) }
        adapter.submitItems(base)
        layoutRecyclerView(recyclerView)
        val stableIds = keys.associateWith { key ->
            adapter.getItemId(keys.indexOf(key))
        }
        val originalHolders = keys.associateWith { key ->
            checkNotNull(recyclerView.findViewHolderForItemId(stableIds.getValue(key)))
        }
        assertLaidOutIdentities(
            recyclerView = recyclerView,
            orderedKeys = keys,
            stableIds = stableIds,
            originalHolders = originalHolders,
            phase = "initial",
        )

        repeat(3) { cycle ->
            for (leftRotation in listOf(1, 3, 7, 4, 2, 6, 5)) {
                val rotatedKeys = keys.drop(leftRotation) + keys.take(leftRotation)
                adapter.submitItems(
                    rotatedKeys.map { key -> identityItem(key, sessionCreations) },
                )
                layoutRecyclerView(recyclerView)
                assertLaidOutIdentities(
                    recyclerView = recyclerView,
                    orderedKeys = rotatedKeys,
                    stableIds = stableIds,
                    originalHolders = originalHolders,
                    phase = "cycle=$cycle leftRotation=$leftRotation",
                )

                adapter.submitItems(base)
                layoutRecyclerView(recyclerView)
                assertLaidOutIdentities(
                    recyclerView = recyclerView,
                    orderedKeys = keys,
                    stableIds = stableIds,
                    originalHolders = originalHolders,
                    phase = "cycle=$cycle leftRotation=$leftRotation reset",
                )
            }
        }

        assertEquals(keys.associateWith { 1 }, sessionCreations)
    }

    private fun item(
        key: Any,
        contentRevision: Any? = key,
        contentType: Any? = null,
        kind: LazyListItemKind = LazyListItemKind.Item,
        span: com.viewcompose.ui.node.policy.GridItemSpan =
            com.viewcompose.ui.node.policy.GridItemSpan.Single,
    ): LazyListItem {
        return LazyListItem(
            key = key,
            contentRevision = contentRevision,
            contentType = contentType,
            kind = kind,
            span = span,
            sessionStrategy = lazyListItemSessionStrategy(
                create = {
                    object : LazyListItemSession {
                        override fun render() = true

                        override fun dispose() = Unit
                    }
                },
                update = {},
            ),
        )
    }

    private fun recordingItem(
        label: String,
        events: MutableList<String>,
        key: Any = "stable",
        contentRevision: Any? = "stable",
        environmentRevision: Any? = null,
        contentType: Any? = null,
        span: com.viewcompose.ui.node.policy.GridItemSpan = com.viewcompose.ui.node.policy.GridItemSpan.Single,
    ): LazyListItem {
        return LazyListItem(
            key = key,
            contentRevision = contentRevision,
            environmentRevision = environmentRevision,
            contentType = contentType,
            span = span,
            sessionStrategy = lazyListItemSessionStrategy(
                create = { RecordingSession(events) },
                update = { session ->
                    (session as RecordingSession).label = label
                    events += "update:$label"
                },
            ),
        )
    }

    private fun retryingItem(
        label: String,
        events: MutableList<String>,
        failNextRender: BooleanArray,
        contentRevision: Any?,
    ): LazyListItem {
        return LazyListItem(
            key = "stable",
            contentRevision = contentRevision,
            sessionStrategy = lazyListItemSessionStrategy(
                create = { RetryingSession(events, failNextRender) },
                update = { session ->
                    (session as RetryingSession).label = label
                    events += "update:$label"
                },
            ),
        )
    }

    private fun throwingItem(
        label: String,
        events: MutableList<String>,
        throwNextRender: BooleanArray,
        contentRevision: Any?,
        key: Any = "stable",
    ): LazyListItem {
        return LazyListItem(
            key = key,
            contentRevision = contentRevision,
            sessionStrategy = lazyListItemSessionStrategy(
                create = { ThrowingOnceSession(events, throwNextRender) },
                update = { session ->
                    (session as ThrowingOnceSession).label = label
                    events += "update:$label"
                },
            ),
        )
    }

    private fun identityItem(
        key: String,
        sessionCreations: MutableMap<String, Int>,
    ): LazyListItem {
        return LazyListItem(
            key = key,
            contentRevision = key,
            contentType = "identity-row",
            sessionStrategy = lazyListItemSessionStrategy(
                create = { handle ->
                    val container = handle.nativeContainer as ViewGroup
                    sessionCreations[key] = sessionCreations.getOrDefault(key, 0) + 1
                    object : LazyListItemSession {
                        override fun render(): Boolean {
                            if (container.childCount == 0) {
                                container.addView(
                                    TextView(container.context).apply {
                                        text = key
                                        layoutParams = ViewGroup.LayoutParams(
                                            ViewGroup.LayoutParams.MATCH_PARENT,
                                            40,
                                        )
                                    },
                                )
                            }
                            return true
                        }

                        override fun dispose() {
                            container.removeAllViews()
                        }
                    }
                },
                update = {},
            ),
        )
    }

    private fun recordingItem(
        events: MutableList<String>,
        sessionUpdater: (LazyListItemSession) -> Unit,
    ): LazyListItem {
        return LazyListItem(
            key = "stable",
            contentRevision = "stable",
            sessionStrategy = lazyListItemSessionStrategy(
                create = { RecordingSession(events) },
                update = sessionUpdater,
            ),
        )
    }

    private class RecordingSession(
        private val events: MutableList<String>,
    ) : LazyListItemSession {
        var label: String = ""

        override fun render(): Boolean {
            events += "render:$label"
            return true
        }

        override fun dispose() = Unit
    }

    private class RetryingSession(
        private val events: MutableList<String>,
        private val failNextRender: BooleanArray,
    ) : LazyListItemSession {
        var label: String = ""

        override fun render(): Boolean {
            val committed = !failNextRender[0]
            failNextRender[0] = false
            events += "render:$label:$committed"
            return committed
        }

        override fun dispose() = Unit
    }

    private class ThrowingOnceSession(
        private val events: MutableList<String>,
        private val throwNextRender: BooleanArray,
    ) : LazyListItemSession {
        var label: String = ""

        override fun render(): Boolean {
            val shouldThrow = throwNextRender[0]
            throwNextRender[0] = false
            events += "render:$label:${if (shouldThrow) "throw" else "true"}"
            if (shouldThrow) throw IllegalStateException("render failed:$label")
            return true
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

    private class RecordingAdapterObserver :
        androidx.recyclerview.widget.RecyclerView.AdapterDataObserver() {
        val operations = mutableListOf<String>()

        override fun onChanged() {
            operations += "reload"
        }

        override fun onItemRangeChanged(
            positionStart: Int,
            itemCount: Int,
            payload: Any?,
        ) {
            assertEquals(LazyListAdapterChangedPayload, payload)
            operations += "change:$positionStart:$itemCount"
        }

        override fun onItemRangeInserted(positionStart: Int, itemCount: Int) {
            operations += "insert:$positionStart:$itemCount"
        }

        override fun onItemRangeRemoved(positionStart: Int, itemCount: Int) {
            operations += "remove:$positionStart:$itemCount"
        }

        override fun onItemRangeMoved(fromPosition: Int, toPosition: Int, itemCount: Int) {
            operations += "move:$fromPosition:$toPosition:$itemCount"
        }
    }

    private class ModelApplyingAdapterObserver :
        androidx.recyclerview.widget.RecyclerView.AdapterDataObserver() {
        var keys: MutableList<Any> = mutableListOf()
            private set
        var moveCount: Int = 0
            private set
        val changes = mutableListOf<String>()
        val unexpectedOperations = mutableListOf<String>()

        fun begin(sourceKeys: List<Any>) {
            keys = sourceKeys.toMutableList()
            moveCount = 0
            changes.clear()
            unexpectedOperations.clear()
        }

        override fun onChanged() {
            unexpectedOperations += "reload"
        }

        override fun onItemRangeChanged(
            positionStart: Int,
            itemCount: Int,
            payload: Any?,
        ) {
            assertEquals(LazyListAdapterChangedPayload, payload)
            changes += "$positionStart:$itemCount"
        }

        override fun onItemRangeInserted(positionStart: Int, itemCount: Int) {
            unexpectedOperations += "insert:$positionStart:$itemCount"
        }

        override fun onItemRangeRemoved(positionStart: Int, itemCount: Int) {
            unexpectedOperations += "remove:$positionStart:$itemCount"
        }

        override fun onItemRangeMoved(fromPosition: Int, toPosition: Int, itemCount: Int) {
            assertEquals(1, itemCount)
            keys.add(toPosition, keys.removeAt(fromPosition))
            moveCount += 1
        }
    }

    private fun expectedChangedRangeRecords(changedPositions: BooleanArray): List<String> {
        val ranges = mutableListOf<String>()
        var position = 0
        while (position < changedPositions.size) {
            if (!changedPositions[position]) {
                position += 1
                continue
            }
            val start = position
            while (position < changedPositions.size && changedPositions[position]) {
                position += 1
            }
            ranges += "$start:${position - start}"
        }
        return ranges
    }

    private fun layoutRecyclerView(recyclerView: androidx.recyclerview.widget.RecyclerView) {
        recyclerView.measure(
            View.MeasureSpec.makeMeasureSpec(320, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(400, View.MeasureSpec.EXACTLY),
        )
        recyclerView.layout(0, 0, 320, 400)
    }

    private fun assertLaidOutIdentities(
        recyclerView: androidx.recyclerview.widget.RecyclerView,
        orderedKeys: List<String>,
        stableIds: Map<String, Long>,
        originalHolders: Map<String, androidx.recyclerview.widget.RecyclerView.ViewHolder>,
        phase: String,
    ) {
        orderedKeys.forEachIndexed { position, key ->
            val holder = checkNotNull(
                recyclerView.findViewHolderForItemId(stableIds.getValue(key)),
            ) { "$phase missing holder for key=$key" }
            assertEquals("$phase position=$position", key, (holder as LazyListViewHolder).boundItemKey)
            assertTrue("$phase replaced holder for key=$key", holder === originalHolders.getValue(key))
            assertEquals("$phase adapter position=$position", position, holder.bindingAdapterPosition)
        }
    }

    private class ThrowingAdapterObserver :
        androidx.recyclerview.widget.RecyclerView.AdapterDataObserver() {
        override fun onItemRangeChanged(
            positionStart: Int,
            itemCount: Int,
            payload: Any?,
        ) {
            throw IllegalArgumentException("observer failed")
        }
    }
}
