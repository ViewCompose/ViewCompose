package com.viewcompose.benchmark

import androidx.benchmark.macro.BaselineProfileMode
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Android Window/SurfaceControl 驱动 Activity 转场的参考测量。
 * Reference measurements for Android's Window/SurfaceControl-backed Activity transition.
 *
 * 它与 [NavigationMotionBenchmark] 放在一起，便于在同一目标应用和设备上对比框架页内转场与系统合成器转场。
 * These are intentionally kept beside [NavigationMotionBenchmark] so reports compare the
 * framework's in-window page transition with the system compositor on the same target and device.
 */
@RunWith(AndroidJUnit4::class)
class ActivityNavigationMotionBenchmark {
    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    @Test
    fun enterSystemNavigationActivityAfterProfileGuidedCompilation() =
        benchmarkRule.measureRepeated(
            packageName = TARGET_PACKAGE,
            metrics = listOf(FrameTimingMetric()),
            compilationMode = profileGuidedCompilation(),
            iterations = activityMotionIterations(),
            startupMode = StartupMode.WARM,
            setupBlock = {
                startDemoAndWait()
            },
        ) {
            startSystemNavigationActivityFromForeground()
            waitForNavigationMotion()
            waitForScenarioTarget(NAVIGATION_SCENARIO, DemoTargetRole.Ready)
        }

    @Test
    fun exitSystemNavigationActivityAfterProfileGuidedCompilation() =
        benchmarkRule.measureRepeated(
            packageName = TARGET_PACKAGE,
            metrics = listOf(FrameTimingMetric()),
            compilationMode = profileGuidedCompilation(),
            iterations = activityMotionIterations(),
            startupMode = StartupMode.WARM,
            setupBlock = {
                startDemoAndWait()
                startSystemNavigationActivityFromForeground()
                waitForNavigationMotion()
                waitForScenarioTarget(NAVIGATION_SCENARIO, DemoTargetRole.Ready)
            },
        ) {
            device.pressBack()
            waitForNavigationMotion()
            waitForScenarioTarget("catalog", DemoTargetRole.Ready)
        }

    private companion object {
        const val NAVIGATION_SCENARIO = "navigation.system"
        const val ITERATIONS_ARGUMENT = "activityNavigationMotionIterations"
        const val PROFILE_WARMUP_ITERATIONS = 3

        fun activityMotionIterations(): Int {
            return InstrumentationRegistry.getArguments()
                .getString(ITERATIONS_ARGUMENT)
                ?.toIntOrNull()
                ?.takeIf { it > 0 }
                ?: FORMAL_INTERACTION_ITERATIONS
        }

        fun profileGuidedCompilation(): CompilationMode {
            return CompilationMode.Partial(
                baselineProfileMode = BaselineProfileMode.Disable,
                warmupIterations = PROFILE_WARMUP_ITERATIONS,
            )
        }
    }
}
