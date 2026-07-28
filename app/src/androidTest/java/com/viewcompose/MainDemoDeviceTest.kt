package com.viewcompose

import androidx.core.view.WindowCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainDemoDeviceTest {
    @Test
    fun explicitThemeSwitch_updatesLazyContentAndSystemBarAppearance() {
        launchDemoActivity(MainActivity::class.java, themeMode = DemoThemeMode.Dark).use { scenario ->
            waitForUiIdle()
            clickDeviceText("设置")
            scenario.onActivity { activity ->
                assertEquals(
                    DemoThemeTokens.dark.colors.onSurface,
                    activity.requireTextView("主题切换").currentTextColor,
                )
                val controller = WindowCompat.getInsetsController(
                    activity.window,
                    activity.window.decorView,
                )
                assertFalse(controller.isAppearanceLightStatusBars)
                assertFalse(controller.isAppearanceLightNavigationBars)
            }

            clickDeviceText("Light")
            scenario.onActivity { activity ->
                assertEquals(
                    DemoThemeTokens.light.colors.onSurface,
                    activity.requireTextView("主题切换").currentTextColor,
                )
                val controller = WindowCompat.getInsetsController(
                    activity.window,
                    activity.window.decorView,
                )
                assertTrue(controller.isAppearanceLightStatusBars)
                assertTrue(controller.isAppearanceLightNavigationBars)
            }

            clickDeviceText("Dark")
            scenario.onActivity { activity ->
                assertEquals(
                    DemoThemeTokens.dark.colors.onSurface,
                    activity.requireTextView("主题切换").currentTextColor,
                )
                val controller = WindowCompat.getInsetsController(
                    activity.window,
                    activity.window.decorView,
                )
                assertFalse(controller.isAppearanceLightStatusBars)
                assertFalse(controller.isAppearanceLightNavigationBars)
            }
        }
    }

    @Test
    fun activityRecreation_preservesSelectedHomeTab() {
        launchDemoActivity(MainActivity::class.java, themeMode = DemoThemeMode.Light).use { scenario ->
            waitForUiIdle()
            clickDeviceText("设置")
            assertDeviceTextVisible("主题切换")

            scenario.recreate()
            waitForUiIdle()

            assertDeviceTextVisible("主题切换")
            scenario.onActivity { activity ->
                assertViewCompletelyVisible(
                    activity.requireViewByTestTagVisible(DemoTestTags.HOME_NAVIGATION_BAR),
                )
            }
        }
    }

    @Test
    fun catalogLaunchAndThemeSwitch_keepMainDemoUsable() {
        launchDemoActivity(MainActivity::class.java, themeMode = DemoThemeMode.System).use { scenario ->
            waitForUiIdle()
            scenario.onActivity { activity ->
                assertViewCompletelyVisible(
                    activity.requireViewByTestTagVisible(DemoTestTags.HOME_NAVIGATION_BAR),
                )
            }

            clickDeviceText("设置")
            clickDeviceText("Dark")
            scenario.onActivity { activity ->
                assertViewCompletelyVisible(
                    activity.requireViewByTestTagVisible(DemoTestTags.HOME_NAVIGATION_BAR),
                )
            }

            clickDeviceText("目录")
            val instrumentation = InstrumentationRegistry.getInstrumentation()
            val monitor = instrumentation.addMonitor(
                WidgetShowcaseActivity::class.java.name,
                null,
                false,
            )
            try {
                scenario.onActivity { activity ->
                    activity.clickByTestTag(DemoTestTags.catalogModuleButton("widget_showcase"))
                }
                val launchedActivity = instrumentation.waitForMonitorWithTimeout(monitor, 5_000)
                assertNotNull("Expected catalog button to launch WidgetShowcaseActivity", launchedActivity)
                launchedActivity?.finish()
            } finally {
                instrumentation.removeMonitor(monitor)
            }
        }
    }
}
