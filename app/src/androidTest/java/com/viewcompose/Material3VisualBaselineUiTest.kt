package com.viewcompose

import android.content.res.Configuration
import android.os.Build
import android.os.SystemClock
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class Material3VisualBaselineUiTest {
    @Test
    fun phase1_defaults_captureRealRendererLightDarkAndAndroidThemeEvidence() {
        captureThemeMatrix(DemoThemeMode.Light, "light")
        captureThemeMatrix(DemoThemeMode.Dark, "dark")
        captureBaseline(
            themeMode = DemoThemeMode.System,
            themeLabel = "android-theme",
            page = Material3VisualBaselineActivity.PAGE_SURFACES,
            pageLabel = "surfaces",
        )
    }

    private fun captureThemeMatrix(
        themeMode: DemoThemeMode,
        themeLabel: String,
    ) {
        captureBaseline(
            themeMode = themeMode,
            themeLabel = themeLabel,
            page = Material3VisualBaselineActivity.PAGE_ACTIONS,
            pageLabel = "actions",
        )
        captureBaseline(
            themeMode = themeMode,
            themeLabel = themeLabel,
            page = Material3VisualBaselineActivity.PAGE_INPUTS,
            pageLabel = "inputs",
        )
        captureBaseline(
            themeMode = themeMode,
            themeLabel = themeLabel,
            page = Material3VisualBaselineActivity.PAGE_SURFACES,
            pageLabel = "surfaces",
        )
    }

    private fun captureBaseline(
        themeMode: DemoThemeMode,
        themeLabel: String,
        page: Int,
        pageLabel: String,
    ) {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val intent = Material3VisualBaselineActivity.newIntent(context, page)
        launchDemoActivity<Material3VisualBaselineActivity>(intent, themeMode).use { scenario ->
            waitForUiIdle()
            // UiDevice captures the system compositor, so wait for the Activity enter transition
            // rather than accepting an alpha-interpolated frame as visual evidence.
            SystemClock.sleep(WINDOW_TRANSITION_SETTLE_MILLIS)
            waitForUiIdle()
            scenario.onActivity { activity ->
                activity.requireViewByTestTagVisible(DemoTestTags.MATERIAL3_BASELINE_ROOT)
                when (page) {
                    Material3VisualBaselineActivity.PAGE_ACTIONS -> assertActionsVisible(activity)
                    Material3VisualBaselineActivity.PAGE_INPUTS -> assertInputsVisible(activity)
                    else -> assertSurfacesVisible(activity)
                }
            }
            val name = "material3-phase1-$themeLabel-$pageLabel"
            val screenshot = captureDeviceScreenshot(
                name = name,
                directoryName = OUTPUT_DIRECTORY,
            )
            val metadata = writeEnvironmentMetadata(
                screenshot = screenshot,
                themeLabel = themeLabel,
                pageLabel = pageLabel,
            )
            preserveAfterConnectedTest(screenshot)
            preserveAfterConnectedTest(metadata)
        }
    }

    private fun assertActionsVisible(activity: Material3VisualBaselineActivity) {
        activity.requireViewByTestTagVisible(DemoTestTags.MATERIAL3_BASELINE_ACTION_PRIMARY)
        activity.requireViewByTestTagVisible(DemoTestTags.MATERIAL3_BASELINE_ACTION_ICON)
        activity.requireViewByTestTagVisible(DemoTestTags.MATERIAL3_BASELINE_ACTION_CHIP)
        activity.requireViewByTestTagVisible(DemoTestTags.MATERIAL3_BASELINE_ACTION_CARD)
        activity.requireViewByTestTagVisible(DemoTestTags.MATERIAL3_BASELINE_ACTION_FAB)
    }

    private fun assertInputsVisible(activity: Material3VisualBaselineActivity) {
        activity.requireViewByTestTagVisible(DemoTestTags.MATERIAL3_BASELINE_INPUT_SEARCH)
        activity.requireViewByTestTagVisible(DemoTestTags.MATERIAL3_BASELINE_INPUT_FIELD)
        activity.requireViewByTestTagVisible(DemoTestTags.MATERIAL3_BASELINE_INPUT_CHECKBOX)
        activity.requireViewByTestTagVisible(DemoTestTags.MATERIAL3_BASELINE_INPUT_SWITCH)
        activity.requireViewByTestTagVisible(DemoTestTags.MATERIAL3_BASELINE_INPUT_SLIDER)
    }

    private fun assertSurfacesVisible(activity: Material3VisualBaselineActivity) {
        activity.requireViewByTestTagVisible(DemoTestTags.MATERIAL3_BASELINE_SURFACE_TYPOGRAPHY)
        activity.requireViewByTestTagVisible(DemoTestTags.MATERIAL3_BASELINE_SURFACE_PROGRESS)
        activity.requireViewByTestTagVisible(DemoTestTags.MATERIAL3_BASELINE_SURFACE_NAVIGATION)
    }

    private fun writeEnvironmentMetadata(
        screenshot: File,
        themeLabel: String,
        pageLabel: String,
    ): File {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val configuration = instrumentation.targetContext.resources.configuration
        val metrics = instrumentation.targetContext.resources.displayMetrics
        val locale = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            configuration.locales[0]
        } else {
            @Suppress("DEPRECATION")
            configuration.locale
        }
        val layoutDirection = if (configuration.layoutDirection == android.view.View.LAYOUT_DIRECTION_RTL) {
            "rtl"
        } else {
            "ltr"
        }
        val systemNightMode = when (configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) {
            Configuration.UI_MODE_NIGHT_YES -> "dark"
            Configuration.UI_MODE_NIGHT_NO -> "light"
            else -> "undefined"
        }
        return File(screenshot.parentFile, "${screenshot.nameWithoutExtension}.txt").apply {
            writeText(
                listOf(
                    "suite=material3-phase1-visual-acceptance",
                    "renderer=real-android-view",
                    "theme=$themeLabel",
                    "systemNightMode=$systemNightMode",
                    "page=$pageLabel",
                    "device=${Build.MANUFACTURER} ${Build.MODEL}",
                    "api=${Build.VERSION.SDK_INT}",
                    "densityDpi=${metrics.densityDpi}",
                    "fontScale=${configuration.fontScale}",
                    "layoutDirection=$layoutDirection",
                    "locale=${locale.toLanguageTag()}",
                    "materialComponentsBaseline=1.13.0",
                ).joinToString(separator = "\n", postfix = "\n"),
            )
        }
    }

    private fun preserveAfterConnectedTest(artifact: File) {
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        val outputDirectory = "/sdcard/Download/$PUBLIC_OUTPUT_DIRECTORY"
        device.executeShellCommand("mkdir -p $outputDirectory")
        val outputPath = "$outputDirectory/${artifact.name}"
        device.executeShellCommand("cp ${artifact.absolutePath} $outputPath")
        assertEquals(
            outputPath,
            device.executeShellCommand("ls $outputPath").trim(),
        )
    }

    private companion object {
        const val OUTPUT_DIRECTORY = "material3-visual-baseline"
        const val PUBLIC_OUTPUT_DIRECTORY = "viewcompose-material3-visual-baseline"
        const val WINDOW_TRANSITION_SETTLE_MILLIS = 750L
    }
}
