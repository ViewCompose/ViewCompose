package com.viewcompose.navigation

import android.view.View
import android.widget.FrameLayout
import com.viewcompose.navigation.core.NavPaneRole
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class NavPanePolicyTest {
    @Test
    fun `adaptive policy admits panes only when minimum width and spacing fit`() {
        val policy = NavPanePolicy(
            minPaneWidthDp = 300f,
            maxPaneCount = 3,
            paneSpacingDp = 12f,
        )

        assertEquals(1, policy.resolvePaneCount(widthPixels = 599, density = 1f))
        assertEquals(2, policy.resolvePaneCount(widthPixels = 612, density = 1f))
        assertEquals(2, policy.resolvePaneCount(widthPixels = 923, density = 1f))
        assertEquals(3, policy.resolvePaneCount(widthPixels = 924, density = 1f))
        assertEquals(2, policy.resolvePaneCount(widthPixels = 1_224, density = 2f))
        assertEquals(24, policy.resolveSpacingPixels(density = 2f))
    }

    @Test
    fun `single policy remains one pane at every width`() {
        assertEquals(
            1,
            NavPanePolicy.Single.resolvePaneCount(
                widthPixels = 10_000,
                density = 1f,
            ),
        )
    }

    @Test
    fun `invalid policy dimensions fail immediately`() {
        assertThrows<IllegalArgumentException> {
            NavPanePolicy(minPaneWidthDp = 0f)
        }
        assertThrows<IllegalArgumentException> {
            NavPanePolicy(maxPaneCount = 4)
        }
        assertThrows<IllegalArgumentException> {
            NavPanePolicy(paneSpacingDp = Float.NaN)
        }
    }

    @Test
    fun `host view measures and lays out exact equal panes with spacing`() {
        val context = RuntimeEnvironment.getApplication()
        val host = NavHostView(context)
        val primary = FrameLayout(context)
        val secondary = FrameLayout(context)
        val tertiary = FrameLayout(context)
        host.addView(primary)
        host.addView(secondary)
        host.addView(tertiary)
        host.paneSpacingPixels = 12
        host.updatePaneLayouts(
            mapOf(
                primary to NavPaneLayout(NavPaneRole.Primary, 3),
                secondary to NavPaneLayout(NavPaneRole.Secondary, 3),
                tertiary to NavPaneLayout(NavPaneRole.Tertiary, 3),
            ),
        )

        measureAndLayout(host, width = 912, height = 600)

        assertEquals(0, primary.left)
        assertEquals(296, primary.right)
        assertEquals(308, secondary.left)
        assertEquals(604, secondary.right)
        assertEquals(616, tertiary.left)
        assertEquals(912, tertiary.right)
        assertEquals(600, primary.height)
        assertEquals(296, secondary.measuredWidth)
    }

    @Test
    fun `host view mirrors logical pane roles in RTL`() {
        val primary = resolvePaneHorizontalBounds(
            availableWidth = 610,
            paneLayout = NavPaneLayout(NavPaneRole.Primary, 2),
            paneSpacingPixels = 10,
            layoutDirection = View.LAYOUT_DIRECTION_RTL,
        )
        val secondary = resolvePaneHorizontalBounds(
            availableWidth = 610,
            paneLayout = NavPaneLayout(NavPaneRole.Secondary, 2),
            paneSpacingPixels = 10,
            layoutDirection = View.LAYOUT_DIRECTION_RTL,
        )

        assertEquals(310, primary.left)
        assertEquals(610, primary.right)
        assertEquals(0, secondary.left)
        assertEquals(300, secondary.right)
    }

    private fun measureAndLayout(
        view: View,
        width: Int,
        height: Int,
    ) {
        view.measure(
            View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY),
        )
        view.layout(0, 0, width, height)
    }

    private inline fun <reified T : Throwable> assertThrows(block: () -> Unit): T {
        try {
            block()
        } catch (throwable: Throwable) {
            if (throwable is T) {
                return throwable
            }
            throw throwable
        }
        fail("Expected ${T::class.simpleName} to be thrown.")
        error("Unreachable")
    }
}
