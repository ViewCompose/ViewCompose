package com.viewcompose.performance

internal const val PERFORMANCE_LIST_ITEM_COUNT: Int = 1_000
internal const val PERFORMANCE_LIST_ROTATION: Int = 37

internal const val PERFORMANCE_BACKGROUND_COLOR: Int = 0xFFF7F8FA.toInt()
internal const val PERFORMANCE_SURFACE_COLOR: Int = 0xFFFFFFFF.toInt()
internal const val PERFORMANCE_PRIMARY_COLOR: Int = 0xFF315EFB.toInt()
internal const val PERFORMANCE_PRIMARY_TEXT_COLOR: Int = 0xFF172033.toInt()
internal const val PERFORMANCE_SECONDARY_TEXT_COLOR: Int = 0xFF647087.toInt()
internal const val PERFORMANCE_BADGE_COLOR: Int = 0xFFE7ECFF.toInt()

internal data class PerformanceListRow(
    val id: Int,
    val title: String,
    val subtitle: String,
    val badge: String,
    val accentColor: Int,
)

private val basePerformanceListRows: List<PerformanceListRow> =
    List(PERFORMANCE_LIST_ITEM_COUNT) { index ->
        PerformanceListRow(
            id = index,
            title = "List item $index",
            subtitle = "Stable keyed row · group ${index % 12}",
            badge = "${index % 100}",
            accentColor = performanceAccentColor(index),
        )
    }

internal fun performanceListRows(revision: Int): List<PerformanceListRow> {
    if (revision == 0) return basePerformanceListRows
    val rotation = (revision * PERFORMANCE_LIST_ROTATION).mod(PERFORMANCE_LIST_ITEM_COUNT)
    val reordered = basePerformanceListRows
        .drop(rotation) + basePerformanceListRows.take(rotation)
    return reordered.map { row ->
        if (row.id % 16 == 0) {
            row.copy(
                subtitle = "Updated revision $revision · group ${row.id % 12}",
                badge = "R$revision",
            )
        } else {
            row
        }
    }
}

private fun performanceAccentColor(index: Int): Int {
    return when (index % 4) {
        0 -> 0xFF315EFB.toInt()
        1 -> 0xFF0B8F6A.toInt()
        2 -> 0xFFF08A24.toInt()
        else -> 0xFF8B5CF6.toInt()
    }
}
