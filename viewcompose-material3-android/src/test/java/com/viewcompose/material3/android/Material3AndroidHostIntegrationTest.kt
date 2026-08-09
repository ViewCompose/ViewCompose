package com.viewcompose.material3.android

/*
 * 测试职责：覆盖 Material 3 Android 具名 Host 的 Context、Token 与刷新一致性。
 * Test responsibility: covers named Material 3 Android host context, token, and refresh coherence.
 */

import android.content.Context
import android.content.MutableContextWrapper
import android.os.Bundle
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import com.viewcompose.material3.Material3DynamicColorPolicy
import com.viewcompose.material3.Material3ThemeRefreshController
import com.viewcompose.material3.android.test.R as TestR
import com.viewcompose.ui.foundation.Button
import com.viewcompose.ui.foundation.OverlayHostDefaults
import com.viewcompose.ui.foundation.Theme
import com.viewcompose.ui.foundation.UiThemeTokens
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [24, 31, 35])
class Material3AndroidHostIntegrationTest {
    @Test
    fun `Material root context is shared by tokens native views and overlays`() {
        val activity = Robolectric.buildActivity(Material3HostActivity::class.java)
            .setup()
            .get()
        var capturedTokens: UiThemeTokens? = null
        var overlayContext: Context? = null

        val root = activity.setMaterial3UiContent(
            dynamicColorPolicy = Material3DynamicColorPolicy.Disabled,
            overlayHostFactory = { overlayRoot ->
                overlayContext = overlayRoot.context
                OverlayHostDefaults.noOp
            },
        ) {
            capturedTokens = Theme.current
            Button(text = "Action")
        }

        assertTrue(root.context is MutableContextWrapper)
        assertSame(root.context, overlayContext)
        assertSame(root.context, root.getChildAt(0).context)
        assertEquals(FrameLayout::class.java, root::class.java)
        assertEquals(0xFF2468AC.toInt(), capturedTokens?.colors?.primary)
        assertEquals(0xFF304050.toInt(), capturedTokens?.colors?.surface)
        assertEquals(0xFFF1F2F3.toInt(), capturedTokens?.colors?.onSurface)
    }

    @Test
    fun `explicit refresh reapplies runtime Android theme changes`() {
        val activity = Robolectric.buildActivity(Material3HostActivity::class.java)
            .setup()
            .get()
        val refreshController = Material3ThemeRefreshController()
        var capturedTokens: UiThemeTokens? = null

        activity.setMaterial3UiContent(
            dynamicColorPolicy = Material3DynamicColorPolicy.Disabled,
            themeRefreshController = refreshController,
            overlayHostFactory = { OverlayHostDefaults.noOp },
        ) {
            capturedTokens = Theme.current
        }
        assertEquals(0xFF2468AC.toInt(), capturedTokens?.colors?.primary)
        assertEquals(0L, capturedTokens?.metadata?.revision)

        activity.setTheme(TestR.style.ViewComposeMaterial3HostAlternateTheme)
        refreshController.refresh()
        Shadows.shadowOf(activity.mainLooper).idle()

        assertEquals(0xFFAC6824.toInt(), capturedTokens?.colors?.primary)
        assertEquals(0xFF504030.toInt(), capturedTokens?.colors?.surface)
        assertEquals(1L, capturedTokens?.metadata?.revision)
    }
}

class Material3HostActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(TestR.style.ViewComposeMaterial3HostTestTheme)
        super.onCreate(savedInstanceState)
    }
}
