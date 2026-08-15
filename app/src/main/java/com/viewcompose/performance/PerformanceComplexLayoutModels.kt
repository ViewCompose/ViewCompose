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

internal fun createBasePerformanceDashboardCards(
    copy: PerformanceCopy,
): List<PerformanceDashboardCard> =
    List(PERFORMANCE_DASHBOARD_CARD_COUNT) { index ->
        PerformanceDashboardCard(
            id = index,
            title = copy.dashboardSection(index + 1),
            subtitle = copy.nestedLayoutGroup(index % 6),
            status = if (index % 3 == 0) copy.active else copy.stable,
            metrics = performanceDashboardMetrics(
                index = index,
                revision = 0,
                copy = copy,
            ),
            tags = listOf(
                copy.region(index % 4 + 1),
                copy.tier(index % 3 + 1),
                copy.node(index + 10),
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
internal fun performanceDashboardCards(
    base: List<PerformanceDashboardCard>,
    copy: PerformanceCopy,
    revision: Int,
): List<PerformanceDashboardCard> {
    if (revision == 0) return base
    return base.map { card ->
        card.copy(
            subtitle = copy.updatedLayoutRevision(revision),
            status = if ((card.id + revision) % 3 == 0) copy.active else copy.updated,
            metrics = performanceDashboardMetrics(
                index = card.id,
                revision = revision,
                copy = copy,
            ),
            detailsVisible = (card.id + revision) % 4 == 0,
        )
    }
}

private fun performanceDashboardMetrics(
    index: Int,
    revision: Int,
    copy: PerformanceCopy,
): List<PerformanceDashboardMetric> {
    return listOf(
        PerformanceDashboardMetric(
            label = copy.requests,
            value = "${1_200 + index * 17 + revision * 31}",
        ),
        PerformanceDashboardMetric(
            label = copy.success,
            value = "${96 + (index + revision) % 4}%",
        ),
        PerformanceDashboardMetric(
            label = copy.latency,
            value = copy.latencyValue(18 + index % 9 + revision * 2),
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
