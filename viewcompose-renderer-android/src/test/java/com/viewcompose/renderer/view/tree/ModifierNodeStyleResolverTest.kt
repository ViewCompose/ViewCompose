package com.viewcompose.renderer.view.tree

import com.viewcompose.ui.unit.sp

import com.viewcompose.ui.unit.dp

/*
 * 测试职责：覆盖 renderer view/tree 中的 Modifier Node Style Resolver 行为，防止渲染和 patch 契约在后续重构中回退。
 * Test responsibility: covers Modifier Node Style Resolver behavior in renderer view/tree and guards render and patch contracts against regressions.
 */

import com.viewcompose.ui.modifier.Modifier
import com.viewcompose.ui.modifier.backgroundColor
import com.viewcompose.ui.modifier.backgroundDrawableRes
import com.viewcompose.ui.node.NodeType
import com.viewcompose.ui.node.UiStateLayerColors
import com.viewcompose.ui.node.VNode
import com.viewcompose.ui.node.spec.ButtonNodeProps
import com.viewcompose.ui.node.spec.BoxNodeProps
import com.viewcompose.ui.node.spec.RowNodeProps
import com.viewcompose.ui.layout.BoxAlignment
import com.viewcompose.ui.layout.MainAxisArrangement
import com.viewcompose.ui.layout.VerticalAlignment
import com.viewcompose.ui.shape.UiShape
import com.viewcompose.renderer.modifier.resolve
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class ModifierNodeStyleResolverTest {
    @Test
    fun `node style keeps drawable resource when drawable and color both set`() {
        val node = buttonVNode(
            modifier = Modifier
                .backgroundColor(0xFF0000AA.toInt())
                .backgroundDrawableRes(99),
        )

        val style = ModifierNodeStyleResolver.resolveNodeStyle(
            node = node,
            resolved = node.modifier.resolve(),
            defaultRippleColor = 0xFF00FF00.toInt(),
        )

        assertEquals(99, style.backgroundDrawableResId)
        assertNotNull(style.backgroundColor)
        assertEquals(0xFF0000AA.toInt(), style.backgroundColor)
        assertEquals(VerticalSurfaceInsetsPx.Zero, style.surfaceInsets)
    }

    @Test
    fun `button style centers visual surface inside effective height`() {
        val node = buttonVNode(
            modifier = Modifier,
            minHeight = 48.dp,
            visualHeight = 40.dp,
        )

        val style = ModifierNodeStyleResolver.resolveNodeStyle(
            node = node,
            resolved = node.modifier.resolve(),
            defaultRippleColor = 0xFF00FF00.toInt(),
        )

        assertEquals(VerticalSurfaceInsetsPx(top = 4, bottom = 4), style.surfaceInsets)
    }

    @Test
    fun `button style carries resolved state layers without material policy`() {
        val colors = UiStateLayerColors(
            pressedColor = 0x1A112233,
            focusedColor = 0x1A223344,
            hoveredColor = 0x14223344,
        )
        val node = buttonVNode(
            modifier = Modifier,
            stateLayerColors = colors,
        )

        val style = ModifierNodeStyleResolver.resolveNodeStyle(
            node = node,
            resolved = node.modifier.resolve(),
            defaultRippleColor = 0xFF00FF00.toInt(),
        )

        assertEquals(colors, style.stateLayerColors)
    }

    @Test
    fun `box and row styles carry the same renderer-neutral state-layer contract`() {
        val colors = UiStateLayerColors(
            pressedColor = 0x1A112233,
            focusedColor = 0x1A223344,
            hoveredColor = 0x14223344,
        )
        val nodes = listOf(
            VNode(
                type = NodeType.Box,
                spec = BoxNodeProps(
                    contentAlignment = BoxAlignment.Center,
                    rippleColor = 0x22112233,
                    stateLayerColors = colors,
                ),
            ),
            VNode(
                type = NodeType.Row,
                spec = RowNodeProps(
                    spacing = 0.dp,
                    arrangement = MainAxisArrangement.Start,
                    verticalAlignment = VerticalAlignment.Center,
                    rippleColor = 0x22112233,
                    stateLayerColors = colors,
                ),
            ),
        )

        nodes.forEach { node ->
            val style = ModifierNodeStyleResolver.resolveNodeStyle(
                node = node,
                resolved = node.modifier.resolve(),
                defaultRippleColor = 0xFF00FF00.toInt(),
            )
            assertEquals(0x22112233, style.rippleColor)
            assertEquals(colors, style.stateLayerColors)
        }
    }

    @Test
    fun `explicit button surface override disables component visual inset`() {
        val node = buttonVNode(
            modifier = Modifier.backgroundColor(0xFF0000AA.toInt()),
            minHeight = 48.dp,
            visualHeight = 40.dp,
        )

        val style = ModifierNodeStyleResolver.resolveNodeStyle(
            node = node,
            resolved = node.modifier.resolve(),
            defaultRippleColor = 0xFF00FF00.toInt(),
        )

        assertEquals(VerticalSurfaceInsetsPx.Zero, style.surfaceInsets)
    }

    @Test
    fun `surface inset clamps invalid visual height and preserves odd pixels`() {
        assertEquals(
            VerticalSurfaceInsetsPx(top = 3, bottom = 4),
            centeredVerticalSurfaceInsets(effectiveHeightPx = 48, visualHeightPx = 41),
        )
        assertEquals(
            VerticalSurfaceInsetsPx.Zero,
            centeredVerticalSurfaceInsets(effectiveHeightPx = 40, visualHeightPx = 48),
        )
    }

    private fun buttonVNode(
        modifier: Modifier,
        minHeight: com.viewcompose.ui.unit.UiDp = 0.dp,
        visualHeight: com.viewcompose.ui.unit.UiDp = minHeight,
        stateLayerColors: UiStateLayerColors? = null,
    ): VNode {
        return VNode(
            type = NodeType.Button,
            spec = ButtonNodeProps(
                text = "Button",
                enabled = true,
                onClick = null,
                textColor = 0xFF000000.toInt(),
                textSizeSp = 14.sp,
                backgroundColor = 0xFFE0E0E0.toInt(),
                borderWidth = 0.dp,
                borderColor = 0,
                shape = UiShape.rounded(0.dp),
                rippleColor = 0x33000000,
                minHeight = minHeight,
                paddingHorizontal = 0.dp,
                paddingVertical = 0.dp,
                leadingIcon = null,
                trailingIcon = null,
                iconTint = 0,
                iconSize = 0.dp,
                iconSpacing = 0.dp,
                visualHeight = visualHeight,
                stateLayerColors = stateLayerColors,
            ),
            modifier = modifier,
            children = emptyList(),
        )
    }
}
