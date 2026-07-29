package com.viewcompose

import com.viewcompose.ui.modifier.Modifier
import com.viewcompose.ui.modifier.Visibility
import com.viewcompose.ui.modifier.backgroundColor
import com.viewcompose.ui.modifier.fillMaxSize
import com.viewcompose.ui.modifier.fillMaxWidth
import com.viewcompose.ui.modifier.height
import com.viewcompose.ui.modifier.margin
import com.viewcompose.ui.modifier.padding
import com.viewcompose.ui.modifier.testTag
import com.viewcompose.ui.modifier.visibility
import com.viewcompose.ui.layout.BoxAlignment
import com.viewcompose.ui.layout.MainAxisArrangement
import com.viewcompose.ui.layout.VerticalAlignment
import com.viewcompose.ui.node.ImageSource
import com.viewcompose.runtime.derivedStateOf
import com.viewcompose.runtime.mutableStateOf
import com.viewcompose.lifecycle.collectAsStateWithLifecycle
import com.viewcompose.widget.core.Box
import com.viewcompose.widget.core.Button
import com.viewcompose.widget.core.Column
import com.viewcompose.widget.core.Image
import com.viewcompose.widget.core.HorizontalPager
import com.viewcompose.widget.core.LazyColumn
import com.viewcompose.widget.core.Row
import com.viewcompose.widget.core.SegmentedControl
import com.viewcompose.widget.core.SurfaceDefaults
import com.viewcompose.widget.core.TabRow
import com.viewcompose.widget.core.Text
import com.viewcompose.widget.core.TextField
import com.viewcompose.widget.core.TextDefaults
import com.viewcompose.widget.core.UiTextStyle
import com.viewcompose.widget.core.UiTreeBuilder
import com.viewcompose.widget.core.VerticalPager
import com.viewcompose.ui.unit.dp
import com.viewcompose.widget.core.key
import com.viewcompose.widget.core.produceState
import com.viewcompose.widget.core.remember
import com.viewcompose.widget.core.rememberTextFieldState
import com.viewcompose.ui.unit.sp
import com.viewcompose.viewmodel.savedStateHandle

internal fun UiTreeBuilder.StatePage(
    initialPageIndex: Int = 0,
    onOpenDiagnostics: () -> Unit,
) {
    val benchmarkStepState = remember { mutableStateOf(0) }
    val clickCountState = remember { mutableStateOf(0) }
    val panelVisibleState = remember { mutableStateOf(true) }
    val selectedPageState = remember { mutableStateOf(initialPageIndex.coerceIn(0, 3)) }
    val patchStepState = remember { mutableStateOf(0) }
    val patchFieldValueState = rememberTextFieldState("value-0")
    val patchSegmentIndexState = remember { mutableStateOf(0) }
    val patchTabIndexState = remember { mutableStateOf(0) }
    val stableTabIndexState = remember { mutableStateOf(0) }
    val stableVerticalPagerIndexState = remember { mutableStateOf(0) }
    val summaryState = remember {
        derivedStateOf {
            val value = clickCountState.value
            when {
                value == 0 -> "尚无点击"
                value % 2 == 0 -> "偶数次点击: $value"
                else -> "奇数次点击: $value"
            }
        }
    }
    val timelineState = produceState(
        initialValue = "最近更新: 等待中",
        clickCountState.value,
    ) {
        value = "最近更新: 已提交 ${clickCountState.value} 次点击"
    }
    val vmStateHandle = savedStateHandle(key = "state_page_vm_counter")
    val vmCounterState = vmStateHandle
        .getStateFlow("counter", 0)
        .collectAsStateWithLifecycle()
    val pageItems = when (selectedPageState.value) {
        0 -> listOf("benchmark", "page", "page_filter", "counter", "viewmodel", "verify")
        1 -> listOf("page", "page_filter", "panel", "verify")
        2 -> listOf("page", "page_filter", "patch", "verify")
        else -> listOf("page", "page_filter", "verify")
    }

    LazyColumn(
        items = pageItems,
        key = { it },
        modifier = Modifier.fillMaxSize(),
    ) { section ->
        when (section) {
            "page" -> ChapterPageOverviewSection(
                title = "状态与副作用",
                goal = "直接操练运行时基元，以便手动检查 remember、derivedState、produceState 和 key 标识的行为。",
                modules = listOf("ui-runtime", "remember", "effects", "key scopes"),
            )

            "page_filter" -> ChapterPageFilterSection(
                pages = listOf("核心", "标识", "Patch", "清单"),
                selectedIndex = selectedPageState.value,
                onSelectionChange = { selectedPageState.value = it },
            )

            "benchmark" -> ScenarioSection(
                kind = ScenarioKind.Benchmark,
                title = "State Benchmark 锚点",
                subtitle = "此区块固定在默认的核心页面，让 benchmark 控件始终保持在首屏可见。",
            ) {
                Text(
                    text = "Benchmark 步骤 ${benchmarkStepState.value}",
                    modifier = Modifier.margin(bottom = 8.dp),
                )
                Button(
                    text = "推进 State Benchmark ${benchmarkStepState.value}",
                    modifier = Modifier
                        .fillMaxWidth()
                        .margin(bottom = 8.dp),
                    onClick = {
                        benchmarkStepState.value = benchmarkStepState.value + 1
                    },
                )
                Button(
                    text = "重置 State Benchmark",
                    modifier = Modifier
                        .fillMaxWidth()
                        .margin(bottom = 8.dp),
                    onClick = {
                        benchmarkStepState.value = 0
                    },
                )
                BenchmarkRouteCallout(
                    route = "Launcher -> MainActivity(extra=state) -> State -> State Benchmark Anchor",
                    stableTargets = listOf(
                        "Advance State Benchmark 0",
                        "Reset State Benchmark",
                    ),
                )
                Text(
                    text = "稳定路径: launcher -> state module -> benchmark anchor",
                    style = UiTextStyle(fontSizeSp = 12.sp),
                    color = TextDefaults.secondaryColor(),
                )
            }

            "counter" -> ScenarioSection(
                kind = ScenarioKind.Core,
                title = "remember + derivedStateOf + produceState",
                subtitle = "展示本地状态、派生标签和 produceState 生成的状态文本。",
            ) {
                Text(text = "点击次数: ${clickCountState.value}")
                Text(
                    text = summaryState.value,
                    color = TextDefaults.secondaryColor(),
                    modifier = Modifier
                        .padding(vertical = 4.dp),
                )
                Text(
                    text = timelineState.value,
                    style = UiTextStyle(fontSizeSp = 13.sp),
                    color = TextDefaults.secondaryColor(),
                )
                Row(
                    spacing = 8.dp,
                    verticalAlignment = com.viewcompose.ui.layout.VerticalAlignment.Center,
                    modifier = Modifier.margin(top = 12.dp),
                ) {
                    Button(
                        text = "递增",
                        onClick = {
                            clickCountState.value = clickCountState.value + 1
                        },
                    )
                    Button(
                        text = "重置",
                        onClick = {
                            clickCountState.value = 0
                        },
                    )
                }
            }

            "viewmodel" -> ScenarioSection(
                kind = ScenarioKind.Core,
                title = "ViewModel + StateFlow + collectAsStateWithLifecycle",
                subtitle = "验证宿主默认注入 Local 后，无需手动 provide 即可完成 ViewModel 协作。",
            ) {
                Text(
                    text = "ViewModel 计数: ${vmCounterState.value}",
                    modifier = Modifier
                        .margin(bottom = 8.dp)
                        .testTag(DemoTestTags.STATE_VM_COUNTER),
                )
                Button(
                    text = "ViewModel 计数 +1",
                    onClick = {
                        vmStateHandle["counter"] = vmCounterState.value + 1
                    },
                    modifier = Modifier.testTag(DemoTestTags.STATE_VM_INCREMENT),
                )
            }

            "panel" -> ScenarioSection(
                kind = ScenarioKind.Visual,
                title = "key 作用域 + 条件 UI",
                subtitle = "临时面板在可见时保持自身状态，切走后再切回时会完全重建。",
            ) {
                Button(
                    text = if (panelVisibleState.value) "隐藏面板" else "显示面板",
                    modifier = Modifier.margin(bottom = 12.dp),
                    onClick = {
                        panelVisibleState.value = !panelVisibleState.value
                    },
                )
                Text(
                    text = "Visibility 示例: 面板关闭时隐藏",
                    modifier = Modifier
                        .visibility(
                            if (panelVisibleState.value) {
                                Visibility.Visible
                            } else {
                                Visibility.Gone
                            },
                        )
                        .padding(bottom = 8.dp),
                )
                if (panelVisibleState.value) {
                    key("transient-panel") {
                        val panelTapState = remember { mutableStateOf(0) }
                        Column(
                            spacing = 8.dp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .backgroundColor(SurfaceDefaults.variantBackgroundColor())
                                .padding(12.dp),
                        ) {
                            Text(text = "有 key 的临时面板")
                            Button(
                                text = "面板点击次数: ${panelTapState.value}",
                                onClick = {
                                    panelTapState.value = panelTapState.value + 1
                                },
                            )
                        }
                    }
                }
            }

            "patch" -> ScenarioSection(
                kind = ScenarioKind.Benchmark,
                title = "Patch 压力测试",
                subtitle = "将第一批节点级 patch 目标放在一起驱动，使手动测试和 benchmark 运行命中同一更新路径。",
            ) {
                val step = patchStepState.value
                Text(text = "Patch 标题 $step")
                Text(
                    text = "已 Patch 节点: Text, Button, TextField, SegmentedControl, TabRow, Row, Column, Box, Image",
                    color = TextDefaults.secondaryColor(),
                    modifier = Modifier.padding(vertical = 4.dp),
                )
                Column(
                    spacing = 8.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .margin(top = 8.dp, bottom = 12.dp),
                ) {
                    Button(
                        text = "推进 Patch 状态 $step",
                        modifier = Modifier.testTag(DemoTestTags.STATE_PATCH_ADVANCE),
                        onClick = {
                            val nextStep = patchStepState.value + 1
                            patchStepState.value = nextStep
                            patchFieldValueState.setTextAndPlaceCursorAtEnd("value-$nextStep")
                            patchSegmentIndexState.value = nextStep % 3
                            patchTabIndexState.value = nextStep % 2
                        },
                    )
                    Button(
                        text = "重置 Patch 状态",
                        onClick = {
                            patchStepState.value = 0
                            patchFieldValueState.setTextAndPlaceCursorAtEnd("value-0")
                            patchSegmentIndexState.value = 0
                            patchTabIndexState.value = 0
                        },
                    )
                }
                Button(
                    text = "Patch 操作 $step",
                    onClick = {},
                    modifier = Modifier.margin(bottom = 12.dp),
                )
                Button(
                    text = "打开诊断渲染器",
                    onClick = onOpenDiagnostics,
                    modifier = Modifier
                        .margin(bottom = 12.dp)
                        .testTag(DemoTestTags.STATE_PATCH_OPEN_DIAGNOSTICS),
                )
                TextField(
                    state = patchFieldValueState,
                    label = "已 Patch 字段",
                    supportingText = "当前 Patch 步骤: $step",
                    modifier = Modifier.margin(bottom = 12.dp),
                )
                SegmentedControl(
                    items = listOf("Alpha", "Beta", "Gamma"),
                    selectedIndex = patchSegmentIndexState.value,
                    onSelectionChange = { patchSegmentIndexState.value = it },
                    modifier = Modifier.margin(bottom = 12.dp),
                )
                Text(
                    text = "Segment 索引: ${patchSegmentIndexState.value}",
                    color = TextDefaults.secondaryColor(),
                    modifier = Modifier
                        .margin(bottom = 12.dp)
                        .testTag(DemoTestTags.STATE_PATCH_SEGMENT_SUMMARY),
                )
                Row(
                    spacing = if (step % 2 == 0) 8.dp else 16.dp,
                    arrangement = if (step % 2 == 0) MainAxisArrangement.Start else MainAxisArrangement.SpaceEvenly,
                    verticalAlignment = VerticalAlignment.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .margin(bottom = 12.dp),
                ) {
                    Text(text = "Row A")
                    Text(text = "Row B")
                    Text(text = "Row C")
                }
                Column(
                    spacing = if (step % 2 == 0) 4.dp else 12.dp,
                    arrangement = if (step % 2 == 0) MainAxisArrangement.Start else MainAxisArrangement.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .margin(bottom = 12.dp),
                ) {
                    Text(text = "Col 项 1")
                    Text(text = "Col 项 2")
                }
                Box(
                    contentAlignment = if (step % 2 == 0) BoxAlignment.TopStart else BoxAlignment.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .backgroundColor(SurfaceDefaults.variantBackgroundColor())
                        .padding(12.dp)
                        .margin(bottom = 12.dp),
                ) {
                    Text(text = "Box 内容 $step")
                }
                Image(
                    source = ImageSource.Resource(android.R.drawable.ic_menu_gallery),
                    tint = if (step % 2 == 0) 0xFF000000.toInt() else 0xFFFF0000.toInt(),
                    modifier = Modifier.margin(bottom = 12.dp),
                )
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "Tab 索引: ${patchTabIndexState.value}",
                        color = TextDefaults.secondaryColor(),
                        modifier = Modifier
                            .margin(bottom = 8.dp)
                            .testTag(DemoTestTags.STATE_PATCH_TAB_SUMMARY),
                    )
                    TabRow(
                        selectedIndex = patchTabIndexState.value,
                        onTabSelected = { patchTabIndexState.value = it },
                    ) {
                        Tab(key = "summary") { selected ->
                            Text(
                                text = "摘要",
                                color = if (selected) TextDefaults.primaryColor() else TextDefaults.secondaryColor(),
                            )
                        }
                        Tab(key = "details") { selected ->
                            Text(
                                text = "详情",
                                color = if (selected) TextDefaults.primaryColor() else TextDefaults.secondaryColor(),
                            )
                        }
                    }
                    HorizontalPager(
                        currentPage = patchTabIndexState.value,
                        onPageChanged = { patchTabIndexState.value = it },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Page(key = "summary", contentToken = "summary-$step") {
                            Column(
                                spacing = 8.dp,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .backgroundColor(SurfaceDefaults.variantBackgroundColor())
                                    .padding(12.dp),
                            ) {
                                Text(text = "Tab 摘要 $step")
                                Text(
                                    text = "Patch 场景保持 Tab 宿主稳定，同时页面元数据随状态变化。",
                                    color = TextDefaults.secondaryColor(),
                                )
                            }
                        }
                        Page(key = "details", contentToken = "details-$step") {
                            Column(
                                spacing = 8.dp,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .backgroundColor(SurfaceDefaults.variantBackgroundColor())
                                    .padding(12.dp),
                            ) {
                                Text(text = "Tab 详情 $step")
                                Text(
                                    text = "使用此页面检查重复更新时 Tab 选中状态的 Patch 行为。",
                                    color = TextDefaults.secondaryColor(),
                                )
                            }
                        }
                    }
                }
                Text(
                    text = "稳定 token 的 Tab Pager 保持页面标识不变，同时页面闭包仍会反映最新的外部状态。",
                    color = TextDefaults.secondaryColor(),
                    modifier = Modifier
                        .padding(top = 12.dp, bottom = 4.dp),
                )
                Column(modifier = Modifier.fillMaxWidth()) {
                    TabRow(
                        selectedIndex = stableTabIndexState.value,
                        onTabSelected = { stableTabIndexState.value = it },
                    ) {
                        Tab(key = "stable-summary") { selected ->
                            Text(
                                text = "稳定摘要",
                                color = if (selected) TextDefaults.primaryColor() else TextDefaults.secondaryColor(),
                            )
                        }
                        Tab(key = "stable-details") { selected ->
                            Text(
                                text = "稳定详情",
                                color = if (selected) TextDefaults.primaryColor() else TextDefaults.secondaryColor(),
                            )
                        }
                    }
                    HorizontalPager(
                        currentPage = stableTabIndexState.value,
                        onPageChanged = { stableTabIndexState.value = it },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Page(key = "stable-summary", contentToken = "stable-summary") {
                            Column(
                                spacing = 8.dp,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .backgroundColor(SurfaceDefaults.variantBackgroundColor())
                                    .padding(12.dp),
                            ) {
                                Text(
                                    text = "稳定摘要 $step",
                                    modifier = Modifier.testTag(DemoTestTags.STATE_STABLE_SUMMARY),
                                )
                                Text(
                                    text = "即使 Tab 页面 token 保持稳定，推进 Patch 状态后此页面仍应刷新。",
                                    color = TextDefaults.secondaryColor(),
                                )
                            }
                        }
                        Page(key = "stable-details", contentToken = "stable-details") {
                            Column(
                                spacing = 8.dp,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .backgroundColor(SurfaceDefaults.variantBackgroundColor())
                                    .padding(12.dp),
                            ) {
                                Text(text = "稳定详情 $step")
                                Text(
                                    text = "使用此页面捕获当 key 页面元数据不变时 Tab 页面闭包是否过期。",
                                    color = TextDefaults.secondaryColor(),
                                )
                            }
                        }
                    }
                }
                Text(
                    text = "VerticalPager 在稳定 token 下也应在 patch 推进时刷新页面闭包内容。",
                    color = TextDefaults.secondaryColor(),
                    modifier = Modifier
                        .padding(top = 12.dp, bottom = 4.dp),
                )
                VerticalPager(
                    currentPage = stableVerticalPagerIndexState.value,
                    onPageChanged = { stableVerticalPagerIndexState.value = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp),
                ) {
                    Page(key = "vertical-summary", contentToken = "vertical-summary") {
                        Column(
                            spacing = 8.dp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .backgroundColor(SurfaceDefaults.variantBackgroundColor())
                                .padding(12.dp),
                        ) {
                            Text(
                                text = "Vertical 摘要 $step",
                                modifier = Modifier.testTag(DemoTestTags.STATE_VERTICAL_PAGER_SUMMARY),
                            )
                            Text(
                                text = "用于验证 VerticalPager 在无结构变化时也会刷新可见页面内容。",
                                color = TextDefaults.secondaryColor(),
                            )
                        }
                    }
                    Page(key = "vertical-details", contentToken = "vertical-details") {
                        Column(
                            spacing = 8.dp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .backgroundColor(SurfaceDefaults.variantBackgroundColor())
                                .padding(12.dp),
                        ) {
                            Text(text = "Vertical 详情 $step")
                            Text(
                                text = "切换到第二页后继续推进 patch，页面文案也应与外部状态同步。",
                                color = TextDefaults.secondaryColor(),
                            )
                        }
                    }
                }
            }

            else -> VerificationNotesSection(
                what = "State 章节应揭示根重渲染、本地标识和条件重建在反复操作下是否行为可预测。",
                howToVerify = listOf(
                    "连续点击递增和重置，确认派生文案与 timeline 一起更新。",
                    "隐藏再显示 transient panel，确认 panel 内点击计数会被重建。",
                    "进入 Patch 页面，连续点击推进 Patch 状态，确认 Text、Button、TextField、SegmentedControl 和 TabRow 都同步更新。",
                    "切换 theme mode 后再继续点击，确认状态值不受主题刷新影响。",
                ),
                expected = listOf(
                    "remember 状态在同一 identity 下保留，在 key 变化后重建。",
                    "derivedStateOf 和 produceState 不会落后于源状态。",
                    "Patch 页面里的第一批节点会优先走 patch，而不是退回全量重绑。",
                    "条件 UI 显隐不会留下脏状态。",
                ),
                relatedGaps = listOf(
                    "还没有更细粒度的通用 subtree recomposition。",
                    "还没有把 patch/rebind/skipped 统计直接可视化到页面上。",
                ),
            )
        }
    }
}
