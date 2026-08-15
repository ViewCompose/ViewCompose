package com.viewcompose

import androidx.annotation.StringRes
import com.viewcompose.host.android.resources.stringResource
import com.viewcompose.ui.modifier.Modifier
import com.viewcompose.ui.modifier.shape
import com.viewcompose.ui.modifier.backgroundColor
import com.viewcompose.ui.modifier.cornerRadius
import com.viewcompose.ui.modifier.fillMaxWidth
import com.viewcompose.ui.node.SegmentedControlItem
import com.viewcompose.ui.modifier.height
import com.viewcompose.ui.modifier.margin
import com.viewcompose.ui.modifier.padding
import com.viewcompose.ui.modifier.testTag
import com.viewcompose.ui.modifier.width
import com.viewcompose.ui.foundation.Box
import com.viewcompose.ui.foundation.Column
import com.viewcompose.ui.foundation.Divider
import com.viewcompose.ui.foundation.Row
import com.viewcompose.ui.foundation.SegmentedControl
import com.viewcompose.ui.foundation.Surface
import com.viewcompose.ui.foundation.SurfaceDefaults
import com.viewcompose.ui.foundation.SurfaceVariant
import com.viewcompose.ui.foundation.Text
import com.viewcompose.ui.foundation.TextDefaults
import com.viewcompose.ui.foundation.Theme
import com.viewcompose.ui.foundation.UiTextStyle
import com.viewcompose.ui.foundation.UiTreeBuilder
import com.viewcompose.ui.unit.dp
import com.viewcompose.ui.unit.sp
import java.util.Locale

/**
 * 标记 demo 场景类型，帮助页面用一致的文案区分基线、压力和边界验证路径。
 * Marks demo scenario types so pages use consistent copy for baseline, stress, and edge verification paths.
 */
internal enum class ScenarioKind(
    @StringRes val labelRes: Int,
    @StringRes val hintRes: Int,
) {
    Guide(
        labelRes = R.string.demo_scenario_kind_guide,
        hintRes = R.string.demo_scenario_kind_guide_hint,
    ),
    Core(
        labelRes = R.string.demo_scenario_kind_core,
        hintRes = R.string.demo_scenario_kind_core_hint,
    ),
    Visual(
        labelRes = R.string.demo_scenario_kind_visual,
        hintRes = R.string.demo_scenario_kind_visual_hint,
    ),
    Stress(
        labelRes = R.string.demo_scenario_kind_stress,
        hintRes = R.string.demo_scenario_kind_stress_hint,
    ),
    Benchmark(
        labelRes = R.string.demo_scenario_kind_benchmark,
        hintRes = R.string.demo_scenario_kind_benchmark_hint,
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
            style = UiTextStyle(fontSizeSp = 13.sp, lineHeightSp = 18.sp),
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
    modifier: Modifier = Modifier,
    content: UiTreeBuilder.() -> Unit,
) {
    DemoSection(
        title = title,
        subtitle = subtitle,
        modifier = modifier,
    ) {
        Text(
            text = stringResource(
                R.string.demo_scenario_kind_format,
                stringResource(kind.labelRes),
                stringResource(kind.hintRes),
            ),
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
                text = stringResource(R.string.demo_benchmark_route),
                style = UiTextStyle(fontSizeSp = 13.sp),
                color = TextDefaults.secondaryColor(),
            )
            Text(text = route)
            if (stableTargets.isNotEmpty()) {
                ChecklistGroup(
                    title = stringResource(R.string.demo_stable_targets),
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
    modifier: Modifier = Modifier,
    content: UiTreeBuilder.() -> Unit,
) {
    Surface(
        variant = SurfaceVariant.Default,
        modifier = Modifier
            .fillMaxWidth()
            .margin(bottom = 12.dp)
            .padding(16.dp)
            .then(modifier),
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
        subtitle = stringResource(R.string.demo_chapter_overview_subtitle),
    ) {
        Text(text = goal)
        ChecklistGroup(
            title = stringResource(R.string.demo_framework_modules),
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
        title = stringResource(R.string.demo_chapter_pages),
        subtitle = stringResource(R.string.demo_chapter_pages_subtitle),
    ) {
        SegmentedControl(
            items = pages.mapIndexed { index, label ->
                SegmentedControlItem(key = index, label = label)
            },
            selectedIndex = selectedIndex,
            onSelectionChange = onSelectionChange,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

internal fun demoSegmentedItems(
    vararg keyedLabels: Pair<Any, String>,
): List<SegmentedControlItem> = keyedLabels.map { (key, label) ->
    SegmentedControlItem(key = key, label = label)
}

internal fun UiTreeBuilder.VerificationNotesSection(
    what: String,
    howToVerify: List<String>,
    expected: List<String>,
    relatedGaps: List<String> = emptyList(),
) {
    DemoSection(
        title = stringResource(R.string.demo_verification_notes),
        subtitle = stringResource(R.string.demo_verification_notes_subtitle),
    ) {
        Text(text = what)
        ChecklistGroup(
            title = stringResource(R.string.demo_how_to_verify),
            items = howToVerify,
        )
        ChecklistGroup(
            title = stringResource(R.string.demo_expected),
            items = expected,
        )
        if (relatedGaps.isNotEmpty()) {
            ChecklistGroup(
                title = stringResource(R.string.demo_related_gaps),
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
            Text(
                text = stringResource(
                    R.string.demo_numbered_item_format,
                    index + 1,
                    item,
                ),
            )
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
        modifier = Modifier
            .fillMaxWidth()
            .margin(top = 8.dp),
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
                    style = UiTextStyle(fontSizeSp = 13.sp, lineHeightSp = 18.sp),
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
