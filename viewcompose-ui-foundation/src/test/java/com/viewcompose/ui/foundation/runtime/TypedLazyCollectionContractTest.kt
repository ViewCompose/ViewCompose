package com.viewcompose.ui.foundation

import com.viewcompose.runtime.composition.ComposerLite
import com.viewcompose.ui.node.LazyListItem
import com.viewcompose.ui.node.VNode
import com.viewcompose.ui.node.policy.GridItemSpan
import com.viewcompose.ui.node.spec.LazyColumnNodeProps
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Test

class TypedLazyCollectionContractTest {
    @Test
    fun `bulk selector may explicitly preserve a null content revision`() {
        val item = ComposerLite().commitTree {
            LazyColumn(
                items = listOf(Row(id = 1, revision = 0)),
                key = Row::id,
                contentRevision = { null },
            ) { row -> Text(row.id.toString()) }
        }.lazyItems().single()

        assertNull(item.contentRevision)
    }

    @Test
    fun `ordinary list evaluates every selector while reusing canonical items`() {
        val composer = ComposerLite()
        val rows = rows(revision = 0)
        val calls = SelectorCalls()

        val first = composer.commitRows(rows, calls)
        val second = composer.commitRows(rows, calls)

        assertEquals(ROW_COUNT * 2, calls.key)
        assertEquals(ROW_COUNT * 2, calls.contentType)
        assertEquals(ROW_COUNT * 2, calls.contentRevision)
        assertSame(first, second)
        val declarationStrategy = first.first().sessionStrategy
        first.indices.forEach { index ->
            assertSame(first[index], second[index])
            assertSame(declarationStrategy, first[index].sessionStrategy)
            assertSame(rows[index], first[index].sessionPayload)
        }
    }

    @Test
    fun `default batch revision replaces only changed immutable models`() {
        val composer = ComposerLite()
        var rows = listOf(
            ImmutableRow(id = 1, label = "first"),
            ImmutableRow(id = 2, label = "stable"),
        )

        fun compose(): List<LazyListItem> {
            return composer.commitTree {
                LazyColumn(
                    items = rows,
                    key = ImmutableRow::id,
                ) { row -> Text(row.label) }
            }.lazyItems()
        }

        val first = compose()
        rows = listOf(
            ImmutableRow(id = 1, label = "changed"),
            ImmutableRow(id = 2, label = "stable"),
        )
        val changed = compose()

        assertNotSame(first[0], changed[0])
        assertNotSame(first[0].sessionStrategy, changed[0].sessionStrategy)
        assertSame(first[1], changed[1])
        assertSame(first[1].sessionStrategy, changed[1].sessionStrategy)
    }

    @Test
    fun `row grid and scrollable wrappers evaluate typed selectors on every pass`() {
        val rows = rows(revision = 0)
        val topLevelComposer = ComposerLite()
        var rowKeyCalls = 0
        var gridSpanCalls = 0

        repeat(2) {
            topLevelComposer.commitForest {
                LazyRow(
                    items = rows,
                    key = { row ->
                        rowKeyCalls += 1
                        row.id
                    },
                ) { row -> Text(row.id.toString()) }
                LazyVerticalGrid(
                    items = rows,
                    key = Row::id,
                    span = { row ->
                        gridSpanCalls += 1
                        if (row.id == 0) GridItemSpan.FullLine else GridItemSpan.Single
                    },
                ) { row -> Text(row.id.toString()) }
            }
        }

        assertEquals(ROW_COUNT * 2, rowKeyCalls)
        assertEquals(ROW_COUNT * 2, gridSpanCalls)

        val nestedComposer = ComposerLite()
        var columnKeyCalls = 0
        var nestedRowKeyCalls = 0
        var nestedGridSpanCalls = 0
        repeat(2) {
            nestedComposer.commitTree {
                PullToRefresh(isRefreshing = false, onRefresh = {}) {
                    LazyColumn(
                        items = rows,
                        key = { row ->
                            columnKeyCalls += 1
                            row.id
                        },
                    ) { row -> Text(row.id.toString()) }
                    LazyRow(
                        items = rows,
                        key = { row ->
                            nestedRowKeyCalls += 1
                            row.id
                        },
                    ) { row -> Text(row.id.toString()) }
                    LazyVerticalGrid(
                        items = rows,
                        key = Row::id,
                        span = { row ->
                            nestedGridSpanCalls += 1
                            if (row.id == 0) GridItemSpan.FullLine else GridItemSpan.Single
                        },
                    ) { row -> Text(row.id.toString()) }
                }
            }
        }

        assertEquals(ROW_COUNT * 2, columnKeyCalls)
        assertEquals(ROW_COUNT * 2, nestedRowKeyCalls)
        assertEquals(ROW_COUNT * 2, nestedGridSpanCalls)
    }

    @Test
    fun `scoped typed declarations evaluate independently without aggregate namespaces`() {
        val composer = ComposerLite()
        val firstGroup = listOf(Row(id = 1, revision = 0), Row(id = 2, revision = 0))
        val secondGroup = listOf(Row(id = 3, revision = 0), Row(id = 4, revision = 0))
        var firstKeyCalls = 0
        var secondKeyCalls = 0

        fun compose(): List<LazyListItem> {
            return composer.commitTree {
                LazyColumn {
                    items(
                        items = firstGroup,
                        key = { row ->
                            firstKeyCalls += 1
                            row.id
                        },
                    ) { row -> Text("first:${row.id}") }
                    items(
                        items = secondGroup,
                        key = { row ->
                            secondKeyCalls += 1
                            row.id
                        },
                    ) { row -> Text("second:${row.id}") }
                }
            }.lazyItems()
        }

        val first = compose()
        val second = compose()

        assertEquals(firstGroup.size * 2, firstKeyCalls)
        assertEquals(secondGroup.size * 2, secondKeyCalls)
        assertEquals(listOf(1, 2, 3, 4), second.map(LazyListItem::key))
        first.indices.forEach { index ->
            assertSame(first[index], second[index])
            assertSame(first[index].sessionStrategy, second[index].sessionStrategy)
        }
    }

    @Test
    fun `selector capture changes remain visible for the same list instance`() {
        val composer = ComposerLite()
        val rows = listOf(Row(id = 1, revision = 0))
        var type = "compact"

        fun compose(): LazyListItem {
            return composer.commitTree {
                LazyColumn(
                    items = rows,
                    key = Row::id,
                    contentType = { type },
                    contentRevision = Row::revision,
                ) { row -> Text(row.id.toString()) }
            }.lazyItems().single()
        }

        val first = compose()
        type = "expanded"
        val changed = compose()

        assertEquals("compact", first.contentType)
        assertEquals("expanded", changed.contentType)
        assertNotSame(first, changed)
        assertNotSame(first.sessionStrategy, changed.sessionStrategy)
    }

    @Test
    fun `changed capture in content revision replaces only affected item binding`() {
        val composer = ComposerLite()
        val rows = listOf(
            Row(id = 1, revision = 0),
            Row(id = 2, revision = 0),
        )
        var labelRevision = 0

        fun compose(): List<LazyListItem> {
            return composer.commitTree {
                LazyColumn(
                    items = rows,
                    key = Row::id,
                    contentRevision = { row ->
                        if (row.id == 1) row.revision to labelRevision else row.revision
                    },
                ) { row ->
                    val label = if (row.id == 1) labelRevision.toString() else "stable"
                    Text("${row.id}:$label")
                }
            }.lazyItems()
        }

        val first = compose()
        labelRevision = 1
        val changed = compose()
        val stable = compose()

        assertNotSame(first[0], changed[0])
        assertNotSame(first[0].sessionStrategy, changed[0].sessionStrategy)
        assertSame(first[1], changed[1])
        assertSame(first[1].sessionStrategy, changed[1].sessionStrategy)
        assertSame(changed[0], stable[0])
        assertSame(changed[0].sessionStrategy, stable[0].sessionStrategy)
    }

    @Test
    fun `reorder reuses same revisions and reset recovers previous item variant`() {
        val composer = ComposerLite()
        val firstRows = listOf(
            NamedRow(id = "A", revision = 1),
            NamedRow(id = "B", revision = 1),
            NamedRow(id = "C", revision = 1),
        )
        val changedRows = listOf(
            NamedRow(id = "C", revision = 1),
            NamedRow(id = "A", revision = 2),
            NamedRow(id = "B", revision = 1),
        )

        fun compose(rows: List<NamedRow>): Map<String, LazyListItem> {
            return composer.commitTree {
                LazyColumn(
                    items = rows,
                    key = NamedRow::id,
                    contentType = { "row" },
                    contentRevision = NamedRow::revision,
                ) { row -> Text("${row.id}:${row.revision}") }
            }.lazyItems().associateBy { item -> item.key as String }
        }

        val first = compose(firstRows)
        val changed = compose(changedRows)
        val reset = compose(firstRows)

        assertSame(first.getValue("B"), changed.getValue("B"))
        assertSame(first.getValue("C"), changed.getValue("C"))
        assertNotSame(first.getValue("A"), changed.getValue("A"))
        assertNotSame(
            first.getValue("A").sessionStrategy,
            changed.getValue("A").sessionStrategy,
        )
        assertSame(first.getValue("A"), reset.getValue("A"))
        assertSame(first.getValue("A").sessionStrategy, reset.getValue("A").sessionStrategy)
    }

    @Test
    fun `queued commits recover from a stale membership baseline`() {
        val composer = ComposerLite()
        val calls = SelectorCalls()
        val originalRows = listOf(Row(id = 1, revision = 0))
        val expandedRows = listOf(
            Row(id = 1, revision = 0),
            Row(id = 2, revision = 0),
        )
        val original = composer.commitRows(originalRows, calls)

        val expandedPrepared = composer.prepareRows(expandedRows, calls)
        expandedPrepared.commit()
        val expanded = expandedPrepared.value.lazyItems()
        val restoredPrepared = composer.prepareRows(originalRows, calls)
        restoredPrepared.commit()
        val restored = restoredPrepared.value.lazyItems()

        composer.commitSideEffects()
        val settled = composer.commitRows(originalRows, calls)
        val expandedAgain = composer.commitRows(expandedRows, calls)

        assertEquals(7, calls.key)
        assertEquals(7, calls.contentType)
        assertEquals(7, calls.contentRevision)
        assertSame(original, restored)
        assertSame(original, settled)
        assertSame(original.single(), expanded.first())
        assertSame(original.single(), restored.single())
        assertSame(original.single(), expandedAgain.first())
        assertSame(original.single().sessionStrategy, expandedAgain.first().sessionStrategy)
        assertNotSame(expanded[1], expandedAgain[1])
        assertNotSame(expanded[1].sessionStrategy, expandedAgain[1].sessionStrategy)
        assertEquals(listOf(1, 2), expandedAgain.map(LazyListItem::key))
    }

    @Test
    fun `environment change replaces binding while equal environment remains canonical`() {
        val composer = ComposerLite()
        val rows = listOf(Row(id = 1, revision = 0))
        val environmentLocal = LocalValue { "default" }

        fun compose(environment: String): LazyListItem {
            return composer.commitTree {
                LocalContext.provide(environmentLocal, environment) {
                    LazyColumn(
                        items = rows,
                        key = Row::id,
                        contentRevision = Row::revision,
                    ) { row -> Text("${LocalContext.current(environmentLocal)}:${row.id}") }
                }
            }.lazyItems().single()
        }

        val first = compose("first")
        val changed = compose("second")
        val stable = compose("second")

        assertNotSame(first, changed)
        assertNotSame(first.sessionStrategy, changed.sessionStrategy)
        assertSame(changed, stable)
        assertSame(changed.sessionStrategy, stable.sessionStrategy)
    }

    @Test
    fun `duplicate typed keys fail without replacing the committed cache`() {
        val composer = ComposerLite()
        val validRows = listOf(
            Row(id = 1, revision = 0),
            Row(id = 2, revision = 0),
        )
        val duplicates = listOf(
            Row(id = 1, revision = 1),
            Row(id = 1, revision = 2),
        )
        val first = composer.commitRows(validRows, SelectorCalls())

        assertThrows(IllegalArgumentException::class.java) {
            composer.prepareRows(duplicates, SelectorCalls())
        }
        val recovered = composer.commitRows(validRows, SelectorCalls())

        first.indices.forEach { index ->
            assertSame(first[index], recovered[index])
            assertSame(first[index].sessionStrategy, recovered[index].sessionStrategy)
        }
    }

    @Test
    fun `aborted typed candidate never becomes a reusable item variant`() {
        val composer = ComposerLite()
        val first = composer.commitRows(
            rows = listOf(Row(id = 1, revision = 0)),
            calls = SelectorCalls(),
        ).single()
        val prepared = composer.prepareRows(
            rows = listOf(Row(id = 1, revision = 1)),
            calls = SelectorCalls(),
        )
        val aborted = prepared.value.lazyItems().single()

        prepared.abort()
        val later = composer.commitRows(
            rows = listOf(Row(id = 1, revision = 2)),
            calls = SelectorCalls(),
        ).single()
        val retried = composer.commitRows(
            rows = listOf(Row(id = 1, revision = 1)),
            calls = SelectorCalls(),
        ).single()

        assertNotSame(first, aborted)
        assertNotSame(aborted, later)
        assertNotSame(aborted, retried)
        assertNotSame(aborted.sessionStrategy, retried.sessionStrategy)
    }

    @Test
    fun `caught direct duplicate keeps first item canonical`() {
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
        assertSame(first.sessionStrategy, second.sessionStrategy)
    }

    @Test
    fun `static revision explicitly reuses item and sticky header bindings`() {
        val composer = ComposerLite()

        fun compose(): List<LazyListItem> {
            return composer.commitTree {
                LazyColumn {
                    stickyHeader(
                        key = "header",
                        contentRevision = StaticContentRevision,
                    ) { Text("Header") }
                    item(
                        key = "row",
                        contentRevision = StaticContentRevision,
                    ) { Text("Row") }
                }
            }.lazyItems()
        }

        val first = compose()
        val second = compose()

        assertSame(first, second)
        first.indices.forEach { index ->
            assertSame(first[index], second[index])
            assertSame(first[index].sessionStrategy, second[index].sessionStrategy)
        }
    }

    private fun ComposerLite.commitRows(
        rows: List<Row>,
        calls: SelectorCalls,
    ): List<LazyListItem> {
        val prepared = prepareRows(rows, calls)
        prepared.commit()
        commitSideEffects()
        return prepared.value.lazyItems()
    }

    private fun ComposerLite.prepareRows(
        rows: List<Row>,
        calls: SelectorCalls,
    ): ComposerLite.PreparedComposition<VNode> {
        return prepareTree {
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
            ) { row -> Text("${row.id}:${row.revision}") }
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

    private fun rows(revision: Int): List<Row> {
        return List(ROW_COUNT) { index ->
            Row(id = index, revision = revision)
        }
    }

    private data class Row(
        val id: Int,
        val revision: Int,
    )

    private data class NamedRow(
        val id: String,
        val revision: Int,
    )

    private data class ImmutableRow(
        val id: Int,
        val label: String,
    )

    private class SelectorCalls {
        var key: Int = 0
        var contentType: Int = 0
        var contentRevision: Int = 0
    }

    private companion object {
        private const val ROW_COUNT = 1_000
    }
}
