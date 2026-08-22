package com.viewcompose

import com.viewcompose.demo.automation.demoAutomationTarget
import com.viewcompose.demo.contract.DemoAutomationRole
import com.viewcompose.demo.contract.DemoScenarioId
import com.viewcompose.demo.contract.DemoScenarioSpec
import com.viewcompose.demo.registry.DemoScenarioIds
import com.viewcompose.host.android.resources.stringResource
import com.viewcompose.preview.tooling.ViewComposePreview
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
import com.viewcompose.ui.foundation.Box
import com.viewcompose.ui.foundation.Button
import com.viewcompose.ui.foundation.Column
import com.viewcompose.ui.foundation.Image
import com.viewcompose.ui.foundation.HorizontalPager
import com.viewcompose.ui.foundation.LazyColumn
import com.viewcompose.ui.foundation.Row
import com.viewcompose.ui.foundation.SegmentedControl
import com.viewcompose.ui.foundation.SurfaceDefaults
import com.viewcompose.ui.foundation.StaticContentRevision
import com.viewcompose.ui.foundation.TabRow
import com.viewcompose.ui.foundation.Text
import com.viewcompose.ui.foundation.TextField
import com.viewcompose.ui.foundation.TextDefaults
import com.viewcompose.ui.foundation.UiTextStyle
import com.viewcompose.ui.foundation.UiTreeBuilder
import com.viewcompose.ui.foundation.VerticalPager
import com.viewcompose.ui.unit.dp
import com.viewcompose.ui.foundation.key
import com.viewcompose.ui.foundation.produceState
import com.viewcompose.ui.foundation.remember
import com.viewcompose.ui.foundation.rememberTextFieldState
import com.viewcompose.ui.unit.sp
import com.viewcompose.viewmodel.savedStateHandle

@ViewComposePreview(name = "State · Core", group = "Demo/Pages")
internal fun UiTreeBuilder.PreviewStateCore() {
    StatePage(StateFixture.RuntimeState, onOpenDiagnostics = {})
}

@ViewComposePreview(name = "State · Identity", group = "Demo/Pages")
internal fun UiTreeBuilder.PreviewStateIdentity() {
    StatePage(StateFixture.KeyIdentity, onOpenDiagnostics = {})
}

@ViewComposePreview(name = "State · Patch", group = "Demo/Pages")
internal fun UiTreeBuilder.PreviewStatePatch() {
    StatePage(StateFixture.ViewPatch, onOpenDiagnostics = {})
}

internal enum class StateFixture(
    val scenarioId: DemoScenarioId,
) {
    RuntimeState(DemoScenarioIds.RuntimeState),
    KeyIdentity(DemoScenarioIds.RuntimeKeyIdentity),
    ViewPatch(DemoScenarioIds.RuntimeViewPatch),
    ;

    companion object {
        fun from(scenarioId: DemoScenarioId): StateFixture =
            entries.singleOrNull { fixture -> fixture.scenarioId == scenarioId }
                ?: error("Unsupported state scenario: $scenarioId")
    }
}

internal fun UiTreeBuilder.StatePage(
    fixture: StateFixture,
    scenario: DemoScenarioSpec? = null,
    onOpenDiagnostics: () -> Unit,
) {
    when (fixture) {
        StateFixture.RuntimeState -> RuntimeStateFixture(scenario)
        StateFixture.KeyIdentity -> KeyIdentityFixture(scenario)
        StateFixture.ViewPatch -> ViewPatchFixture(scenario, onOpenDiagnostics)
    }
}

private fun UiTreeBuilder.RuntimeStateFixture(scenario: DemoScenarioSpec?) {
    val benchmarkStepState = remember { mutableStateOf(0) }
    val clickCountState = remember { mutableStateOf(0) }
    val summaryState = remember {
        derivedStateOf { clickCountState.value }
    }
    val timelineState = produceState(
        initialValue = -1,
        clickCountState.value,
    ) {
        value = clickCountState.value
    }
    val vmStateHandle = savedStateHandle(key = "state_page_vm_counter")
    val vmCounterState = vmStateHandle
        .getStateFlow("counter", 0)
        .collectAsStateWithLifecycle()

    LazyColumn(
        items = listOf("benchmark", "counter", "viewmodel"),
        key = { it },
        modifier = Modifier.fillMaxSize(),
    ) { section ->
        when (section) {
            "benchmark" -> ScenarioSection(
                kind = ScenarioKind.Benchmark,
                title = stringResource(R.string.demo_state_benchmark_title),
                subtitle = stringResource(R.string.demo_state_benchmark_summary),
            ) {
                Text(
                    text = stringResource(
                        R.string.demo_state_benchmark_step,
                        benchmarkStepState.value,
                    ),
                    modifier = Modifier
                        .margin(bottom = 8.dp)
                        .scenarioTarget(scenario, DemoAutomationRole.State),
                )
                Button(
                    text = stringResource(
                        R.string.demo_state_benchmark_advance,
                        benchmarkStepState.value,
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .margin(bottom = 8.dp)
                        .scenarioTarget(scenario, DemoAutomationRole.PrimaryAction),
                    onClick = {
                        benchmarkStepState.value = benchmarkStepState.value + 1
                    },
                )
                Button(
                    text = stringResource(R.string.demo_state_benchmark_reset),
                    modifier = Modifier
                        .fillMaxWidth()
                        .margin(bottom = 8.dp)
                        .scenarioTarget(scenario, DemoAutomationRole.Reset),
                    onClick = {
                        benchmarkStepState.value = 0
                        clickCountState.value = 0
                        vmStateHandle["counter"] = 0
                    },
                )
            }

            "counter" -> ScenarioSection(
                kind = ScenarioKind.Core,
                title = stringResource(R.string.demo_state_counter_title),
                subtitle = stringResource(R.string.demo_state_counter_summary),
            ) {
                val summary = summaryState.value
                Text(
                    text = stringResource(R.string.demo_state_click_count, clickCountState.value),
                    modifier = Modifier.scenarioTarget(scenario, DemoAutomationRole.Target),
                )
                Text(
                    text = when {
                        summary == 0 -> stringResource(R.string.demo_state_summary_none)
                        summary % 2 == 0 -> stringResource(R.string.demo_state_summary_even, summary)
                        else -> stringResource(R.string.demo_state_summary_odd, summary)
                    },
                    color = TextDefaults.secondaryColor(),
                    modifier = Modifier
                        .padding(vertical = 4.dp),
                )
                Text(
                    text = if (timelineState.value < 0) {
                        stringResource(R.string.demo_state_timeline_waiting)
                    } else {
                        stringResource(R.string.demo_state_timeline_committed, timelineState.value)
                    },
                    style = UiTextStyle(fontSizeSp = 13.sp),
                    color = TextDefaults.secondaryColor(),
                )
                Row(
                    spacing = 8.dp,
                    verticalAlignment = com.viewcompose.ui.layout.VerticalAlignment.Center,
                    modifier = Modifier.margin(top = 12.dp),
                ) {
                    Button(
                        text = stringResource(R.string.demo_state_increment),
                        onClick = {
                            clickCountState.value = clickCountState.value + 1
                        },
                    )
                    Button(
                        text = stringResource(R.string.demo_state_reset),
                        onClick = {
                            clickCountState.value = 0
                        },
                    )
                }
            }

            "viewmodel" -> ScenarioSection(
                kind = ScenarioKind.Core,
                title = stringResource(R.string.demo_state_viewmodel_title),
                subtitle = stringResource(R.string.demo_state_viewmodel_summary),
            ) {
                Text(
                    text = stringResource(R.string.demo_state_viewmodel_count, vmCounterState.value),
                    modifier = Modifier
                        .margin(bottom = 8.dp)
                        .testTag(DemoStateTestTags.STATE_VM_COUNTER),
                )
                Button(
                    text = stringResource(R.string.demo_state_viewmodel_increment),
                    onClick = {
                        vmStateHandle["counter"] = vmCounterState.value + 1
                    },
                    modifier = Modifier.testTag(DemoStateTestTags.STATE_VM_INCREMENT),
                )
            }

            else -> error("Unsupported runtime state section: $section")
        }
    }
}

private fun UiTreeBuilder.KeyIdentityFixture(scenario: DemoScenarioSpec?) {
    val panelVisibleState = remember { mutableStateOf(true) }
    val panelGenerationState = remember { mutableStateOf(0) }

    LazyColumn(
        items = listOf("panel"),
        key = { it },
        modifier = Modifier.fillMaxSize(),
    ) { section ->
        when (section) {
            "panel" -> ScenarioSection(
                kind = ScenarioKind.Visual,
                title = stringResource(R.string.demo_state_panel_title),
                subtitle = stringResource(R.string.demo_state_panel_summary),
            ) {
                Column(
                    spacing = 8.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .scenarioTarget(scenario, DemoAutomationRole.Target),
                ) {
                    Text(
                        text = stringResource(
                            if (panelVisibleState.value) {
                                R.string.demo_state_identity_visible
                            } else {
                                R.string.demo_state_identity_hidden
                            },
                        ),
                        modifier = Modifier.scenarioTarget(scenario, DemoAutomationRole.State),
                    )
                    Button(
                        text = if (panelVisibleState.value) {
                            stringResource(R.string.demo_state_panel_hide)
                        } else {
                            stringResource(R.string.demo_state_panel_show)
                        },
                        modifier = Modifier.scenarioTarget(scenario, DemoAutomationRole.PrimaryAction),
                        onClick = {
                            panelVisibleState.value = !panelVisibleState.value
                        },
                    )
                    Button(
                        text = stringResource(R.string.demo_state_identity_reset),
                        modifier = Modifier.scenarioTarget(scenario, DemoAutomationRole.Reset),
                        onClick = {
                            panelVisibleState.value = true
                            panelGenerationState.value = panelGenerationState.value + 1
                        },
                    )
                    Text(
                        text = stringResource(R.string.demo_state_panel_visibility),
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
                        key("transient-panel-${panelGenerationState.value}") {
                            val panelTapState = remember { mutableStateOf(0) }
                            Column(
                                spacing = 8.dp,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .backgroundColor(SurfaceDefaults.variantBackgroundColor())
                                    .padding(12.dp),
                            ) {
                                Text(text = stringResource(R.string.demo_state_panel_keyed))
                                Button(
                                    text = stringResource(R.string.demo_state_panel_taps, panelTapState.value),
                                    onClick = {
                                        panelTapState.value = panelTapState.value + 1
                                    },
                                )
                            }
                        }
                    }
                }
            }

            else -> error("Unsupported key identity section: $section")
        }
    }
}

private fun UiTreeBuilder.ViewPatchFixture(
    scenario: DemoScenarioSpec?,
    onOpenDiagnostics: () -> Unit,
) {
    val patchStepState = remember { mutableStateOf(0) }
    val patchFieldValueState = rememberTextFieldState("value-0")
    val patchSegmentIndexState = remember { mutableStateOf(0) }
    val patchTabIndexState = remember { mutableStateOf(0) }
    val stableTabIndexState = remember { mutableStateOf(0) }
    val stableVerticalPagerIndexState = remember { mutableStateOf(0) }

    LazyColumn(
        items = listOf("patch"),
        key = { it },
        modifier = Modifier.fillMaxSize(),
    ) { section ->
        when (section) {
            "patch" -> ScenarioSection(
                kind = ScenarioKind.Benchmark,
                title = stringResource(R.string.demo_state_patch_title),
                subtitle = stringResource(R.string.demo_state_patch_summary),
            ) {
                val step = patchStepState.value
                Text(
                    text = stringResource(R.string.demo_state_patch_heading, step),
                    modifier = Modifier.scenarioTarget(scenario, DemoAutomationRole.State),
                )
                Text(
                    text = stringResource(R.string.demo_state_patch_nodes),
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
                        text = stringResource(R.string.demo_state_patch_advance, step),
                        modifier = Modifier
                            .testTag(DemoStateTestTags.STATE_PATCH_ADVANCE)
                            .scenarioTarget(scenario, DemoAutomationRole.PrimaryAction),
                        onClick = {
                            val nextStep = patchStepState.value + 1
                            patchStepState.value = nextStep
                            patchFieldValueState.setTextAndPlaceCursorAtEnd("value-$nextStep")
                            patchSegmentIndexState.value = nextStep % 3
                            patchTabIndexState.value = nextStep % 2
                        },
                    )
                    Button(
                        text = stringResource(R.string.demo_state_patch_reset),
                        modifier = Modifier.scenarioTarget(scenario, DemoAutomationRole.Reset),
                        onClick = {
                            patchStepState.value = 0
                            patchFieldValueState.setTextAndPlaceCursorAtEnd("value-0")
                            patchSegmentIndexState.value = 0
                            patchTabIndexState.value = 0
                            stableTabIndexState.value = 0
                            stableVerticalPagerIndexState.value = 0
                        },
                    )
                }
                Button(
                    text = stringResource(R.string.demo_state_patch_action, step),
                    onClick = {},
                    modifier = Modifier.margin(bottom = 12.dp),
                )
                Button(
                    text = stringResource(R.string.demo_state_patch_open_diagnostics),
                    onClick = onOpenDiagnostics,
                    modifier = Modifier
                        .margin(bottom = 12.dp)
                        .testTag(DemoStateTestTags.STATE_PATCH_OPEN_DIAGNOSTICS),
                )
                TextField(
                    state = patchFieldValueState,
                    label = stringResource(R.string.demo_state_patch_field_label),
                    supportingText = stringResource(R.string.demo_state_patch_field_support, step),
                    modifier = Modifier
                        .margin(bottom = 12.dp)
                        .scenarioTarget(scenario, DemoAutomationRole.Target),
                )
                SegmentedControl(
                    items = demoSegmentedItems(
                        "alpha" to stringResource(R.string.demo_state_patch_segment_alpha),
                        "beta" to stringResource(R.string.demo_state_patch_segment_beta),
                        "gamma" to stringResource(R.string.demo_state_patch_segment_gamma),
                    ),
                    selectedIndex = patchSegmentIndexState.value,
                    onSelectionChange = { patchSegmentIndexState.value = it },
                    modifier = Modifier.margin(bottom = 12.dp),
                )
                Text(
                    text = stringResource(
                        R.string.demo_state_patch_segment_index,
                        patchSegmentIndexState.value,
                    ),
                    color = TextDefaults.secondaryColor(),
                    modifier = Modifier
                        .margin(bottom = 12.dp)
                        .testTag(DemoStateTestTags.STATE_PATCH_SEGMENT_SUMMARY),
                )
                Row(
                    spacing = if (step % 2 == 0) 8.dp else 16.dp,
                    arrangement = if (step % 2 == 0) MainAxisArrangement.Start else MainAxisArrangement.SpaceEvenly,
                    verticalAlignment = VerticalAlignment.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .margin(bottom = 12.dp),
                ) {
                    Text(text = stringResource(R.string.demo_state_patch_row_a))
                    Text(text = stringResource(R.string.demo_state_patch_row_b))
                    Text(text = stringResource(R.string.demo_state_patch_row_c))
                }
                Column(
                    spacing = if (step % 2 == 0) 4.dp else 12.dp,
                    arrangement = if (step % 2 == 0) MainAxisArrangement.Start else MainAxisArrangement.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .margin(bottom = 12.dp),
                ) {
                    Text(text = stringResource(R.string.demo_state_patch_column_item_one))
                    Text(text = stringResource(R.string.demo_state_patch_column_item_two))
                }
                Box(
                    contentAlignment = if (step % 2 == 0) BoxAlignment.TopStart else BoxAlignment.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .backgroundColor(SurfaceDefaults.variantBackgroundColor())
                        .padding(12.dp)
                        .margin(bottom = 12.dp),
                ) {
                    Text(text = stringResource(R.string.demo_state_patch_box_content, step))
                }
                Image(
                    source = ImageSource.Resource(android.R.drawable.ic_menu_gallery),
                    tint = if (step % 2 == 0) 0xFF000000.toInt() else 0xFFFF0000.toInt(),
                    modifier = Modifier.margin(bottom = 12.dp),
                )
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = stringResource(R.string.demo_state_patch_tab_index, patchTabIndexState.value),
                        color = TextDefaults.secondaryColor(),
                        modifier = Modifier
                            .margin(bottom = 8.dp)
                            .testTag(DemoStateTestTags.STATE_PATCH_TAB_SUMMARY),
                    )
                    TabRow(
                        selectedIndex = patchTabIndexState.value,
                        onTabSelected = { patchTabIndexState.value = it },
                    ) {
                        Tab(key = "summary", contentRevision = StaticContentRevision) { selected ->
                            Text(
                                text = stringResource(R.string.demo_state_patch_tab_summary),
                                color = if (selected) TextDefaults.primaryColor() else TextDefaults.secondaryColor(),
                            )
                        }
                        Tab(key = "details", contentRevision = StaticContentRevision) { selected ->
                            Text(
                                text = stringResource(R.string.demo_state_patch_tab_details),
                                color = if (selected) TextDefaults.primaryColor() else TextDefaults.secondaryColor(),
                                modifier = Modifier.testTag(DemoStateTestTags.STATE_PATCH_TAB_DETAILS),
                            )
                        }
                    }
                    HorizontalPager(
                        currentPage = patchTabIndexState.value,
                        onPageChanged = { patchTabIndexState.value = it },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Page(key = "summary", contentRevision = "summary-$step") {
                            Column(
                                spacing = 8.dp,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .backgroundColor(SurfaceDefaults.variantBackgroundColor())
                                    .padding(12.dp),
                            ) {
                                Text(
                                    text = stringResource(R.string.demo_state_patch_page_summary, step),
                                    modifier = Modifier.testTag(DemoStateTestTags.STATE_HORIZONTAL_PAGER_SUMMARY),
                                )
                                Text(
                                    text = stringResource(R.string.demo_state_patch_page_summary_note),
                                    color = TextDefaults.secondaryColor(),
                                )
                            }
                        }
                        Page(key = "details", contentRevision = "details-$step") {
                            Column(
                                spacing = 8.dp,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .backgroundColor(SurfaceDefaults.variantBackgroundColor())
                                    .padding(12.dp),
                            ) {
                                Text(
                                    text = stringResource(R.string.demo_state_patch_page_details, step),
                                    modifier = Modifier.testTag(DemoStateTestTags.STATE_HORIZONTAL_PAGER_DETAILS),
                                )
                                Text(
                                    text = stringResource(R.string.demo_state_patch_page_details_note),
                                    color = TextDefaults.secondaryColor(),
                                )
                            }
                        }
                    }
                }
                Text(
                    text = stringResource(R.string.demo_state_stable_pager_note),
                    color = TextDefaults.secondaryColor(),
                    modifier = Modifier
                        .padding(top = 12.dp, bottom = 4.dp),
                )
                Column(modifier = Modifier.fillMaxWidth()) {
                    TabRow(
                        selectedIndex = stableTabIndexState.value,
                        onTabSelected = { stableTabIndexState.value = it },
                    ) {
                        Tab(key = "stable-summary", contentRevision = StaticContentRevision) { selected ->
                            Text(
                                text = stringResource(R.string.demo_state_stable_tab_summary),
                                color = if (selected) TextDefaults.primaryColor() else TextDefaults.secondaryColor(),
                            )
                        }
                        Tab(key = "stable-details", contentRevision = StaticContentRevision) { selected ->
                            Text(
                                text = stringResource(R.string.demo_state_stable_tab_details),
                                color = if (selected) TextDefaults.primaryColor() else TextDefaults.secondaryColor(),
                            )
                        }
                    }
                    HorizontalPager(
                        currentPage = stableTabIndexState.value,
                        onPageChanged = { stableTabIndexState.value = it },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Page(key = "stable-summary", contentRevision = step) {
                            Column(
                                spacing = 8.dp,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .backgroundColor(SurfaceDefaults.variantBackgroundColor())
                                    .padding(12.dp),
                            ) {
                                Text(
                                    text = stringResource(R.string.demo_state_stable_summary, step),
                                    modifier = Modifier.testTag(DemoStateTestTags.STATE_STABLE_SUMMARY),
                                )
                                Text(
                                    text = stringResource(R.string.demo_state_stable_summary_note),
                                    color = TextDefaults.secondaryColor(),
                                )
                            }
                        }
                        Page(key = "stable-details", contentRevision = step) {
                            Column(
                                spacing = 8.dp,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .backgroundColor(SurfaceDefaults.variantBackgroundColor())
                                    .padding(12.dp),
                            ) {
                                Text(text = stringResource(R.string.demo_state_stable_details, step))
                                Text(
                                    text = stringResource(R.string.demo_state_stable_details_note),
                                    color = TextDefaults.secondaryColor(),
                                )
                            }
                        }
                    }
                }
                Text(
                    text = stringResource(R.string.demo_state_vertical_pager_note),
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
                    Page(key = "vertical-summary", contentRevision = step) {
                        Column(
                            spacing = 8.dp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .backgroundColor(SurfaceDefaults.variantBackgroundColor())
                                .padding(12.dp),
                        ) {
                            Text(
                                text = stringResource(R.string.demo_state_vertical_summary, step),
                                modifier = Modifier.testTag(DemoStateTestTags.STATE_VERTICAL_PAGER_SUMMARY),
                            )
                            Text(
                                text = stringResource(R.string.demo_state_vertical_summary_note),
                                color = TextDefaults.secondaryColor(),
                            )
                        }
                    }
                    Page(key = "vertical-details", contentRevision = step) {
                        Column(
                            spacing = 8.dp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .backgroundColor(SurfaceDefaults.variantBackgroundColor())
                                .padding(12.dp),
                        ) {
                            Text(
                                text = stringResource(R.string.demo_state_vertical_details, step),
                                modifier = Modifier.testTag(DemoStateTestTags.STATE_VERTICAL_PAGER_DETAILS),
                            )
                            Text(
                                text = stringResource(R.string.demo_state_vertical_details_note),
                                color = TextDefaults.secondaryColor(),
                            )
                        }
                    }
                }
            }

            else -> error("Unsupported view patch section: $section")
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
