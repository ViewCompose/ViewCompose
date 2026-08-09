package com.viewcompose.android

/*
 * 测试职责：覆盖 Android host 中的 Android Host Theme Integration 行为，防止关键契约在后续重构中回退。
 * Test responsibility: covers Android Host Theme Integration behavior in Android host and guards the contract against regressions.
 */

import android.content.Context
import android.content.MutableContextWrapper
import android.os.Bundle
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.SeekBar
import android.widget.Switch
import androidx.activity.ComponentActivity
import com.viewcompose.android.test.R as TestR
import com.viewcompose.material3.Material3DynamicColorPolicy
import com.viewcompose.material3.Material3ThemeDefaults
import com.viewcompose.material3.Material3ThemeRefreshController
import com.viewcompose.oneui7.OneUi7Theme
import com.viewcompose.oneui7.OneUi7ThemeDefaults
import com.viewcompose.text.TextFieldState
import com.viewcompose.ui.foundation.BasicTextField
import com.viewcompose.ui.foundation.Button as UiButton
import com.viewcompose.ui.foundation.OverlayHostDefaults
import com.viewcompose.ui.foundation.SegmentedControl
import com.viewcompose.ui.foundation.Slider
import com.viewcompose.ui.foundation.Switch as UiSwitch
import com.viewcompose.ui.foundation.Theme
import com.viewcompose.ui.foundation.UiTheme
import com.viewcompose.ui.foundation.UiThemeOverride
import com.viewcompose.ui.foundation.UiTreeBuilder
import com.viewcompose.ui.foundation.UiThemeTokens
import com.viewcompose.ui.shape.UiShape
import com.viewcompose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
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
        var overlayContext: Context? = null

        val root = activity.setUiContent(
            dynamicColorPolicy = Material3DynamicColorPolicy.Disabled,
            overlayHostFactory = { overlayRoot ->
                overlayContext = overlayRoot.context
                OverlayHostDefaults.noOp
            },
        ) {
            capturedTokens = Theme.current
            UiButton(text = "Action")
        }

        assertSame(root.context, overlayContext)
        assertSame(root.context, root.getChildAt(0).context)
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

    @Test
    fun `current host keeps Material context while nested design tokens change`() {
        val expectedXmlPrimary = 0xFF2468AC.toInt()
        val snapshots = HostFixtureMode.entries.map(::renderFixture)

        snapshots.forEach { snapshot ->
            assertTrue(snapshot.root.context is MutableContextWrapper)
            assertEquals(expectedXmlPrimary, snapshot.androidContextPrimary)
            assertTrue(snapshot.views.isNotEmpty())
            snapshot.views.forEach { view -> assertSame(snapshot.root.context, view.context) }
            assertTrue(snapshot.views.any { view -> view is Button })
            assertTrue(snapshot.views.any { view -> view is Switch })
            assertTrue(snapshot.views.any { view -> view is SeekBar })
            assertTrue(snapshot.views.any { view -> view is EditText })
            assertTrue(
                snapshot.views.any { view ->
                    view.javaClass.simpleName == "DeclarativeSegmentedControlLayout"
                },
            )
        }

        assertEquals(expectedXmlPrimary, snapshots[HostFixtureMode.AndroidXml.ordinal].tokens.colors.primary)
        assertEquals(
            Material3ThemeDefaults.light().colors.primary,
            snapshots[HostFixtureMode.StaticMaterial.ordinal].tokens.colors.primary,
        )
        assertEquals(
            OneUi7ThemeDefaults.light().colors.primary,
            snapshots[HostFixtureMode.StaticOneUi.ordinal].tokens.colors.primary,
        )
        assertEquals(
            APPLICATION_OVERRIDE_PRIMARY,
            snapshots[HostFixtureMode.ApplicationOverride.ordinal].tokens.colors.primary,
        )
        assertNotEquals(
            snapshots[HostFixtureMode.StaticMaterial.ordinal].tokens.colors.primary,
            snapshots[HostFixtureMode.StaticMaterial.ordinal].androidContextPrimary,
        )
        assertNotEquals(
            snapshots[HostFixtureMode.StaticOneUi.ordinal].tokens.colors.primary,
            snapshots[HostFixtureMode.StaticOneUi.ordinal].androidContextPrimary,
        )
        assertNotEquals(
            snapshots[HostFixtureMode.ApplicationOverride.ordinal].tokens.shapes.medium,
            snapshots[HostFixtureMode.StaticMaterial.ordinal].tokens.shapes.medium,
        )
    }

    private fun renderFixture(mode: HostFixtureMode): HostFixtureSnapshot {
        val activity = Robolectric.buildActivity(ThemedHostActivity::class.java)
            .setup()
            .get()
        var capturedTokens: UiThemeTokens? = null
        val root = activity.setUiContent(
            dynamicColorPolicy = Material3DynamicColorPolicy.Disabled,
            overlayHostFactory = { OverlayHostDefaults.noOp },
        ) {
            provideFixtureTheme(mode) {
                capturedTokens = Theme.current
                UiButton(text = "Action")
                UiSwitch(text = "Switch", checked = true, onCheckedChange = {})
                Slider(value = 40, onValueChange = {})
                BasicTextField(state = TextFieldState())
                SegmentedControl(
                    items = listOf("Day", "Week"),
                    selectedIndex = 0,
                    onSelectionChange = {},
                )
            }
        }
        val views = root.descendants()
        return HostFixtureSnapshot(
            root = root,
            tokens = requireNotNull(capturedTokens),
            androidContextPrimary = root.context.resolveColorAttribute(
                androidx.appcompat.R.attr.colorPrimary,
            ),
            views = views,
        )
    }

    private fun UiTreeBuilder.provideFixtureTheme(
        mode: HostFixtureMode,
        content: UiTreeBuilder.() -> Unit,
    ) {
        when (mode) {
            HostFixtureMode.AndroidXml -> content()
            HostFixtureMode.StaticMaterial -> UiTheme(Material3ThemeDefaults.light(), content)
            HostFixtureMode.StaticOneUi -> OneUi7Theme(OneUi7ThemeDefaults.light(), content)
            HostFixtureMode.ApplicationOverride -> {
                val base = Material3ThemeDefaults.light()
                UiTheme(base) {
                    UiThemeOverride(
                        colors = base.colors.copy(primary = APPLICATION_OVERRIDE_PRIMARY),
                        shapes = base.shapes.copy(medium = UiShape.rounded(31.dp)),
                        content = content,
                    )
                }
            }
        }
    }

    private fun ViewGroup.descendants(): List<View> = buildList {
        for (index in 0 until childCount) {
            val child = getChildAt(index)
            add(child)
            if (child is ViewGroup) addAll(child.descendants())
        }
    }

    private fun Context.resolveColorAttribute(attribute: Int): Int {
        val value = TypedValue()
        check(theme.resolveAttribute(attribute, value, true)) {
            "Expected theme attribute 0x${attribute.toString(16)}"
        }
        return value.data
    }

    private enum class HostFixtureMode {
        AndroidXml,
        StaticMaterial,
        StaticOneUi,
        ApplicationOverride,
    }

    private data class HostFixtureSnapshot(
        val root: ViewGroup,
        val tokens: UiThemeTokens,
        val androidContextPrimary: Int,
        val views: List<View>,
    )

    private companion object {
        const val APPLICATION_OVERRIDE_PRIMARY: Int = 0xFFB00020.toInt()
    }
}

class ThemedHostActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(TestR.style.ViewComposeHostTestTheme)
        super.onCreate(savedInstanceState)
    }
}
