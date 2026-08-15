package com.viewcompose

import com.viewcompose.demo.automation.demoAutomationTarget
import com.viewcompose.demo.contract.DemoAutomationRole
import com.viewcompose.demo.contract.DemoScenarioSpec
import com.viewcompose.host.android.resources.stringResource
import com.viewcompose.runtime.MutableState
import com.viewcompose.runtime.mutableStateOf
import com.viewcompose.shadow.android.ShadowDecorationLayer
import com.viewcompose.shadow.android.ShadowRasterCacheStats
import com.viewcompose.shadow.android.ShadowRenderBackendStats
import com.viewcompose.shadow.android.ShadowRenderPolicy
import com.viewcompose.ui.graphics.UiShadow
import com.viewcompose.ui.modifier.Modifier
import com.viewcompose.ui.modifier.backgroundColor
import com.viewcompose.ui.modifier.dropShadow
import com.viewcompose.ui.modifier.dropShadows
import com.viewcompose.ui.modifier.fillMaxWidth
import com.viewcompose.ui.modifier.height
import com.viewcompose.ui.modifier.innerShadow
import com.viewcompose.ui.modifier.innerShadows
import com.viewcompose.ui.modifier.margin
import com.viewcompose.ui.modifier.padding
import com.viewcompose.ui.modifier.shape
import com.viewcompose.ui.modifier.testTag
import com.viewcompose.ui.shape.UiShape
import com.viewcompose.ui.unit.dp
import com.viewcompose.ui.foundation.Button
import com.viewcompose.ui.foundation.Column
import com.viewcompose.ui.foundation.DisposableEffect
import com.viewcompose.ui.foundation.LazyColumn
import com.viewcompose.ui.foundation.Row
import com.viewcompose.ui.foundation.SegmentedControl
import com.viewcompose.ui.foundation.Surface
import com.viewcompose.ui.foundation.SurfaceDefaults
import com.viewcompose.ui.foundation.Text
import com.viewcompose.ui.foundation.TextDefaults
import com.viewcompose.ui.foundation.TextField
import com.viewcompose.ui.foundation.TextFieldSize
import com.viewcompose.ui.foundation.UiTreeBuilder
import com.viewcompose.ui.foundation.remember
import com.viewcompose.ui.foundation.rememberTextFieldState
import com.viewcompose.text.TextFieldState

internal const val GRAPHICS_SHADOW_LAZY_ITEM_COUNT: Int = 1_000

internal const val GRAPHICS_SHADOW_LAZY_ITEM_PREFIX = "shadow_lazy_item_"

internal val GraphicsOuterShadowItems = listOf(
    "shadow_outer_single",
    "shadow_outer_multi",
    "shadow_outer_spread",
    "shadow_outer_shape",
)

internal val GraphicsInnerShadowItems = listOf(
    "shadow_inner_interop",
    "shadow_inner_single",
    "shadow_inner_multi",
)

private val GraphicsShadowDiagnosticsHeaderItems = listOf(
    "shadow_diagnostics",
    "shadow_lazy_intro",
)

internal val GraphicsShadowLazyItems = List(GRAPHICS_SHADOW_LAZY_ITEM_COUNT) { index ->
    "$GRAPHICS_SHADOW_LAZY_ITEM_PREFIX$index"
}

internal val GraphicsShadowListItems = GraphicsShadowDiagnosticsHeaderItems +
    GraphicsShadowLazyItems

private val GraphicsShadowPolicies = listOf(
    ShadowRenderPolicy.Auto,
    ShadowRenderPolicy.ExactBitmap,
    ShadowRenderPolicy.RenderNodeDisplayList,
)

internal fun graphicsShadowContentType(section: String): String {
    return if (section.startsWith(GRAPHICS_SHADOW_LAZY_ITEM_PREFIX)) {
        GRAPHICS_SHADOW_LAZY_ITEM_PREFIX
    } else {
        section
    }
}

internal data class GraphicsShadowDiagnosticsSnapshot(
    val outerCache: ShadowRasterCacheStats,
    val innerCache: ShadowRasterCacheStats,
    val backend: ShadowRenderBackendStats,
)

internal data class GraphicsShadowPageState(
    val policy: MutableState<ShadowRenderPolicy>,
    val diagnostics: MutableState<GraphicsShadowDiagnosticsSnapshot>,
    val diagnosticSampleRevision: MutableState<Int>,
)

internal fun UiTreeBuilder.rememberGraphicsShadowPageState(): GraphicsShadowPageState {
    return remember {
        GraphicsShadowPageState(
            policy = mutableStateOf(ShadowRenderPolicy.Auto),
            diagnostics = mutableStateOf(captureGraphicsShadowDiagnostics()),
            diagnosticSampleRevision = mutableStateOf(0),
        )
    }
}

internal fun InstallGraphicsShadowLifecycle() {
    DisposableEffect("graphics-advanced-shadow") {
        ShadowDecorationLayer.setRenderPolicy(ShadowRenderPolicy.Auto)
        ShadowDecorationLayer.resetBackendDiagnostics()
        onDispose {
            ShadowDecorationLayer.setRenderPolicy(ShadowRenderPolicy.Auto)
        }
    }
}

internal fun UiTreeBuilder.GraphicsOuterShadowFixture(scenario: DemoScenarioSpec?) {
    InstallGraphicsShadowLifecycle()
    LazyColumn(
        items = GraphicsOuterShadowItems,
        key = { it },
        modifier = Modifier.fillMaxWidth(),
    ) { section ->
        when (section) {
            "shadow_outer_single" -> GraphicsSingleOuterShadowSection(scenario)
            "shadow_outer_multi" -> GraphicsMultiOuterShadowSection()
            "shadow_outer_spread" -> GraphicsSpreadShadowSection()
            "shadow_outer_shape" -> GraphicsShapeShadowSection()
            else -> error("Unsupported outer-shadow section: $section")
        }
    }
}

internal fun UiTreeBuilder.GraphicsInnerShadowFixture(scenario: DemoScenarioSpec?) {
    InstallGraphicsShadowLifecycle()
    val initialFieldText = stringResource(R.string.demo_graphics_inner_field_initial)
    val fieldState = rememberTextFieldState(initialFieldText)
    val interactionCountState = remember { mutableStateOf(0) }
    LazyColumn(
        items = GraphicsInnerShadowItems,
        key = { it },
        modifier = Modifier.fillMaxWidth(),
    ) { section ->
        when (section) {
            "shadow_inner_single" -> GraphicsSingleInnerShadowSection(scenario)
            "shadow_inner_multi" -> GraphicsMultiInnerShadowSection()
            "shadow_inner_interop" -> GraphicsInnerShadowInteropSection(
                fieldState = fieldState,
                interactionCountState = interactionCountState,
                initialFieldText = initialFieldText,
                scenario = scenario,
            )
            else -> error("Unsupported inner-shadow section: $section")
        }
    }
}

internal fun UiTreeBuilder.GraphicsShadowListFixture(scenario: DemoScenarioSpec?) {
    InstallGraphicsShadowLifecycle()
    val state = rememberGraphicsShadowPageState()
    LazyColumn(
        items = GraphicsShadowListItems,
        key = { it },
        contentType = ::graphicsShadowContentType,
        modifier = Modifier.fillMaxWidth(),
    ) { section ->
        when {
            section == "shadow_diagnostics" -> GraphicsShadowDiagnosticsSection(state, scenario)
            section == "shadow_lazy_intro" -> GraphicsShadowLazyIntroSection()
            section.startsWith(GRAPHICS_SHADOW_LAZY_ITEM_PREFIX) -> {
                val index = section.removePrefix(GRAPHICS_SHADOW_LAZY_ITEM_PREFIX).toInt()
                GraphicsShadowLazyItem(index, scenario)
            }
            else -> error("Unsupported shadow-list section: $section")
        }
    }
}

private fun UiTreeBuilder.GraphicsSingleOuterShadowSection(scenario: DemoScenarioSpec?) {
    ScenarioSection(
        kind = ScenarioKind.Core,
        title = stringResource(R.string.demo_graphics_outer_single_title),
        subtitle = stringResource(R.string.demo_graphics_outer_single_summary),
    ) {
        OuterShadowSample(
            key = "outer-single",
            title = stringResource(R.string.demo_graphics_outer_single_sample_title),
            description = stringResource(
                R.string.demo_graphics_outer_single_sample_description,
            ),
            shape = UiShape.rounded(24.dp),
            shadows = listOf(
                UiShadow(
                    color = 0x42000000,
                    blurRadius = 14.dp,
                    offsetY = 7.dp,
                ),
            ),
            testTag = DemoTestTags.GRAPHICS_SHADOW_OUTER_SINGLE,
            modifier = Modifier.shadowScenarioTarget(scenario, DemoAutomationRole.Target),
        )
    }
}

private fun UiTreeBuilder.GraphicsMultiOuterShadowSection() {
    ScenarioSection(
        kind = ScenarioKind.Visual,
        title = stringResource(R.string.demo_graphics_outer_multi_title),
        subtitle = stringResource(R.string.demo_graphics_outer_multi_summary),
    ) {
        OuterShadowSample(
            key = "outer-multi",
            title = stringResource(R.string.demo_graphics_outer_multi_sample_title),
            description = stringResource(
                R.string.demo_graphics_outer_multi_sample_description,
            ),
            shape = UiShape.rounded(28.dp),
            shadows = listOf(
                UiShadow(
                    color = 0x553B82F6,
                    blurRadius = 18.dp,
                    offsetX = (-7).dp,
                    offsetY = (-3).dp,
                ),
                UiShadow(
                    color = 0x66D946EF,
                    blurRadius = 20.dp,
                    spreadRadius = 2.dp,
                    offsetX = 8.dp,
                    offsetY = 9.dp,
                ),
            ),
            testTag = DemoTestTags.GRAPHICS_SHADOW_OUTER_MULTI,
        )
    }
}

private fun UiTreeBuilder.GraphicsSpreadShadowSection() {
    ScenarioSection(
        kind = ScenarioKind.Visual,
        title = stringResource(R.string.demo_graphics_outer_spread_title),
        subtitle = stringResource(R.string.demo_graphics_outer_spread_summary),
    ) {
        OuterShadowSample(
            key = "outer-spread-positive",
            title = stringResource(R.string.demo_graphics_outer_positive_spread_title),
            description = stringResource(
                R.string.demo_graphics_outer_positive_spread_description,
            ),
            shape = UiShape.rounded(20.dp),
            shadows = listOf(
                UiShadow(
                    color = 0x4422C55E,
                    blurRadius = 10.dp,
                    spreadRadius = 6.dp,
                    offsetY = 3.dp,
                ),
            ),
            testTag = DemoTestTags.GRAPHICS_SHADOW_SPREAD_POSITIVE,
        )
        OuterShadowSample(
            key = "outer-spread-negative",
            title = stringResource(R.string.demo_graphics_outer_negative_spread_title),
            description = stringResource(
                R.string.demo_graphics_outer_negative_spread_description,
            ),
            shape = UiShape.rounded(20.dp),
            shadows = listOf(
                UiShadow(
                    color = 0x660F172A,
                    blurRadius = 10.dp,
                    spreadRadius = (-4).dp,
                    offsetY = 3.dp,
                ),
            ),
            testTag = DemoTestTags.GRAPHICS_SHADOW_SPREAD_NEGATIVE,
        )
    }
}

private fun UiTreeBuilder.GraphicsShapeShadowSection() {
    ScenarioSection(
        kind = ScenarioKind.Visual,
        title = stringResource(R.string.demo_graphics_outer_shape_title),
        subtitle = stringResource(R.string.demo_graphics_outer_shape_summary),
    ) {
        OuterShadowSample(
            key = "outer-cut-shape",
            title = stringResource(R.string.demo_graphics_outer_shape_sample_title),
            description = stringResource(
                R.string.demo_graphics_outer_shape_sample_description,
            ),
            shape = UiShape.cut(18.dp),
            shadows = listOf(
                UiShadow(
                    color = 0x551D4ED8,
                    blurRadius = 13.dp,
                    spreadRadius = 2.dp,
                    offsetY = 6.dp,
                ),
            ),
            testTag = DemoTestTags.GRAPHICS_SHADOW_CUT_SHAPE,
        )
    }
}

private fun UiTreeBuilder.GraphicsSingleInnerShadowSection(scenario: DemoScenarioSpec?) {
    ScenarioSection(
        kind = ScenarioKind.Core,
        title = stringResource(R.string.demo_graphics_inner_single_title),
        subtitle = stringResource(R.string.demo_graphics_inner_single_summary),
    ) {
        InnerShadowSample(
            key = "inner-single",
            title = stringResource(R.string.demo_graphics_inner_single_sample_title),
            description = stringResource(
                R.string.demo_graphics_inner_single_sample_description,
            ),
            shape = UiShape.rounded(24.dp),
            shadows = listOf(
                UiShadow(
                    color = 0x660F172A,
                    blurRadius = 10.dp,
                    offsetX = 3.dp,
                    offsetY = 4.dp,
                ),
            ),
            testTag = DemoTestTags.GRAPHICS_SHADOW_INNER_SINGLE,
            modifier = Modifier.shadowScenarioTarget(scenario, DemoAutomationRole.Target),
        )
    }
}

private fun UiTreeBuilder.GraphicsMultiInnerShadowSection() {
    ScenarioSection(
        kind = ScenarioKind.Visual,
        title = stringResource(R.string.demo_graphics_inner_multi_title),
        subtitle = stringResource(R.string.demo_graphics_inner_multi_summary),
    ) {
        InnerShadowSample(
            key = "inner-multi",
            title = stringResource(R.string.demo_graphics_inner_multi_sample_title),
            description = stringResource(
                R.string.demo_graphics_inner_multi_sample_description,
            ),
            shape = UiShape.cut(16.dp),
            shadows = listOf(
                UiShadow(
                    color = 0x664F46E5,
                    blurRadius = 8.dp,
                    offsetX = (-3).dp,
                    offsetY = (-3).dp,
                ),
                UiShadow(
                    color = 0x77000000,
                    blurRadius = 12.dp,
                    offsetX = 4.dp,
                    offsetY = 5.dp,
                ),
            ),
            testTag = DemoTestTags.GRAPHICS_SHADOW_INNER_MULTI,
        )
    }
}

private fun UiTreeBuilder.GraphicsInnerShadowInteropSection(
    fieldState: TextFieldState,
    interactionCountState: MutableState<Int>,
    initialFieldText: String,
    scenario: DemoScenarioSpec?,
) {
    ScenarioSection(
        kind = ScenarioKind.Stress,
        title = stringResource(R.string.demo_graphics_inner_interop_title),
        subtitle = stringResource(R.string.demo_graphics_inner_interop_summary),
    ) {
        Surface(
            key = "inner-interop",
            modifier = Modifier
                .fillMaxWidth()
                .shape(UiShape.rounded(24.dp))
                .innerShadow(
                    shadow = UiShadow(
                        color = 0x55000000,
                        blurRadius = 12.dp,
                        offsetY = 4.dp,
                    ),
                    shape = UiShape.rounded(24.dp),
                )
                .padding(18.dp)
                .testTag(DemoTestTags.GRAPHICS_SHADOW_INNER_INTEROP),
        ) {
            Column(
                spacing = 10.dp,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(text = stringResource(R.string.demo_graphics_inner_interop_note))
                TextField(
                    state = fieldState,
                    size = TextFieldSize.Medium,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(DemoTestTags.GRAPHICS_SHADOW_INNER_FIELD),
                )
                Button(
                    text = stringResource(
                        R.string.demo_graphics_inner_click,
                        interactionCountState.value,
                    ),
                    onClick = {
                        interactionCountState.value += 1
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(DemoTestTags.GRAPHICS_SHADOW_INNER_CLICK_BUTTON)
                        .shadowScenarioTarget(scenario, DemoAutomationRole.PrimaryAction),
                )
                Button(
                    text = stringResource(R.string.demo_graphics_reset),
                    onClick = {
                        interactionCountState.value = 0
                        fieldState.setTextAndPlaceCursorAtEnd(initialFieldText)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .shadowScenarioTarget(scenario, DemoAutomationRole.Reset),
                )
                Text(
                    text = stringResource(
                        R.string.demo_graphics_inner_click_count,
                        interactionCountState.value,
                    ),
                    color = TextDefaults.secondaryColor(),
                    modifier = Modifier
                        .testTag(DemoTestTags.GRAPHICS_SHADOW_INNER_CLICK_COUNT)
                        .shadowScenarioTarget(scenario, DemoAutomationRole.State),
                )
            }
        }
    }
}

private fun UiTreeBuilder.GraphicsShadowDiagnosticsSection(
    state: GraphicsShadowPageState,
    scenario: DemoScenarioSpec?,
) {
    val snapshot = state.diagnostics.value
    val backend = snapshot.backend
    val policyLabel = stringResource(R.string.demo_graphics_shadow_fact_policy)
    val latestBackendLabel = stringResource(
        R.string.demo_graphics_shadow_fact_latest_backend,
    )
    val outerHitsLabel = stringResource(R.string.demo_graphics_shadow_fact_outer_hits)
    val outerMissesLabel = stringResource(R.string.demo_graphics_shadow_fact_outer_misses)
    val notDrawn = stringResource(R.string.demo_graphics_shadow_not_drawn)
    ScenarioSection(
        kind = ScenarioKind.Benchmark,
        title = stringResource(R.string.demo_graphics_shadow_diagnostics_title),
        subtitle = stringResource(R.string.demo_graphics_shadow_diagnostics_summary),
    ) {
        Text(
            text = stringResource(
                R.string.demo_graphics_shadow_diagnostics_state,
                state.diagnosticSampleRevision.value,
                state.policy.value.wireValue,
            ),
            modifier = Modifier.shadowScenarioTarget(scenario, DemoAutomationRole.State),
        )
        SegmentedControl(
            items = listOf(
                stringResource(R.string.demo_graphics_shadow_policy_auto),
                stringResource(R.string.demo_graphics_shadow_policy_bitmap),
                stringResource(R.string.demo_graphics_shadow_policy_render_node),
            ),
            selectedIndex = GraphicsShadowPolicies.indexOf(state.policy.value).coerceAtLeast(0),
            onSelectionChange = { index ->
                val next = GraphicsShadowPolicies[index]
                ShadowDecorationLayer.setRenderPolicy(next)
                state.policy.value = next
                state.diagnosticSampleRevision.value += 1
                state.diagnostics.value = captureGraphicsShadowDiagnostics()
            },
            modifier = Modifier
                .fillMaxWidth()
                .testTag(DemoTestTags.GRAPHICS_SHADOW_POLICY),
        )
        Row(
            spacing = 8.dp,
            modifier = Modifier
                .fillMaxWidth()
                .margin(top = 10.dp, bottom = 10.dp),
        ) {
            Button(
                text = stringResource(R.string.demo_graphics_shadow_refresh),
                onClick = {
                    state.diagnosticSampleRevision.value += 1
                    state.diagnostics.value = captureGraphicsShadowDiagnostics()
                },
                modifier = Modifier
                    .weight(1f)
                    .testTag(DemoTestTags.GRAPHICS_SHADOW_DIAGNOSTICS_REFRESH)
                    .shadowScenarioTarget(scenario, DemoAutomationRole.PrimaryAction),
            )
            Button(
                text = stringResource(R.string.demo_graphics_shadow_clear_cache),
                onClick = {
                    ShadowDecorationLayer.clearCache()
                    ShadowDecorationLayer.resetBackendDiagnostics()
                    state.diagnosticSampleRevision.value += 1
                    state.diagnostics.value = captureGraphicsShadowDiagnostics()
                },
                modifier = Modifier
                    .weight(1f)
                    .testTag(DemoTestTags.GRAPHICS_SHADOW_CACHE_CLEAR)
                    .shadowScenarioTarget(scenario, DemoAutomationRole.SecondaryAction),
            )
        }
        Button(
            text = stringResource(R.string.demo_graphics_reset),
            onClick = {
                ShadowDecorationLayer.setRenderPolicy(ShadowRenderPolicy.Auto)
                ShadowDecorationLayer.clearCache()
                ShadowDecorationLayer.resetBackendDiagnostics()
                state.policy.value = ShadowRenderPolicy.Auto
                state.diagnosticSampleRevision.value = 0
                state.diagnostics.value = captureGraphicsShadowDiagnostics()
            },
            modifier = Modifier
                .fillMaxWidth()
                .margin(bottom = 10.dp)
                .shadowScenarioTarget(scenario, DemoAutomationRole.Reset),
        )
        OuterShadowSample(
            key = "diagnostic-${state.policy.value.wireValue}-${state.diagnosticSampleRevision.value}",
            title = stringResource(R.string.demo_graphics_shadow_probe_title),
            description = stringResource(R.string.demo_graphics_shadow_probe_description),
            shape = UiShape.rounded(18.dp),
            shadows = LazyItemShadows,
            testTag = DemoTestTags.GRAPHICS_SHADOW_DIAGNOSTIC_SAMPLE,
        )
        DiagnosticFactGroup(
            title = stringResource(R.string.demo_graphics_shadow_backend_group),
            facts = listOf(
                DiagnosticFact(policyLabel, backend.policy.wireValue),
                DiagnosticFact(
                    latestBackendLabel,
                    backend.lastDecision?.backend?.name ?: notDrawn,
                ),
                DiagnosticFact(
                    stringResource(R.string.demo_graphics_shadow_fact_decision_reason),
                    backend.lastDecision?.reason?.name ?: notDrawn,
                ),
                DiagnosticFact(
                    stringResource(R.string.demo_graphics_shadow_fact_bitmap_draws),
                    backend.bitmapDraws.toString(),
                ),
                DiagnosticFact(
                    stringResource(R.string.demo_graphics_shadow_fact_render_node_draws),
                    backend.renderNodeDraws.toString(),
                ),
                DiagnosticFact(
                    stringResource(R.string.demo_graphics_shadow_fact_render_node_records),
                    backend.renderNodeRecordings.toString(),
                ),
                DiagnosticFact(
                    stringResource(R.string.demo_graphics_shadow_fact_render_node_hits),
                    backend.renderNodeCacheHits.toString(),
                ),
                DiagnosticFact(
                    stringResource(R.string.demo_graphics_shadow_fact_render_node_cache),
                    stringResource(
                        R.string.demo_graphics_shadow_kib,
                        backend.renderNodeCachedBytes / 1024,
                    ),
                ),
            ),
            valueTagsByLabel = mapOf(
                policyLabel to DemoTestTags.GRAPHICS_SHADOW_BACKEND_POLICY,
                latestBackendLabel to DemoTestTags.GRAPHICS_SHADOW_BACKEND_ACTUAL,
            ),
        )
        DiagnosticFactGroup(
            title = stringResource(R.string.demo_graphics_shadow_raster_group),
            facts = listOf(
                DiagnosticFact(outerHitsLabel, snapshot.outerCache.hits.toString()),
                DiagnosticFact(outerMissesLabel, snapshot.outerCache.misses.toString()),
                DiagnosticFact(
                    stringResource(R.string.demo_graphics_shadow_fact_outer_evictions),
                    snapshot.outerCache.evictions.toString(),
                ),
                DiagnosticFact(
                    stringResource(R.string.demo_graphics_shadow_fact_outer_oversized),
                    snapshot.outerCache.oversizedSkips.toString(),
                ),
                DiagnosticFact(
                    stringResource(R.string.demo_graphics_shadow_fact_outer_cache),
                    stringResource(
                        R.string.demo_graphics_shadow_kib,
                        snapshot.outerCache.cachedBytes / 1024,
                    ),
                ),
                DiagnosticFact(
                    stringResource(R.string.demo_graphics_shadow_fact_inner_hits),
                    snapshot.innerCache.hits.toString(),
                ),
                DiagnosticFact(
                    stringResource(R.string.demo_graphics_shadow_fact_inner_misses),
                    snapshot.innerCache.misses.toString(),
                ),
                DiagnosticFact(
                    stringResource(R.string.demo_graphics_shadow_fact_inner_cache),
                    stringResource(
                        R.string.demo_graphics_shadow_kib,
                        snapshot.innerCache.cachedBytes / 1024,
                    ),
                ),
            ),
            valueTagsByLabel = mapOf(
                outerHitsLabel to DemoTestTags.GRAPHICS_SHADOW_CACHE_HITS,
                outerMissesLabel to DemoTestTags.GRAPHICS_SHADOW_CACHE_MISSES,
            ),
        )
        val decisions = backend.decisionsByReason.entries
            .joinToString(separator = " · ") { (reason, count) -> "${reason.name}=$count" }
            .ifEmpty { notDrawn }
        Text(
            text = stringResource(R.string.demo_graphics_shadow_decisions, decisions),
            color = TextDefaults.secondaryColor(),
            modifier = Modifier.margin(top = 8.dp),
        )
    }
}

private fun UiTreeBuilder.GraphicsShadowLazyIntroSection() {
    ScenarioSection(
        kind = ScenarioKind.Stress,
        title = stringResource(R.string.demo_graphics_shadow_lazy_title),
        subtitle = stringResource(R.string.demo_graphics_shadow_lazy_summary),
    ) {
        Text(
            text = stringResource(
                R.string.demo_graphics_shadow_lazy_facts,
                GRAPHICS_SHADOW_LAZY_ITEM_COUNT,
            ),
        )
        Text(
            text = stringResource(R.string.demo_graphics_shadow_lazy_instruction),
            color = TextDefaults.secondaryColor(),
        )
    }
}

private fun UiTreeBuilder.GraphicsShadowLazyItem(
    index: Int,
    scenario: DemoScenarioSpec?,
) {
    val tagModifier = if (index == 0) {
        Modifier.testTag(DemoTestTags.GRAPHICS_SHADOW_LAZY_FIRST)
    } else {
        Modifier
    }
    Surface(
        key = "shadow-lazy-$index",
        modifier = Modifier
            .fillMaxWidth()
            .height(72.dp)
            .margin(left = 18.dp, right = 18.dp, bottom = 10.dp)
            .shape(LazyItemShape)
            .dropShadows(
                shadows = LazyItemShadows,
                shape = LazyItemShape,
            )
            .then(tagModifier)
            .then(
                if (index == 0) {
                    Modifier.shadowScenarioTarget(scenario, DemoAutomationRole.Target)
                } else {
                    Modifier
                },
            ),
    ) {
        Column(
            spacing = 4.dp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
        ) {
            Text(text = stringResource(R.string.demo_graphics_shadow_row_title, index))
            Text(
                text = stringResource(R.string.demo_graphics_shadow_row_summary),
                color = TextDefaults.secondaryColor(),
            )
        }
    }
}

private fun UiTreeBuilder.OuterShadowSample(
    key: String,
    title: String,
    description: String,
    shape: UiShape,
    shadows: List<UiShadow>,
    testTag: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .margin(bottom = 10.dp)
            .backgroundColor(SurfaceDefaults.variantBackgroundColor())
            .shape(UiShape.rounded(24.dp))
            .padding(28.dp),
    ) {
        Surface(
            key = key,
            modifier = Modifier
                .fillMaxWidth()
                .height(112.dp)
                .shape(shape)
                .dropShadows(
                    shadows = shadows,
                    shape = shape,
                )
                .testTag(testTag)
                .then(modifier),
        ) {
            Column(
                spacing = 6.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp),
            ) {
                Text(text = title)
                Text(
                    text = description,
                    color = TextDefaults.secondaryColor(),
                )
            }
        }
    }
}

private fun UiTreeBuilder.InnerShadowSample(
    key: String,
    title: String,
    description: String,
    shape: UiShape,
    shadows: List<UiShadow>,
    testTag: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        key = key,
        modifier = Modifier
            .fillMaxWidth()
            .height(128.dp)
            .margin(bottom = 10.dp)
            .shape(shape)
            .innerShadows(
                shadows = shadows,
                shape = shape,
            )
            .testTag(testTag)
            .then(modifier),
    ) {
        Column(
            spacing = 6.dp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(22.dp),
        ) {
            Text(text = title)
            Text(
                text = description,
                color = TextDefaults.secondaryColor(),
            )
        }
    }
}

private fun captureGraphicsShadowDiagnostics(): GraphicsShadowDiagnosticsSnapshot {
    return GraphicsShadowDiagnosticsSnapshot(
        outerCache = ShadowDecorationLayer.cacheStats(),
        innerCache = ShadowDecorationLayer.innerCacheStats(),
        backend = ShadowDecorationLayer.backendStats(),
    )
}

private fun Modifier.shadowScenarioTarget(
    scenario: DemoScenarioSpec?,
    role: DemoAutomationRole,
): Modifier {
    val target = scenario?.automation?.get(role) ?: return this
    return demoAutomationTarget(target)
}

private val LazyItemShape = UiShape.rounded(16.dp)

private val LazyItemShadows = listOf(
    UiShadow(
        color = 0x26000000,
        blurRadius = 6.dp,
        offsetY = 2.dp,
    ),
    UiShadow(
        color = 0x16000000,
        blurRadius = 12.dp,
        spreadRadius = 1.dp,
        offsetY = 5.dp,
    ),
)
