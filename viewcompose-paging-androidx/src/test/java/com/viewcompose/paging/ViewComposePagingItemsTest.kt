package com.viewcompose.paging

import androidx.paging.LoadState
import androidx.paging.LoadStates
import androidx.paging.PagingData
import androidx.paging.PagingSource
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class ViewComposePagingItemsTest {
    @Test
    fun `initial presentation and completed refresh publish one coherent snapshot`() = pagingTest {
        val factory = ControlledSourceFactory()
        val items = ViewComposePagingItems<Int>()
        collect(factory, items)
        val source = factory.nextSource()
        runCurrent()

        assertEquals(0, items.itemCount)
        assertEquals(0, items.loadedItemCount)
        assertTrue(items.loadStates.refresh is LoadState.Loading)

        source.nextRequest().completePage(
            data = listOf(0, 1),
            prevKey = null,
            nextKey = null,
        )
        runCurrent()

        assertEquals(2, items.itemCount)
        assertEquals(2, items.loadedItemCount)
        assertEquals(listOf(0, 1), items.values())
        assertTrue(items.loadStates.refresh is LoadState.NotLoading)
        assertFalse(source.hasPendingRequest())
    }

    @Test
    fun `peek is non-triggering while active access drives prepend and append`() = pagingTest {
        val factory = ControlledSourceFactory()
        val items = ViewComposePagingItems<Int>()
        collect(factory, items, initialKey = 2)
        val source = factory.nextSource()
        runCurrent()
        source.nextRequest().completePage(
            data = listOf(2, 3),
            prevKey = 0,
            nextKey = 4,
        )
        runCurrent()

        assertEquals(2, items.peek(0))
        runCurrent()
        assertFalse(source.hasPendingRequest())

        assertEquals(2, items[0])
        runCurrent()
        val prepend = source.nextRequest()
        assertTrue(prepend.params is PagingSource.LoadParams.Prepend)
        prepend.completePage(
            data = listOf(0, 1),
            prevKey = null,
            nextKey = 2,
        )
        runCurrent()

        assertEquals(3, items[3])
        runCurrent()
        val append = source.nextRequest()
        assertTrue(append.params is PagingSource.LoadParams.Append)
        append.completePage(
            data = listOf(4, 5),
            prevKey = 2,
            nextKey = null,
        )
        runCurrent()

        assertEquals(listOf(0, 1, 2, 3, 4, 5), items.values())
    }

    @Test
    fun `retry preserves generation while refresh and invalidation replace it`() = pagingTest {
        val factory = ControlledSourceFactory()
        val items = ViewComposePagingItems<Int>()
        collect(factory, items)
        val firstSource = factory.nextSource()
        runCurrent()
        val firstRequest = firstSource.nextRequest()
        val failure = IOException("refresh failed")
        firstRequest.completeError(failure)
        runCurrent()

        val error = items.loadStates.refresh
        assertTrue(error is LoadState.Error)
        assertSame(failure, (error as LoadState.Error).error)

        items.retry()
        runCurrent()
        val retry = firstSource.nextRequest()
        assertTrue(retry.params is PagingSource.LoadParams.Refresh)
        assertEquals(1, factory.createdSources.size)
        retry.completePage(listOf(10, 11), prevKey = null, nextKey = null)
        runCurrent()

        items.refresh()
        runCurrent()
        val refreshSource = factory.nextSource()
        runCurrent()
        refreshSource.nextRequest().completePage(listOf(20, 21), prevKey = null, nextKey = null)
        runCurrent()
        assertEquals(listOf(20, 21), items.values())

        refreshSource.invalidate()
        runCurrent()
        val invalidatedSource = factory.nextSource()
        runCurrent()
        invalidatedSource.nextRequest().completePage(
            listOf(30, 31),
            prevKey = null,
            nextKey = null,
        )
        runCurrent()

        assertEquals(3, factory.createdSources.size)
        assertEquals(listOf(30, 31), items.values())
    }

    @Test
    fun `latest paging data emission replaces an earlier query presentation`() = pagingTest {
        val items = ViewComposePagingItems<Int>()
        val complete = LoadState.NotLoading(endOfPaginationReached = true)
        val completeStates = LoadStates(
            refresh = complete,
            prepend = complete,
            append = complete,
        )
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            items.collectFrom(
                flowOf(
                    PagingData.from(listOf(1, 2), sourceLoadStates = completeStates),
                    PagingData.from(listOf(8, 9), sourceLoadStates = completeStates),
                ),
            )
        }
        runCurrent()

        assertEquals(listOf(8, 9), items.values())
        assertTrue(items.loadStates.refresh is LoadState.NotLoading)
    }

    @Test
    fun `bounds and release fail before presenter access`() = pagingTest {
        val items = ViewComposePagingItems<Int>()

        assertTrue(runCatching { items.peek(0) }.exceptionOrNull() is IndexOutOfBoundsException)
        items.close()

        val released = runCatching { items.retry() }.exceptionOrNull()
        assertTrue(released is IllegalStateException)
        assertTrue(released?.message.orEmpty().contains("released"))
        assertNull(items.loadStates.mediator)
    }

    private fun kotlinx.coroutines.test.TestScope.collect(
        factory: ControlledSourceFactory,
        items: ViewComposePagingItems<Int>,
        initialKey: Int? = null,
    ) {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            items.collectFrom(factory.pager(initialKey = initialKey).flow)
        }
    }

    private fun ViewComposePagingItems<Int>.values(): List<Int?> =
        (0 until itemCount).map(::peek)

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
}
