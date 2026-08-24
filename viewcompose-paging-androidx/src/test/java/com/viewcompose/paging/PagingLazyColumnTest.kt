package com.viewcompose.paging

import androidx.paging.PagingData
import com.viewcompose.ui.foundation.Text
import com.viewcompose.ui.foundation.buildVNodeTree
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
        assertEquals(listOf(1, 2), first.items.map { item -> item.key })
        assertEquals(listOf("row", "row"), first.items.map { item -> item.contentType })
        val firstRevisionByKey = first.items.associate { item -> item.key to item.contentRevision }

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
        assertEquals(listOf(2, 1), second.items.map { item -> item.key })
        val secondRevisionByKey = second.items.associate { item -> item.key to item.contentRevision }
        assertNotEquals(firstRevisionByKey.getValue(1), secondRevisionByKey.getValue(1))
        assertNotEquals(firstRevisionByKey.getValue(2), secondRevisionByKey.getValue(2))
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
    fun `bridge rejects unloaded slots and composition does not send access hints`() = pagingTest {
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

        val error = runCatching {
            buildVNodeTree {
                PagingLazyColumn(items = items, key = { value -> value }) { value -> Text("$value") }
            }
        }.exceptionOrNull()

        assertTrue(error is IllegalStateException)
        assertTrue(error?.message.orEmpty().contains("placeholders to be disabled"))
        assertFalse(source.hasPendingRequest())
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
