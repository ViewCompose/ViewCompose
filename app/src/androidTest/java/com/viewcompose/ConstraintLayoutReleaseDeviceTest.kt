package com.viewcompose

import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import androidx.constraintlayout.helper.widget.Flow
import androidx.constraintlayout.helper.widget.Layer
import androidx.constraintlayout.widget.Barrier
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.Group
import androidx.constraintlayout.widget.Placeholder
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.abs

@RunWith(AndroidJUnit4::class)
class ConstraintLayoutReleaseDeviceTest {
    @Before
    fun resetDiagnostics() {
        DemoRenderDiagnosticsStore.reset()
    }

    @Test
    fun allHelpers_remainCorrectAcrossReleaseConfigurations() {
        listOf(
            FixtureCase(
                label = "light-ltr-font100",
                themeMode = DemoThemeMode.Light,
                rtl = false,
                fontScale = 1f,
            ),
            FixtureCase(
                label = "dark-rtl-font130",
                themeMode = DemoThemeMode.Dark,
                rtl = true,
                fontScale = 1.3f,
            ),
        ).forEach(::verifyConfiguration)
    }

    @Test
    fun virtualHelpers_publishExactFlowGroupLayerAndPlaceholderEffects() {
        launchConstraintFixture(FixtureCase.default).use { scenario ->
            waitForUiIdle()
            var flowAInitialTop = 0
            var flowBInitialTop = 0
            var placeholderAInitialTop = 0
            var placeholderBInitialTop = 0
            scenario.onActivity { activity ->
                val flowA = activity.requireViewByTestTagVisible(DemoTestTags.LAYOUTS_CONSTRAINT_VIRTUAL_FLOW_A)
                val flowB = activity.requireViewByTestTagVisible(DemoTestTags.LAYOUTS_CONSTRAINT_VIRTUAL_FLOW_B)
                val flowC = activity.requireViewByTestTagVisible(DemoTestTags.LAYOUTS_CONSTRAINT_VIRTUAL_FLOW_C)
                val flowD = activity.requireViewByTestTagVisible(DemoTestTags.LAYOUTS_CONSTRAINT_VIRTUAL_FLOW_D)
                flowAInitialTop = flowA.topOnScreen()
                flowBInitialTop = flowB.topOnScreen()
                assertEquals("Flow A and B should share the first row before toggle.", flowAInitialTop, flowBInitialTop)
                assertEquals("Flow C and D should share the second row before toggle.", flowC.topOnScreen(), flowD.topOnScreen())

                val groupA = activity.requireViewByTestTagVisible(DemoTestTags.LAYOUTS_CONSTRAINT_VIRTUAL_GROUP_MEMBER)
                val groupB = activity.requireViewByTestTagVisible(DemoTestTags.LAYOUTS_CONSTRAINT_VIRTUAL_GROUP_MEMBER_B)
                assertEquals(View.VISIBLE, groupA.visibility)
                assertEquals(View.VISIBLE, groupB.visibility)

                val layerA = activity.requireViewByTestTagVisible(DemoTestTags.LAYOUTS_CONSTRAINT_VIRTUAL_CHIP_A)
                val layerB = activity.requireViewByTestTagVisible(DemoTestTags.LAYOUTS_CONSTRAINT_VIRTUAL_CHIP_B)
                assertLayerTransform(layerA, rotation = 0f, scale = 1f, translationX = 0f, translationY = 0f)
                assertLayerTransform(layerB, rotation = 0f, scale = 1f, translationX = 0f, translationY = 0f)

                val placeholderA = activity.requireViewByTestTagVisible(DemoTestTags.LAYOUTS_CONSTRAINT_VIRTUAL_PLACEHOLDER_A)
                val placeholderB = activity.requireViewByTestTagVisible(DemoTestTags.LAYOUTS_CONSTRAINT_VIRTUAL_PLACEHOLDER_B)
                placeholderAInitialTop = placeholderA.top
                placeholderBInitialTop = placeholderB.top
                assertTrue(
                    "Placeholder B should occupy the lower host before toggle.",
                    placeholderBInitialTop > placeholderAInitialTop,
                )
                activity.clickByTestTag(DemoTestTags.LAYOUTS_CONSTRAINT_VIRTUAL_TOGGLE)
            }
            waitForUiIdle()
            scenario.onActivity { activity ->
                val flowA = activity.requireViewByTestTagVisible(DemoTestTags.LAYOUTS_CONSTRAINT_VIRTUAL_FLOW_A)
                val flowB = activity.requireViewByTestTagVisible(DemoTestTags.LAYOUTS_CONSTRAINT_VIRTUAL_FLOW_B)
                val flowC = activity.requireViewByTestTagVisible(DemoTestTags.LAYOUTS_CONSTRAINT_VIRTUAL_FLOW_C)
                val flowD = activity.requireViewByTestTagVisible(DemoTestTags.LAYOUTS_CONSTRAINT_VIRTUAL_FLOW_D)
                assertTrue("One-element Flow rows should place B below A.", flowB.topOnScreen() > flowA.topOnScreen())
                assertTrue("One-element Flow rows should place C below B.", flowC.topOnScreen() > flowB.topOnScreen())
                assertTrue("One-element Flow rows should place D below C.", flowD.topOnScreen() > flowC.topOnScreen())
                assertTrue(
                    "Flow B must move away from Flow A after the wrap policy changes.",
                    flowB.topOnScreen() - flowA.topOnScreen() > flowBInitialTop - flowAInitialTop,
                )

                val root = activity.findViewById<ViewGroup>(android.R.id.content)
                val groupA = requireNotNull(findViewByTestTag(root, DemoTestTags.LAYOUTS_CONSTRAINT_VIRTUAL_GROUP_MEMBER))
                val groupB = requireNotNull(findViewByTestTag(root, DemoTestTags.LAYOUTS_CONSTRAINT_VIRTUAL_GROUP_MEMBER_B))
                assertEquals("Group must own member A visibility.", View.GONE, groupA.visibility)
                assertEquals("Group must own member B visibility.", View.GONE, groupB.visibility)

                val layerA = activity.requireViewByTestTagVisible(DemoTestTags.LAYOUTS_CONSTRAINT_VIRTUAL_CHIP_A)
                val layerB = activity.requireViewByTestTagVisible(DemoTestTags.LAYOUTS_CONSTRAINT_VIRTUAL_CHIP_B)
                assertLayerScaleAndRotation(layerA, rotation = 18f, scale = 1.1f)
                assertLayerScaleAndRotation(layerB, rotation = 18f, scale = 1.1f)
                assertTrue(
                    "Layer must project a non-zero translated geometry onto member A.",
                    abs(layerA.translationX) > 1f || abs(layerA.translationY) > 1f,
                )
                assertTrue(
                    "Layer must project a non-zero translated geometry onto member B.",
                    abs(layerB.translationX) > 1f || abs(layerB.translationY) > 1f,
                )

                val placeholderA = activity.requireViewByTestTagVisible(DemoTestTags.LAYOUTS_CONSTRAINT_VIRTUAL_PLACEHOLDER_A)
                val placeholderB = activity.requireViewByTestTagVisible(DemoTestTags.LAYOUTS_CONSTRAINT_VIRTUAL_PLACEHOLDER_B)
                assertTrue(
                    "Placeholder A should move into the lower host after toggle.",
                    placeholderA.top > placeholderB.top,
                )
                assertTrue("Placeholder A must leave its original local top position.", placeholderA.top > placeholderAInitialTop)
                assertTrue("Placeholder B must leave the lower host.", placeholderB.top < placeholderBInitialTop)
                activity.requireViewByTestTagVisible(
                    DemoTestTags.LAYOUTS_CONSTRAINT_VIRTUAL_PLACEHOLDER_NOTE,
                ).centerInsideOwningRecyclerView()
            }
            waitForUiIdle()
            preserveAfterConnectedTest(
                captureDeviceScreenshot("constraint-release-virtual-helper-effects"),
            )
            assertNoRenderWarnings()
        }
    }

    @Test
    fun rapidHelperSwitching_keepsNativeRegistryBoundedAndWarningFree() {
        launchConstraintFixture(FixtureCase.default).use { scenario ->
            waitForUiIdle()
            var initialChildCount = 0
            var initialBarrierCount = 0
            scenario.onActivity { activity ->
                val marker = activity.requireViewByTestTagVisible(DemoTestTags.LAYOUTS_CONSTRAINT_SET_HELPERS_MARKER)
                val layout = marker.requireConstraintLayoutAncestor()
                initialChildCount = layout.childCount
                initialBarrierCount = layout.childrenOfType<Barrier>().size
            }
            repeat(100) {
                scenario.onActivity { activity ->
                    activity.clickByTestTag(DemoTestTags.LAYOUTS_CONSTRAINT_SET_HELPERS_TOGGLE)
                }
                waitForUiIdle()
            }
            scenario.onActivity { activity ->
                val finalMarker = activity.requireViewByTestTagVisible(DemoTestTags.LAYOUTS_CONSTRAINT_SET_HELPERS_MARKER)
                val finalLayout = finalMarker.requireConstraintLayoutAncestor()
                assertEquals("Helper switching must retain a bounded native child count.", initialChildCount, finalLayout.childCount)
                assertEquals("Exactly one Barrier should remain after helper switching.", initialBarrierCount, finalLayout.childrenOfType<Barrier>().size)
            }

            var initialVirtualCounts = emptyList<Int>()
            scenario.onActivity { activity ->
                val flow = activity.requireViewByTestTagVisible(DemoTestTags.LAYOUTS_CONSTRAINT_VIRTUAL_FLOW_A)
                    .requireConstraintLayoutAncestor()
                val group = activity.requireViewByTestTagVisible(DemoTestTags.LAYOUTS_CONSTRAINT_VIRTUAL_GROUP_MEMBER)
                    .requireConstraintLayoutAncestor()
                val layer = activity.requireViewByTestTagVisible(DemoTestTags.LAYOUTS_CONSTRAINT_VIRTUAL_CHIP_A)
                    .requireConstraintLayoutAncestor()
                val placeholder = activity.requireViewByTestTagVisible(DemoTestTags.LAYOUTS_CONSTRAINT_VIRTUAL_PLACEHOLDER_NOTE)
                    .requireConstraintLayoutAncestor()
                initialVirtualCounts = listOf(flow.childCount, group.childCount, layer.childCount, placeholder.childCount)
            }
            repeat(100) {
                scenario.onActivity { activity ->
                    activity.clickByTestTag(DemoTestTags.LAYOUTS_CONSTRAINT_VIRTUAL_TOGGLE)
                }
                waitForUiIdle()
            }
            scenario.onActivity { activity ->
                val flow = activity.requireViewByTestTagVisible(DemoTestTags.LAYOUTS_CONSTRAINT_VIRTUAL_FLOW_A)
                    .requireConstraintLayoutAncestor()
                val group = activity.requireViewByTestTagVisible(DemoTestTags.LAYOUTS_CONSTRAINT_VIRTUAL_GROUP_MEMBER)
                    .requireConstraintLayoutAncestor()
                val layer = activity.requireViewByTestTagVisible(DemoTestTags.LAYOUTS_CONSTRAINT_VIRTUAL_CHIP_A)
                    .requireConstraintLayoutAncestor()
                val placeholder = activity.requireViewByTestTagVisible(DemoTestTags.LAYOUTS_CONSTRAINT_VIRTUAL_PLACEHOLDER_NOTE)
                    .requireConstraintLayoutAncestor()
                assertEquals(initialVirtualCounts[0], flow.childCount)
                assertEquals(initialVirtualCounts[1], group.childCount)
                assertEquals(initialVirtualCounts[2], layer.childCount)
                assertEquals(initialVirtualCounts[3], placeholder.childCount)
                assertEquals(1, flow.childrenOfType<Flow>().size)
                assertEquals(1, group.childrenOfType<Group>().size)
                assertEquals(1, layer.childrenOfType<Layer>().size)
                assertEquals(1, placeholder.childrenOfType<Placeholder>().size)
            }
            assertNoRenderWarnings()
        }
    }

    @Test
    fun phaseTwoGridAndCircularFlow_publishExactNativeGeometryWithoutUnownedHelpers() {
        launchConstraintFixture(FixtureCase.default).use { scenario ->
            waitForUiIdle()
            scenario.onActivity { activity ->
                activity.requireViewByTestTagVisible(
                    DemoTestTags.LAYOUTS_CONSTRAINT_GRID_HERO,
                ).centerInsideOwningRecyclerView()
            }
            waitForUiIdle()
            var horizontalMetricBounds = IntArray(4)
            var horizontalStatusBounds = IntArray(4)
            var horizontalActionBounds = IntArray(4)
            scenario.onActivity { activity ->
                val grid = activity.requireViewByTestTagVisible(DemoTestTags.LAYOUTS_CONSTRAINT_GRID_CONTAINER)
                val hero = activity.requireViewByTestTagVisible(DemoTestTags.LAYOUTS_CONSTRAINT_GRID_HERO)
                val metric = activity.requireViewByTestTagVisible(DemoTestTags.LAYOUTS_CONSTRAINT_GRID_METRIC)
                val status = activity.requireViewByTestTagVisible(DemoTestTags.LAYOUTS_CONSTRAINT_GRID_STATUS)
                val action = activity.requireViewByTestTagVisible(DemoTestTags.LAYOUTS_CONSTRAINT_GRID_ACTION)
                listOf(hero, metric, status, action).forEach { child -> assertInside(grid, child) }
                assertTrue("The spanning Grid item must be wider than every single-cell item.",
                    hero.width > maxOf(metric.width, status.width, action.width))
                assertEquals("The horizontal Grid puts the metric beside the spanning hero.",
                    hero.topOnScreen(), metric.topOnScreen())
                assertEquals("The remaining horizontal Grid cells share the next row.",
                    status.topOnScreen(), action.topOnScreen())
                assertTrue(status.topOnScreen() > hero.topOnScreen())
                assertTrue(hero.leftOnScreen() < metric.leftOnScreen())
                assertTrue(status.leftOnScreen() < action.leftOnScreen())
                assertEquals("Four content Views plus three row and three column proxies are expected.",
                    10, grid.requireConstraintLayoutSelf().childCount)
                horizontalMetricBounds = metric.screenBounds()
                horizontalStatusBounds = status.screenBounds()
                horizontalActionBounds = action.screenBounds()
                activity.clickByTestTag(DemoTestTags.LAYOUTS_CONSTRAINT_GRID_TOGGLE)
            }
            waitForUiIdle()
            scenario.onActivity { activity ->
                val grid = activity.requireViewByTestTagVisible(DemoTestTags.LAYOUTS_CONSTRAINT_GRID_CONTAINER)
                val hero = activity.requireViewByTestTagVisible(DemoTestTags.LAYOUTS_CONSTRAINT_GRID_HERO)
                val metric = activity.requireViewByTestTagVisible(DemoTestTags.LAYOUTS_CONSTRAINT_GRID_METRIC)
                val status = activity.requireViewByTestTagVisible(DemoTestTags.LAYOUTS_CONSTRAINT_GRID_STATUS)
                val action = activity.requireViewByTestTagVisible(DemoTestTags.LAYOUTS_CONSTRAINT_GRID_ACTION)
                assertEquals("The vertical Grid fills the final free row after the skipped center.",
                    status.topOnScreen(), action.topOnScreen())
                assertEquals("The vertical Grid fills the first column downward.",
                    metric.leftOnScreen(), status.leftOnScreen())
                assertTrue(metric.topOnScreen() > hero.topOnScreen())
                assertTrue(status.topOnScreen() > metric.topOnScreen())
                assertTrue(status.leftOnScreen() < action.leftOnScreen())
                assertTrue("Grid orientation must move the metric.",
                    !metric.screenBounds().contentEquals(horizontalMetricBounds))
                assertTrue("Grid orientation must move the status.",
                    !status.screenBounds().contentEquals(horizontalStatusBounds))
                assertTrue("Grid orientation must move the action.",
                    !action.screenBounds().contentEquals(horizontalActionBounds))
                assertEquals(10, grid.requireConstraintLayoutSelf().childCount)
            }
            scenario.onActivity { activity ->
                activity.requireViewByTestTagVisible(
                    DemoTestTags.LAYOUTS_CONSTRAINT_CIRCULAR_CENTER,
                ).centerInsideOwningRecyclerView()
            }
            waitForUiIdle()
            scenario.onActivity { activity ->
                val container = activity.requireViewByTestTagVisible(DemoTestTags.LAYOUTS_CONSTRAINT_CIRCULAR_CONTAINER)
                val center = activity.requireViewByTestTagVisible(DemoTestTags.LAYOUTS_CONSTRAINT_CIRCULAR_CENTER)
                val top = activity.requireViewByTestTagVisible(DemoTestTags.LAYOUTS_CONSTRAINT_CIRCULAR_TOP)
                val right = activity.requireViewByTestTagVisible(DemoTestTags.LAYOUTS_CONSTRAINT_CIRCULAR_RIGHT)
                val bottom = activity.requireViewByTestTagVisible(DemoTestTags.LAYOUTS_CONSTRAINT_CIRCULAR_BOTTOM)
                val left = activity.requireViewByTestTagVisible(DemoTestTags.LAYOUTS_CONSTRAINT_CIRCULAR_LEFT)
                listOf(center, top, right, bottom, left).forEach { child -> assertInside(container, child) }
                assertTrue(top.centerYOnScreen() < center.centerYOnScreen())
                assertTrue(right.centerXOnScreen() > center.centerXOnScreen())
                assertTrue(bottom.centerYOnScreen() > center.centerYOnScreen())
                assertTrue(left.centerXOnScreen() < center.centerXOnScreen())
                val radius = (78 * activity.resources.displayMetrics.density).toInt()
                listOf(
                    abs(center.centerYOnScreen() - top.centerYOnScreen()),
                    abs(right.centerXOnScreen() - center.centerXOnScreen()),
                    abs(bottom.centerYOnScreen() - center.centerYOnScreen()),
                    abs(center.centerXOnScreen() - left.centerXOnScreen()),
                ).forEach { actual ->
                    assertTrue("CircularFlow radius must match 78dp. expected=$radius actual=$actual", abs(actual - radius) <= 2)
                }
                assertEquals("CircularFlow must create no helper View.",
                    5, center.requireConstraintLayoutAncestor().childCount)
            }
            assertNoRenderWarnings()
        }
    }

    private fun verifyConfiguration(fixture: FixtureCase) {
        DemoRenderDiagnosticsStore.reset()
        launchConstraintFixture(fixture).use { scenario ->
            waitForUiIdle()
            SystemClock.sleep(WINDOW_TRANSITION_SETTLE_MILLIS)
            waitForUiIdle()
            scenario.onActivity { activity ->
                val root = activity.requireScenarioViewById<View>(R.id.demo_layout_constraint_root)
                assertEquals(
                    if (fixture.rtl) View.LAYOUT_DIRECTION_RTL else View.LAYOUT_DIRECTION_LTR,
                    root.layoutDirection,
                )
                val helperContainer = activity.requireViewByTestTagVisible(DemoTestTags.LAYOUTS_CONSTRAINT_HELPERS_CONTAINER)
                val headline = activity.requireViewByTestTagVisible(DemoTestTags.LAYOUTS_CONSTRAINT_HELPERS_HEADLINE)
                val summary = activity.requireViewByTestTagVisible(DemoTestTags.LAYOUTS_CONSTRAINT_HELPERS_SUMMARY)
                val marker = activity.requireViewByTestTagVisible(DemoTestTags.LAYOUTS_CONSTRAINT_HELPERS_MARKER)
                val guidelineLabel = activity.requireViewByTestTagVisible(
                    DemoTestTags.LAYOUTS_CONSTRAINT_HELPERS_GUIDELINE_LABEL,
                )
                val sourceTrailingEdge = if (fixture.rtl) {
                    minOf(headline.leftOnScreen(), summary.leftOnScreen())
                } else {
                    maxOf(headline.rightOnScreen(), summary.rightOnScreen())
                }
                val markerLeadingEdge = if (fixture.rtl) marker.rightOnScreen() else marker.leftOnScreen()
                assertTrue("Barrier marker must follow both source nodes for ${fixture.label}.", if (fixture.rtl) markerLeadingEdge < sourceTrailingEdge else markerLeadingEdge > sourceTrailingEdge)
                assertInside(helperContainer, marker)
                assertInside(helperContainer, guidelineLabel)
                marker.centerInsideOwningRecyclerView()
            }
            waitForUiIdle()
            preserveAfterConnectedTest(
                captureDeviceScreenshot("constraint-release-${fixture.label}-core"),
            )

            scenario.onActivity { activity ->
                val top = activity.requireViewByTestTagVisible(DemoTestTags.LAYOUTS_CONSTRAINT_HELPERS_FULL_PROBE_TOP)
                val middle = activity.requireViewByTestTagVisible(DemoTestTags.LAYOUTS_CONSTRAINT_HELPERS_FULL_PROBE_MIDDLE)
                val bottom = activity.requireViewByTestTagVisible(DemoTestTags.LAYOUTS_CONSTRAINT_HELPERS_FULL_PROBE_BOTTOM)
                val marker = requireNotNull(
                    findViewByTestTag(
                        activity.findViewById(android.R.id.content),
                        DemoTestTags.LAYOUTS_CONSTRAINT_HELPERS_FULL_MARKER,
                    ),
                )
                val container = top.requireConstraintLayoutAncestor()
                val guidelineDiagnostics = container.childrenOfType<androidx.constraintlayout.widget.Guideline>()
                    .joinToString(prefix = "guidelines=[", postfix = "]") { guideline ->
                        val params = guideline.layoutParams as ConstraintLayout.LayoutParams
                        "x=${guideline.left},begin=${params.guideBegin},end=${params.guideEnd}," +
                            "percent=${params.guidePercent},useRtl=${params.guidelineUseRtl}," +
                            "direction=${guideline.layoutDirection}"
                    }
                listOf(
                    "top" to top,
                    "middle" to middle,
                    "bottom" to bottom,
                    "marker" to marker,
                ).forEach { (label, child) ->
                    assertInside(
                        container,
                        child,
                        context = "${fixture.label}:$label containerDirection=${container.layoutDirection} " +
                            guidelineDiagnostics,
                    )
                }
                assertTrue("Top/middle/bottom probes must preserve vertical order.", top.topOnScreen() < middle.topOnScreen() && middle.topOnScreen() < bottom.topOnScreen())
                activity.clickByTestTag(DemoTestTags.LAYOUTS_CONSTRAINT_HELPERS_FULL_TOGGLE)
            }
            waitForUiIdle()
            scenario.onActivity { activity ->
                activity.requireViewByTestTagVisible(
                    DemoTestTags.LAYOUTS_CONSTRAINT_HELPERS_FULL_MARKER,
                ).centerInsideOwningRecyclerView()
            }
            waitForUiIdle()
            preserveAfterConnectedTest(
                captureDeviceScreenshot("constraint-release-${fixture.label}-all-barriers"),
            )

            scenario.onActivity { activity ->
                val groupA = activity.requireViewByTestTagVisible(DemoTestTags.LAYOUTS_CONSTRAINT_VIRTUAL_GROUP_MEMBER)
                val groupB = activity.requireViewByTestTagVisible(DemoTestTags.LAYOUTS_CONSTRAINT_VIRTUAL_GROUP_MEMBER_B)
                assertEquals(View.VISIBLE, groupA.visibility)
                assertEquals(View.VISIBLE, groupB.visibility)
                activity.clickByTestTag(DemoTestTags.LAYOUTS_CONSTRAINT_VIRTUAL_TOGGLE)
            }
            waitForUiIdle()
            scenario.onActivity { activity ->
                activity.requireViewByTestTagVisible(
                    DemoTestTags.LAYOUTS_CONSTRAINT_VIRTUAL_FLOW_C,
                ).centerInsideOwningRecyclerView()
            }
            waitForUiIdle()
            preserveAfterConnectedTest(
                captureDeviceScreenshot("constraint-release-${fixture.label}-virtual-flow"),
            )
            scenario.onActivity { activity ->
                activity.requireViewByTestTagVisible(
                    DemoTestTags.LAYOUTS_CONSTRAINT_VIRTUAL_PLACEHOLDER_NOTE,
                ).centerInsideOwningRecyclerView()
            }
            waitForUiIdle()
            preserveAfterConnectedTest(
                captureDeviceScreenshot("constraint-release-${fixture.label}-virtual"),
            )
            assertNoRenderWarnings()
        }
    }

    private fun preserveAfterConnectedTest(artifact: File) {
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        val outputDirectory = "/sdcard/Download/viewcompose-constraint-release"
        device.executeShellCommand("mkdir -p $outputDirectory")
        val outputPath = "$outputDirectory/${artifact.name}"
        device.executeShellCommand("cp ${artifact.absolutePath} $outputPath")
        assertEquals(outputPath, device.executeShellCommand("ls $outputPath").trim())
    }

    private fun launchConstraintFixture(fixture: FixtureCase) = launchDemoActivity<LayoutsActivity>(
        intent = LayoutsActivity.newConstraintVerificationIntent(
            context = ApplicationProvider.getApplicationContext(),
            rtl = fixture.rtl,
            fontScale = fixture.fontScale,
        ),
        themeMode = fixture.themeMode,
    )

    private fun assertNoRenderWarnings() {
        val warnings = DemoRenderDiagnosticsStore.recentSnapshots().flatMap { it.warnings }.distinct()
        assertTrue("Expected warning-free ConstraintLayout renders, but observed: $warnings", warnings.isEmpty())
    }

    private data class FixtureCase(
        val label: String,
        val themeMode: DemoThemeMode,
        val rtl: Boolean,
        val fontScale: Float,
    ) {
        companion object {
            val default = FixtureCase("light-ltr-font100", DemoThemeMode.Light, rtl = false, fontScale = 1f)
        }
    }

    private companion object {
        const val WINDOW_TRANSITION_SETTLE_MILLIS = 750L
    }
}

private fun View.topOnScreen(): Int = IntArray(2).also(::getLocationOnScreen)[1]

private fun View.leftOnScreen(): Int = IntArray(2).also(::getLocationOnScreen)[0]

private fun View.rightOnScreen(): Int = leftOnScreen() + width

private fun View.centerXOnScreen(): Int = leftOnScreen() + width / 2

private fun View.centerYOnScreen(): Int = topOnScreen() + height / 2

private fun View.screenBounds(): IntArray = intArrayOf(
    leftOnScreen(),
    topOnScreen(),
    rightOnScreen(),
    topOnScreen() + height,
)

private fun View.requireConstraintLayoutSelf(): ConstraintLayout =
    this as? ConstraintLayout ?: error("Expected ConstraintLayout but was ${javaClass.simpleName}")

private fun assertInside(container: View, child: View, context: String = "") {
    val containerBounds = "container=[${container.leftOnScreen()},${container.topOnScreen()}..${container.rightOnScreen()},${container.topOnScreen() + container.height}]"
    val childBounds = "child=${child.javaClass.simpleName} [${child.leftOnScreen()},${child.topOnScreen()}..${child.rightOnScreen()},${child.topOnScreen() + child.height}]"
    val diagnosticContext = context.takeIf(String::isNotBlank)?.let { " context=$it" }.orEmpty()
    assertTrue("Child left must remain inside its container: $containerBounds $childBounds$diagnosticContext", child.leftOnScreen() >= container.leftOnScreen())
    assertTrue("Child right must remain inside its container: $containerBounds $childBounds$diagnosticContext", child.rightOnScreen() <= container.rightOnScreen())
    assertTrue("Child top must remain inside its container: $containerBounds $childBounds$diagnosticContext", child.topOnScreen() >= container.topOnScreen())
    assertTrue("Child bottom must remain inside its container: $containerBounds $childBounds$diagnosticContext", child.topOnScreen() + child.height <= container.topOnScreen() + container.height)
}

private fun assertLayerTransform(
    view: View,
    rotation: Float,
    scale: Float,
    translationX: Float,
    translationY: Float,
) {
    assertTrue("Unexpected Layer rotation: ${view.rotation}", abs(view.rotation - rotation) < 0.5f)
    assertTrue("Unexpected Layer scaleX: ${view.scaleX}", abs(view.scaleX - scale) < 0.02f)
    assertTrue("Unexpected Layer scaleY: ${view.scaleY}", abs(view.scaleY - scale) < 0.02f)
    assertTrue("Unexpected Layer translationX: ${view.translationX}", abs(view.translationX - translationX) < 1.5f)
    assertTrue("Unexpected Layer translationY: ${view.translationY}", abs(view.translationY - translationY) < 1.5f)
}

private fun assertLayerScaleAndRotation(view: View, rotation: Float, scale: Float) {
    assertTrue("Unexpected Layer-projected rotation: ${view.rotation}", abs(view.rotation - rotation) < 0.5f)
    assertTrue("Unexpected Layer-projected scaleX: ${view.scaleX}", abs(view.scaleX - scale) < 0.02f)
    assertTrue("Unexpected Layer-projected scaleY: ${view.scaleY}", abs(view.scaleY - scale) < 0.02f)
}

private fun View.requireConstraintLayoutAncestor(): ConstraintLayout {
    var current = parent
    while (current is View) {
        if (current is ConstraintLayout) return current
        current = current.parent
    }
    error("Expected a ConstraintLayout ancestor for ${javaClass.simpleName}")
}

private fun View.centerInsideOwningRecyclerView() {
    var ancestor = parent
    while (ancestor != null && ancestor !is RecyclerView) {
        ancestor = ancestor.parent
    }
    val recyclerView = ancestor as? RecyclerView ?: return
    val viewLocation = IntArray(2).also(::getLocationOnScreen)
    val recyclerLocation = IntArray(2).also(recyclerView::getLocationOnScreen)
    val viewCenterY = viewLocation[1] + height / 2
    val recyclerCenterY = recyclerLocation[1] + recyclerView.height / 2
    recyclerView.scrollBy(0, viewCenterY - recyclerCenterY)
}

private inline fun <reified T : View> ViewGroup.childrenOfType(): List<T> = buildList {
    for (index in 0 until childCount) {
        (getChildAt(index) as? T)?.let(::add)
    }
}
