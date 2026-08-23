package com.viewcompose

import android.view.Choreographer
import android.view.ViewGroup
import android.widget.TextView
import com.viewcompose.demo.automation.demoAutomationTarget
import com.viewcompose.demo.contract.DemoAutomationRole
import com.viewcompose.demo.contract.DemoScenarioSpec
import com.viewcompose.host.android.resources.stringResource
import com.viewcompose.host.android.AndroidView
import com.viewcompose.preview.tooling.ViewComposePreview
import com.viewcompose.ui.modifier.Modifier
import com.viewcompose.ui.modifier.fillMaxSize
import com.viewcompose.ui.modifier.fillMaxWidth
import com.viewcompose.ui.modifier.height
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
import com.viewcompose.ui.foundation.UiTreeBuilder
import com.viewcompose.ui.unit.dp
import com.viewcompose.ui.foundation.remember
import com.viewcompose.runtime.MutableState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val SNAPSHOT_STABLE_FRAME_THRESHOLD = 2
private const val TIMING_WORKLOAD_FRAME_COUNT = 8
internal val DIAGNOSTICS_RENDERER_PAGE_ITEMS = listOf(
    "renderer_actions",
    "renderer_highlight",
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

internal fun diagnosticsPageItems(
    selectedPage: Int,
): List<String> {
    return when (selectedPage) {
        0 -> listOf("runtime")
        1 -> DIAGNOSTICS_THEME_PAGE_ITEMS
        else -> DIAGNOSTICS_RENDERER_PAGE_ITEMS
    }
}

internal fun UiTreeBuilder.DiagnosticsPage(
    root: ViewGroup?,
    selectedPageState: MutableState<Int>,
    scenario: DemoScenarioSpec? = null,
    autoRefreshOnEnter: Boolean = false,
    entryHint: String? = null,
) {
    val pendingSnapshotRefreshState = remember { mutableStateOf(autoRefreshOnEnter) }
    val snapshotRefreshRequestTokenState = remember { mutableStateOf(if (autoRefreshOnEnter) 1 else 0) }
    val scheduledSnapshotRefreshTokenState = remember { mutableStateOf(-1) }
    val snapshotRefreshVersionState = remember { mutableStateOf(0) }
    val snapshotFollowUntilStableState = remember { mutableStateOf(autoRefreshOnEnter) }
    val snapshotStableFrameCountState = remember { mutableStateOf(0) }
    val renderSnapshotState = remember { mutableStateOf(DemoRenderDiagnosticsStore.latestSnapshot()) }
    val patchSnapshotState = remember { mutableStateOf(DemoRenderDiagnosticsStore.latestPatchActiveSnapshot()) }
    val layoutSnapshotState = remember { mutableStateOf(LayoutPassTracker.snapshot()) }
    val snapshotHistorySummaryState = remember { mutableStateOf(buildRenderHistorySummary()) }
    val highlightFixtureGenerationState = remember { mutableStateOf(1) }
    val timingWorkloadFrameState = remember { mutableStateOf(0) }
    val timingWorkloadRunState = remember { mutableStateOf(0) }
    val timingWorkloadRunningState = remember { mutableStateOf(false) }
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
            "runtime" -> ScenarioSection(
                kind = ScenarioKind.Core,
                title = stringResource(R.string.demo_diagnostics_runtime_snapshot_title),
                subtitle = stringResource(R.string.demo_diagnostics_runtime_snapshot_summary),
                modifier = Modifier.scenarioTarget(scenario, DemoAutomationRole.Target),
            ) {
                DiagnosticFactGroup(
                    title = stringResource(R.string.demo_diagnostics_runtime_data),
                    facts = listOf(
                        DiagnosticFact(
                            stringResource(R.string.demo_diagnostics_debug_logging),
                            stringResource(R.string.demo_diagnostics_debug_logging_enabled),
                        ),
                        DiagnosticFact(
                            stringResource(R.string.demo_diagnostics_locale),
                            Environment.localeTags.firstOrNull() ?: "und",
                        ),
                        DiagnosticFact(
                            stringResource(R.string.demo_diagnostics_layout_direction),
                            Environment.layoutDirection.name,
                        ),
                        DiagnosticFact(
                            stringResource(R.string.demo_diagnostics_density),
                            stringResource(
                                R.string.demo_diagnostics_density_format,
                                Environment.density.density,
                            ),
                        ),
                        DiagnosticFact(
                            stringResource(R.string.demo_diagnostics_image_loader),
                            stringResource(R.string.demo_diagnostics_image_loader_coil),
                        ),
                    ),
                )
                DiagnosticFactGroup(
                    title = stringResource(R.string.demo_diagnostics_theme_tokens),
                    facts = listOf(
                        DiagnosticFact("Background", Theme.colors.background.asColorHex()),
                        DiagnosticFact("Surface", Theme.colors.surface.asColorHex()),
                        DiagnosticFact("Primary", Theme.colors.primary.asColorHex()),
                        DiagnosticFact("Secondary", Theme.colors.secondary.asColorHex()),
                        DiagnosticFact("Pressed", (0x22000000 or (Theme.colors.onSurface and 0x00FFFFFF)).asColorHex()),
                        DiagnosticFact(
                            stringResource(R.string.demo_diagnostics_card_shape),
                            Theme.shapes.medium.demoLabel(),
                        ),
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
                        title = stringResource(R.string.demo_diagnostics_local_environment),
                        facts = listOf(
                            DiagnosticFact(
                                stringResource(R.string.demo_diagnostics_sample_density),
                                stringResource(
                                    R.string.demo_diagnostics_density_format,
                                    Environment.density.density,
                                ),
                            ),
                            DiagnosticFact(
                                stringResource(R.string.demo_diagnostics_sample_locale),
                                Environment.localeTags.firstOrNull() ?: "und",
                            ),
                            DiagnosticFact(
                                stringResource(R.string.demo_diagnostics_sample_direction),
                                Environment.layoutDirection.name,
                            ),
                        ),
                    )
                }
            }

            in DIAGNOSTICS_THEME_PAGE_ITEMS -> DiagnosticsThemeSection(
                section = section,
                root = root,
                firstModifier = Modifier.scenarioTarget(
                    scenario,
                    DemoAutomationRole.Target,
                ),
                lastModifier = Modifier.scenarioTarget(
                    scenario,
                    DemoAutomationRole.SecondaryTarget,
                ),
            )

            "renderer_actions" -> ScenarioSection(
                kind = ScenarioKind.Benchmark,
                title = stringResource(R.string.demo_diagnostics_renderer_actions_title),
                subtitle = stringResource(R.string.demo_diagnostics_renderer_actions_summary),
            ) {
                if (!entryHint.isNullOrBlank()) {
                    Text(
                        text = entryHint,
                        color = TextDefaults.secondaryColor(),
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                }
                Text(
                    text = stringResource(
                        R.string.demo_diagnostics_snapshot_revision,
                        snapshotRefreshVersionState.value,
                    ),
                    color = TextDefaults.secondaryColor(),
                    modifier = Modifier
                        .padding(bottom = 8.dp)
                        .testTag(DemoDiagnosticsTestTags.DIAGNOSTICS_RENDER_REFRESH_SEQUENCE)
                        .scenarioTarget(scenario, DemoAutomationRole.State),
                )
                Button(
                    text = stringResource(R.string.demo_diagnostics_refresh_renderer),
                    onClick = {
                        snapshotRefreshRequestTokenState.value = snapshotRefreshRequestTokenState.value + 1
                        snapshotFollowUntilStableState.value = true
                        snapshotStableFrameCountState.value = 0
                        pendingSnapshotRefreshState.value = true
                    },
                    modifier = Modifier
                        .padding(bottom = 8.dp)
                        .testTag(DemoDiagnosticsTestTags.DIAGNOSTICS_RENDERER_REFRESH)
                        .scenarioTarget(scenario, DemoAutomationRole.PrimaryAction),
                )
                Button(
                    text = stringResource(R.string.demo_diagnostics_run_timing_workload),
                    enabled = !timingWorkloadRunningState.value,
                    onClick = {
                        if (!timingWorkloadRunningState.value) {
                            timingWorkloadRunningState.value = true
                            timingWorkloadFrameState.value = 0
                            val choreographer = Choreographer.getInstance()
                            lateinit var requestFrame: () -> Unit
                            requestFrame = {
                                choreographer.postFrameCallback {
                                    val nextFrame = timingWorkloadFrameState.value + 1
                                    val completed = nextFrame >= TIMING_WORKLOAD_FRAME_COUNT
                                    applyStateMutationWithRetry {
                                        timingWorkloadFrameState.value = nextFrame
                                        if (completed) {
                                            timingWorkloadRunState.value += 1
                                            timingWorkloadRunningState.value = false
                                        }
                                    }
                                    if (!completed) requestFrame()
                                }
                            }
                            requestFrame()
                        }
                    },
                    modifier = Modifier
                        .padding(bottom = 8.dp)
                        .testTag(DemoDiagnosticsTestTags.DIAGNOSTICS_TIMING_WORKLOAD),
                )
                Text(
                    text = stringResource(
                        R.string.demo_diagnostics_timing_workload_status,
                        timingWorkloadFrameState.value,
                        TIMING_WORKLOAD_FRAME_COUNT,
                        timingWorkloadRunState.value,
                    ),
                    color = TextDefaults.secondaryColor(),
                    modifier = Modifier
                        .padding(bottom = 4.dp)
                        .testTag(DemoDiagnosticsTestTags.DIAGNOSTICS_TIMING_WORKLOAD_STATUS),
                )
                Text(
                    text = if (timingWorkloadFrameState.value % 2 == 0) {
                        stringResource(
                            R.string.demo_diagnostics_timing_workload_even_fixture,
                            timingWorkloadFrameState.value,
                        )
                    } else {
                        stringResource(
                            R.string.demo_diagnostics_timing_workload_odd_fixture,
                            timingWorkloadFrameState.value,
                        )
                    },
                    modifier = Modifier
                        .padding(bottom = 8.dp)
                        .testTag(DemoDiagnosticsTestTags.DIAGNOSTICS_TIMING_WORKLOAD_FIXTURE),
                )
                if (pendingSnapshotRefreshState.value) {
                    Text(
                        text = stringResource(R.string.demo_diagnostics_capturing_renderer),
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                }
                Button(
                    text = stringResource(R.string.demo_diagnostics_reset_layout_counters),
                    onClick = {
                        LayoutPassTracker.start()
                        snapshotRefreshRequestTokenState.value = snapshotRefreshRequestTokenState.value + 1
                        snapshotFollowUntilStableState.value = true
                        snapshotStableFrameCountState.value = 0
                        pendingSnapshotRefreshState.value = true
                    },
                    modifier = Modifier
                        .padding(bottom = 8.dp)
                        .scenarioTarget(scenario, DemoAutomationRole.Reset),
                )
            }

            "renderer_highlight" -> ScenarioSection(
                kind = ScenarioKind.Core,
                title = stringResource(R.string.demo_diagnostics_highlight_title),
                subtitle = stringResource(R.string.demo_diagnostics_highlight_summary),
            ) {
                val targetLabel = stringResource(
                    R.string.demo_diagnostics_highlight_target,
                    highlightFixtureGenerationState.value,
                )
                Text(
                    text = stringResource(R.string.demo_diagnostics_highlight_instructions),
                    color = TextDefaults.secondaryColor(),
                    modifier = Modifier.padding(bottom = 8.dp),
                )
                Button(
                    text = stringResource(R.string.demo_diagnostics_highlight_replace),
                    onClick = {
                        highlightFixtureGenerationState.value += 1
                    },
                    modifier = Modifier.padding(bottom = 8.dp),
                )
                AndroidView(
                    key = "diagnostics_highlight_${highlightFixtureGenerationState.value}",
                    factory = { context ->
                        TextView(context).apply {
                            includeFontPadding = false
                            textSize = 16f
                            minimumHeight = (72f * resources.displayMetrics.density).toInt()
                            val inset = (16f * resources.displayMetrics.density).toInt()
                            setPadding(inset, inset, inset, inset)
                            setBackgroundColor(0xFFDCEBFF.toInt())
                            setTextColor(0xFF12345B.toInt())
                        }
                    },
                    update = { nativeView ->
                        (nativeView as TextView).apply {
                            text = targetLabel
                            contentDescription = targetLabel
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(88.dp)
                        .testTag(DemoDiagnosticsTestTags.DIAGNOSTICS_HIGHLIGHT_TARGET),
                )
            }

            "renderer_probe" -> ScenarioSection(
                kind = ScenarioKind.Core,
                title = stringResource(R.string.demo_diagnostics_renderer_probe_title),
                subtitle = stringResource(R.string.demo_diagnostics_renderer_probe_summary),
                modifier = Modifier.scenarioTarget(scenario, DemoAutomationRole.Target),
            ) {
                if (!entryHint.isNullOrBlank()) {
                    Text(
                        text = stringResource(R.string.demo_diagnostics_renderer_probe_hint),
                        color = TextDefaults.secondaryColor(),
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                }
                val snapshot = renderSnapshotState.value
                val patchSnapshot = patchSnapshotState.value
                val layoutSnapshot = layoutSnapshotState.value
                val notCaptured = stringResource(R.string.demo_diagnostics_not_captured)
                val patchCapturedAt = patchSnapshot?.updatedAtMillis?.formatDiagnosticsTime(notCaptured)
                    ?: notCaptured
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
                    text = stringResource(
                        R.string.demo_diagnostics_probe_render_count,
                        snapshot.renderCount,
                    ),
                    color = TextDefaults.secondaryColor(),
                    modifier = Modifier
                        .padding(bottom = 4.dp)
                        .testTag(DemoDiagnosticsTestTags.DIAGNOSTICS_RENDER_COUNT),
                )
                Text(
                    text = stringResource(
                        R.string.demo_diagnostics_probe_updated_at,
                        snapshot.updatedAtMillis.formatDiagnosticsTime(notCaptured),
                    ),
                    color = TextDefaults.secondaryColor(),
                    modifier = Modifier
                        .padding(bottom = 8.dp)
                        .testTag(DemoDiagnosticsTestTags.DIAGNOSTICS_RENDER_UPDATED_AT),
                )
                Text(
                    text = stringResource(
                        R.string.demo_diagnostics_probe_patched,
                        patchPatchedCount,
                    ),
                    color = TextDefaults.secondaryColor(),
                    modifier = Modifier
                        .padding(bottom = 4.dp)
                        .testTag(DemoDiagnosticsTestTags.DIAGNOSTICS_PATCH_ACTIVE_PATCHED),
                )
                Text(
                    text = stringResource(
                        R.string.demo_diagnostics_probe_captured_at,
                        patchCapturedAt,
                    ),
                    color = TextDefaults.secondaryColor(),
                    modifier = Modifier
                        .padding(bottom = 8.dp)
                        .testTag(DemoDiagnosticsTestTags.DIAGNOSTICS_PATCH_ACTIVE_CAPTURED_AT),
                )
                DiagnosticFactGroup(
                    title = stringResource(R.string.demo_diagnostics_key_probe),
                    facts = listOf(
                        DiagnosticFact(stringResource(R.string.demo_diagnostics_probe_key), renderProbeKey),
                        DiagnosticFact(stringResource(R.string.demo_diagnostics_probe_tick), renderProbeTickState.value.toString()),
                        DiagnosticFact(stringResource(R.string.demo_diagnostics_object_hash), probeHash),
                        DiagnosticFact(
                            stringResource(R.string.demo_diagnostics_recent_history),
                            snapshotHistorySummaryState.value.ifEmpty {
                                stringResource(R.string.demo_diagnostics_none)
                            },
                        ),
                    ),
                    valueTagsByLabel = mapOf(
                        stringResource(R.string.demo_diagnostics_probe_key) to DemoDiagnosticsTestTags.DIAGNOSTICS_RENDER_PROBE_KEY,
                        stringResource(R.string.demo_diagnostics_probe_tick) to DemoDiagnosticsTestTags.DIAGNOSTICS_RENDER_PROBE_TICK,
                        stringResource(R.string.demo_diagnostics_object_hash) to DemoDiagnosticsTestTags.DIAGNOSTICS_RENDER_PROBE_HASH,
                        stringResource(R.string.demo_diagnostics_recent_history) to DemoDiagnosticsTestTags.DIAGNOSTICS_RENDER_HISTORY,
                    ),
                )
            }

            "renderer_snapshots" -> ScenarioSection(
                kind = ScenarioKind.Core,
                title = stringResource(R.string.demo_diagnostics_renderer_snapshots_title),
                subtitle = stringResource(R.string.demo_diagnostics_renderer_snapshots_summary),
            ) {
                val snapshot = renderSnapshotState.value
                val patchSnapshot = patchSnapshotState.value
                val notCaptured = stringResource(R.string.demo_diagnostics_not_captured)
                val patchCapturedAt = patchSnapshot?.updatedAtMillis?.formatDiagnosticsTime(notCaptured)
                    ?: notCaptured
                val patchPatchedCount = patchSnapshot?.stats?.patchedNodes ?: 0
                DiagnosticFactGroup(
                    title = stringResource(R.string.demo_diagnostics_latest_render_snapshot),
                    facts = listOf(
                        DiagnosticFact(stringResource(R.string.demo_diagnostics_snapshot_revision_label), snapshotRefreshVersionState.value.toString()),
                        DiagnosticFact(stringResource(R.string.demo_diagnostics_render_count), snapshot.renderCount.toString()),
                        DiagnosticFact(stringResource(R.string.demo_diagnostics_updated_at), snapshot.updatedAtMillis.formatDiagnosticsTime(notCaptured)),
                        DiagnosticFact(stringResource(R.string.demo_diagnostics_inserts), snapshot.stats.inserts.toString()),
                        DiagnosticFact(stringResource(R.string.demo_diagnostics_reuses), snapshot.stats.reuses.toString()),
                        DiagnosticFact(stringResource(R.string.demo_diagnostics_removals), snapshot.stats.removals.toString()),
                        DiagnosticFact(stringResource(R.string.demo_diagnostics_patched), snapshot.stats.patchedNodes.toString()),
                        DiagnosticFact(stringResource(R.string.demo_diagnostics_rebound), snapshot.stats.reboundNodes.toString()),
                        DiagnosticFact(stringResource(R.string.demo_diagnostics_skipped), snapshot.stats.skippedBindings.toString()),
                        DiagnosticFact(stringResource(R.string.demo_diagnostics_subtrees_skipped), snapshot.stats.skippedSubtrees.toString()),
                        DiagnosticFact(stringResource(R.string.demo_diagnostics_vnode_count), snapshot.structure.vnodeCount.toString()),
                        DiagnosticFact(stringResource(R.string.demo_diagnostics_mounted_count), snapshot.structure.mountedNodeCount.toString()),
                        DiagnosticFact(stringResource(R.string.demo_diagnostics_vnode_depth), snapshot.structure.maxVNodeDepth.toString()),
                        DiagnosticFact(stringResource(R.string.demo_diagnostics_mounted_depth), snapshot.structure.maxMountedDepth.toString()),
                    ),
                )
                DiagnosticFactGroup(
                    title = stringResource(R.string.demo_diagnostics_latest_patch_snapshot),
                    facts = listOf(
                        DiagnosticFact(stringResource(R.string.demo_diagnostics_captured_at), patchCapturedAt),
                        DiagnosticFact(stringResource(R.string.demo_diagnostics_patched), patchPatchedCount.toString()),
                        DiagnosticFact(stringResource(R.string.demo_diagnostics_rebound), patchSnapshot?.stats?.reboundNodes?.toString() ?: "0"),
                        DiagnosticFact(stringResource(R.string.demo_diagnostics_skipped), patchSnapshot?.stats?.skippedBindings?.toString() ?: "0"),
                        DiagnosticFact(stringResource(R.string.demo_diagnostics_subtrees_skipped), patchSnapshot?.stats?.skippedSubtrees?.toString() ?: "0"),
                        DiagnosticFact(stringResource(R.string.demo_diagnostics_mounted_depth), patchSnapshot?.structure?.maxMountedDepth?.toString() ?: "0"),
                        DiagnosticFact(
                            stringResource(R.string.demo_diagnostics_warnings),
                            patchSnapshot?.warnings?.joinToString()
                                ?: stringResource(R.string.demo_diagnostics_none),
                        ),
                    ),
                )
                val bindingsByType = patchSnapshot?.stats?.bindingsByType
                if (bindingsByType != null && bindingsByType.isNotEmpty()) {
                    DiagnosticFactGroup(
                        title = stringResource(R.string.demo_diagnostics_bindings_by_node_type),
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
                title = stringResource(R.string.demo_diagnostics_render_tree_title),
                subtitle = stringResource(R.string.demo_diagnostics_render_tree_summary),
            ) {
                val snapshot = renderSnapshotState.value
                val patchSnapshot = patchSnapshotState.value
                val inspectorSnapshot = patchSnapshot ?: snapshot
                if (inspectorSnapshot.patches.isNotEmpty()) {
                    DiagnosticFactGroup(
                        title = stringResource(R.string.demo_diagnostics_patch_timeline),
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
                        title = stringResource(R.string.demo_diagnostics_render_tree_first_nodes),
                        facts = treeFacts,
                    )
                }
            }

            "renderer_composition" -> ScenarioSection(
                kind = ScenarioKind.Core,
                title = stringResource(R.string.demo_diagnostics_composition_title),
                subtitle = stringResource(R.string.demo_diagnostics_composition_summary),
            ) {
                val snapshot = renderSnapshotState.value
                val patchSnapshot = patchSnapshotState.value
                val inspectorSnapshot = patchSnapshot ?: snapshot
                val composition = inspectorSnapshot.composition
                DiagnosticFactGroup(
                    title = stringResource(R.string.demo_diagnostics_recomposition_reasons),
                    facts = listOf(
                        DiagnosticFact(stringResource(R.string.demo_diagnostics_invalidated_scopes), composition.invalidatedScopeCount.toString()),
                        DiagnosticFact(stringResource(R.string.demo_diagnostics_recomposed_scopes), composition.recomposedScopeCount.toString()),
                        DiagnosticFact(stringResource(R.string.demo_diagnostics_skipped_scopes), composition.skippedScopeCount.toString()),
                    ) + composition.scopes
                        .filter { scope -> scope.recomposed || scope.reasons.isNotEmpty() }
                        .take(12)
                        .map { scope ->
                            DiagnosticFact(
                                scope.path,
                                "${scope.reasons.joinToString().ifEmpty { stringResource(R.string.demo_diagnostics_dirty) }} · " +
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
                        title = stringResource(R.string.demo_diagnostics_composition_local_browser),
                        facts = localFacts,
                    )
                }
            }

            "renderer_layout" -> ScenarioSection(
                kind = ScenarioKind.Core,
                title = stringResource(R.string.demo_diagnostics_layout_title),
                subtitle = stringResource(R.string.demo_diagnostics_layout_summary),
            ) {
                val layoutSnapshot = layoutSnapshotState.value
                DiagnosticFactGroup(
                    title = stringResource(R.string.demo_diagnostics_layout_pass_counters),
                    facts = listOf(
                        DiagnosticFact(stringResource(R.string.demo_diagnostics_total_measure_count), layoutSnapshot.totalMeasureCount.toString()),
                        DiagnosticFact(stringResource(R.string.demo_diagnostics_total_layout_count), layoutSnapshot.totalLayoutCount.toString()),
                        DiagnosticFact(stringResource(R.string.demo_diagnostics_measure_duration), layoutSnapshot.totalMeasureNs.formatNsAsMs()),
                        DiagnosticFact(stringResource(R.string.demo_diagnostics_layout_duration), layoutSnapshot.totalLayoutNs.formatNsAsMs()),
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
            }

            else -> error("Unknown diagnostics section: $section")
        }
    }
}

private fun Modifier.scenarioTarget(
    scenario: DemoScenarioSpec?,
    role: DemoAutomationRole,
): Modifier {
    val target = scenario?.automation?.get(role) ?: return this
    return demoAutomationTarget(target)
}

private fun Long.formatDiagnosticsTime(notCaptured: String): String {
    if (this <= 0L) {
        return notCaptured
    }
    return SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(Date(this))
}

private fun buildRenderHistorySummary(): String {
    val recent = DemoRenderDiagnosticsStore
        .recentSnapshots()
        .take(6)
    if (recent.isEmpty()) {
        return ""
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
