package com.viewcompose

import android.os.Build
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import androidx.recyclerview.widget.RecyclerView
import com.viewcompose.oneui7.OneUi7Reference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.math.roundToInt

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
            var productionMetadata = ""
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
                    val tokenProducer = activity.requireTextViewByTestTagVisible(
                        DemoTestTags.ONE_UI_7_TOKEN_PRODUCER,
                    ).text.toString()
                    val primaryOrigin = activity.requireTextViewByTestTagVisible(
                        DemoTestTags.ONE_UI_7_PRIMARY_ORIGIN,
                    ).text.toString()
                    val designSystem = activity.requireTextViewByTestTagVisible(
                        DemoTestTags.ONE_UI_7_DESIGN_SYSTEM,
                    ).text.toString()
                    val recipeSet = activity.requireTextViewByTestTagVisible(
                        DemoTestTags.ONE_UI_7_RECIPE_SET,
                    ).text.toString()
                    val componentBackends = activity.requireTextViewByTestTagVisible(
                        DemoTestTags.ONE_UI_7_COMPONENT_BACKENDS,
                    ).text.toString()
                    val overlayTransport = activity.requireTextViewByTestTagVisible(
                        DemoTestTags.ONE_UI_7_OVERLAY_TRANSPORT,
                    ).text.toString()
                    val overlayPresenters = activity.requireTextViewByTestTagVisible(
                        DemoTestTags.ONE_UI_7_OVERLAY_PRESENTERS,
                    ).text.toString()
                    assertEquals("viewcompose-oneui7/static", tokenProducer)
                    assertEquals("FrameworkDefault", primaryOrigin)
                    assertEquals("viewcompose-oneui7", designSystem)
                    assertEquals(OneUi7Reference.componentSet, recipeSet)
                    assertEquals("viewcompose-overlay-android/dialog", overlayTransport)
                    assertTrue(
                        overlayPresenters.contains(
                            "overlay.snackbar:viewcompose-oneui7/native-snackbar/Equivalent",
                        ),
                    )
                    assertTrue(
                        overlayPresenters.contains(
                            "overlay.modal-bottom-sheet:" +
                                "viewcompose-oneui7/bottom-sheet-dialog/Equivalent",
                        ),
                    )
                    assertTrue(!overlayPresenters.contains("material-components"))
                    assertTrue(componentBackends.contains("switch:one-ui7-switch-v2:DslComposite/Equivalent"))
                    assertTrue(
                        componentBackends.contains(
                            "text-field:one-ui7-text-field-v2:NativeBehavioralCore/Equivalent",
                        ),
                    )
                    productionMetadata = buildString {
                        appendLine("tokenProducer=$tokenProducer")
                        appendLine("primaryOrigin=$primaryOrigin")
                        appendLine("designSystem=$designSystem")
                        appendLine("recipeSet=$recipeSet")
                        appendLine("componentBackends=$componentBackends")
                        appendLine("overlayTransport=$overlayTransport")
                        appendLine("overlayPresenters=$overlayPresenters")
                    }
                }
                captureEvidence(
                    label = "one-ui7-${fixture.label}-top",
                    metadata = fixture.metadata(productionMetadata),
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
                    activity.scrollFixtureToPosition(SWITCH_POSITION)
                }
                waitForUiIdle()
                scenario.onActivity { activity ->
                    val switchRoot = activity.requireViewByTestTagVisible(DemoTestTags.ONE_UI_7_SWITCH)
                    val density = activity.resources.displayMetrics.density
                    val trackWidth = (44f * density).roundToInt()
                    val trackHeight = (24f * density).roundToInt()
                    val thumbDiameter = (18f * density).roundToInt()
                    val descendants = switchRoot.descendantViews()
                    assertTrue(switchRoot.height >= (48f * density).roundToInt())
                    assertTrue(
                        descendants.any { view ->
                            view.width == trackWidth && view.height == trackHeight
                        },
                    )
                    assertTrue(
                        descendants.any { view ->
                            view.width == thumbDiameter && view.height == thumbDiameter
                        },
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
                    activity.dragByTestTag(
                        tag = DemoTestTags.ONE_UI_7_SWITCH,
                        deltaX = if (fixture.rtl) -120f else 120f,
                    )
                }
                waitForUiIdle()
                scenario.onActivity { activity ->
                    assertEquals(
                        "Checked: true",
                        activity.requireTextViewByTestTagVisible(DemoTestTags.ONE_UI_7_SWITCH_STATUS)
                            .text.toString(),
                    )
                }
                SystemClock.sleep(INTERACTION_STABILITY_MILLIS)
                waitForUiIdle()
                scenario.onActivity { activity ->
                    assertEquals(
                        "Checked: true",
                        activity.requireTextViewByTestTagVisible(DemoTestTags.ONE_UI_7_SWITCH_STATUS)
                            .text.toString(),
                    )
                }
                captureEvidence(
                    label = "one-ui7-${fixture.label}-switch-drag",
                    metadata = fixture.metadata(productionMetadata),
                )
                scenario.onActivity { activity ->
                    activity.scrollFixtureToPosition(TEXT_FIELD_POSITION)
                }
                waitForUiIdle()
                scenario.onActivity { activity ->
                    val fieldRoot = activity.requireViewByTestTagVisible(DemoTestTags.ONE_UI_7_TEXT_FIELD)
                    val editText = requireNotNull(findDescendant(fieldRoot, EditText::class.java))
                    assertEquals("Galaxy", editText.text.toString())
                    assertNotNull(editText.onCreateInputConnection(android.view.inputmethod.EditorInfo()))
                    activity.scrollFixtureToPosition(NAVIGATION_POSITION)
                }
                waitForUiIdle()
                scenario.onActivity { activity ->
                    activity.requireViewByTestTagVisible(DemoTestTags.ONE_UI_7_NAVIGATION)
                }

                val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
                assertTrue(
                    "Expected visible text-only Search destination",
                    device.wait(Until.hasObject(By.text("Search")), UI_TIMEOUT_MILLIS),
                )
                scenario.onActivity { activity -> activity.clickTextView("Search") }
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
                waitForUiIdle()
                captureEvidence(
                    label = "one-ui7-${fixture.label}-components",
                    metadata = fixture.metadata(productionMetadata),
                )
                scenario.onActivity { activity ->
                    activity.scrollFixtureToPosition(OVERLAY_POSITION)
                }
                waitForUiIdle()
                scenario.onActivity { activity ->
                    activity.clickByTestTag(DemoTestTags.ONE_UI_7_SNACKBAR_ACTION)
                }
                assertTrue(
                    "Expected One UI Snackbar presenter",
                    device.wait(Until.hasObject(By.desc("One UI Snackbar")), UI_TIMEOUT_MILLIS),
                )
                captureEvidence(
                    label = "one-ui7-${fixture.label}-snackbar",
                    metadata = fixture.metadata(productionMetadata),
                )
                device.findObject(By.text("Done")).click()
                waitForUiIdle()
                scenario.onActivity { activity ->
                    activity.clickByTestTag(DemoTestTags.ONE_UI_7_BOTTOM_SHEET_ACTION)
                }
                assertTrue(
                    "Expected One UI bottom-sheet presenter",
                    device.wait(Until.hasObject(By.desc("One UI Bottom Sheet")), UI_TIMEOUT_MILLIS),
                )
                assertTrue(device.hasObject(By.text("Connected devices")))
                captureEvidence(
                    label = "one-ui7-${fixture.label}-bottom-sheet",
                    metadata = fixture.metadata(productionMetadata),
                )
                device.findObject(By.text("Close")).click()
                waitForUiIdle()
            }
        }
    }

    @Test
    fun controlledSwitch_dragSettlesOnceInLtrAndRtl() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        listOf(false, true).forEach { rtl ->
            val intent = OneUi7VerificationActivity.newIntent(
                context = context,
                dark = rtl,
                rtl = rtl,
                fontScale = if (rtl) 1.3f else 1f,
            )
            launchDemoActivity<OneUi7VerificationActivity>(intent).use { scenario ->
                waitForUiIdle()
                scenario.onActivity { activity ->
                    activity.scrollFixtureToPosition(SWITCH_POSITION)
                }
                waitForUiIdle()
                scenario.onActivity { activity ->
                    assertEquals(
                        "Checked: true",
                        activity.requireTextViewByTestTagVisible(DemoTestTags.ONE_UI_7_SWITCH_STATUS)
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
                    activity.dragByTestTag(
                        tag = DemoTestTags.ONE_UI_7_SWITCH,
                        deltaX = if (rtl) -120f else 120f,
                    )
                }
                waitForUiIdle()
                SystemClock.sleep(INTERACTION_STABILITY_MILLIS)
                waitForUiIdle()
                scenario.onActivity { activity ->
                    assertEquals(
                        "Checked: true",
                        activity.requireTextViewByTestTagVisible(DemoTestTags.ONE_UI_7_SWITCH_STATUS)
                            .text.toString(),
                    )
                }
            }
        }
    }

    @Test
    @Suppress("DEPRECATION")
    fun navigation_exposesSingleSelectionCollectionPositionsInLtrAndRtl() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        listOf(false, true).forEach { rtl ->
            val intent = OneUi7VerificationActivity.newIntent(
                context = context,
                rtl = rtl,
            )
            launchDemoActivity<OneUi7VerificationActivity>(intent).use { scenario ->
                waitForUiIdle()
                scenario.onActivity { activity ->
                    activity.scrollFixtureToPosition(NAVIGATION_POSITION)
                }
                waitForUiIdle()
                scenario.onActivity { activity ->
                    val navigation = activity.requireViewByTestTagVisible(DemoTestTags.ONE_UI_7_NAVIGATION)
                    val collectionNode = AccessibilityNodeInfoCompat.wrap(
                        navigation.createAccessibilityNodeInfo(),
                    )
                    assertEquals(1, collectionNode.collectionInfo?.rowCount)
                    assertEquals(3, collectionNode.collectionInfo?.columnCount)
                    assertEquals(
                        AccessibilityNodeInfoCompat.CollectionInfoCompat.SELECTION_MODE_SINGLE,
                        collectionNode.collectionInfo?.selectionMode,
                    )
                    val itemNodes = navigation.descendantViews()
                        .map { view ->
                            AccessibilityNodeInfoCompat.wrap(view.createAccessibilityNodeInfo())
                        }
                        .filter { info -> info.collectionItemInfo != null }
                    assertEquals(
                        listOf(0, 1, 2),
                        itemNodes.map { info -> info.collectionItemInfo!!.columnIndex }.sorted(),
                    )
                    assertEquals(1, itemNodes.count { info -> info.isSelected })
                }
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

    private fun FixtureCase.metadata(productionMetadata: String): String = buildString {
        appendLine("suite=one-ui-7-five-component-alpha")
        appendLine("reference=One UI 7")
        append(productionMetadata)
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

    private fun View.descendantViews(): List<View> = buildList {
        fun collect(view: View) {
            add(view)
            if (view is ViewGroup) {
                repeat(view.childCount) { index -> collect(view.getChildAt(index)) }
            }
        }
        collect(this@descendantViews)
    }

    private fun android.app.Activity.scrollFixtureToPosition(position: Int) {
        val root = findViewById<ViewGroup>(android.R.id.content)
        requireNotNull(findDescendant(root, RecyclerView::class.java)).scrollToPosition(position)
    }

    private data class FixtureCase(
        val label: String,
        val dark: Boolean,
        val rtl: Boolean,
        val fontScale: Float,
    )

    private companion object {
        const val WINDOW_SETTLE_MILLIS = 250L
        const val INTERACTION_STABILITY_MILLIS = 300L
        const val UI_TIMEOUT_MILLIS = 5_000L
        const val PRIVATE_OUTPUT_DIRECTORY = "one-ui7-alpha"
        const val PUBLIC_OUTPUT_DIRECTORY = "viewcompose-one-ui7-alpha"
        const val SWITCH_POSITION = 3
        const val TEXT_FIELD_POSITION = 4
        const val NAVIGATION_POSITION = 5
        const val OVERLAY_POSITION = 6
    }
}
