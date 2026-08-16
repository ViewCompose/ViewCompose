package com.viewcompose.performance

import com.viewcompose.demo.automation.demoAutomationTarget
import com.viewcompose.demo.contract.DemoAutomationRole
import com.viewcompose.demo.contract.DemoScenarioSpec
import com.viewcompose.runtime.mutableStateOf
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
import com.viewcompose.ui.foundation.LazyColumn
import com.viewcompose.ui.foundation.Row
import com.viewcompose.ui.foundation.Surface
import com.viewcompose.ui.foundation.Text
import com.viewcompose.ui.foundation.TextDefaults
import com.viewcompose.ui.foundation.UiTreeBuilder
import com.viewcompose.ui.unit.dp
import com.viewcompose.ui.foundation.remember
import com.viewcompose.ui.node.policy.CollectionMotionPolicy

internal val PerformanceListMotionPolicy = CollectionMotionPolicy(
    disableItemAnimator = true,
)

/**
 * ViewCompose 版本的列表性能场景。
 * ViewCompose implementation of the list performance scenario.
 */
internal fun UiTreeBuilder.ViewComposeListPerformanceScreen(
    shadowsEnabled: Boolean,
    scenario: DemoScenarioSpec,
    fixtures: PerformanceFixtures,
) {
    val revisionState = remember { mutableStateOf(0) }
    val revision = revisionState.value
    // A/B seam: switch only this accessor to listRows(revision) to benchmark the plain-List path
    // against the same prebuilt immutable rows.
    val rows = fixtures.listSnapshot(revision)
    Column(
        modifier = Modifier
            .fillMaxSize()
            .backgroundColor(PERFORMANCE_BACKGROUND_COLOR)
            .scenarioTarget(scenario, DemoAutomationRole.Root),
    ) {
        ListPerformanceHeader(
            engineName = fixtures.copy.engineName(PerformanceEngine.ViewCompose, shadowsEnabled),
            revision = revision,
            onMutate = {
                revisionState.value = revisionState.value + 1
            },
            onReset = {
                revisionState.value = 0
            },
            scenario = scenario,
            copy = fixtures.copy,
        )
        LazyColumn(
            items = rows,
            key = PerformanceListRow::id,
            contentType = { "performance-list-row" },
            contentPadding = 8.dp,
            spacing = 6.dp,
            // Compose does not request animateItem and the Android Views control disables its
            // ItemAnimator. Keep mutation work equivalent instead of timing RecyclerView motion.
            motionPolicy = PerformanceListMotionPolicy,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .scenarioTarget(scenario, DemoAutomationRole.Target),
        ) { row ->
            PerformanceListRow(
                row = row,
                shadowsEnabled = shadowsEnabled,
            )
        }
    }
}

private fun UiTreeBuilder.ListPerformanceHeader(
    engineName: String,
    revision: Int,
    onMutate: () -> Unit,
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
            text = copy.listReady(engineName),
            style = TextDefaults.titleMediumStyle(),
            color = PERFORMANCE_PRIMARY_TEXT_COLOR,
            modifier = Modifier.scenarioTarget(scenario, DemoAutomationRole.Ready),
        )
        Text(
            text = copy.listRevision(revision),
            color = PERFORMANCE_SECONDARY_TEXT_COLOR,
            modifier = Modifier.scenarioTarget(scenario, DemoAutomationRole.State),
        )
        Row(
            spacing = 8.dp,
            verticalAlignment = VerticalAlignment.Center,
        ) {
            PerformanceAction(
                text = copy.mutateList,
                onClick = onMutate,
                modifier = Modifier.scenarioTarget(
                    scenario,
                    DemoAutomationRole.PrimaryAction,
                ),
            )
            PerformanceAction(
                text = copy.resetList,
                onClick = onReset,
                modifier = Modifier.scenarioTarget(scenario, DemoAutomationRole.Reset),
            )
        }
    }
}

private fun UiTreeBuilder.PerformanceAction(
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
): Modifier {
    val target = scenario.automation.require(role)
    return demoAutomationTarget(target)
}

/**
 * 单行使用稳定 key 的 Surface，便于测量列表重排时的节点复用。
 * Row surface uses a stable key so list reordering measures node reuse.
 */
private fun UiTreeBuilder.PerformanceListRow(
    row: PerformanceListRow,
    shadowsEnabled: Boolean,
) {
    val baseModifier = Modifier
        .fillMaxWidth()
        .backgroundColor(PERFORMANCE_SURFACE_COLOR)
        .cornerRadius(10.dp)
        .padding(10.dp)
    val rowModifier = if (shadowsEnabled) {
        baseModifier.dropShadows(PerformanceRowShadows)
    } else {
        baseModifier
    }
    Surface(
        key = row.id,
        modifier = rowModifier,
    ) {
        Row(
            spacing = 10.dp,
            verticalAlignment = VerticalAlignment.Center,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Surface(
                contentColor = row.accentColor,
                modifier = Modifier
                    .width(6.dp)
                    .height(44.dp)
                    .backgroundColor(row.accentColor)
                    .cornerRadius(3.dp),
            ) {}
            Column(
                spacing = 3.dp,
                modifier = Modifier.weight(1f),
            ) {
                Text(
                    text = row.title,
                    color = PERFORMANCE_PRIMARY_TEXT_COLOR,
                    maxLines = 1,
                )
                Text(
                    text = row.subtitle,
                    style = TextDefaults.bodySmallStyle(),
                    color = PERFORMANCE_SECONDARY_TEXT_COLOR,
                    maxLines = 1,
                )
            }
            Surface(
                contentColor = PERFORMANCE_PRIMARY_COLOR,
                modifier = Modifier
                    .backgroundColor(PERFORMANCE_BADGE_COLOR)
                    .cornerRadius(12.dp)
                    .padding(
                        horizontal = 8.dp,
                        vertical = 4.dp,
                    ),
            ) {
                Text(
                    text = row.badge,
                    style = TextDefaults.labelMediumStyle(),
                    color = PERFORMANCE_PRIMARY_COLOR,
                    maxLines = 1,
                )
            }
        }
    }
}

private val PerformanceRowShadows = listOf(
    UiShadow(
        color = 0x24000000,
        blurRadius = 5.dp,
        offsetY = 2.dp,
    ),
    UiShadow(
        color = 0x14000000,
        blurRadius = 10.dp,
        spreadRadius = 1.dp,
        offsetY = 5.dp,
    ),
)
