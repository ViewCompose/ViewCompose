package com.viewcompose.studio.preview

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PreviewAutomaticRefreshGateTest {
    @Test
    fun `coalesces saves to the latest request while rendering`() {
        val gate = PreviewAutomaticRefreshGate<String>()
        gate.markActive(1)

        assertTrue(gate.deferIfActive("first-save"))
        assertTrue(gate.deferIfActive("latest-save"))

        assertEquals("latest-save", gate.complete(1))
        assertFalse(gate.deferIfActive("next-run"))
    }

    @Test
    fun `superseding user render discards automatic refresh from older generation`() {
        val gate = PreviewAutomaticRefreshGate<String>()
        gate.markActive(1)
        gate.deferIfActive("stale-save")

        gate.supersede(2)

        assertNull(gate.complete(1))
        assertNull(gate.complete(2))
    }
}
