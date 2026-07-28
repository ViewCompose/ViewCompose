package com.viewcompose.renderer.view.container

/*
 * 测试职责：覆盖 renderer view/container 中的 Declarative Layout Invalidation 行为，防止渲染和 patch 契约在后续重构中回退。
 * Test responsibility: covers Declarative Layout Invalidation behavior in renderer view/container and guards render and patch contracts against regressions.
 */

import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import com.viewcompose.ui.layout.MainAxisArrangement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class DeclarativeLayoutInvalidationTest {
    @Test
    fun `linear layout keeps margin and spacing placement without temporary specs`() {
        val context = RuntimeEnvironment.getApplication()
        val view = DeclarativeLinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            itemSpacing = 8
            mainAxisArrangement = MainAxisArrangement.Start
        }
        val first = View(context)
        val second = View(context)
        view.addView(
            first,
            LinearLayout.LayoutParams(40, 20).apply {
                leftMargin = 10
                rightMargin = 6
            },
        )
        view.addView(
            second,
            LinearLayout.LayoutParams(20, 20).apply {
                leftMargin = 4
                rightMargin = 2
            },
        )

        val widthSpec = View.MeasureSpec.makeMeasureSpec(200, View.MeasureSpec.EXACTLY)
        val heightSpec = View.MeasureSpec.makeMeasureSpec(40, View.MeasureSpec.EXACTLY)
        view.measure(widthSpec, heightSpec)
        view.layout(0, 0, 200, 40)

        assertEquals(10, first.left)
        assertEquals(50, first.right)
        assertEquals(68, second.left)
        assertEquals(88, second.right)
    }

    @Test
    fun `box only requests layout when content gravity changes`() {
        val view = DeclarativeBoxLayout(RuntimeEnvironment.getApplication())
        settleLayout(view)

        view.contentGravity = Gravity.TOP or Gravity.START
        assertFalse(view.isLayoutRequested)

        view.contentGravity = Gravity.CENTER
        assertTrue(view.isLayoutRequested)
    }

    @Test
    fun `linear layout ignores equivalent arrangement and spacing patches`() {
        val view = DeclarativeLinearLayout(RuntimeEnvironment.getApplication())
        settleLayout(view)

        view.itemSpacing = 0
        view.mainAxisArrangement = MainAxisArrangement.Start
        assertFalse(view.isLayoutRequested)

        view.itemSpacing = 12
        assertTrue(view.isLayoutRequested)
    }

    @Test
    fun `flow row compares the resolved max item count`() {
        val view = DeclarativeFlowRowLayout(RuntimeEnvironment.getApplication())
        view.maxItemsInEachRow = 1
        settleLayout(view)

        view.horizontalSpacing = 0
        view.verticalSpacing = 0
        view.maxItemsInEachRow = 0
        assertFalse(view.isLayoutRequested)

        view.verticalSpacing = 8
        assertTrue(view.isLayoutRequested)
    }

    @Test
    fun `flow column compares the resolved max item count`() {
        val view = DeclarativeFlowColumnLayout(RuntimeEnvironment.getApplication())
        view.maxItemsInEachColumn = 1
        settleLayout(view)

        view.horizontalSpacing = 0
        view.verticalSpacing = 0
        view.maxItemsInEachColumn = -1
        assertFalse(view.isLayoutRequested)

        view.horizontalSpacing = 8
        assertTrue(view.isLayoutRequested)
    }

    private fun settleLayout(view: View) {
        val measureSpec = View.MeasureSpec.makeMeasureSpec(200, View.MeasureSpec.EXACTLY)
        view.measure(measureSpec, measureSpec)
        view.layout(0, 0, 200, 200)
        assertFalse(view.isLayoutRequested)
    }
}
