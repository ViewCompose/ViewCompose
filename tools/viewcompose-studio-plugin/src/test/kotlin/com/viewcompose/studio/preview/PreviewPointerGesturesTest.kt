package com.viewcompose.studio.preview

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PreviewPointerGesturesTest {
    @Test
    fun `trackpad gesture locks to dominant axis until the gesture pauses`() {
        val lock = PreviewTrackpadAxisLock()

        assertNull(lock.resolve(horizontalRotation = 0.08, verticalRotation = 0.0, eventMillis = 0))
        assertNull(lock.resolve(horizontalRotation = 0.0, verticalRotation = 0.05, eventMillis = 10))
        assertEquals(
            PreviewScrollAxis.Horizontal,
            lock.resolve(horizontalRotation = 0.20, verticalRotation = 0.0, eventMillis = 20),
        )
        assertEquals(
            PreviewScrollAxis.Horizontal,
            lock.resolve(horizontalRotation = 0.0, verticalRotation = 1.0, eventMillis = 30),
        )
        assertEquals(
            PreviewScrollAxis.Vertical,
            lock.resolve(horizontalRotation = 0.0, verticalRotation = 0.30, eventMillis = 250),
        )
    }

    @Test
    fun `trackpad gesture waits through diagonal noise before locking one axis`() {
        val lock = PreviewTrackpadAxisLock()

        assertNull(lock.resolve(horizontalRotation = 0.18, verticalRotation = 0.17, eventMillis = 0))
        assertNull(lock.resolve(horizontalRotation = 0.10, verticalRotation = 0.12, eventMillis = 10))
        assertEquals(
            PreviewScrollAxis.Vertical,
            lock.resolve(horizontalRotation = 0.0, verticalRotation = 0.30, eventMillis = 20),
        )
        assertEquals(
            PreviewScrollAxis.Vertical,
            lock.resolve(horizontalRotation = 0.80, verticalRotation = 0.0, eventMillis = 30),
        )
    }

    @Test
    fun `double press survives an AWT click-count reset but rejects drifts`() {
        val tracker = PreviewDoublePressTracker()

        assertFalse(tracker.register(awtClickCount = 1, eventMillis = 100, x = 20, y = 20))
        assertTrue(tracker.register(awtClickCount = 1, eventMillis = 300, x = 23, y = 18))
        assertFalse(tracker.register(awtClickCount = 1, eventMillis = 1_000, x = 20, y = 20))
        assertFalse(tracker.register(awtClickCount = 1, eventMillis = 1_200, x = 50, y = 50))
        assertTrue(tracker.register(awtClickCount = 2, eventMillis = 2_000, x = 0, y = 0))
    }
}
