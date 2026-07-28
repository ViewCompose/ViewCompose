package com.viewcompose.renderer.view.tree

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.After
import org.junit.Test

class LayoutPassTrackerTest {
    @After
    fun tearDown() {
        LayoutPassTracker.stop()
        LayoutPassTracker.reset()
    }

    @Test
    fun `records and resets layout pass counts`() {
        LayoutPassTracker.reset()

        LayoutPassTracker.recordMeasure("DeclarativeLinearLayout", durationNs = 2_000_000)
        LayoutPassTracker.recordMeasure("DeclarativeLinearLayout", durationNs = 3_000_000)
        LayoutPassTracker.recordLayout("DeclarativeLinearLayout", durationNs = 1_500_000)
        LayoutPassTracker.recordLayout("DeclarativeBoxLayout", durationNs = 4_000_000)

        val snapshot = LayoutPassTracker.snapshot()

        assertEquals(2, snapshot.totalMeasureCount)
        assertEquals(2, snapshot.totalLayoutCount)
        assertEquals(5_000_000, snapshot.totalMeasureNs)
        assertEquals(5_500_000, snapshot.totalLayoutNs)
        assertEquals(
            listOf(
                LayoutPassEntry(
                    viewName = "DeclarativeLinearLayout",
                    measureCount = 2,
                    layoutCount = 1,
                    totalMeasureNs = 5_000_000,
                    totalLayoutNs = 1_500_000,
                ),
                LayoutPassEntry(
                    viewName = "DeclarativeBoxLayout",
                    measureCount = 0,
                    layoutCount = 1,
                    totalMeasureNs = 0,
                    totalLayoutNs = 4_000_000,
                ),
            ),
            snapshot.entries,
        )

        LayoutPassTracker.reset()
        assertEquals(LayoutPassSnapshot(), LayoutPassTracker.snapshot())
    }

    @Test
    fun `custom view timing is disabled by default and can be scoped`() {
        LayoutPassTracker.stop()
        LayoutPassTracker.reset()

        val disabledStart = LayoutPassTracker.beginTiming()
        LayoutPassTracker.recordMeasureSince(LayoutPassTrackerTest::class.java, disabledStart)

        assertEquals(LayoutPassSnapshot(), LayoutPassTracker.snapshot())
        assertFalse(LayoutPassTracker.isEnabled)

        LayoutPassTracker.start()
        val enabledStart = LayoutPassTracker.beginTiming()
        LayoutPassTracker.recordMeasureSince(LayoutPassTrackerTest::class.java, enabledStart)
        LayoutPassTracker.stop()

        assertTrue(enabledStart > 0L)
        assertEquals(1, LayoutPassTracker.snapshot().totalMeasureCount)
        assertFalse(LayoutPassTracker.isEnabled)
    }
}
