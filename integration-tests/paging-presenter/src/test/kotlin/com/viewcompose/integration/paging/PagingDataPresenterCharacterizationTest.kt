package com.viewcompose.integration.paging

import androidx.paging.CombinedLoadStates
import androidx.paging.ItemSnapshotList
import androidx.paging.LoadState
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.PagingDataEvent
import androidx.paging.PagingDataPresenter
import androidx.paging.PagingSource
import androidx.paging.PagingState
import java.io.IOException
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PagingDataPresenterCharacterizationTest {
    @Test
    fun `refresh installs items before the event hook and publishes coherent final state`() =
        runTest {
            val factory = ControlledSourceFactory()
            val presenter = RecordingPresenter(UnconfinedTestDispatcher(testScheduler))
            collect(factory.pager(placeholders = true), presenter)
            val source = factory.nextSource()
            runCurrent()
            val refresh = source.nextRequest()

            assertTrue(refresh.params is PagingSource.LoadParams.Refresh)
            assertTrue(requireNotNull(presenter.loadStateFlow.value).refresh is LoadState.Loading)

            refresh.completePage(
                data = listOf(0, 1, 2),
                prevKey = null,
                nextKey = 3,
                itemsBefore = 0,
                itemsAfter = 3,
            )
            runCurrent()

            val event = presenter.events.single()
            assertTrue(event.event is PagingDataEvent.Refresh)
            assertEquals(Snapshot(0, listOf(0, 1, 2), 3), event.snapshot)
            assertTrue(requireNotNull(event.loadStates).refresh is LoadState.Loading)

            val finalPages = presenter.pagesUpdated.last()
            assertEquals(Snapshot(0, listOf(0, 1, 2), 3), finalPages.snapshot)
            assertTrue(requireNotNull(finalPages.loadStates).refresh is LoadState.NotLoading)
            assertEquals(finalPages.snapshot, snapshotOf(presenter.snapshot()))
            assertTrue(
                requireNotNull(presenter.loadStateFlow.value).refresh is LoadState.NotLoading,
            )
            assertTrue(
                presenter.timeline.indexOf("event:Refresh") <
                    presenter.timeline.lastIndexOf("pages:0:[0, 1, 2]:3"),
            )
        }

    @Test
    fun `peek is non-triggering while indexed access sends one append hint`() =
        runTest {
            val factory = ControlledSourceFactory()
            val presenter = RecordingPresenter(UnconfinedTestDispatcher(testScheduler))
            collect(
                factory.pager(
                    placeholders = true,
                    prefetchDistance = 1,
                    initialKey = 2,
                ),
                presenter,
            )
            val source = factory.nextSource()
            runCurrent()
            source.nextRequest().completePage(
                data = listOf(2, 3),
                prevKey = null,
                nextKey = 4,
                itemsBefore = 0,
                itemsAfter = 4,
            )
            runCurrent()

            assertNull(presenter.peek(2))
            runCurrent()
            assertFalse(source.hasPendingRequest())

            assertEquals(3, presenter[1])
            assertEquals(3, presenter[1])
            runCurrent()
            val appendRequest = source.nextRequest()
            assertTrue(appendRequest.params is PagingSource.LoadParams.Append)
            assertEquals(4, appendRequest.params.key)
            assertFalse(source.hasPendingRequest())

            appendRequest.completePage(
                data = listOf(4, 5),
                prevKey = 2,
                nextKey = 6,
                itemsBefore = 4,
                itemsAfter = 2,
            )
            runCurrent()

            val append = presenter.events.last().event
            assertTrue(append is PagingDataEvent.Append)
            append as PagingDataEvent.Append<Int>
            assertEquals(2, append.startIndex)
            assertEquals(listOf(4, 5), append.inserted)
            assertEquals(4, append.oldPlaceholdersAfter)
            assertEquals(2, append.newPlaceholdersAfter)
            assertEquals(Snapshot(0, listOf(2, 3, 4, 5), 2), snapshotOf(presenter.snapshot()))
        }

    @Test
    fun `retry repeats the failed load in the same generation`() =
        runTest {
            val factory = ControlledSourceFactory()
            val presenter = RecordingPresenter(UnconfinedTestDispatcher(testScheduler))
            collect(factory.pager(placeholders = false), presenter)
            val source = factory.nextSource()
            runCurrent()
            val first = source.nextRequest()
            val failure = IOException("refresh failed")

            first.completeError(failure)
            runCurrent()

            val failedState = requireNotNull(presenter.loadStateFlow.value).refresh
            assertTrue(failedState is LoadState.Error)
            assertSame(failure, (failedState as LoadState.Error).error)
            assertTrue(presenter.events.isEmpty())

            presenter.retry()
            runCurrent()
            val retry = source.nextRequest()
            assertTrue(retry.params is PagingSource.LoadParams.Refresh)
            assertEquals(first.params.key, retry.params.key)
            assertEquals(1, factory.createdSources.size)

            retry.completePage(
                data = listOf(10, 11),
                prevKey = null,
                nextKey = null,
                itemsBefore = 0,
                itemsAfter = 0,
            )
            runCurrent()

            assertEquals(listOf(10, 11), presenter.snapshot().items)
            assertTrue(
                requireNotNull(presenter.loadStateFlow.value).refresh is LoadState.NotLoading,
            )
            assertTrue(
                presenter.loadStates.count { it.refresh is LoadState.Loading } >= 2,
            )
        }

    @Test
    fun `refresh and invalidation replace generations and cancel superseded loads`() =
        runTest {
            val factory = ControlledSourceFactory()
            val presenter = RecordingPresenter(UnconfinedTestDispatcher(testScheduler))
            collect(factory.pager(placeholders = false), presenter)
            val firstSource = factory.nextSource()
            runCurrent()
            firstSource.nextRequest().completePage(
                data = listOf(0, 1),
                prevKey = null,
                nextKey = null,
                itemsBefore = 0,
                itemsAfter = 0,
            )
            runCurrent()

            presenter.refresh()
            runCurrent()
            val supersededSource = factory.nextSource()
            runCurrent()
            val supersededRefresh = supersededSource.nextRequest()
            assertEquals(listOf(0, 1), presenter.snapshot().items)

            presenter.refresh()
            runCurrent()
            val winningSource = factory.nextSource()
            runCurrent()
            val winningRefresh = winningSource.nextRequest()
            runCurrent()
            assertTrue(supersededRefresh.cancelled.isCompleted)

            supersededRefresh.completePage(
                data = listOf(20, 21),
                prevKey = null,
                nextKey = null,
                itemsBefore = 0,
                itemsAfter = 0,
            )
            winningRefresh.completePage(
                data = listOf(30, 31),
                prevKey = null,
                nextKey = null,
                itemsBefore = 0,
                itemsAfter = 0,
            )
            runCurrent()

            assertEquals(listOf(30, 31), presenter.snapshot().items)
            assertFalse(presenter.events.any { it.snapshot.items == listOf(20, 21) })

            winningSource.invalidate()
            runCurrent()
            val invalidationSource = factory.nextSource()
            runCurrent()
            invalidationSource.nextRequest().completePage(
                data = listOf(40, 41),
                prevKey = null,
                nextKey = null,
                itemsBefore = 0,
                itemsAfter = 0,
            )
            runCurrent()

            assertEquals(4, factory.createdSources.size)
            assertEquals(listOf(40, 41), presenter.snapshot().items)
            assertTrue(presenter.events.last().event is PagingDataEvent.Refresh)
        }

    @Test
    fun `append beyond max size drops the prepend page and restores placeholders`() =
        runTest {
            val factory = ControlledSourceFactory()
            val presenter = RecordingPresenter(UnconfinedTestDispatcher(testScheduler))
            collect(
                factory.pager(
                    placeholders = true,
                    prefetchDistance = 1,
                    maxSize = 4,
                ),
                presenter,
            )
            val source = factory.nextSource()
            runCurrent()
            source.nextRequest().completePage(
                data = listOf(0, 1),
                prevKey = null,
                nextKey = 2,
                itemsBefore = 0,
                itemsAfter = 6,
            )
            runCurrent()

            assertEquals(1, presenter[1])
            runCurrent()
            source.nextRequest().completePage(
                data = listOf(2, 3),
                prevKey = 0,
                nextKey = 4,
                itemsBefore = 2,
                itemsAfter = 4,
            )
            runCurrent()

            assertEquals(3, presenter[3])
            runCurrent()
            source.nextRequest().completePage(
                data = listOf(4, 5),
                prevKey = 2,
                nextKey = 6,
                itemsBefore = 4,
                itemsAfter = 2,
            )
            runCurrent()

            val drop = presenter.events.first { it.event is PagingDataEvent.DropPrepend }.event
                as PagingDataEvent.DropPrepend<Int>
            assertEquals(2, drop.dropCount)
            assertEquals(0, drop.oldPlaceholdersBefore)
            assertEquals(2, drop.newPlaceholdersBefore)
            assertEquals(Snapshot(2, listOf(2, 3, 4, 5), 2), snapshotOf(presenter.snapshot()))
        }

    @Test
    fun `prepend beyond max size drops the append page and restores placeholders`() =
        runTest {
            val factory = ControlledSourceFactory()
            val presenter = RecordingPresenter(UnconfinedTestDispatcher(testScheduler))
            collect(
                factory.pager(
                    placeholders = true,
                    prefetchDistance = 1,
                    initialKey = 6,
                    maxSize = 4,
                ),
                presenter,
            )
            val source = factory.nextSource()
            runCurrent()
            source.nextRequest().completePage(
                data = listOf(6, 7),
                prevKey = 4,
                nextKey = null,
                itemsBefore = 6,
                itemsAfter = 0,
            )
            runCurrent()

            assertEquals(6, presenter[6])
            runCurrent()
            source.nextRequest().completePage(
                data = listOf(4, 5),
                prevKey = 2,
                nextKey = 6,
                itemsBefore = 4,
                itemsAfter = 2,
            )
            runCurrent()

            assertEquals(4, presenter[4])
            runCurrent()
            source.nextRequest().completePage(
                data = listOf(2, 3),
                prevKey = 0,
                nextKey = 4,
                itemsBefore = 2,
                itemsAfter = 4,
            )
            runCurrent()

            val drop = presenter.events.first { it.event is PagingDataEvent.DropAppend }.event
                as PagingDataEvent.DropAppend<Int>
            assertEquals(2, drop.dropCount)
            assertEquals(0, drop.oldPlaceholdersAfter)
            assertEquals(2, drop.newPlaceholdersAfter)
            assertEquals(Snapshot(2, listOf(2, 3, 4, 5), 2), snapshotOf(presenter.snapshot()))
        }

    private fun TestScope.collect(
        pager: Pager<Int, Int>,
        presenter: RecordingPresenter,
    ): Job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
        pager.flow.collectLatest { pagingData -> presenter.collectFrom(pagingData) }
    }
}

private class RecordingPresenter(
    mainContext: CoroutineContext,
) : PagingDataPresenter<Int>(mainContext) {
    val events = mutableListOf<EventObservation>()
    val pagesUpdated = mutableListOf<Observation>()
    val loadStates = mutableListOf<CombinedLoadStates>()
    val timeline = mutableListOf<String>()

    init {
        addLoadStateListener { states ->
            loadStates += states
            timeline += "state:${states.refresh::class.simpleName}"
        }
        addOnPagesUpdatedListener {
            val observation = Observation(snapshotOf(snapshot()), loadStateFlow.value)
            pagesUpdated += observation
            timeline += "pages:${observation.snapshot.placeholdersBefore}:" +
                "${observation.snapshot.items}:${observation.snapshot.placeholdersAfter}"
        }
    }

    override suspend fun presentPagingDataEvent(event: PagingDataEvent<Int>) {
        events += EventObservation(event, snapshotOf(snapshot()), loadStateFlow.value)
        timeline += "event:${event::class.simpleName}"
    }
}

private data class EventObservation(
    val event: PagingDataEvent<Int>,
    val snapshot: Snapshot,
    val loadStates: CombinedLoadStates?,
)

private data class Observation(
    val snapshot: Snapshot,
    val loadStates: CombinedLoadStates?,
)

private data class Snapshot(
    val placeholdersBefore: Int,
    val items: List<Int>,
    val placeholdersAfter: Int,
)

private fun snapshotOf(snapshot: ItemSnapshotList<Int>): Snapshot =
    Snapshot(
        placeholdersBefore = snapshot.placeholdersBefore,
        items = snapshot.items.toList(),
        placeholdersAfter = snapshot.placeholdersAfter,
    )

private class ControlledSourceFactory {
    private val sources = Channel<ControlledPagingSource>(Channel.UNLIMITED)
    val createdSources = mutableListOf<ControlledPagingSource>()

    fun pager(
        placeholders: Boolean,
        prefetchDistance: Int = 1,
        initialKey: Int? = null,
        maxSize: Int = PagingConfig.MAX_SIZE_UNBOUNDED,
    ): Pager<Int, Int> = Pager(
        config = PagingConfig(
            pageSize = 2,
            prefetchDistance = prefetchDistance,
            enablePlaceholders = placeholders,
            initialLoadSize = 2,
            maxSize = maxSize,
        ),
        initialKey = initialKey,
        pagingSourceFactory = {
            ControlledPagingSource().also { source ->
                createdSources += source
                check(sources.trySend(source).isSuccess)
            }
        },
    )

    suspend fun nextSource(): ControlledPagingSource = withTimeout(5_000) { sources.receive() }
}

private class ControlledPagingSource : PagingSource<Int, Int>() {
    private val requests = Channel<ControlledLoadRequest>(Channel.UNLIMITED)

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, Int> {
        val request = ControlledLoadRequest(params)
        requests.send(request)
        return try {
            request.result.await()
        } catch (cancellation: CancellationException) {
            request.cancelled.complete(Unit)
            throw cancellation
        }
    }

    override fun getRefreshKey(state: PagingState<Int, Int>): Int? =
        state.anchorPosition?.let { position -> state.closestPageToPosition(position)?.prevKey }

    suspend fun nextRequest(): ControlledLoadRequest = withTimeout(5_000) { requests.receive() }

    fun hasPendingRequest(): Boolean = requests.tryReceive().isSuccess
}

private class ControlledLoadRequest(
    val params: PagingSource.LoadParams<Int>,
) {
    val result = CompletableDeferred<PagingSource.LoadResult<Int, Int>>()
    val cancelled = CompletableDeferred<Unit>()

    fun completePage(
        data: List<Int>,
        prevKey: Int?,
        nextKey: Int?,
        itemsBefore: Int,
        itemsAfter: Int,
    ) {
        check(
            result.complete(
                PagingSource.LoadResult.Page(
                    data = data,
                    prevKey = prevKey,
                    nextKey = nextKey,
                    itemsBefore = itemsBefore,
                    itemsAfter = itemsAfter,
                ),
            ),
        )
    }

    fun completeError(error: Throwable) {
        check(result.complete(PagingSource.LoadResult.Error(error)))
    }
}
