package com.viewcompose.performance

import android.content.Context

/**
 * 列表性能场景的固定行数，保持每次 benchmark 的工作量一致。
 * Fixed row count for the list scenario so each benchmark run has the same workload.
 */
internal const val PERFORMANCE_LIST_ITEM_COUNT: Int = 1_000

/**
 * 每次 revision 重排列表时使用的步长。
 * Rotation step used when reordering rows for each revision.
 */
internal const val PERFORMANCE_LIST_ROTATION: Int = 37

/**
 * 性能对比页使用固定色值，避免主题变化影响两个渲染引擎的对比。
 * Performance screens use fixed colors so theme changes do not skew engine comparisons.
 */
internal const val PERFORMANCE_BACKGROUND_COLOR: Int = 0xFFF7F8FA.toInt()
internal const val PERFORMANCE_SURFACE_COLOR: Int = 0xFFFFFFFF.toInt()
internal const val PERFORMANCE_PRIMARY_COLOR: Int = 0xFF315EFB.toInt()
internal const val PERFORMANCE_PRIMARY_TEXT_COLOR: Int = 0xFF172033.toInt()
internal const val PERFORMANCE_SECONDARY_TEXT_COLOR: Int = 0xFF647087.toInt()
internal const val PERFORMANCE_BADGE_COLOR: Int = 0xFFE7ECFF.toInt()

/**
 * 列表 benchmark 的稳定行模型。
 * Stable row model for list benchmarks.
 */
internal data class PerformanceListRow(
    val id: Int,
    val title: String,
    val subtitle: String,
    val badge: String,
    val accentColor: Int,
)

internal class PerformanceFixtures(context: Context) {
    val copy = PerformanceCopy(context)

    private val basePerformanceListRows: List<PerformanceListRow> by lazy(LazyThreadSafetyMode.NONE) {
        List(PERFORMANCE_LIST_ITEM_COUNT) { index ->
            PerformanceListRow(
                id = index,
                title = copy.listItem(index),
                subtitle = copy.stableListSubtitle(index % 12),
                badge = "${index % 100}",
                accentColor = performanceAccentColor(index),
            )
        }
    }

    private val basePerformanceDashboardCards: List<PerformanceDashboardCard> by
        lazy(LazyThreadSafetyMode.NONE) {
            createBasePerformanceDashboardCards(copy)
        }

    private var cachedListRevision: Int = 0
    private var cachedRevisedListRows: List<PerformanceListRow>? = null

    fun listRows(revision: Int): List<PerformanceListRow> {
        if (revision == 0) return basePerformanceListRows
        if (revision == cachedListRevision) {
            cachedRevisedListRows?.let { return it }
        }
        val rotation = (revision * PERFORMANCE_LIST_ROTATION).mod(PERFORMANCE_LIST_ITEM_COUNT)
        val reordered = basePerformanceListRows
            .drop(rotation) + basePerformanceListRows.take(rotation)
        return reordered.map { row ->
            if (row.id % 16 == 0) {
                row.copy(
                    subtitle = copy.updatedListSubtitle(revision, row.id % 12),
                    badge = copy.revisionBadge(revision),
                )
            } else {
                row
            }
        }.also { revisedRows ->
            // The benchmark mutates between immutable snapshots. Reuse the last prepared snapshot
            // so repeated submissions measure reconciliation instead of rebuilding fixture data.
            cachedListRevision = revision
            cachedRevisedListRows = revisedRows
        }
    }

    fun dashboardCards(revision: Int): List<PerformanceDashboardCard> =
        performanceDashboardCards(
            base = basePerformanceDashboardCards,
            copy = copy,
            revision = revision,
        )
}

/**
 * 根据 revision 返回确定性的列表数据。
 * Returns deterministic list data for the given revision.
 *
 * revision 0 返回同一份基础列表；后续 revision 会重排并更新固定子集，用于测量 key 保留和 patch 更新。
 * Revision 0 returns the shared base list; later revisions reorder rows and update a fixed subset
 * to measure key retention and patch updates.
 */
private fun performanceAccentColor(index: Int): Int {
    return when (index % 4) {
        0 -> 0xFF315EFB.toInt()
        1 -> 0xFF0B8F6A.toInt()
        2 -> 0xFFF08A24.toInt()
        else -> 0xFF8B5CF6.toInt()
    }
}
