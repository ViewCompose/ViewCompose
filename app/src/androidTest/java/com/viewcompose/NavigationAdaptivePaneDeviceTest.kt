package com.viewcompose

import android.content.pm.ActivityInfo
import android.graphics.Rect
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NavigationAdaptivePaneDeviceTest {
    @Test
    fun rotation_reuses_entry_owners_and_adapts_between_one_and_three_native_panes() {
        ActivityScenario.launch(NavigationAdaptivePaneTestActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            }
            awaitState(scenario) { activity ->
                activity.routeNames() == Routes &&
                    activity.visibleRouteNames() == Routes &&
                    Routes.all { route ->
                        activity.lifecycleState(route) == Lifecycle.State.RESUMED
                    }
            }
            scenario.onActivity { activity ->
                assertOrderedNonOverlapping(
                    Routes.map { route ->
                        checkNotNull(activity.destinationBounds(route))
                    },
                )
            }

            scenario.onActivity { activity ->
                activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            }
            awaitState(scenario) { activity ->
                activity.routeNames() == Routes &&
                    activity.visibleRouteNames() == listOf(CONFIRMATION_ROUTE) &&
                    activity.lifecycleState(HOME_ROUTE) == Lifecycle.State.CREATED &&
                    activity.lifecycleState(DETAILS_ROUTE) == Lifecycle.State.CREATED &&
                    activity.lifecycleState(CONFIRMATION_ROUTE) == Lifecycle.State.RESUMED
            }

            scenario.onActivity { activity ->
                activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            }
            awaitState(scenario) { activity ->
                activity.visibleRouteNames() == Routes &&
                    Routes.all { route ->
                        activity.lifecycleState(route) == Lifecycle.State.RESUMED
                    }
            }

            scenario.onActivity { activity ->
                assertTrue(activity.pop())
            }
            awaitState(scenario) { activity ->
                activity.routeNames() == listOf(HOME_ROUTE, DETAILS_ROUTE) &&
                    activity.visibleRouteNames() == listOf(HOME_ROUTE, DETAILS_ROUTE) &&
                    activity.lifecycleState(HOME_ROUTE) == Lifecycle.State.RESUMED &&
                    activity.lifecycleState(DETAILS_ROUTE) == Lifecycle.State.RESUMED
            }
        }
    }

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

    private fun assertOrderedNonOverlapping(bounds: List<Rect>) {
        assertEquals(3, bounds.size)
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
