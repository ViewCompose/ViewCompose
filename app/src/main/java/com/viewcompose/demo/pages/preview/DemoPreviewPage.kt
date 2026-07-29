package com.viewcompose

import com.viewcompose.ui.modifier.Modifier
import com.viewcompose.ui.modifier.fillMaxSize
import com.viewcompose.ui.modifier.fillMaxWidth
import com.viewcompose.ui.modifier.margin
import com.viewcompose.ui.modifier.padding
import com.viewcompose.ui.modifier.testTag
import com.viewcompose.ui.modifier.width
import com.viewcompose.runtime.mutableStateOf
import com.viewcompose.widget.core.Button
import com.viewcompose.widget.core.ButtonVariant
import com.viewcompose.widget.core.Column
import com.viewcompose.widget.core.LazyColumn
import com.viewcompose.widget.core.Row
import com.viewcompose.widget.core.SearchBar
import com.viewcompose.widget.core.Surface
import com.viewcompose.widget.core.SurfaceVariant
import com.viewcompose.widget.core.Text
import com.viewcompose.widget.core.TextDefaults
import com.viewcompose.widget.core.UiTreeBuilder
import com.viewcompose.ui.unit.dp
import com.viewcompose.widget.core.remember
import com.viewcompose.widget.core.rememberTextFieldState

internal fun UiTreeBuilder.PreviewPage(
    initialPageIndex: Int = 0,
) {
    val selectedPageState = remember { mutableStateOf(initialPageIndex.coerceIn(0, 2)) }
    val darkThemeState = remember { mutableStateOf(false) }
    val tabletFrameState = remember { mutableStateOf(false) }
    val queryState = rememberTextFieldState()

    val pageItems = when (selectedPageState.value) {
        0 -> listOf("benchmark", "page", "page_filter", "bridge", "verify")
        1 -> listOf("page", "page_filter", "overlay_mock", "verify")
        else -> listOf("page", "page_filter", "snapshot", "verify")
    }

    LazyColumn(
        items = pageItems,
        key = { it },
        modifier = Modifier.fillMaxSize(),
    ) { section ->
        when (section) {
            "page" -> ChapterPageOverviewSection(
                title = "开发预览",
                goal = "把 Studio Preview 与 Paparazzi 统一到同一套组件样例，减少示例漂移和截图回归缺口。",
                modules = listOf("viewcompose-preview", "Compose Preview bridge", "PreviewCatalog", "Paparazzi"),
            )

            "page_filter" -> ChapterPageFilterSection(
                pages = listOf("Bridge", "Overlay Mock", "Snapshot"),
                selectedIndex = selectedPageState.value,
                onSelectionChange = { selectedPageState.value = it },
            )

            "benchmark" -> ScenarioSection(
                kind = ScenarioKind.Benchmark,
                title = "Preview 回归锚点",
                subtitle = "这个锚点和 `:viewcompose-preview` 的快照目录保持对齐，便于人工回归与快照差异定位。",
            ) {
                BenchmarkRouteCallout(
                    route = "Catalog -> Preview -> Snapshot 页",
                    stableTargets = listOf(
                        "Bridge theme/device toggle",
                        "Overlay static mock block",
                        "qaPreview command path",
                    ),
                )
            }

            "bridge" -> ScenarioSection(
                kind = ScenarioKind.Core,
                title = "Compose Preview Bridge 场景",
                subtitle = "模拟 Preview 壳中最常见的 light/dark 与 phone/tablet 组合，确保 DSL 渲染和样式切换可读。",
            ) {
                Row(
                    spacing = 8.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .margin(bottom = 8.dp),
                ) {
                    Button(
                        text = if (darkThemeState.value) "Theme: Dark" else "Theme: Light",
                        variant = ButtonVariant.Tonal,
                        onClick = { darkThemeState.value = !darkThemeState.value },
                        modifier = Modifier
                            .weight(1f)
                            .testTag(DemoTestTags.PREVIEW_THEME_TOGGLE),
                    )
                    Button(
                        text = if (tabletFrameState.value) "Frame: Tablet" else "Frame: Phone",
                        variant = ButtonVariant.Outlined,
                        onClick = { tabletFrameState.value = !tabletFrameState.value },
                        modifier = Modifier
                            .weight(1f)
                            .testTag(DemoTestTags.PREVIEW_DEVICE_TOGGLE),
                    )
                }
                Text(
                    text = "当前组合：${if (darkThemeState.value) "Dark" else "Light"} · ${if (tabletFrameState.value) "Tablet" else "Phone"}",
                    color = TextDefaults.secondaryColor(),
                    modifier = Modifier.margin(bottom = 8.dp),
                )
                Surface(
                    variant = if (darkThemeState.value) SurfaceVariant.Default else SurfaceVariant.Variant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .let { base ->
                            if (tabletFrameState.value) {
                                base.width(560.dp)
                            } else {
                                base
                            }
                        }
                        .testTag(DemoTestTags.PREVIEW_HOST_SAMPLE),
                ) {
                    Column(
                        spacing = 8.dp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                    ) {
                        Text(text = "Preview Host Sample")
                        SearchBar(
                            state = queryState,
                            onSearch = {},
                            placeholder = "输入以模拟 preview 输入态",
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Text(
                            text = "Query = ${queryState.text.ifBlank { "(empty)" }}",
                            color = TextDefaults.secondaryColor(),
                        )
                    }
                }
            }

            "overlay_mock" -> ScenarioSection(
                kind = ScenarioKind.Visual,
                title = "Overlay 静态模拟场景",
                subtitle = "Preview 中不做真实窗口弹层，这里使用静态块模拟 Dialog/Popup/BottomSheet 的布局结构。",
            ) {
                Surface(
                    variant = SurfaceVariant.Variant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(DemoTestTags.PREVIEW_OVERLAY_MOCK),
                ) {
                    Column(
                        spacing = 8.dp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                    ) {
                        Text(text = "Dialog Mock: title + message + actions")
                        Text(
                            text = "Popup Mock: anchored card near trigger",
                            color = TextDefaults.secondaryColor(),
                        )
                        Text(
                            text = "BottomSheet Mock: bottom panel with dismiss button",
                            color = TextDefaults.secondaryColor(),
                        )
                    }
                }
            }

            "snapshot" -> ScenarioSection(
                kind = ScenarioKind.Stress,
                title = "Paparazzi 快照回归路径",
                subtitle = "预览示例与快照回归来自同一份 PreviewCatalog。修改组件视觉后需要更新基线。",
            ) {
                Text(
                    text = "./gradlew qaPreview",
                    modifier = Modifier.testTag(DemoTestTags.PREVIEW_SNAPSHOT_CMD),
                )
                Text(
                    text = "报告目录：viewcompose-preview/build/reports/paparazzi/",
                    color = TextDefaults.secondaryColor(),
                )
                Text(
                    text = "新增组件时，先补 PreviewSpec，再补快照基线。",
                    color = TextDefaults.secondaryColor(),
                )
            }

            else -> VerificationNotesSection(
                what = "开发预览验证覆盖了桥接、静态 mock、快照命令三条路径。",
                howToVerify = listOf(
                    "切换 Theme/Frame 按钮，确认 Host Sample 状态文字同步变化。",
                    "在 Overlay Mock 页确认静态块完整展示，不依赖真实弹层。",
                    "在 Snapshot 页执行 qaPreview，确认可生成/校验快照结果。",
                ),
                expected = listOf(
                    "Preview 用例可作为 Studio Preview 与 Paparazzi 的共同语义锚点。",
                    "overlay 在 preview 场景保持静态模拟，不触发运行时窗口语义。",
                    "快照回归路径稳定，命令和报告目录可直接复用。",
                ),
                relatedGaps = listOf(
                    "真实 overlay 行为仍由 instrumentation 覆盖。",
                ),
            )
        }
    }
}
