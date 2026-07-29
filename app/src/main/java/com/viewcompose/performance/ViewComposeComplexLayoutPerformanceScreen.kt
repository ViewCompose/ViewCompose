package com.viewcompose.performance

import com.viewcompose.runtime.mutableStateOf
import com.viewcompose.ui.layout.VerticalAlignment
import com.viewcompose.ui.modifier.Modifier
import com.viewcompose.ui.modifier.backgroundColor
import com.viewcompose.ui.modifier.cornerRadius
import com.viewcompose.ui.modifier.fillMaxSize
import com.viewcompose.ui.modifier.fillMaxWidth
import com.viewcompose.ui.modifier.height
import com.viewcompose.ui.modifier.padding
import com.viewcompose.ui.modifier.width
import com.viewcompose.widget.core.Column
import com.viewcompose.widget.core.Row
import com.viewcompose.widget.core.ScrollableColumn
import com.viewcompose.widget.core.Surface
import com.viewcompose.widget.core.Text
import com.viewcompose.widget.core.TextDefaults
import com.viewcompose.widget.core.UiTreeBuilder
import com.viewcompose.ui.unit.dp
import com.viewcompose.widget.core.remember

/**
 * ViewCompose 版本的复杂布局性能场景。
 * ViewCompose implementation of the complex-layout performance scenario.
 */
internal fun UiTreeBuilder.ViewComposeComplexLayoutPerformanceScreen() {
    val revisionState = remember { mutableStateOf(0) }
    val revision = revisionState.value
    val cards = performanceDashboardCards(revision)
    Column(
        modifier = Modifier
            .fillMaxSize()
            .backgroundColor(PERFORMANCE_BACKGROUND_COLOR),
    ) {
        ComplexLayoutPerformanceHeader(
            engineName = PerformanceEngine.ViewCompose.displayName,
            revision = revision,
            onUpdate = {
                revisionState.value = revisionState.value + 1
            },
            onReset = {
                revisionState.value = 0
            },
        )
        ScrollableColumn(
            spacing = 8.dp,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(8.dp),
        ) {
            cards.forEach { card ->
                DashboardCard(card)
            }
        }
    }
}

private fun UiTreeBuilder.ComplexLayoutPerformanceHeader(
    engineName: String,
    revision: Int,
    onUpdate: () -> Unit,
    onReset: () -> Unit,
) {
    Column(
        spacing = 8.dp,
        modifier = Modifier
            .fillMaxWidth()
            .backgroundColor(PERFORMANCE_SURFACE_COLOR)
            .padding(12.dp),
    ) {
        Text(
            text = "$engineName Complex Ready",
            style = TextDefaults.titleMediumStyle(),
            color = PERFORMANCE_PRIMARY_TEXT_COLOR,
        )
        Text(
            text = "Dashboard revision $revision",
            color = PERFORMANCE_SECONDARY_TEXT_COLOR,
        )
        Row(
            spacing = 8.dp,
            verticalAlignment = VerticalAlignment.Center,
        ) {
            ComplexLayoutAction(
                text = "Update dashboard",
                onClick = onUpdate,
            )
            ComplexLayoutAction(
                text = "Reset dashboard",
                onClick = onReset,
            )
        }
    }
}

private fun UiTreeBuilder.ComplexLayoutAction(
    text: String,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        contentColor = 0xFFFFFFFF.toInt(),
        modifier = Modifier
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

/**
 * 复杂布局卡片包含嵌套行列、标签和条件明细，用于放大布局与 patch 成本。
 * Complex cards include nested rows, tags, and conditional details to amplify layout and patch cost.
 */
private fun UiTreeBuilder.DashboardCard(card: PerformanceDashboardCard) {
    Surface(
        key = card.id,
        modifier = Modifier
            .fillMaxWidth()
            .backgroundColor(PERFORMANCE_SURFACE_COLOR)
            .cornerRadius(12.dp)
            .padding(12.dp),
    ) {
        Column(
            spacing = 10.dp,
            modifier = Modifier.fillMaxWidth(),
        ) {
            DashboardCardHeader(card)
            DashboardMetricRow(card.metrics)
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
                        text = "Detail",
                        style = TextDefaults.labelMediumStyle(),
                        color = card.accentColor,
                    )
                    Text(
                        text = "Additional nested content for section ${card.id + 1}",
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

private fun UiTreeBuilder.DashboardCardHeader(card: PerformanceDashboardCard) {
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
                text = card.subtitle,
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
                text = card.status,
                style = TextDefaults.labelMediumStyle(),
                color = card.accentColor,
                maxLines = 1,
            )
        }
    }
}

private fun UiTreeBuilder.DashboardMetricRow(
    metrics: List<PerformanceDashboardMetric>,
) {
    Row(
        spacing = 6.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        metrics.forEach { metric ->
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
                    text = metric.value,
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
