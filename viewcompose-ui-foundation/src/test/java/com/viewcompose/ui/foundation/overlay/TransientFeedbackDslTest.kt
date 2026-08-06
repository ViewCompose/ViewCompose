package com.viewcompose.ui.foundation

/*
 * 测试职责：覆盖 widget-core overlay 中的 Transient Feedback Dsl 行为，防止 DSL、状态或主题契约在后续重构中回退。
 * Test responsibility: covers Transient Feedback Dsl behavior in widget-core overlay and guards DSL, state, or theme contracts against regressions.
 */

import org.junit.Assert.assertEquals
import org.junit.Test

class TransientFeedbackDslTest {
    @Test
    fun `snackbar submits overlay request when visible`() {
        val store = OverlayRequestStore()

        OverlayRequestContext.withStore(store) {
            buildVNodeTree {
                Snackbar(
                    visible = true,
                    message = "Saved",
                    actionLabel = "Undo",
                    requestKey = "save_snackbar",
                )
            }
        }

        assertEquals(
            listOf(
                OverlayRequest(
                    key = "save_snackbar",
                    type = OverlayType.Snackbar,
                    payload = SnackbarOverlaySpec(
                        message = "Saved",
                        actionLabel = "Undo",
                    ),
                ),
            ),
            store.currentRequests(),
        )
    }

    @Test
    fun `toast submits overlay request when visible`() {
        val store = OverlayRequestStore()

        OverlayRequestContext.withStore(store) {
            buildVNodeTree {
                Toast(
                    visible = true,
                    message = "Copied",
                    duration = ToastDuration.Long,
                    requestKey = "copy_toast",
                )
            }
        }

        assertEquals(
            listOf(
                OverlayRequest(
                    key = "copy_toast",
                    type = OverlayType.Toast,
                    payload = ToastOverlaySpec(
                        message = "Copied",
                        duration = ToastDuration.Long,
                    ),
                ),
            ),
            store.currentRequests(),
        )
    }

    @Test
    fun `transient feedback is ignored when not visible`() {
        val store = OverlayRequestStore()

        OverlayRequestContext.withStore(store) {
            buildVNodeTree {
                Snackbar(
                    visible = false,
                    message = "Saved",
                )
                Toast(
                    visible = false,
                    message = "Copied",
                )
            }
        }

        assertEquals(emptyList<OverlayRequest>(), store.currentRequests())
    }
}
