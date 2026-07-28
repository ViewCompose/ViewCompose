package com.viewcompose.performance

/**
 * 复杂布局性能场景中的固定卡片数量。
 * Fixed card count used by the complex-layout performance scenario.
 */
internal const val PERFORMANCE_DASHBOARD_CARD_COUNT: Int = 18

/**
 * 复杂布局卡片中的指标项。
 * Metric item displayed inside a complex-layout card.
 */
internal data class PerformanceDashboardMetric(
    val label: String,
    val value: String,
)

/**
 * 复杂布局 benchmark 的稳定卡片模型。
 * Stable card model for complex-layout benchmarks.
 */
internal data class PerformanceDashboardCard(
    val id: Int,
    val title: String,
    val subtitle: String,
    val status: String,
    val metrics: List<PerformanceDashboardMetric>,
    val tags: List<String>,
    val detailsVisible: Boolean,
    val accentColor: Int,
)

private val basePerformanceDashboardCards: List<PerformanceDashboardCard> =
    List(PERFORMANCE_DASHBOARD_CARD_COUNT) { index ->
        PerformanceDashboardCard(
            id = index,
            title = "Dashboard section ${index + 1}",
            subtitle = "Nested layout group ${index % 6}",
            status = if (index % 3 == 0) "Active" else "Stable",
            metrics = performanceDashboardMetrics(
                index = index,
                revision = 0,
            ),
            tags = listOf(
                "Region ${index % 4 + 1}",
                "Tier ${index % 3 + 1}",
                "Node ${index + 10}",
            ),
            detailsVisible = index % 4 == 0,
            accentColor = performanceDashboardAccentColor(index),
        )
    }

/**
 * 根据 revision 返回确定性的复杂布局卡片数据。
 * Returns deterministic complex-layout cards for the given revision.
 *
 * 结构保持同一批 id，但会更新嵌套指标和明细可见性，用于测量深层布局 patch 成本。
 * The same card ids are retained while nested metrics and detail visibility change, measuring
 * deep-layout patch cost.
 */
internal fun performanceDashboardCards(revision: Int): List<PerformanceDashboardCard> {
    if (revision == 0) return basePerformanceDashboardCards
    return basePerformanceDashboardCards.map { card ->
        card.copy(
            subtitle = "Updated layout revision $revision",
            status = if ((card.id + revision) % 3 == 0) "Active" else "Updated",
            metrics = performanceDashboardMetrics(
                index = card.id,
                revision = revision,
            ),
            detailsVisible = (card.id + revision) % 4 == 0,
        )
    }
}

private fun performanceDashboardMetrics(
    index: Int,
    revision: Int,
): List<PerformanceDashboardMetric> {
    return listOf(
        PerformanceDashboardMetric(
            label = "Requests",
            value = "${1_200 + index * 17 + revision * 31}",
        ),
        PerformanceDashboardMetric(
            label = "Success",
            value = "${96 + (index + revision) % 4}%",
        ),
        PerformanceDashboardMetric(
            label = "Latency",
            value = "${18 + index % 9 + revision * 2} ms",
        ),
    )
}

private fun performanceDashboardAccentColor(index: Int): Int {
    return when (index % 4) {
        0 -> 0xFF315EFB.toInt()
        1 -> 0xFF0B8F6A.toInt()
        2 -> 0xFFF08A24.toInt()
        else -> 0xFF8B5CF6.toInt()
    }
}
