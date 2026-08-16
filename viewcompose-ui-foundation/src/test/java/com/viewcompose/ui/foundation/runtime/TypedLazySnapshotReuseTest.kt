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

class TypedLazySnapshotReuseTest {
    @Test
    fun `equal explicit snapshot revision skips every selector for one thousand items`() {
        val composer = ComposerLite()
        val rows = rows(revision = 0)
        val calls = SelectorCalls()

        val first = composer.commitRows(rows, snapshotRevision = 0, calls = calls)
        val second = composer.commitRows(rows, snapshotRevision = 0, calls = calls)

        assertEquals(ROW_COUNT, calls.key)
        assertEquals(ROW_COUNT, calls.contentType)
        assertEquals(ROW_COUNT, calls.contentRevision)
        assertSame(first, second)
        assertSame(first.first().sessionFactory, second.first().sessionFactory)
    }

    @Test
    fun `row and grid typed overloads route snapshot revision to the complete cache`() {
        val rows = rows(revision = 0)
        val rowComposer = ComposerLite()
        var rowKeyCalls = 0

        repeat(2) {
            rowComposer.commitTree {
                LazyRow(
                    items = rows,
                    key = { row ->
                        rowKeyCalls += 1
                        row.id
                    },
                    snapshotRevision = 0,
                ) { row ->
                    Text(row.id.toString())
                }
            }
        }

        val gridComposer = ComposerLite()
        var gridSpanCalls = 0
        repeat(2) {
            gridComposer.commitTree {
                LazyVerticalGrid(
                    items = rows,
                    key = Row::id,
                    span = { row ->
                        gridSpanCalls += 1
                        if (row.id == 0) GridItemSpan.FullLine else GridItemSpan.Single
                    },
                    snapshotRevision = 0,
                ) { row ->
                    Text(row.id.toString())
                }
            }
        }

        assertEquals(ROW_COUNT, rowKeyCalls)
        assertEquals(ROW_COUNT, gridSpanCalls)
    }

    @Test
    fun `scrollable scope typed wrappers forward snapshot revision`() {
        val rows = rows(revision = 0)

        fun assertWrapperSkipsSecondPass(
            content: ScrollableScope.(onSelector: () -> Unit) -> Unit,
        ) {
            val composer = ComposerLite()
            var selectorCalls = 0
            repeat(2) {
                composer.commitTree {
                    PullToRefresh(isRefreshing = false, onRefresh = {}) {
                        content { selectorCalls += 1 }
                    }
                }
            }
            assertEquals(ROW_COUNT, selectorCalls)
        }

        assertWrapperSkipsSecondPass { onSelector ->
            LazyColumn(
                items = rows,
                key = { row ->
                    onSelector()
                    row.id
                },
                snapshotRevision = 0,
            ) { row -> Text(row.id.toString()) }
        }
        assertWrapperSkipsSecondPass { onSelector ->
            LazyRow(
                items = rows,
                key = { row ->
                    onSelector()
                    row.id
                },
                snapshotRevision = 0,
            ) { row -> Text(row.id.toString()) }
        }
        assertWrapperSkipsSecondPass { onSelector ->
            LazyVerticalGrid(
                items = rows,
                key = Row::id,
                span = { row ->
                    onSelector()
                    if (row.id == 0) GridItemSpan.FullLine else GridItemSpan.Single
                },
                snapshotRevision = 0,
            ) { row -> Text(row.id.toString()) }
        }
    }

    @Test
    fun `single scoped typed declaration reuses its exact snapshot`() {
        val composer = ComposerLite()
        val rows = rows(revision = 0)
        var keyCalls = 0

        fun compose(): List<LazyListItem> {
            return composer.commitTree {
                LazyColumn {
                    items(
                        items = rows,
                        key = { row ->
                            keyCalls += 1
                            row.id
                        },
                        snapshotRevision = 0,
                    ) { row ->
                        Text(row.id.toString())
                    }
                }
            }.lazyItems()
        }

        val first = compose()
        val second = compose()

        assertEquals(ROW_COUNT, keyCalls)
        assertSame(first, second)
    }

    @Test
    fun `scoped typed declarations require distinct namespaced snapshot revisions`() {
        val composer = ComposerLite()

        assertThrows(IllegalArgumentException::class.java) {
            composer.prepareTree {
                LazyColumn {
                    items(
                        items = listOf(Row(id = 1, revision = 0)),
                        key = Row::id,
                        snapshotRevision = 0,
                    ) { row -> Text(row.id.toString()) }
                    runCatching {
                        items(
                            items = listOf(Row(id = 2, revision = 0)),
                            key = Row::id,
                            snapshotRevision = 0,
                        ) { row -> Text(row.id.toString()) }
                    }
                }
            }
        }
    }

    @Test
    fun `namespaced scoped snapshots remain independent across declaration order changes`() {
        val composer = ComposerLite()
        var keyCalls = 0

        fun compose(reverse: Boolean): List<Any> {
            return composer.commitTree {
                LazyColumn {
                    val declarations = if (reverse) listOf("second", "first") else listOf("first", "second")
                    declarations.forEach { declaration ->
                        val id = if (declaration == "first") 1 else 2
                        items(
                            items = listOf(Row(id = id, revision = 0)),
                            key = { row ->
                                keyCalls += 1
                                row.id
                            },
                            snapshotRevision = declaration to 0,
                        ) { row -> Text(row.id.toString()) }
                    }
                }
            }.lazyItems().map(LazyListItem::key)
        }

        assertEquals(listOf(1, 2), compose(reverse = false))
        assertEquals(listOf(2, 1), compose(reverse = true))
        assertEquals(2, keyCalls)
    }

    @Test
    fun `changed snapshot revision rebuilds the typed declaration`() {
        val composer = ComposerLite()
        val rows = rows(revision = 0)
        val calls = SelectorCalls()

        val first = composer.commitRows(rows, snapshotRevision = 0, calls = calls)
        val second = composer.commitRows(rows, snapshotRevision = 1, calls = calls)

        assertEquals(ROW_COUNT * 2, calls.key)
        assertEquals(ROW_COUNT * 2, calls.contentType)
        assertEquals(ROW_COUNT * 2, calls.contentRevision)
        assertSame(first.first(), second.first())
    }

    @Test
    fun `host type change cannot reuse a typed snapshot from another collection`() {
        val composer = ComposerLite()
        val rows = rows(revision = 0)
        var keyCalls = 0
        var spanCalls = 0

        composer.commitTree {
            LazyColumn(
                items = rows,
                key = { row ->
                    keyCalls += 1
                    row.id
                },
                snapshotRevision = 0,
            ) { row -> Text(row.id.toString()) }
        }
        composer.commitTree {
            LazyVerticalGrid(
                items = rows,
                key = { row ->
                    keyCalls += 1
                    row.id
                },
                span = { row ->
                    spanCalls += 1
                    if (row.id == 0) GridItemSpan.FullLine else GridItemSpan.Single
                },
                snapshotRevision = 0,
            ) { row -> Text(row.id.toString()) }
        }

        assertEquals(ROW_COUNT * 2, keyCalls)
        assertEquals(ROW_COUNT, spanCalls)
    }

    @Test
    fun `snapshot change reevaluates capture revision while unchanged items stay canonical`() {
        val composer = ComposerLite()

        fun compose(snapshotRevision: Int, labelRevision: Int): List<LazyListItem> {
            return composer.commitTree {
                LazyColumn(
                    items = listOf(Row(id = 1, revision = labelRevision)),
                    key = Row::id,
                    contentRevision = Row::revision,
                    snapshotRevision = snapshotRevision,
                ) { row -> Text("label-$labelRevision-${row.id}") }
            }.lazyItems()
        }

        val first = compose(snapshotRevision = 0, labelRevision = 0)
        val changedCapture = compose(snapshotRevision = 1, labelRevision = 1)
        val structuralOnly = compose(snapshotRevision = 2, labelRevision = 1)

        assertNotSame(first.single(), changedCapture.single())
        assertSame(changedCapture.single(), structuralOnly.single())
    }

    @Test
    fun `environment revision invalidates a complete typed snapshot`() {
        val composer = ComposerLite()
        val rows = rows(revision = 0)
        val calls = SelectorCalls()
        val environmentLocal = LocalValue { "default" }

        val first = composer.commitRows(
            rows = rows,
            snapshotRevision = 0,
            calls = calls,
            environmentLocal = environmentLocal,
            environment = "first",
        )
        val second = composer.commitRows(
            rows = rows,
            snapshotRevision = 0,
            calls = calls,
            environmentLocal = environmentLocal,
            environment = "second",
        )

        assertEquals(ROW_COUNT * 2, calls.key)
        assertNotSame(first, second)
        assertNotSame(first.first(), second.first())
    }

    @Test
    fun `null snapshot revision always reevaluates selectors`() {
        val composer = ComposerLite()
        val rows = rows(revision = 0)
        val calls = SelectorCalls()

        val first = composer.commitRows(rows, snapshotRevision = null, calls = calls)
        val second = composer.commitRows(rows, snapshotRevision = null, calls = calls)

        assertEquals(ROW_COUNT * 2, calls.key)
        assertEquals(ROW_COUNT * 2, calls.contentType)
        assertEquals(ROW_COUNT * 2, calls.contentRevision)
        assertSame(first.first(), second.first())
    }

    @Test
    fun `two committed snapshot revisions are reused across reset`() {
        val composer = ComposerLite()
        val calls = SelectorCalls()
        val rows0 = rows(revision = 0)
        val rows1 = rows(revision = 1)

        val first0 = composer.commitRows(rows0, snapshotRevision = 0, calls = calls)
        val first1 = composer.commitRows(rows1, snapshotRevision = 1, calls = calls)
        val reset0 = composer.commitRows(rows0, snapshotRevision = 0, calls = calls)
        val reset1 = composer.commitRows(rows1, snapshotRevision = 1, calls = calls)

        assertEquals(ROW_COUNT * 2, calls.key)
        assertSame(first0, reset0)
        assertSame(first1, reset1)
    }

    @Test
    fun `third committed snapshot evicts the least recently used revision`() {
        val composer = ComposerLite()
        val calls = SelectorCalls()
        val first = composer.commitRows(rows(0), snapshotRevision = 0, calls = calls)

        composer.commitRows(rows(1), snapshotRevision = 1, calls = calls)
        composer.commitRows(rows(2), snapshotRevision = 2, calls = calls)
        val rebuilt = composer.commitRows(rows(0), snapshotRevision = 0, calls = calls)

        assertEquals(ROW_COUNT * 4, calls.key)
        assertNotSame(first, rebuilt)
    }

    @Test
    fun `direct snapshot commit never iterates the previous item map`() {
        val cache = LazyItemSnapshotReuseCache()
        val retainedKeys = emptySet<Any>()

        fun snapshot(revision: Int): TypedLazyItemSnapshot {
            return TypedLazyItemSnapshot(
                snapshotRevision = revision,
                environmentRevision = "environment",
                items = emptyList(),
                itemsByKey = NoIterationMap(emptyMap()),
                retainedKeys = retainedKeys,
            )
        }

        cache.commitDirectTypedSnapshot(snapshot(0))
        cache.commitDirectTypedSnapshot(snapshot(1))
    }

    @Test
    fun `direct snapshot commit reports saveable key membership by canonical identity`() {
        val cache = LazyItemSnapshotReuseCache()

        fun snapshot(revision: Int, keys: Set<Any>): TypedLazyItemSnapshot {
            val retainedKeys = cache.canonicalizeKeySet(keys)
            return TypedLazyItemSnapshot(
                snapshotRevision = revision,
                environmentRevision = "environment",
                items = emptyList(),
                itemsByKey = emptyMap(),
                retainedKeys = retainedKeys,
            )
        }

        val firstA = snapshot(revision = 0, keys = setOf("a", "b"))
        assertEquals(true, cache.commitDirectTypedSnapshot(firstA))

        val secondA = snapshot(revision = 1, keys = linkedSetOf("b", "a"))
        assertSame(firstA.retainedKeys, secondA.retainedKeys)
        assertEquals(false, cache.commitDirectTypedSnapshot(secondA))

        val different = snapshot(revision = 2, keys = setOf("c"))
        assertEquals(true, cache.commitDirectTypedSnapshot(different))

        val resetA = snapshot(revision = 3, keys = setOf("a", "b"))
        assertSame(firstA.retainedKeys, resetA.retainedKeys)
        assertEquals(true, cache.commitDirectTypedSnapshot(resetA))
    }

    @Test
    fun `aborted composition does not publish a typed snapshot`() {
        val composer = ComposerLite()
        val calls = SelectorCalls()
        val prepared = composer.prepareRows(
            rows = rows(1),
            snapshotRevision = 1,
            calls = calls,
        )
        val aborted = prepared.value.lazyItems()

        prepared.abort()
        val committed = composer.commitRows(rows(1), snapshotRevision = 1, calls = calls)

        assertEquals(ROW_COUNT * 2, calls.key)
        assertNotSame(aborted, committed)
    }

    @Test
    fun `duplicate typed snapshot failure cannot advance the cache`() {
        val composer = ComposerLite()
        val calls = SelectorCalls()
        val duplicates = listOf(
            Row(id = 1, revision = 1),
            Row(id = 1, revision = 2),
        )

        assertThrows(IllegalArgumentException::class.java) {
            composer.prepareRows(
                rows = duplicates,
                snapshotRevision = 1,
                calls = calls,
            )
        }
        val beforeValidPass = calls.key
        val committed = composer.commitRows(
            rows = listOf(Row(id = 1, revision = 1), Row(id = 2, revision = 1)),
            snapshotRevision = 1,
            calls = calls,
        )

        assertEquals(2, committed.size)
        assertEquals(beforeValidPass + 2, calls.key)
    }

    @Test
    fun `caught duplicate item keeps the first declaration committed and reusable`() {
        val composer = ComposerLite()

        fun compose(): LazyListItem {
            return composer.commitTree {
                LazyColumn {
                    item(key = "row", contentRevision = 0) { Text("first") }
                    runCatching {
                        item(key = "row", contentRevision = 1) { Text("rejected") }
                    }
                }
            }.lazyItems().single()
        }

        val first = compose()
        val second = compose()

        assertEquals(0, first.contentRevision)
        assertSame(first, second)
    }

    private fun ComposerLite.commitRows(
        rows: List<Row>,
        snapshotRevision: Any?,
        calls: SelectorCalls,
        environmentLocal: LocalValue<String>? = null,
        environment: String = "default",
    ): List<LazyListItem> {
        val prepared = prepareRows(
            rows = rows,
            snapshotRevision = snapshotRevision,
            calls = calls,
            environmentLocal = environmentLocal,
            environment = environment,
        )
        prepared.commit()
        commitSideEffects()
        return prepared.value.lazyItems()
    }

    private fun ComposerLite.prepareRows(
        rows: List<Row>,
        snapshotRevision: Any?,
        calls: SelectorCalls,
        environmentLocal: LocalValue<String>? = null,
        environment: String = "default",
    ): ComposerLite.PreparedComposition<VNode> {
        return prepareTree {
            val content: UiTreeBuilder.() -> Unit = {
                LazyColumn(
                    items = rows,
                    key = { row ->
                        calls.key += 1
                        row.id
                    },
                    contentType = { row ->
                        calls.contentType += 1
                        row.id % 2
                    },
                    contentRevision = { row ->
                        calls.contentRevision += 1
                        row.revision
                    },
                    snapshotRevision = snapshotRevision,
                ) { row ->
                    Text("${row.id}:${row.revision}")
                }
            }
            if (environmentLocal == null) {
                content()
            } else {
                LocalContext.provide(environmentLocal, environment) {
                    content()
                }
            }
        }
    }

    private fun ComposerLite.commitTree(content: UiTreeBuilder.() -> Unit): VNode {
        val prepared = prepareTree(content)
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
        return (spec as LazyColumnNodeProps).items
    }

    private fun rows(revision: Int): List<Row> {
        return List(ROW_COUNT) { index ->
            Row(id = index, revision = revision)
        }
    }

    private data class Row(
        val id: Int,
        val revision: Int,
    )

    private class SelectorCalls {
        var key: Int = 0
        var contentType: Int = 0
        var contentRevision: Int = 0
    }

    private class NoIterationMap(
        private val delegate: Map<Any, LazyListItem>,
    ) : Map<Any, LazyListItem> by delegate {
        override val entries: Set<Map.Entry<Any, LazyListItem>>
            get() = error("Direct snapshot commit must not iterate entries")

        override val keys: Set<Any>
            get() = error("Direct snapshot commit must not iterate keys")

        override val values: Collection<LazyListItem>
            get() = error("Direct snapshot commit must not iterate values")
    }

    private companion object {
        private const val ROW_COUNT = 1_000
    }
}
