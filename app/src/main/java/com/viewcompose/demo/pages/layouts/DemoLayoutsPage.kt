package com.viewcompose

import com.viewcompose.demo.automation.demoAutomationTarget
import com.viewcompose.demo.contract.DemoAutomationRole
import com.viewcompose.demo.contract.DemoScenarioId
import com.viewcompose.demo.contract.DemoScenarioSpec
import com.viewcompose.demo.registry.DemoScenarioIds
import com.viewcompose.host.android.resources.pluralStringResource
import com.viewcompose.host.android.resources.stringResource
import com.viewcompose.preview.tooling.ViewComposePreview
import com.viewcompose.ui.layout.BoxAlignment
import com.viewcompose.ui.layout.HorizontalAlignment
import com.viewcompose.ui.layout.MainAxisArrangement
import com.viewcompose.ui.layout.VerticalAlignment
import com.viewcompose.ui.modifier.Modifier
import com.viewcompose.ui.modifier.shape
import com.viewcompose.ui.modifier.backgroundColor
import com.viewcompose.ui.modifier.clickable
import com.viewcompose.ui.modifier.cornerRadius
import com.viewcompose.ui.modifier.fillMaxSize
import com.viewcompose.ui.modifier.fillMaxWidth
import com.viewcompose.ui.modifier.height
import com.viewcompose.ui.modifier.layoutId
import com.viewcompose.ui.modifier.margin
import com.viewcompose.ui.modifier.offset
import com.viewcompose.ui.modifier.padding
import com.viewcompose.ui.modifier.testTag
import com.viewcompose.ui.modifier.visibility
import com.viewcompose.ui.modifier.zIndex
import com.viewcompose.ui.modifier.Visibility
import com.viewcompose.ui.node.ImageSource
import com.viewcompose.ui.node.spec.ConstraintChainStyle
import com.viewcompose.ui.node.spec.ConstraintDimension
import com.viewcompose.ui.node.spec.ConstraintFlowWrapMode
import com.viewcompose.ui.node.spec.ConstraintGridOrientation
import com.viewcompose.ui.node.spec.ConstraintHelperVisibility
import com.viewcompose.ui.node.spec.ConstraintMatchMode
import com.viewcompose.ui.node.spec.ConstraintRatio
import com.viewcompose.runtime.mutableStateOf
import com.viewcompose.constraintlayout.*
import com.viewcompose.ui.foundation.Box
import com.viewcompose.ui.foundation.Button
import com.viewcompose.ui.foundation.ButtonSize
import com.viewcompose.ui.foundation.ButtonVariant
import com.viewcompose.ui.foundation.Column
import com.viewcompose.ui.foundation.Divider
import com.viewcompose.ui.foundation.FlowColumn
import com.viewcompose.ui.foundation.FlowRow
import com.viewcompose.ui.foundation.Icon
import com.viewcompose.ui.foundation.LazyColumn
import com.viewcompose.ui.foundation.Row
import com.viewcompose.ui.foundation.ScrollableColumn
import com.viewcompose.ui.foundation.ScrollableRow
import com.viewcompose.ui.foundation.Spacer
import com.viewcompose.ui.foundation.Surface
import com.viewcompose.ui.foundation.SurfaceDefaults
import com.viewcompose.ui.foundation.SurfaceVariant
import com.viewcompose.ui.foundation.Text
import com.viewcompose.ui.foundation.TextDefaults
import com.viewcompose.ui.foundation.Theme
import com.viewcompose.ui.foundation.UiTextStyle
import com.viewcompose.ui.foundation.UiTreeBuilder
import com.viewcompose.ui.unit.dp
import com.viewcompose.ui.foundation.remember
import com.viewcompose.ui.unit.sp

@ViewComposePreview(name = "Layouts · Linear", group = "Demo/Pages")
internal fun UiTreeBuilder.PreviewLayoutsLinear() {
    LayoutPage(LayoutFixture.Linear)
}

@ViewComposePreview(name = "Layouts · Overlay", group = "Demo/Pages")
internal fun UiTreeBuilder.PreviewLayoutsOverlay() {
    LayoutPage(LayoutFixture.Stack)
}

@ViewComposePreview(name = "Layouts · Bounds", group = "Demo/Pages")
internal fun UiTreeBuilder.PreviewLayoutsBounds() {
    LayoutPage(LayoutFixture.Edges)
}

@ViewComposePreview(name = "Layouts · Flow", group = "Demo/Pages")
internal fun UiTreeBuilder.PreviewLayoutsFlow() {
    LayoutPage(LayoutFixture.Flow)
}

@ViewComposePreview(name = "Layouts · Scroll", group = "Demo/Pages")
internal fun UiTreeBuilder.PreviewLayoutsScroll() {
    LayoutPage(LayoutFixture.Scroll)
}

@ViewComposePreview(name = "Layouts · Constraint", group = "Demo/Pages")
internal fun UiTreeBuilder.PreviewLayoutsConstraint() {
    LayoutPage(LayoutFixture.Constraint)
}

@ViewComposePreview(name = "Layouts · Constraint Grid", group = "Demo/ConstraintLayout")
internal fun UiTreeBuilder.PreviewLayoutsConstraintGrid() {
    LayoutPage(
        fixture = LayoutFixture.Constraint,
        constraintSections = listOf("constraint_grid"),
    )
}

@ViewComposePreview(name = "Layouts · Circular Flow", group = "Demo/ConstraintLayout")
internal fun UiTreeBuilder.PreviewLayoutsCircularFlow() {
    LayoutPage(
        fixture = LayoutFixture.Constraint,
        constraintSections = listOf("constraint_circular_flow"),
    )
}

internal enum class LayoutFixture(
    val scenarioId: DemoScenarioId,
) {
    Linear(DemoScenarioIds.LayoutLinear),
    Stack(DemoScenarioIds.LayoutStack),
    Edges(DemoScenarioIds.LayoutEdges),
    Flow(DemoScenarioIds.LayoutFlow),
    Scroll(DemoScenarioIds.LayoutScroll),
    Constraint(DemoScenarioIds.LayoutConstraint),
    ;

    companion object {
        fun from(scenarioId: DemoScenarioId): LayoutFixture =
            entries.singleOrNull { fixture -> fixture.scenarioId == scenarioId }
                ?: error("Unsupported layout scenario: $scenarioId")
    }
}

internal fun UiTreeBuilder.LayoutPage(
    fixture: LayoutFixture,
    scenario: DemoScenarioSpec? = null,
    constraintSections: List<String>? = null,
) {
    val boxTapState = remember { mutableStateOf(0) }
    val benchmarkState = remember { mutableStateOf(false) }
    val useLongLabelsState = remember { mutableStateOf(false) }
    val flowItemCountState = remember { mutableStateOf(8) }
    val constraintHelperLongState = remember { mutableStateOf(false) }
    val constraintSetExpandedState = remember { mutableStateOf(false) }
    val constraintDimensionAdvancedState = remember { mutableStateOf(false) }
    val constraintHelpersFullState = remember { mutableStateOf(false) }
    val constraintVerticalChainPackedState = remember { mutableStateOf(false) }
    val constraintSetHelpersAlternateState = remember { mutableStateOf(false) }
    val constraintVirtualAlternateState = remember { mutableStateOf(false) }
    val pageItems = when (fixture) {
        LayoutFixture.Linear -> listOf("benchmark", "row", "column")
        LayoutFixture.Stack -> listOf("box")
        LayoutFixture.Edges -> listOf("edge")
        LayoutFixture.Flow -> listOf("flow")
        LayoutFixture.Scroll -> listOf("scrollable")
        LayoutFixture.Constraint -> constraintSections ?: listOf(
            "constraint_basic",
            "constraint_helpers",
            "constraint_chain",
            "constraint_grid",
            "constraint_circular_flow",
            "constraint_set",
            "constraint_virtual_helpers",
            "constraint_anchor_advanced",
            "constraint_dimension_advanced",
            "constraint_helpers_full",
            "constraint_vertical_chain",
            "constraint_set_helpers_mirror",
        )
    }
    LazyColumn(
        items = pageItems,
        key = { it },
        modifier = Modifier.fillMaxSize(),
    ) { section ->
        when (section) {
            "benchmark" -> ScenarioSection(
                kind = ScenarioKind.Benchmark,
                title = stringResource(R.string.demo_layouts_benchmark_title),
                subtitle = stringResource(R.string.demo_layouts_benchmark_summary),
            ) {
                Text(
                    text = stringResource(
                        if (benchmarkState.value) {
                            R.string.demo_layouts_state_expanded
                        } else {
                            R.string.demo_layouts_state_compact
                        },
                    ),
                    modifier = Modifier.scenarioTarget(scenario, DemoAutomationRole.State),
                )
                Button(
                    text = stringResource(
                        if (benchmarkState.value) {
                            R.string.demo_layouts_benchmark_expanded
                        } else {
                            R.string.demo_layouts_benchmark_collapsed
                        },
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .scenarioTarget(scenario, DemoAutomationRole.PrimaryAction),
                    onClick = { benchmarkState.value = !benchmarkState.value },
                )
                Row(
                    spacing = 8.dp,
                    verticalAlignment = VerticalAlignment.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .backgroundColor(SurfaceDefaults.backgroundColor())
                        .shape(SurfaceDefaults.shape())
                        .padding(12.dp)
                        .scenarioTarget(scenario, DemoAutomationRole.Target),
                ) {
                    Text(text = stringResource(R.string.demo_layouts_leading))
                    Button(
                        text = if (benchmarkState.value) {
                            stringResource(R.string.demo_layouts_benchmark_long_label)
                        } else {
                            stringResource(R.string.demo_layouts_compact)
                        },
                        modifier = Modifier.weight(1f),
                    )
                    Button(
                        text = stringResource(R.string.demo_layouts_reset),
                        variant = ButtonVariant.Outlined,
                        modifier = Modifier.scenarioTarget(scenario, DemoAutomationRole.Reset),
                        onClick = { benchmarkState.value = false },
                    )
                }
            }

            "row" -> ScenarioSection(
                kind = ScenarioKind.Core,
                title = stringResource(R.string.demo_layouts_row_title),
                subtitle = stringResource(R.string.demo_layouts_row_summary),
            ) {
                Row(
                    arrangement = MainAxisArrangement.Start,
                    verticalAlignment = VerticalAlignment.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .backgroundColor(SurfaceDefaults.backgroundColor())
                        .padding(12.dp),
                ) {
                    Text(
                        text = stringResource(R.string.demo_layouts_top),
                        modifier = Modifier
                            .align(VerticalAlignment.Top)
                            .backgroundColor(Theme.colors.surfaceVariant)
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                    )
                    FlexibleSpacer()
                    Text(
                        text = stringResource(R.string.demo_layouts_bottom),
                        modifier = Modifier
                            .align(VerticalAlignment.Bottom)
                            .backgroundColor(Theme.colors.secondary)
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                    )
                }
            }

            "box" -> ScenarioSection(
                kind = ScenarioKind.Visual,
                title = stringResource(R.string.demo_layouts_stack_title),
                subtitle = stringResource(R.string.demo_layouts_stack_summary),
            ) {
                Text(
                    text = stringResource(R.string.demo_layouts_click_state, boxTapState.value),
                    modifier = Modifier.scenarioTarget(scenario, DemoAutomationRole.State),
                )
                Box(
                    contentAlignment = BoxAlignment.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp)
                        .backgroundColor(SurfaceDefaults.variantBackgroundColor())
                        .shape(SurfaceDefaults.shape())
                        .clickable { boxTapState.value = boxTapState.value + 1 }
                        .padding(12.dp)
                        .scenarioTarget(scenario, DemoAutomationRole.PrimaryAction),
                ) {
                    Text(
                        text = stringResource(R.string.demo_layouts_center_clicks, boxTapState.value),
                        modifier = Modifier
                            .backgroundColor(Theme.colors.primary)
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                    )
                    Text(
                        text = stringResource(R.string.demo_layouts_fixed_label),
                        modifier = Modifier
                            .align(BoxAlignment.BottomEnd)
                            .offset(x = (-8).dp, y = (-8).dp)
                            .zIndex(1f)
                            .backgroundColor(Theme.colors.secondary)
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                    )
                }
                Button(
                    text = stringResource(R.string.demo_layouts_reset_clicks),
                    variant = ButtonVariant.Outlined,
                    modifier = Modifier
                        .fillMaxWidth()
                        .margin(top = 8.dp)
                        .scenarioTarget(scenario, DemoAutomationRole.Reset),
                    onClick = { boxTapState.value = 0 },
                )
            }

            "column" -> ScenarioSection(
                kind = ScenarioKind.Core,
                title = stringResource(R.string.demo_layouts_column_title),
                subtitle = stringResource(R.string.demo_layouts_column_summary),
            ) {
                Column(
                    arrangement = MainAxisArrangement.SpaceEvenly,
                    horizontalAlignment = HorizontalAlignment.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                        .backgroundColor(SurfaceDefaults.backgroundColor())
                        .padding(12.dp),
                ) {
                    Text(text = stringResource(R.string.demo_layouts_one))
                    Divider()
                    Text(text = stringResource(R.string.demo_layouts_two))
                    Divider()
                    Text(text = stringResource(R.string.demo_layouts_three))
                }
            }

            "edge" -> ScenarioSection(
                kind = ScenarioKind.Stress,
                title = stringResource(R.string.demo_layouts_edges_title),
                subtitle = stringResource(R.string.demo_layouts_edges_summary),
            ) {
                Text(
                    text = stringResource(
                        if (useLongLabelsState.value) {
                            R.string.demo_layouts_label_mode_long
                        } else {
                            R.string.demo_layouts_label_mode_short
                        },
                    ),
                    modifier = Modifier.scenarioTarget(scenario, DemoAutomationRole.State),
                )
                Button(
                    text = stringResource(
                        if (useLongLabelsState.value) {
                            R.string.demo_layouts_use_short_labels
                        } else {
                            R.string.demo_layouts_use_long_labels
                        },
                    ),
                    modifier = Modifier
                        .margin(bottom = 12.dp)
                        .scenarioTarget(scenario, DemoAutomationRole.PrimaryAction),
                    onClick = { useLongLabelsState.value = !useLongLabelsState.value },
                )
                Row(
                    spacing = 8.dp,
                    verticalAlignment = VerticalAlignment.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .backgroundColor(SurfaceDefaults.backgroundColor())
                        .shape(SurfaceDefaults.shape())
                        .padding(12.dp)
                        .margin(bottom = 12.dp)
                        .scenarioTarget(scenario, DemoAutomationRole.Target),
                ) {
                    Surface(modifier = Modifier.padding(8.dp)) {
                        Icon(
                            source = ImageSource.Resource(R.drawable.demo_media_icon),
                            contentDescription = stringResource(R.string.demo_layouts_probe_icon_description),
                            modifier = Modifier.testTag(DemoTestTags.LAYOUTS_EDGE_PROBE_ICON),
                        )
                    }
                    Button(
                        text = if (useLongLabelsState.value) {
                            stringResource(R.string.demo_layouts_weighted_long)
                        } else {
                            stringResource(R.string.demo_layouts_weighted)
                        },
                        modifier = Modifier
                            .weight(1f)
                            .testTag(DemoTestTags.LAYOUTS_EDGE_WEIGHTED),
                    )
                    Button(
                        text = stringResource(R.string.demo_layouts_action),
                        variant = ButtonVariant.Outlined,
                        modifier = Modifier
                            .weight(1f)
                            .testTag(DemoTestTags.LAYOUTS_EDGE_ACTION),
                    )
                }
                Button(
                    text = stringResource(R.string.demo_layouts_reset_edges),
                    variant = ButtonVariant.Outlined,
                    modifier = Modifier
                        .fillMaxWidth()
                        .margin(top = 8.dp)
                        .scenarioTarget(scenario, DemoAutomationRole.Reset),
                    onClick = { useLongLabelsState.value = false },
                )
                Row(
                    spacing = 8.dp,
                    verticalAlignment = VerticalAlignment.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .backgroundColor(SurfaceDefaults.variantBackgroundColor())
                        .shape(SurfaceDefaults.shape())
                        .padding(12.dp),
                ) {
                    Surface(modifier = Modifier.padding(8.dp)) {
                        Text(text = stringResource(R.string.demo_layouts_wrap))
                    }
                    Surface(
                        variant = SurfaceVariant.Variant,
                        modifier = Modifier.padding(8.dp),
                    ) {
                        Text(text = stringResource(R.string.demo_layouts_still_wrap))
                    }
                    Text(
                        text = stringResource(R.string.demo_layouts_nested_surface_note),
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            "flow" -> ScenarioSection(
                kind = ScenarioKind.Core,
                title = stringResource(R.string.demo_layouts_flow_title),
                subtitle = stringResource(R.string.demo_layouts_flow_summary),
            ) {
                Text(
                    text = pluralStringResource(
                        R.plurals.demo_layouts_flow_count,
                        flowItemCountState.value,
                        flowItemCountState.value,
                    ),
                    style = UiTextStyle(fontSizeSp = 14.sp),
                    modifier = Modifier
                        .margin(bottom = 8.dp)
                        .scenarioTarget(scenario, DemoAutomationRole.State),
                )
                Row(
                    spacing = 8.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .margin(bottom = 8.dp),
                ) {
                    Button(
                        text = stringResource(R.string.demo_layouts_add_tags),
                        size = ButtonSize.Compact,
                        modifier = Modifier.scenarioTarget(scenario, DemoAutomationRole.PrimaryAction),
                        onClick = { flowItemCountState.value = (flowItemCountState.value + 2).coerceAtMost(20) },
                    )
                    Button(
                        text = stringResource(R.string.demo_layouts_remove_tags),
                        size = ButtonSize.Compact,
                        variant = ButtonVariant.Outlined,
                        modifier = Modifier.scenarioTarget(scenario, DemoAutomationRole.SecondaryAction),
                        onClick = { flowItemCountState.value = (flowItemCountState.value - 2).coerceAtLeast(2) },
                    )
                }
                Button(
                    text = stringResource(R.string.demo_layouts_reset_tags),
                    variant = ButtonVariant.Outlined,
                    modifier = Modifier
                        .fillMaxWidth()
                        .margin(bottom = 8.dp)
                        .scenarioTarget(scenario, DemoAutomationRole.Reset),
                    onClick = { flowItemCountState.value = 8 },
                )
                FlowRow(
                    horizontalSpacing = 8.dp,
                    verticalSpacing = 8.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .margin(bottom = 16.dp)
                        .testTag(DemoTestTags.LAYOUTS_FLOW_ROW),
                ) {
                    (1..flowItemCountState.value).forEach { i ->
                        Surface(
                            variant = SurfaceVariant.Variant,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        ) {
                            Text(text = stringResource(R.string.demo_layouts_tag, i))
                        }
                    }
                }
                Divider(modifier = Modifier.margin(bottom = 12.dp))
                Text(
                    text = stringResource(R.string.demo_layouts_flow_column_summary),
                    style = UiTextStyle(fontSizeSp = 14.sp),
                    modifier = Modifier.margin(bottom = 8.dp),
                )
                FlowColumn(
                    horizontalSpacing = 12.dp,
                    verticalSpacing = 8.dp,
                    maxItemsInEachColumn = 3,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp),
                ) {
                    (1..9).forEach { i ->
                        Surface(
                            variant = SurfaceVariant.Default,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        ) {
                            Text(text = stringResource(R.string.demo_layouts_vertical_item, i))
                        }
                    }
                }
            }

            "scrollable" -> ScenarioSection(
                kind = ScenarioKind.Core,
                title = stringResource(R.string.demo_layouts_scroll_title),
                subtitle = stringResource(R.string.demo_layouts_scroll_summary),
            ) {
                Text(
                    text = stringResource(R.string.demo_layouts_scroll_column_label),
                    style = UiTextStyle(fontSizeSp = 14.sp),
                    modifier = Modifier.margin(bottom = 8.dp),
                )
                ScrollableColumn(
                    spacing = 8.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .backgroundColor(SurfaceDefaults.variantBackgroundColor())
                        .shape(SurfaceDefaults.shape())
                        .padding(12.dp)
                        .testTag(DemoTestTags.LAYOUTS_SCROLLABLE_COLUMN),
                ) {
                    (1..15).forEach { i ->
                        Surface(
                            variant = SurfaceVariant.Default,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                        ) {
                            Text(text = stringResource(R.string.demo_layouts_scroll_content_row, i))
                        }
                    }
                    Text(
                        text = stringResource(R.string.demo_layouts_more_below),
                        style = UiTextStyle(fontSizeSp = 13.sp),
                        color = TextDefaults.secondaryColor(),
                    )
                }
                Divider(modifier = Modifier.margin(vertical = 12.dp))
                Text(
                    text = stringResource(R.string.demo_layouts_scroll_row_label),
                    style = UiTextStyle(fontSizeSp = 14.sp),
                    modifier = Modifier.margin(bottom = 8.dp),
                )
                ScrollableRow(
                    spacing = 8.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .backgroundColor(SurfaceDefaults.variantBackgroundColor())
                        .shape(SurfaceDefaults.shape())
                        .padding(8.dp)
                        .testTag(DemoTestTags.LAYOUTS_SCROLLABLE_ROW),
                ) {
                    (1..20).forEach { i ->
                        Surface(
                            variant = SurfaceVariant.Default,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        ) {
                            Text(text = stringResource(R.string.demo_layouts_horizontal_label, i))
                        }
                    }
                }
            }

            "constraint_basic" -> ScenarioSection(
                kind = ScenarioKind.Core,
                title = stringResource(R.string.demo_layouts_constraint_basic_title),
                subtitle = stringResource(R.string.demo_layouts_constraint_basic_summary),
            ) {
                Button(
                    text = stringResource(R.string.demo_layouts_constraint_reset),
                    variant = ButtonVariant.Outlined,
                    modifier = Modifier
                        .fillMaxWidth()
                        .margin(bottom = 8.dp)
                        .scenarioTarget(scenario, DemoAutomationRole.Reset),
                    onClick = {
                        constraintHelperLongState.value = false
                        constraintSetExpandedState.value = false
                        constraintDimensionAdvancedState.value = false
                        constraintHelpersFullState.value = false
                        constraintVerticalChainPackedState.value = false
                        constraintSetHelpersAlternateState.value = false
                        constraintVirtualAlternateState.value = false
                    },
                )
                ConstraintLayout(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(188.dp)
                        .backgroundColor(SurfaceDefaults.backgroundColor())
                        .shape(SurfaceDefaults.shape())
                        .padding(12.dp)
                        .testTag(DemoTestTags.LAYOUTS_CONSTRAINT_BASIC_CONTAINER),
                ) {
                    val (titleRef, contentRef, badgeRef) = createRefs("title", "content", "badge")
                    Text(
                        text = stringResource(R.string.demo_layouts_constraint_card),
                        style = UiTextStyle(fontSizeSp = 15.sp),
                        modifier = Modifier.constrainAs(titleRef) {
                            topToTop(parent)
                            startToStart(parent)
                        },
                    )
                    Surface(
                        variant = SurfaceVariant.Variant,
                        modifier = Modifier.constrainAs(contentRef) {
                            startToStart(parent)
                            endToEnd(parent)
                            topToBottom(titleRef, margin = 12.dp)
                            bottomToBottom(parent)
                            width = ConstraintDimension.MatchConstraints()
                            height = ConstraintDimension.MatchConstraints()
                        }.padding(12.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.demo_layouts_constraint_content_note),
                            style = UiTextStyle(fontSizeSp = 13.sp),
                            color = TextDefaults.secondaryColor(),
                        )
                    }
                    Surface(
                        variant = SurfaceVariant.Default,
                        modifier = Modifier
                            .constrainAs(badgeRef) {
                                startToStart(contentRef, margin = 8.dp)
                                endToEnd(contentRef, margin = 8.dp)
                                topToTop(contentRef, margin = 8.dp)
                                horizontalBias = 0.78f
                            }
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                            .testTag(DemoTestTags.LAYOUTS_CONSTRAINT_BASIC_BADGE),
                    ) {
                        Text(text = stringResource(R.string.demo_layouts_bias))
                    }
                }
            }

            "constraint_helpers" -> ScenarioSection(
                kind = ScenarioKind.Core,
                title = stringResource(R.string.demo_layouts_helpers_title),
                subtitle = stringResource(R.string.demo_layouts_helpers_summary),
            ) {
                Button(
                    text = stringResource(
                        if (constraintHelperLongState.value) {
                            R.string.demo_layouts_use_short_copy
                        } else {
                            R.string.demo_layouts_use_long_copy
                        },
                    ),
                    size = ButtonSize.Compact,
                    variant = ButtonVariant.Outlined,
                    modifier = Modifier
                        .margin(bottom = 8.dp)
                        .testTag(DemoTestTags.LAYOUTS_CONSTRAINT_HELPERS_TOGGLE),
                    onClick = { constraintHelperLongState.value = !constraintHelperLongState.value },
                )
                ConstraintLayout(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(176.dp)
                        .backgroundColor(SurfaceDefaults.variantBackgroundColor())
                        .shape(SurfaceDefaults.shape())
                        .padding(12.dp)
                        .testTag(DemoTestTags.LAYOUTS_CONSTRAINT_HELPERS_CONTAINER),
                ) {
                    val leftPartition = createGuidelineFromStart(0.55f)
                    val (
                        headlineRef,
                        summaryRef,
                        markerRef,
                        guidelineIndicatorRef,
                        guidelineLabelRef,
                    ) = createRefs(
                        "headline",
                        "summary",
                        "marker",
                        "guideline-indicator",
                        "guideline-label",
                    )
                    val endBarrier = createEndBarrier(headlineRef, summaryRef, margin = 4.dp)
                    Spacer(
                        modifier = Modifier
                            .constrainAs(guidelineIndicatorRef) {
                                startToStart(leftPartition)
                                topToTop(parent)
                                bottomToBottom(parent)
                                width = ConstraintDimension.Fixed(2.dp)
                                height = ConstraintDimension.MatchConstraints()
                            }
                            .backgroundColor(Theme.colors.primary),
                    )
                    Surface(
                        variant = SurfaceVariant.Default,
                        modifier = Modifier
                            .constrainAs(headlineRef) {
                                startToStart(parent)
                                topToTop(parent)
                                endToStart(leftPartition, margin = 8.dp)
                                width = ConstraintDimension.ConstrainedWrapContent
                                horizontalBias = 0f
                            }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                            .testTag(DemoTestTags.LAYOUTS_CONSTRAINT_HELPERS_HEADLINE),
                    ) {
                        Text(
                            text = stringResource(R.string.demo_layouts_helper_area),
                            style = UiTextStyle(fontSizeSp = 14.sp),
                        )
                    }
                    Surface(
                        variant = SurfaceVariant.Default,
                        modifier = Modifier
                            .constrainAs(summaryRef) {
                                startToStart(parent)
                                topToBottom(headlineRef, margin = 8.dp)
                                endToStart(leftPartition, margin = 8.dp)
                                width = ConstraintDimension.ConstrainedWrapContent
                                horizontalBias = 0f
                            }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                            .testTag(DemoTestTags.LAYOUTS_CONSTRAINT_HELPERS_SUMMARY),
                    ) {
                        Text(
                            text = if (constraintHelperLongState.value) {
                                stringResource(R.string.demo_layouts_helper_long_copy)
                            } else {
                                stringResource(R.string.demo_layouts_helper_short_copy)
                            },
                            style = UiTextStyle(fontSizeSp = 12.sp),
                            color = TextDefaults.secondaryColor(),
                        )
                    }
                    Surface(
                        variant = SurfaceVariant.Default,
                        modifier = Modifier
                            .constrainAs(markerRef) {
                                startToEnd(endBarrier, margin = 8.dp)
                                endToEnd(parent)
                                topToTop(parent)
                                width = ConstraintDimension.ConstrainedWrapContent
                                horizontalBias = 0f
                            }
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                            .testTag(DemoTestTags.LAYOUTS_CONSTRAINT_HELPERS_MARKER),
                    ) {
                        Text(text = stringResource(R.string.demo_layouts_barrier_marker))
                    }
                    Text(
                        text = stringResource(R.string.demo_layouts_guideline_partition),
                        style = UiTextStyle(fontSizeSp = 11.sp),
                        color = TextDefaults.secondaryColor(),
                        modifier = Modifier
                            .constrainAs(guidelineLabelRef) {
                                startToStart(leftPartition, margin = 4.dp)
                                endToEnd(parent)
                                bottomToBottom(parent)
                                width = ConstraintDimension.MatchConstraints()
                            }
                            .testTag(DemoTestTags.LAYOUTS_CONSTRAINT_HELPERS_GUIDELINE_LABEL),
                    )
                }
            }

            "constraint_chain" -> ScenarioSection(
                kind = ScenarioKind.Core,
                title = stringResource(R.string.demo_layouts_chain_title),
                subtitle = stringResource(R.string.demo_layouts_chain_summary),
            ) {
                ConstraintLayout(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(148.dp)
                        .backgroundColor(SurfaceDefaults.backgroundColor())
                        .shape(SurfaceDefaults.shape())
                        .padding(12.dp)
                        .testTag(DemoTestTags.LAYOUTS_CONSTRAINT_CHAIN_CONTAINER),
                ) {
                    val (startRef, middleRef, endRef, leftEdgeRef, rightEdgeRef) = createRefs(
                        "start",
                        "middle",
                        "end",
                        "left-edge-indicator",
                        "right-edge-indicator",
                    )
                    val physicalLeft = createGuidelineFromLeft(8.dp)
                    val physicalRight = createGuidelineFromRight(8.dp)
                    createHorizontalChain(
                        startRef,
                        middleRef,
                        endRef,
                        style = ConstraintChainStyle.SpreadInside,
                        startTarget = physicalLeft,
                        startTargetSide = ConstraintHorizontalAnchorSide.Left,
                        startMargin = 8.dp,
                        endTarget = physicalRight,
                        endTargetSide = ConstraintHorizontalAnchorSide.Right,
                        endMargin = 8.dp,
                    )
                    listOf(leftEdgeRef to physicalLeft, rightEdgeRef to physicalRight).forEach { (ref, guide) ->
                        Spacer(
                            modifier = Modifier
                                .constrainAs(ref) {
                                    leftToLeft(guide)
                                    topToTop(parent)
                                    bottomToBottom(parent)
                                    width = ConstraintDimension.Fixed(2.dp)
                                    height = ConstraintDimension.MatchConstraints()
                                }
                                .backgroundColor(Theme.colors.primary),
                        )
                    }
                    Surface(
                        variant = SurfaceVariant.Variant,
                        modifier = Modifier
                            .constrainAs(startRef) {
                                topToTop(parent)
                                bottomToBottom(parent)
                                width = ConstraintDimension.Fixed(88.dp)
                                height = ConstraintDimension.Fixed(56.dp)
                            }
                            .testTag(DemoTestTags.LAYOUTS_CONSTRAINT_CHAIN_START),
                    ) { Box(contentAlignment = BoxAlignment.Center, modifier = Modifier.fillMaxSize()) { Text(text = stringResource(R.string.demo_layouts_node_a)) } }
                    Surface(
                        variant = SurfaceVariant.Default,
                        modifier = Modifier
                            .constrainAs(middleRef) {
                                topToTop(parent)
                                bottomToBottom(parent)
                                width = ConstraintDimension.Fixed(88.dp)
                                height = ConstraintDimension.Fixed(56.dp)
                            }
                            .testTag(DemoTestTags.LAYOUTS_CONSTRAINT_CHAIN_MIDDLE),
                    ) { Box(contentAlignment = BoxAlignment.Center, modifier = Modifier.fillMaxSize()) { Text(text = stringResource(R.string.demo_layouts_node_b)) } }
                    Surface(
                        variant = SurfaceVariant.Variant,
                        modifier = Modifier
                            .constrainAs(endRef) {
                                topToTop(parent)
                                bottomToBottom(parent)
                                width = ConstraintDimension.Fixed(88.dp)
                                height = ConstraintDimension.Fixed(56.dp)
                            }
                            .testTag(DemoTestTags.LAYOUTS_CONSTRAINT_CHAIN_END),
                    ) { Box(contentAlignment = BoxAlignment.Center, modifier = Modifier.fillMaxSize()) { Text(text = stringResource(R.string.demo_layouts_node_c)) } }
                }
            }

            "constraint_grid" -> ScenarioSection(
                kind = ScenarioKind.Core,
                title = stringResource(R.string.demo_layouts_constraint_grid_title),
                subtitle = stringResource(R.string.demo_layouts_constraint_grid_summary),
            ) {
                ConstraintLayout(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp)
                        .backgroundColor(SurfaceDefaults.variantBackgroundColor())
                        .shape(SurfaceDefaults.shape())
                        .padding(12.dp)
                        .testTag(DemoTestTags.LAYOUTS_CONSTRAINT_GRID_CONTAINER),
                ) {
                    val (hero, metric, status, action) = createRefs(
                        "grid-hero",
                        "grid-metric",
                        "grid-status",
                        "grid-action",
                    )
                    createGrid(
                        hero,
                        metric,
                        status,
                        action,
                        id = "demo-grid",
                        rows = 2,
                        columns = 3,
                        orientation = ConstraintGridOrientation.Horizontal,
                        rowWeights = listOf(1f, 1.25f),
                        columnWeights = listOf(1f, 1.6f, 1f),
                        horizontalGap = 8.dp,
                        verticalGap = 8.dp,
                        spans = listOf(ConstraintGridSpan(hero, index = 0, columnSpan = 2)),
                        skips = listOf(ConstraintGridSkip(index = 2)),
                    )
                    fun gridModifier(
                        ref: ConstraintReference,
                        tag: String,
                    ): Modifier = Modifier
                        .constrainAs(ref) {
                            width = ConstraintDimension.MatchConstraints()
                            height = ConstraintDimension.MatchConstraints()
                        }
                        .testTag(tag)
                    Surface(
                        variant = SurfaceVariant.Variant,
                        modifier = gridModifier(hero, DemoTestTags.LAYOUTS_CONSTRAINT_GRID_HERO),
                    ) {
                        Box(contentAlignment = BoxAlignment.Center, modifier = Modifier.fillMaxSize()) {
                            Text(text = stringResource(R.string.demo_layouts_constraint_grid_span))
                        }
                    }
                    Surface(
                        variant = SurfaceVariant.Default,
                        modifier = gridModifier(metric, DemoTestTags.LAYOUTS_CONSTRAINT_GRID_METRIC),
                    ) {
                        Box(contentAlignment = BoxAlignment.Center, modifier = Modifier.fillMaxSize()) {
                            Text(text = stringResource(R.string.demo_layouts_constraint_grid_metric))
                        }
                    }
                    Surface(
                        variant = SurfaceVariant.Variant,
                        modifier = gridModifier(status, DemoTestTags.LAYOUTS_CONSTRAINT_GRID_STATUS),
                    ) {
                        Box(contentAlignment = BoxAlignment.Center, modifier = Modifier.fillMaxSize()) {
                            Text(text = stringResource(R.string.demo_layouts_constraint_grid_status))
                        }
                    }
                    Surface(
                        variant = SurfaceVariant.Default,
                        modifier = gridModifier(action, DemoTestTags.LAYOUTS_CONSTRAINT_GRID_ACTION),
                    ) {
                        Box(contentAlignment = BoxAlignment.Center, modifier = Modifier.fillMaxSize()) {
                            Text(text = stringResource(R.string.demo_layouts_constraint_grid_action))
                        }
                    }
                }
            }

            "constraint_circular_flow" -> ScenarioSection(
                kind = ScenarioKind.Visual,
                title = stringResource(R.string.demo_layouts_constraint_circular_title),
                subtitle = stringResource(R.string.demo_layouts_constraint_circular_summary),
            ) {
                ConstraintLayout(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(260.dp)
                        .backgroundColor(SurfaceDefaults.backgroundColor())
                        .shape(SurfaceDefaults.shape())
                        .padding(12.dp)
                        .testTag(DemoTestTags.LAYOUTS_CONSTRAINT_CIRCULAR_CONTAINER),
                ) {
                    val (center, top, right, bottom, left) = createRefs(
                        "orbit-center",
                        "orbit-top",
                        "orbit-right",
                        "orbit-bottom",
                        "orbit-left",
                    )
                    createCircularFlow(
                        center,
                        ConstraintCircularFlowItem(top, radius = 78.dp, angle = 0f),
                        ConstraintCircularFlowItem(right, radius = 78.dp, angle = 90f),
                        ConstraintCircularFlowItem(bottom, radius = 78.dp, angle = 180f),
                        ConstraintCircularFlowItem(left, radius = 78.dp, angle = 270f),
                        id = "demo-orbit",
                    )
                    Surface(
                        variant = SurfaceVariant.Variant,
                        modifier = Modifier
                            .constrainAs(center) {
                                centerHorizontallyTo()
                                centerVerticallyTo()
                                width = ConstraintDimension.Fixed(72.dp)
                                height = ConstraintDimension.Fixed(72.dp)
                            }
                            .testTag(DemoTestTags.LAYOUTS_CONSTRAINT_CIRCULAR_CENTER),
                    ) {
                        Box(contentAlignment = BoxAlignment.Center, modifier = Modifier.fillMaxSize()) {
                            Text(text = stringResource(R.string.demo_layouts_constraint_circular_center))
                        }
                    }
                    listOf(
                        Triple(top, DemoTestTags.LAYOUTS_CONSTRAINT_CIRCULAR_TOP, R.string.demo_layouts_top),
                        Triple(right, DemoTestTags.LAYOUTS_CONSTRAINT_CIRCULAR_RIGHT, R.string.demo_layouts_right),
                        Triple(bottom, DemoTestTags.LAYOUTS_CONSTRAINT_CIRCULAR_BOTTOM, R.string.demo_layouts_bottom),
                        Triple(left, DemoTestTags.LAYOUTS_CONSTRAINT_CIRCULAR_LEFT, R.string.demo_layouts_left),
                    ).forEach { (ref, tag, label) ->
                        Surface(
                            variant = SurfaceVariant.Default,
                            modifier = Modifier
                                .constrainAs(ref) {
                                    width = ConstraintDimension.Fixed(48.dp)
                                    height = ConstraintDimension.Fixed(48.dp)
                                }
                                .testTag(tag),
                        ) {
                            Box(contentAlignment = BoxAlignment.Center, modifier = Modifier.fillMaxSize()) {
                                Text(text = stringResource(label), style = UiTextStyle(fontSizeSp = 12.sp))
                            }
                        }
                    }
                }
            }

            "constraint_set" -> ScenarioSection(
                kind = ScenarioKind.Visual,
                title = stringResource(R.string.demo_layouts_constraint_set_title),
                subtitle = stringResource(R.string.demo_layouts_constraint_set_summary),
            ) {
                Button(
                    text = stringResource(
                        if (constraintSetExpandedState.value) {
                            R.string.demo_layouts_switch_vertical
                        } else {
                            R.string.demo_layouts_switch_horizontal
                        },
                    ),
                    variant = ButtonVariant.Outlined,
                    modifier = Modifier
                        .margin(bottom = 8.dp)
                        .testTag(DemoTestTags.LAYOUTS_CONSTRAINT_SET_TOGGLE),
                    onClick = { constraintSetExpandedState.value = !constraintSetExpandedState.value },
                )
                val compactSet = constraintSet {
                    val (titleRef, markerRef) = createRefs("title", "marker")
                    constrain(titleRef) {
                        startToStart(parent)
                        topToTop(parent)
                    }
                    constrain(markerRef) {
                        startToStart(titleRef)
                        topToBottom(titleRef, margin = 12.dp)
                        endToEnd(parent)
                        width = ConstraintDimension.MatchConstraints()
                    }
                }
                val expandedSet = constraintSet {
                    val (titleRef, markerRef) = createRefs("title", "marker")
                    constrain(titleRef) {
                        startToStart(parent)
                        topToTop(parent)
                        bottomToBottom(parent)
                    }
                    constrain(markerRef) {
                        startToEnd(titleRef, margin = 12.dp)
                        endToEnd(parent)
                        topToTop(parent)
                        bottomToBottom(parent)
                        width = ConstraintDimension.MatchConstraints()
                    }
                }
                ConstraintLayout(
                    constraintSet = if (constraintSetExpandedState.value) expandedSet else compactSet,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(156.dp)
                        .backgroundColor(SurfaceDefaults.variantBackgroundColor())
                        .shape(SurfaceDefaults.shape())
                        .padding(12.dp),
                ) {
                    Text(
                        text = stringResource(
                            if (constraintSetExpandedState.value) {
                                R.string.demo_layouts_horizontal_mode
                            } else {
                                R.string.demo_layouts_vertical_mode
                            },
                        ),
                        style = UiTextStyle(fontSizeSp = 14.sp),
                        modifier = Modifier.layoutId("title"),
                    )
                    Surface(
                        variant = SurfaceVariant.Default,
                        modifier = Modifier
                            .layoutId("marker")
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                            .testTag(DemoTestTags.LAYOUTS_CONSTRAINT_SET_MARKER),
                    ) {
                        Text(
                            text = if (constraintSetExpandedState.value) {
                                stringResource(R.string.demo_layouts_marker_right)
                            } else {
                                stringResource(R.string.demo_layouts_marker_below)
                            },
                        )
                    }
                }
            }

            "constraint_anchor_advanced" -> ScenarioSection(
                kind = ScenarioKind.Core,
                title = stringResource(R.string.demo_layouts_anchor_title),
                subtitle = stringResource(R.string.demo_layouts_anchor_summary),
            ) {
                ConstraintLayout(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(232.dp)
                        .backgroundColor(SurfaceDefaults.backgroundColor())
                        .shape(SurfaceDefaults.shape())
                        .padding(12.dp)
                        .testTag(DemoTestTags.LAYOUTS_CONSTRAINT_ANCHOR_ADVANCED_CONTAINER),
                ) {
                    val leaderRef = createRef("anchor-leader")
                    val baselineRef = createRef("anchor-baseline")
                    val baselineTopRef = createRef("anchor-baseline-top")
                    val baselineBottomRef = createRef("anchor-baseline-bottom")
                    val centeredRef = createRef("anchor-centered")
                    val circleCenterRef = createRef("anchor-circle-center")
                    val circleNodeRef = createRef("anchor-circle-node")
                    val targetRef = createRef("anchor-target")
                    val linkedRef = createRef("anchor-linked")
                    Text(
                        text = stringResource(R.string.demo_layouts_leader),
                        style = UiTextStyle(fontSizeSp = 16.sp),
                        modifier = Modifier.constrainAs(leaderRef) {
                            topToTop(parent)
                            startToStart(parent)
                        },
                    )
                    Text(
                        text = stringResource(R.string.demo_layouts_baseline),
                        style = UiTextStyle(fontSizeSp = 12.sp),
                        modifier = Modifier
                            .constrainAs(baselineRef) {
                                startToEnd(leaderRef, margin = 10.dp)
                                baselineToBaseline(leaderRef)
                            }
                            .testTag(DemoTestTags.LAYOUTS_CONSTRAINT_ANCHOR_ADVANCED_BASELINE),
                    )
                    Text(
                        text = stringResource(R.string.demo_layouts_baseline_top),
                        style = UiTextStyle(fontSizeSp = 12.sp),
                        modifier = Modifier.constrainAs(baselineTopRef) {
                            startToEnd(baselineRef, margin = 10.dp)
                            baselineToTop(leaderRef, margin = 2.dp)
                        },
                    )
                    Text(
                        text = stringResource(R.string.demo_layouts_baseline_bottom),
                        style = UiTextStyle(fontSizeSp = 12.sp),
                        modifier = Modifier.constrainAs(baselineBottomRef) {
                            startToEnd(baselineTopRef, margin = 10.dp)
                            baselineToBottom(leaderRef, margin = 2.dp)
                        },
                    )
                    Surface(
                        variant = SurfaceVariant.Variant,
                        modifier = Modifier
                            .constrainAs(centeredRef) {
                                centerHorizontallyTo(parent)
                                centerVerticallyTo(parent)
                                width = ConstraintDimension.Fixed(118.dp)
                                height = ConstraintDimension.Fixed(44.dp)
                            },
                    ) {
                        Box(contentAlignment = BoxAlignment.Center, modifier = Modifier.fillMaxSize()) {
                            Text(text = stringResource(R.string.demo_layouts_center_apis))
                        }
                    }
                    Surface(
                        variant = SurfaceVariant.Default,
                        modifier = Modifier
                            .constrainAs(circleCenterRef) {
                                topToTop(parent)
                                endToEnd(parent)
                                width = ConstraintDimension.Fixed(40.dp)
                                height = ConstraintDimension.Fixed(40.dp)
                            },
                    ) {
                        Box(contentAlignment = BoxAlignment.Center, modifier = Modifier.fillMaxSize()) {
                            Text(text = stringResource(R.string.demo_layouts_node_c))
                        }
                    }
                    Surface(
                        variant = SurfaceVariant.Variant,
                        modifier = Modifier
                            .constrainAs(circleNodeRef) {
                                circular(
                                    target = circleCenterRef,
                                    radius = 54.dp,
                                    angle = 225f,
                                )
                                width = ConstraintDimension.Fixed(56.dp)
                                height = ConstraintDimension.Fixed(30.dp)
                            }
                            .testTag(DemoTestTags.LAYOUTS_CONSTRAINT_ANCHOR_ADVANCED_CIRCLE),
                    ) {
                        Box(contentAlignment = BoxAlignment.Center, modifier = Modifier.fillMaxSize()) {
                            Text(text = stringResource(R.string.demo_layouts_circular))
                        }
                    }
                    Surface(
                        variant = SurfaceVariant.Default,
                        modifier = Modifier
                            .constrainAs(targetRef) {
                                bottomToBottom(parent)
                                endToEnd(parent)
                                width = ConstraintDimension.Fixed(92.dp)
                                height = ConstraintDimension.Fixed(32.dp)
                            },
                    ) {
                        Box(contentAlignment = BoxAlignment.Center, modifier = Modifier.fillMaxSize()) {
                            Text(text = stringResource(R.string.demo_layouts_target))
                        }
                    }
                    Surface(
                        variant = SurfaceVariant.Variant,
                        modifier = Modifier
                            .constrainAs(linkedRef) {
                                bottomToTop(targetRef, margin = 8.dp)
                                endToEnd(targetRef)
                                width = ConstraintDimension.Fixed(92.dp)
                                height = ConstraintDimension.Fixed(32.dp)
                            },
                    ) {
                        Box(contentAlignment = BoxAlignment.Center, modifier = Modifier.fillMaxSize()) {
                            Text(text = stringResource(R.string.demo_layouts_bottom_to_top))
                        }
                    }
                }
                Text(
                    text = stringResource(R.string.demo_layouts_anchor_status),
                    style = UiTextStyle(fontSizeSp = 12.sp),
                    color = TextDefaults.secondaryColor(),
                    modifier = Modifier
                        .margin(top = 6.dp)
                        .testTag(DemoTestTags.LAYOUTS_CONSTRAINT_ANCHOR_ADVANCED_STATUS),
                )
            }

            "constraint_dimension_advanced" -> ScenarioSection(
                kind = ScenarioKind.Visual,
                title = stringResource(R.string.demo_layouts_dimension_title),
                subtitle = stringResource(R.string.demo_layouts_dimension_summary),
            ) {
                Button(
                    text = stringResource(
                        if (constraintDimensionAdvancedState.value) {
                            R.string.demo_layouts_dimension_compact
                        } else {
                            R.string.demo_layouts_dimension_expanded
                        },
                    ),
                    variant = ButtonVariant.Outlined,
                    modifier = Modifier
                        .margin(bottom = 8.dp)
                        .testTag(DemoTestTags.LAYOUTS_CONSTRAINT_DIMENSION_ADVANCED_TOGGLE),
                    onClick = { constraintDimensionAdvancedState.value = !constraintDimensionAdvancedState.value },
                )
                ConstraintLayout(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp)
                        .backgroundColor(SurfaceDefaults.variantBackgroundColor())
                        .shape(SurfaceDefaults.shape())
                        .padding(12.dp)
                        .testTag(DemoTestTags.LAYOUTS_CONSTRAINT_DIMENSION_ADVANCED_CONTAINER),
                ) {
                    val (widthRef, heightRef, ratioRef) = createRefs("dim-width", "dim-height", "dim-ratio")
                    Surface(
                        variant = SurfaceVariant.Default,
                        modifier = Modifier
                            .constrainAs(widthRef) {
                                startToStart(parent)
                                endToEnd(parent)
                                topToTop(parent)
                                width = ConstraintDimension.MatchConstraints(
                                    mode = ConstraintMatchMode.Percent(
                                        if (constraintDimensionAdvancedState.value) 0.82f else 0.56f,
                                    ),
                                    min = 120.dp,
                                    max = 280.dp,
                                )
                                height = ConstraintDimension.Fixed(38.dp)
                            }
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                    ) {
                        Text(text = stringResource(R.string.demo_layouts_width_dimension))
                    }
                    Surface(
                        variant = SurfaceVariant.Variant,
                        modifier = Modifier
                            .constrainAs(heightRef) {
                                startToStart(parent)
                                topToBottom(widthRef, margin = 10.dp)
                                bottomToBottom(parent)
                                width = ConstraintDimension.Fixed(104.dp)
                                height = ConstraintDimension.MatchConstraints(
                                    mode = ConstraintMatchMode.Percent(
                                        if (constraintDimensionAdvancedState.value) 0.62f else 0.38f,
                                    ),
                                    min = 64.dp,
                                    max = 146.dp,
                                )
                            }
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                    ) {
                        Text(text = stringResource(R.string.demo_layouts_height_dimension))
                    }
                    Surface(
                        variant = SurfaceVariant.Default,
                        modifier = Modifier
                            .constrainAs(ratioRef) {
                                startToEnd(heightRef, margin = 10.dp)
                                endToEnd(parent)
                                topToBottom(widthRef, margin = 10.dp)
                                bottomToBottom(parent)
                                width = ConstraintDimension.Fixed(80.dp)
                                height = ConstraintDimension.MatchConstraints()
                                ratio = if (constraintDimensionAdvancedState.value) {
                                    ConstraintRatio(width = 16f, height = 9f)
                                } else {
                                    ConstraintRatio(width = 1f, height = 1f)
                                }
                            }
                            .testTag(DemoTestTags.LAYOUTS_CONSTRAINT_DIMENSION_ADVANCED_RATIO),
                    ) {
                        Box(contentAlignment = BoxAlignment.Center, modifier = Modifier.fillMaxSize()) {
                            Text(
                                text = stringResource(
                                    if (constraintDimensionAdvancedState.value) {
                                        R.string.demo_layouts_ratio_16_9
                                    } else {
                                        R.string.demo_layouts_ratio_1_1
                                    },
                                ),
                            )
                        }
                    }
                }
                Text(
                    text = if (constraintDimensionAdvancedState.value) {
                        stringResource(R.string.demo_layouts_dimension_status_expanded)
                    } else {
                        stringResource(R.string.demo_layouts_dimension_status_compact)
                    },
                    style = UiTextStyle(fontSizeSp = 12.sp),
                    color = TextDefaults.secondaryColor(),
                    modifier = Modifier
                        .margin(top = 6.dp)
                        .testTag(DemoTestTags.LAYOUTS_CONSTRAINT_DIMENSION_ADVANCED_STATUS),
                )
            }

            "constraint_helpers_full" -> ScenarioSection(
                kind = ScenarioKind.Visual,
                title = stringResource(R.string.demo_layouts_helpers_full_title),
                subtitle = stringResource(R.string.demo_layouts_helpers_full_summary),
            ) {
                Button(
                    text = stringResource(
                        if (constraintHelpersFullState.value) {
                            R.string.demo_layouts_switch_fraction
                        } else {
                            R.string.demo_layouts_switch_offset
                        },
                    ),
                    variant = ButtonVariant.Outlined,
                    modifier = Modifier
                        .margin(bottom = 8.dp)
                        .testTag(DemoTestTags.LAYOUTS_CONSTRAINT_HELPERS_FULL_TOGGLE),
                    onClick = { constraintHelpersFullState.value = !constraintHelpersFullState.value },
                )
                ConstraintLayout(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(208.dp)
                        .backgroundColor(SurfaceDefaults.backgroundColor())
                        .shape(SurfaceDefaults.shape())
                        .padding(12.dp)
                        .testTag(DemoTestTags.LAYOUTS_CONSTRAINT_HELPERS_FULL_CONTAINER),
                ) {
                    val guideEnd = if (constraintHelpersFullState.value) {
                        createGuidelineFromEnd(0.26f, id = "helpers-full-guide-end")
                    } else {
                        createGuidelineFromEnd(68.dp, id = "helpers-full-guide-end")
                    }
                    val guideTop = if (constraintHelpersFullState.value) {
                        createGuidelineFromTop(0.22f, id = "helpers-full-guide-top")
                    } else {
                        createGuidelineFromTop(24.dp, id = "helpers-full-guide-top")
                    }
                    val guideBottom = if (constraintHelpersFullState.value) {
                        createGuidelineFromBottom(0.18f, id = "helpers-full-guide-bottom")
                    } else {
                        createGuidelineFromBottom(30.dp, id = "helpers-full-guide-bottom")
                    }
                    val (probeTopRef, probeMiddleRef, probeBottomRef, markerRef) = createRefs(
                        "helpers-full-probe-top",
                        "helpers-full-probe-middle",
                        "helpers-full-probe-bottom",
                        "helpers-full-marker",
                    )
                    val startBarrier = createStartBarrier(
                        probeTopRef,
                        probeMiddleRef,
                        probeBottomRef,
                        id = "helpers-full-start-barrier",
                        margin = if (constraintHelpersFullState.value) 14.dp else 4.dp,
                        allowsGoneWidgets = constraintHelpersFullState.value,
                    )
                    val topBarrier = createTopBarrier(
                        probeTopRef,
                        probeMiddleRef,
                        id = "helpers-full-top-barrier",
                        margin = if (constraintHelpersFullState.value) 10.dp else 4.dp,
                        allowsGoneWidgets = constraintHelpersFullState.value,
                    )
                    val bottomBarrier = createBottomBarrier(
                        probeMiddleRef,
                        probeBottomRef,
                        id = "helpers-full-bottom-barrier",
                        margin = if (constraintHelpersFullState.value) 10.dp else 4.dp,
                        allowsGoneWidgets = constraintHelpersFullState.value,
                    )
                    Surface(
                        variant = SurfaceVariant.Variant,
                        modifier = Modifier
                            .constrainAs(probeTopRef) {
                                topToTop(guideTop)
                                endToStart(guideEnd, margin = 6.dp)
                                width = ConstraintDimension.Fixed(110.dp)
                                height = ConstraintDimension.Fixed(30.dp)
                            }
                            .testTag(DemoTestTags.LAYOUTS_CONSTRAINT_HELPERS_FULL_PROBE_TOP),
                    ) {
                        Box(contentAlignment = BoxAlignment.Center, modifier = Modifier.fillMaxSize()) {
                            Text(
                                text = stringResource(R.string.demo_layouts_top_probe),
                                style = UiTextStyle(fontSizeSp = 11.sp),
                            )
                        }
                    }
                    Surface(
                        variant = SurfaceVariant.Default,
                        modifier = Modifier
                            .constrainAs(probeMiddleRef) {
                                topToBottom(probeTopRef, margin = 8.dp)
                                endToStart(guideEnd, margin = 6.dp)
                                width = ConstraintDimension.Fixed(126.dp)
                                height = ConstraintDimension.Fixed(30.dp)
                            }
                            .testTag(DemoTestTags.LAYOUTS_CONSTRAINT_HELPERS_FULL_PROBE_MIDDLE),
                    ) {
                        Box(contentAlignment = BoxAlignment.Center, modifier = Modifier.fillMaxSize()) {
                            Text(
                                text = stringResource(R.string.demo_layouts_middle_probe),
                                style = UiTextStyle(fontSizeSp = 11.sp),
                            )
                        }
                    }
                    Surface(
                        variant = SurfaceVariant.Variant,
                        modifier = Modifier
                            .constrainAs(probeBottomRef) {
                                bottomToTop(guideBottom)
                                endToStart(guideEnd, margin = 6.dp)
                                width = ConstraintDimension.Fixed(98.dp)
                                height = ConstraintDimension.Fixed(30.dp)
                            }
                            .testTag(DemoTestTags.LAYOUTS_CONSTRAINT_HELPERS_FULL_PROBE_BOTTOM),
                    ) {
                        Box(contentAlignment = BoxAlignment.Center, modifier = Modifier.fillMaxSize()) {
                            Text(
                                text = stringResource(R.string.demo_layouts_bottom_probe),
                                style = UiTextStyle(fontSizeSp = 11.sp),
                            )
                        }
                    }
                    Box(
                        contentAlignment = BoxAlignment.Center,
                        modifier = Modifier
                            .constrainAs(markerRef) {
                                endToStart(startBarrier, margin = 8.dp)
                                topToBottom(topBarrier, margin = 8.dp)
                                bottomToTop(bottomBarrier, margin = 8.dp)
                                width = ConstraintDimension.Fixed(80.dp)
                                height = ConstraintDimension.Fixed(34.dp)
                            }
                            .backgroundColor(SurfaceDefaults.variantBackgroundColor())
                            .shape(SurfaceDefaults.shape())
                            .testTag(DemoTestTags.LAYOUTS_CONSTRAINT_HELPERS_FULL_MARKER),
                    ) {
                        Text(
                            text = stringResource(R.string.demo_layouts_barrier_start_side),
                            style = UiTextStyle(fontSizeSp = 11.sp),
                        )
                    }
                }
                Text(
                    text = if (constraintHelpersFullState.value) {
                        stringResource(R.string.demo_layouts_helpers_fraction_status)
                    } else {
                        stringResource(R.string.demo_layouts_helpers_offset_status)
                    },
                    style = UiTextStyle(fontSizeSp = 12.sp),
                    color = TextDefaults.secondaryColor(),
                    modifier = Modifier
                        .margin(top = 6.dp)
                        .testTag(DemoTestTags.LAYOUTS_CONSTRAINT_HELPERS_FULL_STATUS),
                )
            }

            "constraint_vertical_chain" -> ScenarioSection(
                kind = ScenarioKind.Core,
                title = stringResource(R.string.demo_layouts_vertical_chain_title),
                subtitle = stringResource(R.string.demo_layouts_vertical_chain_summary),
            ) {
                Button(
                    text = stringResource(
                        if (constraintVerticalChainPackedState.value) {
                            R.string.demo_layouts_switch_spread_inside
                        } else {
                            R.string.demo_layouts_switch_packed
                        },
                    ),
                    variant = ButtonVariant.Outlined,
                    modifier = Modifier
                        .margin(bottom = 8.dp)
                        .testTag(DemoTestTags.LAYOUTS_CONSTRAINT_VERTICAL_CHAIN_TOGGLE),
                    onClick = { constraintVerticalChainPackedState.value = !constraintVerticalChainPackedState.value },
                )
                ConstraintLayout(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp)
                        .backgroundColor(SurfaceDefaults.variantBackgroundColor())
                        .shape(SurfaceDefaults.shape())
                        .padding(12.dp)
                        .testTag(DemoTestTags.LAYOUTS_CONSTRAINT_VERTICAL_CHAIN_CONTAINER),
                ) {
                    val (topRef, middleRef, bottomRef) = createRefs("v-chain-top", "v-chain-middle", "v-chain-bottom")
                    createVerticalChain(
                        topRef,
                        middleRef,
                        bottomRef,
                        weights = if (constraintVerticalChainPackedState.value) {
                            listOf(1f, 2f, 1f)
                        } else {
                            listOf(1f, 1f, 1f)
                        },
                        style = if (constraintVerticalChainPackedState.value) {
                            ConstraintChainStyle.Packed
                        } else {
                            ConstraintChainStyle.SpreadInside
                        },
                        bias = if (constraintVerticalChainPackedState.value) 0.3f else 0.5f,
                    )
                    Surface(
                        variant = SurfaceVariant.Default,
                        modifier = Modifier
                            .constrainAs(topRef) {
                                startToStart(parent)
                                endToEnd(parent)
                                width = ConstraintDimension.MatchConstraints()
                                height = ConstraintDimension.Fixed(42.dp)
                            }
                            .testTag(DemoTestTags.LAYOUTS_CONSTRAINT_VERTICAL_CHAIN_TOP),
                    ) {
                        Box(contentAlignment = BoxAlignment.Center, modifier = Modifier.fillMaxSize()) {
                            Text(text = stringResource(R.string.demo_layouts_top))
                        }
                    }
                    Surface(
                        variant = SurfaceVariant.Variant,
                        modifier = Modifier
                            .constrainAs(middleRef) {
                                startToStart(parent)
                                endToEnd(parent)
                                width = ConstraintDimension.MatchConstraints()
                                height = ConstraintDimension.Fixed(42.dp)
                            }
                            .testTag(DemoTestTags.LAYOUTS_CONSTRAINT_VERTICAL_CHAIN_MIDDLE),
                    ) {
                        Box(contentAlignment = BoxAlignment.Center, modifier = Modifier.fillMaxSize()) {
                            Text(text = stringResource(R.string.demo_layouts_middle))
                        }
                    }
                    Surface(
                        variant = SurfaceVariant.Default,
                        modifier = Modifier
                            .constrainAs(bottomRef) {
                                startToStart(parent)
                                endToEnd(parent)
                                width = ConstraintDimension.MatchConstraints()
                                height = ConstraintDimension.Fixed(42.dp)
                            }
                            .testTag(DemoTestTags.LAYOUTS_CONSTRAINT_VERTICAL_CHAIN_BOTTOM),
                    ) {
                        Box(contentAlignment = BoxAlignment.Center, modifier = Modifier.fillMaxSize()) {
                            Text(text = stringResource(R.string.demo_layouts_bottom))
                        }
                    }
                }
            }

            "constraint_set_helpers_mirror" -> ScenarioSection(
                kind = ScenarioKind.Visual,
                title = stringResource(R.string.demo_layouts_set_helpers_title),
                subtitle = stringResource(R.string.demo_layouts_set_helpers_summary),
            ) {
                Button(
                    text = stringResource(
                        if (constraintSetHelpersAlternateState.value) {
                            R.string.demo_layouts_set_helpers_horizontal
                        } else {
                            R.string.demo_layouts_set_helpers_vertical
                        },
                    ),
                    variant = ButtonVariant.Outlined,
                    modifier = Modifier
                        .margin(bottom = 8.dp)
                        .testTag(DemoTestTags.LAYOUTS_CONSTRAINT_SET_HELPERS_TOGGLE),
                    onClick = { constraintSetHelpersAlternateState.value = !constraintSetHelpersAlternateState.value },
                )
                val helperSetHorizontal = constraintSet {
                    val (aRef, bRef, cRef, markerRef) = createRefs("set-h-a", "set-h-b", "set-h-c", "set-marker")
                    createHorizontalChain(
                        aRef,
                        bRef,
                        cRef,
                        style = ConstraintChainStyle.SpreadInside,
                        bias = 0.5f,
                    )
                    val endBarrier = createEndBarrier(
                        aRef,
                        bRef,
                        cRef,
                        id = "set-h-end-barrier",
                        margin = 6.dp,
                    )
                    constrain(aRef) {
                        topToTop(parent)
                        bottomToBottom(parent)
                        width = ConstraintDimension.Fixed(64.dp)
                        height = ConstraintDimension.Fixed(36.dp)
                    }
                    constrain(bRef) {
                        topToTop(parent)
                        bottomToBottom(parent)
                        width = ConstraintDimension.Fixed(64.dp)
                        height = ConstraintDimension.Fixed(36.dp)
                    }
                    constrain(cRef) {
                        topToTop(parent)
                        bottomToBottom(parent)
                        width = ConstraintDimension.Fixed(64.dp)
                        height = ConstraintDimension.Fixed(36.dp)
                    }
                    constrain(markerRef) {
                        startToEnd(endBarrier, margin = 8.dp)
                        topToTop(parent)
                        width = ConstraintDimension.Fixed(92.dp)
                        height = ConstraintDimension.Fixed(36.dp)
                    }
                }
                val helperSetVertical = constraintSet {
                    val (aRef, bRef, cRef, markerRef) = createRefs("set-h-a", "set-h-b", "set-h-c", "set-marker")
                    createVerticalChain(
                        aRef,
                        bRef,
                        cRef,
                        style = ConstraintChainStyle.Packed,
                        bias = 0.22f,
                    )
                    val topBarrier = createTopBarrier(
                        aRef,
                        bRef,
                        cRef,
                        id = "set-v-top-barrier",
                        margin = 6.dp,
                    )
                    constrain(aRef) {
                        startToStart(parent)
                        endToEnd(parent)
                        width = ConstraintDimension.Fixed(92.dp)
                        height = ConstraintDimension.Fixed(34.dp)
                    }
                    constrain(bRef) {
                        startToStart(parent)
                        endToEnd(parent)
                        width = ConstraintDimension.Fixed(92.dp)
                        height = ConstraintDimension.Fixed(34.dp)
                    }
                    constrain(cRef) {
                        startToStart(parent)
                        endToEnd(parent)
                        width = ConstraintDimension.Fixed(92.dp)
                        height = ConstraintDimension.Fixed(34.dp)
                    }
                    constrain(markerRef) {
                        topToBottom(topBarrier, margin = 8.dp)
                        startToStart(parent)
                        endToEnd(parent)
                        width = ConstraintDimension.Fixed(126.dp)
                        height = ConstraintDimension.Fixed(34.dp)
                    }
                }
                ConstraintLayout(
                    constraintSet = if (constraintSetHelpersAlternateState.value) helperSetVertical else helperSetHorizontal,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(172.dp)
                        .backgroundColor(SurfaceDefaults.backgroundColor())
                        .shape(SurfaceDefaults.shape())
                        .padding(12.dp)
                        .testTag(DemoTestTags.LAYOUTS_CONSTRAINT_SET_HELPERS_CONTAINER),
                ) {
                    Surface(
                        variant = SurfaceVariant.Default,
                        modifier = Modifier.layoutId("set-h-a").padding(horizontal = 8.dp, vertical = 6.dp),
                    ) {
                        Text(text = stringResource(R.string.demo_layouts_node_a))
                    }
                    Surface(
                        variant = SurfaceVariant.Variant,
                        modifier = Modifier.layoutId("set-h-b").padding(horizontal = 8.dp, vertical = 6.dp),
                    ) {
                        Text(text = stringResource(R.string.demo_layouts_node_b))
                    }
                    Surface(
                        variant = SurfaceVariant.Default,
                        modifier = Modifier.layoutId("set-h-c").padding(horizontal = 8.dp, vertical = 6.dp),
                    ) {
                        Text(text = stringResource(R.string.demo_layouts_node_c))
                    }
                    Surface(
                        variant = SurfaceVariant.Variant,
                        modifier = Modifier
                            .layoutId("set-marker")
                            .padding(horizontal = 8.dp, vertical = 6.dp)
                            .testTag(DemoTestTags.LAYOUTS_CONSTRAINT_SET_HELPERS_MARKER),
                    ) {
                        Text(
                            text = stringResource(
                                if (constraintSetHelpersAlternateState.value) {
                                    R.string.demo_layouts_vertical_helper_set
                                } else {
                                    R.string.demo_layouts_horizontal_helper_set
                                },
                            ),
                        )
                    }
                }
                Text(
                    text = if (constraintSetHelpersAlternateState.value) {
                        stringResource(R.string.demo_layouts_set_helpers_status_vertical)
                    } else {
                        stringResource(R.string.demo_layouts_set_helpers_status_horizontal)
                    },
                    style = UiTextStyle(fontSizeSp = 12.sp),
                    color = TextDefaults.secondaryColor(),
                    modifier = Modifier
                        .margin(top = 6.dp)
                        .testTag(DemoTestTags.LAYOUTS_CONSTRAINT_SET_HELPERS_STATUS),
                )
            }

            "constraint_virtual_helpers" -> ScenarioSection(
                kind = ScenarioKind.Visual,
                title = stringResource(R.string.demo_layouts_virtual_title),
                subtitle = stringResource(R.string.demo_layouts_virtual_summary),
            ) {
                Button(
                    text = if (constraintVirtualAlternateState.value) {
                        stringResource(R.string.demo_layouts_virtual_baseline)
                    } else {
                        stringResource(R.string.demo_layouts_virtual_compare)
                    },
                    variant = ButtonVariant.Outlined,
                    modifier = Modifier
                        .margin(bottom = 8.dp)
                        .testTag(DemoTestTags.LAYOUTS_CONSTRAINT_VIRTUAL_TOGGLE),
                    onClick = { constraintVirtualAlternateState.value = !constraintVirtualAlternateState.value },
                )
                Column(
                    spacing = 10.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .backgroundColor(SurfaceDefaults.variantBackgroundColor())
                        .shape(SurfaceDefaults.shape())
                        .padding(12.dp)
                        .testTag(DemoTestTags.LAYOUTS_CONSTRAINT_VIRTUAL_CONTAINER),
                ) {
                    Text(
                        text = if (constraintVirtualAlternateState.value) {
                            stringResource(R.string.demo_layouts_virtual_mode_b)
                        } else {
                            stringResource(R.string.demo_layouts_virtual_mode_a)
                        },
                        style = UiTextStyle(fontSizeSp = 14.sp),
                    )

                    val flowSet = constraintSet {
                        val flowRef = createRef("flow-helper")
                        constrain(flowRef) {
                            topToTop(parent)
                            startToStart(parent)
                            endToEnd(parent)
                            width = ConstraintDimension.MatchConstraints()
                        }
                    }
                    ConstraintLayout(
                        constraintSet = flowSet,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(if (constraintVirtualAlternateState.value) 220.dp else 96.dp),
                    ) {
                        val (flowA, flowB, flowC, flowD) = createRefs("flow-a", "flow-b", "flow-c", "flow-d")
                        createFlow(
                            flowA,
                            flowB,
                            flowC,
                            flowD,
                            id = "flow-helper",
                            wrapMode = ConstraintFlowWrapMode.Chain,
                            horizontalGap = if (constraintVirtualAlternateState.value) 14.dp else 8.dp,
                            verticalGap = if (constraintVirtualAlternateState.value) 14.dp else 8.dp,
                            maxElementsWrap = if (constraintVirtualAlternateState.value) 1 else 2,
                        )
                        Surface(
                            variant = SurfaceVariant.Default,
                            modifier = Modifier
                                .layoutId("flow-a")
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                                .testTag(DemoTestTags.LAYOUTS_CONSTRAINT_VIRTUAL_FLOW_A),
                        ) { Text(stringResource(R.string.demo_layouts_flow_node, 1)) }
                        Surface(
                            variant = SurfaceVariant.Variant,
                            modifier = Modifier
                                .layoutId("flow-b")
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                                .testTag(DemoTestTags.LAYOUTS_CONSTRAINT_VIRTUAL_FLOW_B),
                        ) { Text(stringResource(R.string.demo_layouts_flow_node, 2)) }
                        Surface(
                            variant = SurfaceVariant.Default,
                            modifier = Modifier
                                .layoutId("flow-c")
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                                .testTag(DemoTestTags.LAYOUTS_CONSTRAINT_VIRTUAL_FLOW_C),
                        ) { Text(stringResource(R.string.demo_layouts_flow_node, 3)) }
                        Surface(
                            variant = SurfaceVariant.Variant,
                            modifier = Modifier
                                .layoutId("flow-d")
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                                .testTag(DemoTestTags.LAYOUTS_CONSTRAINT_VIRTUAL_FLOW_D),
                        ) { Text(stringResource(R.string.demo_layouts_flow_node, 4)) }
                    }

                    val groupSet = constraintSet {
                        val (groupARef, groupBRef) = createRefs("group-a", "group-b")
                        constrain(groupARef) {
                            topToTop(parent)
                            startToStart(parent)
                        }
                        constrain(groupBRef) {
                            topToTop(parent)
                            endToEnd(parent)
                        }
                    }
                    ConstraintLayout(
                        constraintSet = groupSet,
                        modifier = Modifier.fillMaxWidth().height(42.dp),
                    ) {
                        val (groupA, groupB) = createRefs("group-a", "group-b")
                        createGroup(
                            groupA,
                            groupB,
                            id = "group-helper",
                            visibility = if (constraintVirtualAlternateState.value) {
                                ConstraintHelperVisibility.Gone
                            } else {
                                ConstraintHelperVisibility.Visible
                            },
                        )
                        Surface(
                            variant = SurfaceVariant.Variant,
                            modifier = Modifier
                                .layoutId("group-a")
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                                .testTag(DemoTestTags.LAYOUTS_CONSTRAINT_VIRTUAL_GROUP_MEMBER),
                        ) {
                            Text(text = stringResource(R.string.demo_layouts_group_a))
                        }
                        Surface(
                            variant = SurfaceVariant.Default,
                            modifier = Modifier
                                .layoutId("group-b")
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                                .testTag(DemoTestTags.LAYOUTS_CONSTRAINT_VIRTUAL_GROUP_MEMBER_B),
                        ) {
                            Text(text = stringResource(R.string.demo_layouts_group_b))
                        }
                    }

                    val layerSet = constraintSet {
                        val (layerARef, layerBRef) = createRefs("layer-a", "layer-b")
                        constrain(layerARef) {
                            topToTop(parent, margin = 36.dp)
                            startToStart(parent, margin = 40.dp)
                        }
                        constrain(layerBRef) {
                            topToTop(parent, margin = 36.dp)
                            endToEnd(parent, margin = 40.dp)
                        }
                    }
                    ConstraintLayout(
                        constraintSet = layerSet,
                        modifier = Modifier.fillMaxWidth().height(112.dp),
                    ) {
                        val (layerA, layerB) = createRefs("layer-a", "layer-b")
                        createLayer(
                            layerA,
                            layerB,
                            id = "layer-helper",
                            rotation = if (constraintVirtualAlternateState.value) 18f else 0f,
                            scaleX = if (constraintVirtualAlternateState.value) 1.1f else 1f,
                            scaleY = if (constraintVirtualAlternateState.value) 1.1f else 1f,
                            translationX = if (constraintVirtualAlternateState.value) 6.dp else 0.dp,
                            translationY = 0.dp,
                        )
                        Surface(
                            variant = SurfaceVariant.Default,
                            modifier = Modifier
                                .layoutId("layer-a")
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                                .testTag(DemoTestTags.LAYOUTS_CONSTRAINT_VIRTUAL_CHIP_A),
                        ) {
                            Text(text = stringResource(R.string.demo_layouts_layer_a))
                        }
                        Surface(
                            variant = SurfaceVariant.Variant,
                            modifier = Modifier
                                .layoutId("layer-b")
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                                .testTag(DemoTestTags.LAYOUTS_CONSTRAINT_VIRTUAL_CHIP_B),
                        ) {
                            Text(text = stringResource(R.string.demo_layouts_layer_b))
                        }
                    }

                    val placeholderSet = constraintSet {
                        val (placeholderARef, placeholderBRef, hostRef, noteRef) = createRefs(
                            "placeholder-a",
                            "placeholder-b",
                            "placeholder-helper",
                            "placeholder-note",
                        )
                        constrain(placeholderARef) {
                            topToTop(parent)
                            startToStart(parent)
                        }
                        constrain(placeholderBRef) {
                            topToTop(parent)
                            endToEnd(parent)
                        }
                        constrain(hostRef) {
                            topToTop(parent, margin = 52.dp)
                            startToStart(parent)
                            endToEnd(parent)
                            width = ConstraintDimension.MatchConstraints()
                            height = ConstraintDimension.Fixed(46.dp)
                        }
                        constrain(noteRef) {
                            topToBottom(hostRef, margin = 4.dp)
                            startToStart(parent)
                        }
                    }
                    ConstraintLayout(
                        constraintSet = placeholderSet,
                        modifier = Modifier.fillMaxWidth().height(140.dp),
                    ) {
                        val placeholderA = createRef("placeholder-a")
                        val placeholderB = createRef("placeholder-b")
                        createPlaceholder(
                            content = if (constraintVirtualAlternateState.value) placeholderA else placeholderB,
                            id = "placeholder-helper",
                        )
                        Surface(
                            variant = SurfaceVariant.Default,
                            modifier = Modifier
                                .layoutId("placeholder-a")
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                                .testTag(DemoTestTags.LAYOUTS_CONSTRAINT_VIRTUAL_PLACEHOLDER_A),
                        ) {
                            Text(text = stringResource(R.string.demo_layouts_placeholder_a))
                        }
                        Surface(
                            variant = SurfaceVariant.Variant,
                            modifier = Modifier
                                .layoutId("placeholder-b")
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                                .testTag(DemoTestTags.LAYOUTS_CONSTRAINT_VIRTUAL_PLACEHOLDER_B),
                        ) {
                            Text(text = stringResource(R.string.demo_layouts_placeholder_b))
                        }
                        Surface(
                            variant = SurfaceVariant.Default,
                            modifier = Modifier
                                .layoutId("placeholder-note")
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                                .testTag(DemoTestTags.LAYOUTS_CONSTRAINT_VIRTUAL_PLACEHOLDER_NOTE),
                        ) {
                            Text(
                                text = stringResource(
                                    if (constraintVirtualAlternateState.value) {
                                        R.string.demo_layouts_host_a
                                    } else {
                                        R.string.demo_layouts_host_b
                                    },
                                ),
                            )
                        }
                    }

                    Text(
                        text = if (constraintVirtualAlternateState.value) {
                            stringResource(R.string.demo_layouts_virtual_status_hidden)
                        } else {
                            stringResource(R.string.demo_layouts_virtual_status_visible)
                        },
                        style = UiTextStyle(fontSizeSp = 12.sp),
                        color = TextDefaults.secondaryColor(),
                        modifier = Modifier.testTag(DemoTestTags.LAYOUTS_CONSTRAINT_VIRTUAL_STATUS),
                    )
                }
            }

            else -> error("Unknown layout section: $section")
        }
    }
}

private fun Modifier.scenarioTarget(
    scenario: DemoScenarioSpec?,
    role: DemoAutomationRole,
): Modifier = scenario?.automation?.get(role)?.let(::demoAutomationTarget) ?: this
