package com.viewcompose

import com.viewcompose.demo.contract.DemoAutomationRole
import com.viewcompose.demo.contract.DemoScenarioSpec
import com.viewcompose.host.android.resources.stringResource
import com.viewcompose.runtime.mutableStateOf
import com.viewcompose.ui.foundation.Column
import com.viewcompose.ui.foundation.SegmentedControl
import com.viewcompose.ui.foundation.SegmentedControlSize
import com.viewcompose.ui.foundation.Text
import com.viewcompose.ui.foundation.TextDefaults
import com.viewcompose.ui.foundation.Theme
import com.viewcompose.ui.foundation.UiTreeBuilder
import com.viewcompose.ui.foundation.rememberSaveable
import com.viewcompose.ui.modifier.Modifier
import com.viewcompose.ui.modifier.fillMaxWidth
import com.viewcompose.ui.modifier.margin
import com.viewcompose.ui.node.SegmentedControlItem
import com.viewcompose.ui.unit.dp

internal fun UiTreeBuilder.ComponentSegmentedControlFixture(
    scenario: DemoScenarioSpec?,
    generation: Int,
    onReset: () -> Unit,
) {
    val selectedIndex = rememberSaveable(key = "component-segmented-selected-index") {
        mutableStateOf(0)
    }
    val items = segmentedControlItems()
    ComponentFixtureList(generation, listOf("control", "variants")) { section ->
        when (section) {
            "control" -> ComponentFixtureHeader(
                scenario = scenario,
                state = {
                    stringResource(
                        R.string.demo_component_selected_index,
                        selectedIndex.value,
                    )
                },
                onReset = onReset,
            ) {
                SegmentedControl(
                    items = items,
                    selectedIndex = selectedIndex.value,
                    onSelectionChange = { selectedIndex.value = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .componentScenarioTarget(
                            scenario,
                            DemoAutomationRole.PrimaryAction,
                        ),
                )
            }

            else -> ComponentSegmentedControlVariants(scenario, items)
        }
    }
}

private fun UiTreeBuilder.segmentedControlItems(): List<SegmentedControlItem> = listOf(
    SegmentedControlItem("a", stringResource(R.string.demo_component_option_a)),
    SegmentedControlItem("b", stringResource(R.string.demo_component_option_b)),
    SegmentedControlItem("c", stringResource(R.string.demo_component_option_c)),
)

private fun UiTreeBuilder.ComponentSegmentedControlVariants(
    scenario: DemoScenarioSpec?,
    items: List<SegmentedControlItem>,
) {
    Column(
        spacing = 12.dp,
        modifier = Modifier
            .fillMaxWidth()
            .margin(top = 16.dp, bottom = 24.dp)
            .componentScenarioTarget(scenario, DemoAutomationRole.Target),
    ) {
        ComponentFixtureTitle(R.string.demo_component_segmented_sizes)
        SegmentedControlSize.entries.forEachIndexed { index, size ->
            Text(
                text = size.name,
                style = Theme.typography.bodyMedium,
                color = TextDefaults.secondaryColor(),
            )
            SegmentedControl(
                items = items,
                selectedIndex = index.coerceAtMost(items.lastIndex),
                onSelectionChange = {},
                size = size,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        ComponentFixtureTitle(R.string.demo_component_disabled_state)
        SegmentedControl(
            items = items,
            selectedIndex = 0,
            onSelectionChange = {},
            enabled = false,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
