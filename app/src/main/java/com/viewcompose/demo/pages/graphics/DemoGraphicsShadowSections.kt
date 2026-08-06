package com.viewcompose

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

internal const val GRAPHICS_PAGE_DRAWING: Int = 0
internal const val GRAPHICS_PAGE_OUTER_SHADOWS: Int = 1
internal const val GRAPHICS_PAGE_INNER_SHADOWS: Int = 2
internal const val GRAPHICS_PAGE_SHADOW_DIAGNOSTICS: Int = 3
internal const val GRAPHICS_SHADOW_LAZY_ITEM_COUNT: Int = 1_000

private const val SHADOW_LAZY_ITEM_PREFIX = "shadow_lazy_item_"

private val GraphicsCommonPageItems = listOf(
    "overview",
    "page_filter",
)

private val GraphicsDrawingPageItems = listOf(
    "primitives",
    "path_clip",
    "gradient_blend",
    "draw_modifiers",
    "cache",
    "verify",
)

private val GraphicsOuterShadowPageItems = listOf(
    "shadow_outer_single",
    "shadow_outer_multi",
    "shadow_outer_spread",
    "shadow_outer_shape",
    "verify",
)

private val GraphicsInnerShadowPageItems = listOf(
    "shadow_inner_single",
    "shadow_inner_multi",
    "shadow_inner_interop",
    "verify",
)

private val GraphicsShadowDiagnosticsPageItems = listOf(
    "shadow_diagnostics",
    "shadow_lazy_intro",
    "verify",
)

private val GraphicsShadowLazyItems = List(GRAPHICS_SHADOW_LAZY_ITEM_COUNT) { index ->
    "$SHADOW_LAZY_ITEM_PREFIX$index"
}

private val GraphicsDrawingItems = GraphicsCommonPageItems + GraphicsDrawingPageItems
private val GraphicsOuterShadowItems = GraphicsCommonPageItems + GraphicsOuterShadowPageItems
private val GraphicsInnerShadowItems = GraphicsCommonPageItems + GraphicsInnerShadowPageItems
private val GraphicsShadowDiagnosticsItems = GraphicsCommonPageItems +
    GraphicsShadowDiagnosticsPageItems +
    GraphicsShadowLazyItems

private val GraphicsShadowPolicies = listOf(
    ShadowRenderPolicy.Auto,
    ShadowRenderPolicy.ExactBitmap,
    ShadowRenderPolicy.RenderNodeDisplayList,
)

private val GraphicsShadowPolicyLabels = listOf("Auto", "Bitmap", "RenderNode")

internal fun graphicsPageItems(selectedPage: Int): List<String> {
    return when (selectedPage) {
        GRAPHICS_PAGE_DRAWING -> GraphicsDrawingItems
        GRAPHICS_PAGE_OUTER_SHADOWS -> GraphicsOuterShadowItems
        GRAPHICS_PAGE_INNER_SHADOWS -> GraphicsInnerShadowItems
        GRAPHICS_PAGE_SHADOW_DIAGNOSTICS -> GraphicsShadowDiagnosticsItems
        else -> GraphicsDrawingItems
    }
}

internal fun graphicsPageContentType(section: String): String {
    return if (section.startsWith(SHADOW_LAZY_ITEM_PREFIX)) {
        SHADOW_LAZY_ITEM_PREFIX
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
    val innerInteractionCount: MutableState<Int>,
)

internal fun UiTreeBuilder.rememberGraphicsShadowPageState(): GraphicsShadowPageState {
    return remember {
        GraphicsShadowPageState(
            policy = mutableStateOf(ShadowRenderPolicy.Auto),
            diagnostics = mutableStateOf(captureGraphicsShadowDiagnostics()),
            diagnosticSampleRevision = mutableStateOf(0),
            innerInteractionCount = mutableStateOf(0),
        )
    }
}

internal fun InstallGraphicsShadowLifecycle() {
    DisposableEffect("graphics-advanced-shadow") {
        ShadowDecorationLayer.setRenderPolicy(ShadowRenderPolicy.Auto)
        ShadowDecorationLayer.resetBackendDiagnostics()
        return@DisposableEffect {
            ShadowDecorationLayer.setRenderPolicy(ShadowRenderPolicy.Auto)
        }
    }
}

internal fun UiTreeBuilder.RenderGraphicsShadowSection(
    section: String,
    state: GraphicsShadowPageState,
) {
    when {
        section == "shadow_outer_single" -> GraphicsSingleOuterShadowSection()
        section == "shadow_outer_multi" -> GraphicsMultiOuterShadowSection()
        section == "shadow_outer_spread" -> GraphicsSpreadShadowSection()
        section == "shadow_outer_shape" -> GraphicsShapeShadowSection()
        section == "shadow_inner_single" -> GraphicsSingleInnerShadowSection()
        section == "shadow_inner_multi" -> GraphicsMultiInnerShadowSection()
        section == "shadow_inner_interop" -> GraphicsInnerShadowInteropSection(state)
        section == "shadow_diagnostics" -> GraphicsShadowDiagnosticsSection(state)
        section == "shadow_lazy_intro" -> GraphicsShadowLazyIntroSection()
        section.startsWith(SHADOW_LAZY_ITEM_PREFIX) -> {
            val index = section.removePrefix(SHADOW_LAZY_ITEM_PREFIX).toInt()
            GraphicsShadowLazyItem(index)
        }
    }
}

internal fun UiTreeBuilder.GraphicsVerificationNotes(selectedPage: Int) {
    when (selectedPage) {
        GRAPHICS_PAGE_OUTER_SHADOWS -> VerificationNotesSection(
            what = "外阴影页覆盖单层、多层、彩色偏移、正负 spread 与切角 shape。",
            howToVerify = listOf(
                "确认每张卡片外侧阴影没有被父容器裁切。",
                "确认多层和彩色阴影按声明顺序叠加。",
                "对比正负 spread，并检查切角轮廓是否和内容 shape 对齐。",
            ),
            expected = listOf(
                "阴影不改变卡片测量尺寸和周围布局。",
                "阴影跟随 shape、偏移和 spread，且没有方形边缘或残影。",
            ),
        )

        GRAPHICS_PAGE_INNER_SHADOWS -> VerificationNotesSection(
            what = "内阴影页覆盖单层、多层和输入/点击互操作，验证前景装饰平面。",
            howToVerify = listOf(
                "确认内阴影只出现在内容轮廓内部。",
                "编辑文本并点击按钮，确认前景阴影不会拦截输入和手势。",
                "切换暗色主题，确认 shape 和内容仍完整可见。",
            ),
            expected = listOf(
                "内阴影绘制在内容之上，但输入、焦点和点击保持可用。",
                "多层内阴影保留声明顺序且不会溢出 shape。",
            ),
        )

        GRAPHICS_PAGE_SHADOW_DIAGNOSTICS -> VerificationNotesSection(
            what = "Lazy/诊断页使用 1000 个稳定 key 项验证缓存复用、回收和后端选择。",
            howToVerify = listOf(
                "滚动列表后返回顶部，再点击刷新诊断。",
                "确认外阴影 cache hit 增长，miss 不随每一帧持续增长。",
                "切换后端策略并检查实际 backend、选择原因和降级计数。",
            ),
            expected = listOf(
                "滚动期间阴影稳定，无闪烁、错位或回收残留。",
                "Auto 默认保持 ExactBitmap；实验后端不满足条件时明确回退。",
            ),
        )

        else -> VerificationNotesSection(
            what = "Graphics 页覆盖 Canvas 节点、draw modifiers、渐变/混合/路径/缓存。",
            howToVerify = listOf(
                "在基础图元区确认线条、圆形、文字都可见。",
                "切换 Blend 模式，观察状态文案变化并对照图形叠色变化。",
                "切换 drawWithContent 透传，确认内容层可显示/隐藏。",
                "切换 cacheKey 与 accent，确认状态文案与图形同步更新。",
            ),
            expected = listOf(
                "Canvas 节点绘制稳定，无崩溃和空白。",
                "drawWithContent 可以控制内容层是否透传。",
                "drawWithCache 仅在 key 变化时重建缓存命令。",
            ),
        )
    }
}

private fun UiTreeBuilder.GraphicsSingleOuterShadowSection() {
    ScenarioSection(
        kind = ScenarioKind.Core,
        title = "单层精确外阴影",
        subtitle = "显式 blur、offset 和颜色，不依赖 View.elevation。",
    ) {
        OuterShadowSample(
            key = "outer-single",
            title = "Soft elevation",
            description = "blur=14dp · offsetY=7dp",
            shape = UiShape.rounded(24.dp),
            shadows = listOf(
                UiShadow(
                    color = 0x42000000,
                    blurRadius = 14.dp,
                    offsetY = 7.dp,
                ),
            ),
            testTag = DemoTestTags.GRAPHICS_SHADOW_OUTER_SINGLE,
        )
    }
}

private fun UiTreeBuilder.GraphicsMultiOuterShadowSection() {
    ScenarioSection(
        kind = ScenarioKind.Visual,
        title = "多层彩色阴影",
        subtitle = "蓝色左上光晕与紫红右下阴影按声明顺序合成。",
    ) {
        OuterShadowSample(
            key = "outer-multi",
            title = "Ordered color layers",
            description = "2 layers · independent blur and offset",
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
        title = "Spread 对照",
        subtitle = "相同 blur 下对比向外扩张与向内收缩的 mask。",
    ) {
        OuterShadowSample(
            key = "outer-spread-positive",
            title = "Positive spread",
            description = "spread=6dp",
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
            title = "Negative spread",
            description = "spread=-4dp",
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
        title = "Shape 映射",
        subtitle = "切角内容与阴影使用同一 UiShape，检查轮廓桥接精度。",
    ) {
        OuterShadowSample(
            key = "outer-cut-shape",
            title = "Cut corner shadow",
            description = "UiShape.cut(18dp)",
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

private fun UiTreeBuilder.GraphicsSingleInnerShadowSection() {
    ScenarioSection(
        kind = ScenarioKind.Core,
        title = "单层内阴影",
        subtitle = "前景装饰平面在内容完成后绘制，并裁切到圆角轮廓。",
    ) {
        InnerShadowSample(
            key = "inner-single",
            title = "Inset depth",
            description = "blur=10dp · offset=(3dp, 4dp)",
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
        )
    }
}

private fun UiTreeBuilder.GraphicsMultiInnerShadowSection() {
    ScenarioSection(
        kind = ScenarioKind.Visual,
        title = "多层内阴影",
        subtitle = "冷色顶部层与深色底部层验证声明顺序和独立偏移。",
    ) {
        InnerShadowSample(
            key = "inner-multi",
            title = "Dual inset layers",
            description = "2 layers · ordered foreground draw",
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
    state: GraphicsShadowPageState,
) {
    val fieldState = rememberTextFieldState("Inner shadow input")
    ScenarioSection(
        kind = ScenarioKind.Stress,
        title = "输入与手势互操作",
        subtitle = "内阴影覆盖在子树之上，但不能拦截 TextField、焦点、ripple 或 Button 点击。",
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
                Text(text = "Foreground decoration must remain input-transparent")
                TextField(
                    state = fieldState,
                    size = TextFieldSize.Medium,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(DemoTestTags.GRAPHICS_SHADOW_INNER_FIELD),
                )
                Button(
                    text = "验证点击 (${state.innerInteractionCount.value})",
                    onClick = {
                        state.innerInteractionCount.value += 1
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(DemoTestTags.GRAPHICS_SHADOW_INNER_CLICK_BUTTON),
                )
                Text(
                    text = "点击次数 ${state.innerInteractionCount.value}",
                    color = TextDefaults.secondaryColor(),
                    modifier = Modifier.testTag(
                        DemoTestTags.GRAPHICS_SHADOW_INNER_CLICK_COUNT,
                    ),
                )
            }
        }
    }
}

private fun UiTreeBuilder.GraphicsShadowDiagnosticsSection(
    state: GraphicsShadowPageState,
) {
    val snapshot = state.diagnostics.value
    val backend = snapshot.backend
    ScenarioSection(
        kind = ScenarioKind.Benchmark,
        title = "阴影缓存与后端诊断",
        subtitle = "切换策略后重建样本；刷新可读取实际绘制、缓存命中和降级原因。",
    ) {
        SegmentedControl(
            items = GraphicsShadowPolicyLabels,
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
                text = "刷新诊断",
                onClick = {
                    state.diagnostics.value = captureGraphicsShadowDiagnostics()
                },
                modifier = Modifier
                    .weight(1f)
                    .testTag(DemoTestTags.GRAPHICS_SHADOW_DIAGNOSTICS_REFRESH),
            )
            Button(
                text = "清空缓存",
                onClick = {
                    ShadowDecorationLayer.clearCache()
                    ShadowDecorationLayer.resetBackendDiagnostics()
                    state.diagnosticSampleRevision.value += 1
                    state.diagnostics.value = captureGraphicsShadowDiagnostics()
                },
                modifier = Modifier
                    .weight(1f)
                    .testTag(DemoTestTags.GRAPHICS_SHADOW_CACHE_CLEAR),
            )
        }
        OuterShadowSample(
            key = "diagnostic-${state.policy.value.wireValue}-${state.diagnosticSampleRevision.value}",
            title = "Backend draw probe",
            description = "切换策略或清空缓存时重建",
            shape = UiShape.rounded(18.dp),
            shadows = LazyItemShadows,
            testTag = DemoTestTags.GRAPHICS_SHADOW_DIAGNOSTIC_SAMPLE,
        )
        DiagnosticFactGroup(
            title = "Backend",
            facts = listOf(
                DiagnosticFact("策略", backend.policy.wireValue),
                DiagnosticFact("最近后端", backend.lastDecision?.backend?.name ?: "尚未绘制"),
                DiagnosticFact("选择原因", backend.lastDecision?.reason?.name ?: "尚未绘制"),
                DiagnosticFact("Bitmap draws", backend.bitmapDraws.toString()),
                DiagnosticFact("RenderNode draws", backend.renderNodeDraws.toString()),
                DiagnosticFact("RenderNode records", backend.renderNodeRecordings.toString()),
                DiagnosticFact("RenderNode hits", backend.renderNodeCacheHits.toString()),
                DiagnosticFact("RenderNode cache", backend.renderNodeCachedBytes.asKiB()),
            ),
            valueTagsByLabel = mapOf(
                "策略" to DemoTestTags.GRAPHICS_SHADOW_BACKEND_POLICY,
                "最近后端" to DemoTestTags.GRAPHICS_SHADOW_BACKEND_ACTUAL,
            ),
        )
        DiagnosticFactGroup(
            title = "Raster cache",
            facts = listOf(
                DiagnosticFact("外阴影命中", snapshot.outerCache.hits.toString()),
                DiagnosticFact("外阴影未命中", snapshot.outerCache.misses.toString()),
                DiagnosticFact("外阴影淘汰", snapshot.outerCache.evictions.toString()),
                DiagnosticFact("外阴影超预算", snapshot.outerCache.oversizedSkips.toString()),
                DiagnosticFact("外阴影缓存", snapshot.outerCache.cachedBytes.asKiB()),
                DiagnosticFact("内阴影命中", snapshot.innerCache.hits.toString()),
                DiagnosticFact("内阴影未命中", snapshot.innerCache.misses.toString()),
                DiagnosticFact("内阴影缓存", snapshot.innerCache.cachedBytes.asKiB()),
            ),
            valueTagsByLabel = mapOf(
                "外阴影命中" to DemoTestTags.GRAPHICS_SHADOW_CACHE_HITS,
                "外阴影未命中" to DemoTestTags.GRAPHICS_SHADOW_CACHE_MISSES,
            ),
        )
        Text(
            text = backend.decisionsByReason.entries
                .joinToString(
                    prefix = "Decisions: ",
                    separator = " · ",
                ) { (reason, count) -> "${reason.name}=$count" }
                .ifEmpty { "Decisions: 尚未绘制" },
            color = TextDefaults.secondaryColor(),
            modifier = Modifier.margin(top = 8.dp),
        )
    }
}

private fun UiTreeBuilder.GraphicsShadowLazyIntroSection() {
    ScenarioSection(
        kind = ScenarioKind.Stress,
        title = "Lazy 1000 项阴影",
        subtitle = "全部项目使用稳定 key、相同尺寸和双层阴影；滚动后返回可观察缓存复用。",
    ) {
        Text(text = "$GRAPHICS_SHADOW_LAZY_ITEM_COUNT items · stable keys · shared raster spec")
        Text(
            text = "向下快速滚动，再返回此处并刷新上方诊断。",
            color = TextDefaults.secondaryColor(),
        )
    }
}

private fun UiTreeBuilder.GraphicsShadowLazyItem(index: Int) {
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
            .then(tagModifier),
    ) {
        Column(
            spacing = 4.dp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
        ) {
            Text(text = "Shadow row #$index")
            Text(
                text = "stable key · shared shape · cached raster",
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
                .testTag(testTag),
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
            .testTag(testTag),
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

private fun Int.asKiB(): String = "${this / 1024} KiB"

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
