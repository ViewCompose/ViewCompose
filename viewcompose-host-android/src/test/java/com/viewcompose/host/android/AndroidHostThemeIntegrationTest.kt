package com.viewcompose.host.android

import android.content.Context
import android.os.Bundle
import android.view.View
import androidx.activity.ComponentActivity
import com.viewcompose.host.android.test.R as TestR
import com.viewcompose.widget.core.AndroidDynamicColorPolicy
import com.viewcompose.widget.core.AndroidThemeRefreshController
import com.viewcompose.widget.core.OverlayHostDefaults
import com.viewcompose.widget.core.Theme
import com.viewcompose.widget.core.UiThemeTokens
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
            dynamicColorPolicy = AndroidDynamicColorPolicy.Disabled,
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
        assertEquals(0xFF2468AC.toInt(), capturedTokens?.colors?.primary)
        assertEquals(0xFF304050.toInt(), capturedTokens?.colors?.surface)
        assertEquals(0xFFF1F2F3.toInt(), capturedTokens?.colors?.onSurface)
    }

    @Test
    fun `explicit refresh reapplies runtime Android theme changes`() {
        val activity = Robolectric.buildActivity(ThemedHostActivity::class.java)
            .setup()
            .get()
        val refreshController = AndroidThemeRefreshController()
        var capturedTokens: UiThemeTokens? = null

        activity.setUiContent(
            dynamicColorPolicy = AndroidDynamicColorPolicy.Disabled,
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
