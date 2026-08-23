package com.viewcompose.renderer.view.tree

import com.viewcompose.ui.environment.UiEnvironmentValues
import com.viewcompose.ui.unit.sp

import com.viewcompose.ui.unit.dp
import com.viewcompose.ui.unit.UiDensity

/*
 * 测试职责：覆盖 renderer view/tree 中的 Nested Scroll Node Wrapper 行为，防止渲染和 patch 契约在后续重构中回退。
 * Test responsibility: covers Nested Scroll Node Wrapper behavior in renderer view/tree and guards render and patch contracts against regressions.
 */

import com.viewcompose.ui.gesture.NestedScrollConnection
import com.viewcompose.ui.modifier.BackgroundColorModifierElement
import com.viewcompose.ui.modifier.AnimateBoundsModifierElement
import com.viewcompose.ui.modifier.ContentSizeEasingModel
import com.viewcompose.ui.modifier.ContentSizeTweenSpecModel
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
        val environment = UiEnvironmentValues.Default.copy(density = UiDensity(density = 3f, fontScale = 1f))
        val node = VNode(
            type = NodeType.Box,
            key = "panel",
            spec = EmptyNodeSpec,
            modifier = Modifier
                .width(120.dp)
                .then(NestedScrollModifierElement(outerConnection, null))
                .backgroundColor(0xFF112233.toInt())
                .then(NestedScrollModifierElement(innerConnection, null)),
            environment = environment,
        )

        val outer = NestedScrollNodeWrapper.wrapTree(listOf(node)).single()
        val inner = outer.children.single()
        val child = inner.children.single()

        assertEquals(NodeType.NestedScrollHost, outer.type)
        assertEquals(environment, outer.environment)
        assertEquals(environment, inner.environment)
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

    @Test
    fun `bounds ownership wraps the complete nested scroll host`() {
        val connection = object : NestedScrollConnection {}
        val timing = ContentSizeTweenSpecModel(
            durationMillis = 240,
            delayMillis = 0,
            easing = ContentSizeEasingModel.Linear,
        )
        val node = VNode(
            type = NodeType.Box,
            key = "panel",
            spec = EmptyNodeSpec,
            modifier = Modifier
                .width(120.dp)
                .then(AnimateBoundsModifierElement(timing))
                .then(NestedScrollModifierElement(connection, null)),
        )

        val structural = NestedScrollNodeWrapper.wrapTree(listOf(node))
        val animated = LayoutAnimationNodeWrapper.wrapTree(structural).single()

        assertEquals(NodeType.AnimatedBoundsHost, animated.type)
        assertEquals(NodeType.NestedScrollHost, animated.children.single().type)
        assertEquals(NodeType.Box, animated.children.single().children.single().type)
    }
}
