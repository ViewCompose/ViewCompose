package com.viewcompose

import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.graphics.drawable.InsetDrawable
import android.graphics.drawable.LayerDrawable
import android.graphics.drawable.RippleDrawable
import android.view.View
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.math.roundToInt

@RunWith(AndroidJUnit4::class)
class Material3TouchTargetBaselineUiTest {
    @Test
    fun settingsEntry_opensDefaultMaterial3ThemeWithoutDemoTokens() {
        launchDemoActivity(MainActivity::class.java, DemoThemeMode.Light).use { scenario ->
            clickDeviceText("设置")
            val instrumentation = InstrumentationRegistry.getInstrumentation()
            val monitor = instrumentation.addMonitor(
                Material3DefaultThemeActivity::class.java.name,
                null,
                false,
            )
            try {
                scenario.onActivity { activity ->
                    activity.clickByTestTag(DemoTestTags.SETTINGS_MATERIAL3_DEFAULT_ENTRY)
                }
                val launched = instrumentation.waitForMonitorWithTimeout(monitor, 5_000)
                assertNotNull("Expected default Material3 theme validation Activity", launched)
                launched?.let { activity ->
                    waitForUiIdle()
                    activity.requireViewByTestTagVisible(DemoTestTags.MATERIAL3_DEFAULT_ROOT)
                    activity.requireViewByTestTagVisible(DemoTestTags.MATERIAL3_DEFAULT_BUTTON)
                    activity.finish()
                }
            } finally {
                instrumentation.removeMonitor(monitor)
            }
        }
    }

    @Test
    fun material3Defaults_separateVisualAndEffectiveBoundsAtSupportedFontScales() {
        listOf(1f, 1.3f).forEach { fontScale ->
            val context = ApplicationProvider.getApplicationContext<android.content.Context>()
            val intent = Material3DefaultThemeActivity.newIntent(context, fontScale)
            launchDemoActivity<Material3DefaultThemeActivity>(intent, DemoThemeMode.Light).use { scenario ->
                waitForUiIdle()
                var evidence = ""
                var touchX = 0
                var touchY = 0
                scenario.onActivity { activity ->
                    val button = activity.requireViewByTestTagVisible(DemoTestTags.MATERIAL3_DEFAULT_BUTTON)
                    val density = activity.resources.displayMetrics.density
                    assertEquals(48, (button.height / density).toInt())
                    val visualBounds = requireVisualSurfaceBounds(button)
                    assertEquals(40, (visualBounds.height() / density).roundToInt())

                    val semanticBounds = Rect().also { bounds ->
                        button.createAccessibilityNodeInfo().getBoundsInScreen(bounds)
                    }
                    assertEquals(48, (semanticBounds.height() / density).roundToInt())

                    val location = IntArray(2).also(button::getLocationOnScreen)
                    touchX = location[0] + button.width / 2
                    touchY = location[1] + density.roundToInt()

                    val tags = listOf(
                        DemoTestTags.MATERIAL3_DEFAULT_BUTTON,
                        DemoTestTags.MATERIAL3_DEFAULT_ICON_BUTTON,
                        DemoTestTags.MATERIAL3_DEFAULT_CHIP,
                        DemoTestTags.MATERIAL3_DEFAULT_CHECKBOX,
                        DemoTestTags.MATERIAL3_DEFAULT_RADIO,
                        DemoTestTags.MATERIAL3_DEFAULT_SWITCH,
                        DemoTestTags.MATERIAL3_DEFAULT_SLIDER,
                        DemoTestTags.MATERIAL3_DEFAULT_NAVIGATION,
                    )
                    evidence = buildString {
                        appendLine("suite=material3-phase2-button-touch-target")
                        appendLine("fontScale=${activity.resources.configuration.fontScale}")
                        appendLine("density=$density")
                        tags.forEach { tag ->
                            val view = activity.requireViewByTestTagVisible(tag)
                            appendLine(view.boundsEvidence(tag, density))
                        }
                    }
                    tags.forEach { tag ->
                        val view = activity.requireViewByTestTagVisible(tag)
                        assertTrue("Expected measured control for $tag", view.width > 0 && view.height > 0)
                    }
                }
                UiDevice.getInstance(InstrumentationRegistry.getInstrumentation()).click(touchX, touchY)
                waitForUiIdle()
                scenario.onActivity { activity ->
                    assertEquals(
                        "Default clicks: 1",
                        activity.requireTextViewByTestTagVisible(
                            DemoTestTags.MATERIAL3_DEFAULT_BUTTON_STATUS,
                        ).text.toString(),
                    )
                }
                val scaleLabel = (fontScale * 100).toInt()
                val screenshot = captureDeviceScreenshot(
                    name = "material3-phase2-button-font-$scaleLabel",
                    directoryName = "material3-touch-target-baseline",
                )
                val metadata = File(screenshot.parentFile, "material3-phase2-button-font-$scaleLabel.txt")
                    .apply { writeText(evidence) }
                preserveAfterConnectedTest(screenshot)
                preserveAfterConnectedTest(metadata)
            }
        }
    }

    private fun requireVisualSurfaceBounds(view: View): Rect {
        val bounds = findInnermostDrawableBounds(view.background)
        assertNotNull("Expected a drawable surface for ${view.javaClass.simpleName}", bounds)
        return bounds!!
    }

    private fun findInnermostDrawableBounds(drawable: Drawable?): Rect? {
        return when (drawable) {
            null -> null
            is RippleDrawable -> {
                if (drawable.numberOfLayers > 0) {
                    findInnermostDrawableBounds(drawable.getDrawable(0)) ?: Rect(drawable.bounds)
                } else {
                    Rect(drawable.bounds)
                }
            }
            is InsetDrawable -> {
                findInnermostDrawableBounds(drawable.drawable) ?: Rect(drawable.bounds)
            }
            is LayerDrawable -> {
                (0 until drawable.numberOfLayers)
                    .asSequence()
                    .mapNotNull { index -> findInnermostDrawableBounds(drawable.getDrawable(index)) }
                    .firstOrNull()
                    ?: Rect(drawable.bounds)
            }
            else -> Rect(drawable.bounds)
        }
    }

    private fun View.boundsEvidence(tag: String, density: Float): String {
        val semanticBounds = Rect().also { bounds ->
            createAccessibilityNodeInfo().getBoundsInScreen(bounds)
        }
        val visualBounds = findInnermostDrawableBounds(background)
        return listOf(
            "tag=$tag",
            "viewDp=${(width / density).roundToInt()}x${(height / density).roundToInt()}",
            "visualDp=${visualBounds?.let { bounds ->
                "${(bounds.width() / density).roundToInt()}x${(bounds.height() / density).roundToInt()}"
            } ?: "none"}",
            "semanticDp=${(semanticBounds.width() / density).roundToInt()}x${(semanticBounds.height() / density).roundToInt()}",
            "clickable=$isClickable",
            "enabled=$isEnabled",
        ).joinToString(separator = ",")
    }

    private fun preserveAfterConnectedTest(artifact: File) {
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        val outputDirectory = "/sdcard/Download/viewcompose-material3-touch-target-baseline"
        device.executeShellCommand("mkdir -p $outputDirectory")
        val outputPath = "$outputDirectory/${artifact.name}"
        device.executeShellCommand("cp ${artifact.absolutePath} $outputPath")
        assertEquals(outputPath, device.executeShellCommand("ls $outputPath").trim())
    }
}
