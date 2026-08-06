package com.viewcompose

import android.widget.TextView
import com.viewcompose.preview.tooling.ViewComposePreview
import com.viewcompose.ui.modifier.Modifier
import com.viewcompose.ui.modifier.fillMaxSize
import com.viewcompose.ui.modifier.fillMaxWidth
import com.viewcompose.ui.modifier.margin
import com.viewcompose.ui.modifier.padding
import com.viewcompose.ui.modifier.testTag
import com.viewcompose.host.android.AndroidView
import com.viewcompose.runtime.mutableStateOf
import com.viewcompose.ui.foundation.Button
import com.viewcompose.ui.foundation.LazyColumn
import com.viewcompose.ui.foundation.Row
import com.viewcompose.ui.foundation.Text
import com.viewcompose.ui.foundation.TextDefaults
import com.viewcompose.ui.foundation.UiTextStyle
import com.viewcompose.ui.foundation.UiTreeBuilder
import com.viewcompose.ui.unit.dp
import com.viewcompose.ui.foundation.remember
import com.viewcompose.ui.unit.sp

@ViewComposePreview(name = "Interop", group = "Demo/Pages")
internal fun UiTreeBuilder.InteropPage() {
    val alternateLabelsState = remember { mutableStateOf(false) }
    val benchmarkToggleState = remember { mutableStateOf(false) }
    val selectedPageState = remember { mutableStateOf(0) }
    val pageItems = when (selectedPageState.value) {
        0 -> listOf("benchmark", "page", "page_filter", "basics", "verify")
        1 -> listOf("page", "page_filter", "theme_bridge", "verify")
        else -> listOf("page", "page_filter", "why_it_matters", "verify")
    }
    LazyColumn(
        items = pageItems,
        key = { it },
        modifier = Modifier.fillMaxSize(),
    ) { section ->
        when (section) {
            "page" -> ChapterPageOverviewSection(
                title = "互操作",
                goal = "保持 AndroidView 作为一等迁移路径，验证原生控件仍然适配框架主题和状态边界。",
                modules = listOf("AndroidView", "theme bridge", "local propagation"),
            )

            "page_filter" -> ChapterPageFilterSection(
                pages = listOf("基础", "主题桥接", "意义"),
                selectedIndex = selectedPageState.value,
                onSelectionChange = { selectedPageState.value = it },
            )

            "benchmark" -> ScenarioSection(
                kind = ScenarioKind.Benchmark,
                title = "互操作 Benchmark 锚点",
                subtitle = "保持原生 TextView 互操作更新路径在首屏可见。",
            ) {
                Text(
                    text = if (benchmarkToggleState.value) "互操作 benchmark 原生状态: 替代" else "互操作 benchmark 原生状态: 主要",
                    modifier = Modifier.margin(bottom = 8.dp),
                )
                Button(
                    text = if (benchmarkToggleState.value) "Benchmark 替代" else "Benchmark 主要",
                    modifier = Modifier
                        .fillMaxWidth()
                        .margin(bottom = 8.dp)
                        .testTag(DemoTestTags.INTEROP_BENCHMARK_TOGGLE),
                    onClick = { benchmarkToggleState.value = !benchmarkToggleState.value },
                )
                Button(
                    text = "重置互操作 Benchmark",
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(DemoTestTags.INTEROP_BENCHMARK_RESET),
                    onClick = { benchmarkToggleState.value = false },
                )
                AndroidView(
                    key = "interop_benchmark_text_view",
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .testTag(DemoTestTags.INTEROP_BENCHMARK_NATIVE_TEXT),
                    factory = { context -> TextView(context) },
                    update = { view ->
                        (view as TextView).text = if (benchmarkToggleState.value) {
                            "原生 benchmark TextView: 替代"
                        } else {
                            "原生 benchmark TextView: 主要"
                        }
                    },
                )
                BenchmarkRouteCallout(
                    route = "Launcher -> Interop -> Benchmark Anchor",
                    stableTargets = listOf("Benchmark Primary", "Reset"),
                )
                Text(
                    text = "稳定路径: launcher -> interop -> benchmark anchor",
                    style = UiTextStyle(fontSizeSp = 12.sp),
                    color = TextDefaults.secondaryColor(),
                    modifier = Modifier.margin(top = 8.dp),
                )
            }

            "basics" -> ScenarioSection(
                kind = ScenarioKind.Core,
                title = "AndroidView 基础",
                subtitle = "旧版原生 View 仍然挂载在同一声明式树中，可响应框架状态。",
            ) {
                Row(
                    spacing = 8.dp,
                    modifier = Modifier.margin(bottom = 12.dp),
                ) {
                    Button(
                        text = if (alternateLabelsState.value) "显示主要文案" else "显示替代文案",
                        onClick = { alternateLabelsState.value = !alternateLabelsState.value },
                    )
                }
                AndroidView(
                    key = "interop_text_view",
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    factory = { context -> TextView(context) },
                    update = { view ->
                        (view as TextView).text = if (alternateLabelsState.value) {
                            "原生 TextView: 替代内容已启用"
                        } else {
                            "原生 TextView: 主要内容已启用"
                        }
                    },
                )
            }

            "theme_bridge" -> ScenarioSection(
                kind = ScenarioKind.Visual,
                title = "主题桥接",
                subtitle = "验证框架主题 locals 和 Android View 默认值可以共存。",
            ) {
                Text(text = "当前章节将 AndroidView 保持在同一主题容器中，文本、间距和周围的 Surface 仍由 ViewCompose token 驱动。")
                Text(
                    text = "后续应验证主题化原生控件、自定义 View 适配器和 Fragment host 容器。",
                    style = UiTextStyle(fontSizeSp = 13.sp),
                    color = TextDefaults.secondaryColor(),
                )
            }

            "why_it_matters" -> ScenarioSection(
                kind = ScenarioKind.Guide,
                title = "互操作的意义",
                subtitle = "互操作是本框架相比纯 Compose 重写路径的最大实际优势之一。",
            ) {
                Text(text = "使用此章节验证渐进式迁移：现有自定义 View、业务控件和 SDK 界面应可直接挂载，无需先行重写。")
            }

            else -> VerificationNotesSection(
                what = "互操作章节应证明旧 View 控件可以在声明式树中保持挂载，不丢失状态或主题对齐。",
                howToVerify = listOf(
                    "点击切换文案按钮，确认原生 TextView 文案实时更新。",
                    "切换全局 theme mode，确认原生 View 所在容器仍与框架主题协调。",
                    "反复切换章节并返回互操作，确认 AndroidView 依旧可用。",
                ),
                expected = listOf(
                    "AndroidView update 会响应框架状态变化。",
                    "theme locals 和 Android theme bridge 可以共存。",
                    "互操作章节不会因为延迟 session 丢失 local 上下文。",
                ),
                relatedGaps = listOf(
                    "还没有 custom view adapter catalog 和 Fragment host demo。",
                ),
            )
        }
    }
}
