package com.viewcompose

import android.widget.TextView
import com.viewcompose.demo.automation.demoAutomationTarget
import com.viewcompose.demo.contract.DemoAutomationRole
import com.viewcompose.demo.contract.DemoScenarioId
import com.viewcompose.demo.contract.DemoScenarioSpec
import com.viewcompose.demo.registry.DemoScenarioIds
import com.viewcompose.host.android.resources.stringResource
import com.viewcompose.host.android.resources.pluralStringResource
import com.viewcompose.preview.tooling.ViewComposePreview
import com.viewcompose.ui.layout.VerticalAlignment
import com.viewcompose.ui.modifier.Modifier
import com.viewcompose.ui.modifier.shape
import com.viewcompose.ui.modifier.backgroundColor
import com.viewcompose.ui.modifier.cornerRadius
import com.viewcompose.ui.modifier.fillMaxSize
import com.viewcompose.ui.modifier.fillMaxWidth
import com.viewcompose.ui.modifier.height
import com.viewcompose.ui.modifier.margin
import com.viewcompose.ui.modifier.padding
import com.viewcompose.ui.modifier.size
import com.viewcompose.ui.modifier.testTag
import com.viewcompose.host.android.AndroidView
import com.viewcompose.ui.node.ImageSource
import com.viewcompose.ui.node.policy.LazyContentPadding
import com.viewcompose.ui.node.policy.LazyLayoutPrefetchPolicy
import com.viewcompose.runtime.mutableStateOf
import com.viewcompose.ui.foundation.Box
import com.viewcompose.ui.foundation.Button
import com.viewcompose.ui.foundation.ButtonSize
import com.viewcompose.ui.foundation.ButtonVariant
import com.viewcompose.ui.foundation.Card
import com.viewcompose.ui.foundation.CardVariant
import com.viewcompose.ui.foundation.Column
import com.viewcompose.ui.foundation.Image
import com.viewcompose.ui.foundation.LazyColumn
import com.viewcompose.ui.foundation.LazyRow
import com.viewcompose.ui.foundation.LazyVerticalGrid
import com.viewcompose.ui.foundation.PullToRefresh
import com.viewcompose.ui.foundation.Row
import com.viewcompose.ui.foundation.Surface
import com.viewcompose.ui.foundation.SurfaceDefaults
import com.viewcompose.ui.foundation.SurfaceVariant
import com.viewcompose.ui.foundation.Text
import com.viewcompose.ui.foundation.TextDefaults
import com.viewcompose.ui.foundation.Theme
import com.viewcompose.ui.foundation.UiTextStyle
import com.viewcompose.ui.foundation.UiTreeBuilder
import com.viewcompose.ui.unit.dp
import com.viewcompose.ui.foundation.produceState
import com.viewcompose.ui.foundation.remember
import com.viewcompose.ui.foundation.rememberLazyListState
import com.viewcompose.ui.unit.sp

@ViewComposePreview(name = "Collections · Controls", group = "Demo/Pages")
internal fun UiTreeBuilder.PreviewCollectionsControls() {
    CollectionPage(CollectionFixture.Controls)
}

@ViewComposePreview(name = "Collections · List", group = "Demo/Pages")
internal fun UiTreeBuilder.PreviewCollectionsList() {
    CollectionPage(CollectionFixture.LazyList)
}

@ViewComposePreview(name = "Collections · Stress", group = "Demo/Pages")
internal fun UiTreeBuilder.PreviewCollectionsStress() {
    CollectionPage(CollectionFixture.Stress)
}

@ViewComposePreview(name = "Collections · Interop", group = "Demo/Pages")
internal fun UiTreeBuilder.PreviewCollectionsInterop() {
    CollectionPage(CollectionFixture.AndroidView)
}

@ViewComposePreview(name = "Collections · Lazy row", group = "Demo/Pages")
internal fun UiTreeBuilder.PreviewCollectionsLazyRow() {
    CollectionPage(CollectionFixture.LazyRow)
}

@ViewComposePreview(name = "Collections · Grid", group = "Demo/Pages")
internal fun UiTreeBuilder.PreviewCollectionsGrid() {
    CollectionPage(CollectionFixture.Grid)
}

@ViewComposePreview(name = "Collections · Pull refresh", group = "Demo/Pages")
internal fun UiTreeBuilder.PreviewCollectionsPullRefresh() {
    CollectionPage(CollectionFixture.PullRefresh)
}

internal enum class CollectionFixture(
    val scenarioId: DemoScenarioId,
) {
    Controls(DemoScenarioIds.CollectionControls),
    LazyList(DemoScenarioIds.CollectionLazyList),
    Stress(DemoScenarioIds.CollectionStress),
    AndroidView(DemoScenarioIds.CollectionAndroidView),
    LazyRow(DemoScenarioIds.CollectionLazyRow),
    Grid(DemoScenarioIds.CollectionGrid),
    PullRefresh(DemoScenarioIds.CollectionPullRefresh),
    ;

    companion object {
        fun from(scenarioId: DemoScenarioId): CollectionFixture =
            entries.singleOrNull { fixture -> fixture.scenarioId == scenarioId }
                ?: error("Unsupported collection scenario: $scenarioId")
    }
}

internal fun UiTreeBuilder.CollectionPage(
    fixture: CollectionFixture,
    scenario: DemoScenarioSpec? = null,
) {
    val benchmarkRotateState = remember { mutableStateOf(false) }
    val reversedState = remember { mutableStateOf(false) }
    val alternateLabelsState = remember { mutableStateOf(false) }
    val stressRotateState = remember { mutableStateOf(false) }
    val stressEdgeItemState = remember { mutableStateOf(false) }
    val spanCountState = remember { mutableStateOf(2) }
    val refreshingState = remember { mutableStateOf(false) }
    val refreshCountState = remember { mutableStateOf(0) }
    val forwardOrder = stringResource(R.string.demo_collections_order, "A-B-C")
    val reverseOrder = stringResource(R.string.demo_collections_order, "C-B-A")
    val listOrderState = produceState(
        initialValue = forwardOrder,
        reversedState.value,
        forwardOrder,
        reverseOrder,
    ) {
        value = if (reversedState.value) reverseOrder else forwardOrder
    }
    val horizontalItems = (1..10).map {
        DemoListItem(id = "$it", title = stringResource(R.string.demo_collections_horizontal_card, it))
    }
    val gridItems = (1..12).map {
        DemoListItem(id = "$it", title = stringResource(R.string.demo_collections_grid_item, it))
    }
    val pullItems = (1..8).map {
        DemoListItem(
            id = "$it",
            title = pluralStringResource(
                R.plurals.demo_collections_refresh_item,
                refreshCountState.value,
                it,
                refreshCountState.value,
            ),
        )
    }

    val pageItems = when (fixture) {
        CollectionFixture.Controls -> listOf("benchmark")
        CollectionFixture.LazyList -> listOf("controls", "list")
        CollectionFixture.Stress -> listOf("stress")
        CollectionFixture.AndroidView -> listOf("interop")
        CollectionFixture.LazyRow -> listOf("lazy_row")
        CollectionFixture.Grid -> listOf("grid")
        CollectionFixture.PullRefresh -> listOf("pull_refresh")
    }

    LazyColumn(
        items = pageItems,
        key = { it },
        modifier = Modifier.fillMaxSize(),
    ) { section ->
        when (section) {
            "benchmark" -> ScenarioSection(
                kind = ScenarioKind.Benchmark,
                title = stringResource(R.string.demo_collections_benchmark_title),
                subtitle = stringResource(R.string.demo_collections_benchmark_summary),
            ) {
                val benchmarkItems = if (benchmarkRotateState.value) {
                    listOf("C", "A", "B")
                } else {
                    listOf("A", "B", "C")
                }.map { id ->
                    DemoListItem(
                        id = id,
                        title = stringResource(
                            if (benchmarkRotateState.value) {
                                R.string.demo_collections_benchmark_item_expanded
                            } else {
                                R.string.demo_collections_benchmark_item
                            },
                            id,
                        ),
                    )
                }
                Text(
                    text = stringResource(
                        R.string.demo_collections_current_order,
                        if (benchmarkRotateState.value) "C-A-B" else "A-B-C",
                    ),
                    style = UiTextStyle(fontSizeSp = 12.sp),
                    color = TextDefaults.secondaryColor(),
                    modifier = Modifier
                        .margin(bottom = 8.dp)
                        .scenarioTarget(scenario, DemoAutomationRole.State),
                )
                Button(
                    text = stringResource(
                        R.string.demo_collections_current_order,
                        if (benchmarkRotateState.value) "C-A-B" else "A-B-C",
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .margin(bottom = 8.dp)
                        .scenarioTarget(scenario, DemoAutomationRole.PrimaryAction),
                    onClick = { benchmarkRotateState.value = !benchmarkRotateState.value },
                )
                Button(
                    text = stringResource(R.string.demo_collections_benchmark_reset),
                    variant = ButtonVariant.Outlined,
                    modifier = Modifier
                        .fillMaxWidth()
                        .margin(bottom = 8.dp)
                        .scenarioTarget(scenario, DemoAutomationRole.Reset),
                    onClick = { benchmarkRotateState.value = false },
                )
                LazyColumn(
                    items = benchmarkItems,
                    key = { item -> item.id },
                    spacing = 8.dp,
                    contentPadding = 8.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                        .backgroundColor(SurfaceDefaults.variantBackgroundColor())
                        .shape(SurfaceDefaults.shape())
                        .scenarioTarget(scenario, DemoAutomationRole.Target),
                ) { item ->
                    Surface(
                        variant = SurfaceVariant.Default,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            spacing = 8.dp,
                            verticalAlignment = VerticalAlignment.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                        ) {
                            Text(
                                text = item.title,
                                modifier = if (item.id == "A") {
                                    Modifier.testTag(DemoTestTags.COLLECTIONS_BENCHMARK_ITEM_A)
                                } else {
                                    Modifier
                                },
                            )
                            Text(
                                text = stringResource(R.string.demo_collections_stable_key, item.id),
                                style = UiTextStyle(fontSizeSp = 12.sp),
                                color = TextDefaults.secondaryColor(),
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }

            "controls" -> ScenarioSection(
                kind = ScenarioKind.Guide,
                title = stringResource(R.string.demo_collections_controls_title),
                subtitle = stringResource(R.string.demo_collections_controls_summary),
            ) {
                Text(text = listOrderState.value)
                Row(
                    spacing = 8.dp,
                    verticalAlignment = VerticalAlignment.Center,
                    modifier = Modifier.margin(top = 12.dp),
                ) {
                    Button(
                        text = stringResource(
                            R.string.demo_collections_show_order,
                            if (reversedState.value) "A-B-C" else "C-B-A",
                        ),
                        onClick = { reversedState.value = !reversedState.value },
                    )
                    Button(
                        text = stringResource(
                            if (alternateLabelsState.value) {
                                R.string.demo_collections_primary_labels
                            } else {
                                R.string.demo_collections_alternate_labels
                            },
                        ),
                        modifier = Modifier.testTag(DemoTestTags.COLLECTIONS_LABEL_TOGGLE),
                        onClick = { alternateLabelsState.value = !alternateLabelsState.value },
                    )
                }
                Button(
                    text = stringResource(R.string.demo_collections_reset_list),
                    variant = ButtonVariant.Outlined,
                    modifier = Modifier
                        .fillMaxWidth()
                        .margin(top = 8.dp)
                        .scenarioTarget(scenario, DemoAutomationRole.Reset),
                    onClick = {
                        reversedState.value = false
                        alternateLabelsState.value = false
                    },
                )
            }

            "list" -> ScenarioSection(
                kind = ScenarioKind.Core,
                title = stringResource(R.string.demo_collections_lazy_list_title),
                subtitle = stringResource(R.string.demo_collections_lazy_list_summary),
            ) {
                val keyedItems = if (reversedState.value) {
                    listOf("C", "B", "A")
                } else {
                    listOf("A", "B", "C")
                }.map { id ->
                    DemoListItem(
                        id = id,
                        title = if (alternateLabelsState.value) {
                            stringResource(R.string.demo_collections_lazy_item_alternate, id)
                        } else {
                            stringResource(R.string.demo_collections_lazy_item, id)
                        },
                    )
                }
                val listState = rememberLazyListState()
                Text(
                    text = stringResource(
                        R.string.demo_collections_layout_info,
                        listState.layoutInfo.visibleItemsInfo.map { it.index }.toString(),
                        listState.canScrollForward,
                        listState.isScrollInProgress,
                    ),
                    style = UiTextStyle(fontSizeSp = 12.sp),
                    color = TextDefaults.secondaryColor(),
                    modifier = Modifier.margin(bottom = 8.dp),
                )
                LazyColumn(
                    state = listState,
                    contentPadding = LazyContentPadding.symmetric(
                        horizontal = 8.dp,
                        vertical = 4.dp,
                    ),
                    prefetchPolicy = LazyLayoutPrefetchPolicy(
                        nestedInitialPrefetchItemCount = 4,
                        itemViewCacheSize = 4,
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(280.dp),
                ) {
                    stickyHeader(
                        key = "lazy-state-header",
                        contentType = "header",
                        contentRevision = keyedItems.size,
                    ) {
                        Surface(
                            variant = SurfaceVariant.Variant,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                        ) {
                            Text(
                                text = pluralStringResource(
                                    R.plurals.demo_collections_sticky_header,
                                    keyedItems.size,
                                    keyedItems.size,
                                ),
                                style = UiTextStyle(fontSizeSp = 13.sp),
                            )
                        }
                    }
                    items(
                        items = keyedItems,
                        key = { item -> item.id },
                        contentType = { "stateful-row" },
                    ) { item ->
                        val itemCountState = remember { mutableStateOf(0) }
                        Column(
                            key = item.id,
                            spacing = 6.dp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .backgroundColor(SurfaceDefaults.backgroundColor())
                                .padding(12.dp),
                        ) {
                            val titleModifier = if (item.id == "A") {
                                Modifier.testTag(DemoTestTags.COLLECTIONS_LIST_ITEM_A)
                            } else {
                                Modifier
                            }
                            Text(
                                text = item.title,
                                modifier = titleModifier,
                            )
                            Button(
                                text = stringResource(
                                    R.string.demo_collections_item_click_count,
                                    item.id,
                                    itemCountState.value,
                                ),
                                onClick = { itemCountState.value = itemCountState.value + 1 },
                            )
                        }
                    }
                }
            }

            "stress" -> ScenarioSection(
                kind = ScenarioKind.Stress,
                title = stringResource(R.string.demo_collections_stress_title),
                subtitle = stringResource(R.string.demo_collections_stress_summary),
            ) {
                val stressItems = buildList {
                    val baseIds = if (stressRotateState.value) {
                        listOf("C", "D", "A", "B")
                    } else {
                        listOf("A", "B", "C", "D")
                    }
                    if (stressEdgeItemState.value) {
                        add(
                            DemoListItem(
                                id = "X",
                                title = stringResource(R.string.demo_collections_inserted_item),
                            ),
                        )
                    }
                    baseIds.forEach { id ->
                        add(
                            DemoListItem(
                                id = id,
                                title = stringResource(
                                    if (alternateLabelsState.value) {
                                        R.string.demo_collections_stress_item_alternate
                                    } else {
                                        R.string.demo_collections_stress_item
                                    },
                                    id,
                                ),
                            ),
                        )
                    }
                }
                Row(
                    spacing = 8.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .margin(bottom = 12.dp),
                ) {
                    Button(
                        text = stringResource(
                            if (stressRotateState.value) {
                                R.string.demo_collections_linear_order
                            } else {
                                R.string.demo_collections_rotated_order
                            },
                        ),
                        size = ButtonSize.Compact,
                        modifier = Modifier
                            .scenarioTarget(scenario, DemoAutomationRole.PrimaryAction),
                        onClick = { stressRotateState.value = !stressRotateState.value },
                    )
                    Button(
                        text = stringResource(
                            if (stressEdgeItemState.value) {
                                R.string.demo_collections_remove_x
                            } else {
                                R.string.demo_collections_insert_x
                            },
                        ),
                        size = ButtonSize.Compact,
                        modifier = Modifier
                            .scenarioTarget(scenario, DemoAutomationRole.SecondaryAction),
                        onClick = { stressEdgeItemState.value = !stressEdgeItemState.value },
                    )
                }
                Text(
                    text = stringResource(
                        R.string.demo_collections_active_ids,
                        stressItems.joinToString(" -> ") { it.id },
                    ),
                    style = UiTextStyle(fontSizeSp = 13.sp),
                    color = TextDefaults.secondaryColor(),
                    modifier = Modifier
                        .margin(bottom = 12.dp)
                        .scenarioTarget(scenario, DemoAutomationRole.State),
                )
                Button(
                    text = stringResource(R.string.demo_collections_reset_stress),
                    variant = ButtonVariant.Outlined,
                    modifier = Modifier
                        .fillMaxWidth()
                        .margin(bottom = 12.dp)
                        .scenarioTarget(scenario, DemoAutomationRole.Reset),
                    onClick = {
                        stressRotateState.value = false
                        stressEdgeItemState.value = false
                        alternateLabelsState.value = false
                    },
                )
                LazyColumn(
                    items = stressItems,
                    key = { item -> item.id },
                    spacing = 8.dp,
                    contentPadding = 8.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(320.dp)
                        .backgroundColor(SurfaceDefaults.variantBackgroundColor())
                        .shape(SurfaceDefaults.shape())
                        .scenarioTarget(scenario, DemoAutomationRole.Target),
                ) { item ->
                    val itemCountState = remember { mutableStateOf(0) }
                    Surface(
                        variant = SurfaceVariant.Default,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(
                            key = item.id,
                            spacing = 6.dp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                        ) {
                            Text(text = item.title)
                            Text(
                                text = stringResource(R.string.demo_collections_stable_key, item.id),
                                style = UiTextStyle(fontSizeSp = 12.sp),
                                color = TextDefaults.secondaryColor(),
                            )
                            Button(
                                text = stringResource(
                                    R.string.demo_collections_item_click_count,
                                    item.id,
                                    itemCountState.value,
                                ),
                                size = ButtonSize.Compact,
                                onClick = { itemCountState.value = itemCountState.value + 1 },
                            )
                        }
                    }
                }
            }

            "interop" -> ScenarioSection(
                kind = ScenarioKind.Benchmark,
                title = stringResource(R.string.demo_collections_android_view_title),
                subtitle = stringResource(R.string.demo_collections_android_view_summary),
            ) {
                val summaryText = stringResource(R.string.demo_collections_android_view_bound)
                AndroidView(
                    key = "legacy_summary",
                    modifier = Modifier.padding(vertical = 4.dp),
                    factory = { context -> TextView(context) },
                    update = { view -> (view as TextView).text = summaryText },
                )
            }

            "lazy_row" -> ScenarioSection(
                kind = ScenarioKind.Core,
                title = stringResource(R.string.demo_collections_lazy_row_title),
                subtitle = stringResource(R.string.demo_collections_lazy_row_summary),
            ) {
                Text(
                    text = stringResource(R.string.demo_collections_horizontal_cards),
                    style = UiTextStyle(fontSizeSp = 14.sp),
                    modifier = Modifier.margin(bottom = 8.dp),
                )
                LazyRow(
                    items = horizontalItems,
                    key = { item -> item.id },
                    spacing = 12.dp,
                    contentPadding = 8.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp)
                        .margin(bottom = 16.dp)
                        .testTag(DemoTestTags.COLLECTIONS_LAZY_ROW_PRIMARY),
                ) { item ->
                    Card(
                        variant = CardVariant.Outlined,
                        modifier = Modifier.size(120.dp, 120.dp),
                    ) {
                        Column(
                            spacing = 4.dp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(60.dp)
                                    .backgroundColor(Theme.colors.surfaceVariant)
                                    .cornerRadius(8.dp),
                            ) {}
                            Text(
                                text = item.title,
                                style = UiTextStyle(fontSizeSp = 12.sp),
                            )
                        }
                    }
                }
                Text(
                    text = stringResource(R.string.demo_collections_horizontal_labels),
                    style = UiTextStyle(fontSizeSp = 14.sp),
                    modifier = Modifier.margin(bottom = 8.dp),
                )
                LazyRow(
                    items = (1..15).map { stringResource(R.string.demo_collections_label, it) },
                    key = { it },
                    spacing = 8.dp,
                    contentPadding = 4.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                ) { label ->
                    Surface(
                        variant = SurfaceVariant.Variant,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    ) {
                        Text(text = label)
                    }
                }
            }

            "grid" -> ScenarioSection(
                kind = ScenarioKind.Core,
                title = stringResource(R.string.demo_collections_grid_title),
                subtitle = stringResource(R.string.demo_collections_grid_summary),
            ) {
                Row(
                    spacing = 8.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .margin(bottom = 12.dp),
                ) {
                    Button(
                        text = stringResource(R.string.demo_collections_columns, 2),
                        variant = if (spanCountState.value == 2) ButtonVariant.Primary else ButtonVariant.Outlined,
                        onClick = { spanCountState.value = 2 },
                        modifier = Modifier
                            .weight(1f)
                            .testTag(DemoTestTags.COLLECTIONS_GRID_TWO_COLS),
                    )
                    Button(
                        text = stringResource(R.string.demo_collections_columns, 3),
                        variant = if (spanCountState.value == 3) ButtonVariant.Primary else ButtonVariant.Outlined,
                        onClick = { spanCountState.value = 3 },
                        modifier = Modifier
                            .weight(1f)
                            .testTag(DemoTestTags.COLLECTIONS_GRID_THREE_COLS),
                    )
                }
                Button(
                    text = stringResource(R.string.demo_collections_reset_columns),
                    variant = ButtonVariant.Outlined,
                    onClick = { spanCountState.value = 2 },
                    modifier = Modifier
                        .fillMaxWidth()
                        .margin(bottom = 12.dp)
                        .scenarioTarget(scenario, DemoAutomationRole.Reset),
                )
                LazyVerticalGrid(
                    items = gridItems,
                    cells = com.viewcompose.ui.node.policy.GridCells.Fixed(spanCountState.value),
                    key = { item -> item.id },
                    horizontalSpacing = 8.dp,
                    verticalSpacing = 8.dp,
                    contentPadding = 8.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(400.dp)
                        .backgroundColor(SurfaceDefaults.variantBackgroundColor())
                        .shape(SurfaceDefaults.shape()),
                ) { item ->
                    Card(
                        variant = CardVariant.Filled,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(
                            spacing = 4.dp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(60.dp)
                                    .backgroundColor(Theme.colors.surfaceVariant)
                                    .cornerRadius(8.dp),
                            ) {}
                            Text(
                                text = stringResource(
                                    R.string.demo_collections_grid_item_span,
                                    item.title,
                                    spanCountState.value,
                                ),
                                style = UiTextStyle(fontSizeSp = 13.sp),
                                modifier = if (item.id == "1") {
                                    Modifier.testTag(DemoTestTags.COLLECTIONS_GRID_FIRST_ITEM)
                                } else {
                                    Modifier
                                },
                            )
                        }
                    }
                }
            }

            "pull_refresh" -> ScenarioSection(
                kind = ScenarioKind.Core,
                title = stringResource(R.string.demo_collections_pull_refresh_title),
                subtitle = stringResource(R.string.demo_collections_pull_refresh_summary),
            ) {
                Text(
                    text = pluralStringResource(
                        R.plurals.demo_collections_refresh_count,
                        refreshCountState.value,
                        refreshCountState.value,
                    ),
                    style = UiTextStyle(fontSizeSp = 13.sp),
                    color = TextDefaults.secondaryColor(),
                    modifier = Modifier.margin(bottom = 8.dp),
                )
                Button(
                    text = stringResource(
                        if (refreshingState.value) {
                            R.string.demo_collections_refreshing
                        } else {
                            R.string.demo_collections_simulate_refresh
                        },
                    ),
                    onClick = {
                        refreshingState.value = true
                        refreshCountState.value += 1
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .margin(bottom = 8.dp),
                )
                Button(
                    text = stringResource(R.string.demo_collections_reset_refresh),
                    variant = ButtonVariant.Outlined,
                    onClick = {
                        refreshingState.value = false
                        refreshCountState.value = 0
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .margin(bottom = 12.dp)
                        .scenarioTarget(scenario, DemoAutomationRole.Reset),
                )
                PullToRefresh(
                    isRefreshing = refreshingState.value,
                    onRefresh = {
                        refreshingState.value = true
                        refreshCountState.value += 1
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(300.dp),
                ) {
                    ScrollableColumn(
                        spacing = 8.dp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp),
                    ) {
                        pullItems.forEach { item ->
                            Surface(
                                variant = SurfaceVariant.Default,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                            ) {
                                Text(text = item.title)
                            }
                        }
                    }
                }
                Text(
                    text = stringResource(R.string.demo_collections_pull_refresh_hint),
                    style = UiTextStyle(fontSizeSp = 13.sp),
                    color = TextDefaults.secondaryColor(),
                    modifier = Modifier.margin(top = 8.dp),
                )
            }

            else -> error("Unknown collection section: $section")
        }
    }
}

private fun Modifier.scenarioTarget(
    scenario: DemoScenarioSpec?,
    role: DemoAutomationRole,
): Modifier = scenario?.automation?.get(role)?.let(::demoAutomationTarget) ?: this
