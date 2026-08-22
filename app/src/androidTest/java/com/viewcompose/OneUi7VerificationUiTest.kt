package com.viewcompose

import android.graphics.drawable.RippleDrawable
import android.os.Build
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import androidx.recyclerview.widget.RecyclerView
import com.viewcompose.demo.contract.DemoAutomationRole
import com.viewcompose.oneui7.OneUi7Reference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
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
                        activity.requireTextViewByTestTagVisible(DemoOneUi7TestTags.ONE_UI_7_IDENTITY)
                            .text.toString(),
                    )
                    assertEquals(
                        if (fixture.rtl) View.LAYOUT_DIRECTION_RTL else View.LAYOUT_DIRECTION_LTR,
                        activity.requireScenarioTarget(DemoAutomationRole.Root).layoutDirection,
                    )
                    val tokenProducer = activity.requireTextViewByTestTagVisible(
                        DemoOneUi7TestTags.ONE_UI_7_TOKEN_PRODUCER,
                    ).text.toString()
                    val primaryOrigin = activity.requireTextViewByTestTagVisible(
                        DemoOneUi7TestTags.ONE_UI_7_PRIMARY_ORIGIN,
                    ).text.toString()
                    val designSystem = activity.requireTextViewByTestTagVisible(
                        DemoOneUi7TestTags.ONE_UI_7_DESIGN_SYSTEM,
                    ).text.toString()
                    val recipeSet = activity.requireTextViewByTestTagVisible(
                        DemoOneUi7TestTags.ONE_UI_7_RECIPE_SET,
                    ).text.toString()
                    val componentBackends = activity.requireTextViewByTestTagVisible(
                        DemoOneUi7TestTags.ONE_UI_7_COMPONENT_BACKENDS,
                    ).text.toString()
                    val overlayTransport = activity.requireTextViewByTestTagVisible(
                        DemoOneUi7TestTags.ONE_UI_7_OVERLAY_TRANSPORT,
                    ).text.toString()
                    val overlayPresenters = activity.requireTextViewByTestTagVisible(
                        DemoOneUi7TestTags.ONE_UI_7_OVERLAY_PRESENTERS,
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
                    activity.clickScenarioTarget(DemoAutomationRole.PrimaryAction)
                }
                waitForUiIdle()
                scenario.onActivity { activity ->
                    assertEquals(
                        activity.getString(R.string.demo_one_ui7_button_status, 1),
                        activity.requireScenarioText(DemoAutomationRole.State),
                    )
                    activity.scrollFixtureToPosition(SWITCH_POSITION)
                }
                waitForUiIdle()
                scenario.onActivity { activity ->
                    val switchRoot = activity.requireScenarioTarget(DemoAutomationRole.SecondaryAction)
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
                    activity.tapByTestTag(activity.scenarioTag(DemoAutomationRole.SecondaryAction))
                }
                waitForUiIdle()
                scenario.onActivity { activity ->
                    assertEquals(
                        activity.getString(R.string.demo_one_ui7_checked_status, false),
                        activity.requireScenarioText(DemoAutomationRole.SecondaryTarget),
                    )
                    activity.dragByTestTag(
                        tag = activity.scenarioTag(DemoAutomationRole.SecondaryAction),
                        deltaX = if (fixture.rtl) -120f else 120f,
                    )
                }
                waitForUiIdle()
                scenario.onActivity { activity ->
                    assertEquals(
                        activity.getString(R.string.demo_one_ui7_checked_status, true),
                        activity.requireScenarioText(DemoAutomationRole.SecondaryTarget),
                    )
                }
                SystemClock.sleep(INTERACTION_STABILITY_MILLIS)
                waitForUiIdle()
                scenario.onActivity { activity ->
                    assertEquals(
                        activity.getString(R.string.demo_one_ui7_checked_status, true),
                        activity.requireScenarioText(DemoAutomationRole.SecondaryTarget),
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
                    val fieldRoot = activity.requireViewByTestTagVisible(DemoOneUi7TestTags.ONE_UI_7_TEXT_FIELD)
                    val editText = requireNotNull(findDescendant(fieldRoot, EditText::class.java))
                    assertEquals(
                        activity.getString(R.string.demo_one_ui7_account_initial_value),
                        editText.text.toString(),
                    )
                    assertNotNull(editText.onCreateInputConnection(android.view.inputmethod.EditorInfo()))
                    activity.scrollFixtureToPosition(NAVIGATION_POSITION)
                }
                waitForUiIdle()
                scenario.onActivity { activity ->
                    activity.requireViewByTestTagVisible(DemoOneUi7TestTags.ONE_UI_7_NAVIGATION)
                }

                val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
                scenario.onActivity { activity ->
                    activity.clickTextView(activity.getString(R.string.demo_one_ui7_search))
                }
                waitForUiIdle()
                SystemClock.sleep(WINDOW_SETTLE_MILLIS)
                waitForUiIdle()
                scenario.onActivity { activity ->
                    assertEquals(
                        activity.getString(
                            R.string.demo_one_ui7_selected_status,
                            activity.getString(R.string.demo_one_ui7_search),
                        ),
                        activity.requireTextViewByTestTagVisible(DemoOneUi7TestTags.ONE_UI_7_NAVIGATION_STATUS)
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
                    activity.clickByTestTag(DemoOneUi7TestTags.ONE_UI_7_SNACKBAR_ACTION)
                }
                val snackbar = device.wait(
                    Until.findObject(By.desc("One UI Snackbar")),
                    UI_TIMEOUT_MILLIS,
                )
                assertNotNull("Expected One UI Snackbar presenter", snackbar)
                captureEvidence(
                    label = "one-ui7-${fixture.label}-snackbar",
                    metadata = fixture.metadata(productionMetadata),
                )
                val snackbarAction = requireNotNull(snackbar).findObject(By.clickable(true))
                assertNotNull("Expected One UI Snackbar action", snackbarAction)
                requireNotNull(snackbarAction).click()
                waitForUiIdle()
                scenario.onActivity { activity ->
                    activity.clickByTestTag(DemoOneUi7TestTags.ONE_UI_7_BOTTOM_SHEET_ACTION)
                }
                assertTrue(
                    "Expected One UI bottom-sheet presenter",
                    device.wait(Until.hasObject(By.desc("One UI Bottom Sheet")), UI_TIMEOUT_MILLIS),
                )
                assertTrue(
                    device.wait(
                        Until.hasObject(By.res(TARGET_PACKAGE, SHEET_CONTENT_RESOURCE)),
                        UI_TIMEOUT_MILLIS,
                    ),
                )
                captureEvidence(
                    label = "one-ui7-${fixture.label}-bottom-sheet",
                    metadata = fixture.metadata(productionMetadata),
                )
                requireNotNull(
                    device.findObject(By.res(TARGET_PACKAGE, SHEET_DISMISS_RESOURCE)),
                ).click()
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
                        activity.getString(R.string.demo_one_ui7_checked_status, true),
                        activity.requireScenarioText(DemoAutomationRole.SecondaryTarget),
                    )
                    activity.tapByTestTag(activity.scenarioTag(DemoAutomationRole.SecondaryAction))
                }
                waitForUiIdle()
                scenario.onActivity { activity ->
                    assertEquals(
                        activity.getString(R.string.demo_one_ui7_checked_status, false),
                        activity.requireScenarioText(DemoAutomationRole.SecondaryTarget),
                    )
                    activity.dragByTestTag(
                        tag = activity.scenarioTag(DemoAutomationRole.SecondaryAction),
                        deltaX = if (rtl) -120f else 120f,
                    )
                }
                waitForUiIdle()
                SystemClock.sleep(INTERACTION_STABILITY_MILLIS)
                waitForUiIdle()
                scenario.onActivity { activity ->
                    assertEquals(
                        activity.getString(R.string.demo_one_ui7_checked_status, true),
                        activity.requireScenarioText(DemoAutomationRole.SecondaryTarget),
                    )
                }
            }
        }
    }

    @Test
    fun resetRecreatesEveryLazyItemSessionAndDisposesActiveOverlays() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        launchDemoActivity<OneUi7VerificationActivity>(
            OneUi7VerificationActivity.newIntent(context),
        ).use { scenario ->
            waitForUiIdle()
            scenario.onActivity { activity ->
                activity.clickScenarioTarget(DemoAutomationRole.PrimaryAction)
                activity.scrollFixtureToPosition(SWITCH_POSITION)
            }
            waitForUiIdle()
            scenario.onActivity { activity ->
                activity.tapByTestTag(activity.scenarioTag(DemoAutomationRole.SecondaryAction))
                activity.scrollFixtureToPosition(TEXT_FIELD_POSITION)
            }
            waitForUiIdle()
            scenario.onActivity { activity ->
                val fieldRoot = activity.requireViewByTestTagVisible(DemoOneUi7TestTags.ONE_UI_7_TEXT_FIELD)
                requireNotNull(findDescendant(fieldRoot, EditText::class.java)).setText("Changed")
                activity.scrollFixtureToPosition(NAVIGATION_POSITION)
            }
            waitForUiIdle()
            scenario.onActivity { activity ->
                activity.clickTextView(activity.getString(R.string.demo_one_ui7_search))
                activity.scrollFixtureToPosition(OVERLAY_POSITION)
            }
            waitForUiIdle()
            scenario.onActivity { activity ->
                activity.clickByTestTag(DemoOneUi7TestTags.ONE_UI_7_SNACKBAR_ACTION)
            }
            assertTrue(
                device.wait(Until.hasObject(By.desc("One UI Snackbar")), UI_TIMEOUT_MILLIS),
            )

            scenario.onActivity { activity -> activity.scrollFixtureToPosition(IDENTITY_POSITION) }
            waitForUiIdle()
            scenario.onActivity { activity ->
                activity.clickScenarioTarget(DemoAutomationRole.Reset)
            }
            waitForUiIdle()
            assertTrue(
                "Reset must dispose the active One UI Snackbar",
                device.wait(Until.gone(By.desc("One UI Snackbar")), UI_TIMEOUT_MILLIS),
            )

            scenario.onActivity { activity -> activity.scrollFixtureToPosition(OVERLAY_POSITION) }
            waitForUiIdle()
            scenario.onActivity { activity ->
                activity.clickByTestTag(DemoOneUi7TestTags.ONE_UI_7_BOTTOM_SHEET_ACTION)
            }
            assertTrue(
                device.wait(Until.hasObject(By.desc("One UI Bottom Sheet")), UI_TIMEOUT_MILLIS),
            )

            scenario.onActivity { activity -> activity.scrollFixtureToPosition(IDENTITY_POSITION) }
            waitForUiIdle()
            scenario.onActivity { activity ->
                activity.clickScenarioTarget(DemoAutomationRole.Reset)
            }
            waitForUiIdle()
            assertTrue(
                "Reset must dispose the active One UI bottom sheet",
                device.wait(Until.gone(By.desc("One UI Bottom Sheet")), UI_TIMEOUT_MILLIS),
            )
            scenario.onActivity { activity ->
                assertEquals(
                    activity.getString(R.string.demo_one_ui7_button_status, 0),
                    activity.requireScenarioText(DemoAutomationRole.State),
                )
                activity.scrollFixtureToPosition(SWITCH_POSITION)
            }
            waitForUiIdle()
            scenario.onActivity { activity ->
                assertEquals(
                    activity.getString(R.string.demo_one_ui7_checked_status, true),
                    activity.requireScenarioText(DemoAutomationRole.SecondaryTarget),
                )
                activity.scrollFixtureToPosition(TEXT_FIELD_POSITION)
            }
            waitForUiIdle()
            scenario.onActivity { activity ->
                val fieldRoot = activity.requireViewByTestTagVisible(DemoOneUi7TestTags.ONE_UI_7_TEXT_FIELD)
                val field = requireNotNull(findDescendant(fieldRoot, EditText::class.java))
                assertEquals(
                    activity.getString(R.string.demo_one_ui7_account_initial_value),
                    field.text.toString(),
                )
                activity.scrollFixtureToPosition(NAVIGATION_POSITION)
            }
            waitForUiIdle()
            scenario.onActivity { activity ->
                assertEquals(
                    activity.getString(
                        R.string.demo_one_ui7_selected_status,
                        activity.getString(R.string.demo_one_ui7_home),
                    ),
                    activity.requireTextViewByTestTagVisible(
                        DemoOneUi7TestTags.ONE_UI_7_NAVIGATION_STATUS,
                    ).text.toString(),
                )
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
                    val navigation = activity.requireViewByTestTagVisible(DemoOneUi7TestTags.ONE_UI_7_NAVIGATION)
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
    fun navigation_quickTextTapRetainsRippleThroughSelectionPatch() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        launchDemoActivity<OneUi7VerificationActivity>(
            OneUi7VerificationActivity.newIntent(context = context),
        ).use { scenario ->
            waitForUiIdle()
            scenario.onActivity { activity ->
                activity.scrollFixtureToPosition(NAVIGATION_POSITION)
            }
            waitForUiIdle()

            lateinit var tappedItem: View
            lateinit var retainedRipple: RippleDrawable
            scenario.onActivity { activity ->
                val navigation = activity.requireViewByTestTagVisible(DemoOneUi7TestTags.ONE_UI_7_NAVIGATION)
                tappedItem = navigation.descendantViews().first { view ->
                    val info = AccessibilityNodeInfoCompat.wrap(view.createAccessibilityNodeInfo())
                    info.collectionItemInfo?.columnIndex == 1
                }
                val label = tappedItem.descendantViews().filterIsInstance<TextView>().first()
                val center = label.centerRelativeTo(tappedItem as ViewGroup)
                retainedRipple = tappedItem.background as RippleDrawable
                val downTime = SystemClock.uptimeMillis()
                val down = MotionEvent.obtain(
                    downTime,
                    downTime,
                    MotionEvent.ACTION_DOWN,
                    center.first,
                    center.second,
                    0,
                )
                val up = MotionEvent.obtain(
                    downTime,
                    downTime + 16L,
                    MotionEvent.ACTION_UP,
                    center.first,
                    center.second,
                    0,
                )
                try {
                    assertTrue(tappedItem.dispatchTouchEvent(down))
                    assertTrue(tappedItem.dispatchTouchEvent(up))
                } finally {
                    down.recycle()
                    up.recycle()
                }
            }

            SystemClock.sleep(48L)
            captureDeviceScreenshot("oneui-navigation-text-quick-release")
            waitForUiIdle()
            scenario.onActivity {
                assertTrue(tappedItem.isAttachedToWindow)
                assertSame(retainedRipple, tappedItem.background)
                val itemNode = AccessibilityNodeInfoCompat.wrap(
                    tappedItem.createAccessibilityNodeInfo(),
                )
                assertTrue(itemNode.isSelected)
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

    private fun View.centerRelativeTo(ancestor: ViewGroup): Pair<Float, Float> {
        var relativeLeft = left
        var relativeTop = top
        var current = parent as? View
        while (current != null && current !== ancestor) {
            relativeLeft += current.left
            relativeTop += current.top
            current = current.parent as? View
        }
        check(current === ancestor) { "Target is not a descendant of the navigation item." }
        return Pair(
            relativeLeft + width / 2f,
            relativeTop + height / 2f,
        )
    }

    private fun OneUi7VerificationActivity.scenarioTag(
        role: DemoAutomationRole,
    ): String = checkNotNull(currentScenario()).automation.require(role).testTag

    private fun OneUi7VerificationActivity.requireScenarioTarget(
        role: DemoAutomationRole,
    ): View = requireViewByTestTagVisible(scenarioTag(role))

    private fun OneUi7VerificationActivity.requireScenarioText(
        role: DemoAutomationRole,
    ): String = requireTextViewByTestTagVisible(scenarioTag(role)).text.toString()

    private fun OneUi7VerificationActivity.clickScenarioTarget(
        role: DemoAutomationRole,
    ) = clickByTestTag(scenarioTag(role))

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
        const val TARGET_PACKAGE = "com.gzq.uiframework"
        const val SHEET_CONTENT_RESOURCE = "demo_oneui7_sheet_content"
        const val SHEET_DISMISS_RESOURCE = "demo_oneui7_sheet_dismiss"
        const val IDENTITY_POSITION = 0
        const val SWITCH_POSITION = 2
        const val TEXT_FIELD_POSITION = 3
        const val NAVIGATION_POSITION = 4
        const val OVERLAY_POSITION = 5
    }
}
