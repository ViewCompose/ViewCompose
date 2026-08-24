package com.viewcompose.ui.foundation

import com.viewcompose.runtime.composition.ComposerLite
import com.viewcompose.ui.node.LazyListItem
import com.viewcompose.ui.node.VNode
import com.viewcompose.ui.node.policy.GridItemSpan
import com.viewcompose.ui.node.spec.LazyColumnNodeProps
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Test

class LazyItemsSnapshotContractTest {
    @Test
    fun `snapshot freezes source membership and order while retaining element references`() {
        val first = Row(id = 1, version = 0, label = "first")
        val second = Row(id = 2, version = 0, label = "second")
        val source = mutableListOf(first, second)
        val snapshot = source.toLazyItemsSnapshot()

        source.reverse()
        source.removeAt(1)
        source += Row(id = 3, version = 0, label = "third")

        val items = ComposerLite().commitTree {
            LazyColumn(
                items = snapshot,
                key = Row::id,
            ) { row -> Text(row.label) }
        }.lazyItems()

        assertEquals(listOf(1, 2), items.map(LazyListItem::key))
        assertSame(first, items[0].contentRevision)
        assertSame(second, items[1].contentRevision)
    }

    @Test
    fun `same snapshot skips selectors and key traversal after commit`() {
        val composer = ComposerLite()
        val keys = List(ROW_COUNT) { index -> GuardedKey(index) }
        val rows = keys.map { key -> GuardedRow(key) }
        val snapshot = rows.toLazyItemsSnapshot()
        var keyCalls = 0

        fun compose(): List<LazyListItem> {
            return composer.commitTree {
                LazyColumn(
                    items = snapshot,
                    key = { row ->
                        keyCalls += 1
                        row.key
                    },
                ) { row -> Text(row.key.value.toString()) }
            }.lazyItems()
        }

        val first = compose()
        keys.forEach { key -> key.allowHashing = false }
        val second = compose()

        assertEquals(ROW_COUNT, keyCalls)
        assertSame(first, second)
        first.indices.forEach { index ->
            assertSame(first[index], second[index])
            assertSame(first[index].sessionStrategy, second[index].sessionStrategy)
        }
    }

    @Test
    fun `equal content in a different snapshot reevaluates selectors but keeps canonical items`() {
        val composer = ComposerLite()
        val rows = rows(version = 0)
        val firstSnapshot = rows.toLazyItemsSnapshot()
        val secondSnapshot = rows.toLazyItemsSnapshot()
        val calls = SelectorCalls()

        val first = composer.commitSnapshot(firstSnapshot, calls)
        val second = composer.commitSnapshot(secondSnapshot, calls)

        assertEquals(ROW_COUNT * 2, calls.key)
        assertEquals(ROW_COUNT * 2, calls.contentType)
        assertEquals(ROW_COUNT * 2, calls.contentRevision)
        assertSame(first, second)
        first.indices.forEach { index ->
            assertSame(first[index], second[index])
            assertSame(first[index].sessionStrategy, second[index].sessionStrategy)
        }
    }

    @Test
    fun `two committed snapshot identities retain independent exact hits`() {
        val composer = ComposerLite()
        val firstSnapshot = rows(version = 1).toLazyItemsSnapshot()
        val secondSnapshot = rows(version = 2).toLazyItemsSnapshot()
        val calls = SelectorCalls()

        val first = composer.commitSnapshot(firstSnapshot, calls)
        val second = composer.commitSnapshot(secondSnapshot, calls)
        val firstReset = composer.commitSnapshot(firstSnapshot, calls)
        val secondReset = composer.commitSnapshot(secondSnapshot, calls)

        assertEquals(ROW_COUNT * 2, calls.key)
        assertEquals(ROW_COUNT * 2, calls.contentType)
        assertEquals(ROW_COUNT * 2, calls.contentRevision)
        assertSame(first, firstReset)
        assertSame(second, secondReset)
        assertNotSame(first, second)
        assertSame(first.first().sessionStrategy, firstReset.first().sessionStrategy)
        assertSame(second.first().sessionStrategy, secondReset.first().sessionStrategy)
    }

    @Test
    fun `environment change reevaluates one snapshot and replaces its bindings`() {
        val composer = ComposerLite()
        val snapshot = rows(version = 0).toLazyItemsSnapshot()
        val calls = SelectorCalls()
        val environmentLocal = LocalValue { "default" }

        fun compose(environment: String): List<LazyListItem> {
            return composer.commitTree {
                LocalContext.provide(environmentLocal, environment) {
                    LazyColumn(
                        items = snapshot,
                        key = calls::key,
                        contentType = calls::contentType,
                        contentRevision = calls::contentRevision,
                    ) { row ->
                        Text("${LocalContext.current(environmentLocal)}:${row.id}")
                    }
                }
            }.lazyItems()
        }

        val first = compose("first")
        val changed = compose("second")
        val stable = compose("second")

        assertEquals(ROW_COUNT * 2, calls.key)
        assertEquals(ROW_COUNT * 2, calls.contentType)
        assertEquals(ROW_COUNT * 2, calls.contentRevision)
        assertNotSame(first, changed)
        assertNotSame(first.first().sessionStrategy, changed.first().sessionStrategy)
        assertSame(changed, stable)
        assertSame(changed.first().sessionStrategy, stable.first().sessionStrategy)
    }

    @Test
    fun `environment reset hits the other retained snapshot variant`() {
        val composer = ComposerLite()
        val snapshot = rows(version = 0).toLazyItemsSnapshot()
        val calls = SelectorCalls()
        val environmentLocal = LocalValue { "default" }

        fun compose(environment: String): List<LazyListItem> {
            return composer.commitTree {
                LocalContext.provide(environmentLocal, environment) {
                    LazyColumn(
                        items = snapshot,
                        key = calls::key,
                        contentType = calls::contentType,
                        contentRevision = calls::contentRevision,
                    ) { row ->
                        Text("${LocalContext.current(environmentLocal)}:${row.id}")
                    }
                }
            }.lazyItems()
        }

        val first = compose("first")
        val second = compose("second")
        val firstReset = compose("first")

        assertEquals(ROW_COUNT * 2, calls.key)
        assertEquals(ROW_COUNT * 2, calls.contentType)
        assertEquals(ROW_COUNT * 2, calls.contentRevision)
        assertNotSame(first, second)
        assertSame(first, firstReset)
        assertSame(first.first(), firstReset.first())
        assertSame(first.first().sessionStrategy, firstReset.first().sessionStrategy)
    }

    @Test
    fun `plain list submission clears whole snapshot reuse before snapshot returns`() {
        val composer = ComposerLite()
        val rows = rows(version = 0)
        val snapshot = rows.toLazyItemsSnapshot()
        val snapshotCalls = SelectorCalls()
        val plainCalls = SelectorCalls()
        val firstSnapshot = composer.commitSnapshot(snapshot, snapshotCalls)

        val plain = composer.commitTree {
            LazyColumn(
                items = rows,
                key = plainCalls::key,
                contentType = plainCalls::contentType,
                contentRevision = plainCalls::contentRevision,
            ) { row -> Text(row.label) }
        }.lazyItems()
        val returnedSnapshot = composer.commitSnapshot(snapshot, snapshotCalls)
        val callsAfterReturn = snapshotCalls.total()
        val cachedSnapshot = composer.commitSnapshot(snapshot, snapshotCalls)

        assertEquals(ROW_COUNT * 2, snapshotCalls.key)
        assertEquals(ROW_COUNT * 2, snapshotCalls.contentType)
        assertEquals(ROW_COUNT * 2, snapshotCalls.contentRevision)
        assertEquals(ROW_COUNT, plainCalls.key)
        assertEquals(ROW_COUNT, plainCalls.contentType)
        assertEquals(ROW_COUNT, plainCalls.contentRevision)
        assertEquals(callsAfterReturn, snapshotCalls.total())
        assertSame(firstSnapshot, plain)
        assertSame(firstSnapshot, returnedSnapshot)
        assertSame(returnedSnapshot, cachedSnapshot)
        assertSame(firstSnapshot.first(), returnedSnapshot.first())
        assertSame(firstSnapshot.first().sessionStrategy, returnedSnapshot.first().sessionStrategy)
    }

    @Test
    fun `selector failure does not publish an evaluated snapshot`() {
        val composer = ComposerLite()
        val snapshot = rows(version = 0).toLazyItemsSnapshot()
        var fail = true
        var keyCalls = 0

        assertThrows(IllegalStateException::class.java) {
            composer.prepareTree {
                LazyColumn(
                    items = snapshot,
                    key = { row ->
                        keyCalls += 1
                        if (fail && row.id == 1) error("selector failed")
                        row.id
                    },
                ) { row -> Text(row.label) }
            }
        }
        val callsAfterFailure = keyCalls

        fail = false
        val committed = composer.commitTree {
            LazyColumn(
                items = snapshot,
                key = { row ->
                    keyCalls += 1
                    row.id
                },
            ) { row -> Text(row.label) }
        }.lazyItems()
        val callsAfterCommit = keyCalls
        val cached = composer.commitTree {
            LazyColumn(
                items = snapshot,
                key = { row ->
                    keyCalls += 1
                    row.id
                },
            ) { row -> Text(row.label) }
        }.lazyItems()

        assertEquals(2, callsAfterFailure)
        assertEquals(callsAfterFailure + ROW_COUNT, callsAfterCommit)
        assertEquals(callsAfterCommit, keyCalls)
        assertSame(committed, cached)
    }

    @Test
    fun `duplicate failure does not publish an evaluated snapshot`() {
        val composer = ComposerLite()
        val snapshot = rows(version = 0).toLazyItemsSnapshot()
        var duplicate = true
        var keyCalls = 0

        assertThrows(IllegalArgumentException::class.java) {
            composer.prepareTree {
                LazyColumn(
                    items = snapshot,
                    key = { row ->
                        keyCalls += 1
                        if (duplicate) 0 else row.id
                    },
                ) { row -> Text(row.label) }
            }
        }
        val callsAfterFailure = keyCalls

        duplicate = false
        val committed = composer.commitTree {
            LazyColumn(
                items = snapshot,
                key = { row ->
                    keyCalls += 1
                    row.id
                },
            ) { row -> Text(row.label) }
        }.lazyItems()
        val callsAfterCommit = keyCalls
        val cached = composer.commitTree {
            LazyColumn(
                items = snapshot,
                key = { row ->
                    keyCalls += 1
                    row.id
                },
            ) { row -> Text(row.label) }
        }.lazyItems()

        assertEquals(2, callsAfterFailure)
        assertEquals(callsAfterFailure + ROW_COUNT, callsAfterCommit)
        assertEquals(callsAfterCommit, keyCalls)
        assertSame(committed, cached)
    }

    @Test
    fun `aborted prepared snapshot is evaluated again before its first commit`() {
        val composer = ComposerLite()
        val snapshot = rows(version = 1).toLazyItemsSnapshot()
        val calls = SelectorCalls()
        val prepared = composer.prepareSnapshot(snapshot, calls)
        val aborted = prepared.value.lazyItems()

        prepared.abort()
        val committed = composer.commitSnapshot(snapshot, calls)
        val callsAfterCommit = calls.total()
        val cached = composer.commitSnapshot(snapshot, calls)

        assertEquals(ROW_COUNT * 2, calls.key)
        assertEquals(ROW_COUNT * 2, calls.contentType)
        assertEquals(ROW_COUNT * 2, calls.contentRevision)
        assertEquals(callsAfterCommit, calls.total())
        assertNotSame(aborted, committed)
        assertNotSame(aborted.first(), committed.first())
        assertNotSame(aborted.first().sessionStrategy, committed.first().sessionStrategy)
        assertSame(committed, cached)
    }

    @Test
    fun `third committed snapshot evicts the oldest exact entry`() {
        val composer = ComposerLite()
        val firstSnapshot = rows(version = 1).toLazyItemsSnapshot()
        val secondSnapshot = rows(version = 2).toLazyItemsSnapshot()
        val thirdSnapshot = rows(version = 3).toLazyItemsSnapshot()
        val calls = SelectorCalls()

        val first = composer.commitSnapshot(firstSnapshot, calls)
        composer.commitSnapshot(secondSnapshot, calls)
        composer.commitSnapshot(thirdSnapshot, calls)
        val callsBeforeReset = calls.total()
        val rebuiltFirst = composer.commitSnapshot(firstSnapshot, calls)
        val callsAfterReset = calls.total()
        val cachedFirst = composer.commitSnapshot(firstSnapshot, calls)

        assertEquals(ROW_COUNT * 9, callsBeforeReset)
        assertEquals(ROW_COUNT * 12, callsAfterReset)
        assertEquals(callsAfterReset, calls.total())
        assertNotSame(first, rebuiltFirst)
        assertNotSame(first.first(), rebuiltFirst.first())
        assertNotSame(first.first().sessionStrategy, rebuiltFirst.first().sessionStrategy)
        assertSame(rebuiltFirst, cachedFirst)
    }

    @Test
    fun `queued stale side effects publish the last snapshot and preserve the other exact variant`() {
        val composer = ComposerLite()
        val firstSnapshot = rows(version = 1).toLazyItemsSnapshot()
        val secondSnapshot = rows(version = 2).toLazyItemsSnapshot()
        val calls = SelectorCalls()
        val first = composer.commitSnapshot(firstSnapshot, calls)

        val secondPrepared = composer.prepareSnapshot(secondSnapshot, calls)
        secondPrepared.commit()
        val second = secondPrepared.value.lazyItems()
        val firstPrepared = composer.prepareSnapshot(firstSnapshot, calls)
        firstPrepared.commit()
        val queuedLast = firstPrepared.value.lazyItems()

        composer.commitSideEffects()
        val callsAfterFlush = calls.total()
        val settledFirst = composer.commitSnapshot(firstSnapshot, calls)
        val restoredSecond = composer.commitSnapshot(secondSnapshot, calls)

        assertEquals(ROW_COUNT * 6, callsAfterFlush)
        assertEquals(callsAfterFlush, calls.total())
        assertSame(first, queuedLast)
        assertSame(first, settledFirst)
        assertSame(second, restoredSecond)
        assertNotSame(first.first(), second.first())
        assertSame(first.first().sessionStrategy, settledFirst.first().sessionStrategy)
        assertSame(second.first().sessionStrategy, restoredSecond.first().sessionStrategy)
    }

    @Test
    fun `membership exact resets do not retain an evicted absent key binding`() {
        val composer = ComposerLite()
        val expandedRows = listOf(
            Row(id = 1, version = 0, label = "one"),
            Row(id = 2, version = 0, label = "two"),
        )
        val compactRows = listOf(expandedRows.first())
        val expandedSnapshot = expandedRows.toLazyItemsSnapshot()
        val compactSnapshot = compactRows.toLazyItemsSnapshot()
        val calls = SelectorCalls()

        val expanded = composer.commitSnapshot(expandedSnapshot, calls)
        val compact = composer.commitSnapshot(compactSnapshot, calls)
        val expandedReset = composer.commitSnapshot(expandedSnapshot, calls)
        val compactReset = composer.commitSnapshot(compactSnapshot, calls)
        val replacementCompact = compactRows.toLazyItemsSnapshot()
        val compactReplacement = composer.commitSnapshot(replacementCompact, calls)
        val replacementExpanded = expandedRows.toLazyItemsSnapshot()
        val expandedReplacement = composer.commitSnapshot(replacementExpanded, calls)

        assertSame(expanded, expandedReset)
        assertSame(compact, compactReset)
        assertSame(compact.single(), compactReplacement.single())
        assertSame(expanded.first(), expandedReplacement.first())
        assertNotSame(expanded[1], expandedReplacement[1])
        assertNotSame(expanded[1].sessionStrategy, expandedReplacement[1].sessionStrategy)
        assertEquals(listOf(1, 2), expandedReplacement.map(LazyListItem::key))
    }

    @Test
    fun `column row grid and scrollable wrappers share the snapshot contract`() {
        val snapshot = rows(version = 0).toLazyItemsSnapshot()
        val topLevelComposer = ComposerLite()
        var columnCalls = 0
        var rowCalls = 0
        var gridCalls = 0

        repeat(2) {
            topLevelComposer.commitForest {
                LazyColumn(
                    items = snapshot,
                    key = { row ->
                        columnCalls += 1
                        row.id
                    },
                ) { row -> Text(row.label) }
                LazyRow(
                    items = snapshot,
                    key = { row ->
                        rowCalls += 1
                        row.id
                    },
                ) { row -> Text(row.label) }
                LazyVerticalGrid(
                    items = snapshot,
                    key = Row::id,
                    span = { row ->
                        gridCalls += 1
                        if (row.id == 0) GridItemSpan.FullLine else GridItemSpan.Single
                    },
                ) { row -> Text(row.label) }
            }
        }

        assertEquals(ROW_COUNT, columnCalls)
        assertEquals(ROW_COUNT, rowCalls)
        assertEquals(ROW_COUNT, gridCalls)

        val nestedComposer = ComposerLite()
        var nestedColumnCalls = 0
        var nestedRowCalls = 0
        var nestedGridCalls = 0
        repeat(2) {
            nestedComposer.commitTree {
                PullToRefresh(isRefreshing = false, onRefresh = {}) {
                    LazyColumn(
                        items = snapshot,
                        key = { row ->
                            nestedColumnCalls += 1
                            row.id
                        },
                    ) { row -> Text(row.label) }
                    LazyRow(
                        items = snapshot,
                        key = { row ->
                            nestedRowCalls += 1
                            row.id
                        },
                    ) { row -> Text(row.label) }
                    LazyVerticalGrid(
                        items = snapshot,
                        key = Row::id,
                        span = { row ->
                            nestedGridCalls += 1
                            if (row.id == 0) GridItemSpan.FullLine else GridItemSpan.Single
                        },
                    ) { row -> Text(row.label) }
                }
            }
        }

        assertEquals(ROW_COUNT, nestedColumnCalls)
        assertEquals(ROW_COUNT, nestedRowCalls)
        assertEquals(ROW_COUNT, nestedGridCalls)
    }

    private fun ComposerLite.commitSnapshot(
        snapshot: LazyItemsSnapshot<Row>,
        calls: SelectorCalls,
    ): List<LazyListItem> {
        return commitTree {
            LazyColumn(
                items = snapshot,
                key = calls::key,
                contentType = calls::contentType,
                contentRevision = calls::contentRevision,
            ) { row -> Text(row.label) }
        }.lazyItems()
    }

    private fun ComposerLite.prepareSnapshot(
        snapshot: LazyItemsSnapshot<Row>,
        calls: SelectorCalls,
    ): ComposerLite.PreparedComposition<VNode> {
        return prepareTree {
            LazyColumn(
                items = snapshot,
                key = calls::key,
                contentType = calls::contentType,
                contentRevision = calls::contentRevision,
            ) { row -> Text(row.label) }
        }
    }

    private fun ComposerLite.commitTree(content: UiTreeBuilder.() -> Unit): VNode {
        val prepared = prepareTree(content)
        prepared.commit()
        commitSideEffects()
        return prepared.value
    }

    private fun ComposerLite.commitForest(content: UiTreeBuilder.() -> Unit): List<VNode> {
        val prepared = ComposerContext.withComposer(this) {
            requestRootRecompose()
            prepareRoot {
                buildVNodeTree(content)
            }
        }
        prepared.commit()
        commitSideEffects()
        return prepared.value
    }

    private fun ComposerLite.prepareTree(
        content: UiTreeBuilder.() -> Unit,
    ): ComposerLite.PreparedComposition<VNode> {
        return ComposerContext.withComposer(this) {
            requestRootRecompose()
            prepareRoot {
                buildVNodeTree(content).single()
            }
        }
    }

    private fun VNode.lazyItems(): List<LazyListItem> {
        return (spec as LazyColumnNodeProps).items.toList()
    }

    private fun rows(version: Int): List<Row> {
        return List(ROW_COUNT) { index ->
            Row(id = index, version = version, label = "row-$index")
        }
    }

    private data class Row(
        val id: Int,
        val version: Int,
        val label: String,
    )

    private data class GuardedRow(
        val key: GuardedKey,
    )

    private class GuardedKey(
        val value: Int,
    ) {
        var allowHashing: Boolean = true

        override fun hashCode(): Int {
            check(allowHashing) { "A committed snapshot fast hit must not revisit item keys." }
            return value
        }

        override fun equals(other: Any?): Boolean {
            return other is GuardedKey && value == other.value
        }
    }

    private class SelectorCalls {
        var key: Int = 0
        var contentType: Int = 0
        var contentRevision: Int = 0

        fun key(row: Row): Any {
            key += 1
            return row.id
        }

        fun contentType(row: Row): Any {
            contentType += 1
            return row.id % 2
        }

        fun contentRevision(row: Row): Any {
            contentRevision += 1
            return row.version
        }

        fun total(): Int = key + contentType + contentRevision
    }

    private companion object {
        private const val ROW_COUNT = 3
    }
}
