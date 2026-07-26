package com.viewcompose

import android.app.Activity
import android.content.Intent
import android.graphics.Rect
import android.graphics.drawable.LayerDrawable
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.android.material.shape.MaterialShapeDrawable
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.abs

@RunWith(AndroidJUnit4::class)
class DemoVisualUiTest {
    @Test
    fun layoutsBenchmarkControls_areVisibleAndNotEllipsized() {
        launchDemoActivity(LayoutsActivity::class.java).use { scenario ->
            waitForUiIdle()
            captureDeviceScreenshot("layouts-benchmark-light")
            scenario.onActivity { activity ->
                val toggle = activity.requireTextViewByTestTag(DemoTestTags.LAYOUTS_BENCHMARK_TOGGLE)
                val reset = activity.requireTextViewByTestTag(DemoTestTags.LAYOUTS_BENCHMARK_RESET)
                assertViewFullyVisible(toggle)
                assertViewFullyVisible(reset)
                assertTextNotEllipsized(toggle)
                assertTextNotEllipsized(reset)
            }
        }
    }

    @Test
    fun collectionsBenchmarkControls_areVisibleAndNotEllipsized() {
        launchDemoActivity(CollectionsActivity::class.java).use { scenario ->
            waitForUiIdle()
            captureDeviceScreenshot("collections-benchmark-light")
            scenario.onActivity { activity ->
                val toggle = activity.requireTextViewByTestTag(DemoTestTags.COLLECTIONS_BENCHMARK_TOGGLE)
                val reset = activity.requireTextViewByTestTag(DemoTestTags.COLLECTIONS_BENCHMARK_RESET)
                assertViewFullyVisible(toggle)
                assertViewFullyVisible(reset)
                assertTextNotEllipsized(toggle)
                assertTextNotEllipsized(reset)
                activity.requireTextView("Benchmark 项 A")
                activity.clickByTestTag(DemoTestTags.COLLECTIONS_BENCHMARK_TOGGLE)
            }
            waitForUiIdle()
            scenario.onActivity { activity ->
                val rotatedItem = activity.requireTextView("Benchmark 项 C 展开")
                assertViewFullyVisible(rotatedItem)
                activity.clickByTestTag(DemoTestTags.COLLECTIONS_BENCHMARK_RESET)
            }
            waitForUiIdle()
            scenario.onActivity { activity ->
                val resetItem = activity.requireTextView("Benchmark 项 A")
                assertViewFullyVisible(resetItem)
            }
        }
    }

    @Test
    fun collectionsList_labelToggle_refreshesVisibleItemLabels() {
        val intent = Intent(
            ApplicationProvider.getApplicationContext(),
            CollectionsActivity::class.java,
        ).putExtra(EXTRA_COLLECTIONS_PAGE_INDEX, 1)
        launchDemoActivity<CollectionsActivity>(intent, themeMode = DemoThemeMode.Light).use { scenario ->
            waitForUiIdle()
            scenario.onActivity { activity ->
                val toggle = activity.requireTextViewByTestTag(DemoTestTags.COLLECTIONS_LABEL_TOGGLE)
                val itemA = activity.requireTextViewByTestTag(DemoTestTags.COLLECTIONS_LIST_ITEM_A)
                assertViewFullyVisible(toggle)
                assertTrue(itemA.text.toString().contains("Lazy 项 A"))
                activity.clickByTestTag(DemoTestTags.COLLECTIONS_LABEL_TOGGLE)
            }
            waitForUiIdle()
            scenario.onActivity { activity ->
                val itemA = activity.requireTextViewByTestTag(DemoTestTags.COLLECTIONS_LIST_ITEM_A)
                assertViewFullyVisible(itemA)
                assertTrue(itemA.text.toString().contains("（替代）"))
                activity.clickByTestTag(DemoTestTags.COLLECTIONS_LABEL_TOGGLE)
            }
            waitForUiIdle()
            scenario.onActivity { activity ->
                val itemA = activity.requireTextViewByTestTag(DemoTestTags.COLLECTIONS_LIST_ITEM_A)
                assertViewFullyVisible(itemA)
                assertTrue(itemA.text.toString().contains("Lazy 项 A"))
                assertTrue(!itemA.text.toString().contains("（替代）"))
            }
        }
    }

    @Test
    fun actionsElevatedCard_clickKeepsShadowZ() {
        val intent = Intent(
            ApplicationProvider.getApplicationContext(),
            ActionsActivity::class.java,
        ).putExtra(EXTRA_ACTIONS_PAGE_INDEX, 0)
        launchDemoActivity<ActionsActivity>(intent, themeMode = DemoThemeMode.Light).use { scenario ->
            waitForUiIdle()
            var beforeElevation = 0f
            var beforeZ = 0f
            scenario.onActivity { activity ->
                val elevatedCard = activity.requireViewByTestTagVisible(DemoTestTags.ACTIONS_ELEVATED_CARD)
                beforeElevation = elevatedCard.elevation
                beforeZ = elevatedCard.z
                assertTrue("Expected elevated card elevation > 0 before click", beforeElevation > 0f)
                assertTrue("Expected elevated card z > 0 before click", beforeZ > 0f)
                activity.clickByTestTag(DemoTestTags.ACTIONS_ELEVATED_CARD)
            }
            waitForUiIdle()
            scenario.onActivity { activity ->
                val elevatedCard = activity.requireViewByTestTagVisible(DemoTestTags.ACTIONS_ELEVATED_CARD)
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
        val intent = Intent(
            ApplicationProvider.getApplicationContext(),
            ModifiersActivity::class.java,
        ).putExtra(EXTRA_MODIFIERS_PAGE_INDEX, 0)
        launchDemoActivity<ModifiersActivity>(intent, themeMode = DemoThemeMode.Light).use { scenario ->
            waitForUiIdle()
            captureDeviceScreenshot("modifiers-drawable-background-light")
            scenario.onActivity { activity ->
                val colorOnly = activity.requireViewByTestTagVisible(DemoTestTags.MODIFIERS_DRAWABLE_BACKGROUND_COLOR_ONLY)
                val drawablePreferred = activity.requireViewByTestTagVisible(DemoTestTags.MODIFIERS_DRAWABLE_BACKGROUND_SAMPLE)
                assertViewFullyVisible(colorOnly)
                assertViewFullyVisible(drawablePreferred)
                assertTrue(
                    "Expected color-only sample to use MaterialShapeDrawable background",
                    colorOnly.background is MaterialShapeDrawable,
                )
                assertTrue("Expected drawable sample to use layered drawable background", drawablePreferred.background is LayerDrawable)
                assertFalse("Expected color-only sample to keep non-clipped outline by default", colorOnly.clipToOutline)
                assertTrue("Expected drawable sample to auto-clip when cornerRadius is set", drawablePreferred.clipToOutline)
            }
        }
    }

    @Test
    fun interopBenchmarkControls_andNativeMirror_areVisible() {
        launchDemoActivity(InteropActivity::class.java).use { scenario ->
            waitForUiIdle()
            captureDeviceScreenshot("interop-benchmark-light")
            scenario.onActivity { activity ->
                val toggle = activity.requireTextViewByTestTag(DemoTestTags.INTEROP_BENCHMARK_TOGGLE)
                val reset = activity.requireTextViewByTestTag(DemoTestTags.INTEROP_BENCHMARK_RESET)
                val nativeMirror = activity.requireTextViewByTestTag(DemoTestTags.INTEROP_BENCHMARK_NATIVE_TEXT)
                assertViewFullyVisible(toggle)
                assertViewFullyVisible(reset)
                assertViewFullyVisible(nativeMirror)
                assertTextNotEllipsized(toggle)
                assertTextNotEllipsized(reset)
                assertEquals("原生 benchmark TextView: 主要", nativeMirror.text.toString())
                activity.clickByTestTag(DemoTestTags.INTEROP_BENCHMARK_TOGGLE)
            }
            waitForUiIdle()
            scenario.onActivity { activity ->
                val nativeMirror = activity.requireTextViewByTestTag(DemoTestTags.INTEROP_BENCHMARK_NATIVE_TEXT)
                assertEquals("原生 benchmark TextView: 替代", nativeMirror.text.toString())
            }
        }
    }

    @Test
    fun foundationsBenchmarkButtons_areVisibleAndNotEllipsized() {
        launchDemoActivity(FoundationsActivity::class.java).use { scenario ->
            waitForUiIdle()
            captureDeviceScreenshot("foundations-benchmark-light")
            scenario.onActivity { activity ->
                val toggle = activity.requireTextViewByTestTag(DemoTestTags.FOUNDATIONS_BENCHMARK_TOGGLE)
                val reset = activity.requireTextViewByTestTag(DemoTestTags.FOUNDATIONS_BENCHMARK_RESET)
                assertViewFullyVisible(toggle)
                assertViewFullyVisible(reset)
                assertTextNotEllipsized(toggle)
                assertTextNotEllipsized(reset)
            }
        }
    }

    @Test
    fun inputPage_controlsStayVisibleAndResetFormIsNotEllipsized() {
        launchDemoActivity(InputActivity::class.java).use { scenario ->
            waitForUiIdle()
            captureDeviceScreenshot("input-fields-light")
            scenario.onActivity { activity ->
                val benchmark = activity.requireTextViewByTestTag(DemoTestTags.INPUT_BENCHMARK_TOGGLE)
                val resetBenchmark = activity.requireTextViewByTestTag(DemoTestTags.INPUT_BENCHMARK_RESET)
                val benchmarkField = activity.requireViewByTestTag(DemoTestTags.INPUT_BENCHMARK_FIELD)
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
        launchDemoActivity(FeedbackActivity::class.java).use { scenario ->
            waitForUiIdle()
            scenario.onActivity { activity ->
                activity.clickByTestTag(DemoTestTags.FEEDBACK_SHOW_SNACKBAR)
                activity.clickByTestTag(DemoTestTags.FEEDBACK_SHOW_TOAST)
                activity.clickByTestTag(DemoTestTags.FEEDBACK_SHOW_DIALOG)
                activity.clickByTestTag(DemoTestTags.FEEDBACK_SHOW_POPUP)
            }
            waitForUiIdle()
            captureDeviceScreenshot("feedback-transient-light")
            scenario.onActivity { activity ->
                val dialogCount = activity.requireTextViewByTestTag(DemoTestTags.FEEDBACK_DIALOG_COUNT)
                val popupCount = activity.requireTextViewByTestTag(DemoTestTags.FEEDBACK_POPUP_COUNT)
                val toastCount = activity.requireTextViewByTestTag(DemoTestTags.FEEDBACK_TOAST_COUNT)
                assertViewFullyVisible(dialogCount)
                assertViewFullyVisible(popupCount)
                assertViewFullyVisible(toastCount)
                assertEquals(1, extractCount(dialogCount.text.toString()))
                assertEquals(1, extractCount(popupCount.text.toString()))
                assertEquals(1, extractCount(toastCount.text.toString()))
                activity.clickByTestTag(DemoTestTags.FEEDBACK_RESET)
            }
            waitForUiIdle()
            scenario.onActivity { activity ->
                val dialogCount = activity.requireTextViewByTestTag(DemoTestTags.FEEDBACK_DIALOG_COUNT)
                val popupCount = activity.requireTextViewByTestTag(DemoTestTags.FEEDBACK_POPUP_COUNT)
                val toastCount = activity.requireTextViewByTestTag(DemoTestTags.FEEDBACK_TOAST_COUNT)
                val lastEvent = activity.requireTextViewByTestTag(DemoTestTags.FEEDBACK_LAST_EVENT)
                assertEquals(0, extractCount(dialogCount.text.toString()))
                assertEquals(0, extractCount(popupCount.text.toString()))
                assertEquals(0, extractCount(toastCount.text.toString()))
                assertTrue(lastEvent.text.toString().contains("空闲"))
            }
        }
    }

    @Test
    fun feedbackPage_modalBottomSheet_showAndDismissFlow() {
        val intent = Intent(
            ApplicationProvider.getApplicationContext(),
            FeedbackActivity::class.java,
        ).putExtra(EXTRA_FEEDBACK_PAGE_INDEX, 1)
        launchDemoActivity<FeedbackActivity>(intent, themeMode = DemoThemeMode.Light).use { scenario ->
            waitForUiIdle()
            scenario.onActivity { activity ->
                activity.clickByTestTag(DemoTestTags.FEEDBACK_SHOW_BOTTOM_SHEET)
            }
            waitForUiIdle()
            assertDeviceTextVisible("底部弹窗")
            captureDeviceScreenshot("feedback-bottom-sheet-light")
            clickDeviceText("关闭")
            waitForUiIdle()
            scenario.onActivity { activity ->
                val lastEvent = activity.requireTextViewByTestTag(DemoTestTags.FEEDBACK_LAST_EVENT)
                assertTrue(lastEvent.text.toString().contains("BottomSheet 关闭"))
            }
        }
    }

    @Test
    fun inputStress_controlsRemainVisibleAndReadable() {
        val intent = Intent(
            ApplicationProvider.getApplicationContext(),
            InputActivity::class.java,
        ).putExtra(EXTRA_INPUT_PAGE_INDEX, 2)
        launchDemoActivity<InputActivity>(intent, themeMode = DemoThemeMode.Light).use { scenario ->
            waitForUiIdle()
            captureDeviceScreenshot("input-stress-light")
            scenario.onActivity { activity ->
                val expanded = activity.requireTextViewByTestTag(DemoTestTags.INPUT_STRESS_EXPAND)
                val readonly = activity.requireTextViewByTestTag(DemoTestTags.INPUT_STRESS_READONLY)
                val error = activity.requireTextViewByTestTag(DemoTestTags.INPUT_STRESS_ERROR)
                val protectedField = activity.requireViewByTestTag(DemoTestTags.INPUT_STRESS_PROTECTED_FIELD)
                assertViewFullyVisible(expanded)
                assertViewFullyVisible(readonly)
                assertViewFullyVisible(error)
                assertViewFullyVisible(protectedField)
                assertTextNotEllipsized(expanded)
                assertTextNotEllipsized(readonly)
                assertTextNotEllipsized(error)
            }
        }
    }

    @Test
    fun inputSearch_focusSearchBar_doesNotAutoScrollList() {
        val intent = Intent(
            ApplicationProvider.getApplicationContext(),
            InputActivity::class.java,
        ).putExtra(EXTRA_INPUT_PAGE_INDEX, 3)
        launchDemoActivity<InputActivity>(intent, themeMode = DemoThemeMode.Light).use { scenario ->
            waitForUiIdle()
            assertFocusActionKeepsRecyclerAnchor(
                scenario = scenario,
                tag = DemoTestTags.INPUT_SEARCH_PRIMARY,
                maxOffsetDeltaDp = 8,
            )
        }
    }

    @Test
    fun inputSearch_focusScrollableColumnSearch_doesNotAutoScrollList() {
        val intent = Intent(
            ApplicationProvider.getApplicationContext(),
            InputActivity::class.java,
        ).putExtra(EXTRA_INPUT_PAGE_INDEX, 3)
        launchDemoActivity<InputActivity>(intent, themeMode = DemoThemeMode.Light).use { scenario ->
            waitForUiIdle()
            assertFocusActionKeepsRecyclerAnchor(
                scenario = scenario,
                tag = DemoTestTags.INPUT_FOCUS_SCROLLABLE_SEARCH,
            )
        }
    }

    @Test
    fun inputSearch_focusVerticalPagerSearch_doesNotAutoScrollList() {
        val intent = Intent(
            ApplicationProvider.getApplicationContext(),
            InputActivity::class.java,
        ).putExtra(EXTRA_INPUT_PAGE_INDEX, 3)
        launchDemoActivity<InputActivity>(intent, themeMode = DemoThemeMode.Light).use { scenario ->
            waitForUiIdle()
            assertFocusActionKeepsRecyclerAnchor(
                scenario = scenario,
                tag = DemoTestTags.INPUT_FOCUS_VERTICAL_PAGER_SEARCH,
            )
        }
    }

    @Test
    fun inputSearch_focusPullRefreshSearch_doesNotAutoScrollList() {
        val intent = Intent(
            ApplicationProvider.getApplicationContext(),
            InputActivity::class.java,
        ).putExtra(EXTRA_INPUT_PAGE_INDEX, 3)
        launchDemoActivity<InputActivity>(intent, themeMode = DemoThemeMode.Light).use { scenario ->
            waitForUiIdle()
            assertFocusActionKeepsRecyclerAnchor(
                scenario = scenario,
                tag = DemoTestTags.INPUT_FOCUS_PULL_REFRESH_SEARCH,
            )
        }
    }

    @Test
    fun scopedThemeOverride_updatesPrimaryButtonBackground() {
        val intent = Intent(
            ApplicationProvider.getApplicationContext(),
            FoundationsActivity::class.java,
        ).putExtra(EXTRA_FOUNDATIONS_PAGE_INDEX, 1)
        launchDemoActivity<FoundationsActivity>(intent, themeMode = DemoThemeMode.Light).use { scenario ->
            waitForUiIdle()
            captureDeviceScreenshot("foundations-theme-override-light")
            scenario.onActivity { activity ->
                val accentButton = activity.requireTextViewByTestTag(DemoTestTags.FOUNDATIONS_ACCENT_PRIMARY)
                assertViewFullyVisible(accentButton)
                assertTextNotEllipsized(accentButton)
                assertViewBackgroundColor(
                    view = accentButton,
                    expectedColor = DemoThemeTokens.light.colors.secondary,
                )
            }
        }
    }

    @Test
    fun componentStyleOverride_updatesPrimaryTokenButtonBackground() {
        val intent = Intent(
            ApplicationProvider.getApplicationContext(),
            FoundationsActivity::class.java,
        ).putExtra(EXTRA_FOUNDATIONS_PAGE_INDEX, 1)
        launchDemoActivity<FoundationsActivity>(intent, themeMode = DemoThemeMode.Light).use { scenario ->
            waitForUiIdle()
            captureDeviceScreenshot("foundations-component-override-light")
            scenario.onActivity { activity ->
                val tokenButton = activity.requireTextViewByTestTag(DemoTestTags.FOUNDATIONS_PRIMARY_TOKEN)
                assertViewFullyVisible(tokenButton)
                assertTextNotEllipsized(tokenButton)
                assertViewBackgroundColor(
                    view = tokenButton,
                    expectedColor = DemoThemeTokens.light.colors.onSurface,
                )
            }
        }
    }

    @Test
    fun foundationsMedia_viewsRemainVisibleAfterScroll() {
        val intent = Intent(
            ApplicationProvider.getApplicationContext(),
            FoundationsActivity::class.java,
        ).putExtra(EXTRA_FOUNDATIONS_PAGE_INDEX, 2)
        launchDemoActivity<FoundationsActivity>(intent, themeMode = DemoThemeMode.Light).use { scenario ->
            waitForUiIdle()
            captureDeviceScreenshot("foundations-media-light")
            scenario.onActivity { activity ->
                val remoteImage = activity.requireViewByTestTag(DemoTestTags.FOUNDATIONS_REMOTE_IMAGE)
                val fallbackImage = activity.requireViewByTestTag(DemoTestTags.FOUNDATIONS_FALLBACK_IMAGE)
                val iconButton = activity.requireViewByTestTag(DemoTestTags.FOUNDATIONS_PRIMARY_ICON_BUTTON)
                assertViewFullyVisible(remoteImage)
                assertViewFullyVisible(fallbackImage)
                assertViewFullyVisible(iconButton)
            }
        }
    }

    @Test
    fun layoutsEdge_viewsRemainVisibleAfterPageJump() {
        val intent = Intent(
            ApplicationProvider.getApplicationContext(),
            LayoutsActivity::class.java,
        ).putExtra(EXTRA_LAYOUTS_PAGE_INDEX, 2)
        launchDemoActivity<LayoutsActivity>(intent, themeMode = DemoThemeMode.Light).use { scenario ->
            waitForUiIdle()
            captureDeviceScreenshot("layouts-edge-light")
            scenario.onActivity { activity ->
                val toggle = activity.requireTextViewByTestTag(DemoTestTags.LAYOUTS_EDGE_TOGGLE)
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
        val intent = Intent(
            ApplicationProvider.getApplicationContext(),
            LayoutsActivity::class.java,
        ).putExtra(EXTRA_LAYOUTS_PAGE_INDEX, 5)
        launchDemoActivity<LayoutsActivity>(intent, themeMode = DemoThemeMode.Light).use { scenario ->
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
            waitForUiIdle()
            scenario.onActivity { activity ->
                val helpersContainer = activity.requireViewByTestTagVisible(DemoTestTags.LAYOUTS_CONSTRAINT_HELPERS_CONTAINER)
                val helpersMarker = activity.requireViewByTestTagVisible(DemoTestTags.LAYOUTS_CONSTRAINT_HELPERS_MARKER)
                assertViewFullyVisible(helpersContainer)
                assertViewFullyVisible(helpersMarker)
                val containerLeft = viewLeftOnScreen(helpersContainer)
                val containerTop = viewTopOnScreen(helpersContainer)
                val containerRight = containerLeft + helpersContainer.width
                val markerLeft = viewLeftOnScreen(helpersMarker)
                val markerTop = viewTopOnScreen(helpersMarker)
                assertTrue(
                    "Expected helper marker to stay inside helper container horizontal bounds. " +
                        "markerLeft=$markerLeft, containerLeft=$containerLeft, containerRight=$containerRight",
                    markerLeft in containerLeft..containerRight,
                )
                assertTrue(
                    "Expected helper marker to stay near helper container top edge. " +
                        "markerTop=$markerTop, containerTop=$containerTop",
                    markerTop <= containerTop + helpersContainer.height / 3,
                )
            }
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
        val intent = Intent(
            ApplicationProvider.getApplicationContext(),
            LayoutsActivity::class.java,
        ).putExtra(EXTRA_LAYOUTS_PAGE_INDEX, 5)
        launchDemoActivity<LayoutsActivity>(intent, themeMode = DemoThemeMode.Light).use { scenario ->
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
                val toggleText = activity.requireTextViewByTestTag(DemoTestTags.LAYOUTS_CONSTRAINT_SET_TOGGLE).text.toString()
                (leftDelta >= 12 || topDelta >= 12) && toggleText.contains("竖向布局")
            }
            assertTrue("Expected decoupled constraint set toggle to reposition marker immediately.", moved)
        }
    }

    @Test
    fun layoutsConstraint_virtualHelpersToggle_updatesVisibilityAndPlaceholderHosting() {
        val intent = Intent(
            ApplicationProvider.getApplicationContext(),
            LayoutsActivity::class.java,
        ).putExtra(EXTRA_LAYOUTS_PAGE_INDEX, 5)
        launchDemoActivity<LayoutsActivity>(intent, themeMode = DemoThemeMode.Light).use { scenario ->
            waitForUiIdle()
            scenario.onActivity { activity ->
                val container = activity.requireViewByTestTagVisible(DemoTestTags.LAYOUTS_CONSTRAINT_VIRTUAL_CONTAINER)
                assertViewFullyVisible(container)
                val status = activity.requireTextViewByTestTag(DemoTestTags.LAYOUTS_CONSTRAINT_VIRTUAL_STATUS)
                assertTrue(status.text.toString().contains("Group: visible"))
                activity.clickByTestTag(DemoTestTags.LAYOUTS_CONSTRAINT_VIRTUAL_TOGGLE)
            }
            val updated = waitUntilActivityCondition(scenario, timeoutMs = 1_500L) { activity ->
                val status = activity.requireTextViewByTestTag(DemoTestTags.LAYOUTS_CONSTRAINT_VIRTUAL_STATUS)
                val text = status.text.toString()
                text.contains("Group: hidden") && text.contains("Placeholder: A") && text.contains("single column")
            }
            assertTrue("Expected virtual helper status text to update after toggle.", updated)
        }
    }

    @Test
    fun layoutsConstraint_anchorAndDimensionAdvancedScenes_keepVisibleAndReactive() {
        val intent = Intent(
            ApplicationProvider.getApplicationContext(),
            LayoutsActivity::class.java,
        ).putExtra(EXTRA_LAYOUTS_PAGE_INDEX, 5)
        launchDemoActivity<LayoutsActivity>(intent, themeMode = DemoThemeMode.Light).use { scenario ->
            waitForUiIdle()
            var ratioBeforeWidth = 0
            var ratioBeforeHeight = 0
            scenario.onActivity { activity ->
                val container = activity.requireViewByTestTagVisible(DemoTestTags.LAYOUTS_CONSTRAINT_ANCHOR_ADVANCED_CONTAINER)
                val baseline = activity.requireViewByTestTagVisible(DemoTestTags.LAYOUTS_CONSTRAINT_ANCHOR_ADVANCED_BASELINE)
                val circle = activity.requireViewByTestTagVisible(DemoTestTags.LAYOUTS_CONSTRAINT_ANCHOR_ADVANCED_CIRCLE)
                val status = activity.requireTextViewByTestTag(DemoTestTags.LAYOUTS_CONSTRAINT_ANCHOR_ADVANCED_STATUS)
                assertViewFullyVisible(container)
                assertViewFullyVisible(baseline)
                assertViewFullyVisible(circle)
                assertTrue(status.text.toString().contains("baselineToBaseline"))

                val ratio = activity.requireViewByTestTagVisible(DemoTestTags.LAYOUTS_CONSTRAINT_DIMENSION_ADVANCED_RATIO)
                ratioBeforeWidth = ratio.width
                ratioBeforeHeight = ratio.height
                activity.clickByTestTag(DemoTestTags.LAYOUTS_CONSTRAINT_DIMENSION_ADVANCED_TOGGLE)
            }
            val ratioUpdated = waitUntilActivityCondition(scenario, timeoutMs = 1_500L) { activity ->
                val ratio = activity.requireViewByTestTagVisible(DemoTestTags.LAYOUTS_CONSTRAINT_DIMENSION_ADVANCED_RATIO)
                val status = activity.requireTextViewByTestTag(DemoTestTags.LAYOUTS_CONSTRAINT_DIMENSION_ADVANCED_STATUS).text.toString()
                (abs(ratio.width - ratioBeforeWidth) >= 8 || abs(ratio.height - ratioBeforeHeight) >= 8) &&
                    status.contains("扩展模式")
            }
            assertTrue("Expected dimension advanced toggle to update ratio card size and status.", ratioUpdated)
        }
    }

    @Test
    fun layoutsConstraint_helpersFullAndVerticalChain_toggleUpdatesLayoutRelations() {
        val intent = Intent(
            ApplicationProvider.getApplicationContext(),
            LayoutsActivity::class.java,
        ).putExtra(EXTRA_LAYOUTS_PAGE_INDEX, 5)
        launchDemoActivity<LayoutsActivity>(intent, themeMode = DemoThemeMode.Light).use { scenario ->
            waitForUiIdle()
            var markerBeforeLeft = 0
            var markerBeforeTop = 0
            var middleBeforeTop = 0
            scenario.onActivity { activity ->
                val marker = activity.requireViewByTestTagVisible(DemoTestTags.LAYOUTS_CONSTRAINT_HELPERS_FULL_MARKER)
                markerBeforeLeft = viewLeftOnScreen(marker)
                markerBeforeTop = viewTopOnScreen(marker)
                activity.clickByTestTag(DemoTestTags.LAYOUTS_CONSTRAINT_HELPERS_FULL_TOGGLE)
            }
            val helperUpdated = waitUntilActivityCondition(scenario, timeoutMs = 1_500L) { activity ->
                val marker = activity.requireViewByTestTagVisible(DemoTestTags.LAYOUTS_CONSTRAINT_HELPERS_FULL_MARKER)
                val status = activity.requireTextViewByTestTag(DemoTestTags.LAYOUTS_CONSTRAINT_HELPERS_FULL_STATUS).text.toString()
                val leftDelta = abs(viewLeftOnScreen(marker) - markerBeforeLeft)
                val topDelta = abs(viewTopOnScreen(marker) - markerBeforeTop)
                (leftDelta >= 8 || topDelta >= 8 || status.contains("fraction 模式")) && status.contains("fraction 模式")
            }
            assertTrue("Expected helpers full toggle to reposition marker and switch status mode.", helperUpdated)

            scenario.onActivity { activity ->
                val middle = activity.requireViewByTestTagVisible(DemoTestTags.LAYOUTS_CONSTRAINT_VERTICAL_CHAIN_MIDDLE)
                middleBeforeTop = viewTopOnScreen(middle)
                activity.clickByTestTag(DemoTestTags.LAYOUTS_CONSTRAINT_VERTICAL_CHAIN_TOGGLE)
            }
            val chainUpdated = waitUntilActivityCondition(scenario, timeoutMs = 1_500L) { activity ->
                val middle = activity.requireViewByTestTagVisible(DemoTestTags.LAYOUTS_CONSTRAINT_VERTICAL_CHAIN_MIDDLE)
                val toggleText = activity.requireTextViewByTestTag(DemoTestTags.LAYOUTS_CONSTRAINT_VERTICAL_CHAIN_TOGGLE).text.toString()
                abs(viewTopOnScreen(middle) - middleBeforeTop) >= 8 && toggleText.contains("SpreadInside")
            }
            assertTrue("Expected vertical chain toggle to change chain arrangement and middle item position.", chainUpdated)
        }
    }

    @Test
    fun layoutsConstraint_constraintSetHelperMirror_toggleRepositionsMarker() {
        val intent = Intent(
            ApplicationProvider.getApplicationContext(),
            LayoutsActivity::class.java,
        ).putExtra(EXTRA_LAYOUTS_PAGE_INDEX, 5)
        launchDemoActivity<LayoutsActivity>(intent, themeMode = DemoThemeMode.Light).use { scenario ->
            waitForUiIdle()
            var markerBeforeLeft = 0
            var markerBeforeTop = 0
            scenario.onActivity { activity ->
                val status = activity.requireTextViewByTestTag(DemoTestTags.LAYOUTS_CONSTRAINT_SET_HELPERS_STATUS).text.toString()
                assertTrue(status.contains("ConstraintSet(A)"))
                val marker = activity.requireViewByTestTagVisible(DemoTestTags.LAYOUTS_CONSTRAINT_SET_HELPERS_MARKER)
                markerBeforeLeft = viewLeftOnScreen(marker)
                markerBeforeTop = viewTopOnScreen(marker)
                activity.clickByTestTag(DemoTestTags.LAYOUTS_CONSTRAINT_SET_HELPERS_TOGGLE)
            }
            val switched = waitUntilActivityCondition(scenario, timeoutMs = 1_500L) { activity ->
                val marker = activity.requireViewByTestTagVisible(DemoTestTags.LAYOUTS_CONSTRAINT_SET_HELPERS_MARKER)
                val status = activity.requireTextViewByTestTag(DemoTestTags.LAYOUTS_CONSTRAINT_SET_HELPERS_STATUS).text.toString()
                val leftDelta = abs(viewLeftOnScreen(marker) - markerBeforeLeft)
                val topDelta = abs(viewTopOnScreen(marker) - markerBeforeTop)
                (leftDelta >= 10 || topDelta >= 10) && status.contains("ConstraintSet(B)")
            }
            assertTrue("Expected helper mirror constraintSet toggle to reposition marker and switch status.", switched)
        }
    }

    @Test
    fun collectionsStress_toggleUpdatesVisibleControls() {
        val intent = Intent(
            ApplicationProvider.getApplicationContext(),
            CollectionsActivity::class.java,
        ).putExtra(EXTRA_COLLECTIONS_PAGE_INDEX, 2)
        launchDemoActivity<CollectionsActivity>(intent, themeMode = DemoThemeMode.Light).use { scenario ->
            waitForUiIdle()
            scenario.onActivity { activity ->
                val rotate = activity.requireTextViewByTestTag(DemoTestTags.COLLECTIONS_STRESS_ROTATE)
                val edge = activity.requireTextViewByTestTag(DemoTestTags.COLLECTIONS_STRESS_EDGE)
                assertViewFullyVisible(rotate)
                assertViewFullyVisible(edge)
                activity.clickByTestTag(DemoTestTags.COLLECTIONS_STRESS_EDGE)
            }
            waitForUiIdle()
            captureDeviceScreenshot("collections-stress-light")
            scenario.onActivity { activity ->
                val activeIds = activity.requireTextViewByTestTag(DemoTestTags.COLLECTIONS_STRESS_ACTIVE_IDS)
                assertViewFullyVisible(activeIds)
                assertTextNotEllipsized(activeIds)
                assertTrue(activeIds.text.toString().contains("X"))
            }
        }
    }

    @Test
    fun collectionsStress_rotateOrder_refreshesVisibleIdsAcrossToggles() {
        val intent = Intent(
            ApplicationProvider.getApplicationContext(),
            CollectionsActivity::class.java,
        ).putExtra(EXTRA_COLLECTIONS_PAGE_INDEX, 2)
        launchDemoActivity<CollectionsActivity>(intent, themeMode = DemoThemeMode.Light).use { scenario ->
            waitForUiIdle()
            scenario.onActivity { activity ->
                val ids = activity.requireTextViewByTestTag(DemoTestTags.COLLECTIONS_STRESS_ACTIVE_IDS)
                assertViewFullyVisible(ids)
                assertTrue(ids.text.toString().contains("A -> B -> C -> D"))
                activity.clickByTestTag(DemoTestTags.COLLECTIONS_STRESS_ROTATE)
            }
            waitForUiIdle()
            scenario.onActivity { activity ->
                val ids = activity.requireTextViewByTestTag(DemoTestTags.COLLECTIONS_STRESS_ACTIVE_IDS)
                assertViewFullyVisible(ids)
                assertTrue(ids.text.toString().contains("C -> D -> A -> B"))
                activity.clickByTestTag(DemoTestTags.COLLECTIONS_STRESS_ROTATE)
            }
            waitForUiIdle()
            captureDeviceScreenshot("collections-stress-rotate-light")
            scenario.onActivity { activity ->
                val ids = activity.requireTextViewByTestTag(DemoTestTags.COLLECTIONS_STRESS_ACTIVE_IDS)
                assertViewFullyVisible(ids)
                assertTrue(ids.text.toString().contains("A -> B -> C -> D"))
            }
        }
    }

    @Test
    fun collectionsGrid_spanToggle_refreshesVisibleItemContent() {
        val intent = Intent(
            ApplicationProvider.getApplicationContext(),
            CollectionsActivity::class.java,
        ).putExtra(EXTRA_COLLECTIONS_PAGE_INDEX, 5)
        launchDemoActivity<CollectionsActivity>(intent, themeMode = DemoThemeMode.Light).use { scenario ->
            waitForUiIdle()
            scenario.onActivity { activity ->
                val firstItem = activity.requireTextViewByTestTag(DemoTestTags.COLLECTIONS_GRID_FIRST_ITEM)
                assertViewFullyVisible(firstItem)
                assertTrue(firstItem.text.toString().contains("2列"))
                activity.clickByTestTag(DemoTestTags.COLLECTIONS_GRID_THREE_COLS)
            }
            waitForUiIdle()
            captureDeviceScreenshot("collections-grid-refresh-light")
            scenario.onActivity { activity ->
                val firstItem = activity.requireTextViewByTestTag(DemoTestTags.COLLECTIONS_GRID_FIRST_ITEM)
                assertViewFullyVisible(firstItem)
                assertTrue(firstItem.text.toString().contains("3列"))
            }
        }
    }

    @Test
    fun navigationBar_selectionChange_updatesSummary() {
        val intent = Intent(
            ApplicationProvider.getApplicationContext(),
            NavigationActivity::class.java,
        ).putExtra(EXTRA_NAVIGATION_PAGE_INDEX, 1)
        launchDemoActivity<NavigationActivity>(intent, themeMode = DemoThemeMode.Light).use { scenario ->
            waitForUiIdle()
            scenario.onActivity { activity ->
                val summary = activity.requireTextViewByTestTag(DemoTestTags.NAVIGATION_SELECTED_SUMMARY)
                assertViewFullyVisible(summary)
                assertTrue(summary.text.toString().contains("0"))
            }
            clickDeviceText("搜索")
            waitForUiIdle()
            captureDeviceScreenshot("navigation-navbar-selection-light")
            scenario.onActivity { activity ->
                val summary = activity.requireTextViewByTestTag(DemoTestTags.NAVIGATION_SELECTED_SUMMARY)
                assertViewFullyVisible(summary)
                assertTrue(summary.text.toString().contains("1"))
            }
        }
    }

    @Test
    fun statePage_viewModelCounter_updatesThroughLifecycleAwareCollection() {
        val intent = Intent(
            ApplicationProvider.getApplicationContext(),
            StateActivity::class.java,
        ).putExtra(EXTRA_STATE_PAGE_INDEX, 0)
        launchDemoActivity<StateActivity>(intent, themeMode = DemoThemeMode.Light).use { scenario ->
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
    fun statePatchStress_segmentedControlSummary_updatesAcrossAdvances() {
        val intent = Intent(
            ApplicationProvider.getApplicationContext(),
            StateActivity::class.java,
        ).putExtra(EXTRA_STATE_PAGE_INDEX, 2)
        launchDemoActivity<StateActivity>(intent, themeMode = DemoThemeMode.Light).use { scenario ->
            waitForUiIdle()
            scenario.onActivity { activity ->
                val summary = activity.requireTextViewByTestTag(DemoTestTags.STATE_PATCH_SEGMENT_SUMMARY)
                assertViewFullyVisible(summary)
                assertTrue(summary.text.toString().contains("0"))
                activity.clickByTestTag(DemoTestTags.STATE_PATCH_ADVANCE)
                activity.clickByTestTag(DemoTestTags.STATE_PATCH_ADVANCE)
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
        val intent = Intent(
            ApplicationProvider.getApplicationContext(),
            StateActivity::class.java,
        ).putExtra(EXTRA_STATE_PAGE_INDEX, 2)
        launchDemoActivity<StateActivity>(intent, themeMode = DemoThemeMode.Light).use { scenario ->
            waitForUiIdle()
            scenario.onActivity { activity ->
                val summary = activity.requireTextViewByTestTag(DemoTestTags.STATE_PATCH_TAB_SUMMARY)
                assertViewFullyVisible(summary)
                assertTrue(summary.text.toString().contains("0"))
            }
            clickDeviceText("详情")
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
    fun statePatchStress_refreshesStableTabContent() {
        val intent = Intent(
            ApplicationProvider.getApplicationContext(),
            StateActivity::class.java,
        ).putExtra(EXTRA_STATE_PAGE_INDEX, 2)
        launchDemoActivity<StateActivity>(intent, themeMode = DemoThemeMode.Light).use { scenario ->
            waitForUiIdle()
            scenario.onActivity { activity ->
                activity.clickByTestTag(DemoTestTags.STATE_PATCH_ADVANCE)
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
    fun statePatchStress_horizontalPagerContentUpdatesAcrossAdvances() {
        val intent = Intent(
            ApplicationProvider.getApplicationContext(),
            StateActivity::class.java,
        ).putExtra(EXTRA_STATE_PAGE_INDEX, 2)
        launchDemoActivity<StateActivity>(intent, themeMode = DemoThemeMode.Light).use { scenario ->
            waitForUiIdle()
            scenario.onActivity { activity ->
                activity.clickByTestTag(DemoTestTags.STATE_PATCH_ADVANCE)
                activity.clickByTestTag(DemoTestTags.STATE_PATCH_ADVANCE)
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
    fun statePatchStress_verticalPagerContentUpdatesAcrossAdvances() {
        val intent = Intent(
            ApplicationProvider.getApplicationContext(),
            StateActivity::class.java,
        ).putExtra(EXTRA_STATE_PAGE_INDEX, 2)
        launchDemoActivity<StateActivity>(intent, themeMode = DemoThemeMode.Light).use { scenario ->
            waitForUiIdle()
            scenario.onActivity { activity ->
                activity.clickByTestTag(DemoTestTags.STATE_PATCH_ADVANCE)
                activity.clickByTestTag(DemoTestTags.STATE_PATCH_ADVANCE)
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
    fun animationPage_visibilityToggle_showRestoresTargetContent() {
        val intent = Intent(
            ApplicationProvider.getApplicationContext(),
            AnimationActivity::class.java,
        ).putExtra(EXTRA_ANIMATION_PAGE_INDEX, 0)
        launchDemoActivity<AnimationActivity>(intent, themeMode = DemoThemeMode.Light).use { scenario ->
            var footerTopBeforeHide = 0
            var footerTopAfterHide = 0
            waitForUiIdle()
            scenario.onActivity { activity ->
                val target = activity.requireViewByTestTagVisible(DemoTestTags.ANIMATION_VISIBILITY_TARGET)
                val footer = activity.requireViewByTestTagVisible(DemoTestTags.ANIMATION_VISIBILITY_FOOTER)
                footerTopBeforeHide = viewTopOnScreen(footer)
                assertViewFullyVisible(target)
                activity.clickByTestTag(DemoTestTags.ANIMATION_VISIBILITY_TOGGLE)
            }
            waitForUiIdle()
            val hiddenMoved = waitUntilActivityCondition(scenario, timeoutMs = 1_500L) { activity ->
                val toggle = activity.requireTextViewByTestTag(DemoTestTags.ANIMATION_VISIBILITY_TOGGLE)
                val footer = activity.requireViewByTestTagVisible(DemoTestTags.ANIMATION_VISIBILITY_FOOTER)
                footerTopAfterHide = viewTopOnScreen(footer)
                toggle.text.toString().contains("显示块") && footerTopAfterHide < footerTopBeforeHide
            }
            assertTrue(
                "Expected footer to move up after hide, before=$footerTopBeforeHide, after=$footerTopAfterHide",
                hiddenMoved,
            )
            scenario.onActivity { activity ->
                activity.clickByTestTag(DemoTestTags.ANIMATION_VISIBILITY_TOGGLE)
            }
            waitForUiIdle()
            val shownMoved = waitUntilActivityCondition(scenario, timeoutMs = 1_500L) { activity ->
                val toggle = activity.requireTextViewByTestTag(DemoTestTags.ANIMATION_VISIBILITY_TOGGLE)
                val footer = activity.requireViewByTestTagVisible(DemoTestTags.ANIMATION_VISIBILITY_FOOTER)
                val footerTopAfterShow = viewTopOnScreen(footer)
                val root = activity.window.decorView.findViewById<View>(android.R.id.content)
                val target = findViewByTestTag(root, DemoTestTags.ANIMATION_VISIBILITY_TARGET)
                if (!toggle.text.toString().contains("隐藏块")) {
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
        val intent = Intent(
            ApplicationProvider.getApplicationContext(),
            AnimationActivity::class.java,
        ).putExtra(EXTRA_ANIMATION_PAGE_INDEX, 1)
        launchDemoActivity<AnimationActivity>(intent, themeMode = DemoThemeMode.Light).use { scenario ->
            waitForUiIdle()
            scenario.onActivity { activity ->
                val label = activity.requireTextViewByTestTag(DemoTestTags.ANIMATION_CONTENT_LABEL)
                assertTrue(label.text.toString().contains("主文案"))
                activity.clickByTestTag(DemoTestTags.ANIMATION_CONTENT_TOGGLE)
            }
            waitForUiIdle()
            val switchedToAlt = waitUntilActivityCondition(scenario, timeoutMs = 1_500L) { activity ->
                val toggle = activity.requireTextViewByTestTag(DemoTestTags.ANIMATION_CONTENT_TOGGLE)
                val label = activity.requireTextViewByTestTag(DemoTestTags.ANIMATION_CONTENT_LABEL)
                toggle.text.toString().contains("切到主文案") &&
                    label.text.toString().contains("替代文案")
            }
            assertTrue("Expected animated content label to switch to alternative copy", switchedToAlt)
            scenario.onActivity { activity ->
                activity.clickByTestTag(DemoTestTags.ANIMATION_CONTENT_TOGGLE)
            }
            waitForUiIdle()
            val switchedBackToMain = waitUntilActivityCondition(scenario, timeoutMs = 1_500L) { activity ->
                val toggle = activity.requireTextViewByTestTag(DemoTestTags.ANIMATION_CONTENT_TOGGLE)
                val label = activity.requireTextViewByTestTag(DemoTestTags.ANIMATION_CONTENT_LABEL)
                toggle.text.toString().contains("切到替代文案") &&
                    label.text.toString().contains("主文案")
            }
            assertTrue("Expected animated content label to switch back to primary copy", switchedBackToMain)
        }
    }

    @Test
    fun animationPage_listMotion_controlsUpdateFirstItem() {
        val intent = Intent(
            ApplicationProvider.getApplicationContext(),
            AnimationActivity::class.java,
        ).putExtra(EXTRA_ANIMATION_PAGE_INDEX, 2)
        launchDemoActivity<AnimationActivity>(intent, themeMode = DemoThemeMode.Light).use { scenario ->
            waitForUiIdle()
            scenario.onActivity { activity ->
                val first = activity.requireTextViewByTestTag(DemoTestTags.ANIMATION_LIST_FIRST)
                assertTrue(first.text.toString().contains("Item A"))
                activity.clickByTestTag(DemoTestTags.ANIMATION_LIST_ADD)
            }
            waitForUiIdle()
            scenario.onActivity { activity ->
                val first = activity.requireTextViewByTestTag(DemoTestTags.ANIMATION_LIST_FIRST)
                assertTrue(first.text.toString().contains("New"))
                activity.clickByTestTag(DemoTestTags.ANIMATION_LIST_REORDER)
            }
            waitForUiIdle()
            scenario.onActivity { activity ->
                val first = activity.requireTextViewByTestTag(DemoTestTags.ANIMATION_LIST_FIRST)
                assertTrue(first.text.toString().contains("Item A"))
            }
        }
    }

    @Test
    fun animationPage_specsPanel_switchesTypedAndGenericAnimations() {
        val intent = Intent(
            ApplicationProvider.getApplicationContext(),
            AnimationActivity::class.java,
        ).putExtra(EXTRA_ANIMATION_PAGE_INDEX, 3)
        launchDemoActivity<AnimationActivity>(intent, themeMode = DemoThemeMode.Light).use { scenario ->
            waitForUiIdle()
            var floatBefore = ""
            var vectorBefore = ""
            scenario.onActivity { activity ->
                floatBefore = activity.requireTextViewByTestTag(DemoTestTags.ANIMATION_SPEC_FLOAT_VALUE).text.toString()
                vectorBefore = activity.requireTextViewByTestTag(DemoTestTags.ANIMATION_SPEC_VECTOR_VALUE).text.toString()
                activity.clickByTestTag(DemoTestTags.ANIMATION_SPEC_KIND_TOGGLE)
                activity.clickByTestTag(DemoTestTags.ANIMATION_SPEC_TARGET_TOGGLE)
                activity.clickByTestTag(DemoTestTags.ANIMATION_SPEC_VECTOR_TOGGLE)
                activity.clickByTestTag(DemoTestTags.ANIMATION_SPEC_SIZE_TOGGLE)
            }
            waitForUiIdle()
            val typedAndGenericUpdated = waitUntilActivityCondition(scenario, timeoutMs = 1_500L) { activity ->
                val floatAfter = activity.requireTextViewByTestTag(DemoTestTags.ANIMATION_SPEC_FLOAT_VALUE).text.toString()
                val vectorAfter = activity.requireTextViewByTestTag(DemoTestTags.ANIMATION_SPEC_VECTOR_VALUE).text.toString()
                val sizeProbe = activity.requireTextViewByTestTag(DemoTestTags.ANIMATION_SPEC_SIZE_PROBE)
                floatAfter != floatBefore &&
                    vectorAfter != vectorBefore &&
                    isViewVisible(sizeProbe)
            }
            assertTrue("Expected specs panel to update typed and generic animation values", typedAndGenericUpdated)
        }
    }

    @Test
    fun animationPage_transitionPanel_updatesAllTransitionChannels() {
        val intent = Intent(
            ApplicationProvider.getApplicationContext(),
            AnimationActivity::class.java,
        ).putExtra(EXTRA_ANIMATION_PAGE_INDEX, 4)
        launchDemoActivity<AnimationActivity>(intent, themeMode = DemoThemeMode.Light).use { scenario ->
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
                activity.clickByTestTag(DemoTestTags.ANIMATION_TRANSITION_TOGGLE)
            }
            waitForUiIdle()
            val transitionValuesUpdated = waitUntilActivityCondition(scenario, timeoutMs = 1_500L) { activity ->
                val alphaAfter = activity.requireTextViewByTestTag(DemoTestTags.ANIMATION_TRANSITION_ALPHA).text.toString()
                val intAfter = activity.requireTextViewByTestTag(DemoTestTags.ANIMATION_TRANSITION_INT).text.toString()
                val dpAfter = activity.requireTextViewByTestTag(DemoTestTags.ANIMATION_TRANSITION_DP).text.toString()
                val colorAfter = activity.requireTextViewByTestTag(DemoTestTags.ANIMATION_TRANSITION_COLOR).text.toString()
                alphaAfter != alphaBefore &&
                    intAfter != intBefore &&
                    dpAfter != dpBefore &&
                    colorAfter != colorBefore
            }
            assertTrue("Expected transition panel channels to update after toggle", transitionValuesUpdated)
        }
    }

    @Test
    fun animationPage_visibilityStatePanel_reportsIdleAndTargetState() {
        val intent = Intent(
            ApplicationProvider.getApplicationContext(),
            AnimationActivity::class.java,
        ).putExtra(EXTRA_ANIMATION_PAGE_INDEX, 4)
        launchDemoActivity<AnimationActivity>(intent, themeMode = DemoThemeMode.Light).use { scenario ->
            waitForUiIdle()
            scenario.onActivity { activity ->
                activity.clickByTestTag(DemoTestTags.ANIMATION_VISIBILITY_STATE_TOGGLE)
                activity.clickByTestTag(DemoTestTags.ANIMATION_ROW_AXIS_TOGGLE)
                activity.clickByTestTag(DemoTestTags.ANIMATION_COLUMN_AXIS_TOGGLE)
            }
            waitForUiIdle()
            val visibilityStateAndAxisUpdated = waitUntilActivityCondition(scenario, timeoutMs = 1_500L) { activity ->
                val status = activity.requireTextViewByTestTag(DemoTestTags.ANIMATION_VISIBILITY_STATE_STATUS).text.toString()
                val rowToggle = activity.requireTextViewByTestTag(DemoTestTags.ANIMATION_ROW_AXIS_TOGGLE).text.toString()
                val columnToggle = activity.requireTextViewByTestTag(DemoTestTags.ANIMATION_COLUMN_AXIS_TOGGLE).text.toString()
                status.contains("target=true") &&
                    rowToggle.contains("隐藏") &&
                    columnToggle.contains("隐藏")
            }
            assertTrue("Expected visibility state status and axis targets to update", visibilityStateAndAxisUpdated)
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
        val intent = Intent(
            ApplicationProvider.getApplicationContext(),
            AnimationActivity::class.java,
        ).putExtra(EXTRA_ANIMATION_PAGE_INDEX, 5)
        launchDemoActivity<AnimationActivity>(intent, themeMode = DemoThemeMode.Light).use { scenario ->
            waitForUiIdle()
            scenario.onActivity { activity ->
                activity.requireTextViewByTestTag(DemoTestTags.ANIMATION_INFINITE_VALUE)
                activity.clickByTestTag(DemoTestTags.ANIMATION_INFINITE_REPEAT_MODE)
                activity.clickByTestTag(DemoTestTags.ANIMATION_ANIMATABLE_SNAP_HIGH)
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
        val dragSwipeIntent = Intent(
            ApplicationProvider.getApplicationContext(),
            GesturesActivity::class.java,
        ).putExtra(EXTRA_GESTURES_PAGE_INDEX, 1)
        launchDemoActivity<GesturesActivity>(dragSwipeIntent, themeMode = DemoThemeMode.Light).use { scenario ->
            waitForUiIdle()
            var dragBefore = 0f
            scenario.onActivity { activity ->
                val dragTarget = activity.requireViewByTestTagVisible(DemoTestTags.GESTURE_DRAG_TARGET)
                val swipeTarget = activity.requireViewByTestTagVisible(DemoTestTags.GESTURE_SWIPE_TARGET)
                assertViewFullyVisible(dragTarget)
                assertViewFullyVisible(swipeTarget)
                dragBefore = extractFirstFloat(
                    activity.requireTextViewByTestTag(DemoTestTags.GESTURE_DRAG_VALUE).text.toString(),
                ) ?: 0f
                activity.dragByTestTag(
                    tag = DemoTestTags.GESTURE_DRAG_TARGET,
                    deltaX = 180f,
                )
                activity.dragByTestTag(
                    tag = DemoTestTags.GESTURE_SWIPE_TARGET,
                    deltaX = 200f,
                )
            }
            waitForUiIdle()
            val movedToRightAnchor = waitUntilActivityCondition(scenario, timeoutMs = 1_500L) { activity ->
                val dragAfterText = activity.requireTextViewByTestTag(DemoTestTags.GESTURE_DRAG_VALUE).text.toString()
                val swipeAfterText = activity.requireTextViewByTestTag(DemoTestTags.GESTURE_SWIPE_VALUE).text.toString()
                val swipeTargetText = activity.requireTextViewByTestTag(DemoTestTags.GESTURE_SWIPE_TARGET_VALUE).text.toString()
                val swipeOffsetText = activity.requireTextViewByTestTag(DemoTestTags.GESTURE_SWIPE_OFFSET_VALUE).text.toString()
                val dragAfter = extractFirstFloat(dragAfterText) ?: dragBefore
                val offset = extractFirstFloat(swipeOffsetText) ?: 0f
                abs(dragAfter - dragBefore) >= 12f &&
                    swipeAfterText.contains("Right") &&
                    swipeTargetText.contains("Right") &&
                    offset >= 60f
            }
            assertTrue("Expected drag and swipe summaries to move to right anchor", movedToRightAnchor)
            scenario.onActivity { activity ->
                activity.dragByTestTag(
                    tag = DemoTestTags.GESTURE_SWIPE_TARGET,
                    deltaX = -420f,
                )
            }
            waitForUiIdle()
            val movedToLeftAnchor = waitUntilActivityCondition(scenario, timeoutMs = 1_500L) { activity ->
                val swipeAfterText = activity.requireTextViewByTestTag(DemoTestTags.GESTURE_SWIPE_VALUE).text.toString()
                val swipeTargetText = activity.requireTextViewByTestTag(DemoTestTags.GESTURE_SWIPE_TARGET_VALUE).text.toString()
                val swipeOffsetText = activity.requireTextViewByTestTag(DemoTestTags.GESTURE_SWIPE_OFFSET_VALUE).text.toString()
                val offset = extractFirstFloat(swipeOffsetText) ?: 0f
                swipeAfterText.contains("Left") && swipeTargetText.contains("Left") && offset <= -60f
            }
            assertTrue("Expected swipe summaries to move to left anchor after reverse drag", movedToLeftAnchor)
        }
    }

    @Test
    fun gesturesPage_pointerInputConsumed_shortCircuitsCombinedClickable() {
        val tapIntent = Intent(
            ApplicationProvider.getApplicationContext(),
            GesturesActivity::class.java,
        ).putExtra(EXTRA_GESTURES_PAGE_INDEX, 0)
        launchDemoActivity<GesturesActivity>(tapIntent, themeMode = DemoThemeMode.Light).use { scenario ->
            waitForUiIdle()
            scenario.onActivity { activity ->
                val target = activity.requireViewByTestTagVisible(DemoTestTags.GESTURE_POINTER_CONSUMED_TARGET)
                assertViewFullyVisible(target)
                activity.tapByTestTag(DemoTestTags.GESTURE_POINTER_CONSUMED_TARGET)
            }
            waitForUiIdle()
            scenario.onActivity { activity ->
                val count =
                    activity.requireTextViewByTestTag(DemoTestTags.GESTURE_POINTER_CONSUMED_CLICK_COUNT).text.toString()
                assertTrue(
                    "Expected consumed pointer input to suppress combinedClickable click",
                    count.contains("0"),
                )
            }
        }
    }

    @Test
    fun gesturesPage_pointerInputConsumed_andTapTargetStillReceivesClick() {
        val tapIntent = Intent(
            ApplicationProvider.getApplicationContext(),
            GesturesActivity::class.java,
        ).putExtra(EXTRA_GESTURES_PAGE_INDEX, 0)
        launchDemoActivity<GesturesActivity>(tapIntent, themeMode = DemoThemeMode.Light).use { scenario ->
            waitForUiIdle()
            scenario.onActivity { activity ->
                activity.tapByTestTag(DemoTestTags.GESTURE_POINTER_CONSUMED_TARGET)
                activity.tapByTestTag(DemoTestTags.GESTURE_TAP_TARGET)
            }
            waitForUiIdle()
            val pointerAndTapStable = waitUntilActivityCondition(scenario, timeoutMs = 1_500L) { activity ->
                val consumedText = activity.requireTextViewByTestTag(
                    DemoTestTags.GESTURE_POINTER_CONSUMED_CLICK_COUNT,
                ).text.toString()
                val tapText = activity.requireTextViewByTestTag(
                    DemoTestTags.GESTURE_TAP_COUNT,
                ).text.toString()
                val blockedTaps = "Blocked taps:\\s*(\\d+)".toRegex()
                    .find(consumedText)
                    ?.groupValues
                    ?.getOrNull(1)
                    ?.toIntOrNull() ?: 0
                consumedText.contains("Consumed click count: 0") &&
                    blockedTaps >= 1 &&
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
        val transformIntent = Intent(
            ApplicationProvider.getApplicationContext(),
            GesturesActivity::class.java,
        ).putExtra(EXTRA_GESTURES_PAGE_INDEX, 2)
        launchDemoActivity<GesturesActivity>(transformIntent, themeMode = DemoThemeMode.Light).use { scenario ->
            waitForUiIdle()
            scenario.onActivity { activity ->
                val target = activity.requireViewByTestTagVisible(DemoTestTags.GESTURE_TRANSFORM_TARGET)
                assertViewFullyVisible(target)
                activity.transformByTestTag(
                    tag = DemoTestTags.GESTURE_TRANSFORM_TARGET,
                    panX = 140f,
                    panY = 88f,
                    rotationDegrees = 36f,
                    zoomRatio = 1.18f,
                )
            }
            waitForUiIdle()
            val transformUpdated = waitUntilActivityCondition(scenario, timeoutMs = 2_000L) { activity ->
                val text = activity.requireTextViewByTestTag(DemoTestTags.GESTURE_TRANSFORM_VALUE).text.toString()
                val metrics = extractTransformMetrics(text) ?: return@waitUntilActivityCondition false
                abs(metrics.panX) >= 8f && abs(metrics.panY) >= 8f && abs(metrics.rotation) >= 8f
            }
            assertTrue("Expected transform gesture to update pan and rotation summaries", transformUpdated)
        }
    }

    @Test
    fun graphicsPage_blendAndDrawContentToggles_updateStatuses() {
        launchDemoActivity(GraphicsActivity::class.java).use { scenario ->
            waitForUiIdle()
            captureDeviceScreenshot("graphics-core-light")
            scenario.onActivity { activity ->
                assertViewFullyVisible(activity.requireViewByTestTagVisible(DemoTestTags.GRAPHICS_PRIMITIVES_CANVAS))
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
                assertTrue("Expected blend status to switch to Multiply", blendStatus.contains("Multiply"))
                assertTrue("Expected drawWithContent status to report intercepted content", drawStatus.contains("拦截内容"))
            }
        }
    }

    @Test
    fun graphicsPage_cacheControls_updateCacheStatusText() {
        launchDemoActivity(GraphicsActivity::class.java).use { scenario ->
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
        val stateIntent = Intent(
            ApplicationProvider.getApplicationContext(),
            StateActivity::class.java,
        ).putExtra(EXTRA_STATE_PAGE_INDEX, 2)
        launchDemoActivity<StateActivity>(stateIntent, themeMode = DemoThemeMode.Light).use { scenario ->
            waitForUiIdle()
            scenario.onActivity { activity ->
                activity.clickByTestTag(DemoTestTags.STATE_PATCH_ADVANCE)
                activity.clickByTestTag(DemoTestTags.STATE_PATCH_ADVANCE)
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
                patched > 0 && !capturedAtText.contains("尚未捕获")
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

    private fun extractTransformMetrics(text: String): TransformMetrics? {
        val regex = """scale=(-?\d+(?:\.\d+)?)\s+pan=\((-?\d+),\s*(-?\d+)\)\s+rot=(-?\d+(?:\.\d+)?)""".toRegex()
        val match = regex.find(text) ?: return null
        return TransformMetrics(
            scale = match.groupValues[1].toFloatOrNull() ?: return null,
            panX = match.groupValues[2].toFloatOrNull() ?: return null,
            panY = match.groupValues[3].toFloatOrNull() ?: return null,
            rotation = match.groupValues[4].toFloatOrNull() ?: return null,
        )
    }

    private data class TransformMetrics(
        val scale: Float,
        val panX: Float,
        val panY: Float,
        val rotation: Float,
    )

    private fun assertFocusActionKeepsRecyclerAnchor(
        scenario: ActivityScenario<InputActivity>,
        tag: String,
        maxOffsetDeltaDp: Int = 12,
    ) {
        var beforeAnchor: RecyclerViewportAnchor? = null
        var maxOffsetDeltaPx = maxOffsetDeltaDp
        scenario.onActivity { activity ->
            activity.requireViewByTestTagVisible(tag)
            beforeAnchor = activity.readFirstRecyclerAnchor()
            maxOffsetDeltaPx = (maxOffsetDeltaDp * activity.resources.displayMetrics.density).toInt()
            activity.focusInputByTestTag(tag)
        }
        waitForUiIdle()
        scenario.onActivity { activity ->
            val afterAnchor = activity.readFirstRecyclerAnchor()
            assertNotNull(beforeAnchor)
            assertNotNull(afterAnchor)
            val before = beforeAnchor!!
            val after = afterAnchor!!
            assertEquals(before.position, after.position)
            assertTrue(
                "Expected focus action to avoid noticeable auto-scroll, before=$before, after=$after, tag=$tag",
                abs(before.offset - after.offset) <= maxOffsetDeltaPx,
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
