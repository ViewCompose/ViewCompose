package com.viewcompose

import android.view.Choreographer
import android.view.ViewGroup
import com.viewcompose.preview.tooling.ViewComposePreview
import com.viewcompose.ui.modifier.Modifier
import com.viewcompose.ui.modifier.fillMaxSize
import com.viewcompose.ui.modifier.padding
import com.viewcompose.ui.modifier.testTag
import com.viewcompose.runtime.mutableStateOf
import com.viewcompose.runtime.Snapshot
import com.viewcompose.runtime.SnapshotApplyConflictException
import com.viewcompose.renderer.view.tree.LayoutPassTracker
import com.viewcompose.ui.environment.UiEnvironmentValues
import com.viewcompose.ui.environment.UiLayoutDirection
import com.viewcompose.ui.environment.UiLocaleList
import com.viewcompose.ui.node.policy.CollectionMotionPolicy
import com.viewcompose.ui.unit.UiDensity
import com.viewcompose.ui.foundation.Button
import com.viewcompose.ui.foundation.DisposableEffect
import com.viewcompose.ui.foundation.Environment
import com.viewcompose.ui.foundation.LazyColumn
import com.viewcompose.ui.foundation.SideEffect
import com.viewcompose.ui.foundation.Text
import com.viewcompose.ui.foundation.TextDefaults
import com.viewcompose.ui.foundation.Theme
import com.viewcompose.ui.foundation.RenderTreeNode
import com.viewcompose.ui.foundation.UiEnvironment
import com.viewcompose.ui.foundation.UiTextStyle
import com.viewcompose.ui.foundation.UiTreeBuilder
import com.viewcompose.ui.unit.dp
import com.viewcompose.ui.foundation.remember
import com.viewcompose.ui.unit.sp
import com.viewcompose.runtime.MutableState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val SNAPSHOT_STABLE_FRAME_THRESHOLD = 2
private val DIAGNOSTICS_COMMON_PAGE_ITEMS = listOf("page", "page_filter")
private val DIAGNOSTICS_RENDERER_PAGE_ITEMS = listOf(
    "renderer_actions",
    "renderer_probe",
    "renderer_snapshots",
    "renderer_tree",
    "renderer_composition",
    "renderer_layout",
)

@ViewComposePreview(name = "Diagnostics · Runtime", group = "Demo/Pages")
internal fun UiTreeBuilder.PreviewDiagnosticsRuntime() {
    DiagnosticsPage(
        root = null,
        selectedPageState = mutableStateOf(0),
    )
}

@ViewComposePreview(name = "Diagnostics · Theme", group = "Demo/Pages")
internal fun UiTreeBuilder.PreviewDiagnosticsTheme() {
    DiagnosticsPage(
        root = null,
        selectedPageState = mutableStateOf(1),
    )
}

@ViewComposePreview(name = "Diagnostics · Renderer", group = "Demo/Pages")
internal fun UiTreeBuilder.PreviewDiagnosticsRenderer() {
    DiagnosticsPage(
        root = null,
        selectedPageState = mutableStateOf(2),
    )
}

internal fun diagnosticsPageItems(selectedPage: Int): List<String> {
    return DIAGNOSTICS_COMMON_PAGE_ITEMS + when (selectedPage) {
        0 -> listOf("benchmark", "runtime", "verify")
        1 -> DIAGNOSTICS_THEME_PAGE_ITEMS + "theme_verify"
        else -> DIAGNOSTICS_RENDERER_PAGE_ITEMS + "verify"
    }
}

internal fun UiTreeBuilder.DiagnosticsPage(
    root: ViewGroup?,
    selectedPageState: MutableState<Int>,
    autoRefreshOnEnter: Boolean = false,
    entryHint: String? = null,
) {
    val pendingSnapshotRefreshState = remember { mutableStateOf(autoRefreshOnEnter) }
    val snapshotRefreshRequestTokenState = remember { mutableStateOf(if (autoRefreshOnEnter) 1 else 0) }
    val scheduledSnapshotRefreshTokenState = remember { mutableStateOf(-1) }
    val snapshotRefreshVersionState = remember { mutableStateOf(0) }
    val snapshotFollowUntilStableState = remember { mutableStateOf(autoRefreshOnEnter) }
    val snapshotStableFrameCountState = remember { mutableStateOf(0) }
    val benchmarkRefreshCountState = remember { mutableStateOf(0) }
    val renderSnapshotState = remember { mutableStateOf(DemoRenderDiagnosticsStore.latestSnapshot()) }
    val patchSnapshotState = remember { mutableStateOf(DemoRenderDiagnosticsStore.latestPatchActiveSnapshot()) }
    val layoutSnapshotState = remember { mutableStateOf(LayoutPassTracker.snapshot()) }
    val snapshotHistorySummaryState = remember { mutableStateOf(buildRenderHistorySummary()) }
    if (pendingSnapshotRefreshState.value) {
        val refreshToken = snapshotRefreshRequestTokenState.value
        SideEffect {
            if (scheduledSnapshotRefreshTokenState.value == refreshToken) {
                return@SideEffect
            }
            scheduledSnapshotRefreshTokenState.value = refreshToken
            val choreographer = Choreographer.getInstance()
            lateinit var requestNextFrame: () -> Unit
            requestNextFrame = {
                choreographer.postFrameCallback {
                    if (!pendingSnapshotRefreshState.value || snapshotRefreshRequestTokenState.value != refreshToken) {
                        applyStateMutationWithRetry {
                            scheduledSnapshotRefreshTokenState.value = -1
                        }
                        return@postFrameCallback
                    }
                    val previousSnapshot = renderSnapshotState.value
                    val previousPatchSnapshot = patchSnapshotState.value
                    val previousLayoutSnapshot = layoutSnapshotState.value
                    val latestSnapshot = DemoRenderDiagnosticsStore.latestSnapshot()
                    val latestPatchSnapshot = DemoRenderDiagnosticsStore.latestPatchActiveSnapshot()
                    val latestLayoutSnapshot = LayoutPassTracker.snapshot()
                    LayoutPassTracker.stop()
                    val hasSnapshotChanged = latestSnapshot != previousSnapshot ||
                        latestPatchSnapshot != previousPatchSnapshot ||
                        latestLayoutSnapshot != previousLayoutSnapshot
                    val latestHistorySummary = buildRenderHistorySummary()
                    val shouldUpdateHistorySummary = latestHistorySummary != snapshotHistorySummaryState.value
                    val currentFollowUntilStable = snapshotFollowUntilStableState.value
                    val currentStableFrameCount = snapshotStableFrameCountState.value
                    val nextStableFrameCount = if (currentFollowUntilStable) {
                        if (hasSnapshotChanged) 0 else currentStableFrameCount + 1
                    } else {
                        currentStableFrameCount
                    }
                    val shouldContinueFollowing = currentFollowUntilStable &&
                        nextStableFrameCount < SNAPSHOT_STABLE_FRAME_THRESHOLD

                    val applied = applyStateMutationWithRetry {
                        if (latestSnapshot != previousSnapshot) {
                            renderSnapshotState.value = latestSnapshot
                        }
                        if (latestPatchSnapshot != previousPatchSnapshot) {
                            patchSnapshotState.value = latestPatchSnapshot
                        }
                        if (latestLayoutSnapshot != previousLayoutSnapshot) {
                            layoutSnapshotState.value = latestLayoutSnapshot
                        }
                        if (shouldUpdateHistorySummary) {
                            snapshotHistorySummaryState.value = latestHistorySummary
                        }
                        if (hasSnapshotChanged) {
                            snapshotRefreshVersionState.value = snapshotRefreshVersionState.value + 1
                        }
                        if (currentFollowUntilStable) {
                            snapshotStableFrameCountState.value = if (shouldContinueFollowing) {
                                nextStableFrameCount
                            } else {
                                0
                            }
                            pendingSnapshotRefreshState.value = shouldContinueFollowing
                            snapshotFollowUntilStableState.value = shouldContinueFollowing
                        } else {
                            pendingSnapshotRefreshState.value = false
                        }
                        scheduledSnapshotRefreshTokenState.value = -1
                    }
                    if (!applied) {
                        requestNextFrame()
                    }
                }
            }
            requestNextFrame()
        }
    }
    val pageItems = diagnosticsPageItems(selectedPageState.value)
    LazyColumn(
        items = pageItems,
        key = { it },
        motionPolicy = CollectionMotionPolicy(disableItemAnimator = true),
        modifier = Modifier.fillMaxSize(),
    ) { section ->
        when (section) {
            "page" -> ChapterPageOverviewSection(
                title = "诊断",
                goal = "将 demo 变为运行时 locals、主题 token 消费和渲染器 patch 的手动回归控制台。",
                modules = listOf("debug logging", "theme diagnostics", "renderer"),
            )

            "page_filter" -> ChapterPageFilterSection(
                pages = listOf("运行时", "主题", "渲染器"),
                selectedIndex = selectedPageState.value,
                onSelectionChange = { selectedPageState.value = it },
            )

            "benchmark" -> ScenarioSection(
                kind = ScenarioKind.Benchmark,
                title = "Diagnostics Benchmark 锚点",
                subtitle = "此区块固定在默认的运行时页面，让 benchmark 控件始终保持在首屏可见。",
            ) {
                Text(
                    text = "诊断刷新次数 ${benchmarkRefreshCountState.value}",
                    modifier = Modifier.padding(bottom = 8.dp),
                )
                Button(
                    text = "刷新 Diagnostics Benchmark",
                    onClick = {
                        benchmarkRefreshCountState.value = benchmarkRefreshCountState.value + 1
                        snapshotRefreshRequestTokenState.value = snapshotRefreshRequestTokenState.value + 1
                        snapshotFollowUntilStableState.value = true
                        snapshotStableFrameCountState.value = 0
                        pendingSnapshotRefreshState.value = true
                    },
                    modifier = Modifier.padding(bottom = 8.dp),
                )
                Button(
                    text = "重置 Diagnostics Benchmark",
                    onClick = {
                        benchmarkRefreshCountState.value = 0
                        snapshotRefreshRequestTokenState.value = snapshotRefreshRequestTokenState.value + 1
                        snapshotFollowUntilStableState.value = true
                        snapshotStableFrameCountState.value = 0
                        pendingSnapshotRefreshState.value = true
                    },
                    modifier = Modifier.padding(bottom = 8.dp),
                )
                BenchmarkRouteCallout(
                    route = "Launcher -> MainActivity(extra=diagnostics) -> Diagnostics -> Diagnostics Benchmark Anchor",
                    stableTargets = listOf(
                        "Refresh Diagnostics Benchmark",
                        "Reset Diagnostics Benchmark",
                    ),
                )
                Text(
                    text = "稳定路径: launcher -> diagnostics module -> benchmark anchor",
                    style = UiTextStyle(fontSizeSp = 12.sp),
                    color = TextDefaults.secondaryColor(),
                )
            }

            "runtime" -> ScenarioSection(
                kind = ScenarioKind.Core,
                title = "运行时快照",
                subtitle = "此章节将成为状态失效、local 传播和副作用边界的手动检查入口。",
            ) {
                DiagnosticFactGroup(
                    title = "运行时数据",
                    facts = listOf(
                        DiagnosticFact("调试日志", "已启用 (ViewComposeSample)"),
                        DiagnosticFact("区域设置", Environment.localeTags.firstOrNull() ?: "und"),
                        DiagnosticFact("布局方向", Environment.layoutDirection.name),
                        DiagnosticFact("密度", "${"%.2f".format(Locale.US, Environment.density.density)}x"),
                        DiagnosticFact("图片加载器", "Coil 集成已在 demo 中启用"),
                    ),
                )
                DiagnosticFactGroup(
                    title = "主题 Token",
                    facts = listOf(
                        DiagnosticFact("Background", Theme.colors.background.asColorHex()),
                        DiagnosticFact("Surface", Theme.colors.surface.asColorHex()),
                        DiagnosticFact("Primary", Theme.colors.primary.asColorHex()),
                        DiagnosticFact("Secondary", Theme.colors.secondary.asColorHex()),
                        DiagnosticFact("Pressed", (0x22000000 or (Theme.colors.onSurface and 0x00FFFFFF)).asColorHex()),
                        DiagnosticFact("Card shape", Theme.shapes.medium.demoLabel()),
                    ),
                )
                UiEnvironment(
                    values = UiEnvironmentValues(
                        density = UiDensity(
                            density = 1.25f,
                            fontScale = 1f,
                        ),
                        locales = UiLocaleList.of("en-US"),
                        layoutDirection = UiLayoutDirection.Ltr,
                    ),
                ) {
                    DiagnosticFactGroup(
                        title = "局部环境覆盖示例",
                        facts = listOf(
                            DiagnosticFact("示例 density", "${"%.2f".format(Locale.US, Environment.density.density)}x"),
                            DiagnosticFact("示例 locale", Environment.localeTags.firstOrNull() ?: "und"),
                            DiagnosticFact("示例 direction", Environment.layoutDirection.name),
                        ),
                    )
                }
            }

            in DIAGNOSTICS_THEME_PAGE_ITEMS -> DiagnosticsThemeSection(section, root)

            "renderer_actions" -> ScenarioSection(
                kind = ScenarioKind.Benchmark,
                title = "渲染器操作",
                subtitle = "将手动探针放在顶部附近，使诊断刷新在反复测试和 benchmark 运行中易于触达。",
            ) {
                if (!entryHint.isNullOrBlank()) {
                    Text(
                        text = entryHint,
                        color = TextDefaults.secondaryColor(),
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                }
                Text(
                    text = "快照刷新序号: ${snapshotRefreshVersionState.value}",
                    color = TextDefaults.secondaryColor(),
                    modifier = Modifier
                        .padding(bottom = 8.dp)
                        .testTag(DemoTestTags.DIAGNOSTICS_RENDER_REFRESH_SEQUENCE),
                )
                Button(
                    text = "刷新渲染器快照",
                    onClick = {
                        snapshotRefreshRequestTokenState.value = snapshotRefreshRequestTokenState.value + 1
                        snapshotFollowUntilStableState.value = true
                        snapshotStableFrameCountState.value = 0
                        pendingSnapshotRefreshState.value = true
                    },
                    modifier = Modifier
                        .padding(bottom = 8.dp)
                        .testTag(DemoTestTags.DIAGNOSTICS_RENDERER_REFRESH),
                )
                if (pendingSnapshotRefreshState.value) {
                    Text(
                        text = "正在捕获渲染器快照…",
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                }
                Button(
                    text = "重置布局计数器",
                    onClick = {
                        LayoutPassTracker.start()
                        snapshotRefreshRequestTokenState.value = snapshotRefreshRequestTokenState.value + 1
                        snapshotFollowUntilStableState.value = true
                        snapshotStableFrameCountState.value = 0
                        pendingSnapshotRefreshState.value = true
                    },
                    modifier = Modifier.padding(bottom = 8.dp),
                )
            }

            "renderer_probe" -> ScenarioSection(
                kind = ScenarioKind.Core,
                title = "渲染器重组探针",
                subtitle = "优先显示 patch/rebind/skip 的轻量探针，详细快照按后续条目延迟创建。",
            ) {
                if (!entryHint.isNullOrBlank()) {
                    Text(
                        text = "提示: 请优先查看“最近 Patch-Active 快照”中的 patched/rebound/skipped。",
                        color = TextDefaults.secondaryColor(),
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                }
                val snapshot = renderSnapshotState.value
                val patchSnapshot = patchSnapshotState.value
                val layoutSnapshot = layoutSnapshotState.value
                val patchCapturedAt = patchSnapshot?.updatedAtMillis?.formatDiagnosticsTime() ?: "尚未捕获"
                val patchPatchedCount = patchSnapshot?.stats?.patchedNodes ?: 0
                val renderProbeKey = listOf(
                    snapshotRefreshVersionState.value,
                    snapshot.renderCount,
                    snapshot.updatedAtMillis,
                    patchPatchedCount,
                    patchCapturedAt,
                    layoutSnapshot.totalMeasureCount,
                    layoutSnapshot.totalLayoutCount,
                ).joinToString(separator = "|")
                val renderProbeTickState = remember { mutableStateOf(0) }
                DisposableEffect(renderProbeKey) {
                    renderProbeTickState.value = renderProbeTickState.value + 1
                    onDispose {}
                }
                val probeHash = "snapshot=${System.identityHashCode(snapshot)} " +
                    "patch=${patchSnapshot?.let { System.identityHashCode(it) } ?: 0} " +
                    "layout=${System.identityHashCode(layoutSnapshot)}"
                Text(
                    text = "渲染次数(探针): ${snapshot.renderCount}",
                    color = TextDefaults.secondaryColor(),
                    modifier = Modifier
                        .padding(bottom = 4.dp)
                        .testTag(DemoTestTags.DIAGNOSTICS_RENDER_COUNT),
                )
                Text(
                    text = "更新时间(探针): ${snapshot.updatedAtMillis.formatDiagnosticsTime()}",
                    color = TextDefaults.secondaryColor(),
                    modifier = Modifier
                        .padding(bottom = 8.dp)
                        .testTag(DemoTestTags.DIAGNOSTICS_RENDER_UPDATED_AT),
                )
                Text(
                    text = "Patch-active patched(探针): $patchPatchedCount",
                    color = TextDefaults.secondaryColor(),
                    modifier = Modifier
                        .padding(bottom = 4.dp)
                        .testTag(DemoTestTags.DIAGNOSTICS_PATCH_ACTIVE_PATCHED),
                )
                Text(
                    text = "Patch-active 捕获时间(探针): $patchCapturedAt",
                    color = TextDefaults.secondaryColor(),
                    modifier = Modifier
                        .padding(bottom = 8.dp)
                        .testTag(DemoTestTags.DIAGNOSTICS_PATCH_ACTIVE_CAPTURED_AT),
                )
                DiagnosticFactGroup(
                    title = "关键重组探针（优先看这里）",
                    facts = listOf(
                        DiagnosticFact("探针 Key", renderProbeKey),
                        DiagnosticFact("探针 Tick", renderProbeTickState.value.toString()),
                        DiagnosticFact("对象 Hash", probeHash),
                        DiagnosticFact("最近回调历史", snapshotHistorySummaryState.value),
                    ),
                    valueTagsByLabel = mapOf(
                        "探针 Key" to DemoTestTags.DIAGNOSTICS_RENDER_PROBE_KEY,
                        "探针 Tick" to DemoTestTags.DIAGNOSTICS_RENDER_PROBE_TICK,
                        "对象 Hash" to DemoTestTags.DIAGNOSTICS_RENDER_PROBE_HASH,
                        "最近回调历史" to DemoTestTags.DIAGNOSTICS_RENDER_HISTORY,
                    ),
                )
            }

            "renderer_snapshots" -> ScenarioSection(
                kind = ScenarioKind.Core,
                title = "渲染器快照",
                subtitle = "展示最近的完整帧与 Patch-Active 帧统计。",
            ) {
                val snapshot = renderSnapshotState.value
                val patchSnapshot = patchSnapshotState.value
                val patchCapturedAt = patchSnapshot?.updatedAtMillis?.formatDiagnosticsTime() ?: "尚未捕获"
                val patchPatchedCount = patchSnapshot?.stats?.patchedNodes ?: 0
                DiagnosticFactGroup(
                    title = "最近渲染快照（只用于辅助阅读）",
                    facts = listOf(
                        DiagnosticFact("快照序号", snapshotRefreshVersionState.value.toString()),
                        DiagnosticFact("渲染次数", snapshot.renderCount.toString()),
                        DiagnosticFact("更新时间", snapshot.updatedAtMillis.formatDiagnosticsTime()),
                        DiagnosticFact("插入", snapshot.stats.inserts.toString()),
                        DiagnosticFact("复用", snapshot.stats.reuses.toString()),
                        DiagnosticFact("移除", snapshot.stats.removals.toString()),
                        DiagnosticFact("已 Patch", snapshot.stats.patchedNodes.toString()),
                        DiagnosticFact("已重绑", snapshot.stats.reboundNodes.toString()),
                        DiagnosticFact("已跳过", snapshot.stats.skippedBindings.toString()),
                        DiagnosticFact("子树跳过", snapshot.stats.skippedSubtrees.toString()),
                        DiagnosticFact("VNode 数量", snapshot.structure.vnodeCount.toString()),
                        DiagnosticFact("已挂载数量", snapshot.structure.mountedNodeCount.toString()),
                        DiagnosticFact("VNode 深度", snapshot.structure.maxVNodeDepth.toString()),
                        DiagnosticFact("挂载深度", snapshot.structure.maxMountedDepth.toString()),
                    ),
                )
                DiagnosticFactGroup(
                    title = "最近 Patch-Active 快照（只用于辅助阅读）",
                    facts = listOf(
                        DiagnosticFact("捕获时间", patchCapturedAt),
                        DiagnosticFact("已 Patch", patchPatchedCount.toString()),
                        DiagnosticFact("已重绑", patchSnapshot?.stats?.reboundNodes?.toString() ?: "0"),
                        DiagnosticFact("已跳过", patchSnapshot?.stats?.skippedBindings?.toString() ?: "0"),
                        DiagnosticFact("子树跳过", patchSnapshot?.stats?.skippedSubtrees?.toString() ?: "0"),
                        DiagnosticFact("挂载深度", patchSnapshot?.structure?.maxMountedDepth?.toString() ?: "0"),
                        DiagnosticFact("警告", patchSnapshot?.warnings?.joinToString() ?: "无"),
                    ),
                )
                val bindingsByType = patchSnapshot?.stats?.bindingsByType
                if (bindingsByType != null && bindingsByType.isNotEmpty()) {
                    DiagnosticFactGroup(
                        title = "按节点类型的绑定明细",
                        facts = bindingsByType.entries
                            .sortedByDescending { it.value.patched + it.value.rebound }
                            .map { (type, stats) ->
                                val typeName = type::class.simpleName ?: "?"
                                DiagnosticFact(
                                    typeName,
                                    "patched=${stats.patched}  rebound=${stats.rebound}  skipped=${stats.skipped}",
                                )
                        },
                    )
                }
            }

            "renderer_tree" -> ScenarioSection(
                kind = ScenarioKind.Core,
                title = "渲染树与 Patch 时间线",
                subtitle = "仅在滚动到此条目时创建树和时间线诊断内容。",
            ) {
                val snapshot = renderSnapshotState.value
                val patchSnapshot = patchSnapshotState.value
                val inspectorSnapshot = patchSnapshot ?: snapshot
                if (inspectorSnapshot.patches.isNotEmpty()) {
                    DiagnosticFactGroup(
                        title = "Patch 时间线（最新在后）",
                        facts = inspectorSnapshot.patches
                            .takeLast(16)
                            .mapIndexed { index, patch ->
                                val typeName = patch.type::class.simpleName ?: "?"
                                val movement = if (patch.moved) " moved" else ""
                                DiagnosticFact(
                                    "#${index + 1} ${patch.operation}",
                                    "$typeName key=${patch.key ?: "∅"} parent=${patch.parentKey ?: "root"} " +
                                        "index=${patch.index}$movement ${patch.detail.orEmpty()}",
                                )
                            },
                    )
                }
                val treeFacts = flattenRenderTree(inspectorSnapshot.tree).take(24)
                if (treeFacts.isNotEmpty()) {
                    DiagnosticFactGroup(
                        title = "Render Tree（前 24 个节点）",
                        facts = treeFacts,
                    )
                }
            }

            "renderer_composition" -> ScenarioSection(
                kind = ScenarioKind.Core,
                title = "重组与 Local",
                subtitle = "检查失效范围、重组原因以及捕获的 CompositionLocal。",
            ) {
                val snapshot = renderSnapshotState.value
                val patchSnapshot = patchSnapshotState.value
                val inspectorSnapshot = patchSnapshot ?: snapshot
                val composition = inspectorSnapshot.composition
                DiagnosticFactGroup(
                    title = "重组原因",
                    facts = listOf(
                        DiagnosticFact("失效 scope", composition.invalidatedScopeCount.toString()),
                        DiagnosticFact("已重组 scope", composition.recomposedScopeCount.toString()),
                        DiagnosticFact("已跳过 scope", composition.skippedScopeCount.toString()),
                    ) + composition.scopes
                        .filter { scope -> scope.recomposed || scope.reasons.isNotEmpty() }
                        .take(12)
                        .map { scope ->
                            DiagnosticFact(
                                scope.path,
                                "${scope.reasons.joinToString().ifEmpty { "Dirty" }} · " +
                                    "${scope.signature} · recomposed=${scope.recomposed}",
                            )
                        },
                )
                val localFacts = composition.scopes
                    .flatMap { scope ->
                        scope.locals.map { local ->
                            DiagnosticFact(
                                "${local.name} @ ${scope.path}",
                                local.value,
                            )
                        }
                    }
                    .distinct()
                    .take(16)
                if (localFacts.isNotEmpty()) {
                    DiagnosticFactGroup(
                        title = "CompositionLocal 浏览器",
                        facts = localFacts,
                    )
                }
            }

            "renderer_layout" -> ScenarioSection(
                kind = ScenarioKind.Core,
                title = "布局与渲染模型",
                subtitle = "汇总布局采样、当前渲染模型和手动验证路径。",
            ) {
                val layoutSnapshot = layoutSnapshotState.value
                DiagnosticFactGroup(
                    title = "布局 Pass 计数器",
                    facts = listOf(
                        DiagnosticFact("总 measure 次数", layoutSnapshot.totalMeasureCount.toString()),
                        DiagnosticFact("总 layout 次数", layoutSnapshot.totalLayoutCount.toString()),
                        DiagnosticFact("measure 耗时", layoutSnapshot.totalMeasureNs.formatNsAsMs()),
                        DiagnosticFact("layout 耗时", layoutSnapshot.totalLayoutNs.formatNsAsMs()),
                    ) + layoutSnapshot.entries
                        .take(6)
                        .map { entry ->
                            DiagnosticFact(
                                entry.viewName,
                                "measure=${entry.measureCount} (${entry.totalMeasureNs.formatNsAsMs()}), " +
                                    "layout=${entry.layoutCount} (${entry.totalLayoutNs.formatNsAsMs()})",
                            )
                        },
                )
                DiagnosticFactGroup(
                    title = "当前渲染模型",
                    facts = listOf(
                        DiagnosticFact("渲染根", "单根 RenderSession"),
                        DiagnosticFact("更新模型", "根重渲染 + 基于 key 的已挂载复用"),
                        DiagnosticFact("懒容器", "逐项懒 session"),
                        DiagnosticFact("顶部导航", "TabRow + HorizontalPager via ViewPager2 + RecyclerView"),
                        DiagnosticFact("Local 传播", "跨懒容器和 pager session 捕获"),
                        DiagnosticFact("可视检查器", "Render Tree + Patch + Local + 重组原因"),
                    ),
                )
                ChecklistGroup(
                    title = "手动探针",
                    items = listOf(
                        "先看“关键重组探针”：刷新后探针 Tick 应增长，最近回调历史应追加新的 rN/pN 片段。",
                        "进入 State -> Patch Stress 做几次切换，再返回这里点击刷新，确认最近回调历史和 Patch-active patched 都增长。",
                        "点击重置布局计数器后进入 Layouts / Input / Foundations，再回来刷新，确认布局 Pass 计数器主要由自定义容器增长。",
                        "切到 Layouts 或 Collections 压力页后再回来，确认挂载深度和 VNode 深度会跟随复杂场景变化。",
                        "打开 Layouts / Collections 压力页，观察日志中 VNode tree 与 Reconcile 摘要是否稳定。",
                        "切换章节并返回，确认 debug 日志仍持续输出到 ViewComposeSample。",
                        "遇到视觉 bug 时，先用这里的渲染模型判断问题更像 layout、list diff 还是 local 传播。",
                    ),
                )
            }

            "theme_verify" -> DiagnosticsThemeVerificationSection()

            else -> VerificationNotesSection(
                what = "在假设视觉 bug 属于 widget、layout 或 runtime 层之前，诊断应是首先检查的地方。",
                howToVerify = listOf(
                    "切换 theme mode 与章节，确认运行时快照始终反映当前 environment。",
                    "在 State -> Patch Stress 执行几次更新后，返回渲染器页点击刷新，确认 patched/skipped 不再始终为 0。",
                    "点击重置布局计数器，再进入一个复杂章节操作后返回，确认布局 Pass 计数器出现新的 measure/layout 增长。",
                    "对比不同章节后刷新，确认热点排序会把更贵的容器排到前面，而不是只按次数排。",
                    "切到层级更复杂的章节后再次刷新，确认渲染器页能看到 VNode/mounted 深度。",
                    "在出现渲染问题时，对照这里列出的缺口判断是已知缺口还是新回归。",
                    "结合日志观察 renderer 行为，并确认诊断页面描述与当前实现一致。",
                ),
                expected = listOf(
                    "该章节能快速告诉你当前框架还缺什么。",
                    "环境信息和主题信息不会在章节切换后失真。",
                    "渲染器页可以拿到最近一次 render 的统计快照和最近一次 patch-active 快照。",
                    "渲染器页可以看到自定义容器的 measure/layout 次数和累计耗时。",
                    "诊断会持续作为后续 inspector 的落点。",
                ),
                relatedGaps = listOf(
                    "检查器当前是手动快照，还没有节点高亮和跨 session 关联图。",
                    "还没有 deepest path、完整 frame timeline 和每节点耗时。",
                ),
            )
        }
    }
}

private fun Long.formatDiagnosticsTime(): String {
    if (this <= 0L) {
        return "尚未捕获"
    }
    return SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(Date(this))
}

private fun buildRenderHistorySummary(): String {
    val recent = DemoRenderDiagnosticsStore
        .recentSnapshots()
        .take(6)
    if (recent.isEmpty()) {
        return "无"
    }
    return recent.joinToString(separator = " -> ") { snapshot ->
        "r${snapshot.renderCount}/p${snapshot.stats.patchedNodes}"
    }
}

private fun flattenRenderTree(
    nodes: List<RenderTreeNode>,
    depth: Int = 0,
): List<DiagnosticFact> {
    return nodes.flatMapIndexed { index, node ->
        val typeName = node.type::class.simpleName ?: "?"
        val prefix = "  ".repeat(depth)
        listOf(
            DiagnosticFact(
                "$prefix$index · $typeName",
                "key=${node.key ?: "∅"} children=${node.children.size}",
            ),
        ) + flattenRenderTree(node.children, depth + 1)
    }
}

private inline fun applyStateMutationWithRetry(
    maxAttempts: Int = 3,
    crossinline mutation: () -> Unit,
): Boolean {
    repeat(maxAttempts) {
        try {
            Snapshot.withMutableSnapshot {
                mutation()
            }
            return true
        } catch (_: SnapshotApplyConflictException) {
            // Retry on next attempt.
        }
    }
    return false
}

private fun Long.formatNsAsMs(): String {
    return String.format(Locale.US, "%.2f ms", this / 1_000_000f)
}
