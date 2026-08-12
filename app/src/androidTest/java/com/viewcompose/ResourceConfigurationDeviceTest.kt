package com.viewcompose

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

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
                activity.clickByTestTag(DemoTestTags.RESOURCE_CONFIGURATION_LANGUAGE)
            }

            waitForUiIdle()
            scenario.onActivity { activity ->
                assertStableHost(activity, activityIdentity, rootIdentity)
                assertTrue(activity.resourceConfigurationFacts().contains("locale=zh"))
                assertTrue(activity.resourceConfigurationValues().contains("你好，ViewCompose"))
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
