package com.viewcompose.paging

import androidx.paging.ExperimentalPagingApi
import androidx.paging.LoadState
import androidx.paging.LoadType
import com.viewcompose.paging.PagingContentState.Content
import com.viewcompose.paging.PagingContentState.InitialError
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class, ExperimentalPagingApi::class)
@RunWith(RobolectricTestRunner::class)
class PagingMediatedDataTest {
    @Test
    fun `composition release cancels an in flight mediator request without publishing an error`() =
        pagingTest {
            val store = InMemoryPagingStore()
            val mediator = ControlledRemoteMediator(store)
            val harness = PagingCompositionHarness()
            val items = harness.render {
                store.pager(mediator).flow.collectAsViewComposePagingItems(
                    lifecyclePolicy = PagingLifecyclePolicy.Composition,
                    context = Dispatchers.Unconfined,
                )
            }
            runCurrent()
            val request = mediator.nextRequest()
            assertEquals(LoadType.REFRESH, request.loadType)

            harness.dispose()
            withTimeout(5_000) { request.cancelled.await() }

            val mediatorRefresh = items.loadStates.mediator?.refresh
            assertTrue(mediatorRefresh == null || mediatorRefresh is LoadState.Loading)
            assertTrue(runCatching { items.retry() }.exceptionOrNull() is IllegalStateException)
        }

    @Test
    fun `mediator refresh failure preserves source origin and selects initial error`() =
        pagingTest {
            val store = InMemoryPagingStore()
            val mediator = ControlledRemoteMediator(store)
            val harness = PagingCompositionHarness()
            val items = harness.render {
                store.pager(mediator).flow
                    .collectAsViewComposePagingItems(
                        lifecyclePolicy = PagingLifecyclePolicy.Composition,
                        context = Dispatchers.Unconfined,
                    )
            }
            runCurrent()
            val request = mediator.nextRequest()
            assertEquals(LoadType.REFRESH, request.loadType)
            val failure = IOException("remote refresh failed")

            request.completeError(failure)
            runCurrent()

            val refresh = items.loadStates.forLoadType(LoadType.REFRESH)
            assertTrue("combined=${refresh.combined}", refresh.combined is LoadState.Error)
            assertTrue("source=${refresh.source}", refresh.source is LoadState.NotLoading)
            assertTrue("mediator=${refresh.mediator}", refresh.mediator is LoadState.Error)
            assertSame(failure, (refresh.mediator as LoadState.Error).error)
            assertSame(failure, (items.contentState as InitialError).error)
            harness.dispose()
        }

    @Test
    fun `mediator append failure retains loaded content and source state`() = pagingTest {
        val store = InMemoryPagingStore()
        val mediator = ControlledRemoteMediator(store)
        val harness = PagingCompositionHarness()
        val items = harness.render {
            store.pager(mediator).flow
                .collectAsViewComposePagingItems(
                    lifecyclePolicy = PagingLifecyclePolicy.Composition,
                    context = Dispatchers.Unconfined,
                )
        }
        runCurrent()
        mediator.nextRequest().completeSuccess(
            data = listOf(10, 11),
            endOfPaginationReached = false,
        )
        runCurrent()
        assertEquals(listOf(10, 11), items.values())

        val prependRequest = mediator.nextRequest()
        assertEquals(LoadType.PREPEND, prependRequest.loadType)
        prependRequest.completeSuccess(
            data = emptyList(),
            endOfPaginationReached = true,
        )
        runCurrent()
        val appendRequest = mediator.nextRequest()
        assertEquals(LoadType.APPEND, appendRequest.loadType)
        val failure = IOException("remote append failed")
        appendRequest.completeError(failure)
        runCurrent()

        val append = items.loadStates.forLoadType(LoadType.APPEND)
        assertEquals(listOf(10, 11), items.values())
        assertSame(Content, items.contentState)
        assertTrue(append.combined is LoadState.Error)
        assertTrue(append.source is LoadState.NotLoading)
        assertTrue(append.mediator is LoadState.Error)
        assertSame(failure, (append.mediator as LoadState.Error).error)
        harness.dispose()
    }

    @Test
    fun `source failure remains distinct when a mediator is installed`() = pagingTest {
        val store = InMemoryPagingStore()
        val mediator = ControlledRemoteMediator(
            store = store,
            initializeAction = androidx.paging.RemoteMediator.InitializeAction.SKIP_INITIAL_REFRESH,
        )
        val sourceFactory = ControlledSourceFactory()
        val harness = PagingCompositionHarness()
        val items = harness.render {
            sourceFactory.mediatedPager(mediator).flow
                .collectAsViewComposePagingItems(
                    lifecyclePolicy = PagingLifecyclePolicy.Composition,
                    context = Dispatchers.Unconfined,
                )
        }
        runCurrent()
        val source = sourceFactory.nextSource()
        val failure = IOException("local source failed")
        source.nextRequest().completeError(failure)
        runCurrent()

        val refresh = items.loadStates.forLoadType(LoadType.REFRESH)
        assertTrue("combined=${refresh.combined}", refresh.combined is LoadState.NotLoading)
        assertTrue("source=${refresh.source}", refresh.source is LoadState.Error)
        assertTrue("mediator=${refresh.mediator}", refresh.mediator is LoadState.NotLoading)
        assertSame(failure, (refresh.source as LoadState.Error).error)
        assertSame(failure, (items.contentState as InitialError).error)
        assertEquals(1, mediator.initializeCount.get())
        harness.dispose()
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
