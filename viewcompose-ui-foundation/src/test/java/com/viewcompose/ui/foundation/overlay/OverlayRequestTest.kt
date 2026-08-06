package com.viewcompose.ui.foundation

/*
 * 测试职责：覆盖 widget-core overlay 中的 Overlay Request 行为，防止 DSL、状态或主题契约在后续重构中回退。
 * Test responsibility: covers Overlay Request behavior in widget-core overlay and guards DSL, state, or theme contracts against regressions.
 */

import org.junit.Assert.assertEquals
import org.junit.Test

class OverlayRequestTest {
    @Test
    fun `records all overlay requests inside render context`() {
        val store = OverlayRequestStore()
        val first = OverlayRequest(
            key = "toast",
            type = OverlayType.Toast,
            payload = "Saved",
        )
        val second = OverlayRequest(
            key = "snackbar",
            type = OverlayType.Snackbar,
            payload = "Undo",
        )

        OverlayRequestContext.withStore(store) {
            submitOverlayRequest(first)
            submitOverlayRequest(second)
        }

        assertEquals(listOf(first, second), store.currentRequests())
    }

    @Test
    fun `clears previous requests on next render`() {
        val store = OverlayRequestStore()

        OverlayRequestContext.withStore(store) {
            submitOverlayRequest(
                OverlayRequest(
                    key = "first",
                    type = OverlayType.Toast,
                ),
            )
        }
        OverlayRequestContext.withStore(store) {
            submitOverlayRequest(
                OverlayRequest(
                    key = "second",
                    type = OverlayType.Snackbar,
                ),
            )
        }

        assertEquals(
            listOf(
                OverlayRequest(
                    key = "second",
                    type = OverlayType.Snackbar,
                ),
            ),
            store.currentRequests(),
        )
    }

    @Test
    fun `overlay request outside render context is ignored`() {
        val store = OverlayRequestStore()

        submitOverlayRequest(
            OverlayRequest(
                key = "outside",
                type = OverlayType.Dialog,
            ),
        )

        assertEquals(emptyList<OverlayRequest>(), store.currentRequests())
    }
}
