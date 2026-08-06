package com.viewcompose.navigation

/*
 * 测试职责：覆盖 Android navigation runtime 中的 Nav Progress Velocity Tracker 行为，防止导航契约在后续重构中回退。
 * Test responsibility: covers Nav Progress Velocity Tracker behavior in Android navigation runtime and guards navigation contracts against regressions.
 */

import org.junit.Assert.assertEquals
import org.junit.Test

class NavProgressVelocityTrackerTest {
    @Test
    fun `tracks progress per second across the configured sample window`() {
        val tracker = NavProgressVelocityTracker(
            sampleWindowMillis = 100L,
            maxAbsoluteVelocity = 10f,
        )

        assertEquals(0f, tracker.add(frameTimeMillis = 1_000L, progress = 0.1f))
        assertEquals(
            2f,
            tracker.add(frameTimeMillis = 1_100L, progress = 0.3f),
            0.0001f,
        )
    }

    @Test
    fun `keeps one boundary sample and caps fast progress`() {
        val tracker = NavProgressVelocityTracker(
            sampleWindowMillis = 100L,
            maxAbsoluteVelocity = 4f,
        )

        tracker.add(frameTimeMillis = 1_000L, progress = 0f)
        tracker.add(frameTimeMillis = 1_050L, progress = 0.1f)
        assertEquals(
            4f,
            tracker.add(frameTimeMillis = 1_150L, progress = 0.9f),
            0f,
        )
    }

    @Test
    fun `reports reverse velocity and replaces duplicate timestamps`() {
        val tracker = NavProgressVelocityTracker(
            sampleWindowMillis = 100L,
            maxAbsoluteVelocity = 10f,
        )

        tracker.add(frameTimeMillis = 1_000L, progress = 0.8f)
        tracker.add(frameTimeMillis = 1_050L, progress = 0.7f)
        assertEquals(
            -4f,
            tracker.add(frameTimeMillis = 1_050L, progress = 0.6f),
            0.0001f,
        )
    }

    @Test
    fun `resets samples when frame time moves backwards`() {
        val tracker = NavProgressVelocityTracker(
            sampleWindowMillis = 100L,
            maxAbsoluteVelocity = 10f,
        )

        tracker.add(frameTimeMillis = 1_000L, progress = 0.1f)
        tracker.add(frameTimeMillis = 1_050L, progress = 0.3f)

        assertEquals(0f, tracker.add(frameTimeMillis = 900L, progress = 0.4f))
    }
}
