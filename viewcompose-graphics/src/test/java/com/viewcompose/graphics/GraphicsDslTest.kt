package com.viewcompose.graphics

/*
 * 测试职责：覆盖 graphics DSL 中的 Graphics Dsl 行为，防止关键契约在后续重构中回退。
 * Test responsibility: covers Graphics Dsl behavior in graphics DSL and guards the contract against regressions.
 */

import com.viewcompose.graphics.core.DrawCommand
import com.viewcompose.ui.modifier.DrawBehindModifierElement
import com.viewcompose.ui.modifier.DrawWithCacheModifierElement
import com.viewcompose.ui.modifier.DrawWithContentModifierElement
import com.viewcompose.ui.modifier.Modifier
import com.viewcompose.ui.node.NodeType
import com.viewcompose.ui.node.spec.CanvasNodeProps
import com.viewcompose.ui.foundation.buildVNodeTree
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GraphicsDslTest {
    @Test
    fun `Canvas emits canvas node with draw spec`() {
        val nodes = buildVNodeTree {
            Canvas(
                modifier = Modifier,
            ) { _ ->
                drawRect(
                    rect = com.viewcompose.graphics.core.Rect(0f, 0f, 12f, 12f),
                )
            }
        }

        assertEquals(1, nodes.size)
        val node = nodes.first()
        assertEquals(NodeType.Canvas, node.type)
        assertTrue(node.spec is CanvasNodeProps)
    }

    @Test
    fun `graphics module draw wrappers append contract modifier elements`() {
        val modifier = Modifier
            .drawBehind { _ -> }
            .drawWithContent { _ -> drawContent() }
            .drawWithCache { _ -> listOf(DrawCommand.Save, DrawCommand.Restore) }

        assertEquals(3, modifier.elements.size)
        assertTrue(modifier.elements[0] is DrawBehindModifierElement)
        assertTrue(modifier.elements[1] is DrawWithContentModifierElement)
        assertTrue(modifier.elements[2] is DrawWithCacheModifierElement)
    }
}
