package com.viewcompose

import com.viewcompose.ui.modifier.Modifier
import com.viewcompose.ui.modifier.shape
import com.viewcompose.ui.modifier.backgroundColor
import com.viewcompose.ui.modifier.cornerRadius
import com.viewcompose.ui.modifier.fillMaxWidth
import com.viewcompose.ui.modifier.height
import com.viewcompose.ui.modifier.margin
import com.viewcompose.ui.modifier.padding
import com.viewcompose.ui.modifier.testTag
import com.viewcompose.ui.modifier.width
import com.viewcompose.widget.core.Box
import com.viewcompose.widget.core.Column
import com.viewcompose.widget.core.Divider
import com.viewcompose.widget.core.Row
import com.viewcompose.widget.core.SegmentedControl
import com.viewcompose.widget.core.Surface
import com.viewcompose.widget.core.SurfaceDefaults
import com.viewcompose.widget.core.SurfaceVariant
import com.viewcompose.widget.core.Text
import com.viewcompose.widget.core.TextDefaults
import com.viewcompose.widget.core.Theme
import com.viewcompose.widget.core.UiTextStyle
import com.viewcompose.widget.core.UiTreeBuilder
import com.viewcompose.widget.core.dp
import com.viewcompose.widget.core.sp
import java.util.Locale

/**
 * 标记 demo 场景类型，帮助页面用一致的文案区分基线、压力和边界验证路径。
 * Marks demo scenario types so pages use consistent copy for baseline, stress, and edge verification paths.
 */
internal enum class ScenarioKind(
    val label: String,
    val hint: String,
) {
    Guide(
        label = "Guide",
        hint = "Use this block to understand the page goal before testing.",
    ),
    Core(
        label = "Core Scenario",
        hint = "This is the primary manual verification path for the current page.",
    ),
    Visual(
        label = "Visual Scenario",
        hint = "Use this block to inspect placement, styling, and visual state stability.",
    ),
    Stress(
        label = "Stress Scenario",
        hint = "Use this block to trigger edge cases and benchmark-friendly repeated updates.",
    ),
    Benchmark(
        label = "Benchmark Entry",
        hint = "This block is intentionally stable so manual runs and macrobenchmarks can share the same path.",
    ),
}

/**
 * 渲染一组主题色样本，用于手工确认 token 在当前主题模式下的实际输出。
 * Renders theme swatches for manual confirmation of token output under the active theme mode.
 */
internal fun UiTreeBuilder.ThemeSwatchRow(
    label: String,
    swatches: List<ThemeSwatch>,
) {
    Column(
        spacing = 8.dp,
        modifier = Modifier.margin(bottom = 8.dp),
    ) {
        Text(
            text = label,
            style = UiTextStyle(fontSizeSp = 13.sp),
            color = TextDefaults.secondaryColor(),
        )
        Row(
            spacing = 8.dp,
            modifier = Modifier.fillMaxWidth(),
        ) {
            swatches.forEach { swatch ->
                Column(
                    spacing = 6.dp,
                    modifier = Modifier.weight(1f),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .backgroundColor(swatch.color)
                            .shape(SurfaceDefaults.shape()),
                    ) {}
                    Text(
                        text = swatch.label,
                        style = UiTextStyle(fontSizeSp = 12.sp),
                    )
                }
            }
        }
    }
}

internal fun UiTreeBuilder.ScenarioSection(
    kind: ScenarioKind,
    title: String,
    subtitle: String,
    content: UiTreeBuilder.() -> Unit,
) {
    DemoSection(
        title = title,
        subtitle = subtitle,
    ) {
        Text(
            text = "${kind.label} · ${kind.hint}",
            style = UiTextStyle(fontSizeSp = 12.sp),
            color = TextDefaults.secondaryColor(),
            modifier = Modifier.padding(bottom = 4.dp),
        )
        content()
    }
}

internal fun UiTreeBuilder.BenchmarkRouteCallout(
    route: String,
    stableTargets: List<String>,
) {
    Surface(
        variant = SurfaceVariant.Variant,
        modifier = Modifier
            .fillMaxWidth()
            .margin(bottom = 8.dp),
    ) {
        Column(
            spacing = 6.dp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
        ) {
            Text(
                text = "Benchmark Route",
                style = UiTextStyle(fontSizeSp = 13.sp),
                color = TextDefaults.secondaryColor(),
            )
            Text(text = route)
            if (stableTargets.isNotEmpty()) {
                ChecklistGroup(
                    title = "Stable Targets",
                    items = stableTargets,
                )
            }
        }
    }
}

/**
 * 统一 demo 章节中的卡片式说明区域，避免各页面重复搭建标题、副标题和分隔线结构。
 * Provides the shared explanatory section used across demo chapters, avoiding repeated title, subtitle, and divider layout.
 */
internal fun UiTreeBuilder.DemoSection(
    title: String,
    subtitle: String,
    content: UiTreeBuilder.() -> Unit,
) {
    Surface(
        variant = SurfaceVariant.Default,
        modifier = Modifier
            .fillMaxWidth()
            .margin(bottom = 12.dp)
            .padding(16.dp),
    ) {
        Column(
            spacing = 8.dp,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = title,
                style = UiTextStyle(fontSizeSp = 20.sp),
            )
            Text(
                text = subtitle,
                style = UiTextStyle(fontSizeSp = 13.sp),
                color = TextDefaults.secondaryColor(),
                modifier = Modifier
                    .padding(bottom = 4.dp),
            )
            Divider()
            content()
        }
    }
}

internal fun UiTreeBuilder.ChapterPageOverviewSection(
    title: String,
    goal: String,
    modules: List<String>,
) {
    DemoSection(
        title = title,
        subtitle = "This page defines the testing goal and the framework layers that should be touched during manual verification.",
    ) {
        Text(text = goal)
        ChecklistGroup(
            title = "Framework Modules",
            items = modules,
        )
    }
}

internal fun UiTreeBuilder.ChapterPageFilterSection(
    pages: List<String>,
    selectedIndex: Int,
    onSelectionChange: (Int) -> Unit,
) {
    DemoSection(
        title = "Chapter Pages",
        subtitle = "Use the local page switcher to isolate one manual test path at a time within the current chapter.",
    ) {
        SegmentedControl(
            items = pages,
            selectedIndex = selectedIndex,
            onSelectionChange = onSelectionChange,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

internal fun UiTreeBuilder.VerificationNotesSection(
    what: String,
    howToVerify: List<String>,
    expected: List<String>,
    relatedGaps: List<String> = emptyList(),
) {
    DemoSection(
        title = "Verification Notes",
        subtitle = "Use this block as the manual acceptance checklist for the current chapter page.",
    ) {
        Text(text = what)
        ChecklistGroup(
            title = "How To Verify",
            items = howToVerify,
        )
        ChecklistGroup(
            title = "Expected",
            items = expected,
        )
        if (relatedGaps.isNotEmpty()) {
            ChecklistGroup(
                title = "Related Gaps",
                items = relatedGaps,
            )
        }
    }
}

internal fun UiTreeBuilder.ChecklistGroup(
    title: String,
    items: List<String>,
) {
    Column(
        spacing = 4.dp,
        modifier = Modifier.margin(top = 8.dp),
    ) {
        Text(
            text = title,
            style = UiTextStyle(fontSizeSp = 13.sp),
            color = TextDefaults.secondaryColor(),
        )
        items.forEachIndexed { index, item ->
            Text(text = "${index + 1}. $item")
        }
    }
}

internal fun UiTreeBuilder.DiagnosticFactGroup(
    title: String,
    facts: List<DiagnosticFact>,
    valueTagsByLabel: Map<String, String> = emptyMap(),
) {
    Column(
        spacing = 6.dp,
        modifier = Modifier.margin(top = 8.dp),
    ) {
        Text(
            text = title,
            style = UiTextStyle(fontSizeSp = 13.sp),
            color = TextDefaults.secondaryColor(),
        )
        facts.forEach { fact ->
            val valueTag = valueTagsByLabel[fact.label]
            Row(
                spacing = 12.dp,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = fact.label,
                    style = UiTextStyle(fontSizeSp = 13.sp),
                    color = TextDefaults.secondaryColor(),
                    modifier = Modifier.width(136.dp),
                )
                Text(
                    text = fact.value,
                    modifier = valueTag
                        ?.let { tag ->
                            Modifier
                                .weight(1f)
                                .testTag(tag)
                        }
                        ?: Modifier.weight(1f),
                )
            }
        }
    }
}

internal fun Int.asColorHex(): String {
    return "#${toUInt().toString(16).padStart(8, '0').uppercase(Locale.US)}"
}
