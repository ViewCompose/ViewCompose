package com.viewcompose

import com.viewcompose.demo.contract.DemoAutomationRole
import com.viewcompose.demo.contract.DemoScenarioSpec
import com.viewcompose.host.android.resources.stringResource
import com.viewcompose.runtime.mutableStateOf
import com.viewcompose.ui.foundation.Button
import com.viewcompose.ui.foundation.CircularProgressIndicator
import com.viewcompose.ui.foundation.CircularProgressIndicatorOverrides
import com.viewcompose.ui.foundation.Column
import com.viewcompose.ui.foundation.Divider
import com.viewcompose.ui.foundation.LazyColumn
import com.viewcompose.ui.foundation.LinearProgressIndicator
import com.viewcompose.ui.foundation.LinearProgressIndicatorOverrides
import com.viewcompose.ui.foundation.Row
import com.viewcompose.ui.foundation.Text
import com.viewcompose.ui.foundation.TextDefaults
import com.viewcompose.ui.foundation.Theme
import com.viewcompose.ui.foundation.UiTreeBuilder
import com.viewcompose.ui.foundation.rememberSaveable
import com.viewcompose.ui.layout.VerticalAlignment
import com.viewcompose.ui.modifier.Modifier
import com.viewcompose.ui.modifier.fillMaxSize
import com.viewcompose.ui.modifier.fillMaxWidth
import com.viewcompose.ui.modifier.margin
import com.viewcompose.ui.unit.dp

internal fun UiTreeBuilder.ComponentDividerFixture(scenario: DemoScenarioSpec?) {
    LazyColumn(
        items = listOf("default", "colors", "thickness"),
        key = { it },
        modifier = Modifier.fillMaxSize(),
    ) { section ->
        Column(
            spacing = 8.dp,
            modifier = Modifier.fillMaxWidth().margin(top = 12.dp),
        ) {
            if (section == "default") {
                scenario?.let {
                    Text(
                        text = stringResource(it.summaryRes),
                        style = Theme.typography.bodyMedium,
                        color = TextDefaults.secondaryColor(),
                    )
                }
                ComponentFixtureTitle(R.string.demo_component_divider_default)
                Divider(
                    modifier = Modifier.componentScenarioTarget(
                        scenario,
                        DemoAutomationRole.Target,
                    ),
                )
            } else if (section == "colors") {
                ComponentFixtureTitle(R.string.demo_component_divider_colors)
                listOf(
                    R.string.demo_component_color_primary to Theme.colors.primary,
                    R.string.demo_component_color_secondary to Theme.colors.secondary,
                    R.string.demo_component_color_on_surface_variant to
                        Theme.colors.onSurfaceVariant,
                ).forEach { (labelRes, color) ->
                    Text(text = stringResource(labelRes), style = Theme.typography.bodyMedium)
                    Divider(color = color)
                }
            } else {
                ComponentFixtureTitle(R.string.demo_component_divider_thickness)
                listOf(1, 2, 4, 8).forEach { thickness ->
                    Text(
                        text = stringResource(
                            R.string.demo_component_divider_thickness_value,
                            thickness,
                        ),
                        style = Theme.typography.bodyMedium,
                    )
                    Divider(
                        thickness = thickness.dp,
                        modifier = Modifier.margin(bottom = 8.dp),
                    )
                }
            }
        }
    }
}

internal fun UiTreeBuilder.ComponentProgressFixture(
    scenario: DemoScenarioSpec?,
    generation: Int,
    onReset: () -> Unit,
) {
    val progressPercent = rememberSaveable(key = "component-progress-percent") {
        mutableStateOf(25)
    }
    ComponentFixtureList(generation, listOf("control", "variants")) { section ->
        when (section) {
            "control" -> ComponentFixtureHeader(
                scenario = scenario,
                state = {
                    stringResource(
                        R.string.demo_component_progress_percent,
                        progressPercent.value,
                    )
                },
                onReset = onReset,
            ) {
                Button(
                    text = stringResource(R.string.demo_component_progress_advance),
                    onClick = {
                        progressPercent.value = (progressPercent.value + 25) % 125
                    },
                    modifier = Modifier.componentScenarioTarget(
                        scenario,
                        DemoAutomationRole.PrimaryAction,
                    ),
                )
                Row(
                    spacing = 16.dp,
                    verticalAlignment = VerticalAlignment.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .componentScenarioTarget(scenario, DemoAutomationRole.Target),
                ) {
                    LinearProgressIndicator(
                        progress = progressPercent.value / 100f,
                        modifier = Modifier.fillMaxWidth().weight(1f),
                    )
                    CircularProgressIndicator(progress = progressPercent.value / 100f)
                }
            }

            else -> ComponentProgressVariants()
        }
    }
}

private fun UiTreeBuilder.ComponentProgressVariants() {
    Column(
        spacing = 12.dp,
        modifier = Modifier.fillMaxWidth().margin(top = 16.dp, bottom = 24.dp),
    ) {
        ComponentFixtureTitle(R.string.demo_component_progress_variants)
        Text(
            text = stringResource(R.string.demo_component_progress_indeterminate),
            style = Theme.typography.bodyMedium,
            color = TextDefaults.secondaryColor(),
        )
        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        CircularProgressIndicator()
        Text(
            text = stringResource(R.string.demo_component_progress_custom),
            style = Theme.typography.bodyMedium,
            color = TextDefaults.secondaryColor(),
        )
        LinearProgressIndicator(
            progress = 0.6f,
            overrides = LinearProgressIndicatorOverrides(
                indicatorColor = Theme.colors.secondary,
                trackColor = Theme.colors.surfaceVariant,
            ),
        )
        CircularProgressIndicator(
            progress = 0.5f,
            overrides = CircularProgressIndicatorOverrides(
                size = 64.dp,
                trackThickness = 6.dp,
            ),
        )
    }
}
