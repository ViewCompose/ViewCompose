package com.viewcompose.paging

import androidx.paging.PagingData
import com.viewcompose.ui.foundation.Text
import com.viewcompose.ui.foundation.buildVNodeTree
import com.viewcompose.ui.node.LazyItemTableUpdate
import com.viewcompose.ui.node.spec.LazyColumnNodeProps
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class PagingLazyColumnTest {
    @Test
    fun `bridge preserves keys and refreshes access routing across reordered generations`() = pagingTest {
        val pages = MutableSharedFlow<PagingData<Row>>(extraBufferCapacity = 2)
        val items = ViewComposePagingItems<Row>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            items.collectFrom(pages)
        }
        pages.emit(
            PagingData.from(
                listOf(
                    Row(id = 1, version = 1, label = "one"),
                    Row(id = 2, version = 1, label = "two"),
                ),
            ),
        )
        runCurrent()

        val first = items.lazySpec()
        assertEquals(listOf("row", "row"), first.items.map { item -> item.contentType })
        val firstKeyOne = first.items[0].key
        val firstKeyTwo = first.items[1].key
        val firstRevisionOne = first.items[0].contentRevision
        val firstRevisionTwo = first.items[1].contentRevision

        pages.emit(
            PagingData.from(
                listOf(
                    Row(id = 2, version = 2, label = "two updated"),
                    Row(id = 1, version = 1, label = "one"),
                ),
            ),
        )
        runCurrent()

        val second = items.lazySpec()
        assertEquals(firstKeyTwo, second.items[0].key)
        assertEquals(firstKeyOne, second.items[1].key)
        assertNotEquals(firstRevisionOne, second.items[1].contentRevision)
        assertNotEquals(firstRevisionTwo, second.items[0].contentRevision)
    }

    @Test
    fun `bridge rejects duplicate keys before publishing a candidate`() = pagingTest {
        val items = ViewComposePagingItems<Row>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            items.collectFrom(
                kotlinx.coroutines.flow.flowOf(
                    PagingData.from(
                        listOf(
                            Row(1, 1, "first"),
                            Row(1, 2, "duplicate"),
                        ),
                    ),
                ),
            )
        }
        runCurrent()

        val error = runCatching { items.lazySpec() }.exceptionOrNull()

        assertTrue(error is IllegalArgumentException)
        assertTrue(error?.message.orEmpty().contains("Duplicate key"))
    }

    @Test
    fun `loaded declaration revision changes reload an unchanged Paging presentation`() = pagingTest {
        val pages = MutableSharedFlow<PagingData<Row>>(extraBufferCapacity = 1)
        val items = ViewComposePagingItems<Row>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            items.collectFrom(pages)
        }
        pages.emit(PagingData.from(listOf(Row(id = 1, version = 1, label = "one"))))
        runCurrent()

        fun spec(declarationRevision: Int): LazyColumnNodeProps = buildVNodeTree {
            PagingLazyColumn(
                items = items,
                key = Row::id,
                contentRevision = { row -> row.version to declarationRevision },
            ) { row ->
                Text(row.label)
            }
        }.single().spec as LazyColumnNodeProps

        val first = spec(declarationRevision = 1)
        val second = spec(declarationRevision = 2)

        assertEquals(first.items[0].key, second.items[0].key)
        assertNotEquals(first.items[0].contentRevision, second.items[0].contentRevision)
        assertEquals(
            listOf(LazyItemTableUpdate.ReloadAll),
            second.items.updatesFrom(first.items),
        )
    }

    @Test
    fun `bridge requires placeholder content and compact inspection sends no access hints`() = pagingTest {
        val factory = ControlledSourceFactory()
        val items = ViewComposePagingItems<Int>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            items.collectFrom(factory.pager(placeholders = true).flow)
        }
        val source = factory.nextSource()
        runCurrent()
        source.nextRequest().completePage(
            data = listOf(2, 3),
            prevKey = null,
            nextKey = 4,
            itemsBefore = 2,
            itemsAfter = 2,
        )
        runCurrent()

        val missingPlaceholderError = runCatching {
            buildVNodeTree {
                PagingLazyColumn(items = items, key = { value -> value }) { value -> Text("$value") }
            }
        }.exceptionOrNull()

        assertTrue(missingPlaceholderError is IllegalStateException)
        assertTrue(missingPlaceholderError?.message.orEmpty().contains("requires placeholderContent"))

        val spec = buildVNodeTree {
            PagingLazyColumn(
                items = items,
                key = { value -> value },
                placeholderContentRevision = "placeholder-v1",
                placeholderContent = { index -> Text("placeholder-$index") },
            ) { value ->
                Text("$value")
            }
        }.single().spec as LazyColumnNodeProps

        assertEquals(6, spec.items.size)
        assertEquals(2, spec.items.indexOfKey(spec.items[2].key))
        assertEquals(3, spec.items.indexOfKey(spec.items[3].key))
        assertNotEquals(spec.items[0].key, spec.items[2].key)
        assertFalse(source.hasPendingRequest())
    }

    @Test
    fun `placeholder append publishes a bounded change range and replaces placeholder identity`() =
        pagingTest {
            val factory = ControlledSourceFactory()
            val items = ViewComposePagingItems<Int>()
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                items.collectFrom(factory.pager(placeholders = true).flow)
            }
            val source = factory.nextSource()
            runCurrent()
            source.nextRequest().completePage(
                data = listOf(2, 3),
                prevKey = null,
                nextKey = 4,
                itemsBefore = 2,
                itemsAfter = 2,
            )
            runCurrent()
            val first = items.placeholderSpec()
            val placeholderKey = first.items[4].key

            assertEquals(3, items[3])
            runCurrent()
            source.nextRequest().completePage(
                data = listOf(4, 5),
                prevKey = 2,
                nextKey = null,
                itemsBefore = 4,
                itemsAfter = 0,
            )
            runCurrent()
            val second = items.placeholderSpec()

            assertEquals(
                listOf(LazyItemTableUpdate.ChangeRange(index = 4, count = 2)),
                second.items.updatesFrom(first.items),
            )
            assertNotEquals(placeholderKey, second.items[4].key)
            assertEquals(4, second.items.indexOfKey(second.items[4].key))
            assertEquals(-1, second.items.indexOfKey(placeholderKey))
        }

    @Test
    fun `placeholder content revision invalidates content without replacing positional identity`() =
        pagingTest {
            val factory = ControlledSourceFactory()
            val items = ViewComposePagingItems<Int>()
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                items.collectFrom(factory.pager(placeholders = true).flow)
            }
            val source = factory.nextSource()
            runCurrent()
            source.nextRequest().completePage(
                data = listOf(2, 3),
                prevKey = null,
                nextKey = 4,
                itemsBefore = 2,
                itemsAfter = 2,
            )
            runCurrent()

            val first = items.placeholderSpec(placeholderContentRevision = "placeholder-v1")
            val second = items.placeholderSpec(placeholderContentRevision = "placeholder-v2")

            assertEquals(first.items[0].key, second.items[0].key)
            assertNotEquals(first.items[0].contentRevision, second.items[0].contentRevision)
            assertEquals(first.items[2].key, second.items[2].key)
            assertEquals(first.items[2].contentRevision, second.items[2].contentRevision)
            assertEquals(
                listOf(LazyItemTableUpdate.ReloadAll),
                second.items.updatesFrom(first.items),
            )
        }

    @Test
    fun `page drop removes loaded identity and skipped table revisions reload without enumeration`() =
        pagingTest {
            val factory = ControlledSourceFactory()
            val items = ViewComposePagingItems<Int>()
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                items.collectFrom(
                    factory.pager(
                        placeholders = true,
                        initialKey = 2,
                        maxSize = 4,
                    ).flow,
                )
            }
            val source = factory.nextSource()
            runCurrent()
            source.nextRequest().completePage(
                data = listOf(2, 3),
                prevKey = 0,
                nextKey = 4,
                itemsBefore = 2,
                itemsAfter = 4,
            )
            runCurrent()

            assertEquals(3, items[3])
            runCurrent()
            source.nextRequest().completePage(
                data = listOf(4, 5),
                prevKey = 2,
                nextKey = 6,
                itemsBefore = 4,
                itemsAfter = 2,
            )
            runCurrent()
            val beforeDrop = items.placeholderSpec()
            val keyTwo = beforeDrop.items[2].key
            val keyFour = beforeDrop.items[4].key
            assertEquals(2, beforeDrop.items.indexOfKey(keyTwo))

            assertEquals(5, items[5])
            runCurrent()
            source.nextRequest().completePage(
                data = listOf(6, 7),
                prevKey = 4,
                nextKey = null,
                itemsBefore = 6,
                itemsAfter = 0,
            )
            runCurrent()
            val afterDrop = items.placeholderSpec()

            assertEquals(8, afterDrop.items.size)
            assertEquals(-1, afterDrop.items.indexOfKey(keyTwo))
            assertEquals(4, afterDrop.items.indexOfKey(keyFour))
            assertEquals(6, afterDrop.items.indexOfKey(afterDrop.items[6].key))
            assertEquals(
                listOf(LazyItemTableUpdate.ReloadAll),
                afterDrop.items.updatesFrom(beforeDrop.items),
            )
            assertEquals(
                listOf(LazyItemTableUpdate.ChangeRange(index = 6, count = 2)),
                items.presentationForLazyColumn().itemUpdates,
            )
        }

    private fun ViewComposePagingItems<Row>.lazySpec(): LazyColumnNodeProps =
        buildVNodeTree {
            PagingLazyColumn(
                items = this@lazySpec,
                key = Row::id,
                contentType = { "row" },
                contentRevision = Row::version,
            ) { row ->
                Text(row.label)
            }
        }.single().spec as LazyColumnNodeProps

    private fun ViewComposePagingItems<Int>.placeholderSpec(
        placeholderContentRevision: Any = "placeholder-v1",
    ): LazyColumnNodeProps =
        buildVNodeTree {
            PagingLazyColumn(
                items = this@placeholderSpec,
                key = { value -> value },
                placeholderContentRevision = placeholderContentRevision,
                placeholderContent = { index -> Text("placeholder-$index") },
            ) { value ->
                Text("$value")
            }
        }.single().spec as LazyColumnNodeProps

    private fun pagingTest(
        block: suspend kotlinx.coroutines.test.TestScope.() -> Unit,
    ) = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        try {
            block()
        } finally {
            Dispatchers.resetMain()
        }
    }

    private data class Row(
        val id: Int,
        val version: Int,
        val label: String,
    )
}
