package com.viewcompose

import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.graphics.Rect
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.floor

/**
 * 自适应导航窗格在真机上的生命周期和布局验证。
 * Real-device lifecycle and layout validation for adaptive navigation panes.
 */
@RunWith(AndroidJUnit4::class)
class NavigationAdaptivePaneDeviceTest {
    @Test
    fun rotation_reuses_entry_owners_and_adapts_native_panes_to_available_width() {
        ActivityScenario.launch(NavigationAdaptivePaneTestActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            }
            awaitState(scenario) { activity ->
                activity.routeNames() == Routes &&
                    activity.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE &&
                    activity.visibleRouteNames().let { visibleRoutes ->
                        visibleRoutes == activity.expectedVisibleRoutes(Routes) &&
                            activity.hasLifecycleProjection(Routes, visibleRoutes)
                    }
            }
            var landscapeRoutes = emptyList<String>()
            scenario.onActivity { activity ->
                landscapeRoutes = activity.visibleRouteNames()
                assertOrderedNonOverlapping(
                    landscapeRoutes.map { route ->
                        checkNotNull(activity.destinationBounds(route))
                    },
                )
            }

            scenario.onActivity { activity ->
                activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            }
            awaitState(scenario) { activity ->
                activity.routeNames() == Routes &&
                    activity.resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT &&
                    activity.visibleRouteNames().let { visibleRoutes ->
                        visibleRoutes == activity.expectedVisibleRoutes(Routes) &&
                            visibleRoutes.size <= landscapeRoutes.size &&
                            activity.hasLifecycleProjection(Routes, visibleRoutes)
                    }
            }

            scenario.onActivity { activity ->
                activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            }
            awaitState(scenario) { activity ->
                activity.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE &&
                    activity.visibleRouteNames() == landscapeRoutes &&
                    activity.hasLifecycleProjection(Routes, landscapeRoutes)
            }

            scenario.onActivity { activity ->
                assertTrue(activity.pop())
            }
            val remainingRoutes = listOf(HOME_ROUTE, DETAILS_ROUTE)
            awaitState(scenario) { activity ->
                activity.routeNames() == remainingRoutes &&
                    activity.visibleRouteNames().let { visibleRoutes ->
                        visibleRoutes == activity.expectedVisibleRoutes(remainingRoutes) &&
                            activity.hasLifecycleProjection(remainingRoutes, visibleRoutes)
                    }
            }
        }
    }

    private fun NavigationAdaptivePaneTestActivity.expectedVisibleRoutes(
        activeRoutes: List<String>,
    ): List<String> {
        val widthPixels = findViewById<android.view.View>(android.R.id.content).width
        if (widthPixels <= 0) return emptyList()
        val density = resources.displayMetrics.density
        val widthDp = widthPixels / density
        val paneCount = floor(
            (widthDp + NavigationAdaptivePaneTestActivity.PANE_SPACING_DP) /
                (NavigationAdaptivePaneTestActivity.MIN_PANE_WIDTH_DP +
                    NavigationAdaptivePaneTestActivity.PANE_SPACING_DP),
        ).toInt().coerceIn(1, NavigationAdaptivePaneTestActivity.MAX_PANE_COUNT)
        return activeRoutes.takeLast(paneCount.coerceAtMost(activeRoutes.size))
    }

    private fun NavigationAdaptivePaneTestActivity.hasLifecycleProjection(
        activeRoutes: List<String>,
        visibleRoutes: List<String>,
    ): Boolean {
        return activeRoutes.all { route ->
            lifecycleState(route) == if (route in visibleRoutes) {
                Lifecycle.State.RESUMED
            } else {
                Lifecycle.State.CREATED
            }
        }
    }

    /**
     * 轮询 Activity 状态，等待异步导航和旋转布局都完成。
     * Polls Activity state until asynchronous navigation and rotation layout settle.
     */
    private fun awaitState(
        scenario: ActivityScenario<NavigationAdaptivePaneTestActivity>,
        predicate: (NavigationAdaptivePaneTestActivity) -> Boolean,
    ) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        repeat(120) {
            instrumentation.waitForIdleSync()
            var matched = false
            scenario.onActivity { activity ->
                matched = predicate(activity)
            }
            if (matched) {
                return
            }
            Thread.sleep(50)
        }
        scenario.onActivity { activity ->
            throw AssertionError(
                "Timed out waiting for adaptive pane state. " +
                    "routes=${activity.routeNames()}, " +
                    "visible=${activity.visibleRouteNames()}, " +
                    "lifecycles=${Routes.associateWith(activity::lifecycleState)}",
            )
        }
    }

    /**
     * 断言当前可见窗格从左到右排列且互不重叠。
     * Asserts that the visible panes are ordered left-to-right without overlap.
     */
    private fun assertOrderedNonOverlapping(bounds: List<Rect>) {
        assertTrue(bounds.isNotEmpty())
        bounds.zipWithNext().forEach { (left, right) ->
            assertTrue(left.width() > 0)
            assertTrue(right.width() > 0)
            assertTrue(left.right < right.left)
            assertEquals(left.top, right.top)
            assertEquals(left.bottom, right.bottom)
        }
    }

    private companion object {
        const val HOME_ROUTE = "adaptive-home"
        const val DETAILS_ROUTE = "adaptive-details"
        const val CONFIRMATION_ROUTE = "adaptive-confirmation"
        val Routes = listOf(
            HOME_ROUTE,
            DETAILS_ROUTE,
            CONFIRMATION_ROUTE,
        )
    }
}
