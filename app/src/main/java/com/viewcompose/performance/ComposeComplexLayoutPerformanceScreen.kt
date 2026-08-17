package com.viewcompose.performance

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.viewcompose.demo.contract.DemoAutomationRole
import com.viewcompose.demo.contract.DemoScenarioSpec

/**
 * Compose 对照版本的复杂布局性能场景。
 * Compose control implementation of the complex-layout performance scenario.
 */
@Composable
internal fun ComposeComplexLayoutPerformanceScreen(
    shadowsEnabled: Boolean,
    scenario: DemoScenarioSpec,
    fixtures: PerformanceFixtures,
) {
    var propertyRevision by remember { mutableIntStateOf(0) }
    var structureRevision by remember { mutableIntStateOf(0) }
    val propertyCards = fixtures.dashboardCards(propertyRevision)
    val structureCards = fixtures.dashboardCards(structureRevision)
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(PERFORMANCE_BACKGROUND_COLOR))
            .performanceScenarioTarget(
                scenario,
                DemoAutomationRole.Root,
                enableResourceIds = true,
            ),
    ) {
        ComposeComplexLayoutPerformanceHeader(
            engineName = fixtures.copy.engineName(PerformanceEngine.Compose, shadowsEnabled),
            propertyRevision = propertyRevision,
            structureRevision = structureRevision,
            onPropertyUpdate = {
                propertyRevision += 1
            },
            onStructureUpdate = {
                structureRevision += 1
            },
            onReset = {
                propertyRevision = 0
                structureRevision = 0
            },
            scenario = scenario,
            copy = fixtures.copy,
        )
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(8.dp)
                .performanceScenarioTarget(scenario, DemoAutomationRole.Target),
        ) {
            structureCards.forEachIndexed { index, card ->
                ComposeDashboardCard(
                    structureCard = card,
                    propertyCard = propertyCards[index],
                    shadowsEnabled = shadowsEnabled,
                    copy = fixtures.copy,
                )
            }
        }
    }
}

@Composable
private fun ComposeComplexLayoutPerformanceHeader(
    engineName: String,
    propertyRevision: Int,
    structureRevision: Int,
    onPropertyUpdate: () -> Unit,
    onStructureUpdate: () -> Unit,
    onReset: () -> Unit,
    scenario: DemoScenarioSpec,
    copy: PerformanceCopy,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(PERFORMANCE_SURFACE_COLOR))
            .padding(12.dp),
    ) {
        PerformanceText(
            text = copy.complexReady(engineName),
            sizeSp = 18,
            weight = FontWeight.SemiBold,
            color = PERFORMANCE_PRIMARY_TEXT_COLOR,
            modifier = Modifier.performanceScenarioTarget(scenario, DemoAutomationRole.Ready),
        )
        PerformanceText(
            text = copy.dashboardRevision(propertyRevision, structureRevision),
            sizeSp = 14,
            color = PERFORMANCE_SECONDARY_TEXT_COLOR,
            modifier = Modifier.performanceScenarioTarget(scenario, DemoAutomationRole.State),
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ComposeComplexLayoutAction(
                text = copy.updateDashboard,
                onClick = onPropertyUpdate,
                modifier = Modifier.performanceScenarioTarget(
                    scenario,
                    DemoAutomationRole.PrimaryAction,
                ),
            )
            ComposeComplexLayoutAction(
                text = copy.updateDashboardStructure,
                onClick = onStructureUpdate,
                modifier = Modifier.performanceScenarioTarget(
                    scenario,
                    DemoAutomationRole.SecondaryAction,
                ),
            )
            ComposeComplexLayoutAction(
                text = copy.resetDashboard,
                onClick = onReset,
                modifier = Modifier.performanceScenarioTarget(scenario, DemoAutomationRole.Reset),
            )
        }
    }
}

@Composable
private fun ComposeComplexLayoutAction(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .background(
                color = Color(PERFORMANCE_PRIMARY_COLOR),
                shape = RoundedCornerShape(8.dp),
            )
            .clickable(onClick = onClick)
            .padding(
                horizontal = 14.dp,
                vertical = 8.dp,
            ),
    ) {
        PerformanceText(
            text = text,
            sizeSp = 14,
            weight = FontWeight.Medium,
            color = 0xFFFFFFFF.toInt(),
        )
    }
}

/**
 * Compose 对照卡片，结构需要与 ViewCompose 版本保持同等复杂度。
 * Compose control card; its structure should stay equally complex to the ViewCompose version.
 */
@Composable
private fun ComposeDashboardCard(
    structureCard: PerformanceDashboardCard,
    propertyCard: PerformanceDashboardCard,
    shadowsEnabled: Boolean,
    copy: PerformanceCopy,
) {
    val shape = RoundedCornerShape(12.dp)
    val shadowModifier = if (shadowsEnabled) {
        Modifier.shadow(
            elevation = 8.dp,
            shape = shape,
            clip = false,
        )
    } else {
        Modifier
    }
    Column(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier
            .fillMaxWidth()
            .then(shadowModifier)
            .background(
                color = Color(PERFORMANCE_SURFACE_COLOR),
                shape = shape,
            )
            .padding(12.dp),
    ) {
        ComposeDashboardCardHeader(structureCard, propertyCard)
        ComposeDashboardMetricRow(propertyCard.metrics)
        ComposeDashboardTagRow(structureCard.tags)
        if (structureCard.detailsVisible) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        color = Color(PERFORMANCE_BACKGROUND_COLOR),
                        shape = RoundedCornerShape(8.dp),
                    )
                    .padding(8.dp),
            ) {
                PerformanceText(
                    text = copy.detail,
                    sizeSp = 12,
                    weight = FontWeight.Medium,
                    color = structureCard.accentColor,
                )
                Box(modifier = Modifier.weight(1f)) {
                    PerformanceText(
                        text = copy.detailContent(structureCard.id + 1),
                        sizeSp = 12,
                        color = PERFORMANCE_SECONDARY_TEXT_COLOR,
                    )
                }
            }
        }
    }
}

@Composable
private fun ComposeDashboardCardHeader(
    structureCard: PerformanceDashboardCard,
    propertyCard: PerformanceDashboardCard,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Spacer(
            modifier = Modifier
                .width(10.dp)
                .height(48.dp)
                .background(
                    color = Color(structureCard.accentColor),
                    shape = RoundedCornerShape(5.dp),
                ),
        )
        Column(
            verticalArrangement = Arrangement.spacedBy(3.dp),
            modifier = Modifier.weight(1f),
        ) {
            PerformanceText(
                text = structureCard.title,
                sizeSp = 16,
                weight = FontWeight.SemiBold,
                color = PERFORMANCE_PRIMARY_TEXT_COLOR,
            )
            PerformanceText(
                text = propertyCard.subtitle,
                sizeSp = 12,
                color = PERFORMANCE_SECONDARY_TEXT_COLOR,
            )
        }
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .background(
                    color = Color(PERFORMANCE_BADGE_COLOR),
                    shape = RoundedCornerShape(12.dp),
                )
                .padding(
                    horizontal = 8.dp,
                    vertical = 4.dp,
                ),
        ) {
            PerformanceText(
                text = propertyCard.status,
                sizeSp = 12,
                weight = FontWeight.Medium,
                color = structureCard.accentColor,
            )
        }
    }
}

@Composable
private fun ComposeDashboardMetricRow(
    metrics: List<PerformanceDashboardMetric>,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        metrics.forEach { metric ->
            Column(
                verticalArrangement = Arrangement.spacedBy(2.dp),
                modifier = Modifier
                    .weight(1f)
                    .background(
                        color = Color(PERFORMANCE_BACKGROUND_COLOR),
                        shape = RoundedCornerShape(8.dp),
                    )
                    .padding(8.dp),
            ) {
                PerformanceText(
                    text = metric.label,
                    sizeSp = 12,
                    color = PERFORMANCE_SECONDARY_TEXT_COLOR,
                )
                PerformanceText(
                    text = metric.value,
                    sizeSp = 15,
                    weight = FontWeight.SemiBold,
                    color = PERFORMANCE_PRIMARY_TEXT_COLOR,
                )
            }
        }
    }
}

@Composable
private fun ComposeDashboardTagRow(tags: List<String>) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        tags.forEach { tag ->
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .background(
                        color = Color(PERFORMANCE_BADGE_COLOR),
                        shape = RoundedCornerShape(10.dp),
                    )
                    .padding(
                        horizontal = 7.dp,
                        vertical = 3.dp,
                    ),
            ) {
                PerformanceText(
                    text = tag,
                    sizeSp = 12,
                    color = PERFORMANCE_SECONDARY_TEXT_COLOR,
                )
            }
        }
    }
}
