package com.viewcompose

import android.os.Build
import android.os.StrictMode
import android.os.SystemClock
import android.os.strictmode.Violation
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.android.gms.maps.MapView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeFalse
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executor

@RunWith(AndroidJUnit4::class)
class GoogleMapsViewDeviceTest {
    @Test
    fun credentialFreeFixture_reportsExplicitUnsupportedStateAcrossRecreation() {
        assumeFalse(BuildConfig.VIEWCOMPOSE_MAPS_CONFIGURED)
        launchDemoScenarioActivity(
            activityClass = GoogleMapsActivity::class.java,
            scenarioId = "maps.google-map-view",
        ).use { scenario ->
            waitForUiIdle()
            assertFalse(scenario.hasNativeMapView())
            val initialStatus = scenario.readMapStatus()
            assertTrue(initialStatus.isNotBlank())

            scenario.onActivity { activity ->
                activity.clickScenarioViewById(R.id.demo_maps_google_map_view_primary_action)
                activity.clickScenarioViewById(R.id.demo_maps_google_map_view_secondary_action)
            }
            waitForUiIdle()
            assertFalse(scenario.hasNativeMapView())
            assertNotEquals(initialStatus, scenario.readMapStatus())

            scenario.onActivity { activity ->
                activity.clickScenarioViewById(R.id.demo_maps_google_map_view_reset)
            }
            waitForUiIdle()
            assertEquals(initialStatus, scenario.readMapStatus())

            scenario.recreate()
            waitForUiIdle()
            assertFalse(scenario.hasNativeMapView())
            assertEquals(initialStatus, scenario.readMapStatus())
        }
    }

    @Test
    fun credentialedFixture_retainsMapAcrossStateAndLifecycleAndRestoresAfterRecreation() {
        assumeTrue(BuildConfig.VIEWCOMPOSE_MAPS_CONFIGURED)
        assumeTrue(Build.VERSION.SDK_INT >= 31)
        val strictMode = StrictModeDeviceGate.install()
        try {
            launchDemoScenarioActivity(
                activityClass = GoogleMapsActivity::class.java,
                scenarioId = "maps.google-map-view",
            ).use { scenario ->
                waitUntil("the Google map becomes ready") {
                    scenario.readMapReadyCount() > 0
                }
                waitUntil("the Google map tiles finish loading") {
                    scenario.readMapLoadedCount() > 0
                }
                val initial = scenario.readNativeMapView()
                assertTrue(initial.context.isUiContext)
                assertEquals(1, scenario.readMapLoadedCount())
                scenario.assertMapAccessibilityDescription()

                scenario.onActivity { activity ->
                    activity.clickScenarioViewById(R.id.demo_maps_google_map_view_primary_action)
                    activity.clickScenarioViewById(R.id.demo_maps_google_map_view_secondary_action)
                }
                waitUntil("the Google map JSON style is accepted") {
                    MAP_STYLE_ACCEPTED.containsMatchIn(scenario.readMapStatus())
                }
                assertSame(initial, scenario.readNativeMapView())
                assertEquals(1, scenario.readMapLoadedCount())

                scenario.moveToState(Lifecycle.State.CREATED)
                scenario.moveToState(Lifecycle.State.RESUMED)
                waitForUiIdle()
                assertSame(initial, scenario.readNativeMapView())
                assertEquals(1, scenario.readMapLoadedCount())

                scenario.recreate()
                waitUntil("the recreated Google map becomes ready") {
                    scenario.readMapReadyCount() > 0
                }
                waitUntil("the recreated Google map tiles finish loading") {
                    scenario.readMapLoadedCount() > 0
                }
                assertEquals(1, scenario.readMapLoadedCount())
                scenario.assertMapAccessibilityDescription()
                assertNotSame(initial, scenario.readNativeMapView())
                assertNull(initial.getTag(com.viewcompose.maps.google.R.id.viewcompose_google_map_binding))
            }
        } finally {
            strictMode.restore()
        }
        strictMode.assertNoIntegrationViolations()
    }
}

private class StrictModeDeviceGate private constructor(
    private val previousThreadPolicy: StrictMode.ThreadPolicy,
    private val previousVmPolicy: StrictMode.VmPolicy,
    private val violations: CopyOnWriteArrayList<Violation>,
) {
    fun restore() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            StrictMode.setThreadPolicy(previousThreadPolicy)
        }
        StrictMode.setVmPolicy(previousVmPolicy)
    }

    fun assertNoIntegrationViolations() {
        val integrationViolations = violations.filter(::isIntegrationOwnedViolation)
        val externalSummary = violations
            .filterNot(::isIntegrationOwnedViolation)
            .groupingBy { violation -> violation.javaClass.simpleName }
            .eachCount()
            .toSortedMap()
        println(
            "StrictMode summary: total=${violations.size}, " +
                "integrationOwned=${integrationViolations.size}, external=$externalSummary",
        )
        assertTrue(
            "Integration-owned StrictMode violations were reported: " +
                integrationViolations.joinToString(separator = "\n") { violation ->
                    "$violation\n${violation.stackTrace.joinToString(separator = "\n")}"
                },
            integrationViolations.isEmpty(),
        )
    }

    companion object {
        fun install(): StrictModeDeviceGate {
            val instrumentation = InstrumentationRegistry.getInstrumentation()
            val violations = CopyOnWriteArrayList<Violation>()
            val directExecutor = Executor { command -> command.run() }
            var previousThreadPolicy: StrictMode.ThreadPolicy? = null
            instrumentation.runOnMainSync {
                previousThreadPolicy = StrictMode.getThreadPolicy()
                StrictMode.setThreadPolicy(
                    StrictMode.ThreadPolicy.Builder()
                        .detectAll()
                        .penaltyListener(directExecutor, violations::add)
                        .build(),
                )
            }
            val previousVmPolicy = StrictMode.getVmPolicy()
            StrictMode.setVmPolicy(
                StrictMode.VmPolicy.Builder()
                    .detectAll()
                    .penaltyListener(directExecutor, violations::add)
                    .build(),
            )
            return StrictModeDeviceGate(
                previousThreadPolicy = checkNotNull(previousThreadPolicy),
                previousVmPolicy = previousVmPolicy,
                violations = violations,
            )
        }
    }
}

private fun isIntegrationOwnedViolation(violation: Violation): Boolean {
    val frames = violation.stackTrace.map(StackTraceElement::getClassName)
    val firstIntegration = frames.indexOfFirst { className ->
        INTEGRATION_PACKAGES.any(className::startsWith)
    }
    if (firstIntegration < 0) return false
    val firstGoogleSdk = frames.indexOfFirst { className ->
        className.startsWith("com.google.") || className.startsWith("m140.")
    }
    return firstGoogleSdk < 0 || firstIntegration < firstGoogleSdk
}

private val INTEGRATION_PACKAGES = listOf(
    "com.viewcompose.",
)

private fun ActivityScenario<GoogleMapsActivity>.hasNativeMapView(): Boolean {
    var result = false
    onActivity { activity ->
        val target = activity.requireScenarioViewById<View>(R.id.demo_maps_google_map_view_target)
        result = findDescendant(target, MapView::class.java) != null
    }
    return result
}

private fun ActivityScenario<GoogleMapsActivity>.readNativeMapView(): MapView {
    var result: MapView? = null
    onActivity { activity ->
        val target = activity.requireScenarioViewById<View>(R.id.demo_maps_google_map_view_target)
        result = findDescendant(target, MapView::class.java)
    }
    return requireNotNull(result)
}

private fun ActivityScenario<GoogleMapsActivity>.readMapStatus(): String {
    var result = ""
    onActivity { activity ->
        result = activity.requireScenarioViewById<TextView>(
            R.id.demo_maps_google_map_view_state,
        ).text.toString()
    }
    return result
}

private fun ActivityScenario<GoogleMapsActivity>.readMapReadyCount(): Int {
    val status = readMapStatus()
    return MAP_READY_COUNT.find(status)?.groupValues?.get(1)?.toIntOrNull() ?: 0
}

private fun ActivityScenario<GoogleMapsActivity>.readMapLoadedCount(): Int {
    val status = readMapStatus()
    return MAP_LOADED_COUNT.find(status)?.groupValues?.get(1)?.toIntOrNull() ?: 0
}

private fun ActivityScenario<GoogleMapsActivity>.assertMapAccessibilityDescription() {
    onActivity { activity ->
        val target = activity.requireScenarioViewById<View>(R.id.demo_maps_google_map_view_target)
        val mapView = checkNotNull(findDescendant(target, MapView::class.java))
        val expected = activity.getString(R.string.demo_google_maps_content_description)
        assertTrue(
            "Expected the native map hierarchy to expose its content description",
            hasContentDescription(mapView, expected),
        )
    }
}

private fun waitUntil(
    description: String,
    timeoutMillis: Long = 20_000L,
    condition: () -> Boolean,
) {
    val deadline = SystemClock.uptimeMillis() + timeoutMillis
    while (SystemClock.uptimeMillis() < deadline) {
        if (condition()) return
        SystemClock.sleep(50L)
    }
    assertTrue("Timed out waiting for $description", condition())
}

private val MAP_READY_COUNT = Regex("(?:ready|就绪)\\s+(\\d+)")
private val MAP_LOADED_COUNT = Regex("(?:loaded|已加载)\\s+(\\d+)")
private val MAP_STYLE_ACCEPTED = Regex("(?:style\\s+accepted|样式\\s+已接受)")

private fun <T : View> findDescendant(root: View, type: Class<T>): T? {
    if (type.isInstance(root)) return type.cast(root)
    val group = root as? ViewGroup ?: return null
    repeat(group.childCount) { index ->
        findDescendant(group.getChildAt(index), type)?.let { return it }
    }
    return null
}

private fun hasContentDescription(root: View, expected: String): Boolean {
    if (root.contentDescription?.toString() == expected) return true
    val group = root as? ViewGroup ?: return false
    repeat(group.childCount) { index ->
        if (hasContentDescription(group.getChildAt(index), expected)) return true
    }
    return false
}
