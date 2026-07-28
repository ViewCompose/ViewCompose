package com.viewcompose.renderer.view.tree

import android.widget.FrameLayout
import com.google.android.material.shape.MaterialShapeDrawable
import com.viewcompose.renderer.view.container.DeclarativeSegmentedControlLayout
import com.viewcompose.ui.node.NodeType
import com.viewcompose.ui.node.SegmentedControlItem
import com.viewcompose.ui.node.VNode
import com.viewcompose.ui.node.spec.SegmentedControlNodeProps
import com.viewcompose.ui.shape.UiShape
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
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

        assertEquals(initialColor, view.materialBackgroundColor())

        val patchedColor = 0xFFEFE4D2.toInt()
        val patched = ViewTreeRenderer.renderInto(
            container = container,
            previous = initial.mountedNodes,
            nodes = listOf(segmentedControlNode(selectedIndex = 1, backgroundColor = patchedColor)),
        )

        assertSame(view, patched.mountedNodes.single().view)
        assertEquals(patchedColor, view.materialBackgroundColor())
        assertEquals(1, patched.stats.patchedNodes)
        assertEquals(0, patched.stats.reboundNodes)
    }

    private fun segmentedControlNode(
        selectedIndex: Int,
        backgroundColor: Int,
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
                shape = UiShape.rounded(18),
                textColor = 0xFF6A5A4A.toInt(),
                selectedTextColor = 0xFFFFFFFF.toInt(),
                rippleColor = 0x22000000,
                textSizeSp = 14,
                paddingHorizontal = 14,
                paddingVertical = 8,
            ),
        )
    }

    private fun DeclarativeSegmentedControlLayout.materialBackgroundColor(): Int {
        return (background as MaterialShapeDrawable).fillColor!!.defaultColor
    }
}
