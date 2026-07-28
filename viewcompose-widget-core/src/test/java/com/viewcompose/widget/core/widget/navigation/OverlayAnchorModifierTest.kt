package com.viewcompose.widget.core

/*
 * 测试职责：覆盖 widget-core widget/navigation 中的 Overlay Anchor Modifier 行为，防止 DSL、状态或主题契约在后续重构中回退。
 * Test responsibility: covers Overlay Anchor Modifier behavior in widget-core widget/navigation and guards DSL, state, or theme contracts against regressions.
 */

import com.viewcompose.ui.modifier.Modifier
import com.viewcompose.ui.modifier.OverlayAnchorModifierElement
import com.viewcompose.ui.modifier.overlayAnchor
import com.viewcompose.ui.node.NodeType
import org.junit.Assert.assertEquals
import org.junit.Test

class OverlayAnchorModifierTest {
    @Test
    fun `box with overlay anchor emits anchor metadata`() {
        val nodes = buildVNodeTree {
            Box(modifier = Modifier.overlayAnchor("feedback_popup_anchor")) {
                Text(text = "Anchor content")
            }
        }

        val node = nodes.single()
        val elements = node.modifier.readModifierElements()
        val anchor = elements.last { it is OverlayAnchorModifierElement } as OverlayAnchorModifierElement
        assertEquals(NodeType.Box, node.type)
        assertEquals("feedback_popup_anchor", anchor.anchorId)
        assertEquals(1, node.children.size)
    }

    @Test
    fun `last overlay anchor modifier wins`() {
        val nodes = buildVNodeTree {
            Box(
                modifier = Modifier
                    .overlayAnchor("stale_anchor")
                    .overlayAnchor("expected_anchor"),
            ) {
                Text(text = "Anchor content")
            }
        }

        val node = nodes.single()
        val elements = node.modifier.readModifierElements()
        val anchor = elements.last { it is OverlayAnchorModifierElement } as OverlayAnchorModifierElement

        assertEquals("expected_anchor", anchor.anchorId)
    }

    private fun com.viewcompose.ui.modifier.Modifier.readModifierElements(): List<Any?> {
        val field = com.viewcompose.ui.modifier.Modifier::class.java.getDeclaredField("elements")
        field.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        return field.get(this) as List<Any?>
    }
}
