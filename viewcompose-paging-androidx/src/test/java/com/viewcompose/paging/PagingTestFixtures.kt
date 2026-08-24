package com.viewcompose.paging

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingSource
import androidx.paging.PagingState
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.withTimeout
import com.viewcompose.runtime.composition.ComposerLite
import com.viewcompose.ui.foundation.UiTreeBuilder
import com.viewcompose.ui.foundation.buildVNodeTree

internal class ControlledSourceFactory {
    private val sources = Channel<ControlledPagingSource>(Channel.UNLIMITED)
    val createdSources = mutableListOf<ControlledPagingSource>()

    fun pager(
        placeholders: Boolean = false,
        prefetchDistance: Int = 1,
        initialKey: Int? = null,
    ): Pager<Int, Int> = Pager(
        config = PagingConfig(
            pageSize = 2,
            prefetchDistance = prefetchDistance,
            enablePlaceholders = placeholders,
            initialLoadSize = 2,
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

internal class ControlledPagingSource : PagingSource<Int, Int>() {
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
        state.anchorPosition?.let { position ->
            state.closestPageToPosition(position)?.let { page ->
                page.prevKey?.plus(state.config.pageSize)
                    ?: page.nextKey?.minus(state.config.pageSize)
            }
        }

    suspend fun nextRequest(): ControlledLoadRequest = withTimeout(5_000) { requests.receive() }

    fun hasPendingRequest(): Boolean = requests.tryReceive().isSuccess
}

internal class ControlledLoadRequest(
    val params: PagingSource.LoadParams<Int>,
) {
    val result = CompletableDeferred<PagingSource.LoadResult<Int, Int>>()
    val cancelled = CompletableDeferred<Unit>()

    fun completePage(
        data: List<Int>,
        prevKey: Int?,
        nextKey: Int?,
        itemsBefore: Int = PagingSource.LoadResult.Page.COUNT_UNDEFINED,
        itemsAfter: Int = PagingSource.LoadResult.Page.COUNT_UNDEFINED,
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

internal class PagingCompositionHarness {
    private val composer = ComposerLite()
    private val composerContextClass = Class.forName("com.viewcompose.ui.foundation.ComposerContext")
    private val composerContextInstance: Any = requireNotNull(
        composerContextClass.getField("INSTANCE").get(null),
    )
    private val withComposer = composerContextClass.findMethodPrefix(
        prefix = "withComposer",
        paramCount = 3,
    )

    fun <T> render(block: UiTreeBuilder.() -> T): T = compose {
        var value: T? = null
        buildVNodeTree {
            value = block()
        }
        @Suppress("UNCHECKED_CAST")
        value as T
    }

    fun dispose() {
        composer.dispose()
    }

    private fun <T> compose(block: () -> T): T {
        if (!composer.hasPendingInvalidations()) {
            composer.requestRootRecompose()
        }
        val result = inComposerContext {
            composer.composeRoot(block)
        }
        composer.commitSideEffects()
        return result
    }

    private fun <T> inComposerContext(block: () -> T): T {
        val callback = object : kotlin.jvm.functions.Function0<T> {
            override fun invoke(): T = block()
        }
        return try {
            @Suppress("UNCHECKED_CAST")
            withComposer.invoke(
                composerContextInstance,
                composer,
                Dispatchers.Unconfined,
                callback,
            ) as T
        } catch (error: InvocationTargetException) {
            throw error.targetException
        }
    }

    private fun Class<*>.findMethodPrefix(
        prefix: String,
        paramCount: Int,
    ): Method {
        val method = methods.firstOrNull { candidate ->
            candidate.name.startsWith(prefix) && candidate.parameterCount == paramCount
        } ?: declaredMethods.firstOrNull { candidate ->
            candidate.name.startsWith(prefix) && candidate.parameterCount == paramCount
        } ?: error("Method with prefix '$prefix' and $paramCount params not found in $name")
        method.isAccessible = true
        return method
    }
}
