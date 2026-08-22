package com.viewcompose

import android.app.Activity
import android.content.Intent
import android.graphics.Rect
import android.graphics.drawable.LayerDrawable
import android.graphics.drawable.RippleDrawable
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.widget.NestedScrollView
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.viewcompose.demo.contract.EXTRA_DEMO_SCENARIO_ID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.abs

@RunWith(AndroidJUnit4::class)
class DemoVisualUiTest {
    @Test
    fun layoutsScroll_verticalDragMovesScrollableColumn() {
        launchDemoScenarioActivity(
            LayoutsActivity::class.java,
            "layout.scroll",
        ).use { scenario ->
            waitForUiIdle()
            var startX = 0
            var startY = 0
            var endY = 0
            scenario.onActivity { activity ->
                val scrollView = activity.requireViewByTestTag(
                    DemoTestTags.LAYOUTS_SCROLLABLE_COLUMN,
                ) as NestedScrollView
                assertTrue("Expected ScrollableColumn content to overflow", scrollView.canScrollVertically(1))
                val location = IntArray(2)
                scrollView.getLocationOnScreen(location)
                startX = location[0] + scrollView.width / 2
                startY = location[1] + scrollView.height * 3 / 4
                endY = location[1] + scrollView.height / 4
            }
            androidx.test.uiautomator.UiDevice.getInstance(
                androidx.test.platform.app.InstrumentationRegistry.getInstrumentation(),
            ).swipe(startX, startY, startX, endY, 12)
            waitForUiIdle()
            scenario.onActivity { activity ->
                val scrollView = activity.requireViewByTestTag(
                    DemoTestTags.LAYOUTS_SCROLLABLE_COLUMN,
                ) as NestedScrollView
                assertTrue(
                    "Expected device swipe from y=$startY to y=$endY to increase ScrollableColumn scrollY",
                    scrollView.scrollY > 0,
                )
            }
        }
    }

    @Test
    fun layoutsBenchmarkControls_areVisibleAndNotEllipsized() {
        launchDemoScenarioActivity(
            LayoutsActivity::class.java,
            "layout.linear",
        ).use { scenario ->
            waitForUiIdle()
            captureDeviceScreenshot("layouts-benchmark-light")
            scenario.onActivity { activity ->
                val toggle = activity.requireScenarioViewById<android.widget.TextView>(
                    R.id.demo_layout_linear_primary_action,
                )
                val reset = activity.requireScenarioViewById<android.widget.TextView>(
                    R.id.demo_layout_linear_reset,
                )
                assertViewFullyVisible(toggle)
                assertViewFullyVisible(reset)
                assertTextNotEllipsized(toggle)
                assertTextNotEllipsized(reset)
            }
        }
    }

    @Test
    fun collectionsBenchmarkControls_areVisibleAndNotEllipsized() {
        launchDemoScenarioActivity(
            CollectionsActivity::class.java,
            "collection.controls",
        ).use { scenario ->
            waitForUiIdle()
            captureDeviceScreenshot("collections-benchmark-light")
            scenario.onActivity { activity ->
                val toggle = activity.requireScenarioViewById<android.widget.TextView>(
                    R.id.demo_collection_controls_primary_action,
                )
                val reset = activity.requireScenarioViewById<android.widget.TextView>(
                    R.id.demo_collection_controls_reset,
                )
                assertViewFullyVisible(toggle)
                assertViewFullyVisible(reset)
                assertTextNotEllipsized(toggle)
                assertTextNotEllipsized(reset)
                val itemA = activity.requireTextViewByTestTagVisible(
                    DemoTestTags.COLLECTIONS_BENCHMARK_ITEM_A,
                )
                assertTrue(itemA.text.toString().contains("A"))
                activity.clickScenarioViewById(R.id.demo_collection_controls_primary_action)
            }
            waitForUiIdle()
            scenario.onActivity { activity ->
                val rotatedItemA = activity.requireTextViewByTestTagVisible(
                    DemoTestTags.COLLECTIONS_BENCHMARK_ITEM_A,
                )
                assertViewFullyVisible(rotatedItemA)
                assertTrue(rotatedItemA.text.toString().contains("A"))
                activity.clickScenarioViewById(R.id.demo_collection_controls_reset)
            }
            waitForUiIdle()
            scenario.onActivity { activity ->
                val resetItem = activity.requireTextViewByTestTagVisible(
                    DemoTestTags.COLLECTIONS_BENCHMARK_ITEM_A,
                )
                assertViewFullyVisible(resetItem)
                assertTrue(resetItem.text.toString().contains("A"))
            }
        }
    }

    @Test
    fun collectionsList_labelToggle_refreshesVisibleItemLabels() {
        var primaryLabel = ""
        launchDemoScenarioActivity(
            CollectionsActivity::class.java,
            "collection.lazy-list",
        ).use { scenario ->
            waitForUiIdle()
            scenario.onActivity { activity ->
                val toggle = activity.requireTextViewByTestTagVisible(DemoTestTags.COLLECTIONS_LABEL_TOGGLE)
                val itemA = activity.requireTextViewByTestTagVisible(DemoTestTags.COLLECTIONS_LIST_ITEM_A)
                assertViewFullyVisible(toggle)
                assertTrue(itemA.text.toString().contains("A"))
                primaryLabel = itemA.text.toString()
                activity.clickByTestTag(DemoTestTags.COLLECTIONS_LABEL_TOGGLE)
            }
            waitForUiIdle()
            val switchedToAlternate = waitUntilActivityCondition(scenario, timeoutMs = 1_500L) { activity ->
                activity.requireTextViewByTestTagVisible(DemoTestTags.COLLECTIONS_LIST_ITEM_A)
                    .text
                    .toString() != primaryLabel
            }
            assertTrue("Expected visible LazyColumn item to refresh its alternate label", switchedToAlternate)
            scenario.onActivity { activity ->
                val itemA = activity.requireTextViewByTestTagVisible(DemoTestTags.COLLECTIONS_LIST_ITEM_A)
                assertViewFullyVisible(itemA)
                activity.clickByTestTag(DemoTestTags.COLLECTIONS_LABEL_TOGGLE)
            }
            waitForUiIdle()
            val switchedToPrimary = waitUntilActivityCondition(scenario, timeoutMs = 1_500L) { activity ->
                activity.requireTextViewByTestTagVisible(DemoTestTags.COLLECTIONS_LIST_ITEM_A)
                    .text
                    .toString() == primaryLabel
            }
            assertTrue("Expected visible LazyColumn item to restore its primary label", switchedToPrimary)
            scenario.onActivity { activity ->
                val itemA = activity.requireTextViewByTestTagVisible(DemoTestTags.COLLECTIONS_LIST_ITEM_A)
                assertViewFullyVisible(itemA)
            }
        }
    }

    @Test
    fun actionsElevatedCard_clickKeepsShadowZ() {
        launchDemoScenarioActivity(
            activityClass = ActionsActivity::class.java,
            scenarioId = "component.card",
            themeMode = DemoThemeMode.Light,
        ).use { scenario ->
            waitForUiIdle()
            var beforeElevation = 0f
            var beforeZ = 0f
            scenario.onActivity { activity ->
                val elevatedCard = activity.requireScenarioViewByIdVisible<View>(
                    R.id.demo_component_card_primary_action,
                )
                beforeElevation = elevatedCard.elevation
                beforeZ = elevatedCard.z
                assertTrue("Expected elevated card elevation > 0 before click", beforeElevation > 0f)
                assertTrue("Expected elevated card z > 0 before click", beforeZ > 0f)
                elevatedCard.performClick()
            }
            waitForUiIdle()
            scenario.onActivity { activity ->
                val elevatedCard = activity.requireScenarioViewByIdVisible<View>(
                    R.id.demo_component_card_primary_action,
                )
                val afterElevation = elevatedCard.elevation
                val afterZ = elevatedCard.z
                assertTrue("Expected elevated card elevation > 0 after click", afterElevation > 0f)
                assertTrue("Expected elevated card z > 0 after click", afterZ > 0f)
                assertTrue("Expected elevation to remain stable after click", abs(afterElevation - beforeElevation) <= 0.5f)
                assertTrue("Expected z to remain stable after click", abs(afterZ - beforeZ) <= 0.5f)
            }
        }
    }

    @Test
    fun modifiersPage_drawableBackgroundOverridesColorBackground() {
        launchDemoScenarioActivity(
            ModifiersActivity::class.java,
            "modifier.visual",
            themeMode = DemoThemeMode.Light,
        ).use { scenario ->
            waitForUiIdle()
            captureDeviceScreenshot("modifiers-drawable-background-light")
            scenario.onActivity { activity ->
                val colorOnly = activity.requireScenarioViewByIdVisible<View>(
                    R.id.demo_modifier_visual_secondary_target,
                )
                val drawablePreferred = activity.requireScenarioViewByIdVisible<View>(
                    R.id.demo_modifier_visual_target,
                )
                assertViewFullyVisible(colorOnly)
                assertViewFullyVisible(drawablePreferred)
                assertViewBackgroundColor(
                    view = colorOnly,
                    expectedColor = DemoThemeTokens.light.colors.error,
                )
                assertTrue("Expected drawable sample to use layered drawable background", drawablePreferred.background is LayerDrawable)
                assertFalse("Expected color-only sample to keep non-clipped outline by default", colorOnly.clipToOutline)
                assertTrue("Expected drawable sample to auto-clip when cornerRadius is set", drawablePreferred.clipToOutline)
            }
        }
    }

    @Test
    fun modifiersPage_fillMaxHeightMatchesOwningRow() {
        launchDemoScenarioActivity(
            ModifiersActivity::class.java,
            "modifier.sizing",
            themeMode = DemoThemeMode.Light,
        ).use { scenario ->
            waitForUiIdle()
            scenario.onActivity { activity ->
                val target = activity.requireScenarioViewByIdVisible<View>(
                    R.id.demo_modifier_sizing_target,
                )
                val parent = target.parent as View
                assertViewFullyVisible(target)
                assertTrue(
                    "Expected fillMaxHeight target to match its parent: " +
                        "target=${target.height}, parent=${parent.height}",
                    abs(target.height - parent.height) <= 1,
                )
            }
        }
    }

    @Test
    fun modifiersPage_accessibilityAndNativePatchRemainObservable() {
        launchDemoScenarioActivity(
            ModifiersActivity::class.java,
            "modifier.accessibility",
            themeMode = DemoThemeMode.Light,
        ).use { scenario ->
            waitForUiIdle()
            scenario.onActivity { activity ->
                val accessibilityTarget = activity.requireScenarioViewByIdVisible<View>(
                    R.id.demo_modifier_accessibility_target,
                )
                assertEquals(
                    activity.getString(R.string.demo_modifiers_accessibility_description),
                    accessibilityTarget.contentDescription?.toString(),
                )

                val nativeTarget = activity.requireScenarioViewByIdVisible<TextView>(
                    R.id.demo_modifier_accessibility_secondary_target,
                )
                assertTrue("Expected nativeView to apply bold typeface", nativeTarget.typeface.isBold)
                assertTrue(
                    "Expected nativeView to apply letter spacing",
                    abs(nativeTarget.letterSpacing - 0.1f) <= 0.001f,
                )
            }
        }
    }

    @Test
    fun interopBenchmarkControls_andNativeMirror_areVisible() {
        launchDemoScenarioActivity(
            InteropActivity::class.java,
            "interop.android-view",
            themeMode = DemoThemeMode.Light,
        ).use { scenario ->
            waitForUiIdle()
            captureDeviceScreenshot("interop-benchmark-light")
            var mountedNativeView: TextView? = null
            scenario.onActivity { activity ->
                val toggle = activity.requireScenarioViewByIdVisible<TextView>(
                    R.id.demo_interop_android_view_primary_action,
                )
                val reset = activity.requireScenarioViewByIdVisible<TextView>(
                    R.id.demo_interop_android_view_reset,
                )
                val nativeMirror = activity.requireScenarioViewByIdVisible<TextView>(
                    R.id.demo_interop_android_view_target,
                )
                assertViewFullyVisible(toggle)
                assertViewFullyVisible(reset)
                assertViewFullyVisible(nativeMirror)
                assertTextNotEllipsized(toggle)
                assertTextNotEllipsized(reset)
                assertEquals(
                    activity.getString(R.string.demo_interop_native_primary),
                    nativeMirror.text.toString(),
                )
                assertEquals(DemoThemeTokens.light.colors.onSurface, nativeMirror.currentTextColor)
                mountedNativeView = nativeMirror
                activity.clickScenarioViewById(R.id.demo_interop_android_view_primary_action)
            }
            waitForUiIdle()
            scenario.onActivity { activity ->
                val nativeMirror = activity.requireScenarioViewById<TextView>(
                    R.id.demo_interop_android_view_target,
                )
                val declarativeMirror = activity.requireScenarioViewById<TextView>(
                    R.id.demo_interop_android_view_secondary_target,
                )
                assertSame(mountedNativeView, nativeMirror)
                assertEquals(
                    activity.getString(R.string.demo_interop_native_alternate),
                    nativeMirror.text.toString(),
                )
                assertEquals(
                    activity.getString(
                        R.string.demo_interop_declarative_mirror,
                        activity.getString(R.string.demo_interop_native_alternate),
                    ),
                    declarativeMirror.text.toString(),
                )
                DemoThemeSession.mode = DemoThemeMode.Dark
            }
            waitForUiIdle()
            scenario.onActivity { activity ->
                val nativeMirror = activity.requireScenarioViewById<TextView>(
                    R.id.demo_interop_android_view_target,
                )
                assertSame(mountedNativeView, nativeMirror)
                assertEquals(DemoThemeTokens.dark.colors.onSurface, nativeMirror.currentTextColor)
                DemoThemeSession.mode = DemoThemeMode.Light
            }
            waitForUiIdle()
        }
    }

    @Test
    fun inputPage_controlsStayVisibleAndResetFormIsNotEllipsized() {
        launchDemoScenarioActivity(
            InputActivity::class.java,
            "input.fields",
        ).use { scenario ->
            waitForUiIdle()
            captureDeviceScreenshot("input-fields-light")
            scenario.onActivity { activity ->
                val benchmark = activity.requireScenarioViewById<android.widget.TextView>(
                    R.id.demo_input_fields_primary_action,
                )
                val resetBenchmark = activity.requireScenarioViewById<android.widget.TextView>(
                    R.id.demo_input_fields_reset,
                )
                val benchmarkField = activity.requireScenarioViewById<View>(
                    R.id.demo_input_fields_target,
                )
                assertViewFullyVisible(benchmark)
                assertViewFullyVisible(resetBenchmark)
                assertViewFullyVisible(benchmarkField)
                assertTextNotEllipsized(benchmark)
                assertTextNotEllipsized(resetBenchmark)
            }
        }
    }

    @Test
    fun feedbackPage_triggersTransientFlows() {
        launchDemoScenarioActivity(
            FeedbackActivity::class.java,
            "overlay.transient",
        ).use { scenario ->
            waitForUiIdle()
            scenario.onActivity { activity ->
                activity.clickScenarioViewById(R.id.demo_overlay_transient_secondary_action)
            }
            waitForUiIdle()
            assertDeviceResourceIdVisible(R.id.demo_overlay_transient_secondary_target)
            clickDeviceResourceId(R.id.demo_overlay_transient_secondary_target)
            scenario.onActivity { activity ->
                activity.clickScenarioViewById(R.id.demo_overlay_transient_primary_action)
            }
            waitForUiIdle()
            assertDeviceResourceIdVisible(R.id.demo_overlay_transient_target)
            assertDeviceResourceIdVisible(R.id.demo_overlay_transient_reset)
            captureDeviceScreenshot("feedback-transient-light")
            scenario.onActivity { activity ->
                val state = activity.requireScenarioViewById<TextView>(
                    R.id.demo_overlay_transient_state,
                )
                assertEquals(
                    activity.getString(R.string.demo_feedback_transient_state, 1, 1, 1, 1),
                    state.text.toString(),
                )
            }
            clickDeviceResourceId(R.id.demo_overlay_transient_reset)
            waitForUiIdle()
            scenario.onActivity { activity ->
                val state = activity.requireScenarioViewById<TextView>(
                    R.id.demo_overlay_transient_state,
                )
                assertEquals(
                    activity.getString(R.string.demo_feedback_transient_state, 0, 0, 0, 0),
                    state.text.toString(),
                )
            }
        }
    }

    @Test
    fun feedbackPage_modalBottomSheet_showAndDismissFlow() {
        launchDemoScenarioActivity(
            FeedbackActivity::class.java,
            "overlay.dialog",
        ).use { scenario ->
            waitForUiIdle()
            var initial = ""
            scenario.onActivity { activity ->
                initial = activity.requireScenarioViewById<TextView>(
                    R.id.demo_overlay_dialog_state,
                ).text.toString()
                activity.clickScenarioViewById(R.id.demo_overlay_dialog_primary_action)
            }
            waitForUiIdle()
            assertDeviceResourceIdVisible(R.id.demo_overlay_dialog_target)
            assertDeviceResourceIdVisible(R.id.demo_overlay_dialog_reset)
            captureDeviceScreenshot("feedback-bottom-sheet-light")
            clickDeviceResourceId(R.id.demo_overlay_dialog_reset)
            waitForUiIdle()
            scenario.onActivity { activity ->
                assertEquals(
                    initial,
                    activity.requireScenarioViewById<TextView>(
                        R.id.demo_overlay_dialog_state,
                    ).text.toString(),
                )
            }
        }
    }

    @Test
    fun feedbackPage_menuSelectionAndResetFlow() {
        launchDemoScenarioActivity(
            FeedbackActivity::class.java,
            "overlay.menu",
        ).use { scenario ->
            waitForUiIdle()
            var initial = ""
            scenario.onActivity { activity ->
                initial = activity.requireScenarioViewById<TextView>(
                    R.id.demo_overlay_menu_state,
                ).text.toString()
                activity.clickScenarioViewById(R.id.demo_overlay_menu_secondary_action)
            }
            waitForUiIdle()
            assertDeviceResourceIdVisible(R.id.demo_overlay_menu_target)
            captureDeviceScreenshot("overlay-menu-shadow-light")
            clickDeviceResourceId(R.id.demo_overlay_menu_target)
            scenario.onActivity { activity ->
                val state = activity.requireScenarioViewById<TextView>(
                    R.id.demo_overlay_menu_state,
                ).text.toString()
                assertNotEquals(initial, state)
                activity.clickScenarioViewById(R.id.demo_overlay_menu_reset)
            }
            waitForUiIdle()
            scenario.onActivity { activity ->
                assertEquals(
                    initial,
                    activity.requireScenarioViewById<TextView>(
                        R.id.demo_overlay_menu_state,
                    ).text.toString(),
                )
            }
        }
    }

    @Test
    fun inputFields_multilineBioAndSupportingTextRemainFullyVisible() {
        launchDemoScenarioActivity(
            InputActivity::class.java,
            "input.fields",
            themeMode = DemoThemeMode.Light,
        ).use { scenario ->
            waitForUiIdle()
            scenario.onActivity { activity ->
                assertViewFullyVisible(
                    activity.requireViewByTestTagVisible(DemoTestTags.INPUT_BIO_FIELD),
                )
            }
        }
    }

    @Test
    fun inputStress_controlsRemainVisibleAndReadable() {
        launchDemoScenarioActivity(
            InputActivity::class.java,
            "input.stress",
            themeMode = DemoThemeMode.Light,
        ).use { scenario ->
            waitForUiIdle()
            captureDeviceScreenshot("input-stress-light")
            scenario.onActivity { activity ->
                fun assertReadable(view: android.widget.TextView) {
                    assertViewFullyVisible(view)
                    assertTextNotEllipsized(view)
                }

                assertReadable(
                    activity.requireScenarioViewByIdVisible(
                        R.id.demo_input_stress_primary_action,
                    ),
                )
                assertReadable(
                    activity.requireScenarioViewByIdVisible(
                        R.id.demo_input_stress_secondary_action,
                    ),
                )
                assertReadable(
                    activity.requireTextViewByTestTagVisible(DemoTestTags.INPUT_STRESS_ERROR),
                )
                val protectedField = activity.requireScenarioViewByIdVisible<View>(
                    R.id.demo_input_stress_target,
                )
                assertViewFullyVisible(
                    activity.requireViewByTestTagVisible(DemoTestTags.INPUT_STRESS_NOTES_FIELD),
                )
                assertViewFullyVisible(protectedField)
            }
        }
    }

    @Test
    fun inputFocusFollowScrollableColumn_scrollsOnlyEnoughToRevealInput() {
        launchDemoScenarioActivity(
            InputActivity::class.java,
            "input.focus-follow-scrollable-column",
            themeMode = DemoThemeMode.Light,
        ).use { scenario ->
            waitForUiIdle()
            assertFocusActionRevealsInput(
                scenario = scenario,
                resourceId = R.id.demo_input_focus_follow_scrollable_column_target,
            )
        }
    }

    @Test
    fun inputFocusFollowVerticalPager_scrollsOnlyEnoughToRevealInput() {
        launchDemoScenarioActivity(
            InputActivity::class.java,
            "input.focus-follow-vertical-pager",
            themeMode = DemoThemeMode.Light,
        ).use { scenario ->
            waitForUiIdle()
            assertFocusActionRevealsInput(
                scenario = scenario,
                resourceId = R.id.demo_input_focus_follow_vertical_pager_target,
            )
        }
    }

    @Test
    fun inputFocusFollowPullRefresh_scrollsOnlyEnoughToRevealInput() {
        launchDemoScenarioActivity(
            InputActivity::class.java,
            "input.focus-follow-pull-refresh",
            themeMode = DemoThemeMode.Light,
        ).use { scenario ->
            waitForUiIdle()
            assertFocusActionRevealsInput(
                scenario = scenario,
                resourceId = R.id.demo_input_focus_follow_pull_refresh_target,
            )
        }
    }

    @Test
    fun inputFocusFollowLazyColumn_scrollsOnlyEnoughToRevealInput() {
        launchDemoScenarioActivity(
            InputActivity::class.java,
            "input.focus-follow-lazy-column",
            themeMode = DemoThemeMode.Light,
        ).use { scenario ->
            waitForUiIdle()
            assertFocusActionRevealsInput(
                scenario = scenario,
                resourceId = R.id.demo_input_focus_follow_lazy_column_target,
            )
        }
    }

    @Test
    fun inputFocusFollowLazyGrid_scrollsOnlyEnoughToRevealInput() {
        launchDemoScenarioActivity(
            InputActivity::class.java,
            "input.focus-follow-lazy-grid",
            themeMode = DemoThemeMode.Light,
        ).use { scenario ->
            waitForUiIdle()
            assertFocusActionRevealsInput(
                scenario = scenario,
                resourceId = R.id.demo_input_focus_follow_lazy_grid_target,
            )
        }
    }

    @Test
    fun layoutsEdge_viewsRemainVisibleAfterPageJump() {
        launchDemoScenarioActivity(
            LayoutsActivity::class.java,
            "layout.edges",
        ).use { scenario ->
            waitForUiIdle()
            captureDeviceScreenshot("layouts-edge-light")
            scenario.onActivity { activity ->
                val toggle = activity.requireScenarioViewById<android.widget.TextView>(
                    R.id.demo_layout_edges_primary_action,
                )
                val weighted = activity.requireTextViewByTestTag(DemoTestTags.LAYOUTS_EDGE_WEIGHTED)
                val action = activity.requireTextViewByTestTag(DemoTestTags.LAYOUTS_EDGE_ACTION)
                val icon = activity.requireViewByTestTag(DemoTestTags.LAYOUTS_EDGE_PROBE_ICON)
                assertViewFullyVisible(toggle)
                assertViewFullyVisible(weighted)
                assertViewFullyVisible(action)
                assertViewFullyVisible(icon)
                assertTextNotEllipsized(toggle)
                assertTextNotEllipsized(weighted)
                assertTextNotEllipsized(action)
            }
        }
    }

    @Test
    fun layoutsConstraint_coreScenes_keepExpectedRelativePositions() {
        launchDemoScenarioActivity(
            LayoutsActivity::class.java,
            "layout.constraint",
        ).use { scenario ->
            waitForUiIdle()
            captureDeviceScreenshot("layouts-constraint-core-light")
            scenario.onActivity { activity ->
                val basicContainer = activity.requireViewByTestTagVisible(DemoTestTags.LAYOUTS_CONSTRAINT_BASIC_CONTAINER)
                val basicBadge = activity.requireViewByTestTagVisible(DemoTestTags.LAYOUTS_CONSTRAINT_BASIC_BADGE)
                assertViewFullyVisible(basicContainer)
                assertViewFullyVisible(basicBadge)

                val badgeCenterX = viewCenterXOnScreen(basicBadge)
                val basicContainerCenterX = viewCenterXOnScreen(basicContainer)
                assertTrue(
                    "Expected basic badge to stay in right half of its container.",
                    badgeCenterX > basicContainerCenterX,
                )
            }
            var shortMarkerLeft = 0
            var shortSummaryRight = 0
            waitForUiIdle()
            scenario.onActivity { activity ->
                val helpersContainer = activity.requireViewByTestTagVisible(DemoTestTags.LAYOUTS_CONSTRAINT_HELPERS_CONTAINER)
                val helpersHeadline = activity.requireViewByTestTagVisible(DemoTestTags.LAYOUTS_CONSTRAINT_HELPERS_HEADLINE)
                val helpersSummary = activity.requireViewByTestTagVisible(DemoTestTags.LAYOUTS_CONSTRAINT_HELPERS_SUMMARY)
                val helpersMarker = activity.requireViewByTestTagVisible(DemoTestTags.LAYOUTS_CONSTRAINT_HELPERS_MARKER)
                assertViewFullyVisible(helpersContainer)
                assertViewFullyVisible(helpersHeadline)
                assertViewFullyVisible(helpersSummary)
                assertViewFullyVisible(helpersMarker)
                val containerLeft = viewLeftOnScreen(helpersContainer)
                val containerTop = viewTopOnScreen(helpersContainer)
                val containerRight = containerLeft + helpersContainer.width
                val markerLeft = viewLeftOnScreen(helpersMarker)
                val markerRight = markerLeft + helpersMarker.width
                val markerTop = viewTopOnScreen(helpersMarker)
                val headlineRight = viewLeftOnScreen(helpersHeadline) + helpersHeadline.width
                shortSummaryRight = viewLeftOnScreen(helpersSummary) + helpersSummary.width
                shortMarkerLeft = markerLeft
                assertTrue(
                    "Expected helper marker to follow the longest short-copy view without overlap. " +
                        "markerLeft=$markerLeft, headlineRight=$headlineRight, summaryRight=$shortSummaryRight",
                    markerLeft > maxOf(headlineRight, shortSummaryRight),
                )
                assertTrue(
                    "Expected the complete helper marker to stay inside its container. " +
                        "marker=[$markerLeft,$markerRight], container=[$containerLeft,$containerRight]",
                    markerLeft >= containerLeft && markerRight <= containerRight,
                )
                assertTrue(
                    "Expected helper marker to stay near helper container top edge. " +
                        "markerTop=$markerTop, containerTop=$containerTop",
                    markerTop <= containerTop + helpersContainer.height / 3,
                )
                activity.clickByTestTag(DemoTestTags.LAYOUTS_CONSTRAINT_HELPERS_TOGGLE)
            }
            val barrierFollowedLongCopy = waitUntilActivityCondition(scenario, timeoutMs = 1_500L) { activity ->
                val container = activity.requireViewByTestTagVisible(DemoTestTags.LAYOUTS_CONSTRAINT_HELPERS_CONTAINER)
                val headline = activity.requireViewByTestTagVisible(DemoTestTags.LAYOUTS_CONSTRAINT_HELPERS_HEADLINE)
                val summary = activity.requireViewByTestTagVisible(DemoTestTags.LAYOUTS_CONSTRAINT_HELPERS_SUMMARY)
                val marker = activity.requireViewByTestTagVisible(DemoTestTags.LAYOUTS_CONSTRAINT_HELPERS_MARKER)
                val markerLeft = viewLeftOnScreen(marker)
                val markerRight = markerLeft + marker.width
                val headlineRight = viewLeftOnScreen(headline) + headline.width
                val summaryRight = viewLeftOnScreen(summary) + summary.width
                val containerRight = viewLeftOnScreen(container) + container.width
                markerLeft > shortMarkerLeft + (8 * activity.resources.displayMetrics.density).toInt() &&
                    summaryRight > shortSummaryRight &&
                    markerLeft > maxOf(headlineRight, summaryRight) &&
                    markerRight <= containerRight
            }
            assertTrue(
                "Expected the end Barrier to move right with long copy while keeping the marker bounded.",
                barrierFollowedLongCopy,
            )
            waitForUiIdle()
            val chainStable = waitUntilActivityCondition(scenario, timeoutMs = 1_500L) { activity ->
                val chainContainer = activity.requireViewByTestTagVisible(DemoTestTags.LAYOUTS_CONSTRAINT_CHAIN_CONTAINER)
                val chainStart = activity.requireViewByTestTagVisible(DemoTestTags.LAYOUTS_CONSTRAINT_CHAIN_START)
                val chainMiddle = activity.requireViewByTestTagVisible(DemoTestTags.LAYOUTS_CONSTRAINT_CHAIN_MIDDLE)
                val chainEnd = activity.requireViewByTestTagVisible(DemoTestTags.LAYOUTS_CONSTRAINT_CHAIN_END)
                val startCenterX = viewCenterXOnScreen(chainStart)
                val middleCenterX = viewCenterXOnScreen(chainMiddle)
                val endCenterX = viewCenterXOnScreen(chainEnd)
                val containerLeft = viewLeftOnScreen(chainContainer)
                val containerRight = containerLeft + chainContainer.width
                startCenterX < middleCenterX &&
                    middleCenterX < endCenterX &&
                    middleCenterX in containerLeft..containerRight
            }
            assertTrue(
                "Expected chain layout to settle to ascending horizontal order within timeout.",
                chainStable,
            )
        }
    }

    @Test
    fun layoutsConstraint_decoupledConstraintSetToggle_repositionsMarker() {
        launchDemoScenarioActivity(
            LayoutsActivity::class.java,
            "layout.constraint",
        ).use { scenario ->
            waitForUiIdle()
            var beforeLeft = 0
            var beforeTop = 0
            scenario.onActivity { activity ->
                val marker = activity.requireViewByTestTagVisible(DemoTestTags.LAYOUTS_CONSTRAINT_SET_MARKER)
                beforeLeft = viewLeftOnScreen(marker)
                beforeTop = viewTopOnScreen(marker)
                activity.clickByTestTag(DemoTestTags.LAYOUTS_CONSTRAINT_SET_TOGGLE)
            }
            val moved = waitUntilActivityCondition(scenario, timeoutMs = 1_500L) { activity ->
                val marker = activity.requireViewByTestTagVisible(DemoTestTags.LAYOUTS_CONSTRAINT_SET_MARKER)
                val leftDelta = abs(viewLeftOnScreen(marker) - beforeLeft)
                val topDelta = abs(viewTopOnScreen(marker) - beforeTop)
                leftDelta >= 12 || topDelta >= 12
            }
            assertTrue("Expected decoupled constraint set toggle to reposition marker immediately.", moved)
        }
    }

    @Test
    fun layoutsConstraint_virtualHelpersToggle_updatesVisibilityAndPlaceholderHosting() {
        launchDemoScenarioActivity(
            LayoutsActivity::class.java,
            "layout.constraint",
        ).use { scenario ->
            waitForUiIdle()
            var initialStatus = ""
            var observedStatus = ""
            var observedVisibility: Int? = null
            scenario.onActivity { activity ->
                val container = activity.requireViewByTestTagVisible(DemoTestTags.LAYOUTS_CONSTRAINT_VIRTUAL_CONTAINER)
                assertViewFullyVisible(container)
                initialStatus = activity.requireTextViewByTestTag(
                    DemoTestTags.LAYOUTS_CONSTRAINT_VIRTUAL_STATUS,
                ).text.toString()
                assertEquals(
                    View.VISIBLE,
                    activity.requireViewByTestTagVisible(
                        DemoTestTags.LAYOUTS_CONSTRAINT_VIRTUAL_GROUP_MEMBER,
                    ).visibility,
                )
                activity.clickByTestTag(DemoTestTags.LAYOUTS_CONSTRAINT_VIRTUAL_TOGGLE)
            }
            val updated = waitUntilActivityCondition(scenario, timeoutMs = 1_500L) { activity ->
                val status = activity.requireTextViewByTestTag(DemoTestTags.LAYOUTS_CONSTRAINT_VIRTUAL_STATUS)
                val member = findViewByTestTag(
                    activity.findViewById(android.R.id.content),
                    DemoTestTags.LAYOUTS_CONSTRAINT_VIRTUAL_GROUP_MEMBER,
                )
                observedStatus = status.text.toString()
                observedVisibility = member?.visibility
                status.text.toString() != initialStatus && (member == null || member.visibility != View.VISIBLE)
            }
            assertTrue(
                "Expected virtual helper state and visibility to update after toggle. " +
                    "initial=$initialStatus, observed=$observedStatus, visibility=$observedVisibility",
                updated,
            )
        }
    }

    @Test
    fun layoutsConstraint_anchorAndDimensionAdvancedScenes_keepVisibleAndReactive() {
        launchDemoScenarioActivity(
            LayoutsActivity::class.java,
            "layout.constraint",
        ).use { scenario ->
            waitForUiIdle()
            var ratioBeforeWidth = 0
            var ratioBeforeHeight = 0
            scenario.onActivity { activity ->
                val container = activity.requireViewByTestTagVisible(DemoTestTags.LAYOUTS_CONSTRAINT_ANCHOR_ADVANCED_CONTAINER)
                val baseline = activity.requireViewByTestTagVisible(DemoTestTags.LAYOUTS_CONSTRAINT_ANCHOR_ADVANCED_BASELINE)
                val circle = activity.requireViewByTestTagVisible(DemoTestTags.LAYOUTS_CONSTRAINT_ANCHOR_ADVANCED_CIRCLE)
                assertViewFullyVisible(container)
                assertViewFullyVisible(baseline)
                assertViewFullyVisible(circle)

                val ratio = activity.requireViewByTestTagVisible(DemoTestTags.LAYOUTS_CONSTRAINT_DIMENSION_ADVANCED_RATIO)
                ratioBeforeWidth = ratio.width
                ratioBeforeHeight = ratio.height
                activity.clickByTestTag(DemoTestTags.LAYOUTS_CONSTRAINT_DIMENSION_ADVANCED_TOGGLE)
            }
            val ratioUpdated = waitUntilActivityCondition(scenario, timeoutMs = 1_500L) { activity ->
                val ratio = activity.requireViewByTestTagVisible(DemoTestTags.LAYOUTS_CONSTRAINT_DIMENSION_ADVANCED_RATIO)
                abs(ratio.width - ratioBeforeWidth) >= 8 || abs(ratio.height - ratioBeforeHeight) >= 8
            }
            assertTrue("Expected dimension advanced toggle to update ratio card size and status.", ratioUpdated)
        }
    }

    @Test
    fun layoutsConstraint_helpersFullAndVerticalChain_toggleUpdatesLayoutRelations() {
        launchDemoScenarioActivity(
            LayoutsActivity::class.java,
            "layout.constraint",
        ).use { scenario ->
            waitForUiIdle()
            var markerBeforeLeft = 0
            var markerBeforeTop = 0
            var middleBeforeTop = 0
            var helperLeftDelta = 0
            var helperTopDelta = 0
            var initialHelperStatus = ""
            var helperStatus = ""
            scenario.onActivity { activity ->
                val marker = activity.requireViewByTestTagVisible(DemoTestTags.LAYOUTS_CONSTRAINT_HELPERS_FULL_MARKER)
                markerBeforeLeft = viewLeftOnScreen(marker)
                markerBeforeTop = viewTopOnScreen(marker)
                initialHelperStatus = activity.requireTextViewByTestTag(
                    DemoTestTags.LAYOUTS_CONSTRAINT_HELPERS_FULL_STATUS,
                ).text.toString()
                activity.clickByTestTag(DemoTestTags.LAYOUTS_CONSTRAINT_HELPERS_FULL_TOGGLE)
            }
            val helperUpdated = waitUntilActivityCondition(scenario, timeoutMs = 1_500L) { activity ->
                val marker = activity.requireViewByTestTagVisible(DemoTestTags.LAYOUTS_CONSTRAINT_HELPERS_FULL_MARKER)
                helperLeftDelta = abs(viewLeftOnScreen(marker) - markerBeforeLeft)
                helperTopDelta = abs(viewTopOnScreen(marker) - markerBeforeTop)
                helperStatus = activity.requireTextViewByTestTag(
                    DemoTestTags.LAYOUTS_CONSTRAINT_HELPERS_FULL_STATUS,
                ).text.toString()
                helperStatus != initialHelperStatus
            }
            assertTrue(
                "Expected helpers full toggle to publish a new configuration. " +
                    "leftDelta=$helperLeftDelta, topDelta=$helperTopDelta, status=$helperStatus",
                helperUpdated,
            )

            scenario.onActivity { activity ->
                val middle = activity.requireViewByTestTagVisible(DemoTestTags.LAYOUTS_CONSTRAINT_VERTICAL_CHAIN_MIDDLE)
                middleBeforeTop = viewTopOnScreen(middle)
                activity.clickByTestTag(DemoTestTags.LAYOUTS_CONSTRAINT_VERTICAL_CHAIN_TOGGLE)
            }
            val chainUpdated = waitUntilActivityCondition(scenario, timeoutMs = 1_500L) { activity ->
                val middle = activity.requireViewByTestTagVisible(DemoTestTags.LAYOUTS_CONSTRAINT_VERTICAL_CHAIN_MIDDLE)
                abs(viewTopOnScreen(middle) - middleBeforeTop) >= 8
            }
            assertTrue("Expected vertical chain toggle to change chain arrangement and middle item position.", chainUpdated)
        }
    }

    @Test
    fun layoutsConstraint_constraintSetHelperMirror_toggleRepositionsMarker() {
        launchDemoScenarioActivity(
            LayoutsActivity::class.java,
            "layout.constraint",
        ).use { scenario ->
            waitForUiIdle()
            var markerBeforeLeft = 0
            var markerBeforeTop = 0
            scenario.onActivity { activity ->
                val marker = activity.requireViewByTestTagVisible(DemoTestTags.LAYOUTS_CONSTRAINT_SET_HELPERS_MARKER)
                markerBeforeLeft = viewLeftOnScreen(marker)
                markerBeforeTop = viewTopOnScreen(marker)
                activity.clickByTestTag(DemoTestTags.LAYOUTS_CONSTRAINT_SET_HELPERS_TOGGLE)
            }
            val switched = waitUntilActivityCondition(scenario, timeoutMs = 1_500L) { activity ->
                val marker = activity.requireViewByTestTagVisible(DemoTestTags.LAYOUTS_CONSTRAINT_SET_HELPERS_MARKER)
                val leftDelta = abs(viewLeftOnScreen(marker) - markerBeforeLeft)
                val topDelta = abs(viewTopOnScreen(marker) - markerBeforeTop)
                leftDelta >= 10 || topDelta >= 10
            }
            assertTrue("Expected helper mirror constraintSet toggle to reposition marker and switch status.", switched)
        }
    }

    @Test
    fun collectionsStress_toggleUpdatesVisibleControls() {
        launchDemoScenarioActivity(
            CollectionsActivity::class.java,
            "collection.stress",
        ).use { scenario ->
            waitForUiIdle()
            scenario.onActivity { activity ->
                val rotate = activity.requireScenarioViewById<android.widget.TextView>(
                    R.id.demo_collection_stress_primary_action,
                )
                val edge = activity.requireScenarioViewById<android.widget.TextView>(
                    R.id.demo_collection_stress_secondary_action,
                )
                assertViewFullyVisible(rotate)
                assertViewFullyVisible(edge)
                activity.clickScenarioViewById(R.id.demo_collection_stress_secondary_action)
            }
            waitForUiIdle()
            captureDeviceScreenshot("collections-stress-light")
            scenario.onActivity { activity ->
                val activeIds = activity.requireScenarioViewById<android.widget.TextView>(
                    R.id.demo_collection_stress_state,
                )
                assertViewFullyVisible(activeIds)
                assertTextNotEllipsized(activeIds)
                assertTrue(activeIds.text.toString().contains("X"))
            }
        }
    }

    @Test
    fun collectionsStress_rotateOrder_refreshesVisibleIdsAcrossToggles() {
        launchDemoScenarioActivity(
            CollectionsActivity::class.java,
            "collection.stress",
        ).use { scenario ->
            waitForUiIdle()
            scenario.onActivity { activity ->
                val ids = activity.requireScenarioViewById<android.widget.TextView>(
                    R.id.demo_collection_stress_state,
                )
                assertViewFullyVisible(ids)
                assertTrue(ids.text.toString().contains("A -> B -> C -> D"))
                activity.clickScenarioViewById(R.id.demo_collection_stress_primary_action)
            }
            waitForUiIdle()
            scenario.onActivity { activity ->
                val ids = activity.requireScenarioViewById<android.widget.TextView>(
                    R.id.demo_collection_stress_state,
                )
                assertViewFullyVisible(ids)
                assertTrue(ids.text.toString().contains("C -> D -> A -> B"))
                activity.clickScenarioViewById(R.id.demo_collection_stress_primary_action)
            }
            waitForUiIdle()
            captureDeviceScreenshot("collections-stress-rotate-light")
            scenario.onActivity { activity ->
                val ids = activity.requireScenarioViewById<android.widget.TextView>(
                    R.id.demo_collection_stress_state,
                )
                assertViewFullyVisible(ids)
                assertTrue(ids.text.toString().contains("A -> B -> C -> D"))
            }
        }
    }

    @Test
    fun collectionsGrid_spanToggle_refreshesVisibleItemContent() {
        var twoColumnLabel = ""
        launchDemoScenarioActivity(
            CollectionsActivity::class.java,
            "collection.grid",
        ).use { scenario ->
            waitForUiIdle()
            scenario.onActivity { activity ->
                val grid = activity.requireScenarioViewById<View>(R.id.demo_collection_grid_target)
                assertTrue("Expected the rounded grid host to clip its children", grid.clipToOutline)
                val firstItem = activity.requireTextViewByTestTag(DemoTestTags.COLLECTIONS_GRID_FIRST_ITEM)
                assertViewFullyVisible(firstItem)
                twoColumnLabel = firstItem.text.toString()
                activity.clickByTestTag(DemoTestTags.COLLECTIONS_GRID_THREE_COLS)
            }
            waitForUiIdle()
            captureDeviceScreenshot("collections-grid-refresh-light")
            scenario.onActivity { activity ->
                val firstItem = activity.requireTextViewByTestTag(DemoTestTags.COLLECTIONS_GRID_FIRST_ITEM)
                assertViewFullyVisible(firstItem)
                assertNotEquals(twoColumnLabel, firstItem.text.toString())
            }
        }
    }

    @Test
    fun collectionsPullRefresh_downwardGestureStartsAndCompletesRefresh() {
        launchDemoScenarioActivity(
            CollectionsActivity::class.java,
            "collection.pull-refresh",
            themeMode = DemoThemeMode.Light,
        ).use { scenario ->
            waitForUiIdle()
            var centerX = 0
            var startY = 0
            var endY = 0
            scenario.onActivity { activity ->
                val refreshLayout = activity.requireScenarioViewById<View>(
                    R.id.demo_collection_pull_refresh_target,
                )
                val location = IntArray(2)
                refreshLayout.getLocationOnScreen(location)
                centerX = location[0] + refreshLayout.width / 2
                startY = location[1] + refreshLayout.height / 4
                endY = location[1] + refreshLayout.height * 3 / 4
            }
            androidx.test.uiautomator.UiDevice.getInstance(
                androidx.test.platform.app.InstrumentationRegistry.getInstrumentation(),
            ).swipe(centerX, startY, centerX, endY, 24)
            val refreshing = waitUntilActivityCondition(scenario, timeoutMs = 3_000L) { activity ->
                val refreshLayout = activity.requireScenarioViewById<View>(
                    R.id.demo_collection_pull_refresh_target,
                )
                val state = activity.requireScenarioViewById<TextView>(
                    R.id.demo_collection_pull_refresh_state,
                )
                refreshLayout.readBooleanProperty("isRefreshing") &&
                    state.text.toString().contains("1")
            }
            assertTrue("Expected a real downward gesture to start exactly one refresh", refreshing)
            scenario.onActivity { activity ->
                activity.clickScenarioViewById(R.id.demo_collection_pull_refresh_secondary_action)
            }
            val completed = waitUntilActivityCondition(scenario) { activity ->
                !activity.requireScenarioViewById<View>(
                    R.id.demo_collection_pull_refresh_target,
                ).readBooleanProperty("isRefreshing")
            }
            assertTrue("Expected Complete refresh to stop the indicator", completed)
        }
    }

    @Test
    fun navigationBar_selectionChange_updatesSummary() {
        launchDemoScenarioActivity(
            activityClass = NavigationActivity::class.java,
            scenarioId = "component.navigation-bar",
            themeMode = DemoThemeMode.Light,
        ).use { scenario ->
            waitForUiIdle()
            scenario.onActivity { activity ->
                val summary = activity.requireScenarioViewById<android.widget.TextView>(
                    R.id.demo_component_navigation_bar_state,
                )
                assertViewFullyVisible(summary)
                assertTrue(summary.text.toString().contains("0"))
            }
            var downTime = 0L
            var touchX = 0f
            var touchY = 0f
            lateinit var retainedRipple: RippleDrawable
            scenario.onActivity { activity ->
                val navigationBar = activity.requireScenarioViewById<android.view.ViewGroup>(
                    R.id.demo_component_navigation_bar_primary_action,
                )
                val item = navigationBar.getChildAt(1) as ViewGroup
                val iconContainer = item.getChildAt(0) as ViewGroup
                val icon = iconContainer.getChildAt(1)
                touchX = iconContainer.left + icon.left + icon.width / 2f
                touchY = iconContainer.top + icon.top + icon.height / 2f
                retainedRipple = item.foreground as RippleDrawable
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
                    assertTrue(item.dispatchTouchEvent(down))
                } finally {
                    down.recycle()
                }
            }
            SystemClock.sleep(16L)
            scenario.onActivity { activity ->
                val navigationBar = activity.requireScenarioViewById<android.view.ViewGroup>(
                    R.id.demo_component_navigation_bar_primary_action,
                )
                val item = navigationBar.getChildAt(1) as ViewGroup
                val up = MotionEvent.obtain(
                    downTime,
                    SystemClock.uptimeMillis(),
                    MotionEvent.ACTION_UP,
                    touchX,
                    touchY,
                    0,
                )
                try {
                    assertTrue(item.dispatchTouchEvent(up))
                } finally {
                    up.recycle()
                }
            }
            SystemClock.sleep(48L)
            captureDeviceScreenshot("navigation-navbar-icon-quick-release-light")
            waitForUiIdle()
            captureDeviceScreenshot("navigation-navbar-selection-light")
            scenario.onActivity { activity ->
                val navigationBar = activity.requireScenarioViewById<android.view.ViewGroup>(
                    R.id.demo_component_navigation_bar_primary_action,
                )
                val selectedItem = navigationBar.getChildAt(1) as ViewGroup
                assertSame(retainedRipple, selectedItem.foreground)
                val summary = activity.requireScenarioViewById<android.widget.TextView>(
                    R.id.demo_component_navigation_bar_state,
                )
                assertViewFullyVisible(summary)
                assertTrue(summary.text.toString().contains("1"))
            }
        }
    }

    @Test
    fun statePage_viewModelCounter_updatesThroughLifecycleAwareCollection() {
        launchDemoScenarioActivity(
            StateActivity::class.java,
            "runtime.state",
        ).use { scenario ->
            waitForUiIdle()
            scenario.onActivity { activity ->
                val summary = activity.requireTextViewByTestTag(DemoTestTags.STATE_VM_COUNTER)
                assertViewFullyVisible(summary)
                assertTrue(summary.text.toString().contains("0"))
                activity.clickByTestTag(DemoTestTags.STATE_VM_INCREMENT)
            }
            waitForUiIdle()
            captureDeviceScreenshot("state-viewmodel-counter-light")
            scenario.onActivity { activity ->
                val summary = activity.requireTextViewByTestTag(DemoTestTags.STATE_VM_COUNTER)
                assertViewFullyVisible(summary)
                assertTextNotEllipsized(summary)
                assertTrue(summary.text.toString().contains("1"))
            }
        }
    }

    @Test
    fun stateKeyIdentity_actionAndReset_recreateDeterministicFixtureState() {
        launchDemoScenarioActivity(
            StateActivity::class.java,
            "runtime.key-identity",
        ).use { scenario ->
            waitForUiIdle()
            var initialState = ""
            scenario.onActivity { activity ->
                initialState = activity.requireScenarioViewById<android.widget.TextView>(
                    R.id.demo_runtime_key_identity_state,
                ).text.toString()
                activity.clickScenarioViewById(R.id.demo_runtime_key_identity_primary_action)
            }
            waitForUiIdle()
            var hiddenState = ""
            scenario.onActivity { activity ->
                hiddenState = activity.requireScenarioViewById<android.widget.TextView>(
                    R.id.demo_runtime_key_identity_state,
                ).text.toString()
                assertNotEquals(initialState, hiddenState)
                activity.clickScenarioViewById(R.id.demo_runtime_key_identity_reset)
            }
            waitForUiIdle()
            scenario.onActivity { activity ->
                val resetState = activity.requireScenarioViewById<android.widget.TextView>(
                    R.id.demo_runtime_key_identity_state,
                ).text.toString()
                assertEquals(initialState, resetState)
                assertNotEquals(hiddenState, resetState)
                assertViewFullyVisible(
                    activity.requireScenarioViewById(R.id.demo_runtime_key_identity_target),
                )
            }
        }
    }

    @Test
    fun statePatchStress_segmentedControlSummary_updatesAcrossAdvances() {
        launchDemoScenarioActivity(
            StateActivity::class.java,
            "runtime.view-patch",
        ).use { scenario ->
            waitForUiIdle()
            scenario.onActivity { activity ->
                val summary = activity.requireTextViewByTestTag(DemoTestTags.STATE_PATCH_SEGMENT_SUMMARY)
                assertViewFullyVisible(summary)
                assertTrue(summary.text.toString().contains("0"))
                activity.clickScenarioViewByIdVisible(R.id.demo_runtime_view_patch_primary_action)
                activity.clickScenarioViewByIdVisible(R.id.demo_runtime_view_patch_primary_action)
            }
            waitForUiIdle()
            captureDeviceScreenshot("state-patch-segmented-step2-light")
            scenario.onActivity { activity ->
                val summary = activity.requireTextViewByTestTag(DemoTestTags.STATE_PATCH_SEGMENT_SUMMARY)
                assertViewFullyVisible(summary)
                assertTextNotEllipsized(summary)
                assertTrue(summary.text.toString().contains("2"))
            }
        }
    }

    @Test
    fun statePatchStress_tabRowSelection_updatesSummary() {
        launchDemoScenarioActivity(
            StateActivity::class.java,
            "runtime.view-patch",
        ).use { scenario ->
            waitForUiIdle()
            scenario.onActivity { activity ->
                val summary = activity.requireTextViewByTestTag(DemoTestTags.STATE_PATCH_TAB_SUMMARY)
                assertViewFullyVisible(summary)
                assertTrue(summary.text.toString().contains("0"))
            }
            scenario.onActivity { activity ->
                activity.clickByTestTag(DemoTestTags.STATE_PATCH_TAB_DETAILS)
            }
            waitForUiIdle()
            captureDeviceScreenshot("state-patch-tab-selection-light")
            scenario.onActivity { activity ->
                val summary = activity.requireTextViewByTestTag(DemoTestTags.STATE_PATCH_TAB_SUMMARY)
                assertViewFullyVisible(summary)
                assertTextNotEllipsized(summary)
                assertTrue(summary.text.toString().contains("1"))
            }
        }
    }

    @Test
    fun statePatchStress_explicitRevisionRefreshesStableTabPage() {
        launchDemoScenarioActivity(
            StateActivity::class.java,
            "runtime.view-patch",
        ).use { scenario ->
            waitForUiIdle()
            scenario.onActivity { activity ->
                activity.clickScenarioViewByIdVisible(R.id.demo_runtime_view_patch_primary_action)
            }
            waitForUiIdle()
            captureDeviceScreenshot("state-patch-stable-tab-light")
            scenario.onActivity { activity ->
                val summary = activity.requireTextViewByTestTag(DemoTestTags.STATE_STABLE_SUMMARY)
                assertViewFullyVisible(summary)
                assertTextNotEllipsized(summary)
                val text = summary.text.toString()
                assertTrue("expected stable summary to contain 1, actual=$text", text.contains("1"))
            }
        }
    }

    @Test
    fun statePatchStress_horizontalPagerContentUpdatesAcrossExplicitRevisions() {
        launchDemoScenarioActivity(
            StateActivity::class.java,
            "runtime.view-patch",
        ).use { scenario ->
            waitForUiIdle()
            scenario.onActivity { activity ->
                activity.clickScenarioViewByIdVisible(R.id.demo_runtime_view_patch_primary_action)
                activity.clickScenarioViewByIdVisible(R.id.demo_runtime_view_patch_primary_action)
            }
            waitForUiIdle()
            captureDeviceScreenshot("state-patch-stable-tab-step2-light")
            scenario.onActivity { activity ->
                val summary = activity.requireTextViewByTestTag(DemoTestTags.STATE_STABLE_SUMMARY)
                assertViewFullyVisible(summary)
                assertTextNotEllipsized(summary)
                val text = summary.text.toString()
                assertTrue("expected stable summary to contain 2, actual=$text", text.contains("2"))
            }
        }
    }

    @Test
    fun statePatchStress_verticalPagerContentUpdatesAcrossExplicitRevisions() {
        launchDemoScenarioActivity(
            StateActivity::class.java,
            "runtime.view-patch",
        ).use { scenario ->
            waitForUiIdle()
            scenario.onActivity { activity ->
                activity.clickScenarioViewByIdVisible(R.id.demo_runtime_view_patch_primary_action)
                activity.clickScenarioViewByIdVisible(R.id.demo_runtime_view_patch_primary_action)
            }
            waitForUiIdle()
            captureDeviceScreenshot("state-patch-vertical-pager-step2-light")
            scenario.onActivity { activity ->
                val summary = activity.requireTextViewByTestTag(DemoTestTags.STATE_VERTICAL_PAGER_SUMMARY)
                assertViewFullyVisible(summary)
                assertTextNotEllipsized(summary)
                val text = summary.text.toString()
                assertTrue("expected vertical pager summary to contain 2, actual=$text", text.contains("2"))
            }
        }
    }

    @Test
    fun statePatchStress_pagerGesturesSettleOnlyOnTheAdjacentPage() {
        verifyPagerGestures(rtl = false)
    }

    @Test
    fun statePatchStress_rtlPagerGesturesSettleOnlyOnTheAdjacentPage() {
        verifyPagerGestures(rtl = true)
    }

    @Test
    fun animationPage_visibilityToggle_showRestoresTargetContent() {
        launchDemoScenarioActivity(
            AnimationActivity::class.java,
            "animation.core",
            themeMode = DemoThemeMode.Light,
        ).use { scenario ->
            var footerTopBeforeHide = 0
            var footerTopAfterHide = 0
            waitForUiIdle()
            scenario.onActivity { activity ->
                val target = activity.requireScenarioViewByIdVisible<View>(
                    R.id.demo_animation_core_target,
                )
                val footer = activity.requireViewByTestTagVisible(DemoTestTags.ANIMATION_VISIBILITY_FOOTER)
                footerTopBeforeHide = viewTopOnScreen(footer)
                assertViewFullyVisible(target)
                activity.clickScenarioViewByIdVisible(R.id.demo_animation_core_primary_action)
            }
            waitForUiIdle()
            val hiddenMoved = waitUntilActivityCondition(scenario, timeoutMs = 1_500L) { activity ->
                val toggle = activity.requireScenarioViewById<TextView>(
                    R.id.demo_animation_core_primary_action,
                )
                val footer = activity.requireViewByTestTagVisible(DemoTestTags.ANIMATION_VISIBILITY_FOOTER)
                footerTopAfterHide = viewTopOnScreen(footer)
                toggle.text.toString() == activity.getString(R.string.demo_animation_core_show) &&
                    footerTopAfterHide < footerTopBeforeHide
            }
            assertTrue(
                "Expected footer to move up after hide, before=$footerTopBeforeHide, after=$footerTopAfterHide",
                hiddenMoved,
            )
            scenario.onActivity { activity ->
                activity.clickScenarioViewByIdVisible(R.id.demo_animation_core_primary_action)
            }
            waitForUiIdle()
            val shownMoved = waitUntilActivityCondition(scenario, timeoutMs = 1_500L) { activity ->
                val toggle = activity.requireScenarioViewById<TextView>(
                    R.id.demo_animation_core_primary_action,
                )
                val footer = activity.requireViewByTestTagVisible(DemoTestTags.ANIMATION_VISIBILITY_FOOTER)
                val footerTopAfterShow = viewTopOnScreen(footer)
                val target = activity.findViewById<View>(R.id.demo_animation_core_target)
                if (toggle.text.toString() != activity.getString(R.string.demo_animation_core_hide)) {
                    return@waitUntilActivityCondition false
                }
                if (footerTopAfterShow <= footerTopAfterHide) {
                    return@waitUntilActivityCondition false
                }
                target != null && isViewVisible(target)
            }
            assertTrue(
                "Expected footer to move down after show, hidden=$footerTopAfterHide",
                shownMoved,
            )
        }
    }

    @Test
    fun animationPage_contentToggle_updatesAnimatedContentLabel() {
        launchDemoScenarioActivity(
            AnimationActivity::class.java,
            "animation.content",
            themeMode = DemoThemeMode.Light,
        ).use { scenario ->
            waitForUiIdle()
            scenario.onActivity { activity ->
                val label = activity.requireTextViewByTestTag(DemoTestTags.ANIMATION_CONTENT_LABEL)
                assertEquals(activity.getString(R.string.demo_animation_content_primary), label.text.toString())
                activity.clickScenarioViewByIdVisible(R.id.demo_animation_content_primary_action)
            }
            waitForUiIdle()
            val switchedToAlt = waitUntilActivityCondition(scenario, timeoutMs = 1_500L) { activity ->
                val toggle = activity.requireScenarioViewById<TextView>(
                    R.id.demo_animation_content_primary_action,
                )
                val label = activity.requireTextViewByTestTag(DemoTestTags.ANIMATION_CONTENT_LABEL)
                toggle.text.toString() == activity.getString(R.string.demo_animation_content_to_primary) &&
                    label.text.toString() == activity.getString(R.string.demo_animation_content_alternative)
            }
            assertTrue("Expected animated content label to switch to alternative copy", switchedToAlt)
            scenario.onActivity { activity ->
                activity.clickScenarioViewByIdVisible(R.id.demo_animation_content_primary_action)
            }
            waitForUiIdle()
            val switchedBackToMain = waitUntilActivityCondition(scenario, timeoutMs = 1_500L) { activity ->
                val toggle = activity.requireScenarioViewById<TextView>(
                    R.id.demo_animation_content_primary_action,
                )
                val label = activity.requireTextViewByTestTag(DemoTestTags.ANIMATION_CONTENT_LABEL)
                toggle.text.toString() == activity.getString(R.string.demo_animation_content_to_alternative) &&
                    label.text.toString() == activity.getString(R.string.demo_animation_content_primary)
            }
            assertTrue("Expected animated content label to switch back to primary copy", switchedBackToMain)
        }
    }

    @Test
    fun animationPage_listMotion_controlsUpdateFirstItem() {
        launchDemoScenarioActivity(
            AnimationActivity::class.java,
            "animation.list-motion",
            themeMode = DemoThemeMode.Light,
        ).use { scenario ->
            waitForUiIdle()
            scenario.onActivity { activity ->
                val first = activity.requireTextViewByTestTag(DemoTestTags.ANIMATION_LIST_FIRST)
                assertEquals(activity.getString(R.string.demo_animation_list_item_a), first.text.toString())
                activity.clickScenarioViewByIdVisible(R.id.demo_animation_list_motion_primary_action)
            }
            waitForUiIdle()
            scenario.onActivity { activity ->
                val first = activity.requireTextViewByTestTag(DemoTestTags.ANIMATION_LIST_FIRST)
                assertEquals(
                    activity.getString(R.string.demo_animation_list_item_new, 1),
                    first.text.toString(),
                )
                activity.clickScenarioViewByIdVisible(R.id.demo_animation_list_motion_secondary_action)
            }
            waitForUiIdle()
            scenario.onActivity { activity ->
                val first = activity.requireTextViewByTestTag(DemoTestTags.ANIMATION_LIST_FIRST)
                assertEquals(activity.getString(R.string.demo_animation_list_item_a), first.text.toString())
            }
        }
    }

    @Test
    fun animationPage_specsPanel_switchesTypedAndGenericAnimations() {
        launchDemoScenarioActivity(
            AnimationActivity::class.java,
            "animation.specs",
            themeMode = DemoThemeMode.Light,
        ).use { scenario ->
            waitForUiIdle()
            var floatBefore = ""
            var vectorBefore = ""
            scenario.onActivity { activity ->
                floatBefore = activity.requireTextViewByTestTag(DemoTestTags.ANIMATION_SPEC_FLOAT_VALUE).text.toString()
                vectorBefore = activity.requireTextViewByTestTag(DemoTestTags.ANIMATION_SPEC_VECTOR_VALUE).text.toString()
                activity.clickScenarioViewByIdVisible(R.id.demo_animation_specs_secondary_action)
                activity.clickScenarioViewByIdVisible(R.id.demo_animation_specs_primary_action)
                activity.clickByTestTag(DemoTestTags.ANIMATION_SPEC_VECTOR_TOGGLE)
            }
            waitForUiIdle()
            val typedAndGenericUpdated = waitUntilActivityCondition(scenario, timeoutMs = 1_500L) { activity ->
                val floatAfter = activity.requireTextViewByTestTag(DemoTestTags.ANIMATION_SPEC_FLOAT_VALUE).text.toString()
                val vectorAfter = activity.requireTextViewByTestTag(DemoTestTags.ANIMATION_SPEC_VECTOR_VALUE).text.toString()
                floatAfter != floatBefore &&
                    vectorAfter != vectorBefore
            }
            assertTrue("Expected specs panel to update typed and generic animation values", typedAndGenericUpdated)
        }
    }

    @Test
    fun animationContentSize_expansionProbeRemainsVisibleOnNonScrollingStage() {
        launchDemoScenarioActivity(
            AnimationActivity::class.java,
            "animation.content-size",
            themeMode = DemoThemeMode.Light,
        ).use { scenario ->
            waitForUiIdle()
            scenario.onActivity { activity ->
                activity.clickScenarioViewByIdVisible(
                    R.id.demo_animation_content_size_primary_action,
                )
            }
            waitForUiIdle()
            scenario.onActivity { activity ->
                assertViewFullyVisible(
                    activity.requireTextViewByTestTagVisible(
                        DemoTestTags.ANIMATION_SPEC_SIZE_PROBE,
                    ),
                )
                assertViewFullyVisible(
                    activity.requireScenarioViewByIdVisible<View>(
                        R.id.demo_animation_content_size_target,
                    ),
                )
            }
        }
    }

    @Test
    fun animationPage_transitionPanel_updatesAllTransitionChannels() {
        launchDemoScenarioActivity(
            AnimationActivity::class.java,
            "animation.transition",
            themeMode = DemoThemeMode.Light,
        ).use { scenario ->
            waitForUiIdle()
            var alphaBefore = ""
            var intBefore = ""
            var dpBefore = ""
            var colorBefore = ""
            scenario.onActivity { activity ->
                alphaBefore = activity.requireTextViewByTestTag(DemoTestTags.ANIMATION_TRANSITION_ALPHA).text.toString()
                intBefore = activity.requireTextViewByTestTag(DemoTestTags.ANIMATION_TRANSITION_INT).text.toString()
                dpBefore = activity.requireTextViewByTestTag(DemoTestTags.ANIMATION_TRANSITION_DP).text.toString()
                colorBefore = activity.requireTextViewByTestTag(DemoTestTags.ANIMATION_TRANSITION_COLOR).text.toString()
                activity.clickScenarioViewByIdVisible(R.id.demo_animation_transition_primary_action)
            }
            waitForUiIdle()
            var transitionAfterSnapshot = ""
            val transitionValuesUpdated = waitUntilActivityCondition(scenario, timeoutMs = 1_500L) { activity ->
                val alphaAfter = activity.requireTextViewByTestTag(DemoTestTags.ANIMATION_TRANSITION_ALPHA).text.toString()
                val intAfter = activity.requireTextViewByTestTag(DemoTestTags.ANIMATION_TRANSITION_INT).text.toString()
                val dpAfter = activity.requireTextViewByTestTag(DemoTestTags.ANIMATION_TRANSITION_DP).text.toString()
                val colorAfter = activity.requireTextViewByTestTag(DemoTestTags.ANIMATION_TRANSITION_COLOR).text.toString()
                transitionAfterSnapshot = "$alphaAfter, $intAfter, $dpAfter, $colorAfter"
                alphaAfter != alphaBefore &&
                    intAfter != intBefore &&
                    dpAfter != dpBefore &&
                    colorAfter != colorBefore
            }
            assertTrue(
                "Expected transition panel channels to update after toggle; " +
                    "before=[$alphaBefore, $intBefore, $dpBefore, $colorBefore], " +
                    "after=[$transitionAfterSnapshot]",
                transitionValuesUpdated,
            )
        }
    }

    @Test
    fun animationPage_visibilityStatePanel_reportsIdleAndTargetState() {
        launchDemoScenarioActivity(
            AnimationActivity::class.java,
            "animation.transition",
            themeMode = DemoThemeMode.Light,
        ).use { scenario ->
            waitForUiIdle()
            scenario.onActivity { activity ->
                activity.clickScenarioViewByIdVisible(R.id.demo_animation_transition_secondary_action)
                activity.clickByTestTag(DemoTestTags.ANIMATION_ROW_AXIS_TOGGLE)
                activity.clickByTestTag(DemoTestTags.ANIMATION_COLUMN_AXIS_TOGGLE)
            }
            waitForUiIdle()
            var visibilityAfterSnapshot = ""
            val visibilityStateAndAxisUpdated = waitUntilActivityCondition(scenario, timeoutMs = 1_500L) { activity ->
                val status = activity.requireTextViewByTestTag(DemoTestTags.ANIMATION_VISIBILITY_STATE_STATUS).text.toString()
                val rowToggle = activity.requireTextViewByTestTag(DemoTestTags.ANIMATION_ROW_AXIS_TOGGLE).text.toString()
                val columnToggle = activity.requireTextViewByTestTag(DemoTestTags.ANIMATION_COLUMN_AXIS_TOGGLE).text.toString()
                visibilityAfterSnapshot = "$status, row=$rowToggle, column=$columnToggle"
                status == activity.getString(
                    R.string.demo_animation_visibility_status,
                    true,
                    true,
                    true,
                ) &&
                    rowToggle == activity.getString(R.string.demo_animation_row_hide) &&
                    columnToggle == activity.getString(R.string.demo_animation_column_hide)
            }
            assertTrue(
                "Expected visibility state status and axis targets to update; after=[$visibilityAfterSnapshot]",
                visibilityStateAndAxisUpdated,
            )
            var rowShownWidth = 0
            val rowTargetShown = waitUntilActivityCondition(scenario, timeoutMs = 1_000L) { activity ->
                val rowTarget = findViewByTestTag(
                    activity.findViewById<ViewGroup>(android.R.id.content),
                    DemoTestTags.ANIMATION_ROW_AXIS_TARGET,
                )
                if (rowTarget == null || !isViewVisible(rowTarget)) {
                    return@waitUntilActivityCondition false
                }
                rowShownWidth = rowTarget.width
                rowShownWidth > 0
            }
            assertTrue("Expected row-axis visibility target to become visible", rowTargetShown)
            scenario.onActivity { activity ->
                activity.clickByTestTag(DemoTestTags.ANIMATION_ROW_AXIS_TOGGLE)
            }
            val rowTargetExitProgressed = waitUntilActivityCondition(scenario, timeoutMs = 1_000L) { activity ->
                val rowTarget = findViewByTestTag(
                    activity.findViewById<ViewGroup>(android.R.id.content),
                    DemoTestTags.ANIMATION_ROW_AXIS_TARGET,
                )
                if (rowTarget == null || !isViewVisible(rowTarget)) {
                    return@waitUntilActivityCondition true
                }
                val width = rowTarget.width
                width in 1 until rowShownWidth
            }
            assertTrue(
                "Expected row-axis visibility target to shrink during exit or be removed after exit",
                rowTargetExitProgressed,
            )
        }
    }

    @Test
    fun animationPage_infiniteAndAnimatable_controlsAffectRenderedValue() {
        launchDemoScenarioActivity(
            AnimationActivity::class.java,
            "animation.infinite",
            themeMode = DemoThemeMode.Light,
        ).use { scenario ->
            waitForUiIdle()
            scenario.onActivity { activity ->
                activity.requireTextViewByTestTag(DemoTestTags.ANIMATION_INFINITE_VALUE)
                activity.clickByTestTag(DemoTestTags.ANIMATION_INFINITE_REPEAT_MODE)
                activity.clickScenarioViewByIdVisible(R.id.demo_animation_infinite_secondary_action)
            }
            waitForUiIdle()
            val snapHighApplied = waitUntilActivityCondition(scenario, timeoutMs = 1_500L) { activity ->
                val text = activity.requireTextViewByTestTag(DemoTestTags.ANIMATION_ANIMATABLE_VALUE).text.toString()
                extractFirstFloat(text)?.let { it >= 0.99f } == true
            }
            assertTrue("Expected animatable value to reach ~1.0 after snap high", snapHighApplied)
            scenario.onActivity { activity ->
                activity.clickByTestTag(DemoTestTags.ANIMATION_ANIMATABLE_SNAP_LOW)
            }
            waitForUiIdle()
            val snapLowApplied = waitUntilActivityCondition(scenario, timeoutMs = 1_500L) { activity ->
                val text = activity.requireTextViewByTestTag(DemoTestTags.ANIMATION_ANIMATABLE_VALUE).text.toString()
                extractFirstFloat(text)?.let { it <= 0.01f } == true
            }
            assertTrue("Expected animatable value to reach ~0.0 after snap low", snapLowApplied)
        }
    }

    @Test
    fun gesturesPage_tapAndDragSwipe_updateGestureSummaries() {
        launchDemoScenarioActivity(
            activityClass = GesturesActivity::class.java,
            scenarioId = "gesture.drag-swipe",
            themeMode = DemoThemeMode.Light,
        ).use { scenario ->
            waitForUiIdle()
            var dragBefore = 0f
            scenario.onActivity { activity ->
                val dragTarget = activity.requireScenarioViewByIdVisible<View>(
                    R.id.demo_gesture_drag_swipe_target,
                )
                val swipeTarget = activity.requireScenarioViewByIdVisible<View>(
                    R.id.demo_gesture_drag_swipe_secondary_target,
                )
                assertViewFullyVisible(dragTarget)
                assertViewFullyVisible(swipeTarget)
                dragBefore = extractFirstFloat(
                    activity.requireTextViewByTestTag(DemoTestTags.GESTURE_DRAG_VALUE).text.toString(),
                ) ?: 0f
                activity.dragScenarioViewById(
                    id = R.id.demo_gesture_drag_swipe_target,
                    deltaX = 180f,
                )
            }
            waitForUiIdle()
            scenario.onActivity { activity ->
                activity.dragScenarioViewById(
                    id = R.id.demo_gesture_drag_swipe_secondary_target,
                    deltaX = 200f,
                )
            }
            waitForUiIdle()
            var rightAnchorSnapshot = ""
            val movedToRightAnchor = waitUntilActivityCondition(scenario, timeoutMs = 1_500L) { activity ->
                val dragAfterText = activity.requireTextViewByTestTagVisible(DemoTestTags.GESTURE_DRAG_VALUE).text.toString()
                val swipeAfterText = activity.requireTextViewByTestTagVisible(DemoTestTags.GESTURE_SWIPE_VALUE).text.toString()
                val swipeTargetText = activity.requireTextViewByTestTagVisible(DemoTestTags.GESTURE_SWIPE_TARGET_VALUE).text.toString()
                val swipeOffsetText = activity.requireTextViewByTestTagVisible(DemoTestTags.GESTURE_SWIPE_OFFSET_VALUE).text.toString()
                val dragAfter = extractFirstFloat(dragAfterText) ?: dragBefore
                val offset = extractFirstFloat(swipeOffsetText) ?: 0f
                val rightLabel = activity.getString(R.string.demo_gesture_anchor_right)
                rightAnchorSnapshot = "$dragAfterText, $swipeAfterText, $swipeTargetText, $swipeOffsetText"
                abs(dragAfter - dragBefore) >= 12f &&
                    swipeAfterText.contains(rightLabel) &&
                    swipeTargetText.contains(rightLabel) &&
                    offset >= 60f
            }
            assertTrue(
                "Expected drag and swipe summaries to move to right anchor; after=[$rightAnchorSnapshot]",
                movedToRightAnchor,
            )
            scenario.onActivity { activity ->
                activity.dragScenarioViewById(
                    id = R.id.demo_gesture_drag_swipe_secondary_target,
                    deltaX = -420f,
                )
            }
            waitForUiIdle()
            var centerAnchorSnapshot = ""
            val movedToCenterAnchor = waitUntilActivityCondition(scenario, timeoutMs = 1_500L) { activity ->
                val swipeAfterText = activity.requireTextViewByTestTagVisible(DemoTestTags.GESTURE_SWIPE_VALUE).text.toString()
                val swipeTargetText = activity.requireTextViewByTestTagVisible(DemoTestTags.GESTURE_SWIPE_TARGET_VALUE).text.toString()
                val swipeOffsetText = activity.requireTextViewByTestTagVisible(DemoTestTags.GESTURE_SWIPE_OFFSET_VALUE).text.toString()
                val offset = extractFirstFloat(swipeOffsetText) ?: 0f
                val centerLabel = activity.getString(R.string.demo_gesture_anchor_center)
                centerAnchorSnapshot = "$swipeAfterText, $swipeTargetText, $swipeOffsetText"
                swipeAfterText.contains(centerLabel) &&
                    swipeTargetText.contains(centerLabel) &&
                    abs(offset) <= 1f
            }
            assertTrue(
                "Expected one reverse swipe to settle at the adjacent center anchor; " +
                    "after=[$centerAnchorSnapshot]",
                movedToCenterAnchor,
            )
            scenario.onActivity { activity ->
                activity.dragScenarioViewById(
                    id = R.id.demo_gesture_drag_swipe_secondary_target,
                    deltaX = -420f,
                )
            }
            waitForUiIdle()
            var leftAnchorSnapshot = ""
            val movedToLeftAnchor = waitUntilActivityCondition(scenario, timeoutMs = 1_500L) { activity ->
                val swipeAfterText = activity.requireTextViewByTestTagVisible(DemoTestTags.GESTURE_SWIPE_VALUE).text.toString()
                val swipeTargetText = activity.requireTextViewByTestTagVisible(DemoTestTags.GESTURE_SWIPE_TARGET_VALUE).text.toString()
                val swipeOffsetText = activity.requireTextViewByTestTagVisible(DemoTestTags.GESTURE_SWIPE_OFFSET_VALUE).text.toString()
                val offset = extractFirstFloat(swipeOffsetText) ?: 0f
                val leftLabel = activity.getString(R.string.demo_gesture_anchor_left)
                leftAnchorSnapshot = "$swipeAfterText, $swipeTargetText, $swipeOffsetText"
                swipeAfterText.contains(leftLabel) && swipeTargetText.contains(leftLabel) && offset <= -60f
            }
            assertTrue(
                "Expected the second reverse swipe to settle at the adjacent left anchor; " +
                    "after=[$leftAnchorSnapshot]",
                movedToLeftAnchor,
            )
        }
    }

    @Test
    fun gesturesPage_pointerInputConsumed_shortCircuitsCombinedClickable() {
        launchDemoScenarioActivity(
            activityClass = GesturesActivity::class.java,
            scenarioId = "gesture.tap",
            themeMode = DemoThemeMode.Light,
        ).use { scenario ->
            waitForUiIdle()
            scenario.onActivity { activity ->
                val target = activity.requireScenarioViewByIdVisible<View>(
                    R.id.demo_gesture_tap_secondary_target,
                )
                assertViewFullyVisible(target)
                activity.tapScenarioViewById(R.id.demo_gesture_tap_secondary_target)
            }
            waitForUiIdle()
            scenario.onActivity { activity ->
                val count =
                    activity.requireTextViewByTestTag(DemoTestTags.GESTURE_POINTER_CONSUMED_CLICK_COUNT).text.toString()
                assertTrue(
                    "Expected consumed pointer input to suppress combinedClickable click",
                    extractIntegers(count).firstOrNull() == 0,
                )
            }
        }
    }

    @Test
    fun gesturesPage_pointerInputConsumed_andTapTargetStillReceivesClick() {
        launchDemoScenarioActivity(
            activityClass = GesturesActivity::class.java,
            scenarioId = "gesture.tap",
            themeMode = DemoThemeMode.Light,
        ).use { scenario ->
            waitForUiIdle()
            scenario.onActivity { activity ->
                activity.tapScenarioViewById(R.id.demo_gesture_tap_secondary_target)
                activity.tapScenarioViewById(R.id.demo_gesture_tap_target)
            }
            waitForUiIdle()
            val pointerAndTapStable = waitUntilActivityCondition(scenario, timeoutMs = 1_500L) { activity ->
                val consumedText = activity.requireTextViewByTestTag(
                    DemoTestTags.GESTURE_POINTER_CONSUMED_CLICK_COUNT,
                ).text.toString()
                val tapText = activity.requireTextViewByTestTag(
                    DemoTestTags.GESTURE_TAP_COUNT,
                ).text.toString()
                val consumedCounts = extractIntegers(consumedText)
                consumedCounts.getOrNull(0) == 0 &&
                    (consumedCounts.getOrNull(1) ?: 0) >= 1 &&
                    extractCount(tapText) >= 1
            }
            assertTrue(
                "Expected consumed pointer branch to block its click while normal tap target still updates",
                pointerAndTapStable,
            )
        }
    }

    @Test
    fun gesturesPage_transform_updatesPanAndRotationSummaries() {
        launchDemoScenarioActivity(
            activityClass = GesturesActivity::class.java,
            scenarioId = "gesture.transform",
            themeMode = DemoThemeMode.Light,
        ).use { scenario ->
            waitForUiIdle()
            scenario.onActivity { activity ->
                val target = activity.requireScenarioViewByIdVisible<View>(
                    R.id.demo_gesture_transform_target,
                )
                assertViewFullyVisible(target)
                activity.transformScenarioViewById(
                    id = R.id.demo_gesture_transform_target,
                    panX = 140f,
                    panY = 88f,
                    rotationDegrees = 36f,
                    zoomRatio = 1.18f,
                )
            }
            waitForUiIdle()
            val transformUpdated = waitUntilActivityCondition(scenario, timeoutMs = 2_000L) { activity ->
                val text = activity.requireScenarioViewById<TextView>(
                    R.id.demo_gesture_transform_state,
                ).text.toString()
                val metrics = extractTransformMetrics(text) ?: return@waitUntilActivityCondition false
                abs(metrics.panX) >= 8f && abs(metrics.panY) >= 8f && abs(metrics.rotation) >= 8f
            }
            assertTrue("Expected transform gesture to update pan and rotation summaries", transformUpdated)
        }
    }

    @Test
    fun graphicsPage_blendAndDrawContentToggles_updateStatuses() {
        launchDemoScenarioActivity(
            activityClass = GraphicsActivity::class.java,
            scenarioId = "graphics.drawing",
        ).use { scenario ->
            waitForUiIdle()
            captureDeviceScreenshot("graphics-core-light")
            scenario.onActivity { activity ->
                assertViewFullyVisible(
                    activity.requireScenarioViewByIdVisible<View>(R.id.demo_graphics_drawing_target),
                )
                assertViewFullyVisible(activity.requireViewByTestTagVisible(DemoTestTags.GRAPHICS_PATH_CLIP_CANVAS))
                assertViewFullyVisible(activity.requireViewByTestTagVisible(DemoTestTags.GRAPHICS_BLEND_CANVAS))
                assertViewFullyVisible(activity.requireViewByTestTagVisible(DemoTestTags.GRAPHICS_DRAW_CONTENT_CANVAS))
                activity.clickByTestTag(DemoTestTags.GRAPHICS_BLEND_TOGGLE)
                activity.clickByTestTag(DemoTestTags.GRAPHICS_DRAW_CONTENT_TOGGLE)
            }
            waitForUiIdle()
            scenario.onActivity { activity ->
                val blendStatus = activity.requireTextViewByTestTag(DemoTestTags.GRAPHICS_BLEND_STATUS).text.toString()
                val drawStatus = activity.requireTextViewByTestTag(DemoTestTags.GRAPHICS_DRAW_CONTENT_STATUS).text.toString()
                assertEquals(
                    activity.getString(R.string.demo_graphics_blend_status_multiply),
                    blendStatus,
                )
                assertEquals(
                    activity.getString(R.string.demo_graphics_draw_status_hidden),
                    drawStatus,
                )
            }
        }
    }

    @Test
    fun graphicsPage_cacheControls_updateCacheStatusText() {
        launchDemoScenarioActivity(
            activityClass = GraphicsActivity::class.java,
            scenarioId = "graphics.drawing",
        ).use { scenario ->
            waitForUiIdle()
            scenario.onActivity { activity ->
                assertViewFullyVisible(activity.requireViewByTestTagVisible(DemoTestTags.GRAPHICS_CACHE_CANVAS))
                val before = activity.requireTextViewByTestTag(DemoTestTags.GRAPHICS_CACHE_STATUS).text.toString()
                assertTrue(before.contains("cacheKey=0"))
                activity.clickByTestTag(DemoTestTags.GRAPHICS_CACHE_KEY_BUMP)
            }
            waitForUiIdle()
            scenario.onActivity { activity ->
                val after = activity.requireTextViewByTestTag(DemoTestTags.GRAPHICS_CACHE_STATUS).text.toString()
                assertTrue("Expected cache key to increase after bump button", after.contains("cacheKey=1"))
            }
        }
    }

    @Test
    fun diagnosticsPage_rendererRefresh_updatesSnapshotProbes() {
        DemoRenderDiagnosticsStore.reset()
        val intent = DiagnosticsActivity.newIntent(
            context = ApplicationProvider.getApplicationContext(),
            page = DiagnosticsActivity.PAGE_RENDERER,
        )
        launchDemoActivity<DiagnosticsActivity>(intent, themeMode = DemoThemeMode.Light).use { scenario ->
            waitForUiIdle()
            var beforeSequence = ""
            var beforeRenderCount = ""
            var beforeUpdatedAt = ""
            scenario.onActivity { activity ->
                beforeSequence = activity.requireTextViewByTestTag(
                    DemoTestTags.DIAGNOSTICS_RENDER_REFRESH_SEQUENCE,
                ).text.toString()
                beforeRenderCount = activity.requireTextViewByTestTag(
                    DemoTestTags.DIAGNOSTICS_RENDER_COUNT,
                ).text.toString()
                beforeUpdatedAt = activity.requireTextViewByTestTag(
                    DemoTestTags.DIAGNOSTICS_RENDER_UPDATED_AT,
                ).text.toString()
                activity.clickByTestTag(DemoTestTags.DIAGNOSTICS_RENDERER_REFRESH)
            }
            waitForUiIdle()
            val updated = waitUntilActivityCondition(scenario, timeoutMs = 1_500L) { activity ->
                val sequence = activity.requireTextViewByTestTag(
                    DemoTestTags.DIAGNOSTICS_RENDER_REFRESH_SEQUENCE,
                ).text.toString()
                val count = activity.requireTextViewByTestTag(
                    DemoTestTags.DIAGNOSTICS_RENDER_COUNT,
                ).text.toString()
                val updatedAt = activity.requireTextViewByTestTag(
                    DemoTestTags.DIAGNOSTICS_RENDER_UPDATED_AT,
                ).text.toString()
                sequence != beforeSequence &&
                    (count != beforeRenderCount || updatedAt != beforeUpdatedAt)
            }
            assertTrue(
                "Expected diagnostics refresh to update render snapshot probes " +
                    "(beforeSeq=$beforeSequence, beforeCount=$beforeRenderCount, beforeAt=$beforeUpdatedAt).",
                updated,
            )
        }
    }

    @Test
    fun statePatchStress_openDiagnostics_showsPatchActiveSnapshotProbe() {
        DemoRenderDiagnosticsStore.reset()
        launchDemoScenarioActivity(
            StateActivity::class.java,
            "runtime.view-patch",
        ).use { scenario ->
            waitForUiIdle()
            scenario.onActivity { activity ->
                activity.clickScenarioViewByIdVisible(R.id.demo_runtime_view_patch_primary_action)
                activity.clickScenarioViewByIdVisible(R.id.demo_runtime_view_patch_primary_action)
            }
            waitForUiIdle()
        }

        val diagnosticsIntent = DiagnosticsActivity.newIntent(
            context = ApplicationProvider.getApplicationContext(),
            page = DiagnosticsActivity.PAGE_RENDERER,
            autoRefreshRendererSnapshot = true,
            entryHint = "UI test: state patch stress",
        )
        launchDemoActivity<DiagnosticsActivity>(diagnosticsIntent, themeMode = DemoThemeMode.Light).use { scenario ->
            waitForUiIdle()
            val patchCaptured = waitUntilActivityCondition(scenario, timeoutMs = 2_000L) { activity ->
                val patchedText = activity.requireTextViewByTestTag(
                    DemoTestTags.DIAGNOSTICS_PATCH_ACTIVE_PATCHED,
                ).text.toString()
                val capturedAtText = activity.requireTextViewByTestTag(
                    DemoTestTags.DIAGNOSTICS_PATCH_ACTIVE_CAPTURED_AT,
                ).text.toString()
                val patched = extractCount(patchedText)
                patched > 0 && !capturedAtText.contains(
                    activity.getString(R.string.demo_diagnostics_not_captured),
                )
            }
            assertTrue(
                "Expected state patch stress updates to appear in diagnostics patch-active snapshot probes.",
                patchCaptured,
            )
        }
    }

    private fun extractCount(text: String): Int {
        return "(\\d+)".toRegex().find(text)?.value?.toIntOrNull() ?: 0
    }

    private fun extractFirstFloat(text: String): Float? {
        return "(-?\\d+(?:\\.\\d+)?)".toRegex().find(text)?.value?.toFloatOrNull()
    }

    private fun extractIntegers(text: String): List<Int> {
        return "-?\\d+".toRegex().findAll(text).mapNotNull { match ->
            match.value.toIntOrNull()
        }.toList()
    }

    private fun verifyPagerGestures(rtl: Boolean) {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val intent = Intent(context, StateActivity::class.java)
            .putExtra(EXTRA_DEMO_SCENARIO_ID, "runtime.view-patch")
            .withDemoVerificationEnvironment(
                localeTag = if (rtl) "ar" else "en",
                rtl = rtl,
                fontScale = 1f,
                densityScale = 1f,
            )
        launchDemoActivity<StateActivity>(intent).use { scenario ->
            waitForUiIdle()
            scenario.onActivity { activity ->
                val summary = activity.requireTextViewByTestTagVisible(
                    DemoTestTags.STATE_HORIZONTAL_PAGER_SUMMARY,
                )
                var pagerAncestor: View? = summary
                while (pagerAncestor != null && pagerAncestor !is RecyclerView) {
                    pagerAncestor = pagerAncestor.parent as? View
                }
                assertNotNull("Expected a RecyclerView pager ancestor.", pagerAncestor)
                assertEquals(
                    if (rtl) View.LAYOUT_DIRECTION_RTL else View.LAYOUT_DIRECTION_LTR,
                    pagerAncestor?.layoutDirection,
                )
                activity.dragByTestTag(
                    tag = DemoTestTags.STATE_HORIZONTAL_PAGER_SUMMARY,
                    deltaX = if (rtl) 700f else -700f,
                    steps = 12,
                )
            }
            val horizontalSettled = waitUntilActivityCondition(scenario, timeoutMs = 2_000L) { activity ->
                val target = findViewByTestTag(
                    activity.findViewById(android.R.id.content),
                    DemoTestTags.STATE_HORIZONTAL_PAGER_DETAILS,
                ) ?: return@waitUntilActivityCondition false
                val bounds = Rect()
                target.isShown && target.getGlobalVisibleRect(bounds) && !bounds.isEmpty
            }
            assertTrue("Expected the horizontal pager to settle on its adjacent page.", horizontalSettled)
            scenario.onActivity { activity ->
                assertViewFullyVisible(
                    activity.requireTextViewByTestTagVisible(
                        DemoTestTags.STATE_HORIZONTAL_PAGER_DETAILS,
                    ),
                )
                activity.requireTextViewByTestTagVisible(
                    DemoTestTags.STATE_VERTICAL_PAGER_SUMMARY,
                )
                activity.dragByTestTag(
                    tag = DemoTestTags.STATE_VERTICAL_PAGER_SUMMARY,
                    deltaX = 0f,
                    deltaY = -420f,
                    steps = 12,
                )
            }
            val verticalSettled = waitUntilActivityCondition(scenario, timeoutMs = 2_000L) { activity ->
                val target = findViewByTestTag(
                    activity.findViewById(android.R.id.content),
                    DemoTestTags.STATE_VERTICAL_PAGER_DETAILS,
                ) ?: return@waitUntilActivityCondition false
                val bounds = Rect()
                target.isShown && target.getGlobalVisibleRect(bounds) && !bounds.isEmpty
            }
            assertTrue("Expected the vertical pager to settle on its adjacent page.", verticalSettled)
            scenario.onActivity { activity ->
                assertViewFullyVisible(
                    activity.requireTextViewByTestTagVisible(
                        DemoTestTags.STATE_VERTICAL_PAGER_DETAILS,
                    ),
                )
            }
        }
    }

    private fun extractTransformMetrics(text: String): TransformMetrics? {
        val values = "-?\\d+(?:\\.\\d+)?".toRegex()
            .findAll(text)
            .mapNotNull { match -> match.value.toFloatOrNull() }
            .toList()
        if (values.size < 4) return null
        return TransformMetrics(
            scale = values[0],
            panX = values[1],
            panY = values[2],
            rotation = values[3],
        )
    }

    private data class TransformMetrics(
        val scale: Float,
        val panX: Float,
        val panY: Float,
        val rotation: Float,
    )

    private fun assertFocusActionRevealsInput(
        scenario: ActivityScenario<InputActivity>,
        tag: String,
    ) = assertFocusActionRevealsInput(
        scenario = scenario,
        targetDescription = "testTag=$tag",
        resolveHost = { requireViewByTestTagVisible(tag) },
        requestFocus = { focusInputByTestTag(tag) },
    )

    private fun assertFocusActionRevealsInput(
        scenario: ActivityScenario<InputActivity>,
        resourceId: Int,
    ) = assertFocusActionRevealsInput(
        scenario = scenario,
        targetDescription = "resourceId=$resourceId",
        resolveHost = {
            checkNotNull(findViewById(resourceId)) {
                "Expected scenario resource target: $resourceId"
            }
        },
        requestFocus = { focusInputByScenarioViewId(resourceId) },
    )

    private fun assertFocusActionRevealsInput(
        scenario: ActivityScenario<InputActivity>,
        targetDescription: String,
        resolveHost: InputActivity.() -> View,
        requestFocus: InputActivity.() -> Unit,
    ) {
        assertTrue(
            "Expected scenario window focus before requesting editor focus: $targetDescription",
            waitUntilActivityCondition(scenario, timeoutMs = 3_000L) { activity ->
                activity.hasWindowFocus()
            },
        )
        var beforeVisibleHeight = 0
        scenario.onActivity { activity ->
            val inputHost = activity.resolveHost()
            beforeVisibleHeight = Rect().also(inputHost::getGlobalVisibleRect).height()
            activity.requestFocus()
        }
        var focusVisibilityDiagnostics = "IME visibility was not observed."
        val revealedAboveKeyboard = waitUntilActivityCondition(scenario, timeoutMs = 3_000L) { activity ->
            val focusedHost = activity.resolveHost()
            val root = activity.window.decorView
            val rootInsets = ViewCompat.getRootWindowInsets(root) ?: return@waitUntilActivityCondition false
            if (!rootInsets.isVisible(WindowInsetsCompat.Type.ime())) {
                return@waitUntilActivityCondition false
            }
            val rootLocation = IntArray(2)
            root.getLocationOnScreen(rootLocation)
            val imeTop = rootLocation[1] + root.height -
                rootInsets.getInsets(WindowInsetsCompat.Type.ime()).bottom
            val visibleBounds = Rect()
            val hasVisibleBounds = focusedHost.getGlobalVisibleRect(visibleBounds)
            focusVisibilityDiagnostics = buildString {
                append("host=")
                append(focusedHost.javaClass.simpleName)
                append(" size=")
                append(focusedHost.width)
                append('x')
                append(focusedHost.height)
                append(" visible=")
                append(visibleBounds)
                append(" imeTop=")
                append(imeTop)
                var child: View = focusedHost
                var ancestor = focusedHost.parent
                while (ancestor is View) {
                    append(" <- ")
                    append(ancestor.javaClass.simpleName)
                    append("(top=")
                    append(ancestor.top)
                    append(",height=")
                    append(ancestor.height)
                    append(",scrollY=")
                    append(ancestor.scrollY)
                    append(",childTop=")
                    append(child.top)
                    append(')')
                    child = ancestor
                    ancestor = ancestor.parent
                }
            }
            hasVisibleBounds &&
                visibleBounds.height() == focusedHost.height &&
                visibleBounds.bottom <= imeTop
        }
        assertTrue(
            "Expected complete focused host above the visible IME: $targetDescription; " +
                focusVisibilityDiagnostics,
            revealedAboveKeyboard,
        )
        scenario.onActivity { activity ->
            val focusedHost = activity.resolveHost()
            val focusedInput = focusedHost.findFocus()
            assertNotNull("Expected input to retain focus: $targetDescription", focusedInput)
            assertTrue(
                "Expected focused descendant to remain a visible text editor: $targetDescription",
                focusedInput!!.onCheckIsTextEditor() && isViewVisible(focusedInput),
            )
            val visibleHeight = Rect().also(focusedHost::getGlobalVisibleRect).height()
            assertEquals(
                "Expected the complete focused host height to be visible: $targetDescription",
                focusedHost.height,
                visibleHeight,
            )
            assertTrue(
                "Expected focus follow to preserve or improve input visibility: $targetDescription",
                visibleHeight >= beforeVisibleHeight,
            )
        }
    }

    private fun viewTopOnScreen(view: View): Int {
        val location = IntArray(2)
        view.getLocationOnScreen(location)
        return location[1]
    }

    private fun viewLeftOnScreen(view: View): Int {
        val location = IntArray(2)
        view.getLocationOnScreen(location)
        return location[0]
    }

    private fun viewCenterXOnScreen(view: View): Int {
        return viewLeftOnScreen(view) + (view.width / 2)
    }

    private fun isViewVisible(view: View): Boolean {
        val rect = Rect()
        return view.getGlobalVisibleRect(rect) && !rect.isEmpty
    }

    private fun View.readBooleanProperty(getterName: String): Boolean {
        return javaClass.getMethod(getterName).invoke(this) as Boolean
    }

    private fun <T : Activity> waitUntilActivityCondition(
        scenario: ActivityScenario<T>,
        timeoutMs: Long = 1_000L,
        intervalMs: Long = 32L,
        condition: (T) -> Boolean,
    ): Boolean {
        val deadline = SystemClock.uptimeMillis() + timeoutMs
        while (SystemClock.uptimeMillis() < deadline) {
            var matched = false
            scenario.onActivity { activity ->
                matched = condition(activity)
            }
            if (matched) {
                return true
            }
            SystemClock.sleep(intervalMs)
        }
        var matched = false
        scenario.onActivity { activity ->
            matched = condition(activity)
        }
        return matched
    }
}
