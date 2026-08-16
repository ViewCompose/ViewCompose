package com.viewcompose.performance

import android.content.Context
import com.viewcompose.ui.foundation.LazyItemsSnapshot
import com.viewcompose.ui.foundation.toLazyItemsSnapshot
import java.util.Collections

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

private class PreparedPerformanceListRevision(
    val rows: List<PerformanceListRow>,
    val snapshot: LazyItemsSnapshot<PerformanceListRow>,
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
        }.asImmutableFixtureList()
    }

    private val basePerformanceListRevision: PreparedPerformanceListRevision by
        lazy(LazyThreadSafetyMode.NONE) {
            preparePerformanceListRevision(basePerformanceListRows)
        }

    private val firstPerformanceListMutation: PreparedPerformanceListRevision by
        lazy(LazyThreadSafetyMode.NONE) {
            preparePerformanceListRevision(createPerformanceListRows(revision = 1))
        }

    private val basePerformanceDashboardCards: List<PerformanceDashboardCard> by
        lazy(LazyThreadSafetyMode.NONE) {
            createBasePerformanceDashboardCards(copy)
        }

    private var cachedAdditionalListRevision: Int = 0
    private var cachedAdditionalListRows: PreparedPerformanceListRevision? = null

    /**
     * Prepares both snapshots exercised by the alternating list benchmark before its Ready marker.
     * Every rendering engine pays the same fixture preparation cost outside the measured mutation.
     */
    fun prepareListScenario() {
        check(basePerformanceListRevision.rows.size == PERFORMANCE_LIST_ITEM_COUNT)
        check(firstPerformanceListMutation.rows.size == PERFORMANCE_LIST_ITEM_COUNT)
    }

    fun listRows(revision: Int): List<PerformanceListRow> =
        listRevision(revision).rows

    fun listSnapshot(revision: Int): LazyItemsSnapshot<PerformanceListRow> =
        listRevision(revision).snapshot

    private fun listRevision(revision: Int): PreparedPerformanceListRevision {
        when (revision) {
            0 -> return basePerformanceListRevision
            1 -> return firstPerformanceListMutation
        }
        if (revision == cachedAdditionalListRevision) {
            cachedAdditionalListRows?.let { return it }
        }
        return preparePerformanceListRevision(createPerformanceListRows(revision)).also {
            cachedAdditionalListRevision = revision
            cachedAdditionalListRows = it
        }
    }

    private fun createPerformanceListRows(revision: Int): List<PerformanceListRow> {
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
        }.asImmutableFixtureList()
    }

    fun dashboardCards(revision: Int): List<PerformanceDashboardCard> =
        performanceDashboardCards(
            base = basePerformanceDashboardCards,
            copy = copy,
            revision = revision,
        )
}

private fun preparePerformanceListRevision(
    rows: List<PerformanceListRow>,
): PreparedPerformanceListRevision = PreparedPerformanceListRevision(
    rows = rows,
    snapshot = rows.toLazyItemsSnapshot(),
)

private fun <T> List<T>.asImmutableFixtureList(): List<T> =
    Collections.unmodifiableList(this)

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
