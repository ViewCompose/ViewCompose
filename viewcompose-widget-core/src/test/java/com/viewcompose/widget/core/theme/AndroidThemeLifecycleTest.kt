package com.viewcompose.widget.core

import android.content.Context
import android.content.res.Configuration
import android.view.ContextThemeWrapper
import com.google.android.material.color.DynamicColors
import com.viewcompose.ui.shape.UiCornerFamily
import com.viewcompose.ui.shape.UiCornerSize
import com.viewcompose.widget.core.test.R as TestR
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31])
class AndroidThemeLifecycleTest {
    @Test
    fun `dynamic color policy records the resolved token origin`() {
        val tokens = AndroidThemeBridge.fromContext(themedContext())
        val expectedOrigin = if (DynamicColors.isDynamicColorAvailable()) {
            UiThemeOrigin.AndroidDynamicColor
        } else {
            UiThemeOrigin.AndroidTheme
        }

        assertEquals(expectedOrigin, tokens.metadata.origin)
    }

    @Test
    fun `android shape appearance preserves corner family size and percentage`() {
        val context = themedContext()

        val tokens = AndroidThemeBridge.fromContext(
            context = context,
            dynamicColorPolicy = AndroidDynamicColorPolicy.Disabled,
        )

        assertEquals(UiCornerFamily.Cut, tokens.shapes.small.topStart.family)
        assertEquals(UiCornerSize.Absolute(12), tokens.shapes.small.topStart.size)
        assertEquals(UiCornerFamily.Rounded, tokens.shapes.small.topEnd.family)
        assertEquals(UiCornerSize.Relative(0.5f), tokens.shapes.small.topEnd.size)
        assertEquals(UiCornerFamily.Cut, tokens.shapes.small.bottomStart.family)
        assertEquals(UiCornerSize.Absolute(20), tokens.shapes.small.bottomStart.size)
        assertEquals(UiThemeOrigin.AndroidTheme, tokens.metadata.origin)
    }

    @Test
    fun `configuration refresh advances token revision without retaining callbacks after close`() {
        val context = themedContext()
        val lifecycle = AndroidThemeTokenLifecycle(
            context = context,
            dynamicColorPolicy = AndroidDynamicColorPolicy.Disabled,
        )

        assertEquals(0L, lifecycle.tokens.value.metadata.revision)
        lifecycle.start()
        lifecycle.start()
        lifecycle.onConfigurationChanged(Configuration(context.resources.configuration))

        assertEquals(1L, lifecycle.tokens.value.metadata.revision)
        assertEquals(UiThemeOrigin.AndroidTheme, lifecycle.tokens.value.metadata.origin)

        lifecycle.close()
        lifecycle.close()
    }

    private fun themedContext(): Context {
        return ContextThemeWrapper(
            RuntimeEnvironment.getApplication(),
            TestR.style.ViewComposeTestTheme,
        )
    }
}
