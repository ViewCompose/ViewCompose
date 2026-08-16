package com.viewcompose.renderer.view.tree

import android.widget.Button
import android.widget.FrameLayout
import android.graphics.Color
import android.graphics.drawable.RippleDrawable
import com.viewcompose.ui.layout.BoxAlignment
import com.viewcompose.ui.modifier.Modifier
import com.viewcompose.ui.modifier.backgroundColor
import com.viewcompose.ui.modifier.clickable
import com.viewcompose.ui.modifier.interactionIndication
import com.viewcompose.ui.modifier.shape
import com.viewcompose.ui.node.NodeType
import com.viewcompose.ui.node.UiInteractionIndication
import com.viewcompose.ui.node.UiStateLayerColors
import com.viewcompose.ui.node.VNode
import com.viewcompose.ui.node.spec.ButtonNodeProps
import com.viewcompose.ui.node.spec.BoxNodeProps
import com.viewcompose.ui.shape.UiShape
import com.viewcompose.ui.unit.dp
import com.viewcompose.ui.unit.sp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class ButtonStateLayerRenderingTest {
    private val context = RuntimeEnvironment.getApplication()

    @Test
    fun `state layer changes patch the retained button and selector`() {
        val container = FrameLayout(context)
        val initialColors = UiStateLayerColors(0x1A112233, 0x1A223344, 0x14334455)
        val initial = ViewTreeRenderer.renderInto(
            container = container,
            previous = emptyList(),
            nodes = listOf(buttonNode(initialColors)),
        )
        val button = initial.mountedNodes.single().view as Button
        val initialBackground = button.background

        val nextColors = UiStateLayerColors(0x1A556677, 0x1A667788, 0x14778899)
        val patched = ViewTreeRenderer.renderInto(
            container = container,
            previous = initial.mountedNodes,
            nodes = listOf(buttonNode(nextColors)),
        )

        assertSame(button, patched.mountedNodes.single().view)
        assertNotSame(initialBackground, button.background)
        assertEquals(1, patched.stats.patchedNodes)
        assertEquals(0, patched.stats.reboundNodes)
    }

    @Test
    fun `absent state layers retain value-only ripple fallback`() {
        val rippleColor = 0x33445566
        val selector = interactionColorStateList(rippleColor, indication = null)

        assertFalse(selector.isStateful)
        assertEquals(rippleColor, selector.defaultColor)
    }

    @Test
    fun `clickable container renders resolved pressed focused and hovered colors`() {
        val colors = UiStateLayerColors(0x1A112233, 0x1A223344, 0x14334455)
        val container = FrameLayout(context)
        val mounted = ViewTreeRenderer.renderInto(
            container = container,
            previous = emptyList(),
            nodes = listOf(
                VNode(
                    type = NodeType.Box,
                    spec = BoxNodeProps(contentAlignment = BoxAlignment.Center),
                    modifier = Modifier
                        .backgroundColor(Color.WHITE)
                        .shape(UiShape.rounded(20.dp))
                        .interactionIndication(UiInteractionIndication.StateLayer(colors))
                        .clickable {},
                ),
            ),
        )
        val ripple = mounted.mountedNodes.single().view.background as RippleDrawable

        assertTrue(ripple.isStateful)
        assertTrue(ripple.hasFocusStateSpecified())
    }

    private fun buttonNode(
        stateLayerColors: UiStateLayerColors?,
    ): VNode {
        return VNode(
            type = NodeType.Button,
            key = "button",
            spec = ButtonNodeProps(
                text = "Action",
                enabled = true,
                onClick = {},
                textColor = 0xFF000000.toInt(),
                textSizeSp = 14.sp,
                backgroundColor = 0xFFE0E0E0.toInt(),
                borderWidth = 0.dp,
                borderColor = 0,
                shape = UiShape.rounded(20.dp),
                minHeight = 40.dp,
                paddingHorizontal = 16.dp,
                paddingVertical = 8.dp,
                leadingIcon = null,
                trailingIcon = null,
                iconTint = 0,
                iconSize = 0.dp,
                iconSpacing = 0.dp,
            ),
            modifier = if (stateLayerColors == null) {
                Modifier
            } else {
                Modifier.interactionIndication(UiInteractionIndication.StateLayer(stateLayerColors))
            },
        )
    }
}
