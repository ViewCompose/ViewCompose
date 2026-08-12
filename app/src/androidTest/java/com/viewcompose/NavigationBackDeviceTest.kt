package com.viewcompose

import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.os.SystemClock
import android.util.Log
import android.util.SparseIntArray
import android.view.View
import android.view.ViewGroup
import androidx.activity.BackEventCompat
import androidx.activity.ExperimentalActivityApi
import androidx.core.app.FrameMetricsAggregator
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import com.viewcompose.navigation.NavResult
import com.viewcompose.navigation.NavTransitionSpec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.abs
import kotlin.math.ceil

/**
 * 系统 Back、predictive Back 和导航事务性的设备级验证。
 * Device-level validation for system Back, predictive Back, and navigation transactions.
 */
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
                assertEquals(1f, home.alpha, 0f)
                assertEquals(1f, details.alpha, 0f)
                assertTrue(home.scaleX in 0.9f..1f)
                assertTrue(details.scaleX in 0.9f..1f)
                assertTrue(home.scaleX < 1f)
                assertTrue(details.scaleX < 1f)
                assertTrue(home.foreground is ColorDrawable)
                assertTrue((home.foreground as ColorDrawable).alpha > 0)
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
                assertEquals(1f, home.alpha, 0f)
                assertNull(home.foreground)
            }
            awaitBackCancellation()

            scenario.onActivity { activity ->
                val home = activity.destinationContainer(NavigationBackTestActivity.HOME_ROUTE)
                val details = activity.destinationContainer(NavigationBackTestActivity.DETAILS_ROUTE)
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
                assertNull(
                    activity.destinationContainer(
                        NavigationBackTestActivity.HOME_ROUTE,
                    ).foreground,
                )
                assertEquals(0, activity.failureCount)
            }
        }
    }

    @Test
    fun committedPushAndPopFollowPlatformLayeringWithoutDoubleExposure() {
        launchHost().use { scenario ->
            val pushSamples = sampleDestinationViewsDuring(scenario) {
                scenario.onActivity { activity ->
                    activity.push(NavigationBackTestActivity.DETAILS_ROUTE)
                }
                awaitTransition()
            }

            assertPlatformTransitionSamples(
                phase = "push",
                samples = pushSamples,
                fadingRoute = NavigationBackTestActivity.DETAILS_ROUTE,
            )
            scenario.onActivity { activity ->
                val home = activity.destinationContainer(NavigationBackTestActivity.HOME_ROUTE)
                val details = activity.destinationContainer(NavigationBackTestActivity.DETAILS_ROUTE)
                assertEquals(
                    255,
                    checkNotNull(home.background).alpha,
                )
                assertEquals(
                    255,
                    checkNotNull(details.background).alpha,
                )
            }

            val popSamples = sampleDestinationViewsDuring(scenario) {
                scenario.onActivity { activity ->
                    activity.navController.popBackStack()
                }
                awaitTransition()
            }

            assertPlatformTransitionSamples(
                phase = "pop",
                samples = popSamples,
                fadingRoute = NavigationBackTestActivity.DETAILS_ROUTE,
            )
        }
    }

    @SdkSuppress(minSdkVersion = 24)
    @Test
    fun predictiveBackCancellationFrameTimingStaysWithinDeviceBudget() {
        launchHost().use { scenario ->
            scenario.onActivity { activity ->
                activity.push(NavigationBackTestActivity.DETAILS_ROUTE)
            }
            awaitTransition()

            dispatchPredictiveCancellation(scenario)
            awaitBackCancellation()

            val aggregator = FrameMetricsAggregator(FrameMetricsAggregator.TOTAL_DURATION)
            var refreshRateHz = 60f
            scenario.onActivity { activity ->
                @Suppress("DEPRECATION")
                val display = activity.windowManager.defaultDisplay
                refreshRateHz = display.refreshRate.takeIf { rate -> rate > 0f } ?: 60f
                aggregator.add(activity)
            }

            repeat(FRAME_TIMING_ITERATIONS) {
                dispatchPredictiveCancellation(scenario)
                awaitBackCancellation()
            }

            var metrics: Array<SparseIntArray>? = null
            scenario.onActivity { activity ->
                metrics = aggregator.remove(activity)
            }
            val summary = checkNotNull(metrics)
                .getOrNull(FrameMetricsAggregator.TOTAL_INDEX)
                .toFrameTimingSummary(refreshRateHz)
            Log.i(FRAME_TIMING_LOG_TAG, summary.description)

            assertTrue(
                "${summary.description}; expected at least $MIN_MEASURED_FRAMES frames",
                summary.frameCount >= MIN_MEASURED_FRAMES,
            )
            if (!isEmulator()) {
                assertTrue(
                    "${summary.description}; P95 exceeded ${summary.maxP95Millis}ms",
                    summary.p95Millis <= summary.maxP95Millis,
                )
                assertTrue(
                    "${summary.description}; severe-frame ratio exceeded " +
                        "$MAX_SEVERE_FRAME_RATIO",
                    summary.severeFrameRatio <= MAX_SEVERE_FRAME_RATIO,
                )
            }
        }
    }

    @SdkSuppress(minSdkVersion = 34)
    @Test
    fun platformEdgeGestureProgressAndCancellationDriveRealViews() {
        assumeGestureNavigation()
        assumeExternalPlatformGestureRunner()
        launchHost().use { scenario ->
            scenario.onActivity { activity ->
                activity.push(NavigationBackTestActivity.DETAILS_ROUTE)
            }
            awaitTransition()

            val samples = sampleDestinationViewsDuring(scenario) {
                device.executeShellCommand(
                    "log -t $EXTERNAL_GESTURE_LOG_TAG $EXTERNAL_CANCEL_GESTURE_READY_MESSAGE",
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
        assumeExternalPlatformGestureRunner()
        launchHost().use { scenario ->
            scenario.onActivity { activity ->
                activity.push(NavigationBackTestActivity.DETAILS_ROUTE)
            }
            awaitTransition()

            val samples = sampleDestinationViewsDuring(scenario) {
                device.executeShellCommand(
                    "log -t $EXTERNAL_GESTURE_LOG_TAG $EXTERNAL_COMMIT_GESTURE_READY_MESSAGE",
                )
                SystemClock.sleep(EXTERNAL_GESTURE_WINDOW_MILLIS)
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
                // 先结束合成手势周期，再让 Android 分发新的物理 Back 事件。
                // Finish the synthetic gesture cycle before asking Android to dispatch a new physical Back event.
                // 真实平台手势一定会以取消或提交结束；dispatcher-only start 混用独立按键事件会让 AndroidX bridge 状态不可能成立。
                // A real platform gesture always ends with cancellation or commit; mixing a dispatcher-only
                // start with a separate key event leaves AndroidX's platform bridge in an impossible state.
                activity.onBackPressedDispatcher.dispatchOnBackCancelled()
            }
            awaitTransition()

            scenario.onActivity { activity ->
                val details = activity.destinationContainer(
                    NavigationBackTestActivity.DETAILS_ROUTE,
                )
                val confirmation = activity.destinationContainer(
                    NavigationBackTestActivity.CONFIRMATION_ROUTE,
                )
                assertEquals(View.GONE, details.visibility)
                assertEquals(View.VISIBLE, confirmation.visibility)
                assertEquals(0f, details.translationX, 0f)
                assertEquals(0f, confirmation.translationX, 0f)
                assertEquals(1f, details.alpha, 0f)
                assertEquals(1f, confirmation.alpha, 0f)
            }

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
    fun lifecycleStopAndRecreationDuringActiveTransitionsPreserveCommittedStack() {
        launchHost().use { scenario ->
            var detailsEntryIds: List<String> = emptyList()
            scenario.onActivity { activity ->
                assertTrue(
                    activity.push(NavigationBackTestActivity.DETAILS_ROUTE) is
                        NavResult.Committed,
                )
                detailsEntryIds = activity.entryIds()
            }

            scenario.moveToState(Lifecycle.State.CREATED)
            scenario.moveToState(Lifecycle.State.RESUMED)
            awaitTransition()

            scenario.onActivity { activity ->
                assertEquals(detailsEntryIds, activity.entryIds())
                assertEquals(
                    listOf(
                        NavigationBackTestActivity.HOME_ROUTE,
                        NavigationBackTestActivity.DETAILS_ROUTE,
                    ),
                    activity.routeNames(),
                )
                assertEquals(0, activity.failureCount)
            }

            var confirmationEntryIds: List<String> = emptyList()
            scenario.onActivity { activity ->
                assertTrue(
                    activity.push(NavigationBackTestActivity.CONFIRMATION_ROUTE) is
                        NavResult.Committed,
                )
                confirmationEntryIds = activity.entryIds()
            }

            scenario.recreate()
            waitForUiIdle()

            scenario.onActivity { activity ->
                assertEquals(confirmationEntryIds, activity.entryIds())
                assertEquals(
                    listOf(
                        NavigationBackTestActivity.HOME_ROUTE,
                        NavigationBackTestActivity.DETAILS_ROUTE,
                        NavigationBackTestActivity.CONFIRMATION_ROUTE,
                    ),
                    activity.routeNames(),
                )
                assertEquals(0, activity.failureCount)
            }

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
    fun repeatedPushAndImmediateBackDispatchRemainTransactional() {
        launchHost().use { scenario ->
            repeat(STRESS_ITERATIONS) { index ->
                scenario.onActivity { activity ->
                    assertTrue(activity.push("stress-$index") is NavResult.Committed)
                    activity.onBackPressedDispatcher.onBackPressed()
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

    /**
     * 启动导航 Back 测试宿主并等待首帧稳定。
     * Launches the navigation Back test host and waits for the first stable frame.
     */
    private fun launchHost(): ActivityScenario<NavigationBackTestActivity> {
        return ActivityScenario.launch(NavigationBackTestActivity::class.java).also { scenario ->
            scenario.moveToState(Lifecycle.State.RESUMED)
            waitForUiIdle()
        }
    }

    private fun backEvent(progress: Float): BackEventCompat {
        return backEvent(
            progress = progress,
            frameTimeMillis = SystemClock.uptimeMillis(),
        )
    }

    private fun backEvent(
        progress: Float,
        frameTimeMillis: Long,
    ): BackEventCompat {
        return BackEventCompat(
            touchX = 0f,
            touchY = 500f,
            progress = progress,
            swipeEdge = BackEventCompat.EDGE_LEFT,
            frameTimeMillis = frameTimeMillis,
        )
    }

    /**
     * 合成一段 predictive Back 取消手势。
     * Synthesizes a predictive Back cancellation gesture.
     */
    private fun dispatchPredictiveCancellation(
        scenario: ActivityScenario<NavigationBackTestActivity>,
    ) {
        scenario.onActivity { activity ->
            val startTimeMillis = SystemClock.uptimeMillis()
            activity.onBackPressedDispatcher.dispatchOnBackStarted(
                backEvent(
                    progress = 0f,
                    frameTimeMillis = startTimeMillis,
                ),
            )
            listOf(0.2f, 0.45f, 0.7f).forEachIndexed { index, progress ->
                activity.onBackPressedDispatcher.dispatchOnBackProgressed(
                    backEvent(
                        progress = progress,
                        frameTimeMillis = startTimeMillis + (index + 1) * 16L,
                    ),
                )
            }
            activity.onBackPressedDispatcher.dispatchOnBackCancelled()
        }
    }

    private fun isEmulator(): Boolean {
        return Build.FINGERPRINT.startsWith("generic") ||
            Build.FINGERPRINT.contains("emulator") ||
            Build.HARDWARE.contains("goldfish") ||
            Build.HARDWARE.contains("ranchu")
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

    private fun assumeExternalPlatformGestureRunner() {
        val enabled = InstrumentationRegistry.getArguments()
            .getString(EXTERNAL_GESTURE_ARGUMENT)
            .toBoolean()
        assumeTrue(
            "Platform predictive-back gestures require the emulator host gesture runner; " +
                "run tools/navigation/validate_android_predictive_back.sh.",
            enabled,
        )
    }

    /**
     * 在指定代码块执行期间采样 destination View 属性。
     * Samples destination View properties while the given block runs.
     */
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

    /**
     * 等待常规导航转场结束。
     * Waits for a regular navigation transition to settle.
     */
    private fun awaitTransition() {
        SystemClock.sleep(NavigationBackTestActivity.TRANSITION_DURATION_MILLIS + 80L)
        waitForUiIdle()
    }

    /**
     * 等待 predictive Back 取消弹簧结束。
     * Waits for the predictive Back cancellation spring to settle.
     */
    private fun awaitBackCancellation() {
        SystemClock.sleep(
            NavTransitionSpec.Default.predictiveBack.cancelSpring.maxDurationMillis + 80L,
        )
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

    /**
     * 断言系统 Activity 转场采样具备重叠帧和单 surface 淡出行为。
     * Asserts that platform transition samples contain overlap frames and one-surface fade behavior.
     */
    private fun assertPlatformTransitionSamples(
        phase: String,
        samples: List<NavigationBackTestActivity.DestinationViewSample>,
        fadingRoute: String,
    ) {
        val overlappingSamples = samples.filter { sample ->
            sample.homeVisibility == View.VISIBLE &&
                sample.detailsVisibility == View.VISIBLE
        }
        assertTrue(
            "$phase transition did not expose overlapping destination frames; samples=${samples.size}",
            overlappingSamples.isNotEmpty(),
        )
        assertTrue(
            "$phase transition made both destination surfaces translucent: $overlappingSamples",
            overlappingSamples.all { sample ->
                (sample.homeAlpha == 1f || sample.detailsAlpha == 1f) &&
                    sample.homeScaleX == 1f &&
                    sample.detailsScaleX == 1f
            },
        )
        val fadingSamples = when (fadingRoute) {
            NavigationBackTestActivity.HOME_ROUTE -> overlappingSamples.map { it.homeAlpha }
            NavigationBackTestActivity.DETAILS_ROUTE -> overlappingSamples.map { it.detailsAlpha }
            else -> error("Unsupported fading route '$fadingRoute'.")
        }
        assertTrue(
            "$phase transition did not capture the platform fade phase: $overlappingSamples",
            fadingSamples.any { alpha -> alpha < 1f },
        )
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

    /**
     * 将 FrameMetricsAggregator 的直方图转换成测试使用的帧耗时摘要。
     * Converts a FrameMetricsAggregator histogram into the frame-timing summary used by tests.
     */
    private fun SparseIntArray?.toFrameTimingSummary(
        refreshRateHz: Float,
    ): FrameTimingSummary {
        requireNotNull(this) {
            "FrameMetricsAggregator did not return total-duration metrics."
        }
        val frameCount = (0 until size()).sumOf { index -> valueAt(index) }
        require(frameCount > 0) {
            "FrameMetricsAggregator did not capture any predictive-back frames."
        }
        val p95Target = ceil(frameCount * 0.95).toInt()
        var cumulativeFrames = 0
        var p95Millis = 0
        for (index in 0 until size()) {
            cumulativeFrames += valueAt(index)
            if (cumulativeFrames >= p95Target) {
                p95Millis = keyAt(index)
                break
            }
        }
        val frameBudgetMillis = 1_000f / refreshRateHz
        val severeFrameThresholdMillis = maxOf(
            MIN_SEVERE_FRAME_MILLIS,
            ceil(frameBudgetMillis * 2f).toInt(),
        )
        val severeFrameCount = (0 until size()).sumOf { index ->
            if (keyAt(index) > severeFrameThresholdMillis) valueAt(index) else 0
        }
        return FrameTimingSummary(
            refreshRateHz = refreshRateHz,
            frameCount = frameCount,
            p95Millis = p95Millis,
            maxP95Millis = maxOf(
                MIN_MAX_P95_MILLIS,
                ceil(frameBudgetMillis * 3f).toInt(),
            ),
            severeFrameThresholdMillis = severeFrameThresholdMillis,
            severeFrameCount = severeFrameCount,
        )
    }

    private data class FrameTimingSummary(
        val refreshRateHz: Float,
        val frameCount: Int,
        val p95Millis: Int,
        val maxP95Millis: Int,
        val severeFrameThresholdMillis: Int,
        val severeFrameCount: Int,
    ) {
        val severeFrameRatio: Float
            get() = severeFrameCount.toFloat() / frameCount

        val description: String
            get() = "predictive-back frame timing: refreshRate=${refreshRateHz}Hz, " +
                "frames=$frameCount, p95=${p95Millis}ms, " +
                "severe(>${severeFrameThresholdMillis}ms)=" +
                "$severeFrameCount (${severeFrameRatio * 100f}%)"
    }

    companion object {
        private const val STRESS_ITERATIONS = 30
        private const val FRAME_TIMING_ITERATIONS = 6
        private const val MIN_MEASURED_FRAMES = 30
        private const val MIN_SEVERE_FRAME_MILLIS = 32
        private const val MIN_MAX_P95_MILLIS = 48
        private const val MAX_SEVERE_FRAME_RATIO = 0.2f
        private const val FRAME_TIMING_LOG_TAG = "ViewComposeNavFrames"
        private const val GESTURE_NAVIGATION_MODE = "2"
        private const val EXTERNAL_GESTURE_ARGUMENT = "platformPredictiveBackGesture"
        private const val EXTERNAL_GESTURE_LOG_TAG = "ViewComposeNavigationBack"
        private const val EXTERNAL_CANCEL_GESTURE_READY_MESSAGE = "READY_FOR_CANCEL_GESTURE"
        private const val EXTERNAL_COMMIT_GESTURE_READY_MESSAGE = "READY_FOR_COMMIT_GESTURE"
        private const val EXTERNAL_GESTURE_WINDOW_MILLIS = 15_000L
        private const val MIN_GESTURE_TRANSLATION_PX = 1f
    }
}
