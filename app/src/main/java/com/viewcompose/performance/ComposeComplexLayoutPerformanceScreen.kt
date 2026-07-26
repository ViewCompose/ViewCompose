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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
internal fun ComposeComplexLayoutPerformanceScreen() {
    var revision by remember { mutableIntStateOf(0) }
    val cards = performanceDashboardCards(revision)
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(PERFORMANCE_BACKGROUND_COLOR)),
    ) {
        ComposeComplexLayoutPerformanceHeader(
            engineName = PerformanceEngine.Compose.displayName,
            revision = revision,
            onUpdate = {
                revision += 1
            },
            onReset = {
                revision = 0
            },
        )
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(8.dp),
        ) {
            cards.forEach { card ->
                ComposeDashboardCard(card)
            }
        }
    }
}

@Composable
private fun ComposeComplexLayoutPerformanceHeader(
    engineName: String,
    revision: Int,
    onUpdate: () -> Unit,
    onReset: () -> Unit,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(PERFORMANCE_SURFACE_COLOR))
            .padding(12.dp),
    ) {
        PerformanceText(
            text = "$engineName Complex Ready",
            sizeSp = 18,
            weight = FontWeight.SemiBold,
            color = PERFORMANCE_PRIMARY_TEXT_COLOR,
        )
        PerformanceText(
            text = "Dashboard revision $revision",
            sizeSp = 14,
            color = PERFORMANCE_SECONDARY_TEXT_COLOR,
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ComposeComplexLayoutAction(
                text = "Update dashboard",
                onClick = onUpdate,
            )
            ComposeComplexLayoutAction(
                text = "Reset dashboard",
                onClick = onReset,
            )
        }
    }
}

@Composable
private fun ComposeComplexLayoutAction(
    text: String,
    onClick: () -> Unit,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
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

@Composable
private fun ComposeDashboardCard(card: PerformanceDashboardCard) {
    Column(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = Color(PERFORMANCE_SURFACE_COLOR),
                shape = RoundedCornerShape(12.dp),
            )
            .padding(12.dp),
    ) {
        ComposeDashboardCardHeader(card)
        ComposeDashboardMetricRow(card.metrics)
        ComposeDashboardTagRow(card.tags)
        if (card.detailsVisible) {
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
                    text = "Detail",
                    sizeSp = 12,
                    weight = FontWeight.Medium,
                    color = card.accentColor,
                )
                Box(modifier = Modifier.weight(1f)) {
                    PerformanceText(
                        text = "Additional nested content for section ${card.id + 1}",
                        sizeSp = 12,
                        color = PERFORMANCE_SECONDARY_TEXT_COLOR,
                    )
                }
            }
        }
    }
}

@Composable
private fun ComposeDashboardCardHeader(card: PerformanceDashboardCard) {
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
                    color = Color(card.accentColor),
                    shape = RoundedCornerShape(5.dp),
                ),
        )
        Column(
            verticalArrangement = Arrangement.spacedBy(3.dp),
            modifier = Modifier.weight(1f),
        ) {
            PerformanceText(
                text = card.title,
                sizeSp = 16,
                weight = FontWeight.SemiBold,
                color = PERFORMANCE_PRIMARY_TEXT_COLOR,
            )
            PerformanceText(
                text = card.subtitle,
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
                text = card.status,
                sizeSp = 12,
                weight = FontWeight.Medium,
                color = card.accentColor,
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
