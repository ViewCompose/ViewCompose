package com.viewcompose.ui.foundation

/*
 * 测试职责：覆盖 widget-core overlay 中的 Dialog Dsl 行为，防止 DSL、状态或主题契约在后续重构中回退。
 * Test responsibility: covers Dialog Dsl behavior in widget-core overlay and guards DSL, state, or theme contracts against regressions.
 */

import com.viewcompose.ui.node.VNode
import org.junit.Assert.assertEquals
import org.junit.Test

class DialogDslTest {
    @Test
    fun `dialog submits overlay request with content when visible`() {
        val store = OverlayRequestStore()

        val tree = OverlayRequestContext.withStore(store) {
            buildVNodeTree {
                Dialog(
                    visible = true,
                    requestKey = "settings_dialog",
                    dismissOnBackPress = false,
                    dismissOnClickOutside = false,
                    position = DialogPosition.Bottom,
                    scrimOpacity = 0.48f,
                ) {
                    Text(text = "Dialog body")
                }
            }
        }

        assertEquals(emptyList<VNode>(), tree)
        assertEquals(
            1,
            store.currentRequests().size,
        )
        val request = store.currentRequests().single()
        assertEquals("settings_dialog", request.key)
        assertEquals(OverlayType.Dialog, request.type)
        assertEquals(
            DialogOverlaySpec(
                dismissOnBackPress = false,
                dismissOnClickOutside = false,
                position = DialogPosition.Bottom,
                scrimOpacity = 0.48f,
            ),
            request.payload,
        )
        val content = request.contentToken as DialogOverlayContent
        assertEquals(
            buildVNodeTree {
                Text(text = "Dialog body")
            },
            content.surface.buildNodes(),
        )
    }

    @Test
    fun `dialog is ignored when not visible`() {
        val store = OverlayRequestStore()

        val tree = OverlayRequestContext.withStore(store) {
            buildVNodeTree {
                Dialog(visible = false) {
                    Text(text = "Hidden dialog")
                }
            }
        }

        assertEquals(emptyList<VNode>(), tree)
        assertEquals(emptyList<OverlayRequest>(), store.currentRequests())
    }
}
