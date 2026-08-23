package com.viewcompose.renderer.view.tree

import com.viewcompose.ui.environment.UiEnvironmentValues
import com.viewcompose.ui.modifier.AspectRatioModifierElement
import com.viewcompose.ui.modifier.MaxWidthModifierElement
import com.viewcompose.ui.modifier.Modifier
import com.viewcompose.ui.modifier.Visibility
import com.viewcompose.ui.modifier.VisibilityModifierElement
import com.viewcompose.ui.modifier.aspectRatio
import com.viewcompose.ui.modifier.fillMaxWidth
import com.viewcompose.ui.modifier.maxWidth
import com.viewcompose.ui.modifier.minWidth
import com.viewcompose.ui.modifier.visibility
import com.viewcompose.ui.modifier.width
import com.viewcompose.ui.node.NodeType
import com.viewcompose.ui.node.VNode
import com.viewcompose.ui.node.spec.EmptyNodeSpec
import com.viewcompose.ui.node.spec.LayoutConstraintHostNodeProps
import com.viewcompose.ui.unit.dp
import com.viewcompose.ui.unit.UiDensity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class LayoutConstraintNodeWrapperTest {
    @Test
    fun `wraps all portable measurement constraints once`() {
        val environment = UiEnvironmentValues.Default.copy(density = UiDensity(density = 3f, fontScale = 1f))
        val original = node(
            Modifier
                .fillMaxWidth()
                .maxWidth(320.dp)
                .aspectRatio(2f),
            environment,
        )

        val host = LayoutConstraintNodeWrapper.wrapTree(listOf(original)).single()
        val spec = host.spec as LayoutConstraintHostNodeProps

        assertEquals(NodeType.LayoutConstraintHost, host.type)
        assertEquals(environment, host.environment)
        assertEquals(320.dp, spec.maxWidth)
        assertEquals(2f, spec.aspectRatio)
        assertTrue(spec.fillWidth)
        assertFalse(host.modifier.elements.any { it is MaxWidthModifierElement })
        assertFalse(host.modifier.elements.any { it is AspectRatioModifierElement })
        assertFalse(host.children.single().modifier.elements.any { it is MaxWidthModifierElement })
    }

    @Test
    fun `retains exact dimensions on the host`() {
        val host = LayoutConstraintNodeWrapper.wrapTree(
            listOf(node(Modifier.width(120.dp).maxWidth(160.dp))),
        ).single()

        assertTrue(
            host.modifier.elements.any { element ->
                element is com.viewcompose.ui.modifier.WidthModifierElement
            },
        )
    }

    @Test
    fun `moves visibility to the outer measurement host`() {
        val host = LayoutConstraintNodeWrapper.wrapTree(
            listOf(node(Modifier.maxWidth(160.dp).visibility(Visibility.Gone))),
        ).single()

        assertTrue(host.modifier.elements.any { it is VisibilityModifierElement })
        assertFalse(host.children.single().modifier.elements.any { it is VisibilityModifierElement })
    }

    @Test(expected = IllegalArgumentException::class)
    fun `rejects minimum larger than maximum`() {
        LayoutConstraintNodeWrapper.wrapTree(
            listOf(node(Modifier.minWidth(200.dp).maxWidth(100.dp))),
        )
    }

    @Test
    fun `leaves unconstrained trees referentially stable`() {
        val original = node(Modifier.width(120.dp))
        val tree = listOf(original)

        assertSame(tree, LayoutConstraintNodeWrapper.wrapTree(tree))
    }

    private fun node(
        modifier: Modifier,
        environment: UiEnvironmentValues = UiEnvironmentValues.Default,
    ): VNode = VNode(
        type = NodeType.Spacer,
        spec = EmptyNodeSpec,
        modifier = modifier,
        environment = environment,
    )
}
