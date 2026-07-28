package com.viewcompose.renderer.view.tree

/*
 * 测试职责：覆盖 renderer view/tree 中的 Nested Scroll Node Wrapper 行为，防止渲染和 patch 契约在后续重构中回退。
 * Test responsibility: covers Nested Scroll Node Wrapper behavior in renderer view/tree and guards render and patch contracts against regressions.
 */

import com.viewcompose.ui.gesture.NestedScrollConnection
import com.viewcompose.ui.modifier.BackgroundColorModifierElement
import com.viewcompose.ui.modifier.Modifier
import com.viewcompose.ui.modifier.NestedScrollModifierElement
import com.viewcompose.ui.modifier.WidthModifierElement
import com.viewcompose.ui.modifier.backgroundColor
import com.viewcompose.ui.modifier.width
import com.viewcompose.ui.node.NodeType
import com.viewcompose.ui.node.VNode
import com.viewcompose.ui.node.spec.EmptyNodeSpec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class NestedScrollNodeWrapperTest {
    @Test
    fun `wraps every nested connection and keeps layout modifiers on outer host`() {
        val outerConnection = object : NestedScrollConnection {}
        val innerConnection = object : NestedScrollConnection {}
        val node = VNode(
            type = NodeType.Box,
            key = "panel",
            spec = EmptyNodeSpec,
            modifier = Modifier
                .width(120)
                .then(NestedScrollModifierElement(outerConnection, null))
                .backgroundColor(0xFF112233.toInt())
                .then(NestedScrollModifierElement(innerConnection, null)),
        )

        val outer = NestedScrollNodeWrapper.wrapTree(listOf(node)).single()
        val inner = outer.children.single()
        val child = inner.children.single()

        assertEquals(NodeType.NestedScrollHost, outer.type)
        assertTrue(outer.modifier.elements.first() is WidthModifierElement)
        assertSame(
            outerConnection,
            outer.modifier.elements
                .filterIsInstance<NestedScrollModifierElement>()
                .single()
                .connection,
        )
        assertEquals(NodeType.NestedScrollHost, inner.type)
        assertSame(
            innerConnection,
            inner.modifier.elements
                .filterIsInstance<NestedScrollModifierElement>()
                .single()
                .connection,
        )
        assertEquals(NodeType.Box, child.type)
        assertTrue(child.modifier.elements.single() is BackgroundColorModifierElement)
    }

    @Test
    fun `returns original tree when no nested scroll modifier exists`() {
        val nodes = listOf(
            VNode(
                type = NodeType.Box,
                spec = EmptyNodeSpec,
            ),
        )

        assertSame(nodes, NestedScrollNodeWrapper.wrapTree(nodes))
    }
}
