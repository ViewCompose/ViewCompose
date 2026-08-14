package com.viewcompose

import com.viewcompose.demo.automation.demoAutomationTarget
import com.viewcompose.demo.contract.DemoAutomationRole
import com.viewcompose.demo.contract.DemoScenarioId
import com.viewcompose.demo.contract.DemoScenarioSpec
import com.viewcompose.demo.registry.DemoScenarioIds
import com.viewcompose.host.android.resources.stringResource
import com.viewcompose.preview.tooling.ViewComposePreview
import com.viewcompose.runtime.mutableStateOf
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

@ViewComposePreview(name = "Foundations · Locals", group = "Demo/Foundations")
internal fun UiTreeBuilder.PreviewFoundationsLocals() {
    FoundationsPage(FoundationsFixture.Locals)
}

@ViewComposePreview(name = "Foundations · Theme", group = "Demo/Foundations")
internal fun UiTreeBuilder.PreviewFoundationsTheme() {
    FoundationsPage(FoundationsFixture.Theme)
}

@ViewComposePreview(name = "Foundations · Media", group = "Demo/Foundations")
internal fun UiTreeBuilder.PreviewFoundationsMedia() {
    FoundationsPage(FoundationsFixture.Media)
}

@ViewComposePreview(name = "Foundations · Typography", group = "Demo/Foundations")
internal fun UiTreeBuilder.PreviewFoundationsTypography() {
    FoundationsPage(FoundationsFixture.Typography)
}

internal enum class FoundationsFixture(
    val scenarioId: DemoScenarioId,
) {
    Locals(DemoScenarioIds.FoundationsLocals),
    Theme(DemoScenarioIds.FoundationsTheme),
    Media(DemoScenarioIds.FoundationsMedia),
    Typography(DemoScenarioIds.FoundationsTypography),
    ;

    companion object {
        fun from(scenarioId: DemoScenarioId): FoundationsFixture =
            entries.singleOrNull { fixture -> fixture.scenarioId == scenarioId }
                ?: error("Unsupported foundations scenario: $scenarioId")
    }
}

internal fun UiTreeBuilder.FoundationsPage(
    fixture: FoundationsFixture,
    scenario: DemoScenarioSpec? = null,
) {
    if (fixture != FoundationsFixture.Media) {
        when (fixture) {
            FoundationsFixture.Locals -> FoundationsLocalsFixture(scenario)
            FoundationsFixture.Theme -> FoundationsThemeFixture(scenario)
            FoundationsFixture.Typography -> FoundationsTypographyFixture(scenario)
            FoundationsFixture.Media -> Unit
        }
        return
    }

    val generation = rememberSaveable(key = "foundations-media-session-generation") {
        mutableStateOf(0)
    }
    key(generation.value) {
        FoundationsMediaFixture(
            scenario = scenario,
            generation = generation.value,
            onReset = { generation.value += 1 },
        )
    }
}

internal fun UiTreeBuilder.FoundationsFixtureList(
    generation: Int = 0,
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

internal fun UiTreeBuilder.FoundationsSummary(scenario: DemoScenarioSpec?) {
    scenario?.let {
        Text(
            text = stringResource(it.summaryRes),
            style = Theme.typography.bodyMedium,
            color = TextDefaults.secondaryColor(),
            modifier = Modifier.fillMaxWidth().margin(top = 12.dp, bottom = 8.dp),
        )
    }
}

internal fun Modifier.foundationsScenarioTarget(
    scenario: DemoScenarioSpec?,
    role: DemoAutomationRole,
): Modifier = scenario?.automation?.get(role)?.let(::demoAutomationTarget) ?: this
