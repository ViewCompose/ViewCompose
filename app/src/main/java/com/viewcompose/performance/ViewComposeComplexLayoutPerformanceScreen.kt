package com.viewcompose.performance

import com.viewcompose.demo.automation.demoAutomationTarget
import com.viewcompose.demo.contract.DemoAutomationRole
import com.viewcompose.demo.contract.DemoScenarioSpec
import com.viewcompose.runtime.mutableStateOf
import com.viewcompose.runtime.derivedStateOf
import com.viewcompose.runtime.State
import com.viewcompose.ui.graphics.UiShadow
import com.viewcompose.ui.layout.VerticalAlignment
import com.viewcompose.ui.modifier.Modifier
import com.viewcompose.ui.modifier.backgroundColor
import com.viewcompose.ui.modifier.cornerRadius
import com.viewcompose.ui.modifier.dropShadows
import com.viewcompose.ui.modifier.fillMaxSize
import com.viewcompose.ui.modifier.fillMaxWidth
import com.viewcompose.ui.modifier.height
import com.viewcompose.ui.modifier.padding
import com.viewcompose.ui.modifier.width
import com.viewcompose.ui.foundation.Column
import com.viewcompose.ui.foundation.Row
import com.viewcompose.ui.foundation.ScrollableColumn
import com.viewcompose.ui.foundation.Surface
import com.viewcompose.ui.foundation.Text
import com.viewcompose.ui.foundation.TextDefaults
import com.viewcompose.ui.foundation.UiTreeBuilder
import com.viewcompose.ui.unit.dp
import com.viewcompose.ui.foundation.remember
import com.viewcompose.ui.foundation.observedValue

/**
 * ViewCompose 版本的复杂布局性能场景。
 * ViewCompose implementation of the complex-layout performance scenario.
 */
internal fun UiTreeBuilder.ViewComposeComplexLayoutPerformanceScreen(
    shadowsEnabled: Boolean,
    scenario: DemoScenarioSpec,
    fixtures: PerformanceFixtures,
) {
    val propertyRevisionState = remember { mutableStateOf(0) }
    val structureRevisionState = remember { mutableStateOf(0) }
    val propertyCards = remember {
        derivedStateOf { fixtures.dashboardCards(propertyRevisionState.value) }
    }
    val structureRevision = structureRevisionState.value
    val structureCards = fixtures.dashboardCards(structureRevision)
    Column(
        modifier = Modifier
            .fillMaxSize()
            .backgroundColor(PERFORMANCE_BACKGROUND_COLOR)
            .scenarioTarget(scenario, DemoAutomationRole.Root),
    ) {
        ComplexLayoutPerformanceHeader(
            engineName = fixtures.copy.engineName(PerformanceEngine.ViewCompose, shadowsEnabled),
            propertyRevisionState = propertyRevisionState,
            structureRevisionState = structureRevisionState,
            onPropertyUpdate = {
                propertyRevisionState.value = propertyRevisionState.value + 1
            },
            onStructureUpdate = {
                structureRevisionState.value = structureRevisionState.value + 1
            },
            onReset = {
                propertyRevisionState.value = 0
                structureRevisionState.value = 0
            },
            scenario = scenario,
            copy = fixtures.copy,
        )
        ScrollableColumn(
            spacing = 8.dp,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(8.dp)
                .scenarioTarget(scenario, DemoAutomationRole.Target),
        ) {
            structureCards.forEachIndexed { index, card ->
                DashboardCard(
                    card = card,
                    cardIndex = index,
                    propertyCards = propertyCards,
                    shadowsEnabled = shadowsEnabled,
                    copy = fixtures.copy,
                )
            }
        }
    }
}

private fun UiTreeBuilder.ComplexLayoutPerformanceHeader(
    engineName: String,
    propertyRevisionState: State<Int>,
    structureRevisionState: State<Int>,
    onPropertyUpdate: () -> Unit,
    onStructureUpdate: () -> Unit,
    onReset: () -> Unit,
    scenario: DemoScenarioSpec,
    copy: PerformanceCopy,
) {
    Column(
        spacing = 8.dp,
        modifier = Modifier
            .fillMaxWidth()
            .backgroundColor(PERFORMANCE_SURFACE_COLOR)
            .padding(12.dp),
    ) {
        Text(
            text = copy.complexReady(engineName),
            style = TextDefaults.titleMediumStyle(),
            color = PERFORMANCE_PRIMARY_TEXT_COLOR,
            modifier = Modifier.scenarioTarget(scenario, DemoAutomationRole.Ready),
        )
        Text(
            text = observedValue {
                copy.dashboardRevision(
                    propertyRevisionState.value,
                    structureRevisionState.value,
                )
            },
            color = PERFORMANCE_SECONDARY_TEXT_COLOR,
            modifier = Modifier.scenarioTarget(scenario, DemoAutomationRole.State),
        )
        Row(
            spacing = 8.dp,
            verticalAlignment = VerticalAlignment.Center,
        ) {
            ComplexLayoutAction(
                text = copy.updateDashboard,
                onClick = onPropertyUpdate,
                modifier = Modifier.scenarioTarget(
                    scenario,
                    DemoAutomationRole.PrimaryAction,
                ),
            )
            ComplexLayoutAction(
                text = copy.updateDashboardStructure,
                onClick = onStructureUpdate,
                modifier = Modifier.scenarioTarget(
                    scenario,
                    DemoAutomationRole.SecondaryAction,
                ),
            )
            ComplexLayoutAction(
                text = copy.resetDashboard,
                onClick = onReset,
                modifier = Modifier.scenarioTarget(scenario, DemoAutomationRole.Reset),
            )
        }
    }
}

private fun UiTreeBuilder.ComplexLayoutAction(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        contentColor = 0xFFFFFFFF.toInt(),
        modifier = modifier
            .backgroundColor(PERFORMANCE_PRIMARY_COLOR)
            .cornerRadius(8.dp)
            .padding(
                horizontal = 14.dp,
                vertical = 8.dp,
            ),
    ) {
        Text(
            text = text,
            color = 0xFFFFFFFF.toInt(),
        )
    }
}

private fun Modifier.scenarioTarget(
    scenario: DemoScenarioSpec,
    role: DemoAutomationRole,
): Modifier = demoAutomationTarget(scenario.automation.require(role))

/**
 * 复杂布局卡片包含嵌套行列、标签和条件明细，用于放大布局与 patch 成本。
 * Complex cards include nested rows, tags, and conditional details to amplify layout and patch cost.
 */
private fun UiTreeBuilder.DashboardCard(
    card: PerformanceDashboardCard,
    cardIndex: Int,
    propertyCards: State<List<PerformanceDashboardCard>>,
    shadowsEnabled: Boolean,
    copy: PerformanceCopy,
) {
    val baseModifier = Modifier
        .fillMaxWidth()
        .backgroundColor(PERFORMANCE_SURFACE_COLOR)
        .cornerRadius(12.dp)
        .padding(12.dp)
    val cardModifier = if (shadowsEnabled) {
        baseModifier.dropShadows(PerformanceDashboardShadows)
    } else {
        baseModifier
    }
    Surface(
        key = card.id,
        modifier = cardModifier,
    ) {
        Column(
            spacing = 10.dp,
            modifier = Modifier.fillMaxWidth(),
        ) {
            DashboardCardHeader(card, cardIndex, propertyCards)
            DashboardMetricRow(card.metrics, cardIndex, propertyCards)
            DashboardTagRow(card.tags)
            if (card.detailsVisible) {
                Row(
                    spacing = 8.dp,
                    verticalAlignment = VerticalAlignment.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .backgroundColor(PERFORMANCE_BACKGROUND_COLOR)
                        .cornerRadius(8.dp)
                        .padding(8.dp),
                ) {
                    Text(
                        text = copy.detail,
                        style = TextDefaults.labelMediumStyle(),
                        color = card.accentColor,
                    )
                    Text(
                        text = copy.detailContent(card.id + 1),
                        style = TextDefaults.bodySmallStyle(),
                        color = PERFORMANCE_SECONDARY_TEXT_COLOR,
                        maxLines = 1,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

private val PerformanceDashboardShadows = listOf(
    UiShadow(
        color = 0x28000000,
        blurRadius = 7.dp,
        offsetY = 3.dp,
    ),
    UiShadow(
        color = 0x12000000,
        blurRadius = 14.dp,
        spreadRadius = 1.dp,
        offsetY = 7.dp,
    ),
)

private fun UiTreeBuilder.DashboardCardHeader(
    card: PerformanceDashboardCard,
    cardIndex: Int,
    propertyCards: State<List<PerformanceDashboardCard>>,
) {
    Row(
        spacing = 10.dp,
        verticalAlignment = VerticalAlignment.Center,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Surface(
            contentColor = card.accentColor,
            modifier = Modifier
                .width(10.dp)
                .height(48.dp)
                .backgroundColor(card.accentColor)
                .cornerRadius(5.dp),
        ) {}
        Column(
            spacing = 3.dp,
            modifier = Modifier.weight(1f),
        ) {
            Text(
                text = card.title,
                style = TextDefaults.titleSmallStyle(),
                color = PERFORMANCE_PRIMARY_TEXT_COLOR,
                maxLines = 1,
            )
            Text(
                text = observedValue { propertyCards.value[cardIndex].subtitle },
                style = TextDefaults.bodySmallStyle(),
                color = PERFORMANCE_SECONDARY_TEXT_COLOR,
                maxLines = 1,
            )
        }
        Surface(
            contentColor = card.accentColor,
            modifier = Modifier
                .backgroundColor(PERFORMANCE_BADGE_COLOR)
                .cornerRadius(12.dp)
                .padding(
                    horizontal = 8.dp,
                    vertical = 4.dp,
                ),
        ) {
            Text(
                text = observedValue { propertyCards.value[cardIndex].status },
                style = TextDefaults.labelMediumStyle(),
                color = card.accentColor,
                maxLines = 1,
            )
        }
    }
}

private fun UiTreeBuilder.DashboardMetricRow(
    metrics: List<PerformanceDashboardMetric>,
    cardIndex: Int,
    propertyCards: State<List<PerformanceDashboardCard>>,
) {
    Row(
        spacing = 6.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        metrics.forEachIndexed { metricIndex, metric ->
            Column(
                spacing = 2.dp,
                modifier = Modifier
                    .weight(1f)
                    .backgroundColor(PERFORMANCE_BACKGROUND_COLOR)
                    .cornerRadius(8.dp)
                    .padding(8.dp),
            ) {
                Text(
                    text = metric.label,
                    style = TextDefaults.bodySmallStyle(),
                    color = PERFORMANCE_SECONDARY_TEXT_COLOR,
                    maxLines = 1,
                )
                Text(
                    text = observedValue {
                        propertyCards.value[cardIndex].metrics[metricIndex].value
                    },
                    style = TextDefaults.titleSmallStyle(),
                    color = PERFORMANCE_PRIMARY_TEXT_COLOR,
                    maxLines = 1,
                )
            }
        }
    }
}

private fun UiTreeBuilder.DashboardTagRow(tags: List<String>) {
    Row(
        spacing = 6.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        tags.forEach { tag ->
            Surface(
                contentColor = PERFORMANCE_SECONDARY_TEXT_COLOR,
                modifier = Modifier
                    .backgroundColor(PERFORMANCE_BADGE_COLOR)
                    .cornerRadius(10.dp)
                    .padding(
                        horizontal = 7.dp,
                        vertical = 3.dp,
                    ),
            ) {
                Text(
                    text = tag,
                    style = TextDefaults.bodySmallStyle(),
                    color = PERFORMANCE_SECONDARY_TEXT_COLOR,
                    maxLines = 1,
                )
            }
        }
    }
}
