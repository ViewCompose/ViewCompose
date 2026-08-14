package com.viewcompose

import android.app.Activity
import android.view.View
import android.view.ViewGroup
import androidx.annotation.IdRes
import androidx.core.view.WindowCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainDemoDeviceTest {
    @Test
    fun catalogHost_exposesStableTargetsAndSurvivesRecreation() {
        launchDemoActivity(MainActivity::class.java).use { scenario ->
            waitForUiIdle()
            scenario.onActivity { activity ->
                activity.requireVisibleView(R.id.demo_catalog_root)
                activity.requireVisibleView(R.id.demo_catalog_ready)
                activity.requireVisibleView(R.id.demo_catalog_target)
            }

            scenario.recreate()
            waitForUiIdle()

            scenario.onActivity { activity ->
                activity.requireVisibleView(R.id.demo_catalog_root)
                activity.requireVisibleView(R.id.demo_catalog_ready)
            }
        }
    }

    @Test
    fun catalogToolbar_opensEnvironmentAndGeneratedBuildFacts() {
        launchDemoActivity(MainActivity::class.java).use { scenario ->
            waitForUiIdle()
            val instrumentation = InstrumentationRegistry.getInstrumentation()

            val environmentMonitor = instrumentation.addMonitor(
                DemoEnvironmentActivity::class.java.name,
                null,
                false,
            )
            try {
                scenario.onActivity { activity ->
                    activity.clickView(R.id.demo_catalog_primary_action)
                }
                val environment = instrumentation.waitForMonitorWithTimeout(environmentMonitor, 5_000)
                assertNotNull("Expected the Environment panel", environment)
                environment?.finish()
                waitForUiIdle()
            } finally {
                instrumentation.removeMonitor(environmentMonitor)
            }

            val buildMonitor = instrumentation.addMonitor(
                DemoBuildInfoActivity::class.java.name,
                null,
                false,
            )
            try {
                scenario.onActivity { activity ->
                    activity.clickView(R.id.demo_catalog_secondary_action)
                }
                val buildInfo = instrumentation.waitForMonitorWithTimeout(buildMonitor, 5_000)
                assertNotNull("Expected generated build information", buildInfo)
                buildInfo?.finish()
            } finally {
                instrumentation.removeMonitor(buildMonitor)
            }
        }
    }

    @Test
    fun explicitThemeSwitch_updatesEnvironmentAndSystemBarAppearance() {
        launchDemoActivity(
            DemoEnvironmentActivity::class.java,
            themeMode = DemoThemeMode.Dark,
        ).use { scenario ->
            waitForUiIdle()
            scenario.onActivity { activity ->
                assertTheme(activity, DemoThemeMode.Dark)
                activity.clickSegment(DemoTestTags.SETTINGS_THEME_CONTROL, index = 1)
            }
            waitForUiIdle()
            scenario.onActivity { activity ->
                assertTheme(activity, DemoThemeMode.Light)
                activity.clickSegment(DemoTestTags.SETTINGS_THEME_CONTROL, index = 2)
            }
            waitForUiIdle()
            scenario.onActivity { activity ->
                assertTheme(activity, DemoThemeMode.Dark)
            }
        }
    }

    @Test
    fun secondaryActivityThemeSwitch_refreshesEnvironmentSession() {
        launchDemoActivity(
            DemoEnvironmentActivity::class.java,
            themeMode = DemoThemeMode.Light,
        ).use { scenario ->
            waitForUiIdle()
            scenario.onActivity { activity ->
                assertTheme(activity, DemoThemeMode.Light)
            }

            val instrumentation = InstrumentationRegistry.getInstrumentation()
            val monitor = instrumentation.addMonitor(
                ThemeSwitchActivity::class.java.name,
                null,
                false,
            )
            try {
                scenario.onActivity { activity ->
                    activity.clickByTestTag(DemoTestTags.SETTINGS_CROSS_ACTIVITY_THEME_ENTRY)
                }
                val secondary = instrumentation.waitForMonitorWithTimeout(monitor, 5_000)
                assertNotNull("Expected ThemeSwitchActivity to launch", secondary)
                waitForUiIdle()

                instrumentation.runOnMainSync {
                    val secondaryActivity = checkNotNull(secondary)
                    secondaryActivity.clickSegment(
                        DemoTestTags.THEME_SWITCH_SECONDARY_CONTROL,
                        index = 2,
                    )
                    secondaryActivity.clickByTestTag(DemoTestTags.THEME_SWITCH_SECONDARY_RETURN)
                }
                waitForUiIdle()

                scenario.onActivity { activity ->
                    assertEquals(DemoThemeMode.Dark, DemoThemeSession.mode)
                    assertTheme(activity, DemoThemeMode.Dark)
                }
            } finally {
                instrumentation.removeMonitor(monitor)
            }
        }
    }

    @Test
    fun catalogLaunch_usesStrictScenarioRoute() {
        launchDemoActivity(MainActivity::class.java).use { scenario ->
            waitForUiIdle()
            val instrumentation = InstrumentationRegistry.getInstrumentation()
            val monitor = instrumentation.addMonitor(StateActivity::class.java.name, null, false)
            try {
                scenario.onActivity { activity ->
                    activity.clickByTestTag("demo.catalog.launch.runtime.state")
                }
                val launched = instrumentation.waitForMonitorWithTimeout(monitor, 5_000)
                assertNotNull("Expected runtime.state to launch StateActivity", launched)
                waitForUiIdle()
                checkNotNull(launched).apply {
                    requireVisibleView(R.id.demo_runtime_state_root)
                    requireVisibleView(R.id.demo_runtime_state_ready)
                    finish()
                }
            } finally {
                instrumentation.removeMonitor(monitor)
            }
        }
    }

    private fun assertTheme(
        activity: Activity,
        mode: DemoThemeMode,
    ) {
        val expected = DemoThemeTokens.select(
            mode = mode,
            isSystemDark = DemoThemeTokens.isSystemDark(activity),
        )
        val status = activity.requireTextViewByTestTagVisible(DemoTestTags.SETTINGS_THEME_STATUS)
        assertTrue(status.text.toString().contains(mode.name))
        assertEquals(expected.colors.onSurfaceVariant, status.currentTextColor)
        val controller = WindowCompat.getInsetsController(activity.window, activity.window.decorView)
        val isDark = expected.metadata.isDark == true
        assertEquals(!isDark, controller.isAppearanceLightStatusBars)
        assertEquals(!isDark, controller.isAppearanceLightNavigationBars)
    }
}

private fun Activity.requireVisibleView(@IdRes id: Int): View {
    val view = findViewById<View>(id)
    assertNotNull("Expected Android resource target: $id", view)
    assertTrue("Expected target to be shown: $id", view!!.isShown)
    return view
}

private fun Activity.clickView(@IdRes id: Int) {
    val view = requireVisibleView(id)
    assertTrue("Expected click to be handled: $id", view.performClick())
}

private fun Activity.clickSegment(
    testTag: String,
    index: Int,
) {
    val control = requireViewByTestTagVisible(testTag)
    assertTrue("Expected segmented control host", control is ViewGroup)
    val group = control as ViewGroup
    assertTrue("Expected segment index $index in ${group.childCount} children", index < group.childCount)
    assertTrue("Expected segment click to be handled", group.getChildAt(index).performClick())
}
