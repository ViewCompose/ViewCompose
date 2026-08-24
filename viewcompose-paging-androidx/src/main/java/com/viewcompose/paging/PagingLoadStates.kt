package com.viewcompose.paging

import androidx.paging.CombinedLoadStates
import androidx.paging.LoadState
import androidx.paging.LoadStates
import androidx.paging.LoadType

/**
 * Selects the primary body that an application should compose for one Paging presentation.
 *
 * Resolve this hierarchy exhaustively. [Content] takes precedence whenever at least one item is
 * loaded, including during refresh, prepend, or append loading and failure, so existing content is
 * not replaced by a transient load state. With no loaded items, combined refresh state selects
 * [InitialLoading], [InitialError], or [Empty]. This projection owns no nodes, visuals, wording,
 * analytics, retry policy, or lifecycle.
 *
 * The known subtypes are a closed source-compatible set for the current Alpha contract. Adding a
 * subtype before stabilization is an API change that requires callers' exhaustive branches to be
 * updated.
 *
 * @sample com.viewcompose.paging.samples.pagingLoadStateCompositionSample
 */
sealed interface PagingContentState {
    /** Selects initial loading UI when combined refresh is loading and no item is loaded. */
    data object InitialLoading : PagingContentState

    /**
     * Selects initial failure UI when combined refresh failed and no item is loaded.
     *
     * @property error exact failure carried by AndroidX Paging's combined refresh state
     */
    data class InitialError(val error: Throwable) : PagingContentState

    /** Selects empty UI after combined refresh completed successfully with no loaded item. */
    data object Empty : PagingContentState

    /** Selects mounted item content whenever at least one item remains loaded. */
    data object Content : PagingContentState
}

/**
 * Captures one load type without flattening AndroidX Paging's source and mediator origins.
 *
 * Values are copied from one immutable [CombinedLoadStates] input. [combined] follows AndroidX's
 * convenience UI semantics, [source] always exposes the local [androidx.paging.PagingSource]
 * state, and [mediator] is `null` when no [androidx.paging.RemoteMediator] participates. Instances
 * are structural values and own no loading work, failure, command, listener, or lifecycle.
 *
 * @sample com.viewcompose.paging.samples.pagingLoadStateCompositionSample
 * @property loadType refresh, prepend, or append operation selected from the input
 * @property combined AndroidX convenience state for the selected operation
 * @property source unflattened source state for the selected operation
 * @property mediator unflattened mediator state, or `null` when the presentation has no mediator
 */
data class PagingLoadStateSnapshot(
    val loadType: LoadType,
    val combined: LoadState,
    val source: LoadState,
    val mediator: LoadState?,
)

/**
 * Returns the observable primary-content projection of this coherent Paging presentation.
 *
 * The getter reads one accepted item/load-state snapshot. Any positive loaded-item count returns
 * [PagingContentState.Content], even if refresh, prepend, or append is loading or failed. With no
 * loaded items, combined refresh loading returns [PagingContentState.InitialLoading], refresh
 * failure returns [PagingContentState.InitialError] with the original [Throwable], and completed
 * refresh returns [PagingContentState.Empty]. Inspect [ViewComposePagingItems.loadStates] and
 * [forLoadType] for non-blocking refresh UI and unflattened source or mediator detail.
 *
 * Reads participate in normal ViewCompose observation, allocate only an error value when needed,
 * perform O(1) synchronous work without dispatch or blocking, and remain available from a retained
 * reference after collection release. The returned value does not emit UI or invoke `retry()` or
 * `refresh()`.
 *
 * @sample com.viewcompose.paging.samples.pagingLoadStateCompositionSample
 * @receiver remembered Paging presentation whose accepted snapshot is projected
 */
val ViewComposePagingItems<*>.contentState: PagingContentState
    get() {
        val current = presentationForLazyColumn()
        if (current.items.isNotEmpty()) {
            return PagingContentState.Content
        }
        return when (val refresh = current.loadStates.refresh) {
            is LoadState.Loading -> PagingContentState.InitialLoading
            is LoadState.Error -> PagingContentState.InitialError(refresh.error)
            is LoadState.NotLoading -> PagingContentState.Empty
        }
    }

/**
 * Returns combined, source, and mediator states for one refresh, prepend, or append operation.
 *
 * Selection is a pure O(1) operation over this immutable value. It allocates one
 * [PagingLoadStateSnapshot], performs no observation or dispatch, and preserves the exact
 * [LoadState] and failure identities supplied by AndroidX. A missing mediator remains `null`; the
 * function does not substitute source state, invoke commands, retry failures, or emit UI.
 *
 * @sample com.viewcompose.paging.samples.pagingLoadStateCompositionSample
 * @receiver one coherent AndroidX combined load-state snapshot
 * @param loadType operation whose combined and origin-specific states are selected
 * @return immutable structural snapshot for [loadType]
 */
fun CombinedLoadStates.forLoadType(loadType: LoadType): PagingLoadStateSnapshot {
    return PagingLoadStateSnapshot(
        loadType = loadType,
        combined = select(loadType),
        source = source.select(loadType),
        mediator = mediator?.select(loadType),
    )
}

private fun CombinedLoadStates.select(loadType: LoadType): LoadState = when (loadType) {
    LoadType.REFRESH -> refresh
    LoadType.PREPEND -> prepend
    LoadType.APPEND -> append
}

private fun LoadStates.select(loadType: LoadType): LoadState = when (loadType) {
    LoadType.REFRESH -> refresh
    LoadType.PREPEND -> prepend
    LoadType.APPEND -> append
}
