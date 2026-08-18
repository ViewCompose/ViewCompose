package com.viewcompose.renderer.view.container

import com.viewcompose.ui.unit.sp

import com.viewcompose.ui.unit.dp

/*
 * 测试职责：覆盖 renderer view/container 中的 Navigation Container Invalidation 行为，防止渲染和 patch 契约在后续重构中回退。
 * Test responsibility: covers Navigation Container Invalidation behavior in renderer view/container and guards render and patch contracts against regressions.
 */

import android.content.pm.ApplicationInfo
import android.graphics.drawable.RippleDrawable
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import com.viewcompose.ui.node.ImageSource
import com.viewcompose.ui.node.NavigationBarItem
import com.viewcompose.ui.node.UiStateLayerColors
import com.viewcompose.ui.node.collection.TabIndicatorPosition
import com.viewcompose.ui.node.collection.TabIndicatorWidthMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
class NavigationContainerInvalidationTest {
    @Test
    fun `tab row preserves initial selection while eager children are mounted`() {
        val context: android.content.Context = RuntimeEnvironment.getApplication()
        val view = DeclarativeTabRowLayout(context)

        view.bind(
            selectedIndex = 2,
            pagerState = null,
            indicatorColor = 0xFF000000.toInt(),
            indicatorHeight = 4,
            indicatorCornerRadius = 2,
            indicatorPosition = TabIndicatorPosition.Bottom,
            indicatorWidthMode = TabIndicatorWidthMode.MatchItem,
            indicatorFixedWidth = 0,
            containerColor = 0xFFFFFFFF.toInt(),
            scrollable = true,
            equalWidth = true,
            itemSpacing = 0,
            itemPaddingHorizontal = 0,
            itemPaddingVertical = 0,
            minItemWidth = 0,
        )
        repeat(3) {
            view.childHost.addView(View(context))
        }
        settleLayout(view)

        val selectedIndexField = DeclarativeTabRowLayout::class.java.getDeclaredField("selectedIndex")
        selectedIndexField.isAccessible = true
        assertEquals(2, selectedIndexField.getInt(view))
    }

    @Test
    fun `tab row container only requests layout for layout-affecting changes`() {
        val view = TabRowContainer(RuntimeEnvironment.getApplication())
        settleLayout(view)

        view.equalWidth = true
        view.itemSpacingPx = 0
        view.minItemWidthPx = 0
        assertFalse(view.isLayoutRequested)

        view.indicatorHeightPx = 2
        assertFalse(view.isLayoutRequested)

        view.itemSpacingPx = 8
        assertTrue(view.isLayoutRequested)
    }

    @Test
    @Config(qualifiers = "ldrtl")
    fun `tab row lays out eager children from right to left in rtl`() {
        val context = RuntimeEnvironment.getApplication()
        context.applicationInfo.flags = context.applicationInfo.flags or ApplicationInfo.FLAG_SUPPORTS_RTL
        val view = DeclarativeTabRowLayout(context).apply {
            layoutDirection = View.LAYOUT_DIRECTION_RTL
        }
        view.childHost.layoutDirection = View.LAYOUT_DIRECTION_RTL
        view.bind(
            selectedIndex = 0,
            pagerState = null,
            indicatorColor = 0,
            indicatorHeight = 0,
            indicatorCornerRadius = 0,
            indicatorPosition = TabIndicatorPosition.Bottom,
            indicatorWidthMode = TabIndicatorWidthMode.MatchItem,
            indicatorFixedWidth = 0,
            containerColor = 0,
            scrollable = true,
            equalWidth = false,
            itemSpacing = 10,
            itemPaddingHorizontal = 0,
            itemPaddingVertical = 0,
            minItemWidth = 0,
        )
        val first = View(context).apply {
            minimumWidth = 20
            minimumHeight = 20
        }
        val second = View(context).apply {
            minimumWidth = 20
            minimumHeight = 20
        }
        view.childHost.addView(first, ViewGroup.LayoutParams(20, 20))
        view.childHost.addView(second, ViewGroup.LayoutParams(20, 20))
        val host = FrameLayout(context).apply {
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            addView(view, FrameLayout.LayoutParams(100, 40))
        }
        host.measure(
            View.MeasureSpec.makeMeasureSpec(100, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(40, View.MeasureSpec.EXACTLY),
        )
        host.layout(0, 0, 100, 40)

        assertEquals(View.LAYOUT_DIRECTION_RTL, view.childHost.layoutDirection)
        assertEquals(20, first.measuredWidth)
        assertTrue(first.left > second.left)
        assertEquals(30, first.left - second.left)
    }

    @Test
    fun `navigation selection reuses icon badge and text style objects`() {
        val view = DeclarativeNavigationBarLayout(RuntimeEnvironment.getApplication())
        val items = navigationItems()
        bindNavigation(view, items, selectedIndex = 0)

        val firstRoot = view.getChildAt(0) as LinearLayout
        val firstIconContainer = firstRoot.getChildAt(0) as FrameLayout
        val firstIcon = firstIconContainer.getChildAt(1) as ImageView
        val firstBadge = firstIconContainer.getChildAt(2) as TextView
        val firstLabel = firstRoot.getChildAt(1) as TextView
        val iconDrawable = firstIcon.drawable
        val badgeDrawable = firstBadge.background
        val typeface = firstLabel.typeface

        bindNavigation(view, items, selectedIndex = 1)

        assertSame(firstRoot, view.getChildAt(0))
        assertSame(iconDrawable, firstIcon.drawable)
        assertSame(badgeDrawable, firstBadge.background)
        assertSame(typeface, firstLabel.typeface)
    }

    @Test
    fun `navigation reorder reuses views by stable key and updates callback index`() {
        val view = DeclarativeNavigationBarLayout(RuntimeEnvironment.getApplication())
        val original = navigationItems()
        val selected = mutableListOf<Int>()
        bindNavigation(view, original, selectedIndex = 0, onItemSelected = selected::add)
        val first = view.getChildAt(0)
        val second = view.getChildAt(1)

        bindNavigation(
            view,
            original.reversed(),
            selectedIndex = 0,
            onItemSelected = selected::add,
        )
        view.getChildAt(0).performClick()

        assertSame(second, view.getChildAt(0))
        assertSame(first, view.getChildAt(1))
        assertEquals(listOf(0), selected)
    }

    @Test
    fun `navigation item enabled state blocks only that item`() {
        val view = DeclarativeNavigationBarLayout(RuntimeEnvironment.getApplication())
        val selected = mutableListOf<Int>()
        val items = navigationItems().mapIndexed { index, item ->
            if (index == 0) item.copy(enabled = false) else item
        }
        bindNavigation(view, items, selectedIndex = 0, onItemSelected = selected::add)

        view.getChildAt(0).performClick()
        view.getChildAt(1).performClick()

        assertEquals(false, view.getChildAt(0).isEnabled)
        assertEquals(true, view.getChildAt(1).isEnabled)
        assertEquals(listOf(1), selected)
    }

    @Test
    fun `navigation state layer stays visible for icon and label touch targets`() {
        val view = DeclarativeNavigationBarLayout(RuntimeEnvironment.getApplication())
        bindNavigation(
            view = view,
            items = navigationItems(),
            selectedIndex = 0,
            selectedStateLayerColor = 0x22112233,
            unselectedStateLayerColor = 0x22445566,
        )
        settleLayout(view)

        val selectedRoot = view.getChildAt(0) as LinearLayout
        val selectedIcon = ((selectedRoot.getChildAt(0) as FrameLayout).getChildAt(1))
        val unselectedRoot = view.getChildAt(1) as LinearLayout
        val unselectedLabel = unselectedRoot.getChildAt(1)

        val selectedRipple = selectedRoot.foreground as RippleDrawable
        val unselectedRipple = unselectedRoot.foreground as RippleDrawable
        assertEquals(null, selectedRoot.background)
        dispatchPressWithForegroundAssertion(selectedRoot, selectedIcon)
        dispatchPressWithForegroundAssertion(unselectedRoot, unselectedLabel)

        bindNavigation(
            view = view,
            items = navigationItems(),
            selectedIndex = 1,
            selectedStateLayerColor = 0x22112233,
            unselectedStateLayerColor = 0x22445566,
        )

        assertSame(selectedRipple, selectedRoot.foreground)
        assertSame(unselectedRipple, unselectedRoot.foreground)
    }

    @Test
    @Suppress("DEPRECATION")
    fun `navigation items expose tab position selection and enabled semantics`() {
        val view = DeclarativeNavigationBarLayout(RuntimeEnvironment.getApplication())
        val items = navigationItems().mapIndexed { index, item ->
            if (index == 1) item.copy(enabled = false) else item
        }
        bindNavigation(view, items, selectedIndex = 0)

        val selectedNode = accessibilityNode(view.getChildAt(0))
        val disabledNode = accessibilityNode(view.getChildAt(1))

        assertEquals(android.widget.Button::class.java.name, selectedNode.className)
        assertEquals(0, selectedNode.collectionItemInfo?.columnIndex)
        assertTrue(selectedNode.collectionItemInfo?.isSelected == true)
        assertTrue(selectedNode.isEnabled)
        assertEquals(1, disabledNode.collectionItemInfo?.columnIndex)
        assertFalse(disabledNode.collectionItemInfo?.isSelected == true)
        assertFalse(disabledNode.isEnabled)
    }

    private fun settleLayout(view: View) {
        val measureSpec = View.MeasureSpec.makeMeasureSpec(320, View.MeasureSpec.EXACTLY)
        view.measure(measureSpec, measureSpec)
        view.layout(0, 0, 320, 320)
        assertFalse(view.isLayoutRequested)
    }

    private fun dispatchPressWithForegroundAssertion(root: ViewGroup, target: View) {
        val targetCenter = target.centerRelativeTo(root)
        val downTime = SystemClock.uptimeMillis()
        val down = MotionEvent.obtain(
            downTime,
            downTime,
            MotionEvent.ACTION_DOWN,
            targetCenter.first,
            targetCenter.second,
            0,
        )
        val cancel = MotionEvent.obtain(
            downTime,
            downTime + 16L,
            MotionEvent.ACTION_CANCEL,
            targetCenter.first,
            targetCenter.second,
            0,
        )
        try {
            assertTrue(root.dispatchTouchEvent(down))
            root.refreshDrawableState()
            assertTrue(root.isPressed)
            assertTrue(
                requireNotNull(root.foreground).state.contains(android.R.attr.state_pressed),
            )
            assertTrue(root.dispatchTouchEvent(cancel))
            root.isPressed = false
        } finally {
            down.recycle()
            cancel.recycle()
        }
    }

    private fun View.centerRelativeTo(ancestor: ViewGroup): Pair<Float, Float> {
        var relativeLeft = left
        var relativeTop = top
        var current = parent as? View
        while (current != null && current !== ancestor) {
            relativeLeft += current.left
            relativeTop += current.top
            current = current.parent as? View
        }
        check(current === ancestor) { "Target is not a descendant of the navigation item." }
        return Pair(
            relativeLeft + width / 2f,
            relativeTop + height / 2f,
        )
    }

    private fun navigationItems(): List<NavigationBarItem> {
        return listOf(
            NavigationBarItem(
                key = "first",
                label = "First",
                icon = ImageSource.Resource(android.R.drawable.ic_menu_add),
                badgeCount = 3,
            ),
            NavigationBarItem(
                key = "second",
                label = "Second",
                icon = ImageSource.Resource(android.R.drawable.ic_menu_search),
            ),
        )
    }

    private fun bindNavigation(
        view: DeclarativeNavigationBarLayout,
        items: List<NavigationBarItem>,
        selectedIndex: Int,
        onItemSelected: ((Int) -> Unit)? = null,
        selectedStateLayerColor: Int = 0x22000000,
        unselectedStateLayerColor: Int = 0x22000000,
    ) {
        view.bind(
            items = items,
            selectedIndex = selectedIndex,
            onItemSelected = onItemSelected,
            containerColor = 0xFFFFFFFF.toInt(),
            selectedIconColor = 0xFF000000.toInt(),
            unselectedIconColor = 0xFF777777.toInt(),
            selectedLabelColor = 0xFF000000.toInt(),
            unselectedLabelColor = 0xFF777777.toInt(),
            indicatorColor = 0xFFE0E0E0.toInt(),
            selectedStateLayerColors = stateLayerColors(selectedStateLayerColor),
            unselectedStateLayerColors = stateLayerColors(unselectedStateLayerColor),
            iconSize = 24,
            labelSizePx = 12f,
            labelFontWeight = null,
            labelFontFamily = null,
            labelLetterSpacingEm = null,
            labelLineHeightPx = null,
            labelIncludeFontPadding = false,
            badgeColor = 0xFFFF0000.toInt(),
            badgeTextColor = 0xFFFFFFFF.toInt(),
        )
    }

    private fun stateLayerColors(color: Int) = UiStateLayerColors(
        pressedColor = color,
        focusedColor = color,
        hoveredColor = color,
    )

    @Suppress("DEPRECATION")
    private fun accessibilityNode(view: View): AccessibilityNodeInfoCompat {
        return AccessibilityNodeInfoCompat.obtain().also { node ->
            requireNotNull(ViewCompat.getAccessibilityDelegate(view))
                .onInitializeAccessibilityNodeInfo(view, node)
        }
    }
}
