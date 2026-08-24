package com.viewcompose.paging

import android.os.Looper
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.paging.CombinedLoadStates
import androidx.paging.LoadState
import androidx.paging.LoadStates
import androidx.paging.PagingData
import androidx.paging.PagingDataEvent
import androidx.paging.PagingDataPresenter
import com.viewcompose.lifecycle.LocalLifecycleOwner
import com.viewcompose.runtime.MutableState
import com.viewcompose.runtime.mutableStateOf
import com.viewcompose.ui.foundation.DisposableEffect
import com.viewcompose.ui.foundation.LaunchedEffect
import com.viewcompose.ui.foundation.remember
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Exposes one remembered, observable AndroidX Paging presentation to ViewCompose UI.
 *
 * Obtain this owner through [collectAsViewComposePagingItems]. Item and load-state properties read
 * one coherent snapshot, so a page event never exposes new items with the preceding load state.
 * Flow identity owns the instance; policy or non-`Job` context changes restart collection without
 * discarding its last accepted presentation.
 *
 * Item access and commands are confined to Android's main thread. Leaving composition cancels the
 * collector and releases presenter listeners. A retained reference may still read its final
 * properties, but item access and commands then fail. The owner does not save `PagingData`, loaded
 * pages, or repository data.
 *
 * @sample com.viewcompose.paging.samples.pagingLazyColumnSample
 * @param T non-null item type presented by AndroidX Paging
 */
class ViewComposePagingItems<T : Any> internal constructor() {
    private val presentation: MutableState<PagingPresentation<T>> =
        mutableStateOf(PagingPresentation.initial())
    private val presenter = Presenter()
    private val collectionMutex = Mutex()
    private var pendingPageEvent = false
    private var closed = false
    private var revision = 0L

    private val loadStateListener: (CombinedLoadStates) -> Unit = { states ->
        if (!closed && !pendingPageEvent) {
            publishLoadStates(states)
        }
    }
    private val pagesUpdatedListener: () -> Unit = {
        if (!closed) {
            pendingPageEvent = false
            publishPages(presenter.loadStateFlow.value ?: presentation.value.loadStates)
        }
    }

    init {
        presenter.addLoadStateListener(loadStateListener)
        presenter.addOnPagesUpdatedListener(pagesUpdatedListener)
    }

    /** Returns the observable number of presented slots in the current coherent snapshot. */
    val itemCount: Int
        get() = presentation.value.itemCount

    /** Returns the observable number of non-placeholder items in the current coherent snapshot. */
    val loadedItemCount: Int
        get() = presentation.value.items.size

    /** Returns the observable combined source and mediator load states for the current snapshot. */
    val loadStates: CombinedLoadStates
        get() = presentation.value.loadStates

    /**
     * Returns the item at [index] and sends AndroidX Paging the corresponding load-access hint.
     *
     * This operation targets the active presenter generation and must run on Android's main thread.
     * With placeholders disabled, a valid index returns a non-null value. Placeholder-enabled
     * presentations may return `null` after that overload is introduced.
     *
     * @param index presented zero-based slot index
     * @return the loaded item, or `null` for an unloaded placeholder slot
     * @throws IndexOutOfBoundsException when [index] is outside the current presented snapshot
     * @throws IllegalStateException when called off the Android main thread or after release
     */
    operator fun get(index: Int): T? {
        checkUsable("get")
        checkIndex(index)
        return presenter[index]
    }

    /**
     * Returns the item at [index] without sending a load-access hint.
     *
     * Use this path for inspection and reconciliation. It reads the active presenter generation on
     * Android's main thread and may return `null` only for an unloaded placeholder slot.
     *
     * @param index presented zero-based slot index
     * @return the loaded item, or `null` for an unloaded placeholder slot
     * @throws IndexOutOfBoundsException when [index] is outside the current presented snapshot
     * @throws IllegalStateException when called off the Android main thread or after release
     */
    fun peek(index: Int): T? {
        checkUsable("peek")
        checkIndex(index)
        return presenter.peek(index)
    }

    /**
     * Retries failed loads in the current AndroidX Paging generation.
     *
     * The command runs on Android's main thread and does not create a replacement generation. It is
     * ignored by AndroidX when the current generation has no retryable load.
     *
     * @throws IllegalStateException when called off the Android main thread or after release
     */
    fun retry() {
        checkUsable("retry")
        presenter.retry()
    }

    /**
     * Requests a new AndroidX Paging generation from the upstream `Pager`.
     *
     * The command runs on Android's main thread. The current coherent presentation remains observable
     * until AndroidX publishes its replacement according to normal `PagingData` behavior.
     *
     * @throws IllegalStateException when called off the Android main thread or after release
     */
    fun refresh() {
        checkUsable("refresh")
        presenter.refresh()
    }

    internal fun loadedItemsForLazyColumn(): List<PagingLoadedItem<T>> {
        val current = presentation.value
        check(current.itemCount == current.items.size) {
            "PagingLazyColumn without placeholderContent requires placeholders to be disabled. " +
                "Presented ${current.itemCount} slots but only ${current.items.size} are loaded."
        }
        return current.items.mapIndexed { index, item -> PagingLoadedItem(index, item) }
    }

    internal fun requestLoadForActiveItem(index: Int) {
        checkMainThread("PagingLazyColumn item access")
        if (closed || index !in 0 until presenter.size) return
        presenter[index]
    }

    internal suspend fun collectFrom(flow: Flow<PagingData<T>>) {
        collectionMutex.withLock {
            flow.collectLatest { pagingData ->
                try {
                    presenter.collectFrom(pagingData)
                } finally {
                    withContext(Dispatchers.Main.immediate) {
                        pendingPageEvent = false
                    }
                }
            }
        }
    }

    internal fun close() {
        checkMainThread("ViewComposePagingItems release")
        if (closed) return
        closed = true
        pendingPageEvent = false
        presenter.removeLoadStateListener(loadStateListener)
        presenter.removeOnPagesUpdatedListener(pagesUpdatedListener)
    }

    private fun publishLoadStates(states: CombinedLoadStates) {
        checkMainThread("Paging load-state publication")
        val current = presentation.value
        if (current.loadStates == states) return
        presentation.value = current.copy(
            loadStates = states,
            revision = nextRevision(),
        )
    }

    private fun publishPages(states: CombinedLoadStates) {
        checkMainThread("Paging page publication")
        val snapshot = presenter.snapshot()
        val current = presentation.value
        val nextItems = snapshot.items.toList()
        if (
            current.itemCount == snapshot.size &&
            current.items == nextItems &&
            current.loadStates == states
        ) {
            return
        }
        presentation.value = PagingPresentation(
            itemCount = snapshot.size,
            items = nextItems,
            loadStates = states,
            revision = nextRevision(),
        )
    }

    private fun nextRevision(): Long = ++revision

    private fun checkIndex(index: Int) {
        val count = presentation.value.itemCount
        if (index !in 0 until count) {
            throw IndexOutOfBoundsException("Paging item index $index is outside 0 until $count.")
        }
    }

    private fun checkUsable(operation: String) {
        checkMainThread("ViewComposePagingItems.$operation")
        check(!closed) { "ViewComposePagingItems has left composition and is released." }
    }

    private inner class Presenter : PagingDataPresenter<T>(Dispatchers.Main.immediate) {
        override suspend fun presentPagingDataEvent(event: PagingDataEvent<T>) {
            pendingPageEvent = true
        }
    }
}

/**
 * Collects this AndroidX Paging stream into a remembered ViewCompose presentation owner.
 *
 * Collection starts only after a successful composition commit. [lifecyclePolicy] controls Android
 * lifecycle gating and retains the last coherent snapshot while inactive. Flow identity creates a
 * new [ViewComposePagingItems]; changing only the policy or [context] serially restarts collection
 * on the existing owner. Latest `PagingData` wins through `collectLatest` cancellation.
 *
 * [context] may add a dispatcher, name, or other contextual element but cannot contain a [Job]. The
 * composition owns cancellation. Upstream Flow exceptions follow the render session's coroutine
 * failure route, while Paging load failures remain represented by [CombinedLoadStates].
 *
 * @sample com.viewcompose.paging.samples.pagingLazyColumnSample
 * @param T non-null item type emitted by Paging
 * @receiver Paging generations owned and cached by the application
 * @param lifecyclePolicy collection threshold and inactive-retention policy
 * @param context additional non-`Job` coroutine context used for upstream collection
 * @return one observable owner remembered for this Flow identity
 * @throws IllegalArgumentException when [context] contains a [Job] or a lifecycle-gated policy has
 * no nearest AndroidX lifecycle owner
 */
fun <T : Any> Flow<PagingData<T>>.collectAsViewComposePagingItems(
    lifecyclePolicy: PagingLifecyclePolicy = PagingLifecyclePolicy.Visible,
    context: CoroutineContext = EmptyCoroutineContext,
): ViewComposePagingItems<T> {
    require(context[Job] == null) {
        "collectAsViewComposePagingItems context must not contain a Job."
    }
    val lifecycle = when (lifecyclePolicy) {
        PagingLifecyclePolicy.Visible,
        PagingLifecyclePolicy.Retained,
        -> requireNotNull(LocalLifecycleOwner.current) {
            "No LifecycleOwner found. Use ComponentActivity/Fragment.setUiContent, wrap with " +
                "ProvideLifecycleOwner, or select PagingLifecyclePolicy.Composition."
        }.lifecycle
        PagingLifecyclePolicy.Composition -> null
    }
    val items = remember(this) { ViewComposePagingItems<T>() }

    DisposableEffect(items) {
        onDispose(items::close)
    }
    LaunchedEffect(this, lifecyclePolicy, context, lifecycle) {
        when (lifecyclePolicy) {
            PagingLifecyclePolicy.Visible -> checkNotNull(lifecycle).repeatOnLifecycle(
                Lifecycle.State.STARTED,
            ) {
                withContext(context) {
                    items.collectFrom(this@collectAsViewComposePagingItems)
                }
            }
            PagingLifecyclePolicy.Retained -> checkNotNull(lifecycle).repeatOnLifecycle(
                Lifecycle.State.CREATED,
            ) {
                withContext(context) {
                    items.collectFrom(this@collectAsViewComposePagingItems)
                }
            }
            PagingLifecyclePolicy.Composition -> withContext(context) {
                items.collectFrom(this@collectAsViewComposePagingItems)
            }
        }
    }
    return items
}

internal data class PagingLoadedItem<T : Any>(
    val index: Int,
    val value: T,
)

private data class PagingPresentation<T : Any>(
    val itemCount: Int,
    val items: List<T>,
    val loadStates: CombinedLoadStates,
    val revision: Long,
) {
    companion object {
        fun <T : Any> initial(): PagingPresentation<T> {
            val incomplete = LoadState.NotLoading(endOfPaginationReached = false)
            val source = LoadStates(
                refresh = LoadState.Loading,
                prepend = incomplete,
                append = incomplete,
            )
            return PagingPresentation(
                itemCount = 0,
                items = emptyList(),
                loadStates = CombinedLoadStates(
                    refresh = LoadState.Loading,
                    prepend = incomplete,
                    append = incomplete,
                    source = source,
                    mediator = null,
                ),
                revision = 0L,
            )
        }
    }
}

private fun checkMainThread(operation: String) {
    check(Looper.myLooper() === Looper.getMainLooper()) {
        "$operation must run on the Android main thread."
    }
}
