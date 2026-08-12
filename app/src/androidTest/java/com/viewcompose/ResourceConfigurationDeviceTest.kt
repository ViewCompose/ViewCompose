package com.viewcompose

import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.RecyclerView
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.roundToInt

@RunWith(AndroidJUnit4::class)
class ResourceConfigurationDeviceTest {
    @Test
    fun resourceConfigurationChangesRefreshTheSameActivityAndRoot() {
        launchDemoActivity(ResourceConfigurationActivity::class.java).use { scenario ->
            waitForUiIdle()
            var activityIdentity = 0
            var rootIdentity = 0
            var baselineDensityDpi = 0
            var baselineDimensionPx = 0
            scenario.onActivity { activity ->
                activityIdentity = System.identityHashCode(activity)
                rootIdentity = System.identityHashCode(
                    activity.requireViewByTestTagVisible(DemoTestTags.RESOURCE_CONFIGURATION_ROOT),
                )
                val facts = activity.resourceConfigurationFacts()
                val values = activity.resourceConfigurationValues()
                assertTrue(facts.contains("locale=en"))
                assertTrue(facts.contains("night=light"))
                assertTrue(facts.contains("direction=ltr"))
                assertTrue(facts.contains("fontScale=1.00"))
                baselineDensityDpi = facts.factInt("densityDpi")
                baselineDimensionPx = values.dimensionPixels()
                assertTrue(values.contains("Hello, ViewCompose"))
                assertTrue(values.contains("bool=false"))
                assertResourceConfigurationLayoutIntegrity(activity)
                activity.clickByTestTag(DemoTestTags.RESOURCE_CONFIGURATION_LANGUAGE)
            }

            waitForUiIdle()
            scenario.onActivity { activity ->
                assertStableHost(activity, activityIdentity, rootIdentity)
                assertTrue(activity.resourceConfigurationFacts().contains("locale=zh"))
                assertTrue(activity.resourceConfigurationValues().contains("你好，ViewCompose"))
                assertResourceConfigurationLayoutIntegrity(activity)
                activity.clickByTestTag(DemoTestTags.RESOURCE_CONFIGURATION_NIGHT)
            }

            waitForUiIdle()
            scenario.onActivity { activity ->
                assertStableHost(activity, activityIdentity, rootIdentity)
                assertTrue(activity.resourceConfigurationFacts().contains("night=dark"))
                assertTrue(activity.resourceConfigurationValues().contains("bool=true"))
                activity.clickByTestTag(DemoTestTags.RESOURCE_CONFIGURATION_FONT_SCALE)
            }

            waitForUiIdle()
            scenario.onActivity { activity ->
                assertStableHost(activity, activityIdentity, rootIdentity)
                assertTrue(activity.resourceConfigurationFacts().contains("fontScale=1.30"))
                assertResourceConfigurationLayoutIntegrity(activity)
                activity.clickByTestTag(DemoTestTags.RESOURCE_CONFIGURATION_DENSITY)
            }

            waitForUiIdle()
            scenario.onActivity { activity ->
                assertStableHost(activity, activityIdentity, rootIdentity)
                val facts = activity.resourceConfigurationFacts()
                val values = activity.resourceConfigurationValues()
                assertNotEquals(baselineDensityDpi, facts.factInt("densityDpi"))
                assertTrue(values.dimensionPixels() > baselineDimensionPx)
                activity.clickByTestTag(DemoTestTags.RESOURCE_CONFIGURATION_DIRECTION)
            }

            waitForUiIdle()
            scenario.onActivity { activity ->
                assertStableHost(activity, activityIdentity, rootIdentity)
                val facts = activity.resourceConfigurationFacts()
                assertTrue(facts.contains("direction=rtl"))
                assertEquals(5, facts.factInt("revision"))
            }
        }
    }

    private fun assertStableHost(
        activity: ResourceConfigurationActivity,
        activityIdentity: Int,
        rootIdentity: Int,
    ) {
        assertEquals(activityIdentity, System.identityHashCode(activity))
        assertEquals(
            rootIdentity,
            System.identityHashCode(
                activity.requireViewByTestTagVisible(DemoTestTags.RESOURCE_CONFIGURATION_ROOT),
            ),
        )
    }

    private fun assertResourceConfigurationLayoutIntegrity(
        activity: ResourceConfigurationActivity,
    ) {
        val root = activity.requireViewByTestTagVisible(
            DemoTestTags.RESOURCE_CONFIGURATION_ROOT,
        ) as RecyclerView
        val title = activity.requireTextViewByTestTagVisible(
            DemoTestTags.RESOURCE_CONFIGURATION_TITLE,
        )
        val systemBars = ViewCompat.getRootWindowInsets(root)
            ?.getInsets(WindowInsetsCompat.Type.systemBars())
            ?: androidx.core.graphics.Insets.NONE
        val horizontalGutter = (16f * root.resources.displayMetrics.density).roundToInt()

        assertEquals(systemBars.left + horizontalGutter, root.paddingLeft)
        assertEquals(systemBars.top, root.paddingTop)
        assertEquals(systemBars.right + horizontalGutter, root.paddingRight)
        assertEquals(systemBars.bottom, root.paddingBottom)
        assertNaturalLineSpacing(title)
    }

    private fun assertNaturalLineSpacing(textView: TextView) {
        val fontMetricsHeight = textView.paint.fontMetricsInt.run { descent - ascent }
        assertTrue(textView.lineSpacingExtra >= 0f)
        assertTrue(textView.lineHeight >= fontMetricsHeight)
    }

    private fun ResourceConfigurationActivity.resourceConfigurationFacts(): String {
        return requireTextViewByTestTagVisible(DemoTestTags.RESOURCE_CONFIGURATION_FACTS).text.toString()
    }

    private fun ResourceConfigurationActivity.resourceConfigurationValues(): String {
        return requireTextViewByTestTagVisible(DemoTestTags.RESOURCE_CONFIGURATION_VALUES).text.toString()
    }

    private fun String.factInt(name: String): Int {
        return substringAfter("$name=").substringBefore(';').trim().toInt()
    }

    private fun String.dimensionPixels(): Int {
        return substringAfter("dimen=").substringAfter('/').substringBefore("px").toInt()
    }
}
