package com.viewcompose.widget.core

import com.viewcompose.ui.node.VNode
import org.junit.Assert.assertEquals
import org.junit.Test

class PopupDslTest {
    @Test
    fun `popup submits overlay request with anchor and content when visible`() {
        val store = OverlayRequestStore()

        val tree = OverlayRequestContext.withStore(store) {
            buildVNodeTree {
                Popup(
                    visible = true,
                    anchorId = "feedback_popup_anchor",
                    requestKey = "feedback_popup",
                    alignment = PopupAlignment.AboveEnd,
                    offsetY = 8.dp,
                ) {
                    Text(text = "Popup content")
                }
            }
        }

        assertEquals(emptyList<VNode>(), tree)
        assertEquals(
            1,
            store.currentRequests().size,
        )
        val request = store.currentRequests().single()
        assertEquals("feedback_popup", request.key)
        assertEquals(OverlayType.Popup, request.type)
        assertEquals(
            PopupOverlaySpec(
                anchorId = "feedback_popup_anchor",
                alignment = PopupAlignment.AboveEnd,
                windowMargin = 8.dp,
                offsetY = 8.dp,
            ),
            request.payload,
        )
        val content = request.contentToken as PopupOverlayContent
        assertEquals(
            buildVNodeTree {
                Text(text = "Popup content")
            },
            content.surface.buildNodes(),
        )
    }

    @Test
    fun `popup is ignored when not visible`() {
        val store = OverlayRequestStore()

        val tree = OverlayRequestContext.withStore(store) {
            buildVNodeTree {
                Popup(
                    visible = false,
                    anchorId = "feedback_popup_anchor",
                ) {
                    Text(text = "Hidden popup")
                }
            }
        }

        assertEquals(emptyList<VNode>(), tree)
        assertEquals(emptyList<OverlayRequest>(), store.currentRequests())
    }
}
