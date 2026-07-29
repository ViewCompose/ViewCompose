package com.viewcompose.renderer.view.container

import com.viewcompose.ui.unit.sp

import com.viewcompose.ui.unit.dp

/*
 * 测试职责：覆盖 renderer view/container 中的 Navigation Container Invalidation 行为，防止渲染和 patch 契约在后续重构中回退。
 * Test responsibility: covers Navigation Container Invalidation behavior in renderer view/container and guards render and patch contracts against regressions.
 */

import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.viewcompose.ui.node.ImageSource
import com.viewcompose.ui.node.NavigationBarItem
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class NavigationContainerInvalidationTest {
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

    private fun settleLayout(view: View) {
        val measureSpec = View.MeasureSpec.makeMeasureSpec(320, View.MeasureSpec.EXACTLY)
        view.measure(measureSpec, measureSpec)
        view.layout(0, 0, 320, 320)
        assertFalse(view.isLayoutRequested)
    }

    private fun navigationItems(): List<NavigationBarItem> {
        return listOf(
            NavigationBarItem(
                label = "First",
                icon = ImageSource.Resource(android.R.drawable.ic_menu_add),
                badgeCount = 3,
            ),
            NavigationBarItem(
                label = "Second",
                icon = ImageSource.Resource(android.R.drawable.ic_menu_search),
            ),
        )
    }

    private fun bindNavigation(
        view: DeclarativeNavigationBarLayout,
        items: List<NavigationBarItem>,
        selectedIndex: Int,
    ) {
        view.bind(
            items = items,
            selectedIndex = selectedIndex,
            onItemSelected = null,
            containerColor = 0xFFFFFFFF.toInt(),
            selectedIconColor = 0xFF000000.toInt(),
            unselectedIconColor = 0xFF777777.toInt(),
            selectedLabelColor = 0xFF000000.toInt(),
            unselectedLabelColor = 0xFF777777.toInt(),
            indicatorColor = 0xFFE0E0E0.toInt(),
            rippleColor = 0x22000000,
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
}
