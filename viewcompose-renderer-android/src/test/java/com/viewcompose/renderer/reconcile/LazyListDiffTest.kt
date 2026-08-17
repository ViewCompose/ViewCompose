package com.viewcompose.renderer.reconcile

/*
 * 测试职责：覆盖 renderer reconcile 中的 Lazy List Diff 行为，防止渲染和 patch 契约在后续重构中回退。
 * Test responsibility: covers Lazy List Diff behavior in renderer reconcile and guards render and patch contracts against regressions.
 */

import com.viewcompose.ui.modifier.Modifier
import com.viewcompose.ui.node.LazyListItem
import com.viewcompose.ui.node.LazyListItemKind
import com.viewcompose.ui.node.LazyListItemSession
import com.viewcompose.ui.node.lazyListItemSessionStrategy
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
    fun `keeps latest lazy item instances when keyed diff produces no updates`() {
        val previous = item("A", contentRevision = "stable")
        val next = item("A", contentRevision = "stable")

        val result = LazyListDiff.calculate(
            previous = listOf(previous),
            next = listOf(next),
        )

        assertEquals(emptyList<LazyListUpdate>(), result.updates)
        assertSame(next, result.items.first())
    }

    @Test
    fun `keeps latest page instances when pager pages are structurally stable`() {
        val previous = item("page-1", contentRevision = "stable")
        val next = item("page-1", contentRevision = "stable")

        val result = LazyListDiff.calculate(
            previous = listOf(previous),
            next = listOf(next),
        )

        assertEquals(emptyList<LazyListUpdate>(), result.updates)
        assertSame(next, result.items.first())
    }

    @Test
    fun `keeps latest grid item instances when grid rows are structurally stable`() {
        val previous = item("grid-1", contentRevision = "stable")
        val next = item("grid-1", contentRevision = "stable")

        val result = LazyListDiff.calculate(
            previous = listOf(previous),
            next = listOf(next),
        )

        assertEquals(emptyList<LazyListUpdate>(), result.updates)
        assertSame(next, result.items.first())
    }

    @Test
    fun `emits content and environment revision payload on change updates`() {
        val result = LazyListDiff.calculate(
            previous = listOf(item("A", contentRevision = 1)),
            next = listOf(item("A", contentRevision = 2)),
        )

        assertEquals(1, result.updates.size)
        val update = result.updates.first()
        assertTrue(update is LazyListUpdate.Change)
        val payload = (update as LazyListUpdate.Change).payload
        assertTrue(payload is LazyListChangePayload.RevisionChanged)
        payload as LazyListChangePayload.RevisionChanged
        assertEquals(1, payload.previousContent)
        assertEquals(2, payload.nextContent)
        assertEquals(null, payload.previousEnvironment)
        assertEquals(null, payload.nextEnvironment)
    }

    @Test
    fun `layout metadata change does not claim item content revision changed`() {
        val result = LazyListDiff.calculate(
            previous = listOf(item("A")),
            next = listOf(item("A", span = com.viewcompose.ui.node.policy.GridItemSpan.Fixed(2))),
        )

        assertEquals(
            LazyListPresentationChangedPayload,
            (result.updates.single() as LazyListUpdate.Change).payload,
        )
    }

    @Test
    fun `adapter plan batches adjacent changes when key order is stable`() {
        val result = LazyListDiff.calculateAdapterUpdatePlan(
            previous = listOf(
                item("A", contentRevision = 1),
                item("B", contentRevision = 1),
                item("C", contentRevision = 1),
                item("D", contentRevision = 1),
                item("E", contentRevision = 1),
            ),
            next = listOf(
                item("A", contentRevision = 2),
                item("B", contentRevision = 2),
                item("C", contentRevision = 1),
                item("D", contentRevision = 2),
                item("E", contentRevision = 2),
            ),
            supportsKeyedDiff = true,
        )

        assertEquals(
            LazyListAdapterUpdatePlan.SameKeyOrderChanges(
                ranges = listOf(
                    LazyListChangedRange(positionStart = 0, itemCount = 2),
                    LazyListChangedRange(positionStart = 3, itemCount = 2),
                ),
            ),
            result,
        )
    }

    @Test
    fun `adapter plan can publish semantic changes without native change ranges`() {
        val result = LazyListDiff.calculateAdapterUpdatePlan(
            previous = listOf(
                item("A", contentRevision = 1),
                item("B", environmentRevision = 1),
            ),
            next = listOf(
                item("A", contentRevision = 2),
                item("B", environmentRevision = 2),
            ),
            supportsKeyedDiff = true,
            includeSemanticChanges = false,
        )

        assertEquals(
            LazyListAdapterUpdatePlan.SameKeyOrderChanges(ranges = emptyList()),
            result,
        )
    }

    @Test
    fun `adapter plan keeps physical changes when semantic notifications are disabled`() {
        val result = LazyListDiff.calculateAdapterUpdatePlan(
            previous = listOf(item("A", contentRevision = 1)),
            next = listOf(
                item(
                    "A",
                    contentRevision = 2,
                    span = com.viewcompose.ui.node.policy.GridItemSpan.Fixed(2),
                ),
            ),
            supportsKeyedDiff = true,
            includeSemanticChanges = false,
        )

        assertEquals(
            LazyListAdapterUpdatePlan.SameKeyOrderChanges(
                ranges = listOf(LazyListChangedRange(positionStart = 0, itemCount = 1)),
            ),
            result,
        )
    }

    @Test
    fun `adapter plan skips equal semantics without replacing item callbacks`() {
        val result = LazyListDiff.calculateAdapterUpdatePlan(
            previous = listOf(item("A", contentRevision = 1)),
            next = listOf(item("A", contentRevision = 1)),
            supportsKeyedDiff = true,
        )

        assertEquals(LazyListAdapterUpdatePlan.NoChange, result)
    }

    @Test
    fun `adapter plan retains structural diff for non-cyclic keyed reorder`() {
        val result = LazyListDiff.calculateAdapterUpdatePlan(
            previous = listOf(item("A"), item("B"), item("C")),
            next = listOf(item("C"), item("B"), item("A")),
            supportsKeyedDiff = true,
        )

        assertTrue(result is LazyListAdapterUpdatePlan.StructuralDiff)
    }

    @Test
    fun `adapter plan expresses left cyclic rotation with minimum moves`() {
        val result = LazyListDiff.calculateAdapterUpdatePlan(
            previous = listOf(item("A"), item("B"), item("C"), item("D"), item("E")),
            next = listOf(item("C"), item("D"), item("E"), item("A"), item("B")),
            supportsKeyedDiff = true,
        )

        assertEquals(
            LazyListAdapterUpdatePlan.CyclicRotation(
                direction = LazyListRotationDirection.Left,
                moveCount = 2,
                changedRanges = emptyList(),
            ),
            result,
        )
    }

    @Test
    fun `adapter plan expresses right cyclic rotation with minimum moves`() {
        val result = LazyListDiff.calculateAdapterUpdatePlan(
            previous = listOf(item("A"), item("B"), item("C"), item("D"), item("E")),
            next = listOf(item("D"), item("E"), item("A"), item("B"), item("C")),
            supportsKeyedDiff = true,
        )

        assertEquals(
            LazyListAdapterUpdatePlan.CyclicRotation(
                direction = LazyListRotationDirection.Right,
                moveCount = 2,
                changedRanges = emptyList(),
            ),
            result,
        )
    }

    @Test
    fun `adapter rotation plan batches semantic changes by next position`() {
        val result = LazyListDiff.calculateAdapterUpdatePlan(
            previous = listOf(
                item("A", contentRevision = 1),
                item("B", contentRevision = 1),
                item("C", contentRevision = 1),
                item("D", contentRevision = 1),
                item("E", contentRevision = 1),
            ),
            next = listOf(
                item("C", contentRevision = 2),
                item("D", contentRevision = 2),
                item("E", contentRevision = 1),
                item("A", contentRevision = 2),
                item("B", contentRevision = 1),
            ),
            supportsKeyedDiff = true,
        )

        assertEquals(
            LazyListAdapterUpdatePlan.CyclicRotation(
                direction = LazyListRotationDirection.Left,
                moveCount = 2,
                changedRanges = listOf(
                    LazyListChangedRange(positionStart = 0, itemCount = 2),
                    LazyListChangedRange(positionStart = 3, itemCount = 1),
                ),
            ),
            result,
        )
    }

    @Test
    fun `adapter rotation omits semantic ranges when native motion is disabled`() {
        val result = LazyListDiff.calculateAdapterUpdatePlan(
            previous = listOf(
                item("A", contentRevision = 1),
                item("B", contentRevision = 1),
                item("C", contentRevision = 1),
            ),
            next = listOf(
                item("B", contentRevision = 2),
                item("C", contentRevision = 2),
                item("A", contentRevision = 2),
            ),
            supportsKeyedDiff = true,
            includeSemanticChanges = false,
        )

        assertEquals(
            LazyListAdapterUpdatePlan.CyclicRotation(
                direction = LazyListRotationDirection.Left,
                moveCount = 1,
                changedRanges = emptyList(),
            ),
            result,
        )
    }

    @Test
    fun `all cyclic rotations use minimum moves and final change coordinates`() {
        for (size in 2..128) {
            val previous = List(size) { index ->
                item(key = "$size:$index", contentRevision = 0)
            }
            val previousKeys = previous.map(LazyListItem::key)
            for (leftRotation in 1 until size) {
                val rotated = previous.drop(leftRotation) + previous.take(leftRotation)
                val changedPositions = BooleanArray(size) { position ->
                    position == 0 || position == size - 1 || position % 13 == 4
                }
                val next = rotated.mapIndexed { position, oldItem ->
                    if (changedPositions[position]) {
                        item(
                            key = oldItem.key as String,
                            contentRevision = "$leftRotation:$position",
                        )
                    } else {
                        oldItem
                    }
                }
                val plan = LazyListDiff.calculateAdapterUpdatePlan(
                    previous = previous,
                    next = next,
                    supportsKeyedDiff = true,
                    includeSemanticChanges = true,
                ) as LazyListAdapterUpdatePlan.CyclicRotation
                val expectedMoveCount = minOf(leftRotation, size - leftRotation)
                val expectedDirection = if (leftRotation <= size - leftRotation) {
                    LazyListRotationDirection.Left
                } else {
                    LazyListRotationDirection.Right
                }
                val simulatedKeys = previousKeys.toMutableList()
                repeat(plan.moveCount) {
                    when (plan.direction) {
                        LazyListRotationDirection.Left -> {
                            simulatedKeys += simulatedKeys.removeAt(0)
                        }
                        LazyListRotationDirection.Right -> {
                            simulatedKeys.add(0, simulatedKeys.removeAt(simulatedKeys.lastIndex))
                        }
                    }
                }
                val case = "size=$size leftRotation=$leftRotation"

                assertEquals(case, expectedDirection, plan.direction)
                assertEquals(case, expectedMoveCount, plan.moveCount)
                assertEquals(case, next.map(LazyListItem::key), simulatedKeys)
                assertEquals(case, expectedChangedRanges(changedPositions), plan.changedRanges)
            }
        }
    }

    @Test
    fun `adapter plan reloads changed duplicate keys even when order is stable`() {
        val result = LazyListDiff.calculateAdapterUpdatePlan(
            previous = listOf(
                item("duplicate", contentRevision = 1),
                item("duplicate", contentRevision = 1),
            ),
            next = listOf(
                item("duplicate", contentRevision = 2),
                item("duplicate", contentRevision = 2),
            ),
            supportsKeyedDiff = false,
        )

        assertEquals(LazyListAdapterUpdatePlan.ReloadAll, result)
    }

    @Test
    fun `adapter plan never treats duplicate key permutation as rotation`() {
        val result = LazyListDiff.calculateAdapterUpdatePlan(
            previous = listOf(item("duplicate"), item("B"), item("duplicate")),
            next = listOf(item("B"), item("duplicate"), item("duplicate")),
            supportsKeyedDiff = false,
        )

        assertEquals(LazyListAdapterUpdatePlan.ReloadAll, result)
    }

    @Test
    fun `adapter plan handles empty and single item snapshots without rotation`() {
        assertEquals(
            LazyListAdapterUpdatePlan.NoChange,
            LazyListDiff.calculateAdapterUpdatePlan(
                previous = mutableListOf(),
                next = mutableListOf(),
                supportsKeyedDiff = true,
            ),
        )
        assertEquals(
            LazyListAdapterUpdatePlan.SameKeyOrderChanges(
                ranges = listOf(LazyListChangedRange(positionStart = 0, itemCount = 1)),
            ),
            LazyListDiff.calculateAdapterUpdatePlan(
                previous = listOf(item("A", contentRevision = 1)),
                next = listOf(item("A", contentRevision = 2)),
                supportsKeyedDiff = true,
            ),
        )
        assertTrue(
            LazyListDiff.calculateAdapterUpdatePlan(
                previous = listOf(item("A")),
                next = listOf(item("B")),
                supportsKeyedDiff = true,
            ) is LazyListAdapterUpdatePlan.StructuralDiff,
        )
    }

    @Test
    fun `adapter same-order plan observes every item semantic field`() {
        val previous = listOf(
            item("content"),
            item("environment"),
            item("type"),
            item("kind"),
            item("span"),
        )
        val next = listOf(
            item("content", contentRevision = "changed"),
            item("environment", environmentRevision = "changed"),
            item("type", contentType = "changed"),
            item("kind", kind = LazyListItemKind.StickyHeader),
            item("span", span = com.viewcompose.ui.node.policy.GridItemSpan.Fixed(2)),
        )

        assertEquals(
            LazyListAdapterUpdatePlan.SameKeyOrderChanges(
                ranges = listOf(LazyListChangedRange(positionStart = 0, itemCount = 5)),
            ),
            LazyListDiff.calculateAdapterUpdatePlan(
                previous = previous,
                next = next,
                supportsKeyedDiff = true,
            ),
        )
    }

    @Test
    fun `adapter structural diff shares payload so adjacent changes can batch`() {
        val result = LazyListDiff.calculateAdapterUpdatePlan(
            previous = listOf(
                item("A", contentRevision = 1),
                item("B", contentRevision = 1),
            ),
            next = listOf(
                item("A", contentRevision = 2),
                item("B", contentRevision = 2),
                item("C", contentRevision = 1),
            ),
            supportsKeyedDiff = true,
        )
        val operations = mutableListOf<String>()
        val structural = result as LazyListAdapterUpdatePlan.StructuralDiff

        structural.result.dispatchUpdatesTo(
            object : androidx.recyclerview.widget.ListUpdateCallback {
                override fun onInserted(position: Int, count: Int) {
                    operations += "insert:$position:$count"
                }

                override fun onRemoved(position: Int, count: Int) {
                    operations += "remove:$position:$count"
                }

                override fun onMoved(fromPosition: Int, toPosition: Int) {
                    operations += "move:$fromPosition:$toPosition"
                }

                override fun onChanged(position: Int, count: Int, payload: Any?) {
                    assertEquals(LazyListAdapterChangedPayload, payload)
                    operations += "change:$position:$count"
                }
            },
        )

        assertTrue(operations.contains("insert:2:1"))
        assertTrue(operations.contains("change:0:2"))
    }

    @Test
    fun `adapter structural diff omits semantic payloads when native motion is disabled`() {
        val result = LazyListDiff.calculateAdapterUpdatePlan(
            previous = listOf(
                item("A", contentRevision = 1),
                item("B", contentRevision = 1),
                item("C", contentRevision = 1),
            ),
            next = listOf(
                item("C", contentRevision = 2),
                item("B", contentRevision = 2),
                item("A", contentRevision = 2),
            ),
            supportsKeyedDiff = true,
            includeSemanticChanges = false,
        )
        var changedCount = 0
        val structural = result as LazyListAdapterUpdatePlan.StructuralDiff

        structural.result.dispatchUpdatesTo(
            object : androidx.recyclerview.widget.ListUpdateCallback {
                override fun onInserted(position: Int, count: Int) = Unit

                override fun onRemoved(position: Int, count: Int) = Unit

                override fun onMoved(fromPosition: Int, toPosition: Int) = Unit

                override fun onChanged(position: Int, count: Int, payload: Any?) {
                    changedCount += count
                }
            },
        )

        assertEquals(0, changedCount)
    }

    private fun expectedChangedRanges(changedPositions: BooleanArray): List<LazyListChangedRange> {
        val ranges = mutableListOf<LazyListChangedRange>()
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
            ranges += LazyListChangedRange(
                positionStart = start,
                itemCount = position - start,
            )
        }
        return ranges
    }

    private fun item(
        key: String,
        contentRevision: Any? = key,
        environmentRevision: Any? = null,
        contentType: Any? = null,
        kind: LazyListItemKind = LazyListItemKind.Item,
        span: com.viewcompose.ui.node.policy.GridItemSpan = com.viewcompose.ui.node.policy.GridItemSpan.Single,
    ): LazyListItem {
        return LazyListItem(
            key = key,
            contentRevision = contentRevision,
            environmentRevision = environmentRevision,
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
}
