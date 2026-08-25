package com.viewcompose.performance

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.viewcompose.R
import com.viewcompose.demo.automation.demoAutomationTarget
import com.viewcompose.demo.contract.DemoAutomationRole
import com.viewcompose.demo.contract.DemoScenarioSpec
import com.viewcompose.host.android.resources.stringResource
import com.viewcompose.paging.PagingLazyColumn
import com.viewcompose.paging.PagingLifecyclePolicy
import com.viewcompose.paging.collectAsViewComposePagingItems
import com.viewcompose.runtime.mutableStateOf
import com.viewcompose.ui.foundation.Column
import com.viewcompose.ui.foundation.LaunchedEffect
import com.viewcompose.ui.foundation.Row
import com.viewcompose.ui.foundation.Surface
import com.viewcompose.ui.foundation.Text
import com.viewcompose.ui.foundation.TextDefaults
import com.viewcompose.ui.foundation.UiTreeBuilder
import com.viewcompose.ui.foundation.remember
import com.viewcompose.ui.foundation.rememberLazyListState
import com.viewcompose.ui.modifier.Modifier
import com.viewcompose.ui.modifier.backgroundColor
import com.viewcompose.ui.modifier.cornerRadius
import com.viewcompose.ui.modifier.fillMaxSize
import com.viewcompose.ui.modifier.fillMaxWidth
import com.viewcompose.ui.modifier.height
import com.viewcompose.ui.modifier.padding
import com.viewcompose.ui.unit.dp
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flatMapLatest

/** One immutable row in the fixed Paging performance workload. */
internal data class PagingPerformanceRow(
    val query: Int,
    val position: Int,
) {
    val stableId: Long = query.toLong() * PERFORMANCE_PAGING_TOTAL_ITEMS + position
}

private data class PagingPerformanceQuery(
    val query: Int,
    val initialKey: Int,
)

/** Immediate local source used to isolate Paging presentation and renderer work from I/O. */
internal class PagingPerformanceSource(
    private val query: Int,
) : PagingSource<Int, PagingPerformanceRow>() {
    override val jumpingSupported: Boolean = true

    override suspend fun load(
        params: LoadParams<Int>,
    ): LoadResult<Int, PagingPerformanceRow> {
        val start = (params.key ?: 0).coerceIn(0, PERFORMANCE_PAGING_TOTAL_ITEMS - 1)
        val endExclusive = (start + params.loadSize).coerceAtMost(PERFORMANCE_PAGING_TOTAL_ITEMS)
        return LoadResult.Page(
            data = (start until endExclusive).map { position ->
                PagingPerformanceRow(query = query, position = position)
            },
            prevKey = start.takeIf { it > 0 }
                ?.let { previous -> (previous - PERFORMANCE_PAGING_PAGE_SIZE).coerceAtLeast(0) },
            nextKey = endExclusive.takeIf { it < PERFORMANCE_PAGING_TOTAL_ITEMS },
            itemsBefore = start,
            itemsAfter = PERFORMANCE_PAGING_TOTAL_ITEMS - endExclusive,
        )
    }

    override fun getRefreshKey(
        state: PagingState<Int, PagingPerformanceRow>,
    ): Int? = state.anchorPosition?.let { anchor ->
        (anchor - state.config.initialLoadSize / 2)
            .coerceIn(0, PERFORMANCE_PAGING_TOTAL_ITEMS - 1)
    }
}

/**
 * Fixed one-million-position Paging workload for append/drop, query-replacement, and scroll runs.
 */
@OptIn(ExperimentalCoroutinesApi::class)
internal fun UiTreeBuilder.ViewComposePagingPerformanceScreen(
    scenario: DemoScenarioSpec,
) {
    val queryState = remember { mutableStateOf(0) }
    val targetState = remember { mutableStateOf(0) }
    val pendingAnchorState = remember { mutableStateOf<Int?>(null) }
    val queryRequests = remember {
        MutableStateFlow(PagingPerformanceQuery(query = 0, initialKey = 0))
    }
    val listState = rememberLazyListState()
    val query = queryState.value
    val target = targetState.value
    val pagingFlow = remember {
        queryRequests.flatMapLatest { request ->
            Pager(
                config = PagingConfig(
                    pageSize = PERFORMANCE_PAGING_PAGE_SIZE,
                    prefetchDistance = PERFORMANCE_PAGING_PREFETCH_DISTANCE,
                    enablePlaceholders = true,
                    initialLoadSize = PERFORMANCE_PAGING_PAGE_SIZE,
                    maxSize = PERFORMANCE_PAGING_MAX_LOADED_ITEMS,
                    jumpThreshold = PERFORMANCE_PAGING_JUMP_THRESHOLD,
                ),
                initialKey = request.initialKey,
                pagingSourceFactory = { PagingPerformanceSource(request.query) },
            ).flow
        }
    }
    val items = pagingFlow.collectAsViewComposePagingItems(
        lifecyclePolicy = PagingLifecyclePolicy.Visible,
    )
    val targetReady = target < items.itemCount && items.peek(target)?.query == query
    val pendingAnchor = pendingAnchorState.value
    if (pendingAnchor != null && pendingAnchor < items.itemCount) {
        // A generation replacement can dispatch several adapter updates while RecyclerView retains
        // its old key anchor. Debounce those updates, then confirm the requested anchor exactly once.
        LaunchedEffect(pendingAnchor, items.loadedItemCount, targetReady) {
            delay(PERFORMANCE_PAGING_ANCHOR_SETTLE_MILLIS)
            listState.scrollToItem(pendingAnchor)
            delay(PERFORMANCE_PAGING_ANCHOR_CONFIRM_MILLIS)
            if (targetReady && listState.firstVisibleItemIndex == pendingAnchor) {
                pendingAnchorState.value = null
            }
        }
    }
    val automationReady = targetReady && pendingAnchor == null

    Column(
        modifier = Modifier
            .fillMaxSize()
            .backgroundColor(PERFORMANCE_BACKGROUND_COLOR)
            .pagingScenarioTarget(scenario, DemoAutomationRole.Root),
    ) {
        Column(
            spacing = 8.dp,
            modifier = Modifier
                .fillMaxWidth()
                .backgroundColor(PERFORMANCE_SURFACE_COLOR)
                .padding(12.dp),
        ) {
            if (automationReady) {
                Text(
                    text = stringResource(R.string.demo_performance_paging_ready),
                    style = TextDefaults.titleMediumStyle(),
                    color = PERFORMANCE_PRIMARY_TEXT_COLOR,
                    modifier = Modifier.pagingScenarioTarget(
                        scenario,
                        DemoAutomationRole.Ready,
                    ),
                )
            }
            Text(
                text = stringResource(
                    R.string.demo_performance_paging_state,
                    query,
                    target,
                    if (automationReady) 1 else 0,
                ),
                color = PERFORMANCE_SECONDARY_TEXT_COLOR,
                modifier = Modifier.pagingScenarioTarget(scenario, DemoAutomationRole.State),
            )
            Text(
                text = stringResource(
                    R.string.demo_performance_paging_loaded_count,
                    items.loadedItemCount,
                    PERFORMANCE_PAGING_MAX_LOADED_ITEMS,
                ),
                color = PERFORMANCE_SECONDARY_TEXT_COLOR,
                modifier = Modifier.pagingScenarioTarget(
                    scenario,
                    DemoAutomationRole.SecondaryTarget,
                ),
            )
            Row(spacing = 8.dp) {
                PagingPerformanceAction(
                    text = stringResource(R.string.demo_performance_paging_advance),
                    onClick = {
                        val nextTarget = (target + PERFORMANCE_PAGING_PAGE_SIZE)
                            .coerceAtMost(PERFORMANCE_PAGING_TOTAL_ITEMS - 1)
                        targetState.value = nextTarget
                        listState.scrollToItem(nextTarget)
                    },
                    modifier = Modifier.pagingScenarioTarget(
                        scenario,
                        DemoAutomationRole.PrimaryAction,
                    ),
                )
                PagingPerformanceAction(
                    text = stringResource(R.string.demo_performance_paging_replace_query),
                    onClick = {
                        val nextQuery = (query + 1) % PERFORMANCE_PAGING_QUERY_COUNT
                        queryState.value = nextQuery
                        pendingAnchorState.value = target
                        queryRequests.value = PagingPerformanceQuery(
                            query = nextQuery,
                            initialKey = target,
                        )
                    },
                    modifier = Modifier.pagingScenarioTarget(
                        scenario,
                        DemoAutomationRole.SecondaryAction,
                    ),
                )
                PagingPerformanceAction(
                    text = stringResource(R.string.demo_performance_paging_reset),
                    onClick = {
                        queryState.value = 0
                        targetState.value = 0
                        pendingAnchorState.value = 0
                        queryRequests.value = PagingPerformanceQuery(query = 0, initialKey = 0)
                        listState.scrollToItem(0)
                    },
                    modifier = Modifier.pagingScenarioTarget(
                        scenario,
                        DemoAutomationRole.Reset,
                    ),
                )
            }
        }
        PagingLazyColumn(
            items = items,
            key = PagingPerformanceRow::stableId,
            contentType = { PERFORMANCE_PAGING_CONTENT_TYPE },
            contentRevision = PagingPerformanceRow::stableId,
            placeholderContentRevision = PERFORMANCE_PAGING_PLACEHOLDER_REVISION,
            placeholderContent = { index ->
                Text(
                    text = stringResource(R.string.demo_performance_paging_placeholder, index),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(PERFORMANCE_PAGING_ITEM_HEIGHT)
                        .padding(horizontal = 12.dp),
                )
            },
            placeholderContentType = PERFORMANCE_PAGING_CONTENT_TYPE,
            state = listState,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .pagingScenarioTarget(scenario, DemoAutomationRole.Target),
        ) { row ->
            Surface(
                key = row.stableId,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(PERFORMANCE_PAGING_ITEM_HEIGHT)
                    .backgroundColor(PERFORMANCE_SURFACE_COLOR)
                    .padding(horizontal = 12.dp),
            ) {
                Text(
                    text = stringResource(
                        R.string.demo_performance_paging_row,
                        row.query,
                        row.position,
                    ),
                    color = PERFORMANCE_PRIMARY_TEXT_COLOR,
                )
            }
        }
    }
}

private fun UiTreeBuilder.PagingPerformanceAction(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier,
) {
    Surface(
        onClick = onClick,
        contentColor = 0xFFFFFFFF.toInt(),
        modifier = modifier
            .backgroundColor(PERFORMANCE_PRIMARY_COLOR)
            .cornerRadius(8.dp)
            .padding(horizontal = 10.dp, vertical = 8.dp),
    ) {
        Text(text = text, color = 0xFFFFFFFF.toInt())
    }
}

private fun Modifier.pagingScenarioTarget(
    scenario: DemoScenarioSpec,
    role: DemoAutomationRole,
): Modifier = demoAutomationTarget(scenario.automation.require(role))

internal const val PERFORMANCE_PAGING_TOTAL_ITEMS = 1_000_000
internal const val PERFORMANCE_PAGING_PAGE_SIZE = 32
internal const val PERFORMANCE_PAGING_PREFETCH_DISTANCE = 2
internal const val PERFORMANCE_PAGING_MAX_LOADED_ITEMS = 96
internal const val PERFORMANCE_PAGING_JUMP_THRESHOLD = 64
private const val PERFORMANCE_PAGING_QUERY_COUNT = 2
private const val PERFORMANCE_PAGING_ANCHOR_SETTLE_MILLIS = 32L
private const val PERFORMANCE_PAGING_ANCHOR_CONFIRM_MILLIS = 16L
private const val PERFORMANCE_PAGING_CONTENT_TYPE = "performance-paging-row"
private const val PERFORMANCE_PAGING_PLACEHOLDER_REVISION = "performance-paging-placeholder-v1"
private val PERFORMANCE_PAGING_ITEM_HEIGHT = 48.dp
