package com.viewcompose.android

/*
 * 测试职责：覆盖 Android host 中的 Android Host Theme Integration 行为，防止关键契约在后续重构中回退。
 * Test responsibility: covers Android Host Theme Integration behavior in Android host and guards the contract against regressions.
 */

import android.content.Context
import android.os.Bundle
import android.view.View
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import com.viewcompose.android.test.R as TestR
import com.viewcompose.host.android.AndroidView
import com.viewcompose.material3.Material3DynamicColorPolicy
import com.viewcompose.material3.Material3ThemeRefreshController
import com.viewcompose.ui.foundation.OverlayHostDefaults
import com.viewcompose.ui.foundation.Theme
import com.viewcompose.ui.foundation.UiThemeTokens
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31])
class AndroidHostThemeIntegrationTest {
    @Test
    fun `host theme context is shared by tokens native views and overlays`() {
        val activity = Robolectric.buildActivity(ThemedHostActivity::class.java)
            .setup()
            .get()
        var capturedTokens: UiThemeTokens? = null
        var androidViewContext: Context? = null
        var overlayContext: Context? = null

        val root = activity.setUiContent(
            dynamicColorPolicy = Material3DynamicColorPolicy.Disabled,
            overlayHostFactory = { overlayRoot ->
                overlayContext = overlayRoot.context
                OverlayHostDefaults.noOp
            },
        ) {
            capturedTokens = Theme.current
            AndroidView(
                factory = { context ->
                    androidViewContext = context
                    View(context)
                },
            )
        }

        assertSame(root.context, overlayContext)
        assertSame(root.context, androidViewContext)
        assertEquals(FrameLayout::class.java, root::class.java)
        assertEquals(0xFF2468AC.toInt(), capturedTokens?.colors?.primary)
        assertEquals(0xFF304050.toInt(), capturedTokens?.colors?.surface)
        assertEquals(0xFFF1F2F3.toInt(), capturedTokens?.colors?.onSurface)
    }

    @Test
    fun `explicit refresh reapplies runtime Android theme changes`() {
        val activity = Robolectric.buildActivity(ThemedHostActivity::class.java)
            .setup()
            .get()
        val refreshController = Material3ThemeRefreshController()
        var capturedTokens: UiThemeTokens? = null

        activity.setUiContent(
            dynamicColorPolicy = Material3DynamicColorPolicy.Disabled,
            themeRefreshController = refreshController,
            overlayHostFactory = { OverlayHostDefaults.noOp },
        ) {
            capturedTokens = Theme.current
        }
        assertEquals(0xFF2468AC.toInt(), capturedTokens?.colors?.primary)
        assertEquals(0L, capturedTokens?.metadata?.revision)

        activity.setTheme(TestR.style.ViewComposeHostAlternateTheme)
        refreshController.refresh()
        Shadows.shadowOf(activity.mainLooper).idle()

        assertEquals(0xFFAC6824.toInt(), capturedTokens?.colors?.primary)
        assertEquals(0xFF504030.toInt(), capturedTokens?.colors?.surface)
        assertEquals(1L, capturedTokens?.metadata?.revision)
    }
}

class ThemedHostActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(TestR.style.ViewComposeHostTestTheme)
        super.onCreate(savedInstanceState)
    }
}
