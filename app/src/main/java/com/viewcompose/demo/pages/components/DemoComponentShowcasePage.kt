package com.viewcompose

import com.viewcompose.demo.automation.demoAutomationTarget
import com.viewcompose.demo.contract.DemoAutomationRole
import com.viewcompose.demo.contract.DemoScenarioId
import com.viewcompose.demo.contract.DemoScenarioSpec
import com.viewcompose.demo.registry.DemoScenarioIds
import com.viewcompose.host.android.resources.stringResource
import com.viewcompose.preview.tooling.ViewComposePreview
import com.viewcompose.runtime.mutableStateOf
import com.viewcompose.ui.foundation.Button
import com.viewcompose.ui.foundation.ButtonVariant
import com.viewcompose.ui.foundation.Column
import com.viewcompose.ui.foundation.LazyColumn
import com.viewcompose.ui.foundation.Text
import com.viewcompose.ui.foundation.TextDefaults
import com.viewcompose.ui.foundation.Theme
import com.viewcompose.ui.foundation.UiTreeBuilder
import com.viewcompose.ui.foundation.key
import com.viewcompose.ui.foundation.rememberSaveable
import com.viewcompose.ui.modifier.Modifier
import com.viewcompose.ui.modifier.fillMaxSize
import com.viewcompose.ui.modifier.fillMaxWidth
import com.viewcompose.ui.modifier.margin
import com.viewcompose.ui.unit.dp

@ViewComposePreview(name = "Component · Button", group = "Demo/Components")
internal fun UiTreeBuilder.PreviewComponentButton() {
    ComponentShowcasePage(ComponentShowcaseFixture.Button)
}

@ViewComposePreview(name = "Component · Icon button", group = "Demo/Components")
internal fun UiTreeBuilder.PreviewComponentIconButton() {
    ComponentShowcasePage(ComponentShowcaseFixture.IconButton)
}

@ViewComposePreview(name = "Component · Segmented control", group = "Demo/Components")
internal fun UiTreeBuilder.PreviewComponentSegmentedControl() {
    ComponentShowcasePage(ComponentShowcaseFixture.SegmentedControl)
}

@ViewComposePreview(name = "Component · Divider", group = "Demo/Components")
internal fun UiTreeBuilder.PreviewComponentDivider() {
    ComponentShowcasePage(ComponentShowcaseFixture.Divider)
}

@ViewComposePreview(name = "Component · Progress", group = "Demo/Components")
internal fun UiTreeBuilder.PreviewComponentProgress() {
    ComponentShowcasePage(ComponentShowcaseFixture.Progress)
}

internal enum class ComponentShowcaseFixture(
    val scenarioId: DemoScenarioId,
) {
    Button(DemoScenarioIds.ComponentButton),
    IconButton(DemoScenarioIds.ComponentIconButton),
    SegmentedControl(DemoScenarioIds.ComponentSegmentedControl),
    Divider(DemoScenarioIds.ComponentDivider),
    Progress(DemoScenarioIds.ComponentProgress),
    ;

    companion object {
        fun from(scenarioId: DemoScenarioId): ComponentShowcaseFixture =
            entries.singleOrNull { fixture -> fixture.scenarioId == scenarioId }
                ?: error("Unsupported component-showcase scenario: $scenarioId")
    }
}

internal fun UiTreeBuilder.ComponentShowcasePage(
    fixture: ComponentShowcaseFixture,
    scenario: DemoScenarioSpec? = null,
) {
    if (fixture == ComponentShowcaseFixture.Divider) {
        ComponentDividerFixture(scenario)
        return
    }

    val generation = rememberSaveable(key = "component-showcase-session-generation") {
        mutableStateOf(0)
    }
    key(generation.value) {
        when (fixture) {
            ComponentShowcaseFixture.Button -> ComponentButtonFixture(
                scenario = scenario,
                generation = generation.value,
                onReset = { generation.value += 1 },
            )

            ComponentShowcaseFixture.IconButton -> ComponentIconButtonFixture(
                scenario = scenario,
                generation = generation.value,
                onReset = { generation.value += 1 },
            )

            ComponentShowcaseFixture.SegmentedControl -> ComponentSegmentedControlFixture(
                scenario = scenario,
                generation = generation.value,
                onReset = { generation.value += 1 },
            )

            ComponentShowcaseFixture.Progress -> ComponentProgressFixture(
                scenario = scenario,
                generation = generation.value,
                onReset = { generation.value += 1 },
            )

            ComponentShowcaseFixture.Divider -> Unit
        }
    }
}

internal fun UiTreeBuilder.ComponentFixtureList(
    generation: Int,
    sections: List<String>,
    content: UiTreeBuilder.(String) -> Unit,
) {
    LazyColumn(
        items = sections,
        key = { section -> "$generation:$section" },
        modifier = Modifier.fillMaxSize(),
        itemContent = content,
    )
}

internal fun UiTreeBuilder.ComponentFixtureHeader(
    scenario: DemoScenarioSpec?,
    state: UiTreeBuilder.() -> String,
    onReset: () -> Unit,
    primaryContent: UiTreeBuilder.() -> Unit,
) {
    Column(
        spacing = 10.dp,
        modifier = Modifier.fillMaxWidth().margin(top = 12.dp),
    ) {
        scenario?.let {
            Text(
                text = stringResource(it.summaryRes),
                style = Theme.typography.bodyMedium,
                color = TextDefaults.secondaryColor(),
            )
        }
        primaryContent()
        Text(
            // Resolve dynamic copy inside the independent lazy-item Session.
            text = state(),
            style = Theme.typography.bodyMedium,
            color = TextDefaults.secondaryColor(),
            modifier = Modifier.componentScenarioTarget(scenario, DemoAutomationRole.State),
        )
        Button(
            text = stringResource(R.string.demo_component_reset),
            variant = ButtonVariant.Outlined,
            onClick = onReset,
            modifier = Modifier.componentScenarioTarget(scenario, DemoAutomationRole.Reset),
        )
    }
}

internal fun UiTreeBuilder.ComponentFixtureTitle(titleRes: Int) {
    Text(
        text = stringResource(titleRes),
        style = Theme.typography.titleMedium,
        color = Theme.colors.onSurface,
    )
}

internal fun Modifier.componentScenarioTarget(
    scenario: DemoScenarioSpec?,
    role: DemoAutomationRole,
): Modifier {
    val target = scenario?.automation?.get(role) ?: return this
    return demoAutomationTarget(target)
}
