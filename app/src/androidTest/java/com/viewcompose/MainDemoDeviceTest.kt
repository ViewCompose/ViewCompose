package com.viewcompose

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainDemoDeviceTest {
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
