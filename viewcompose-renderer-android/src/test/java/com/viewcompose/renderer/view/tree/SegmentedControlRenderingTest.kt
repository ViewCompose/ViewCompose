package com.viewcompose.renderer.view.tree

import com.viewcompose.ui.unit.sp

import com.viewcompose.ui.unit.dp

/*
 * 测试职责：覆盖 renderer view/tree 中的 Segmented Control Rendering 行为，防止渲染和 patch 契约在后续重构中回退。
 * Test responsibility: covers Segmented Control Rendering behavior in renderer view/tree and guards render and patch contracts against regressions.
 */

import android.graphics.Color
import android.graphics.drawable.RippleDrawable
import android.widget.FrameLayout
import android.widget.TextView
import com.viewcompose.renderer.view.shape.UiShapeDrawable
import com.viewcompose.renderer.view.container.DeclarativeSegmentedControlLayout
import com.viewcompose.ui.node.NodeType
import com.viewcompose.ui.node.SegmentedControlItem
import com.viewcompose.ui.node.UiStateLayerColors
import com.viewcompose.ui.node.VNode
import com.viewcompose.ui.node.spec.SegmentedControlNodeProps
import com.viewcompose.ui.shape.UiShape
import org.junit.Assert.assertEquals
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
class SegmentedControlRenderingTest {
    private val context = RuntimeEnvironment.getApplication()

    @Test
    fun `mount and selection patch keep the semantic container background attached`() {
        val container = FrameLayout(context)
        val initialColor = 0xFFF8EED8.toInt()
        val initial = ViewTreeRenderer.renderInto(
            container = container,
            previous = emptyList(),
            nodes = listOf(segmentedControlNode(selectedIndex = 0, backgroundColor = initialColor)),
        )
        val view = initial.mountedNodes.single().view as DeclarativeSegmentedControlLayout
        val runtimeTab = view.getChildAt(0) as TextView
        val themeTab = view.getChildAt(1) as TextView
        val runtimeBackground = runtimeTab.background
        val themeBackground = themeTab.background
        val runtimeTypeface = runtimeTab.typeface
        val themeTypeface = themeTab.typeface

        assertEquals(initialColor, view.materialBackgroundColor())
        assertEquals(0xFF7B9E68.toInt(), runtimeTab.indicatorColor())
        assertEquals(Color.TRANSPARENT, themeTab.indicatorColor())

        val patchedColor = 0xFFEFE4D2.toInt()
        val patched = ViewTreeRenderer.renderInto(
            container = container,
            previous = initial.mountedNodes,
            nodes = listOf(segmentedControlNode(selectedIndex = 1, backgroundColor = patchedColor)),
        )

        assertSame(view, patched.mountedNodes.single().view)
        assertSame(runtimeBackground, runtimeTab.background)
        assertSame(themeBackground, themeTab.background)
        assertSame(runtimeTypeface, runtimeTab.typeface)
        assertSame(themeTypeface, themeTab.typeface)
        assertEquals(patchedColor, view.materialBackgroundColor())
        assertEquals(Color.TRANSPARENT, runtimeTab.indicatorColor())
        assertEquals(0xFF7B9E68.toInt(), themeTab.indicatorColor())
        assertEquals(1, patched.stats.patchedNodes)
        assertEquals(0, patched.stats.reboundNodes)
    }

    @Test
    fun `selected and unselected segments use their resolved state-layer roles`() {
        val container = FrameLayout(context)
        val unselected = UiStateLayerColors(
            pressedColor = 0x1A112233,
            focusedColor = 0x1A223344,
            hoveredColor = 0x14223344,
        )
        val selected = UiStateLayerColors(
            pressedColor = 0x1A445566,
            focusedColor = 0x1A556677,
            hoveredColor = 0x14556677,
        )
        val initial = ViewTreeRenderer.renderInto(
            container = container,
            previous = emptyList(),
            nodes = listOf(
                segmentedControlNode(
                    selectedIndex = 0,
                    backgroundColor = Color.WHITE,
                    unselectedStateLayerColors = unselected,
                    selectedStateLayerColors = selected,
                ),
            ),
        )
        val view = initial.mountedNodes.single().view as DeclarativeSegmentedControlLayout
        val first = view.getChildAt(0) as TextView
        val second = view.getChildAt(1) as TextView
        val firstBackground = first.background
        val secondBackground = second.background

        assertTrue((first.background as RippleDrawable).hasFocusStateSpecified())
        assertTrue((second.background as RippleDrawable).hasFocusStateSpecified())

        ViewTreeRenderer.renderInto(
            container = container,
            previous = initial.mountedNodes,
            nodes = listOf(
                segmentedControlNode(
                    selectedIndex = 1,
                    backgroundColor = Color.WHITE,
                    unselectedStateLayerColors = unselected,
                    selectedStateLayerColors = selected,
                ),
            ),
        )

        assertNotSame(firstBackground, first.background)
        assertNotSame(secondBackground, second.background)
        assertTrue((first.background as RippleDrawable).hasFocusStateSpecified())
        assertTrue((second.background as RippleDrawable).hasFocusStateSpecified())
    }

    private fun segmentedControlNode(
        selectedIndex: Int,
        backgroundColor: Int,
        unselectedStateLayerColors: UiStateLayerColors? = null,
        selectedStateLayerColors: UiStateLayerColors? = null,
    ): VNode {
        return VNode(
            type = NodeType.SegmentedControl,
            key = "tabs",
            spec = SegmentedControlNodeProps(
                items = listOf(
                    SegmentedControlItem("Runtime"),
                    SegmentedControlItem("Theme"),
                ),
                selectedIndex = selectedIndex,
                onSelectionChange = {},
                enabled = true,
                backgroundColor = backgroundColor,
                indicatorColor = 0xFF7B9E68.toInt(),
                shape = UiShape.rounded(18.dp),
                textColor = 0xFF6A5A4A.toInt(),
                selectedTextColor = 0xFFFFFFFF.toInt(),
                rippleColor = 0x22000000,
                textSizeSp = 14.sp,
                paddingHorizontal = 14.dp,
                paddingVertical = 8.dp,
                unselectedStateLayerColors = unselectedStateLayerColors,
                selectedStateLayerColors = selectedStateLayerColors,
            ),
        )
    }

    private fun DeclarativeSegmentedControlLayout.materialBackgroundColor(): Int {
        return (background as UiShapeDrawable).currentFillColor
    }

    private fun TextView.indicatorColor(): Int {
        val ripple = background as RippleDrawable
        return (ripple.getDrawable(0) as UiShapeDrawable).currentFillColor
    }

}
