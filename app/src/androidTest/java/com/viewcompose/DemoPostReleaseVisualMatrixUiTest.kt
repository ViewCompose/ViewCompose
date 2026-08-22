package com.viewcompose

import android.app.LocaleManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Rect
import android.graphics.drawable.RippleDrawable
import android.os.Build
import android.os.LocaleList
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.BySelector
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.viewcompose.demo.contract.EXTRA_DEMO_SCENARIO_ID
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.math.abs
import kotlin.math.ceil

/** Executes the post-release pairwise visual matrix against real Android Views. */
@RunWith(AndroidJUnit4::class)
class DemoPostReleaseVisualMatrixUiTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val device = UiDevice.getInstance(instrumentation)
    private val originalLanguageTags = currentApplicationLanguageTags()

    @After
    fun restoreApplicationLocaleAndExitFixture() {
        setApplicationLanguageTags(originalLanguageTags)
        device.pressHome()
    }

    @Test
    fun popupThemeGridAndSegmentedSlices_coverPairwiseConfigurationMatrix() {
        prepareEvidenceDirectory()
        MATRIX.forEach { configuration ->
            setApplicationLanguageTags(configuration.localeTag)
            verifyPopupShadowAndInteraction(configuration)
            verifyThemeSwatchGeometry(configuration)
            verifyRoundedGrid(configuration)
            verifySegmentedGeometry(configuration)
        }
    }

    @Test
    fun navigationQuickTapSlices_preserveReleaseFeedbackAcrossPairwiseConfigurationMatrix() {
        prepareEvidenceDirectory()
        MATRIX.forEach { configuration ->
            setApplicationLanguageTags(configuration.localeTag)
            verifyStandardNavigationQuickTap(configuration)
            verifyOneUiNavigationQuickTap(configuration)
        }
    }

    @Test
    fun nestedScrollAndFocusFollowSlices_coverPairwiseConfigurationMatrix() {
        prepareEvidenceDirectory()
        MATRIX.forEach { configuration ->
            setApplicationLanguageTags(configuration.localeTag)
            verifyNestedSameAxisHandoff(configuration)
            FOCUS_FOLLOW_CASES.forEach { focusCase ->
                verifyFocusFollow(configuration, focusCase)
            }
        }
    }

    private fun verifyPopupShadowAndInteraction(configuration: MatrixConfiguration) {
        launchScenario(FeedbackActivity::class.java, "overlay.menu", configuration).use { scenario ->
            waitForUiIdle()
            var initialState = ""
            val anchorBounds = Rect()
            scenario.onActivity { activity ->
                initialState = activity.requireScenarioViewById<TextView>(
                    R.id.demo_overlay_menu_state,
                ).text.toString()
                assertTrue(
                    activity.requireScenarioViewByIdVisible<View>(
                        R.id.demo_overlay_menu_secondary_action,
                    ).getGlobalVisibleRect(anchorBounds),
                )
                activity.clickScenarioViewById(R.id.demo_overlay_menu_secondary_action)
            }
            waitForUiIdle()

            val surface = requireDeviceResourceId(R.id.demo_overlay_menu_target)
            val surfaceBounds = Rect(surface.visibleBounds)
            val alignmentTolerance = ceil(2f * effectiveDensity(configuration)).toInt()
            if (configuration.rtl) {
                assertTrue(
                    "Expected RTL BelowStart to preserve the semantic end edge.",
                    abs(surfaceBounds.right - anchorBounds.right) <= alignmentTolerance,
                )
            } else {
                assertTrue(
                    "Expected LTR BelowStart to preserve the semantic start edge.",
                    abs(surfaceBounds.left - anchorBounds.left) <= alignmentTolerance,
                )
            }
            val screenshot = captureMatrixEvidence(
                scenarioId = "overlay.menu",
                configuration = configuration,
                action = "open-menu",
                expected = "semantic anchor alignment; rounded surface; transparent corners; native shadow on illuminated edges",
            )
            assertPopupPixelGolden(
                bitmap = requireNotNull(BitmapFactory.decodeFile(screenshot.absolutePath)),
                contentBounds = surfaceBounds,
                density = effectiveDensity(configuration),
                label = configuration.label,
            )

            device.click(
                surfaceBounds.centerX(),
                surfaceBounds.top + surfaceBounds.height() / 8,
            )
            waitForUiIdle()
            scenario.onActivity { activity ->
                assertNotEquals(
                    initialState,
                    activity.requireScenarioViewById<TextView>(R.id.demo_overlay_menu_state).text.toString(),
                )
                activity.clickScenarioViewById(R.id.demo_overlay_menu_secondary_action)
            }
            waitForUiIdle()
            val reopenedBounds = Rect(requireDeviceResourceId(R.id.demo_overlay_menu_target).visibleBounds)
            device.click(reopenedBounds.left - 1, reopenedBounds.top - 1)
            assertTrue(
                "Expected a touch in the transparent visual outset to dismiss the popup.",
                device.wait(
                    Until.gone(resourceSelector(R.id.demo_overlay_menu_target)),
                    UI_TIMEOUT_MILLIS,
                ),
            )
        }
    }

    private fun verifyThemeSwatchGeometry(configuration: MatrixConfiguration) {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val intent = DiagnosticsActivity.newIntent(
            context = context,
            page = DiagnosticsActivity.PAGE_THEME,
        ).putExtra(EXTRA_DEMO_SCENARIO_ID, "diagnostics.theme")
            .withDemoVerificationEnvironment(
                localeTag = configuration.localeTag,
                rtl = configuration.rtl,
                fontScale = configuration.fontScale,
                densityScale = configuration.densityScale,
            )
        launchDemoActivity<DiagnosticsActivity>(intent, configuration.themeMode).use { scenario ->
            waitForUiIdle()
            scenario.onActivity { activity ->
                val taggedSection = activity.requireViewByTestTagVisible(
                    DemoTestTags.DIAGNOSTICS_THEME_SWATCH_ROW,
                )
                assertTrue("Expected the four-token swatch row.", taggedSection is ViewGroup)
                val swatchRow = taggedSection as ViewGroup
                assertEquals(4, swatchRow.childCount)
                val widths = (0 until swatchRow.childCount)
                    .map { index -> swatchRow.getChildAt(index).width }
                assertTrue(widths.all { it > 0 })
                assertTrue(
                    "Expected equal theme-swatch columns, but widths were $widths.",
                    widths.max() - widths.min() <= 2,
                )
            }
            captureMatrixEvidence(
                scenarioId = "diagnostics.theme",
                configuration = configuration,
                action = "inspect-primary-secondary-swatches",
                expected = "four equal-width swatches with readable labels and no horizontal clipping",
            )
        }
    }

    private fun verifyRoundedGrid(configuration: MatrixConfiguration) {
        launchScenario(CollectionsActivity::class.java, "collection.grid", configuration).use { scenario ->
            waitForUiIdle()
            var initialLabel = ""
            scenario.onActivity { activity ->
                val grid = activity.requireScenarioViewById<View>(R.id.demo_collection_grid_target)
                assertTrue("Expected the rounded grid to clip descendants.", grid.clipToOutline)
                initialLabel = activity.requireTextViewByTestTagVisible(
                    DemoTestTags.COLLECTIONS_GRID_FIRST_ITEM,
                ).text.toString()
                activity.clickByTestTag(DemoTestTags.COLLECTIONS_GRID_THREE_COLS)
            }
            waitForUiIdle()
            scenario.onActivity { activity ->
                val changed = activity.requireTextViewByTestTagVisible(
                    DemoTestTags.COLLECTIONS_GRID_FIRST_ITEM,
                ).text.toString()
                assertNotEquals(initialLabel, changed)
                assertTrue(activity.requireScenarioViewById<View>(R.id.demo_collection_grid_target).clipToOutline)
            }
            captureMatrixEvidence(
                scenarioId = "collection.grid",
                configuration = configuration,
                action = "switch-to-three-columns",
                expected = "first and last rows remain reachable; no child pixel escapes any rounded corner",
            )
        }
    }

    private fun verifySegmentedGeometry(configuration: MatrixConfiguration) {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val intent = DemoDesignSystemVerificationActivity.newIntent(
            context = context,
            kind = DemoDesignSystemKind.RoundedReference,
            dark = configuration.themeMode == DemoThemeMode.Dark,
            rtl = configuration.rtl,
            fontScale = configuration.fontScale,
            densityScale = configuration.densityScale,
            localeTag = configuration.localeTag,
        )
        launchDemoActivity<DemoDesignSystemVerificationActivity>(intent, configuration.themeMode).use { scenario ->
            waitForUiIdle()
            scenario.onActivity { activity ->
                val segmented = activity.requireViewByTestTagVisible(DemoTestTags.DESIGN_SYSTEM_SEGMENTED)
                assertTrue(segmented is ViewGroup && segmented.childCount >= 2)
                activity.clickTextView(activity.getString(R.string.demo_design_system_week))
            }
            waitForUiIdle()
            scenario.onActivity { activity ->
                assertTrue(
                    activity.requireTextViewByTestTagVisible(DemoTestTags.DESIGN_SYSTEM_SEGMENTED_STATUS)
                        .text.toString()
                        .contains(activity.getString(R.string.demo_design_system_week)),
                )
            }
            captureMatrixEvidence(
                scenarioId = "design.bundle-material3",
                configuration = configuration,
                action = "select-week-segment",
                expected = "selected item follows the two-dp concentric inset in both physical directions",
            )
        }
    }

    private fun verifyStandardNavigationQuickTap(configuration: MatrixConfiguration) {
        launchScenario(NavigationActivity::class.java, "component.navigation-bar", configuration).use { scenario ->
            waitForUiIdle()
            lateinit var retainedRipple: RippleDrawable
            lateinit var selectedItem: ViewGroup
            var touchX = 0f
            var touchY = 0f
            var downTime = 0L
            scenario.onActivity { activity ->
                val navigation = activity.requireScenarioViewById<ViewGroup>(
                    R.id.demo_component_navigation_bar_primary_action,
                )
                selectedItem = navigation.getChildAt(1) as ViewGroup
                val iconContainer = selectedItem.getChildAt(0) as ViewGroup
                val icon = iconContainer.getChildAt(1)
                touchX = iconContainer.left + icon.left + icon.width / 2f
                touchY = iconContainer.top + icon.top + icon.height / 2f
                retainedRipple = selectedItem.foreground as RippleDrawable
                downTime = SystemClock.uptimeMillis()
                val down = MotionEvent.obtain(
                    downTime,
                    downTime,
                    MotionEvent.ACTION_DOWN,
                    touchX,
                    touchY,
                    0,
                )
                try {
                    assertTrue(selectedItem.dispatchTouchEvent(down))
                } finally {
                    down.recycle()
                }
            }
            SystemClock.sleep(16L)
            scenario.onActivity {
                val up = MotionEvent.obtain(
                    downTime,
                    SystemClock.uptimeMillis(),
                    MotionEvent.ACTION_UP,
                    touchX,
                    touchY,
                    0,
                )
                try {
                    assertTrue(selectedItem.dispatchTouchEvent(up))
                } finally {
                    up.recycle()
                }
            }
            SystemClock.sleep(48L)
            captureMatrixEvidence(
                scenarioId = "component.navigation-bar",
                configuration = configuration,
                action = "quick-tap-middle-icon-release-frame",
                expected = "release ripple remains visible while selection patches synchronously",
            )
            waitForUiIdle()
            scenario.onActivity { activity ->
                assertSame(retainedRipple, selectedItem.foreground)
                assertTrue(
                    activity.requireScenarioViewById<TextView>(R.id.demo_component_navigation_bar_state)
                        .text.toString().contains("1"),
                )
            }
        }
    }

    private fun verifyOneUiNavigationQuickTap(configuration: MatrixConfiguration) {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val intent = OneUi7VerificationActivity.newIntent(
            context = context,
            dark = configuration.themeMode == DemoThemeMode.Dark,
            rtl = configuration.rtl,
            fontScale = configuration.fontScale,
            densityScale = configuration.densityScale,
            localeTag = configuration.localeTag,
        )
        launchDemoActivity<OneUi7VerificationActivity>(intent, configuration.themeMode).use { scenario ->
            waitForUiIdle()
            scenario.onActivity { activity -> activity.scrollToNestedPosition(ONE_UI_NAVIGATION_POSITION) }
            waitForUiIdle()
            lateinit var tappedItem: View
            lateinit var retainedRipple: RippleDrawable
            scenario.onActivity { activity ->
                val navigation = activity.requireViewByTestTagVisible(DemoTestTags.ONE_UI_7_NAVIGATION)
                tappedItem = navigation.descendantViews().first { view ->
                    AccessibilityNodeInfoCompat.wrap(view.createAccessibilityNodeInfo())
                        .collectionItemInfo?.columnIndex == 1
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
            captureMatrixEvidence(
                scenarioId = "design.oneui7",
                configuration = configuration,
                action = "quick-tap-middle-label-release-frame",
                expected = "text-item release state remains visible while selected role changes",
            )
            waitForUiIdle()
            assertSame(retainedRipple, tappedItem.background)
            assertTrue(
                AccessibilityNodeInfoCompat.wrap(tappedItem.createAccessibilityNodeInfo()).isSelected,
            )
        }
    }

    private fun verifyNestedSameAxisHandoff(configuration: MatrixConfiguration) {
        launchScenario(
            CollectionsActivity::class.java,
            "collection.nested-lazy-list",
            configuration,
        ).use { scenario ->
            waitForUiIdle()
            lateinit var inner: RecyclerView
            lateinit var outer: RecyclerView
            scenario.onActivity { activity ->
                inner = activity.requireScenarioViewByIdVisible(
                    R.id.demo_collection_nested_lazy_list_target,
                )
                outer = requireNotNull(inner.ancestorRecyclerView())
                inner.stopScroll()
                outer.stopScroll()
                inner.scrollToPosition(0)
            }
            waitForUiIdle()
            alignNestedTarget(inner, outer)
            waitForUiIdle()
            assertFullyVisibleInsideOuter(inner, outer)
            assertTrue("Expected inner content beyond the initial viewport.", inner.canScrollVertically(1))
            captureMatrixEvidence(
                scenarioId = "collection.nested-lazy-list",
                configuration = configuration,
                action = "inner-at-start-edge",
                expected = "inner target is fully visible and owns the first same-axis upward gesture",
            )
            val initialInnerOffset = inner.verticalScrollSnapshot()
            swipeInside(inner, upward = true)
            waitForUiIdle()
            val innerAfterGesture = inner.verticalScrollSnapshot()
            assertTrue(
                "Expected the inner list to consume an upward gesture before its edge; " +
                    "before=$initialInnerOffset after=$innerAfterGesture.",
                innerAfterGesture.isAfter(initialInnerOffset),
            )

            instrumentation.runOnMainSync {
                inner.stopScroll()
                outer.stopScroll()
                inner.scrollToPosition(inner.adapter!!.itemCount - 1)
            }
            waitForUiIdle()
            assertTrue("Expected the inner list at its forward edge.", !inner.canScrollVertically(1))
            alignNestedTarget(inner, outer)
            waitForUiIdle()
            assertFullyVisibleInsideOuter(inner, outer)
            val outerBeforeForwardHandoff = outer.verticalScrollSnapshot()
            swipeInside(inner, upward = true)
            waitForUiIdle()
            assertTrue(
                "Expected unconsumed upward motion to hand off to the outer list.",
                outer.verticalScrollSnapshot().isAfter(outerBeforeForwardHandoff),
            )

            instrumentation.runOnMainSync {
                inner.stopScroll()
                outer.stopScroll()
                inner.scrollToPosition(0)
            }
            waitForUiIdle()
            assertTrue("Expected the inner list at its reverse edge.", !inner.canScrollVertically(-1))
            alignNestedTarget(inner, outer)
            waitForUiIdle()
            assertFullyVisibleInsideOuter(inner, outer)
            val outerBeforeReverseHandoff = outer.verticalScrollSnapshot()
            swipeInside(inner, upward = false)
            waitForUiIdle()
            assertTrue(
                "Expected unconsumed downward motion to hand back to the outer list.",
                outer.verticalScrollSnapshot().isBefore(outerBeforeReverseHandoff),
            )
            captureMatrixEvidence(
                scenarioId = "collection.nested-lazy-list",
                configuration = configuration,
                action = "inner-scroll-forward-and-reverse-edge-handoff",
                expected = "inner owner scrolls first; outer owner receives both-direction unconsumed motion at edges",
            )
        }
    }

    private fun verifyFocusFollow(
        configuration: MatrixConfiguration,
        focusCase: FocusFollowCase,
    ) {
        launchScenario(InputActivity::class.java, focusCase.scenarioId, configuration).use { scenario ->
            waitForUiIdle()
            assertTrue(
                "Expected ${focusCase.scenarioId} window focus before requesting editor focus.",
                waitUntil(scenario) { activity -> activity.hasWindowFocus() },
            )
            var hostHeight = 0
            scenario.onActivity { activity ->
                val header = activity.requireViewByTestTagVisible(
                    DemoTestTags.INPUT_FOCUS_FOLLOW_HEADER,
                )
                val action = activity.requireScenarioViewByIdVisible<View>(focusCase.primaryActionId)
                val headerBounds = Rect()
                val actionBounds = Rect()
                assertTrue(header.getGlobalVisibleRect(headerBounds))
                assertTrue(action.getGlobalVisibleRect(actionBounds))
                assertTrue(
                    "Expected focus header and action controls not to overlap for ${focusCase.scenarioId}.",
                    !Rect.intersects(headerBounds, actionBounds),
                )
                val host = activity.findViewById<View>(focusCase.targetId)
                    ?: activity.requireScenarioViewByIdVisible(focusCase.targetId)
                hostHeight = host.height
                activity.focusInputByScenarioViewId(focusCase.targetId)
            }
            var lastObservation = "IME not observed"
            val revealed = waitUntil(scenario) { activity ->
                val host = activity.requireScenarioViewById<View>(focusCase.targetId)
                val insets = ViewCompat.getRootWindowInsets(activity.window.decorView)
                    ?: return@waitUntil false
                if (!insets.isVisible(WindowInsetsCompat.Type.ime())) return@waitUntil false
                val decorLocation = IntArray(2)
                activity.window.decorView.getLocationOnScreen(decorLocation)
                val imeTop = decorLocation[1] + activity.window.decorView.height -
                    insets.getInsets(WindowInsetsCompat.Type.ime()).bottom
                val visible = Rect()
                val globallyVisible = host.getGlobalVisibleRect(visible)
                lastObservation =
                    "hostHeight=$hostHeight visible=${visible.toShortString()} imeTop=$imeTop " +
                    "imeBottom=${insets.getInsets(WindowInsetsCompat.Type.ime()).bottom}"
                globallyVisible &&
                    visible.height() == hostHeight &&
                    visible.bottom <= imeTop
            }
            captureMatrixEvidence(
                scenarioId = focusCase.scenarioId,
                configuration = configuration,
                action = "request-editor-focus",
                expected = "declared scroll owner minimally reveals the complete editor above the IME",
            )
            assertTrue(
                "Expected ${focusCase.scenarioId} to reveal the complete editor above IME; $lastObservation.",
                revealed,
            )
            scenario.onActivity { activity ->
                val host = activity.requireScenarioViewById<View>(focusCase.targetId)
                val focusedEditor = host.findFocus()
                assertNotNull("Expected ${focusCase.scenarioId} to retain input focus.", focusedEditor)
                val focusedBounds = Rect()
                assertTrue(
                    "Expected ${focusCase.scenarioId} to retain a visible text editor.",
                    focusedEditor!!.onCheckIsTextEditor() &&
                        focusedEditor.isShown &&
                        focusedEditor.getGlobalVisibleRect(focusedBounds) &&
                        !focusedBounds.isEmpty,
                )
            }
            device.pressBack()
            waitForUiIdle()
        }
    }

    private fun <A : android.app.Activity> launchScenario(
        activityClass: Class<A>,
        scenarioId: String,
        configuration: MatrixConfiguration,
    ): ActivityScenario<A> {
        val context = instrumentation.targetContext
        val intent = Intent(context, activityClass)
            .putExtra(EXTRA_DEMO_SCENARIO_ID, scenarioId)
            .withDemoVerificationEnvironment(
                localeTag = configuration.localeTag,
                rtl = configuration.rtl,
                fontScale = configuration.fontScale,
                densityScale = configuration.densityScale,
            )
        return launchDemoActivity(intent, configuration.themeMode)
    }

    private fun captureMatrixEvidence(
        scenarioId: String,
        configuration: MatrixConfiguration,
        action: String,
        expected: String,
    ): File {
        val safeScenario = scenarioId.replace('.', '-')
        val label = "$safeScenario-${configuration.label}-$action"
        val screenshot = captureDeviceScreenshot(label, PRIVATE_EVIDENCE_DIRECTORY)
        val metadata = File(screenshot.parentFile, "$label.txt").apply {
            writeText(
                buildString {
                    appendLine("suite=demo-post-release-visual-matrix-v1")
                    appendLine("scenarioId=$scenarioId")
                    appendLine("locale=${configuration.localeTag}")
                    appendLine("theme=${configuration.themeMode.name.lowercase()}")
                    appendLine("layoutDirection=${if (configuration.rtl) "rtl" else "ltr"}")
                    appendLine("fontScale=${configuration.fontScale}")
                    appendLine("densityScale=${configuration.densityScale}")
                    appendLine("device=${Build.MANUFACTURER} ${Build.MODEL}")
                    appendLine("api=${Build.VERSION.SDK_INT}")
                    appendLine("action=$action")
                    appendLine("expected=$expected")
                },
            )
        }
        preserveEvidence(screenshot)
        preserveEvidence(metadata)
        return screenshot
    }

    private fun assertPopupPixelGolden(
        bitmap: Bitmap,
        contentBounds: Rect,
        density: Float,
        label: String,
    ) {
        val outset = ceil(6f * density).toInt().coerceAtLeast(2)
        val referenceDistance = outset + ceil(2f * density).toInt()
        assertTrue(contentBounds.left - referenceDistance >= 0)
        assertTrue(contentBounds.top - referenceDistance >= 0)
        assertTrue(contentBounds.right + referenceDistance < bitmap.width)
        assertTrue(contentBounds.bottom + referenceDistance < bitmap.height)

        val edgeSamples = listOf(
            EdgeSample(
                name = "left",
                referenceX = contentBounds.left - referenceDistance,
                referenceY = contentBounds.centerY(),
                sample = { distance -> contentBounds.left - distance to contentBounds.centerY() },
            ),
            EdgeSample(
                name = "top",
                referenceX = contentBounds.centerX(),
                referenceY = contentBounds.top - referenceDistance,
                sample = { distance -> contentBounds.centerX() to contentBounds.top - distance },
            ),
            EdgeSample(
                name = "right",
                referenceX = contentBounds.right + referenceDistance,
                referenceY = contentBounds.centerY(),
                sample = { distance -> contentBounds.right + distance to contentBounds.centerY() },
            ),
            EdgeSample(
                name = "bottom",
                referenceX = contentBounds.centerX(),
                referenceY = contentBounds.bottom + referenceDistance,
                sample = { distance -> contentBounds.centerX() to contentBounds.bottom + distance },
            ),
        )
        val shadowPixelsByEdge = edgeSamples.associate { edge ->
            val reference = bitmap.getPixel(edge.referenceX, edge.referenceY)
            val shadowPixels = (1..outset).count { distance ->
                val (x, y) = edge.sample(distance)
                colorDistance(reference, bitmap.getPixel(x, y)) >= MIN_SHADOW_COLOR_DISTANCE
            }
            edge.name to shadowPixels
        }
        assertTrue(
            "Expected native shadow pixels on at least three illuminated edges for $label; " +
                "samples=$shadowPixelsByEdge, outset=$outset.",
            shadowPixelsByEdge.count { (_, changed) -> changed >= 1 } >= 3,
        )
        assertTrue(
            "Expected the native light projection below the popup for $label; " +
                "samples=$shadowPixelsByEdge, outset=$outset.",
            shadowPixelsByEdge.getValue("bottom") >= 1,
        )

        val fill = bitmap.getPixel(contentBounds.centerX(), contentBounds.centerY())
        listOf(
            contentBounds.left + 1 to contentBounds.top + 1,
            contentBounds.right - 2 to contentBounds.top + 1,
            contentBounds.left + 1 to contentBounds.bottom - 2,
            contentBounds.right - 2 to contentBounds.bottom - 2,
        ).forEachIndexed { index, (x, y) ->
            assertTrue(
                "Expected rounded corner $index to expose the transparent visual outset for $label.",
                colorDistance(fill, bitmap.getPixel(x, y)) >= MIN_ROUNDED_CORNER_COLOR_DISTANCE,
            )
        }
    }

    private fun swipeInside(view: View, upward: Boolean) {
        val visibleBounds = Rect()
        instrumentation.runOnMainSync {
            assertTrue("Expected a visible gesture target.", view.getGlobalVisibleRect(visibleBounds))
        }
        val centerX = visibleBounds.centerX()
        val upperY = visibleBounds.top + visibleBounds.height() / 4
        val lowerY = visibleBounds.top + visibleBounds.height() * 3 / 4
        assertTrue(
            "Expected gesture injection inside $visibleBounds.",
            device.swipe(
                centerX,
                if (upward) lowerY else upperY,
                centerX,
                if (upward) upperY else lowerY,
                80,
            ),
        )
    }

    private fun <A : android.app.Activity> waitUntil(
        scenario: ActivityScenario<A>,
        timeoutMs: Long = UI_TIMEOUT_MILLIS,
        predicate: (A) -> Boolean,
    ): Boolean {
        val deadline = SystemClock.uptimeMillis() + timeoutMs
        var matched = false
        while (!matched && SystemClock.uptimeMillis() < deadline) {
            scenario.onActivity { activity -> matched = predicate(activity) }
            if (!matched) SystemClock.sleep(16L)
        }
        return matched
    }

    private fun View.ancestorRecyclerView(): RecyclerView? {
        var current = parent
        while (current is View) {
            if (current is RecyclerView) return current
            current = current.parent
        }
        return null
    }

    private fun RecyclerView.verticalScrollSnapshot(): VerticalScrollSnapshot {
        val manager = layoutManager as LinearLayoutManager
        val position = manager.findFirstVisibleItemPosition()
        val first = manager.findViewByPosition(position)
        return VerticalScrollSnapshot(position = position, firstChildTop = first?.top ?: 0)
    }

    private fun alignNestedTarget(inner: RecyclerView, outer: RecyclerView) {
        instrumentation.runOnMainSync {
            val outerVisible = Rect()
            assertTrue(outer.getGlobalVisibleRect(outerVisible))
            val innerLocation = IntArray(2)
            inner.getLocationOnScreen(innerLocation)
            val innerTop = innerLocation[1]
            val innerBottom = innerTop + inner.height
            val dy = when {
                innerBottom > outerVisible.bottom -> innerBottom - outerVisible.bottom
                innerTop < outerVisible.top -> innerTop - outerVisible.top
                else -> 0
            }
            if (dy != 0) outer.scrollBy(0, dy)
        }
    }

    private fun assertFullyVisibleInsideOuter(inner: RecyclerView, outer: RecyclerView) {
        val innerVisible = Rect()
        val outerVisible = Rect()
        instrumentation.runOnMainSync {
            assertTrue(inner.getGlobalVisibleRect(innerVisible))
            assertTrue(outer.getGlobalVisibleRect(outerVisible))
        }
        assertEquals(inner.height, innerVisible.height())
        assertTrue(innerVisible.top >= outerVisible.top)
        assertTrue(innerVisible.bottom <= outerVisible.bottom)
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

    private fun TextView.centerRelativeTo(ancestor: ViewGroup): Pair<Float, Float> {
        var relativeLeft = left
        var relativeTop = top
        var current = parent as? View
        while (current != null && current !== ancestor) {
            relativeLeft += current.left
            relativeTop += current.top
            current = current.parent as? View
        }
        check(current === ancestor)
        return relativeLeft + width / 2f to relativeTop + height / 2f
    }

    private fun android.app.Activity.scrollToNestedPosition(position: Int) {
        val root = findViewById<ViewGroup>(android.R.id.content)
        val recyclerView = root.descendantViews().filterIsInstance<RecyclerView>().first()
        recyclerView.scrollToPosition(position)
    }

    private fun currentApplicationLanguageTags(): String {
        val context = instrumentation.targetContext
        return if (Build.VERSION.SDK_INT >= 33) {
            context.getSystemService(LocaleManager::class.java).applicationLocales.toLanguageTags()
        } else {
            AppCompatDelegate.getApplicationLocales().toLanguageTags()
        }
    }

    private fun setApplicationLanguageTags(languageTags: String) {
        val context = instrumentation.targetContext
        instrumentation.runOnMainSync {
            if (Build.VERSION.SDK_INT >= 33) {
                context.getSystemService(LocaleManager::class.java).applicationLocales =
                    LocaleList.forLanguageTags(languageTags)
            } else {
                AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(languageTags))
            }
        }
        SystemClock.sleep(100L)
        instrumentation.waitForIdleSync()
    }

    private fun resourceSelector(id: Int): BySelector {
        val context = instrumentation.targetContext
        return By.res(context.packageName, context.resources.getResourceEntryName(id))
    }

    private fun effectiveDensity(configuration: MatrixConfiguration): Float =
        instrumentation.targetContext.resources.displayMetrics.density * configuration.densityScale

    private fun colorDistance(first: Int, second: Int): Int = maxOf(
        abs(Color.red(first) - Color.red(second)),
        abs(Color.green(first) - Color.green(second)),
        abs(Color.blue(first) - Color.blue(second)),
    )

    private fun preserveEvidence(artifact: File) {
        val outputDirectory = "/sdcard/Download/$PUBLIC_EVIDENCE_DIRECTORY"
        device.executeShellCommand("mkdir -p $outputDirectory")
        val outputPath = "$outputDirectory/${artifact.name}"
        device.executeShellCommand("cp ${artifact.absolutePath} $outputPath")
        assertEquals(outputPath, device.executeShellCommand("ls $outputPath").trim())
    }

    private fun prepareEvidenceDirectory() {
        device.executeShellCommand("mkdir -p /sdcard/Download/$PUBLIC_EVIDENCE_DIRECTORY")
    }

    private data class MatrixConfiguration(
        val label: String,
        val localeTag: String,
        val themeMode: DemoThemeMode,
        val rtl: Boolean,
        val fontScale: Float,
        val densityScale: Float,
    )

    private data class FocusFollowCase(
        val scenarioId: String,
        val targetId: Int,
        val primaryActionId: Int,
    )

    private data class EdgeSample(
        val name: String,
        val referenceX: Int,
        val referenceY: Int,
        val sample: (Int) -> Pair<Int, Int>,
    )

    private data class VerticalScrollSnapshot(
        val position: Int,
        val firstChildTop: Int,
    ) {
        fun isAfter(other: VerticalScrollSnapshot): Boolean {
            return position > other.position ||
                (position == other.position && firstChildTop < other.firstChildTop)
        }

        fun isBefore(other: VerticalScrollSnapshot): Boolean {
            return position < other.position ||
                (position == other.position && firstChildTop > other.firstChildTop)
        }
    }

    private companion object {
        val MATRIX = listOf(
            MatrixConfiguration(
                label = "en-light-ltr-font100-density100",
                localeTag = "en",
                themeMode = DemoThemeMode.Light,
                rtl = false,
                fontScale = 1f,
                densityScale = 1f,
            ),
            MatrixConfiguration(
                label = "zh-dark-rtl-font130-density125",
                localeTag = "zh-CN",
                themeMode = DemoThemeMode.Dark,
                rtl = true,
                fontScale = 1.3f,
                densityScale = 1.25f,
            ),
        )
        val FOCUS_FOLLOW_CASES = listOf(
            FocusFollowCase(
                "input.focus-follow-lazy-column",
                R.id.demo_input_focus_follow_lazy_column_target,
                R.id.demo_input_focus_follow_lazy_column_primary_action,
            ),
            FocusFollowCase(
                "input.focus-follow-lazy-grid",
                R.id.demo_input_focus_follow_lazy_grid_target,
                R.id.demo_input_focus_follow_lazy_grid_primary_action,
            ),
            FocusFollowCase(
                "input.focus-follow-scrollable-column",
                R.id.demo_input_focus_follow_scrollable_column_target,
                R.id.demo_input_focus_follow_scrollable_column_primary_action,
            ),
            FocusFollowCase(
                "input.focus-follow-vertical-pager",
                R.id.demo_input_focus_follow_vertical_pager_target,
                R.id.demo_input_focus_follow_vertical_pager_primary_action,
            ),
            FocusFollowCase(
                "input.focus-follow-pull-refresh",
                R.id.demo_input_focus_follow_pull_refresh_target,
                R.id.demo_input_focus_follow_pull_refresh_primary_action,
            ),
        )
        const val ONE_UI_NAVIGATION_POSITION = 4
        const val UI_TIMEOUT_MILLIS = 5_000L
        const val PRIVATE_EVIDENCE_DIRECTORY = "demo-post-release-visual-matrix"
        const val PUBLIC_EVIDENCE_DIRECTORY = "viewcompose-demo-post-release-visual-matrix"
        const val MIN_SHADOW_COLOR_DISTANCE = 1
        const val MIN_ROUNDED_CORNER_COLOR_DISTANCE = 4
    }
}
