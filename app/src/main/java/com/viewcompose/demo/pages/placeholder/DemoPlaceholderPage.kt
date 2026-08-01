package com.viewcompose

import com.viewcompose.preview.tooling.ViewComposePreview
import com.viewcompose.ui.modifier.Modifier
import com.viewcompose.ui.modifier.fillMaxSize
import com.viewcompose.widget.core.LazyColumn
import com.viewcompose.widget.core.Text
import com.viewcompose.widget.core.UiTreeBuilder

@ViewComposePreview(name = "Roadmap placeholder", group = "Demo/Pages", heightDp = 891)
internal fun UiTreeBuilder.PreviewChapterPlaceholder() {
    ChapterPlaceholderPage(
        title = "规划章节",
        subtitle = "用于验证尚未落地模块的统一占位页面。",
        plannedPages = listOf("高级场景", "平台集成", "性能诊断"),
        currentGaps = listOf("能力仍在规划", "交互路径尚未接入"),
    )
}

internal fun UiTreeBuilder.ChapterPlaceholderPage(
    title: String,
    subtitle: String,
    plannedPages: List<String>,
    currentGaps: List<String>,
) {
    LazyColumn(
        items = listOf("overview", "planned", "gaps", "verify"),
        key = { it },
        modifier = Modifier.fillMaxSize(),
    ) { section ->
        when (section) {
            "overview" -> ChapterPageOverviewSection(
                title = title,
                goal = "保持此章节可见，使 demo 能力地图在底层框架工作尚未完成时依然保持稳定。",
                modules = listOf("roadmap placeholder", "future scenario groups"),
            )

            "planned" -> ScenarioSection(
                kind = ScenarioKind.Guide,
                title = "规划页面",
                subtitle = subtitle,
            ) {
                Text(
                    text = "此章节现在就有意呈现，以便 demo 结构在框架能力追赶期间保持稳定。",
                )
                plannedPages.forEach { page ->
                    Text(text = "• $page")
                }
            }

            "gaps" -> ScenarioSection(
                kind = ScenarioKind.Guide,
                title = "当前缺口",
                subtitle = "这些缺口直接对应与 Compose 相比仍然缺失的框架工作。",
            ) {
                currentGaps.forEach { gap ->
                    Text(text = "• $gap")
                }
            }

            else -> VerificationNotesSection(
                what = "将此 placeholder 章节用作稳定的路线图界面，而不是死胡同目录卡片。",
                howToVerify = listOf(
                    "从目录进入该章节，确认可以正常打开并返回。",
                    "检查规划页面与当前缺口是否和当前 roadmap 一致。",
                    "切换全局主题，确认 placeholder 页面仍保持和正式模块一致的壳层表现。",
                ),
                expected = listOf(
                    "规划中模块已经进入统一的 Activity 壳。",
                    "后续补能力时只需要替换章节内容，不需要重做 demo 导航结构。",
                    "placeholder 页面本身不会影响现有 benchmark 路径。",
                ),
            )
        }
    }
}
