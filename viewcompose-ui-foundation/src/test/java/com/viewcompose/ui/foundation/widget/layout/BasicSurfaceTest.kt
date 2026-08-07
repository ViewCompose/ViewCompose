package com.viewcompose.ui.foundation

import com.viewcompose.graphics.core.Brush
import com.viewcompose.graphics.core.ColorStop
import com.viewcompose.graphics.core.Offset
import com.viewcompose.ui.graphics.UiShadow
import com.viewcompose.ui.modifier.ClickableModifierElement
import com.viewcompose.ui.modifier.DropShadowModifierElement
import com.viewcompose.ui.modifier.ElevationModifierElement
import com.viewcompose.ui.modifier.SemanticsModifierElement
import com.viewcompose.ui.modifier.SemanticsRole
import com.viewcompose.ui.node.NodeType
import com.viewcompose.ui.node.UiStateLayerColors
import com.viewcompose.ui.node.spec.SurfaceNodeProps
import com.viewcompose.ui.shape.UiShape
import com.viewcompose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BasicSurfaceTest {
    @Test
    fun `basic surface emits fully resolved neutral surface contract`() {
        val fill = Brush.LinearGradient(
            from = Offset(0f, 0f),
            to = Offset(160f, 0f),
            colorStops = listOf(
                ColorStop(0f, 0xFF102030.toInt()),
                ColorStop(1f, 0xFF304050.toInt()),
            ),
        )
        val shape = UiShape.continuous(18.dp)
        val interaction = UiStateLayerColors(
            pressedColor = 0x33112233,
            focusedColor = 0x44223344,
            hoveredColor = 0x55334455,
        )
        val shadow = UiShadow(
            color = 0x22000000,
            blurRadius = 8.dp,
            offsetY = 2.dp,
        )

        val node = buildVNodeTree {
            BasicSurface(
                style = BasicSurfaceStyle(
                    fill = fill,
                    shape = shape,
                    borderWidth = 2.dp,
                    borderColor = 0xFF8090A0.toInt(),
                    elevation = 3.dp,
                    dropShadows = listOf(shadow),
                    clipContent = true,
                ),
                contentColor = 0xFFF0F1F2.toInt(),
                onClick = {},
                stateLayerColors = interaction,
                minimumWidth = 64.dp,
                minimumHeight = 48.dp,
                visualHeight = 40.dp,
                role = SemanticsRole.Button,
            ) {
                Text("Launch")
            }
        }.single()

        val spec = node.spec as SurfaceNodeProps
        assertEquals(NodeType.Surface, node.type)
        assertEquals(fill, spec.fill)
        assertEquals(shape, spec.shape)
        assertEquals(2.dp, spec.borderWidth)
        assertEquals(64.dp, spec.minimumWidth)
        assertEquals(48.dp, spec.minimumHeight)
        assertEquals(40.dp, spec.visualHeight)
        assertTrue(spec.clipContent)
        assertEquals(interaction, spec.stateLayerColors)
        assertTrue(node.modifier.elements.any { it is ClickableModifierElement })
        assertTrue(node.modifier.elements.any { it is ElevationModifierElement })
        assertEquals(
            listOf(shadow),
            node.modifier.elements.filterIsInstance<DropShadowModifierElement>().single().shadows,
        )
        val semantics = node.modifier.elements.filterIsInstance<SemanticsModifierElement>().single()
        assertEquals(SemanticsRole.Button, semantics.configuration.role)
        assertEquals(true, semantics.configuration.enabled)
        assertEquals(0xFFF0F1F2.toInt(), (node.children.single().spec as com.viewcompose.ui.node.spec.TextNodeProps).textColor)
    }

    @Test
    fun `disabled basic surface retains semantics without installing interaction`() {
        val node = buildVNodeTree {
            BasicSurface(
                style = BasicSurfaceStyle(
                    fill = Brush.SolidColor(0xFF112233.toInt()),
                    shape = UiShape.cut(6.dp),
                ),
                contentColor = 0xFFFFFFFF.toInt(),
                enabled = false,
                onClick = {},
                stateLayerColors = UiStateLayerColors(1, 2, 3),
                role = SemanticsRole.Button,
            ) {}
        }.single()

        val spec = node.spec as SurfaceNodeProps
        assertNull(spec.stateLayerColors)
        assertFalse(node.modifier.elements.any { it is ClickableModifierElement })
        val semantics = node.modifier.elements.filterIsInstance<SemanticsModifierElement>().single()
        assertEquals(false, semantics.configuration.enabled)
    }
}
