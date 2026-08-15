package com.viewcompose

/*
 * 测试职责：覆盖 app demo 中的 Demo Diagnostics Page Model 行为，防止关键契约在后续重构中回退。
 * Test responsibility: covers Demo Diagnostics Page Model behavior in app demo and guards the contract against regressions.
 */

import org.junit.Assert.assertEquals
import org.junit.Test

class DemoDiagnosticsPageModelTest {
    @Test
    fun `runtime diagnostics is a direct fixture without chapter chrome`() {
        assertEquals(listOf("runtime"), diagnosticsPageItems(selectedPage = 0))
    }

    @Test
    fun `theme diagnostics splits its fixture into independently virtualized sections`() {
        val items = diagnosticsPageItems(selectedPage = 1)

        assertEquals(
            DIAGNOSTICS_THEME_PAGE_ITEMS,
            items,
        )
    }

    @Test
    fun `renderer diagnostics splits expensive inspector groups into independent items`() {
        assertEquals(
            listOf(
                "renderer_actions",
                "renderer_probe",
                "renderer_snapshots",
                "renderer_tree",
                "renderer_composition",
                "renderer_layout",
            ),
            diagnosticsPageItems(selectedPage = 2),
        )
    }

    @Test
    fun `direct diagnostics scenarios omit chapter chrome and verification prose`() {
        assertEquals(
            listOf("runtime"),
            diagnosticsPageItems(selectedPage = 0),
        )
        assertEquals(
            DIAGNOSTICS_THEME_PAGE_ITEMS,
            diagnosticsPageItems(selectedPage = 1),
        )
        assertEquals(
            DIAGNOSTICS_RENDERER_PAGE_ITEMS,
            diagnosticsPageItems(selectedPage = 2),
        )
    }
}
