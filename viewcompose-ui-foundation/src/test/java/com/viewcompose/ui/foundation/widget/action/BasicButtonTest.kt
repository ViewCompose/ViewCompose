package com.viewcompose.ui.foundation

import com.viewcompose.graphics.core.Brush
import com.viewcompose.ui.modifier.ClickableModifierElement
import com.viewcompose.ui.modifier.SemanticsModifierElement
import com.viewcompose.ui.modifier.SemanticsRole
import com.viewcompose.ui.node.ImageSource
import com.viewcompose.ui.node.NodeType
import com.viewcompose.ui.node.UiStateLayerColors
import com.viewcompose.ui.node.spec.RowNodeProps
import com.viewcompose.ui.node.spec.SurfaceNodeProps
import com.viewcompose.ui.shape.UiShape
import com.viewcompose.ui.unit.dp
import com.viewcompose.ui.unit.sp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BasicButtonTest {
    @Test
    fun `basic button composes action without native button node`() {
        val style = style()
        val root = buildVNodeTree {
            BasicButton(
                text = "Continue",
                onClick = {},
                style = style,
                leadingIcon = ImageSource.Resource(1),
                trailingIcon = ImageSource.Resource(2),
            )
        }.single()

        val spec = root.spec as SurfaceNodeProps
        assertEquals(NodeType.Surface, root.type)
        assertEquals(style.surface.fill, spec.fill)
        assertEquals(48.dp, spec.minimumHeight)
        assertEquals(40.dp, spec.visualHeight)
        assertTrue(root.modifier.elements.any { it is ClickableModifierElement })
        val semantics = root.modifier.elements.filterIsInstance<SemanticsModifierElement>().single()
        assertEquals(SemanticsRole.Button, semantics.configuration.role)

        val row = root.children.single()
        assertTrue(row.spec is RowNodeProps)
        assertEquals(
            listOf(NodeType.Image, NodeType.Text, NodeType.Image),
            row.children.map { it.type },
        )
        assertFalse(root.flatten().any { it.type == NodeType.Button })
    }

    @Test
    fun `disabled basic button keeps structure and removes click and state layers`() {
        val root = buildVNodeTree {
            BasicButton(
                text = "Unavailable",
                onClick = {},
                style = style(),
                enabled = false,
            )
        }.single()

        assertEquals(null, root.stateLayerColorsOrNull())
        assertFalse(root.modifier.elements.any { it is ClickableModifierElement })
        val semantics = root.modifier.elements.filterIsInstance<SemanticsModifierElement>().single()
        assertEquals(false, semantics.configuration.enabled)
        assertEquals(listOf(NodeType.Text), root.children.single().children.map { it.type })
    }

    private fun style(): BasicButtonStyle = BasicButtonStyle(
        surface = BasicSurfaceStyle(
            fill = Brush.SolidColor(0xFF123456.toInt()),
            shape = UiShape.continuous(20.dp),
            borderWidth = 1.dp,
            borderColor = 0xFF89ABCD.toInt(),
            clipContent = true,
        ),
        contentColor = 0xFFFFFFFF.toInt(),
        textStyle = UiTextStyle(fontSizeSp = 14.sp),
        stateLayerColors = UiStateLayerColors(1, 2, 3),
        minimumWidth = 64.dp,
        minimumHeight = 48.dp,
        visualHeight = 40.dp,
        paddingHorizontal = 16.dp,
        paddingVertical = 8.dp,
        iconSize = 18.dp,
        iconSpacing = 8.dp,
    )

    private fun com.viewcompose.ui.node.VNode.flatten(): List<com.viewcompose.ui.node.VNode> =
        listOf(this) + children.flatMap { it.flatten() }
}
