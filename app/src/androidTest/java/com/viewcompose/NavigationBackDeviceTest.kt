package com.viewcompose

import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import androidx.activity.BackEventCompat
import androidx.activity.ExperimentalActivityApi
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import com.viewcompose.navigation.NavResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.abs

@OptIn(ExperimentalActivityApi::class)
@RunWith(AndroidJUnit4::class)
class NavigationBackDeviceTest {
    private lateinit var device: UiDevice

    @Before
    fun prepareDevice() {
        device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        device.wakeUp()
        device.executeShellCommand("wm dismiss-keyguard")
        device.waitForIdle()
    }

    @Test
    fun systemBackPopsNavigationThenDelegatesAtRoot() {
        launchHost().use { scenario ->
            scenario.onActivity { activity ->
                assertTrue(activity.push(NavigationBackTestActivity.DETAILS_ROUTE) is NavResult.Committed)
                assertEquals(
                    listOf(
                        NavigationBackTestActivity.HOME_ROUTE,
                        NavigationBackTestActivity.DETAILS_ROUTE,
                    ),
                    activity.routeNames(),
                )
            }

            device.pressBack()
            waitForUiIdle()

            scenario.onActivity { activity ->
                assertEquals(
                    listOf(NavigationBackTestActivity.HOME_ROUTE),
                    activity.routeNames(),
                )
                assertEquals(0, activity.delegatedBackCount)
            }

            device.pressBack()
            waitForUiIdle()

            scenario.onActivity { activity ->
                assertEquals(1, activity.delegatedBackCount)
                assertEquals(
                    listOf(NavigationBackTestActivity.HOME_ROUTE),
                    activity.routeNames(),
                )
                assertEquals(0, activity.failureCount)
            }
        }
    }

    @Test
    fun predictiveProgressCancellationAndCommitDriveRealViews() {
        launchHost().use { scenario ->
            scenario.onActivity { activity ->
                activity.push(NavigationBackTestActivity.DETAILS_ROUTE)
            }
            awaitTransition()

            scenario.onActivity { activity ->
                activity.onBackPressedDispatcher.dispatchOnBackStarted(backEvent(0f))
                activity.onBackPressedDispatcher.dispatchOnBackProgressed(backEvent(0.5f))

                val home = activity.destinationContainer(NavigationBackTestActivity.HOME_ROUTE)
                val details = activity.destinationContainer(NavigationBackTestActivity.DETAILS_ROUTE)
                assertEquals(View.VISIBLE, home.visibility)
                assertEquals(View.VISIBLE, details.visibility)
                assertTrue(home.translationX < 0f)
                assertTrue(details.translationX > 0f)
                assertTrue(home.alpha in 0f..1f)
                assertTrue(details.alpha in 0f..1f)
                assertEquals(
                    listOf(
                        NavigationBackTestActivity.HOME_ROUTE,
                        NavigationBackTestActivity.DETAILS_ROUTE,
                    ),
                    activity.routeNames(),
                )

                activity.onBackPressedDispatcher.dispatchOnBackCancelled()

                assertEquals(View.GONE, home.visibility)
                assertEquals(View.VISIBLE, details.visibility)
                assertEquals(0f, home.translationX, 0f)
                assertEquals(0f, details.translationX, 0f)
                assertEquals(1f, home.alpha, 0f)
                assertEquals(1f, details.alpha, 0f)

                activity.onBackPressedDispatcher.dispatchOnBackStarted(backEvent(0f))
                activity.onBackPressedDispatcher.dispatchOnBackProgressed(backEvent(0.65f))
                activity.onBackPressedDispatcher.onBackPressed()

                assertEquals(
                    listOf(NavigationBackTestActivity.HOME_ROUTE),
                    activity.routeNames(),
                )
            }
            awaitTransition()

            scenario.onActivity { activity ->
                assertNull(
                    activity.findDestinationTextViewOrNull(
                        NavigationBackTestActivity.DETAILS_ROUTE,
                    ),
                )
                assertEquals(
                    View.VISIBLE,
                    activity.destinationContainer(
                        NavigationBackTestActivity.HOME_ROUTE,
                    ).visibility,
                )
                assertEquals(0, activity.failureCount)
            }
        }
    }

    @SdkSuppress(minSdkVersion = 34)
    @Test
    fun platformEdgeGestureProgressAndCancellationDriveRealViews() {
        assumeGestureNavigation()
        assumeExternalCancellationGesture()
        launchHost().use { scenario ->
            scenario.onActivity { activity ->
                activity.push(NavigationBackTestActivity.DETAILS_ROUTE)
            }
            awaitTransition()

            val samples = sampleDestinationViewsDuring(scenario) {
                device.executeShellCommand(
                    "log -t $EXTERNAL_GESTURE_LOG_TAG $EXTERNAL_GESTURE_READY_MESSAGE",
                )
                SystemClock.sleep(EXTERNAL_GESTURE_WINDOW_MILLIS)
            }
            awaitTransition()

            assertTrue(
                samples.predictiveProgressFailureMessage(),
                samples.any { sample -> sample.showsPredictiveProgress() },
            )
            scenario.onActivity { activity ->
                val home = activity.destinationContainer(NavigationBackTestActivity.HOME_ROUTE)
                val details = activity.destinationContainer(NavigationBackTestActivity.DETAILS_ROUTE)

                assertEquals(
                    listOf(
                        NavigationBackTestActivity.HOME_ROUTE,
                        NavigationBackTestActivity.DETAILS_ROUTE,
                    ),
                    activity.routeNames(),
                )
                assertEquals(View.GONE, home.visibility)
                assertEquals(View.VISIBLE, details.visibility)
                assertEquals(0f, home.translationX, 0f)
                assertEquals(0f, details.translationX, 0f)
                assertEquals(1f, home.alpha, 0f)
                assertEquals(1f, details.alpha, 0f)
                assertEquals(0, activity.failureCount)
            }
        }
    }

    @SdkSuppress(minSdkVersion = 34)
    @Test
    fun platformEdgeGestureProgressAndCommitPopTheStack() {
        assumeGestureNavigation()
        launchHost().use { scenario ->
            scenario.onActivity { activity ->
                activity.push(NavigationBackTestActivity.DETAILS_ROUTE)
            }
            awaitTransition()

            val centerY = device.displayHeight / 2
            val endX = (device.displayWidth * COMMIT_GESTURE_WIDTH_FRACTION).toInt()
            val samples = sampleDestinationViewsDuring(scenario) {
                device.executeShellCommand(
                    "input touchscreen swipe " +
                        "$EDGE_GESTURE_START_X $centerY " +
                        "$endX $centerY " +
                        "$EDGE_GESTURE_DURATION_MILLIS",
                )
            }
            awaitTransition()

            assertTrue(
                samples.predictiveProgressFailureMessage(),
                samples.any { sample -> sample.showsPredictiveProgress() },
            )
            scenario.onActivity { activity ->
                assertEquals(
                    listOf(NavigationBackTestActivity.HOME_ROUTE),
                    activity.routeNames(),
                )
                assertNull(
                    activity.findDestinationTextViewOrNull(
                        NavigationBackTestActivity.DETAILS_ROUTE,
                    ),
                )
                assertEquals(
                    View.VISIBLE,
                    activity.destinationContainer(
                        NavigationBackTestActivity.HOME_ROUTE,
                    ).visibility,
                )
                assertEquals(0, activity.failureCount)
            }
        }
    }

    @Test
    fun programmaticNavigationRedirectsPredictivePreview() {
        launchHost().use { scenario ->
            scenario.onActivity { activity ->
                activity.push(NavigationBackTestActivity.DETAILS_ROUTE)
            }
            awaitTransition()

            scenario.onActivity { activity ->
                activity.onBackPressedDispatcher.dispatchOnBackStarted(backEvent(0f))
                activity.onBackPressedDispatcher.dispatchOnBackProgressed(backEvent(0.45f))

                assertTrue(
                    activity.push(NavigationBackTestActivity.CONFIRMATION_ROUTE) is
                        NavResult.Committed,
                )
                assertEquals(
                    listOf(
                        NavigationBackTestActivity.HOME_ROUTE,
                        NavigationBackTestActivity.DETAILS_ROUTE,
                        NavigationBackTestActivity.CONFIRMATION_ROUTE,
                    ),
                    activity.routeNames(),
                )
                // Finish the synthetic gesture cycle before asking Android to dispatch a new,
                // physical Back event. A real platform gesture always terminates with either
                // cancellation or commit; mixing a dispatcher-only start with a separate key
                // event leaves AndroidX's platform bridge in an impossible state.
                activity.onBackPressedDispatcher.dispatchOnBackCancelled()
            }
            awaitTransition()

            device.pressBack()
            waitForUiIdle()

            scenario.onActivity { activity ->
                assertEquals(
                    listOf(
                        NavigationBackTestActivity.HOME_ROUTE,
                        NavigationBackTestActivity.DETAILS_ROUTE,
                    ),
                    activity.routeNames(),
                )
                assertEquals(0, activity.failureCount)
            }
        }
    }

    @Test
    fun systemBackEnablementChangesWithoutReplacingStack() {
        launchHost().use { scenario ->
            scenario.onActivity { activity ->
                activity.push(NavigationBackTestActivity.DETAILS_ROUTE)
                activity.setSystemBackEnabled(false)
            }
            waitForUiIdle()

            device.pressBack()
            waitForUiIdle()

            scenario.onActivity { activity ->
                assertEquals(1, activity.delegatedBackCount)
                assertEquals(
                    listOf(
                        NavigationBackTestActivity.HOME_ROUTE,
                        NavigationBackTestActivity.DETAILS_ROUTE,
                    ),
                    activity.routeNames(),
                )
                activity.setSystemBackEnabled(true)
            }
            waitForUiIdle()

            device.pressBack()
            waitForUiIdle()

            scenario.onActivity { activity ->
                assertEquals(1, activity.delegatedBackCount)
                assertEquals(
                    listOf(NavigationBackTestActivity.HOME_ROUTE),
                    activity.routeNames(),
                )
            }
        }
    }

    @Test
    fun activityRecreationRestoresStackBeforeSystemBack() {
        launchHost().use { scenario ->
            var expectedEntryIds: List<String> = emptyList()
            scenario.onActivity { activity ->
                activity.push(NavigationBackTestActivity.DETAILS_ROUTE)
                expectedEntryIds = activity.entryIds()
            }
            awaitTransition()

            scenario.recreate()
            waitForUiIdle()

            scenario.onActivity { activity ->
                assertEquals(expectedEntryIds, activity.entryIds())
                assertEquals(
                    listOf(
                        NavigationBackTestActivity.HOME_ROUTE,
                        NavigationBackTestActivity.DETAILS_ROUTE,
                    ),
                    activity.routeNames(),
                )
            }

            device.pressBack()
            waitForUiIdle()

            scenario.onActivity { activity ->
                assertEquals(
                    listOf(NavigationBackTestActivity.HOME_ROUTE),
                    activity.routeNames(),
                )
                assertEquals(0, activity.failureCount)
            }
        }
    }

    @Test
    fun repeatedPushAndImmediateSystemBackRemainTransactional() {
        launchHost().use { scenario ->
            repeat(STRESS_ITERATIONS) { index ->
                scenario.onActivity { activity ->
                    assertTrue(activity.push("stress-$index") is NavResult.Committed)
                }

                device.pressBack()
                waitForUiIdle()

                scenario.onActivity { activity ->
                    assertEquals(
                        "iteration=$index",
                        listOf(NavigationBackTestActivity.HOME_ROUTE),
                        activity.routeNames(),
                    )
                    assertEquals("iteration=$index", 0, activity.failureCount)
                }
            }
            awaitTransition()
        }
    }

    private fun launchHost(): ActivityScenario<NavigationBackTestActivity> {
        return ActivityScenario.launch(NavigationBackTestActivity::class.java).also { scenario ->
            scenario.moveToState(Lifecycle.State.RESUMED)
            waitForUiIdle()
        }
    }

    private fun backEvent(progress: Float): BackEventCompat {
        return BackEventCompat(
            touchX = 0f,
            touchY = 500f,
            progress = progress,
            swipeEdge = BackEventCompat.EDGE_LEFT,
            frameTimeMillis = SystemClock.uptimeMillis(),
        )
    }

    private fun assumeGestureNavigation() {
        val navigationMode = device.executeShellCommand(
            "settings get secure navigation_mode",
        ).trim()
        assumeTrue(
            "Platform edge-gesture tests require gesture navigation; navigation_mode=$navigationMode",
            navigationMode == GESTURE_NAVIGATION_MODE,
        )
    }

    private fun assumeExternalCancellationGesture() {
        val enabled = InstrumentationRegistry.getArguments()
            .getString(EXTERNAL_GESTURE_ARGUMENT)
            .toBoolean()
        assumeTrue(
            "Platform cancellation requires the emulator host gesture runner; " +
                "run tools/navigation/validate_android_predictive_back.sh.",
            enabled,
        )
    }

    private fun sampleDestinationViewsDuring(
        scenario: ActivityScenario<NavigationBackTestActivity>,
        block: () -> Unit,
    ): List<NavigationBackTestActivity.DestinationViewSample> {
        scenario.onActivity(NavigationBackTestActivity::beginDestinationViewSampling)
        var samples = emptyList<NavigationBackTestActivity.DestinationViewSample>()
        try {
            block()
        } finally {
            scenario.onActivity { activity ->
                samples = activity.endDestinationViewSampling()
            }
        }
        return samples
    }

    private fun NavigationBackTestActivity.destinationContainer(
        routeName: String,
    ): View {
        val textView = checkNotNull(findDestinationTextViewOrNull(routeName)) {
            "No destination text found for route '$routeName'."
        }
        return textView.parent as View
    }

    private fun NavigationBackTestActivity.findDestinationTextViewOrNull(
        routeName: String,
    ): View? {
        val root = findViewById<ViewGroup>(android.R.id.content)
        return findTextViewByText(
            root = root,
            text = NavigationBackTestActivity.destinationText(routeName),
        )
    }

    private fun awaitTransition() {
        SystemClock.sleep(NavigationBackTestActivity.TRANSITION_DURATION_MILLIS + 80L)
        waitForUiIdle()
    }

    private fun NavigationBackTestActivity.DestinationViewSample.showsPredictiveProgress(): Boolean {
        return homeVisibility == View.VISIBLE &&
            detailsVisibility == View.VISIBLE &&
            abs(homeTranslationX) > MIN_GESTURE_TRANSLATION_PX &&
            abs(detailsTranslationX) > MIN_GESTURE_TRANSLATION_PX &&
            homeAlpha in 0f..1f &&
            detailsAlpha in 0f..1f
    }

    private fun List<NavigationBackTestActivity.DestinationViewSample>
        .predictiveProgressFailureMessage(): String {
        val maxHomeTranslation = maxOfOrNull { sample -> abs(sample.homeTranslationX) } ?: 0f
        val maxDetailsTranslation = maxOfOrNull { sample -> abs(sample.detailsTranslationX) } ?: 0f
        val bothVisibleFrames = count { sample ->
            sample.homeVisibility == View.VISIBLE &&
                sample.detailsVisibility == View.VISIBLE
        }
        return "Android did not deliver predictive-back progress to the destination Views: " +
            "samples=$size, bothVisibleFrames=$bothVisibleFrames, " +
            "maxHomeTranslation=$maxHomeTranslation, " +
            "maxDetailsTranslation=$maxDetailsTranslation."
    }

    companion object {
        private const val STRESS_ITERATIONS = 30
        private const val GESTURE_NAVIGATION_MODE = "2"
        private const val EDGE_GESTURE_START_X = 1
        private const val EDGE_GESTURE_DURATION_MILLIS = 1_200
        private const val EXTERNAL_GESTURE_ARGUMENT = "platformPredictiveBackCancel"
        private const val EXTERNAL_GESTURE_LOG_TAG = "ViewComposeNavigationBack"
        private const val EXTERNAL_GESTURE_READY_MESSAGE = "READY_FOR_CANCEL_GESTURE"
        private const val EXTERNAL_GESTURE_WINDOW_MILLIS = 15_000L
        private const val COMMIT_GESTURE_WIDTH_FRACTION = 0.72f
        private const val MIN_GESTURE_TRANSLATION_PX = 1f
    }
}
