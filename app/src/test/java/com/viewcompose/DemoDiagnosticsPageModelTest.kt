package com.viewcompose

/*
 * 测试职责：覆盖 app demo 中的 Demo Diagnostics Page Model 行为，防止关键契约在后续重构中回退。
 * Test responsibility: covers Demo Diagnostics Page Model behavior in app demo and guards the contract against regressions.
 */

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
