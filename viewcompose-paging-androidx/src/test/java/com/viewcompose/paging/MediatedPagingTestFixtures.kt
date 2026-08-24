package com.viewcompose.paging

import androidx.paging.ExperimentalPagingApi
import androidx.paging.LoadType
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingSource
import androidx.paging.PagingState
import androidx.paging.RemoteMediator
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.withTimeout

internal class InMemoryPagingStore(
    initialValues: List<Int> = emptyList(),
) {
    private val lock = Any()
    private var values = initialValues.toList()
    private val activeSources = LinkedHashSet<StorePagingSource>()

    fun pagingSource(): PagingSource<Int, Int> = synchronized(lock) {
        StorePagingSource().also(activeSources::add)
    }

    @OptIn(ExperimentalPagingApi::class)
    fun pager(remoteMediator: RemoteMediator<Int, Int>): Pager<Int, Int> = Pager(
        config = PagingConfig(
            pageSize = 2,
            prefetchDistance = 1,
            enablePlaceholders = false,
            initialLoadSize = 2,
        ),
        remoteMediator = remoteMediator,
        pagingSourceFactory = ::pagingSource,
    )

    fun apply(loadType: LoadType, data: List<Int>) {
        val sources = synchronized(lock) {
            values = when (loadType) {
                LoadType.REFRESH -> data.toList()
                LoadType.PREPEND -> data + values
                LoadType.APPEND -> values + data
            }
            activeSources.toList()
        }
        sources.forEach(PagingSource<Int, Int>::invalidate)
    }

    private fun snapshot(): List<Int> = synchronized(lock) { values }

    private inner class StorePagingSource : PagingSource<Int, Int>() {
        override suspend fun load(params: LoadParams<Int>): LoadResult<Int, Int> {
            return LoadResult.Page(
                data = snapshot(),
                prevKey = null,
                nextKey = null,
            )
        }

        override fun getRefreshKey(state: PagingState<Int, Int>): Int? = null
    }
}

@OptIn(ExperimentalPagingApi::class)
internal class ControlledRemoteMediator(
    private val store: InMemoryPagingStore,
    private val initializeAction: RemoteMediator.InitializeAction =
        RemoteMediator.InitializeAction.LAUNCH_INITIAL_REFRESH,
) : RemoteMediator<Int, Int>() {
    private val requests = Channel<ControlledMediatorRequest>(Channel.UNLIMITED)
    val initializeCount = AtomicInteger()

    override suspend fun initialize(): InitializeAction {
        initializeCount.incrementAndGet()
        return initializeAction
    }

    override suspend fun load(
        loadType: LoadType,
        state: PagingState<Int, Int>,
    ): MediatorResult {
        val request = ControlledMediatorRequest(loadType)
        requests.send(request)
        return try {
            when (val result = request.result.await()) {
                is ControlledMediatorResult.Success -> {
                    store.apply(loadType, result.data)
                    MediatorResult.Success(result.endOfPaginationReached)
                }
                is ControlledMediatorResult.Error -> MediatorResult.Error(result.error)
            }
        } catch (cancellation: CancellationException) {
            request.cancelled.complete(Unit)
            throw cancellation
        }
    }

    suspend fun nextRequest(): ControlledMediatorRequest =
        withTimeout(5_000) { requests.receive() }
}

internal class ControlledMediatorRequest(
    val loadType: LoadType,
) {
    internal val result = CompletableDeferred<ControlledMediatorResult>()
    val cancelled = CompletableDeferred<Unit>()

    fun completeSuccess(
        data: List<Int>,
        endOfPaginationReached: Boolean,
    ) {
        check(
            result.complete(
                ControlledMediatorResult.Success(data, endOfPaginationReached),
            ),
        )
    }

    fun completeError(error: Throwable) {
        check(result.complete(ControlledMediatorResult.Error(error)))
    }
}

internal sealed interface ControlledMediatorResult {
    data class Success(
        val data: List<Int>,
        val endOfPaginationReached: Boolean,
    ) : ControlledMediatorResult

    data class Error(val error: Throwable) : ControlledMediatorResult
}
