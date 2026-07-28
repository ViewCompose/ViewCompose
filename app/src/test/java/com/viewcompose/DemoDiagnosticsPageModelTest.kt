package com.viewcompose

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DemoDiagnosticsPageModelTest {
    @Test
    fun `every diagnostics tab keeps the overview and switcher at stable leading positions`() {
        (0..3).forEach { selectedPage ->
            assertEquals(
                listOf("page", "page_filter"),
                diagnosticsPageItems(selectedPage).take(2),
            )
        }
    }

    @Test
    fun `theme diagnostics exposes granular lazy items instead of one eager subtree`() {
        val items = diagnosticsPageItems(selectedPage = 1)

        assertFalse("theme" in items)
        assertTrue(items.containsAll(DIAGNOSTICS_THEME_SECTION_KEYS))
        assertEquals(items.size, items.distinct().size)
        assertEquals("theme_verify", items.last())
    }
}
