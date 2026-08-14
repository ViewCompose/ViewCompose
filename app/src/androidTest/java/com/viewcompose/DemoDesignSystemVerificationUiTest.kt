package com.viewcompose

import android.os.Build
import android.os.SystemClock
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.accessibility.AccessibilityNodeInfo
import android.view.autofill.AutofillValue
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import androidx.recyclerview.widget.RecyclerView
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
class DemoDesignSystemVerificationUiTest {
    @Test
    fun rootReplacement_preservesCallerStateAndRefreshesLazyAndOverlaySnapshots() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val intent = DemoDesignSystemVerificationActivity.newIntent(
            context = context,
            kind = DemoDesignSystemKind.CutContrast,
        )
        launchDemoActivity<DemoDesignSystemVerificationActivity>(intent).use { scenario ->
            waitForUiIdle()
            var previousRoot: View? = null
            scenario.onActivity { activity ->
                previousRoot = activity.requireViewByTestTagVisible(DemoTestTags.DESIGN_SYSTEM_ROOT)
                activity.clickByTestTag(DemoTestTags.DESIGN_SYSTEM_BUTTON)
            }
            waitForUiIdle()
            scenario.onActivity { activity ->
                assertEquals(
                    "Button clicks: 1",
                    activity.requireTextViewByTestTagVisible(
                        DemoTestTags.DESIGN_SYSTEM_BUTTON_STATUS,
                    ).text.toString(),
                )
                activity.clickByTestTag(DemoTestTags.DESIGN_SYSTEM_REPLACE_ROOT)
            }
            waitForUiIdle()
            SystemClock.sleep(WINDOW_TRANSITION_SETTLE_MS)
            waitForUiIdle()

            scenario.onActivity { activity ->
                val nextRoot = activity.requireViewByTestTagVisible(DemoTestTags.DESIGN_SYSTEM_ROOT)
                assertTrue("Expected root View replacement", nextRoot !== previousRoot)
                assertEquals(
                    "rounded-reference · Rounded reference",
                    activity.requireTextViewByTestTagVisible(
                        DemoTestTags.DESIGN_SYSTEM_IDENTITY,
                    ).text.toString(),
                )
                assertEquals(
                    "Lazy system: rounded-reference",
                    activity.requireTextViewByTestTagVisible(
                        DemoTestTags.DESIGN_SYSTEM_LAZY_IDENTITY,
                    ).text.toString(),
                )
                assertEquals(
                    "Button clicks: 1",
                    activity.requireTextViewByTestTagVisible(
                        DemoTestTags.DESIGN_SYSTEM_BUTTON_STATUS,
                    ).text.toString(),
                )
                activity.clickByTestTag(DemoTestTags.DESIGN_SYSTEM_OPEN_DIALOG)
            }

            val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
            assertTrue(
                "Expected overlay to capture rounded-reference locals",
                device.wait(
                    Until.hasObject(By.text("Overlay system: rounded-reference")),
                    OVERLAY_TIMEOUT_MS,
                ),
            )
            captureEvidence(
                label = "phase5-overlay-rounded-reference",
                metadata = "suite=multi-design-phase5\ndesignSystem=rounded-reference\nsnapshot=root+lazy+overlay\n",
            )
            device.findObject(By.text("Switch overlay to cupertino-pressure")).click()
            waitForUiIdle()
            SystemClock.sleep(WINDOW_TRANSITION_SETTLE_MS)
            waitForUiIdle()
            assertTrue(
                "Expected restored overlay to capture cupertino-pressure locals",
                device.wait(
                    Until.hasObject(By.text("Overlay system: cupertino-pressure")),
                    OVERLAY_TIMEOUT_MS,
                ),
            )
            assertTrue(
                "Old overlay session must be cleared atomically",
                !device.hasObject(By.text("Overlay system: rounded-reference")),
            )
            captureEvidence(
                label = "phase5-overlay-cupertino-pressure",
                metadata = "suite=multi-design-phase5\ndesignSystem=cupertino-pressure\nsnapshot=root+lazy+overlay\n",
            )

            scenario.onActivity { activity ->
                assertEquals(
                    "cupertino-pressure · Cupertino pressure",
                    activity.requireTextViewByTestTagVisible(
                        DemoTestTags.DESIGN_SYSTEM_IDENTITY,
                    ).text.toString(),
                )
                assertEquals(
                    "Lazy system: cupertino-pressure",
                    activity.requireTextViewByTestTagVisible(
                        DemoTestTags.DESIGN_SYSTEM_LAZY_IDENTITY,
                    ).text.toString(),
                )
                assertEquals(
                    "Button clicks: 1",
                    activity.requireTextViewByTestTagVisible(
                        DemoTestTags.DESIGN_SYSTEM_BUTTON_STATUS,
                    ).text.toString(),
                )
            }
            device.findObject(By.text("Close coherent dialog")).click()
            assertTrue(
                "Expected new-session dialog dismissal",
                device.wait(
                    Until.gone(By.text("Overlay system: cupertino-pressure")),
                    OVERLAY_TIMEOUT_MS,
                ),
            )
        }
    }

    @Test
    fun designSystemPressureSlice_exportsAttributionAndPreservesBehaviorAcrossMatrix() {
        resetPublicEvidenceDirectory()
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val requestedKind = InstrumentationRegistry.getArguments()
            .getString("designSystemKind")
            ?.let(DemoDesignSystemKind::fromId)
        val cases = listOf(
            FixtureCase(DemoDesignSystemKind.CutContrast, dark = false, rtl = false, fontScale = 1f),
            FixtureCase(
                DemoDesignSystemKind.CutContrast,
                dark = true,
                rtl = true,
                fontScale = 1.3f,
                reducedMotion = true,
            ),
            FixtureCase(
                DemoDesignSystemKind.RoundedReference,
                dark = false,
                rtl = false,
                fontScale = 2f,
            ),
            FixtureCase(
                DemoDesignSystemKind.CupertinoPressure,
                dark = false,
                rtl = false,
                fontScale = 1f,
            ),
            FixtureCase(
                DemoDesignSystemKind.CupertinoPressure,
                dark = true,
                rtl = true,
                fontScale = 1.3f,
                reducedMotion = true,
            ),
        ).filter { fixture -> requestedKind == null || fixture.kind == requestedKind }

        cases.forEach { fixture ->
            val intent = DemoDesignSystemVerificationActivity.newIntent(
                context = context,
                kind = fixture.kind,
                dark = fixture.dark,
                rtl = fixture.rtl,
                fontScale = fixture.fontScale,
                reducedMotionEnabled = fixture.reducedMotion,
            )
            launchDemoActivity<DemoDesignSystemVerificationActivity>(intent).use { scenario ->
                waitForUiIdle()
                SystemClock.sleep(WINDOW_TRANSITION_SETTLE_MS)
                waitForUiIdle()
                val label = fixture.artifactLabel()
                var metadata = ""
                scenario.onActivity { activity ->
                    assertEquals(
                        "${fixture.kind.id} · ${fixture.kind.label}",
                        activity.requireTextViewByTestTagVisible(
                            DemoTestTags.DESIGN_SYSTEM_IDENTITY,
                        ).text.toString(),
                    )
                    assertEquals(
                        "demo-design-system/${fixture.kind.id}",
                        activity.requireTextViewByTestTagVisible(
                            DemoTestTags.DESIGN_SYSTEM_TOKEN_SOURCE,
                        ).text.toString(),
                    )
                    assertTrue(
                        "Expected the pre-extraction Material context wrapper baseline",
                        activity.requireTextViewByTestTagVisible(
                            DemoTestTags.DESIGN_SYSTEM_ROOT_CONTEXT,
                        ).text.toString().startsWith("MutableContextWrapper > "),
                    )
                    assertEquals(
                        "#FF7B9E68",
                        activity.requireTextViewByTestTagVisible(
                            DemoTestTags.DESIGN_SYSTEM_ANDROID_PRIMARY,
                        ).text.toString(),
                    )
                    assertEquals(
                        "Button=DSL/DeclarativeBoxLayout; Switch=DSL/DeclarativeBoxLayout; " +
                            "TextField=native/ViewComposeEditText",
                        activity.requireTextViewByTestTagVisible(
                            DemoTestTags.DESIGN_SYSTEM_COMPONENT_BACKENDS,
                        ).text.toString(),
                    )
                    assertEquals(
                        fixture.reducedMotion.toString(),
                        activity.requireTextViewByTestTagVisible(
                            DemoTestTags.DESIGN_SYSTEM_REDUCED_MOTION,
                        ).text.toString(),
                    )
                    assertEquals(
                        fixture.fontScale.toString(),
                        activity.requireTextViewByTestTagVisible(
                            DemoTestTags.DESIGN_SYSTEM_FONT_SCALE,
                        ).text.toString(),
                    )
                    assertEquals(
                        if (fixture.rtl) View.LAYOUT_DIRECTION_RTL else View.LAYOUT_DIRECTION_LTR,
                        activity.requireViewByTestTagVisible(DemoTestTags.DESIGN_SYSTEM_ROOT).layoutDirection,
                    )
                    metadata = fixture.metadata(activity)
                    val root = activity.findViewById<ViewGroup>(android.R.id.content)
                    findDescendant(root, RecyclerView::class.java)?.scrollToPosition(0)
                }
                waitForUiIdle()

                captureEvidence("$label-identity", metadata)

                scenario.onActivity { activity ->
                    listOf(
                        DemoTestTags.DESIGN_SYSTEM_BUTTON,
                        DemoTestTags.DESIGN_SYSTEM_BUTTON_DISABLED,
                        DemoTestTags.DESIGN_SYSTEM_SURFACE,
                        DemoTestTags.DESIGN_SYSTEM_SWITCH,
                        DemoTestTags.DESIGN_SYSTEM_SWITCH_DISABLED,
                        DemoTestTags.DESIGN_SYSTEM_TEXT_FIELD,
                        DemoTestTags.DESIGN_SYSTEM_TEXT_FIELD_ERROR,
                        DemoTestTags.DESIGN_SYSTEM_SEGMENTED,
                        DemoTestTags.DESIGN_SYSTEM_NAVIGATION,
                    ).forEach { tag ->
                        val view = activity.requireViewByTestTagVisible(tag)
                        assertTrue("Expected measured fixture for $tag", view.width > 0 && view.height > 0)
                    }
                    activity.requireViewByTestTagVisible(DemoTestTags.DESIGN_SYSTEM_SURFACE)
                    activity.requireViewByTestTagVisible(DemoTestTags.DESIGN_SYSTEM_BUTTON)
                }
                waitForUiIdle()
                captureEvidence("$label-button-surface", metadata)

                scenario.onActivity { activity ->
                    activity.requireViewByTestTagVisible(DemoTestTags.DESIGN_SYSTEM_TEXT_FIELD_ERROR)
                }
                waitForUiIdle()
                captureEvidence("$label-switch-text-field", metadata)

                scenario.onActivity { activity ->
                    val button = activity.requireViewByTestTagVisible(DemoTestTags.DESIGN_SYSTEM_BUTTON)
                    assertTrue("Expected a stateful Button background", button.background?.isStateful == true)
                    button.isFocusableInTouchMode = true
                    assertTrue("Expected Button to accept keyboard focus", button.requestFocus())
                    button.isHovered = true
                    button.isPressed = true
                    button.refreshDrawableState()
                    assertTrue(button.drawableState.contains(android.R.attr.state_focused))
                    assertTrue(button.drawableState.contains(android.R.attr.state_hovered))
                    assertTrue(button.drawableState.contains(android.R.attr.state_pressed))
                    button.isPressed = false
                    button.isHovered = false
                    assertTrue(
                        "Expected d-pad activation to reach the Button",
                        button.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_CENTER)),
                    )
                    button.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DPAD_CENTER))

                    val switch = activity.requireViewByTestTagVisible(DemoTestTags.DESIGN_SYSTEM_SWITCH)
                    val nodeInfo = switch.createAccessibilityNodeInfo()
                    assertTrue("Expected checked Switch semantics", nodeInfo.isCheckable && nodeInfo.isChecked)
                    assertTrue(
                        "Expected Switch to preserve the 48dp minimum touch target",
                        switch.height / activity.resources.displayMetrics.density >= 48f,
                    )
                    assertTrue(
                        "Expected the Switch accessibility click action to be handled",
                        switch.performAccessibilityAction(AccessibilityNodeInfo.ACTION_CLICK, null),
                    )

                    val fieldRoot = activity.requireViewByTestTagVisible(DemoTestTags.DESIGN_SYSTEM_TEXT_FIELD)
                    val nativeField = findDescendant(fieldRoot, EditText::class.java)
                    assertNotNull("Expected native EditText editing core", nativeField)
                    requireNotNull(nativeField).apply {
                        assertTrue(isFocusable)
                        assertTrue(requestFocus())
                        assertNotNull("Expected a native IME input connection", onCreateInputConnection(EditorInfo()))
                        selectAll()
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            assertTrue(
                                "Expected the account field to expose a username autofill hint",
                                autofillHints?.contains(View.AUTOFILL_HINT_USERNAME) == true,
                            )
                            autofill(AutofillValue.forText("Grace"))
                        } else {
                            setText("Grace")
                        }
                        setSelection(1, 3)
                    }

                    val navigation = activity.requireViewByTestTagVisible(DemoTestTags.DESIGN_SYSTEM_NAVIGATION)
                    assertEquals(3, navigation.childCountOrZero())
                    val middleItem = (navigation as ViewGroup).getChildAt(1)
                    activity.tapView(middleItem)
                    activity.requireViewByTestTagVisible(DemoTestTags.DESIGN_SYSTEM_SEGMENTED)
                    activity.tapTextView("Week")
                }
                waitForUiIdle()
                scenario.onActivity { activity ->
                    assertEquals(
                        "Button clicks: 1",
                        activity.requireTextViewByTestTagVisible(
                            DemoTestTags.DESIGN_SYSTEM_BUTTON_STATUS,
                        ).text.toString(),
                    )
                    assertEquals(
                        "Checked: false",
                        activity.requireTextViewByTestTagVisible(
                            DemoTestTags.DESIGN_SYSTEM_SWITCH_STATUS,
                        ).text.toString(),
                    )
                    assertEquals(
                        "Selected: Search",
                        activity.requireTextViewByTestTagVisible(
                            DemoTestTags.DESIGN_SYSTEM_NAVIGATION_STATUS,
                        ).text.toString(),
                    )
                    assertEquals(
                        "Segment: Week",
                        activity.requireTextViewByTestTagVisible(
                            DemoTestTags.DESIGN_SYSTEM_SEGMENTED_STATUS,
                        ).text.toString(),
                    )
                }

                scenario.recreate()
                waitForUiIdle()
                scenario.onActivity { activity ->
                    assertEquals(
                        "Checked: false",
                        activity.requireTextViewByTestTagVisible(
                            DemoTestTags.DESIGN_SYSTEM_SWITCH_STATUS,
                        ).text.toString(),
                    )
                    assertEquals(
                        "Button clicks: 1",
                        activity.requireTextViewByTestTagVisible(
                            DemoTestTags.DESIGN_SYSTEM_BUTTON_STATUS,
                        ).text.toString(),
                    )
                    assertEquals(
                        "Selected: Search",
                        activity.requireTextViewByTestTagVisible(
                            DemoTestTags.DESIGN_SYSTEM_NAVIGATION_STATUS,
                        ).text.toString(),
                    )
                    assertEquals(
                        "Segment: Week",
                        activity.requireTextViewByTestTagVisible(
                            DemoTestTags.DESIGN_SYSTEM_SEGMENTED_STATUS,
                        ).text.toString(),
                    )
                    val fieldRoot = activity.requireViewByTestTagVisible(DemoTestTags.DESIGN_SYSTEM_TEXT_FIELD)
                    val restoredField = requireNotNull(findDescendant(fieldRoot, EditText::class.java))
                    assertEquals("Grace", restoredField.text.toString())
                    assertEquals(1, restoredField.selectionStart)
                    assertEquals(3, restoredField.selectionEnd)
                }

            }
        }
    }

    private fun FixtureCase.metadata(activity: DemoDesignSystemVerificationActivity): String {
        val layoutDirection = if (rtl) "rtl" else "ltr"
        return buildString {
            appendLine("suite=multi-design-system-pressure-v2")
            appendLine("designSystem=${kind.id}")
            appendLine("tokenSource=demo-design-system/${kind.id}")
            appendLine("recipeIdentity=${kind.id}/pressure-v2")
            appendLine(
                "rootContext=" + activity.requireTextViewByTestTagVisible(
                    DemoTestTags.DESIGN_SYSTEM_ROOT_CONTEXT,
                ).text,
            )
            appendLine(
                "androidColorPrimary=" + activity.requireTextViewByTestTagVisible(
                    DemoTestTags.DESIGN_SYSTEM_ANDROID_PRIMARY,
                ).text,
            )
            appendLine("mode=${if (dark) "dark" else "light"}")
            appendLine("reducedMotion=$reducedMotion")
            appendLine("api=${Build.VERSION.SDK_INT}")
            appendLine("device=${Build.MANUFACTURER} ${Build.MODEL}")
            appendLine("fontScale=$fontScale")
            appendLine("layoutDirection=$layoutDirection")
            appendLine("densityDpi=${activity.resources.displayMetrics.densityDpi}")
            val sharedPrimitiveOutcome = if (kind == DemoDesignSystemKind.CupertinoPressure) {
                "Equivalent"
            } else {
                "Exact"
            }
            appendLine("button=$sharedPrimitiveOutcome:BasicButton")
            appendLine("surface=$sharedPrimitiveOutcome:BasicSurface")
            appendLine("switch=Equivalent:owned-composite")
            appendLine("textField=Equivalent:native-edit-core")
            appendLine("navigation=Equivalent:owned-composite")
            if (kind == DemoDesignSystemKind.CupertinoPressure) {
                appendLine("segmentedControl=Equivalent:owned-composite")
                appendLine("continuousCorners=Exact:framework-path")
                appendLine("shapeMorph=Degraded:discrete-endpoint")
                appendLine("backdropBlur=Degraded:tinted-translucent-surface")
            } else {
                appendLine("backdropBlur=Degraded:tinted-surface")
            }
        }
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

    private fun captureEvidence(label: String, metadata: String) {
        val screenshot = captureDeviceScreenshot(label, OUTPUT_DIRECTORY)
        val sidecar = File(screenshot.parentFile, "$label.txt").apply {
            writeText(metadata)
        }
        preserveEvidence(screenshot)
        preserveEvidence(sidecar)
    }

    private fun <T : View> findDescendant(root: View, type: Class<T>): T? {
        if (type.isInstance(root)) return type.cast(root)
        if (root !is ViewGroup) return null
        repeat(root.childCount) { index ->
            findDescendant(root.getChildAt(index), type)?.let { return it }
        }
        return null
    }

    private fun View.childCountOrZero(): Int = (this as? ViewGroup)?.childCount ?: 0

    private data class FixtureCase(
        val kind: DemoDesignSystemKind,
        val dark: Boolean,
        val rtl: Boolean,
        val fontScale: Float,
        val reducedMotion: Boolean = false,
    ) {
        fun artifactLabel(): String {
            val mode = if (dark) "dark" else "light"
            val direction = if (rtl) "rtl" else "ltr"
            val scale = (fontScale * 100).toInt()
            return "${kind.id}-$mode-$direction-font-$scale"
        }
    }

    private companion object {
        const val OUTPUT_DIRECTORY = "multi-design-system"
        const val PUBLIC_OUTPUT_DIRECTORY = "viewcompose-multi-design-system"
        const val WINDOW_TRANSITION_SETTLE_MS = 500L
        const val OVERLAY_TIMEOUT_MS = 5_000L
    }
}
