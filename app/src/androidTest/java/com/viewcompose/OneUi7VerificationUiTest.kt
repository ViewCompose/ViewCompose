package com.viewcompose

import android.os.Build
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class OneUi7VerificationUiTest {
    @Test
    fun fiveComponentAlpha_preservesBehaviorAndExportsDeterministicEvidence() {
        resetPublicEvidenceDirectory()
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        listOf(
            FixtureCase(label = "light-ltr-1_0", dark = false, rtl = false, fontScale = 1f),
            FixtureCase(label = "dark-rtl-1_3", dark = true, rtl = true, fontScale = 1.3f),
        ).forEach { fixture ->
            val intent = OneUi7VerificationActivity.newIntent(
                context = context,
                dark = fixture.dark,
                rtl = fixture.rtl,
                fontScale = fixture.fontScale,
            )
            launchDemoActivity<OneUi7VerificationActivity>(intent).use { scenario ->
                waitForUiIdle()
                SystemClock.sleep(WINDOW_SETTLE_MILLIS)
                waitForUiIdle()
                scenario.onActivity { activity ->
                    assertEquals(
                        "one-ui-7-five-component-alpha",
                        activity.requireTextViewByTestTagVisible(DemoTestTags.ONE_UI_7_IDENTITY)
                            .text.toString(),
                    )
                    assertEquals(
                        if (fixture.rtl) View.LAYOUT_DIRECTION_RTL else View.LAYOUT_DIRECTION_LTR,
                        activity.requireViewByTestTagVisible(DemoTestTags.ONE_UI_7_ROOT).layoutDirection,
                    )
                }
                captureEvidence(
                    label = "one-ui7-${fixture.label}-top",
                    metadata = fixture.metadata(),
                )

                scenario.onActivity { activity ->
                    activity.clickByTestTag(DemoTestTags.ONE_UI_7_BUTTON)
                }
                waitForUiIdle()
                scenario.onActivity { activity ->
                    assertEquals(
                        "Button clicks: 1",
                        activity.requireTextViewByTestTagVisible(DemoTestTags.ONE_UI_7_BUTTON_STATUS)
                            .text.toString(),
                    )
                    activity.clickByTestTag(DemoTestTags.ONE_UI_7_SWITCH)
                }
                waitForUiIdle()
                scenario.onActivity { activity ->
                    assertEquals(
                        "Checked: false",
                        activity.requireTextViewByTestTagVisible(DemoTestTags.ONE_UI_7_SWITCH_STATUS)
                            .text.toString(),
                    )
                    val fieldRoot = activity.requireViewByTestTagVisible(DemoTestTags.ONE_UI_7_TEXT_FIELD)
                    val editText = requireNotNull(findDescendant(fieldRoot, EditText::class.java))
                    assertEquals("Galaxy", editText.text.toString())
                    assertNotNull(editText.onCreateInputConnection(android.view.inputmethod.EditorInfo()))
                    activity.requireViewByTestTagVisible(DemoTestTags.ONE_UI_7_NAVIGATION)
                }

                val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
                assertTrue(
                    "Expected visible text-only Search destination",
                    device.wait(Until.hasObject(By.text("Search")), UI_TIMEOUT_MILLIS),
                )
                device.findObject(By.text("Search")).click()
                waitForUiIdle()
                SystemClock.sleep(WINDOW_SETTLE_MILLIS)
                waitForUiIdle()
                scenario.onActivity { activity ->
                    assertEquals(
                        "Selected: Search",
                        activity.requireTextViewByTestTagVisible(DemoTestTags.ONE_UI_7_NAVIGATION_STATUS)
                            .text.toString(),
                    )
                }
                captureEvidence(
                    label = "one-ui7-${fixture.label}-components",
                    metadata = fixture.metadata(),
                )
            }
        }
    }

    @Test
    fun settingsEntry_opensOneUi7AlphaFixture() {
        launchDemoActivity(MainActivity::class.java, DemoThemeMode.Light).use { scenario ->
            scenario.onActivity { activity -> activity.clickTextView("设置") }
            waitForUiIdle()
            val instrumentation = InstrumentationRegistry.getInstrumentation()
            val monitor = instrumentation.addMonitor(
                OneUi7VerificationActivity::class.java.name,
                null,
                false,
            )
            try {
                scenario.onActivity { activity ->
                    activity.clickByTestTag(DemoTestTags.SETTINGS_ONE_UI_7_ENTRY)
                }
                val launched = instrumentation.waitForMonitorWithTimeout(monitor, 5_000)
                assertNotNull("Expected One UI 7 alpha verification Activity", launched)
                launched?.finish()
            } finally {
                instrumentation.removeMonitor(monitor)
            }
        }
    }

    private fun FixtureCase.metadata(): String = buildString {
        appendLine("suite=one-ui-7-five-component-alpha")
        appendLine("reference=One UI 7")
        appendLine("tokenSource=viewcompose-oneui7/static")
        appendLine("mode=${if (dark) "dark" else "light"}")
        appendLine("layoutDirection=${if (rtl) "rtl" else "ltr"}")
        appendLine("fontScale=$fontScale")
        appendLine("api=${Build.VERSION.SDK_INT}")
        appendLine("device=${Build.MANUFACTURER} ${Build.MODEL}")
        appendLine("button=Equivalent:BasicButton")
        appendLine("surface=Equivalent:BasicSurface")
        appendLine("switch=Equivalent:owned-composite")
        appendLine("textField=Equivalent:native-edit-core")
        appendLine("navigation=Equivalent:text-only-tabs")
        appendLine("backdropBlur=Degraded:tinted-surface")
    }

    private fun captureEvidence(label: String, metadata: String) {
        val screenshot = captureDeviceScreenshot(label, PRIVATE_OUTPUT_DIRECTORY)
        val sidecar = File(screenshot.parentFile, "$label.txt").apply { writeText(metadata) }
        preserveEvidence(screenshot)
        preserveEvidence(sidecar)
    }

    private fun preserveEvidence(artifact: File) {
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        val outputDirectory = "/sdcard/Download/$PUBLIC_OUTPUT_DIRECTORY"
        device.executeShellCommand("mkdir -p $outputDirectory")
        val outputPath = "$outputDirectory/${artifact.name}"
        device.executeShellCommand("cp ${artifact.absolutePath} $outputPath")
        assertEquals(outputPath, device.executeShellCommand("ls $outputPath").trim())
    }

    private fun resetPublicEvidenceDirectory() {
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        val outputDirectory = "/sdcard/Download/$PUBLIC_OUTPUT_DIRECTORY"
        device.executeShellCommand("rm -rf $outputDirectory")
        device.executeShellCommand("mkdir -p $outputDirectory")
    }

    private fun <T : View> findDescendant(root: View, type: Class<T>): T? {
        if (type.isInstance(root)) return type.cast(root)
        if (root !is ViewGroup) return null
        repeat(root.childCount) { index ->
            findDescendant(root.getChildAt(index), type)?.let { return it }
        }
        return null
    }

    private data class FixtureCase(
        val label: String,
        val dark: Boolean,
        val rtl: Boolean,
        val fontScale: Float,
    )

    private companion object {
        const val WINDOW_SETTLE_MILLIS = 250L
        const val UI_TIMEOUT_MILLIS = 5_000L
        const val PRIVATE_OUTPUT_DIRECTORY = "one-ui7-alpha"
        const val PUBLIC_OUTPUT_DIRECTORY = "viewcompose-one-ui7-alpha"
    }
}
