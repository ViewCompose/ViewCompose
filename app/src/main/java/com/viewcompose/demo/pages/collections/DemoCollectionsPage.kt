package com.viewcompose

import android.widget.TextView
import com.viewcompose.ui.layout.VerticalAlignment
import com.viewcompose.ui.modifier.Modifier
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
import com.viewcompose.widget.core.Box
import com.viewcompose.widget.core.Button
import com.viewcompose.widget.core.ButtonSize
import com.viewcompose.widget.core.ButtonVariant
import com.viewcompose.widget.core.Card
import com.viewcompose.widget.core.CardVariant
import com.viewcompose.widget.core.Column
import com.viewcompose.widget.core.Image
import com.viewcompose.widget.core.LazyColumn
import com.viewcompose.widget.core.LazyRow
import com.viewcompose.widget.core.LazyVerticalGrid
import com.viewcompose.widget.core.PullToRefresh
import com.viewcompose.widget.core.Row
import com.viewcompose.widget.core.Surface
import com.viewcompose.widget.core.SurfaceDefaults
import com.viewcompose.widget.core.SurfaceVariant
import com.viewcompose.widget.core.Text
import com.viewcompose.widget.core.TextDefaults
import com.viewcompose.widget.core.Theme
import com.viewcompose.widget.core.UiTextStyle
import com.viewcompose.widget.core.UiTreeBuilder
import com.viewcompose.widget.core.dp
import com.viewcompose.widget.core.produceState
import com.viewcompose.widget.core.remember
import com.viewcompose.widget.core.rememberLazyListState
import com.viewcompose.widget.core.sp

internal fun UiTreeBuilder.CollectionPage(
    initialPageIndex: Int = 0,
) {
    val benchmarkRotateState = remember { mutableStateOf(false) }
    val reversedState = remember { mutableStateOf(false) }
    val alternateLabelsState = remember { mutableStateOf(false) }
    val stressRotateState = remember { mutableStateOf(false) }
    val stressEdgeItemState = remember { mutableStateOf(false) }
    val selectedPageState = remember { mutableStateOf(initialPageIndex.coerceIn(0, 6)) }
    val spanCountState = remember { mutableStateOf(2) }
    val refreshingState = remember { mutableStateOf(false) }
    val refreshCountState = remember { mutableStateOf(0) }
    val listOrderState = produceState(
        initialValue = "列表顺序: A-B-C",
        reversedState.value,
    ) {
        value = if (reversedState.value) "列表顺序: C-B-A" else "列表顺序: A-B-C"
    }
    val keyedItems = if (reversedState.value) {
        listOf("C", "B", "A")
    } else {
        listOf("A", "B", "C")
    }.map { id ->
        DemoListItem(
            id = id,
            title = if (alternateLabelsState.value) {
                "Lazy 项 $id（替代）"
            } else {
                "Lazy 项 $id"
            },
        )
    }
    val horizontalItems = (1..10).map { DemoListItem(id = "$it", title = "横向卡片 $it") }
    val gridItems = (1..12).map { DemoListItem(id = "$it", title = "网格项 $it") }
    val pullItems = (1..8).map { DemoListItem(id = "$it", title = "刷新列表项 $it · 刷新 ${refreshCountState.value} 次") }

    val pageItems = when (selectedPageState.value) {
        0 -> listOf("benchmark", "page", "page_filter", "verify")
        1 -> listOf("page", "page_filter", "controls", "list", "verify")
        2 -> listOf("page", "page_filter", "stress", "verify")
        3 -> listOf("page", "page_filter", "interop", "verify")
        4 -> listOf("page", "page_filter", "lazy_row", "verify")
        5 -> listOf("page", "page_filter", "grid", "verify")
        else -> listOf("page", "page_filter", "pull_refresh", "verify")
    }

    LazyColumn(
        items = pageItems,
        key = { it },
        modifier = Modifier.fillMaxSize(),
    ) { section ->
        when (section) {
            "page" -> ChapterPageOverviewSection(
                title = "集合组件",
                goal = "验证 LazyColumn/LazyRow/LazyVerticalGrid 的键控复用、PullToRefresh 的下拉刷新、以及 AndroidView 互操作。",
                modules = listOf("LazyColumn", "LazyRow", "LazyVerticalGrid", "PullToRefresh", "diff", "lazy item sessions", "AndroidView"),
            )

            "page_filter" -> ChapterPageFilterSection(
                pages = listOf("控制", "列表", "压力", "互操作", "横向列表", "网格", "下拉刷新"),
                selectedIndex = selectedPageState.value,
                onSelectionChange = { selectedPageState.value = it },
            )

            "benchmark" -> ScenarioSection(
                kind = ScenarioKind.Benchmark,
                title = "集合组件 Benchmark 锚点",
                subtitle = "键控列表排序切换和网格 spanCount 切换的稳定路径。",
            ) {
                val benchmarkItems = if (benchmarkRotateState.value) {
                    listOf("C", "A", "B")
                } else {
                    listOf("A", "B", "C")
                }.map { id ->
                    DemoListItem(
                        id = id,
                        title = if (benchmarkRotateState.value) "Benchmark 项 $id 展开" else "Benchmark 项 $id",
                    )
                }
                Text(
                    text = "稳定路径: launcher -> collections -> benchmark anchor",
                    style = UiTextStyle(fontSizeSp = 12.sp),
                    color = TextDefaults.secondaryColor(),
                    modifier = Modifier.margin(bottom = 8.dp),
                )
                Button(
                    text = if (benchmarkRotateState.value) "Benchmark C-A-B" else "Benchmark A-B-C",
                    modifier = Modifier
                        .fillMaxWidth()
                        .margin(bottom = 8.dp)
                        .testTag(DemoTestTags.COLLECTIONS_BENCHMARK_TOGGLE),
                    onClick = { benchmarkRotateState.value = !benchmarkRotateState.value },
                )
                Button(
                    text = "重置 Benchmark",
                    variant = ButtonVariant.Outlined,
                    modifier = Modifier
                        .fillMaxWidth()
                        .margin(bottom = 8.dp)
                        .testTag(DemoTestTags.COLLECTIONS_BENCHMARK_RESET),
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
                        .cornerRadius(SurfaceDefaults.cardCornerRadius()),
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
                            Text(text = item.title)
                            Text(
                                text = "稳定 key: ${item.id}",
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
                title = "集合控制",
                subtitle = "这些按钮变更数据源和标签，同时保持键控 item 状态。",
            ) {
                Text(text = listOrderState.value)
                Row(
                    spacing = 8.dp,
                    verticalAlignment = VerticalAlignment.Center,
                    modifier = Modifier.margin(top = 12.dp),
                ) {
                    Button(
                        text = if (reversedState.value) "显示 A-B-C" else "显示 C-B-A",
                        onClick = { reversedState.value = !reversedState.value },
                    )
                    Button(
                        text = if (alternateLabelsState.value) "主要标签" else "替代标签",
                        modifier = Modifier.testTag(DemoTestTags.COLLECTIONS_LABEL_TOGGLE),
                        onClick = { alternateLabelsState.value = !alternateLabelsState.value },
                    )
                }
            }

            "list" -> ScenarioSection(
                kind = ScenarioKind.Core,
                title = "LazyColumn",
                subtitle = "完整布局状态、sticky header、contentType、预取与键控 item 状态。",
            ) {
                val listState = rememberLazyListState()
                Text(
                    text = "可见 ${listState.layoutInfo.visibleItemsInfo.map { it.index }} · " +
                        "可前滚 ${listState.canScrollForward} · 滚动中 ${listState.isScrollInProgress}",
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
                        initialPrefetchItemCount = 4,
                        itemViewCacheSize = 4,
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(280.dp),
                ) {
                    stickyHeader(
                        key = "lazy-state-header",
                        contentType = "header",
                    ) {
                        Surface(
                            variant = SurfaceVariant.Variant,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                        ) {
                            Text(
                                text = "固定标题 · ${keyedItems.size} 项",
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
                                text = "项 ${item.id} 点击: ${itemCountState.value}",
                                onClick = { itemCountState.value = itemCountState.value + 1 },
                            )
                        }
                    }
                }
            }

            "stress" -> ScenarioSection(
                kind = ScenarioKind.Stress,
                title = "Lazy 压力测试",
                subtitle = "排序、插入、标签变更和受限高度集中在一个可重复的测试路径中。",
            ) {
                val stressItems = buildList {
                    val baseIds = if (stressRotateState.value) {
                        listOf("C", "D", "A", "B")
                    } else {
                        listOf("A", "B", "C", "D")
                    }
                    if (stressEdgeItemState.value) {
                        add(DemoListItem(id = "X", title = "插入项 X"))
                    }
                    baseIds.forEach { id ->
                        add(
                            DemoListItem(
                                id = id,
                                title = if (alternateLabelsState.value) "压力项 $id（替代）" else "压力项 $id",
                            ),
                        )
                    }
                }
                BenchmarkRouteCallout(
                    route = "Catalog -> Collections -> 压力页",
                    stableTargets = listOf("Linear Order / Rotate Order", "Insert X / Remove X"),
                )
                Row(
                    spacing = 8.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .margin(bottom = 12.dp),
                ) {
                    Button(
                        text = if (stressRotateState.value) "线性顺序" else "旋转顺序",
                        size = ButtonSize.Compact,
                        modifier = Modifier.testTag(DemoTestTags.COLLECTIONS_STRESS_ROTATE),
                        onClick = { stressRotateState.value = !stressRotateState.value },
                    )
                    Button(
                        text = if (stressEdgeItemState.value) "移除 X" else "插入 X",
                        size = ButtonSize.Compact,
                        modifier = Modifier.testTag(DemoTestTags.COLLECTIONS_STRESS_EDGE),
                        onClick = { stressEdgeItemState.value = !stressEdgeItemState.value },
                    )
                }
                Text(
                    text = "当前 IDs: ${stressItems.joinToString(" -> ") { it.id }}",
                    style = UiTextStyle(fontSizeSp = 13.sp),
                    color = TextDefaults.secondaryColor(),
                    modifier = Modifier
                        .margin(bottom = 12.dp)
                        .testTag(DemoTestTags.COLLECTIONS_STRESS_ACTIVE_IDS),
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
                        .cornerRadius(SurfaceDefaults.cardCornerRadius()),
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
                                text = "稳定 key: ${item.id}",
                                style = UiTextStyle(fontSizeSp = 12.sp),
                                color = TextDefaults.secondaryColor(),
                            )
                            Button(
                                text = "项 ${item.id} 点击: ${itemCountState.value}",
                                size = ButtonSize.Compact,
                                onClick = { itemCountState.value = itemCountState.value + 1 },
                            )
                        }
                    }
                }
            }

            "interop" -> ScenarioSection(
                kind = ScenarioKind.Benchmark,
                title = "AndroidView 互操作",
                subtitle = "原生 View 插入声明式状态流。",
            ) {
                val summaryText = if (alternateLabelsState.value) {
                    "原生 TextView: 替代标签已启用"
                } else {
                    "原生 TextView: 主要标签已启用"
                }
                AndroidView(
                    key = "legacy_summary",
                    modifier = Modifier.padding(vertical = 4.dp),
                    factory = { context -> TextView(context) },
                    update = { view -> (view as TextView).text = summaryText },
                )
            }

            "lazy_row" -> ScenarioSection(
                kind = ScenarioKind.Core,
                title = "LazyRow 横向列表",
                subtitle = "LazyRow 提供横向滚动的 RecyclerView，支持 key、spacing 和 contentPadding。",
            ) {
                Text(
                    text = "图片卡片横向列表",
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
                    text = "文字标签横向列表",
                    style = UiTextStyle(fontSizeSp = 14.sp),
                    modifier = Modifier.margin(bottom = 8.dp),
                )
                LazyRow(
                    items = (1..15).map { "标签 $it" },
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
                title = "LazyVerticalGrid 网格",
                subtitle = "LazyVerticalGrid 基于 GridLayoutManager 实现，支持 spanCount 切换。",
            ) {
                Row(
                    spacing = 8.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .margin(bottom = 12.dp),
                ) {
                    Button(
                        text = "2 列",
                        variant = if (spanCountState.value == 2) ButtonVariant.Primary else ButtonVariant.Outlined,
                        onClick = { spanCountState.value = 2 },
                        modifier = Modifier
                            .weight(1f)
                            .testTag(DemoTestTags.COLLECTIONS_GRID_TWO_COLS),
                    )
                    Button(
                        text = "3 列",
                        variant = if (spanCountState.value == 3) ButtonVariant.Primary else ButtonVariant.Outlined,
                        onClick = { spanCountState.value = 3 },
                        modifier = Modifier
                            .weight(1f)
                            .testTag(DemoTestTags.COLLECTIONS_GRID_THREE_COLS),
                    )
                }
                LazyVerticalGrid(
                    items = gridItems,
                    spanCount = spanCountState.value,
                    key = { item -> item.id },
                    horizontalSpacing = 8.dp,
                    verticalSpacing = 8.dp,
                    contentPadding = 8.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(400.dp)
                        .backgroundColor(SurfaceDefaults.variantBackgroundColor())
                        .cornerRadius(SurfaceDefaults.cardCornerRadius()),
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
                                text = "${item.title} · ${spanCountState.value}列",
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
                title = "PullToRefresh 下拉刷新",
                subtitle = "PullToRefresh 包裹 ScrollableColumn，支持下拉触发刷新回调。",
            ) {
                Text(
                    text = "刷新次数: ${refreshCountState.value}",
                    style = UiTextStyle(fontSizeSp = 13.sp),
                    color = TextDefaults.secondaryColor(),
                    modifier = Modifier.margin(bottom = 8.dp),
                )
                Button(
                    text = if (refreshingState.value) "正在刷新…" else "模拟刷新",
                    onClick = {
                        refreshingState.value = true
                        refreshCountState.value += 1
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .margin(bottom = 8.dp),
                )
                Button(
                    text = "停止刷新",
                    variant = ButtonVariant.Outlined,
                    onClick = { refreshingState.value = false },
                    modifier = Modifier
                        .fillMaxWidth()
                        .margin(bottom = 12.dp),
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
                    text = "向下拉动上方区域触发刷新，或点击模拟刷新按钮。",
                    style = UiTextStyle(fontSizeSp = 13.sp),
                    color = TextDefaults.secondaryColor(),
                    modifier = Modifier.margin(top = 8.dp),
                )
            }

            else -> VerificationNotesSection(
                what = "集合组件应验证键控复用、LazyRow/LazyVerticalGrid 的渲染、PullToRefresh 的刷新流程。",
                howToVerify = listOf(
                    "对单个 item 连续点击计数，再切换顺序，确认同 key 的计数被保留。",
                    "横向滑动 LazyRow，确认滚动流畅，卡片不重叠。",
                    "切换网格列数（2列/3列），确认布局即时重排。",
                    "下拉 PullToRefresh 区域，确认刷新指示器出现。",
                    "点击停止刷新，确认指示器消失。",
                ),
                expected = listOf(
                    "键控 reorder 只移动节点，不重建 item session。",
                    "LazyRow 横向滚动和 LazyVerticalGrid 网格布局稳定。",
                    "PullToRefresh 刷新指示器跟随状态正确显隐。",
                ),
            )
        }
    }
}
