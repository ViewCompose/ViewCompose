package com.viewcompose

import androidx.paging.LoadState
import androidx.paging.LoadType
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.viewcompose.demo.automation.demoAutomationTarget
import com.viewcompose.demo.contract.DemoAutomationRole
import com.viewcompose.demo.contract.DemoScenarioSpec
import com.viewcompose.host.android.resources.stringResource
import com.viewcompose.paging.PagingContentState
import com.viewcompose.paging.PagingLazyColumn
import com.viewcompose.paging.PagingLifecyclePolicy
import com.viewcompose.paging.PagingLoadStateSnapshot
import com.viewcompose.paging.collectAsViewComposePagingItems
import com.viewcompose.paging.contentState
import com.viewcompose.paging.forLoadType
import com.viewcompose.ui.foundation.Button
import com.viewcompose.ui.foundation.ButtonVariant
import com.viewcompose.ui.foundation.Column
import com.viewcompose.ui.foundation.Row
import com.viewcompose.ui.foundation.Surface
import com.viewcompose.ui.foundation.SurfaceVariant
import com.viewcompose.ui.foundation.Text
import com.viewcompose.ui.foundation.TextDefaults
import com.viewcompose.ui.foundation.UiTextStyle
import com.viewcompose.ui.foundation.UiTreeBuilder
import com.viewcompose.ui.foundation.key
import com.viewcompose.ui.foundation.remember
import com.viewcompose.runtime.mutableStateOf
import com.viewcompose.ui.modifier.Modifier
import com.viewcompose.ui.modifier.fillMaxSize
import com.viewcompose.ui.modifier.fillMaxWidth
import com.viewcompose.ui.modifier.height
import com.viewcompose.ui.modifier.padding
import com.viewcompose.ui.node.policy.LazyLayoutPrefetchPolicy
import com.viewcompose.ui.unit.dp
import com.viewcompose.ui.unit.sp
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow

internal data class DemoPagingRow(
    val id: Int,
    val label: String,
)

internal enum class DemoPagingOutcome {
    Data,
    Empty,
    Error,
    ;

    fun next(): DemoPagingOutcome = entries[(ordinal + 1) % entries.size]
}

/**
 * Owns a deterministic fake source for the Demo without replacing any AndroidX Paging behavior.
 * Each load suspends until the page resolves its next result, which keeps transient load states
 * inspectable by people and automation without sleeping or depending on a network.
 */
internal class ControlledPagingDemo(
    private val rows: List<DemoPagingRow>,
    private val errorMessage: String,
) {
    private val resultLock = Any()
    private var pendingResult: CompletableDeferred<DemoPagingOutcome>? = null
    private var earlyResult: DemoPagingOutcome? = null

    val flow: Flow<androidx.paging.PagingData<DemoPagingRow>> = Pager(
        config = PagingConfig(
            pageSize = PAGE_SIZE,
            initialLoadSize = PAGE_SIZE,
            prefetchDistance = 1,
            enablePlaceholders = false,
        ),
        pagingSourceFactory = ::newPagingSource,
    ).flow

    internal fun newPagingSource(): PagingSource<Int, DemoPagingRow> =
        ControlledDemoPagingSource(this)

    fun resolveNext(outcome: DemoPagingOutcome) {
        val pending = synchronized(resultLock) {
            pendingResult?.also { pendingResult = null } ?: run {
                if (earlyResult == null) {
                    earlyResult = outcome
                }
                null
            }
        }
        pending?.complete(outcome)
    }

    internal suspend fun load(
        params: PagingSource.LoadParams<Int>,
    ): PagingSource.LoadResult<Int, DemoPagingRow> {
        return when (awaitOutcome()) {
            DemoPagingOutcome.Data -> dataPage(params)
            DemoPagingOutcome.Empty -> PagingSource.LoadResult.Page(
                data = emptyList(),
                prevKey = null,
                nextKey = null,
            )
            DemoPagingOutcome.Error -> PagingSource.LoadResult.Error(
                IllegalStateException(errorMessage),
            )
        }
    }

    private suspend fun awaitOutcome(): DemoPagingOutcome {
        val deferred = CompletableDeferred<DemoPagingOutcome>()
        val immediate = synchronized(resultLock) {
            earlyResult?.also { earlyResult = null } ?: run {
                check(pendingResult == null) { "The controlled Demo supports one load at a time." }
                pendingResult = deferred
                null
            }
        }
        if (immediate != null) {
            return immediate
        }
        return try {
            deferred.await()
        } finally {
            synchronized(resultLock) {
                if (pendingResult === deferred) {
                    pendingResult = null
                }
            }
        }
    }

    private fun dataPage(
        params: PagingSource.LoadParams<Int>,
    ): PagingSource.LoadResult.Page<Int, DemoPagingRow> {
        val page = params.key ?: 0
        val start = page * PAGE_SIZE
        val data = rows.drop(start).take(PAGE_SIZE)
        val nextKey = (page + 1).takeIf { start + data.size < rows.size }
        return PagingSource.LoadResult.Page(
            data = data,
            prevKey = null,
            nextKey = nextKey,
        )
    }

    private companion object {
        const val PAGE_SIZE = 10
    }
}

private class ControlledDemoPagingSource(
    private val fixture: ControlledPagingDemo,
) : PagingSource<Int, DemoPagingRow>() {
    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, DemoPagingRow> =
        fixture.load(params)

    override fun getRefreshKey(state: PagingState<Int, DemoPagingRow>): Int? = null
}

internal fun UiTreeBuilder.CollectionPagingPage(
    scenario: DemoScenarioSpec?,
    lifecyclePolicy: PagingLifecyclePolicy = PagingLifecyclePolicy.Visible,
) {
    val generationState = remember { mutableStateOf(0) }
    val outcomeState = remember { mutableStateOf(DemoPagingOutcome.Data) }
    val rows = (1..30).map { id ->
        DemoPagingRow(
            id = id,
            label = stringResource(R.string.demo_collections_paging_row, id),
        )
    }
    val controlledError = stringResource(R.string.demo_collections_paging_controlled_error)
    val fixture = remember(generationState.value, rows, controlledError) {
        ControlledPagingDemo(rows = rows, errorMessage = controlledError)
    }
    val items = fixture.flow.collectAsViewComposePagingItems(lifecyclePolicy = lifecyclePolicy)
    val contentState = items.contentState
    val refresh = items.loadStates.forLoadType(LoadType.REFRESH)
    val append = items.loadStates.forLoadType(LoadType.APPEND)
    val primaryAction = pagingPrimaryAction(contentState, refresh, append)
    val bodyLabel = pagingBodyLabel(contentState)
    val refreshLabel = pagingLoadStateLabel(refresh.combined)
    val appendLabel = pagingLoadStateLabel(append.combined)
    val outcomeLabel = pagingOutcomeLabel(outcomeState.value)

    Column(
        spacing = 8.dp,
        modifier = Modifier.fillMaxSize(),
    ) {
        Text(
            text = stringResource(R.string.demo_collections_paging_verification),
            style = UiTextStyle(fontSizeSp = 13.sp),
            color = TextDefaults.secondaryColor(),
        )
        Text(
            text = stringResource(
                R.string.demo_collections_paging_state,
                bodyLabel,
                refreshLabel,
                appendLabel,
                items.loadedItemCount,
            ),
            modifier = Modifier
                .fillMaxWidth()
                .scenarioPagingTarget(scenario, DemoAutomationRole.State),
        )
        Text(
            text = stringResource(R.string.demo_collections_paging_next_result, outcomeLabel),
            color = TextDefaults.secondaryColor(),
            modifier = Modifier
                .fillMaxWidth()
                .scenarioPagingTarget(scenario, DemoAutomationRole.SecondaryTarget),
        )
        Button(
            text = pagingPrimaryActionLabel(primaryAction),
            enabled = primaryAction != PagingDemoPrimaryAction.None,
            onClick = {
                when (primaryAction) {
                    PagingDemoPrimaryAction.Resolve -> fixture.resolveNext(outcomeState.value)
                    PagingDemoPrimaryAction.Retry -> items.retry()
                    PagingDemoPrimaryAction.Append -> items[items.itemCount - 1]
                    PagingDemoPrimaryAction.None -> Unit
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .scenarioPagingTarget(scenario, DemoAutomationRole.PrimaryAction),
        )
        Row(
            spacing = 8.dp,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Button(
                text = stringResource(R.string.demo_collections_paging_cycle_result),
                variant = ButtonVariant.Outlined,
                onClick = { outcomeState.value = outcomeState.value.next() },
                modifier = Modifier
                    .weight(1f)
                    .scenarioPagingTarget(scenario, DemoAutomationRole.SecondaryAction),
            )
            Button(
                text = stringResource(R.string.demo_collections_paging_reset),
                variant = ButtonVariant.Outlined,
                onClick = {
                    outcomeState.value = DemoPagingOutcome.Data
                    generationState.value += 1
                },
                modifier = Modifier
                    .weight(1f)
                    .scenarioPagingTarget(scenario, DemoAutomationRole.Reset),
            )
        }
        Surface(
            variant = SurfaceVariant.Variant,
            modifier = Modifier
                .fillMaxWidth()
                .height(300.dp)
                .scenarioPagingTarget(scenario, DemoAutomationRole.Target),
        ) {
            when (contentState) {
                PagingContentState.InitialLoading -> PagingMessage(
                    stringResource(R.string.demo_collections_paging_initial_loading),
                )
                is PagingContentState.InitialError -> PagingMessage(
                    stringResource(
                        R.string.demo_collections_paging_initial_error,
                        contentState.error.message.orEmpty(),
                    ),
                )
                PagingContentState.Empty -> PagingMessage(
                    stringResource(R.string.demo_collections_paging_empty),
                )
                PagingContentState.Content -> key("paging-demo-generation-${generationState.value}") {
                    PagingLazyColumn(
                        items = items,
                        key = DemoPagingRow::id,
                        contentType = { "demo-paging-row" },
                        contentRevision = DemoPagingRow::label,
                        contentPadding = 8.dp,
                        spacing = 8.dp,
                        prefetchPolicy = LazyLayoutPrefetchPolicy(
                            nestedInitialPrefetchItemCount = 0,
                            itemViewCacheSize = 0,
                        ),
                        modifier = Modifier.fillMaxSize(),
                    ) { row ->
                        Surface(
                            variant = SurfaceVariant.Default,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(
                                spacing = 4.dp,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                            ) {
                                Text(text = row.label)
                                Text(
                                    text = stringResource(
                                        R.string.demo_collections_paging_row_identity,
                                        row.id,
                                    ),
                                    style = UiTextStyle(fontSizeSp = 12.sp),
                                    color = TextDefaults.secondaryColor(),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private enum class PagingDemoPrimaryAction {
    Resolve,
    Retry,
    Append,
    None,
}

private fun pagingPrimaryAction(
    contentState: PagingContentState,
    refresh: PagingLoadStateSnapshot,
    append: PagingLoadStateSnapshot,
): PagingDemoPrimaryAction = when {
    refresh.hasLoading() || append.hasLoading() -> PagingDemoPrimaryAction.Resolve
    refresh.hasError() || append.hasError() -> PagingDemoPrimaryAction.Retry
    contentState == PagingContentState.Content &&
        (append.combined as? LoadState.NotLoading)?.endOfPaginationReached == false ->
        PagingDemoPrimaryAction.Append
    else -> PagingDemoPrimaryAction.None
}

private fun PagingLoadStateSnapshot.hasLoading(): Boolean =
    combined is LoadState.Loading || source is LoadState.Loading || mediator is LoadState.Loading

private fun PagingLoadStateSnapshot.hasError(): Boolean =
    combined is LoadState.Error || source is LoadState.Error || mediator is LoadState.Error

private fun UiTreeBuilder.pagingPrimaryActionLabel(action: PagingDemoPrimaryAction): String =
    stringResource(
        when (action) {
            PagingDemoPrimaryAction.Resolve -> R.string.demo_collections_paging_resolve_load
            PagingDemoPrimaryAction.Retry -> R.string.demo_collections_paging_retry_load
            PagingDemoPrimaryAction.Append -> R.string.demo_collections_paging_request_append
            PagingDemoPrimaryAction.None -> R.string.demo_collections_paging_no_action
        },
    )

private fun UiTreeBuilder.pagingOutcomeLabel(outcome: DemoPagingOutcome): String =
    stringResource(
        when (outcome) {
            DemoPagingOutcome.Data -> R.string.demo_collections_paging_outcome_data
            DemoPagingOutcome.Empty -> R.string.demo_collections_paging_outcome_empty
            DemoPagingOutcome.Error -> R.string.demo_collections_paging_outcome_error
        },
    )

private fun UiTreeBuilder.pagingBodyLabel(state: PagingContentState): String =
    stringResource(
        when (state) {
            PagingContentState.InitialLoading -> R.string.demo_collections_paging_body_initial_loading
            is PagingContentState.InitialError -> R.string.demo_collections_paging_body_initial_error
            PagingContentState.Empty -> R.string.demo_collections_paging_body_empty
            PagingContentState.Content -> R.string.demo_collections_paging_body_content
        },
    )

private fun UiTreeBuilder.pagingLoadStateLabel(state: LoadState): String =
    stringResource(
        when (state) {
            LoadState.Loading -> R.string.demo_collections_paging_load_loading
            is LoadState.Error -> R.string.demo_collections_paging_load_error
            is LoadState.NotLoading -> if (state.endOfPaginationReached) {
                R.string.demo_collections_paging_load_end
            } else {
                R.string.demo_collections_paging_load_complete
            }
        },
    )

private fun UiTreeBuilder.PagingMessage(message: String) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
    ) {
        Text(text = message)
    }
}

private fun Modifier.scenarioPagingTarget(
    scenario: DemoScenarioSpec?,
    role: DemoAutomationRole,
): Modifier = scenario?.automation?.get(role)?.let(::demoAutomationTarget) ?: this
