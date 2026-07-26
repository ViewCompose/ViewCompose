package com.viewcompose.performance

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertSame
import org.junit.Test

class PerformanceComparisonModelsTest {
    @Test
    fun `base list has stable unique keys`() {
        val rows = performanceListRows(revision = 0)

        assertEquals(PERFORMANCE_LIST_ITEM_COUNT, rows.size)
        assertEquals(PERFORMANCE_LIST_ITEM_COUNT, rows.map(PerformanceListRow::id).toSet().size)
        assertSame(rows, performanceListRows(revision = 0))
    }

    @Test
    fun `revision reorders and updates a deterministic subset`() {
        val base = performanceListRows(revision = 0)
        val revised = performanceListRows(revision = 1)

        assertEquals(PERFORMANCE_LIST_ROTATION, revised.first().id)
        assertEquals(base.map(PerformanceListRow::id).toSet(), revised.map(PerformanceListRow::id).toSet())
        assertNotEquals(
            base.first { it.id == 0 }.subtitle,
            revised.first { it.id == 0 }.subtitle,
        )
        assertSame(
            base.first { it.id == 1 },
            revised.first { it.id == 1 },
        )
    }
}
