package com.viewcompose.renderer.view.tree

/*
 * 测试职责：覆盖 renderer view/tree 中的 Animated Size Node Wrapper 行为，防止渲染和 patch 契约在后续重构中回退。
 * Test responsibility: covers Animated Size Node Wrapper behavior in renderer view/tree and guards render and patch contracts against regressions.
 */

import com.viewcompose.ui.modifier.AlphaModifierElement
import com.viewcompose.ui.modifier.AnimateContentSizeModifierElement
import com.viewcompose.ui.modifier.ContentSizeEasingModel
import com.viewcompose.ui.modifier.ContentSizeTweenSpecModel
import com.viewcompose.ui.modifier.Modifier
import com.viewcompose.ui.modifier.width
import com.viewcompose.ui.node.NodeType
import com.viewcompose.ui.node.TextAlign
import com.viewcompose.ui.node.TextOverflow
import com.viewcompose.ui.node.VNode
import com.viewcompose.ui.node.spec.AnimatedSizeHostNodeProps
import com.viewcompose.ui.node.spec.TextNodeProps
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class AnimatedSizeNodeWrapperTest {
    @Test
    fun `wraps animateContentSize nodes with animated size host`() {
        val original = textNode(
            modifier = Modifier
                .width(240)
                .then(AlphaModifierElement(alpha = 0.8f))
                .then(
                    AnimateContentSizeModifierElement(
                        animationSpec = ContentSizeTweenSpecModel(
                            durationMillis = 240,
                            delayMillis = 24,
                            easing = ContentSizeEasingModel.FastOutSlowIn,
                        ),
                    ),
                ),
        )

        val wrapped = AnimatedSizeNodeWrapper.wrapTree(listOf(original)).single()
        val wrappedChild = wrapped.children.single()

        assertEquals(NodeType.AnimatedSizeHost, wrapped.type)
        assertTrue(wrapped.spec is AnimatedSizeHostNodeProps)
        assertEquals(NodeType.Text, wrappedChild.type)
        assertTrue(wrapped.modifier.elements.any { it is com.viewcompose.ui.modifier.WidthModifierElement })
        assertTrue(wrappedChild.modifier.elements.any { it is AlphaModifierElement })
        assertFalse(wrappedChild.modifier.elements.any { it is AnimateContentSizeModifierElement })
    }

    @Test
    fun `keeps tree untouched when animateContentSize is absent`() {
        val original = textNode(modifier = Modifier.width(120))
        val tree = listOf(original)

        val wrappedTree = AnimatedSizeNodeWrapper.wrapTree(tree)
        val wrapped = wrappedTree.single()

        assertEquals(NodeType.Text, wrapped.type)
        assertEquals(original.spec, wrapped.spec)
        assertEquals(original.modifier, wrapped.modifier)
        assertSame(tree, wrappedTree)
        assertSame(original, wrapped)
    }

    @Test
    fun `copies only ancestors of an animated descendant`() {
        val animated = textNode(
            modifier = Modifier.then(
                AnimateContentSizeModifierElement(
                    animationSpec = ContentSizeTweenSpecModel(
                        durationMillis = 120,
                        delayMillis = 0,
                        easing = ContentSizeEasingModel.Linear,
                    ),
                ),
            ),
        )
        val stableSibling = textNode(modifier = Modifier.width(80))
        val parent = VNode(
            type = NodeType.Box,
            spec = com.viewcompose.ui.node.spec.BoxNodeProps(
                contentAlignment = com.viewcompose.ui.layout.BoxAlignment.TopStart,
            ),
            children = listOf(animated, stableSibling),
        )

        val wrappedParent = AnimatedSizeNodeWrapper.wrapTree(listOf(parent)).single()

        assertNotSame(parent, wrappedParent)
        assertEquals(NodeType.AnimatedSizeHost, wrappedParent.children.first().type)
        assertSame(stableSibling, wrappedParent.children[1])
    }

    private fun textNode(modifier: Modifier): VNode {
        return VNode(
            type = NodeType.Text,
            spec = TextNodeProps(
                text = "demo",
                maxLines = 1,
                overflow = TextOverflow.Clip,
                textAlign = TextAlign.Start,
                textColor = 0xFF000000.toInt(),
                textSizeSp = 14,
            ),
            modifier = modifier,
        )
    }
}
