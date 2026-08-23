package com.viewcompose.renderer.view.tree

import com.viewcompose.text.TextDocument
import com.viewcompose.ui.environment.UiEnvironmentValues
import com.viewcompose.ui.modifier.AlphaModifierElement
import com.viewcompose.ui.modifier.AnimateBoundsModifierElement
import com.viewcompose.ui.modifier.AnimateContentSizeModifierElement
import com.viewcompose.ui.modifier.ContentSizeEasingModel
import com.viewcompose.ui.modifier.ContentSizeTweenSpecModel
import com.viewcompose.ui.modifier.Modifier
import com.viewcompose.ui.modifier.maxWidth
import com.viewcompose.ui.modifier.width
import com.viewcompose.ui.node.NodeType
import com.viewcompose.ui.node.TextAlign
import com.viewcompose.ui.node.TextOverflow
import com.viewcompose.ui.node.VNode
import com.viewcompose.ui.node.spec.AnimatedBoundsHostNodeProps
import com.viewcompose.ui.node.spec.AnimatedSizeHostNodeProps
import com.viewcompose.ui.node.spec.TextNodeProps
import com.viewcompose.ui.unit.UiDensity
import com.viewcompose.ui.unit.dp
import com.viewcompose.ui.unit.sp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Test

class AnimatedSizeNodeWrapperTest {
    @Test
    fun `wraps animateContentSize nodes with animated size host`() {
        val original = textNode(
            modifier = Modifier
                .width(240.dp)
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

        val wrapped = LayoutAnimationNodeWrapper.wrapTree(listOf(original)).single()
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
        val original = textNode(modifier = Modifier.width(120.dp))
        val tree = listOf(original)

        val wrappedTree = LayoutAnimationNodeWrapper.wrapTree(tree)
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
        val stableSibling = textNode(modifier = Modifier.width(80.dp))
        val parent = VNode(
            type = NodeType.Box,
            spec = com.viewcompose.ui.node.spec.BoxNodeProps(
                contentAlignment = com.viewcompose.ui.layout.BoxAlignment.TopStart,
            ),
            children = listOf(animated, stableSibling),
        )

        val wrappedParent = LayoutAnimationNodeWrapper.wrapTree(listOf(parent)).single()

        assertNotSame(parent, wrappedParent)
        assertEquals(NodeType.AnimatedSizeHost, wrappedParent.children.first().type)
        assertSame(stableSibling, wrappedParent.children[1])
    }

    @Test
    fun `wraps animateBounds outside structural measurement host`() {
        val environment = UiEnvironmentValues.Default.copy(density = UiDensity(density = 3f, fontScale = 1f))
        val original = textNode(
            modifier = Modifier
                .maxWidth(240.dp)
                .then(
                    AnimateBoundsModifierElement(
                        animationSpec = ContentSizeTweenSpecModel(
                            durationMillis = 240,
                            delayMillis = 0,
                            easing = ContentSizeEasingModel.Linear,
                        ),
                    ),
                ),
            environment = environment,
        )

        val constrained = LayoutConstraintNodeWrapper.wrapTree(listOf(original))
        val wrapped = LayoutAnimationNodeWrapper.wrapTree(constrained).single()

        assertEquals(NodeType.AnimatedBoundsHost, wrapped.type)
        assertTrue(wrapped.spec is AnimatedBoundsHostNodeProps)
        assertEquals(environment, wrapped.environment)
        assertEquals(NodeType.LayoutConstraintHost, wrapped.children.single().type)
        assertFalse(wrapped.children.single().modifier.elements.any { it is AnimateBoundsModifierElement })
    }

    @Test
    fun `rejects competing bounds and content-size owners before wrapping`() {
        val timing = ContentSizeTweenSpecModel(
            durationMillis = 240,
            delayMillis = 0,
            easing = ContentSizeEasingModel.Linear,
        )
        val original = textNode(
            modifier = Modifier
                .then(AnimateBoundsModifierElement(timing))
                .then(AnimateContentSizeModifierElement(timing)),
        )

        assertThrows(IllegalArgumentException::class.java) {
            LayoutAnimationNodeWrapper.wrapTree(listOf(original))
        }
    }

    @Test
    fun `last bounds modifier owns the synthetic host`() {
        val first = ContentSizeTweenSpecModel(
            durationMillis = 120,
            delayMillis = 0,
            easing = ContentSizeEasingModel.Linear,
        )
        val last = ContentSizeTweenSpecModel(
            durationMillis = 420,
            delayMillis = 16,
            easing = ContentSizeEasingModel.FastOutSlowIn,
        )
        val original = textNode(
            modifier = Modifier
                .then(AnimateBoundsModifierElement(first))
                .then(AnimateBoundsModifierElement(last)),
        )

        val wrapped = LayoutAnimationNodeWrapper.wrapTree(listOf(original)).single()

        assertEquals(last, (wrapped.spec as AnimatedBoundsHostNodeProps).animationSpec)
        assertFalse(wrapped.children.single().modifier.elements.any { it is AnimateBoundsModifierElement })
    }

    @Test
    fun `nested bounds owners retain one physical host per local parent`() {
        val timing = ContentSizeTweenSpecModel(
            durationMillis = 240,
            delayMillis = 0,
            easing = ContentSizeEasingModel.Linear,
        )
        val child = textNode(Modifier.then(AnimateBoundsModifierElement(timing)))
        val parent = VNode(
            type = NodeType.Box,
            spec = com.viewcompose.ui.node.spec.BoxNodeProps(
                contentAlignment = com.viewcompose.ui.layout.BoxAlignment.TopStart,
            ),
            modifier = Modifier.then(AnimateBoundsModifierElement(timing)),
            children = listOf(child),
        )

        val wrappedParent = LayoutAnimationNodeWrapper.wrapTree(listOf(parent)).single()

        assertEquals(NodeType.AnimatedBoundsHost, wrappedParent.type)
        assertEquals(NodeType.Box, wrappedParent.children.single().type)
        assertEquals(NodeType.AnimatedBoundsHost, wrappedParent.children.single().children.single().type)
    }

    private fun textNode(
        modifier: Modifier,
        environment: UiEnvironmentValues = UiEnvironmentValues.Default,
    ): VNode {
        return VNode(
            type = NodeType.Text,
            spec = TextNodeProps(
                document = TextDocument.plain("demo"),
                maxLines = 1,
                overflow = TextOverflow.Clip,
                textAlign = TextAlign.Start,
                textColor = 0xFF000000.toInt(),
                textSizeSp = 14.sp,
            ),
            modifier = modifier,
            environment = environment,
        )
    }
}
