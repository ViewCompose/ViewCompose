package com.viewcompose.android

/*
 * 测试职责：覆盖中立 Android Host 的 Context 与主题边界，防止通用入口重新隐式选择设计系统。
 * Test responsibility: covers neutral Android host context and theme boundaries and prevents the
 * generally named entry point from selecting a design system again.
 */

import android.content.Context
import android.content.MutableContextWrapper
import android.os.Bundle
import android.os.LocaleList
import android.content.res.Configuration
import android.view.ContextThemeWrapper
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import com.viewcompose.android.test.R as TestR
import com.viewcompose.oneui7.OneUi7Theme
import com.viewcompose.oneui7.OneUi7ThemeDefaults
import com.viewcompose.host.android.resources.AndroidResourceRefreshController
import com.viewcompose.host.android.resources.stringResource
import com.viewcompose.ui.foundation.Button
import com.viewcompose.ui.foundation.OverlayHostDefaults
import com.viewcompose.ui.foundation.Theme
import com.viewcompose.ui.foundation.UiThemeDefaults
import com.viewcompose.ui.foundation.UiThemeTokens
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [24, 31, 35])
class AndroidHostThemeIntegrationTest {
    @Test
    fun `neutral host preserves receiver context and framework defaults`() {
        val activity = Robolectric.buildActivity(NeutralHostActivity::class.java)
            .setup()
            .get()
        var capturedTokens: UiThemeTokens? = null
        var overlayContext: Context? = null

        val root = activity.setUiContent(
            overlayHostFactory = { overlayRoot ->
                overlayContext = overlayRoot.context
                OverlayHostDefaults.noOp
            },
        ) {
            capturedTokens = Theme.current
            Button(text = "Action")
        }

        assertSame(activity, root.context)
        assertSame(root.context, overlayContext)
        assertSame(root.context, root.getChildAt(0).context)
        assertEquals(FrameLayout::class.java, root::class.java)
        assertEquals(UiThemeDefaults.light(), capturedTokens)
    }

    @Test
    fun `explicit root context is shared by root native views and overlays`() {
        val activity = Robolectric.buildActivity(NeutralHostActivity::class.java)
            .setup()
            .get()
        val resolvedContext = ContextThemeWrapper(activity, TestR.style.ViewComposeHostAlternateTheme)
        var overlayContext: Context? = null

        val firstRoot = activity.setUiContent {
            Button(text = "First")
        }
        val secondRoot = activity.setUiContent(
            rootContext = resolvedContext,
            overlayHostFactory = { overlayRoot ->
                overlayContext = overlayRoot.context
                OverlayHostDefaults.noOp
            },
        ) {
            Button(text = "Second")
        }

        assertNotSame(firstRoot, secondRoot)
        assertSame(resolvedContext, secondRoot.context)
        assertSame(resolvedContext, overlayContext)
        assertSame(resolvedContext, secondRoot.getChildAt(0).context)
    }

    @Test
    fun `static One UI root has no implicit Material context wrapper`() {
        val activity = Robolectric.buildActivity(NeutralHostActivity::class.java)
            .setup()
            .get()
        var capturedTokens: UiThemeTokens? = null

        val root = activity.setUiContent(
            overlayHostFactory = { OverlayHostDefaults.noOp },
        ) {
            OneUi7Theme {
                capturedTokens = Theme.current
                Button(text = "One UI action")
            }
        }

        assertSame(activity, root.context)
        assertFalse(root.context is MutableContextWrapper)
        assertSame(root.context, root.getChildAt(0).context)
        assertEquals(OneUi7ThemeDefaults.light(), capturedTokens)
    }

    @Test
    fun `neutral host refreshes Android resources without reinstalling content`() {
        val activity = Robolectric.buildActivity(NeutralHostActivity::class.java)
            .setup()
            .get()
        val englishContext = activity.createConfigurationContext(
            Configuration(activity.resources.configuration).apply {
                setLocales(LocaleList(Locale.forLanguageTag("en-US")))
            },
        )
        val chineseContext = activity.createConfigurationContext(
            Configuration(activity.resources.configuration).apply {
                setLocales(LocaleList(Locale.forLanguageTag("zh-CN")))
            },
        )
        val stableContext = MutableContextWrapper(englishContext)
        val refreshController = AndroidResourceRefreshController()
        var pendingContext: Context? = null
        var title = ""

        val root = activity.setUiContent(
            rootContext = stableContext,
            resourceRefreshController = refreshController,
            onBeforeResourceRefresh = {
                pendingContext?.let(stableContext::setBaseContext)
            },
        ) {
            title = stringResource(TestR.string.resource_host_title)
            Button(text = title)
        }

        val originalButton = root.getChildAt(0)
        assertEquals("Resource host", title)

        pendingContext = chineseContext
        refreshController.refresh()
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()

        assertEquals("资源 Host", title)
        assertSame(originalButton, root.getChildAt(0))
    }
}

class NeutralHostActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(TestR.style.ViewComposeHostTestTheme)
        super.onCreate(savedInstanceState)
    }
}
