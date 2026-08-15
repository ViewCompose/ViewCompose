package com.viewcompose.renderer.view.tree

import android.graphics.Color
import android.graphics.drawable.RippleDrawable
import android.widget.FrameLayout
import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import com.viewcompose.renderer.view.container.DeclarativeSegmentedControlLayout
import com.viewcompose.renderer.view.shape.UiShapeDrawable
import com.viewcompose.ui.environment.UiEnvironmentValues
import com.viewcompose.ui.node.NodeType
import com.viewcompose.ui.node.SegmentedControlItem
import com.viewcompose.ui.node.UiStateLayerColors
import com.viewcompose.ui.node.VNode
import com.viewcompose.ui.node.spec.SegmentedControlNodeProps
import com.viewcompose.ui.shape.UiShape
import com.viewcompose.ui.unit.UiDensity
import com.viewcompose.ui.unit.dp
import com.viewcompose.ui.unit.sp
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

    @Test
    fun `segment reorder reuses keyed views and respects item enabled state`() {
        val container = FrameLayout(context)
        val initialItems = listOf(
            SegmentedControlItem(key = "runtime", label = "Runtime"),
            SegmentedControlItem(key = "theme", label = "Theme"),
        )
        val selected = mutableListOf<Int>()
        val initial = ViewTreeRenderer.renderInto(
            container = container,
            previous = emptyList(),
            nodes = listOf(
                segmentedControlNode(
                    selectedIndex = 0,
                    backgroundColor = Color.WHITE,
                    items = initialItems,
                    onSelectionChange = selected::add,
                ),
            ),
        )
        val view = initial.mountedNodes.single().view as DeclarativeSegmentedControlLayout
        val runtime = view.getChildAt(0)
        val theme = view.getChildAt(1)

        ViewTreeRenderer.renderInto(
            container = container,
            previous = initial.mountedNodes,
            nodes = listOf(
                segmentedControlNode(
                    selectedIndex = 0,
                    backgroundColor = Color.WHITE,
                    items = listOf(
                        initialItems[1].copy(enabled = false),
                        initialItems[0],
                    ),
                    onSelectionChange = selected::add,
                ),
            ),
        )
        view.getChildAt(0).performClick()
        view.getChildAt(1).performClick()

        assertSame(theme, view.getChildAt(0))
        assertSame(runtime, view.getChildAt(1))
        assertEquals(false, view.getChildAt(0).isEnabled)
        assertEquals(listOf(1), selected)
    }

    @Test
    fun `density change recreates the container shape drawable`() {
        val container = FrameLayout(context)
        val initialNode = segmentedControlNode(
            selectedIndex = 0,
            backgroundColor = Color.WHITE,
        ).copy(
            environment = UiEnvironmentValues(
                density = UiDensity(density = 1f, fontScale = 1f),
            ),
        )
        val initial = ViewTreeRenderer.renderInto(
            container = container,
            previous = emptyList(),
            nodes = listOf(initialNode),
        )
        val view = initial.mountedNodes.single().view as DeclarativeSegmentedControlLayout
        val originalBackground = view.background

        ViewTreeRenderer.renderInto(
            container = container,
            previous = initial.mountedNodes,
            nodes = listOf(
                initialNode.copy(
                    environment = UiEnvironmentValues(
                        density = UiDensity(density = 2f, fontScale = 1f),
                    ),
                ),
            ),
        )

        assertNotSame(originalBackground, view.background)
        assertEquals(Color.WHITE, view.materialBackgroundColor())
    }

    @Test
    @Suppress("DEPRECATION")
    fun `segments expose stable collection item semantics`() {
        val container = FrameLayout(context)
        val result = ViewTreeRenderer.renderInto(
            container = container,
            previous = emptyList(),
            nodes = listOf(
                segmentedControlNode(
                    selectedIndex = 1,
                    backgroundColor = Color.WHITE,
                    items = listOf(
                        SegmentedControlItem(key = "runtime", label = "Runtime"),
                        SegmentedControlItem(key = "theme", label = "Theme", enabled = false),
                    ),
                ),
            ),
        )
        val view = result.mountedNodes.single().view as DeclarativeSegmentedControlLayout
        val selected = accessibilityNode(view.getChildAt(1))

        assertEquals(android.widget.Button::class.java.name, selected.className)
        assertEquals(0, selected.collectionItemInfo?.rowIndex)
        assertEquals(1, selected.collectionItemInfo?.columnIndex)
        assertTrue(selected.collectionItemInfo?.isSelected == true)
        assertEquals(false, selected.isEnabled)
    }

    private fun segmentedControlNode(
        selectedIndex: Int,
        backgroundColor: Int,
        items: List<SegmentedControlItem> = listOf(
            SegmentedControlItem(key = "runtime", label = "Runtime"),
            SegmentedControlItem(key = "theme", label = "Theme"),
        ),
        onSelectionChange: (Int) -> Unit = {},
        unselectedStateLayerColors: UiStateLayerColors? = null,
        selectedStateLayerColors: UiStateLayerColors? = null,
    ): VNode {
        return VNode(
            type = NodeType.SegmentedControl,
            key = "tabs",
            spec = SegmentedControlNodeProps(
                items = items,
                selectedIndex = selectedIndex,
                onSelectionChange = onSelectionChange,
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

    @Suppress("DEPRECATION")
    private fun accessibilityNode(view: android.view.View): AccessibilityNodeInfoCompat {
        return AccessibilityNodeInfoCompat.obtain().also { node ->
            requireNotNull(ViewCompat.getAccessibilityDelegate(view))
                .onInitializeAccessibilityNodeInfo(view, node)
        }
    }

}
