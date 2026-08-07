package com.viewcompose

import android.graphics.Color
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
import com.viewcompose.material3.Material3ThemeDefaults
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNotEquals
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

                    listOf(
                        DemoTestTags.MATERIAL3_DEFAULT_CHECKBOX,
                        DemoTestTags.MATERIAL3_DEFAULT_RADIO,
                        DemoTestTags.MATERIAL3_DEFAULT_SWITCH,
                        DemoTestTags.MATERIAL3_DEFAULT_SLIDER,
                    ).forEach { tag ->
                        val view = activity.requireViewByTestTagVisible(tag)
                        assertEquals(48, (view.height / density).roundToInt())
                        val inputSemanticBounds = Rect().also { bounds ->
                            view.createAccessibilityNodeInfo().getBoundsInScreen(bounds)
                        }
                        assertEquals(48, (inputSemanticBounds.height() / density).roundToInt())
                    }

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
                        appendLine("suite=material3-phase2-touch-targets")
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

    @Test
    fun compactTargets_useNonOverlappingViewsAndRespectExplicitConstraints() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val intent = Material3DefaultThemeActivity.newIntent(context, 1f)
        launchDemoActivity<Material3DefaultThemeActivity>(intent, DemoThemeMode.Light).use { scenario ->
            scrollDeviceTextIntoView("Touch target probes")
            var firstTouchX = 0
            var firstTouchY = 0
            var secondTouchX = 0
            var secondTouchY = 0
            scenario.onActivity { activity ->
                val density = activity.resources.displayMetrics.density
                val first = activity.requireViewByTestTagVisible(DemoTestTags.MATERIAL3_TARGET_ADJACENT_FIRST)
                val second = activity.requireViewByTestTagVisible(DemoTestTags.MATERIAL3_TARGET_ADJACENT_SECOND)
                val explicit = activity.requireViewByTestTagVisible(
                    DemoTestTags.MATERIAL3_TARGET_EXPLICIT_COMPACT,
                )
                val clippedParent = activity.requireViewByTestTagVisible(
                    DemoTestTags.MATERIAL3_TARGET_CLIPPED_PARENT,
                )
                val clippedChild = activity.requireViewByTestTagVisible(
                    DemoTestTags.MATERIAL3_TARGET_CLIPPED_CHILD,
                )

                assertEquals(48, (first.height / density).roundToInt())
                assertEquals(48, (second.height / density).roundToInt())
                assertEquals(32, (explicit.height / density).roundToInt())
                assertEquals(32, (clippedParent.height / density).roundToInt())
                assertEquals(32, (clippedChild.height / density).roundToInt())

                val firstLocation = IntArray(2).also(first::getLocationOnScreen)
                val secondLocation = IntArray(2).also(second::getLocationOnScreen)
                assertEquals(firstLocation[1] + first.height, secondLocation[1])
                firstTouchX = firstLocation[0] + first.width / 2
                firstTouchY = firstLocation[1] + first.height - 1
                secondTouchX = secondLocation[0] + second.width / 2
                secondTouchY = secondLocation[1] + 1
            }

            val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
            device.click(firstTouchX, firstTouchY)
            waitForUiIdle()
            scenario.onActivity { activity ->
                assertEquals(
                    "Adjacent: true/false",
                    activity.requireTextViewByTestTagVisible(
                        DemoTestTags.MATERIAL3_TARGET_ADJACENT_STATUS,
                    ).text.toString(),
                )
            }
            device.click(secondTouchX, secondTouchY)
            waitForUiIdle()
            scenario.onActivity { activity ->
                assertEquals(
                    "Adjacent: true/true",
                    activity.requireTextViewByTestTagVisible(
                        DemoTestTags.MATERIAL3_TARGET_ADJACENT_STATUS,
                    ).text.toString(),
                )
            }
        }
    }

    @Test
    fun material3Button_recordsCurrentSingleColorStateLayerBaseline() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val intent = Material3DefaultThemeActivity.newIntent(context, 1f)
        launchDemoActivity<Material3DefaultThemeActivity>(intent, DemoThemeMode.Light).use { scenario ->
            waitForUiIdle()
            var evidence = ""
            scenario.onActivity { activity ->
                val button = activity.requireViewByTestTagVisible(DemoTestTags.MATERIAL3_DEFAULT_BUTTON)
                val ripple = findRippleDrawable(button.background)
                assertNotNull("Expected Material3 Button RippleDrawable", ripple)
                val material = Material3ThemeDefaults.light().colors
                val pressed = material.ripple
                val focused = material.ripple
                val hovered = material.ripple
                val referencePressed = material.onPrimary.withAlpha(0.10f)
                val referenceFocused = material.onPrimary.withAlpha(0.10f)
                val referenceHovered = material.onPrimary.withAlpha(0.08f)

                assertEquals(pressed, focused)
                assertEquals(pressed, hovered)
                assertNotEquals(referencePressed, pressed)
                assertNotEquals(referenceHovered, hovered)

                evidence = buildString {
                    appendLine("suite=material3-phase2-state-layer-current")
                    appendLine("component=primary-button")
                    appendLine("actualPressed=${pressed.toArgbHex()}")
                    appendLine("actualFocused=${focused.toArgbHex()}")
                    appendLine("actualHovered=${hovered.toArgbHex()}")
                    appendLine("referencePressed=${referencePressed.toArgbHex()}")
                    appendLine("referenceFocused=${referenceFocused.toArgbHex()}")
                    appendLine("referenceHovered=${referenceHovered.toArgbHex()}")
                }
            }
            val instrumentation = InstrumentationRegistry.getInstrumentation()
            val artifact = File(
                instrumentation.targetContext.getExternalFilesDir(null),
                "material3-phase2-state-layer-current.txt",
            ).apply { writeText(evidence) }
            preserveAfterConnectedTest(artifact)
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

    private fun findRippleDrawable(drawable: Drawable?): RippleDrawable? {
        return when (drawable) {
            null -> null
            is RippleDrawable -> drawable
            is InsetDrawable -> findRippleDrawable(drawable.drawable)
            is LayerDrawable -> (0 until drawable.numberOfLayers)
                .asSequence()
                .mapNotNull { index -> findRippleDrawable(drawable.getDrawable(index)) }
                .firstOrNull()
            else -> null
        }
    }

    private fun Int.withAlpha(opacity: Float): Int {
        return Color.argb(
            (opacity.coerceIn(0f, 1f) * 255f).roundToInt(),
            Color.red(this),
            Color.green(this),
            Color.blue(this),
        )
    }

    private fun Int.toArgbHex(): String = "#%08X".format(this)

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
