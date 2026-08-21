package com.viewcompose

import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ConstraintLayoutPhase3DeviceTest {
    private val device: UiDevice
        get() = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())

    @Before
    fun resetDiagnosticsAndLogs() {
        DemoRenderDiagnosticsStore.reset()
        device.executeShellCommand("logcat -c")
    }

    @Test
    fun helperLifecycle_rollsBackRejectedGraph_reusesKeys_andRemainsBoundedAfterReattach() {
        launchFixture().use { scenario ->
            waitForUiIdle()
            scenario.onActivity { activity ->
                val first = activity.requireViewByTestTagVisible(
                    DemoTestTags.LAYOUTS_CONSTRAINT_LIFECYCLE_FIRST,
                )
                val layout = first.requireConstraintLayoutAncestor()
                assertEquals(4, layout.managedHelperCount())
                assertEquals(7, layout.childCount)
                activity.clickByTestTag(DemoTestTags.LAYOUTS_CONSTRAINT_LIFECYCLE_TOGGLE)
            }
            waitForUiIdle()

            var removedRevision = 0L
            var removedAttempt = 0L
            var removedBounds = emptyMap<String, ScreenBounds>()
            var retainedNodeIdentities = emptyMap<String, Int>()
            scenario.onActivity { activity ->
                val tagged = lifecycleNodes(activity)
                val layout = tagged.getValue("first").requireConstraintLayoutAncestor()
                assertEquals(0, layout.managedHelperCount())
                assertEquals(3, layout.childCount)
                removedRevision = layout.acceptedRevision()
                removedAttempt = layout.attemptedRevision()
                removedBounds = tagged.mapValues { (_, view) -> view.screenBounds() }
                retainedNodeIdentities = tagged.mapValues { (_, view) -> System.identityHashCode(view) }
                activity.clickByTestTag(DemoTestTags.LAYOUTS_CONSTRAINT_LIFECYCLE_TOGGLE)
            }
            waitForUiIdle()
            SystemClock.sleep(DIAGNOSTIC_SETTLE_MILLIS)
            waitForUiIdle()

            scenario.onActivity { activity ->
                val tagged = lifecycleNodes(activity)
                val layout = tagged.getValue("first").requireConstraintLayoutAncestor()
                assertEquals("Rejected graph must preserve the accepted revision.", removedRevision, layout.acceptedRevision())
                assertEquals(removedAttempt + 1L, layout.attemptedRevision())
                assertEquals(0, layout.managedHelperCount())
                assertEquals(3, layout.childCount)
                assertEquals("MissingReference", layout.rejectionReason())
                assertEquals(removedBounds, tagged.mapValues { (_, view) -> view.screenBounds() })
                assertTrue(
                    activity.textForTag(DemoTestTags.LAYOUTS_CONSTRAINT_LIFECYCLE_FAILURE)
                        .contains("MissingReference"),
                )
                activity.clickByTestTag(DemoTestTags.LAYOUTS_CONSTRAINT_LIFECYCLE_TOGGLE)
            }
            waitForUiIdle()

            scenario.onActivity { activity ->
                val tagged = lifecycleNodes(activity)
                val layout = tagged.getValue("first").requireConstraintLayoutAncestor()
                assertTrue(layout.acceptedRevision() > removedRevision)
                assertEquals(4, layout.managedHelperCount())
                assertEquals(7, layout.childCount)
                assertNull(layout.rejection())
                assertEquals(
                    "Stable keys must retain all three content Views while emission order reverses.",
                    retainedNodeIdentities,
                    tagged.mapValues { (_, view) -> System.identityHashCode(view) },
                )
                assertTrue(tagged.getValue("first").leftOnScreen() < tagged.getValue("second").leftOnScreen())
                assertTrue(tagged.getValue("second").leftOnScreen() < tagged.getValue("third").leftOnScreen())
                activity.requireViewByTestTagVisible(DemoTestTags.LAYOUTS_CONSTRAINT_WRAP_BEHAVIOR_STATUS)
            }
            waitForUiIdle()

            scenario.onActivity { activity ->
                val first = activity.requireViewByTestTagVisible(
                    DemoTestTags.LAYOUTS_CONSTRAINT_LIFECYCLE_FIRST,
                )
                val remounted = first.requireConstraintLayoutAncestor()
                assertEquals("Reattached fixture must recreate only the active Grid proxies.", 4, remounted.managedHelperCount())
                assertEquals(7, remounted.childCount)
            }

            val warnings = relevantConstraintWarnings()
            assertEquals("The rejected candidate should emit one bounded warning.\n$warnings", 1, warnings.size)
            assertTrue(warnings.single().contains("UIConstraintLayout"))
            assertTrue(warnings.single().contains("MissingReference"))
            assertNoRenderWarnings()
        }
    }

    @Test
    fun gridCircularGoneMarginAndWrapBehavior_publishExactPhysicalGeometry() {
        launchFixture(
            sections = listOf(
                "constraint_grid",
                "constraint_circular_flow",
                "constraint_gone_margin",
                "constraint_wrap_behavior",
            ),
        ).use { scenario ->
            waitForUiIdle()
            var gridTopology: Long? = null
            var gridScalar: Long? = null
            scenario.onActivity { activity ->
                val hero = activity.requireViewByTestTagVisible(DemoTestTags.LAYOUTS_CONSTRAINT_GRID_HERO)
                val layout = hero.requireConstraintLayoutAncestor()
                gridTopology = layout.topologyFingerprint()
                gridScalar = layout.scalarFingerprint()
                assertEquals(6, layout.managedHelperCount())
                activity.clickByTestTag(DemoTestTags.LAYOUTS_CONSTRAINT_GRID_TOGGLE)
            }
            waitForUiIdle()
            scenario.onActivity { activity ->
                val updated = activity.requireViewByTestTagVisible(DemoTestTags.LAYOUTS_CONSTRAINT_GRID_HERO)
                    .requireConstraintLayoutAncestor()
                assertTrue(
                    "Grid orientation changes resolved member placement and therefore topology.",
                    gridTopology != updated.topologyFingerprint(),
                )
                assertTrue(gridScalar != updated.scalarFingerprint())
                assertEquals(6, updated.managedHelperCount())
            }

            var circularTopology: Long? = null
            var circularScalar: Long? = null
            scenario.onActivity { activity ->
                val center = activity.requireViewByTestTagVisible(DemoTestTags.LAYOUTS_CONSTRAINT_CIRCULAR_CENTER)
                assertCircularOrbit(activity, center, radiusDp = 78f, angleOffsetDegrees = 0f)
                val layout = center.requireConstraintLayoutAncestor()
                circularTopology = layout.topologyFingerprint()
                circularScalar = layout.scalarFingerprint()
                assertEquals(0, layout.managedHelperCount())
                activity.clickByTestTag(DemoTestTags.LAYOUTS_CONSTRAINT_CIRCULAR_TOGGLE)
            }
            waitForUiIdle()
            scenario.onActivity { activity ->
                val updatedCenter = activity.requireViewByTestTagVisible(
                    DemoTestTags.LAYOUTS_CONSTRAINT_CIRCULAR_CENTER,
                )
                val updatedLayout = updatedCenter.requireConstraintLayoutAncestor()
                assertEquals(circularTopology, updatedLayout.topologyFingerprint())
                assertTrue(circularScalar != updatedLayout.scalarFingerprint())
                assertCircularOrbit(activity, updatedCenter, radiusDp = 90f, angleOffsetDegrees = 15f)
                assertEquals(0, updatedLayout.managedHelperCount())
            }

            scenario.onActivity { activity ->
                activity.requireViewByTestTagVisible(DemoTestTags.LAYOUTS_CONSTRAINT_GONE_MARGIN_TARGET)
                val marker = activity.requireViewByTestTagVisible(DemoTestTags.LAYOUTS_CONSTRAINT_GONE_MARGIN_MARKER)
                val layout = marker.requireConstraintLayoutAncestor()
                val targetHost = layout.requireContentView("gone-target")
                val markerHost = layout.requireContentView("gone-marker")
                assertEquals(View.VISIBLE, targetHost.visibility)
                assertNear(activity.dp(24f), markerHost.left - targetHost.right)
                activity.clickByTestTag(DemoTestTags.LAYOUTS_CONSTRAINT_GONE_MARGIN_TOGGLE)
            }
            waitForUiIdle()
            scenario.onActivity { activity ->
                val marker = activity.requireViewByTestTagVisible(DemoTestTags.LAYOUTS_CONSTRAINT_GONE_MARGIN_MARKER)
                val layout = marker.requireConstraintLayoutAncestor()
                val targetHost = layout.requireContentView("gone-target")
                val markerHost = layout.requireContentView("gone-marker")
                assertEquals(View.GONE, targetHost.visibility)
                val params = markerHost.layoutParams as ConstraintLayout.LayoutParams
                assertEquals(activity.dp(64f), params.goneStartMargin)
                assertNear(activity.dp(64f), markerHost.left - layout.paddingLeft)
            }

            val expectedWrapSizes = listOf(
                224f to 136f,
                224f to 36f,
                36f to 136f,
                36f to 36f,
            )
            expectedWrapSizes.forEachIndexed { index, (widthDp, heightDp) ->
                scenario.onActivity { activity ->
                    val container = activity.requireViewByTestTagVisible(
                        DemoTestTags.LAYOUTS_CONSTRAINT_WRAP_BEHAVIOR_CONTAINER,
                    )
                    val remote = activity.requireViewByTestTagVisible(
                        DemoTestTags.LAYOUTS_CONSTRAINT_WRAP_BEHAVIOR_REMOTE,
                    )
                    assertNear(activity.dp(widthDp), container.width)
                    assertNear(activity.dp(heightDp), container.height)
                    assertNear(activity.dp(148f), remote.leftOnScreen() - container.leftOnScreen())
                    assertNear(activity.dp(92f), remote.topOnScreen() - container.topOnScreen())
                    if (index < expectedWrapSizes.lastIndex) {
                        activity.clickByTestTag(DemoTestTags.LAYOUTS_CONSTRAINT_WRAP_BEHAVIOR_TOGGLE)
                    }
                }
                waitForUiIdle()
            }
            assertTrue("Supported geometry interactions must not log warnings.", relevantConstraintWarnings().isEmpty())
            assertNoRenderWarnings()
        }
    }

    @Test
    fun anchorsDimensionsBiasAndChains_useToleranceBoundedNativeGeometry() {
        launchFixture(
            sections = listOf(
                "constraint_vertical_chain",
                "constraint_basic",
                "constraint_anchor_advanced",
                "constraint_dimension_advanced",
            ),
        ).use { scenario ->
            waitForUiIdle()
            repeat(8) {
                scenario.onActivity { activity ->
                    val recycler = activity.findViewById<View>(android.R.id.content).requireRecyclerView()
                    recycler.scrollBy(0, -recycler.height)
                }
                waitForUiIdle()
            }
            SystemClock.sleep(DIAGNOSTIC_SETTLE_MILLIS)
            waitForUiIdle()
            scenario.onActivity { activity ->
                val container = activity.requireVerticalChainLayout()
                val top = container.requireContentView("v-chain-top")
                val middle = container.requireContentView("v-chain-middle")
                val bottom = container.requireContentView("v-chain-bottom")
                assertNear(activity.dp(42f), top.height)
                assertNear(top.height, middle.height)
                assertNear(top.height, bottom.height)
                assertNear(container.topOnScreen() + container.paddingTop, top.topOnScreen())
                assertNear(container.bottomOnScreen() - container.paddingBottom, bottom.bottomOnScreen())
                assertNear(middle.topOnScreen() - top.bottomOnScreen(), bottom.topOnScreen() - middle.bottomOnScreen())
                activity.clickByTestTag(DemoTestTags.LAYOUTS_CONSTRAINT_VERTICAL_CHAIN_TOGGLE)
            }
            waitForUiIdle()
            scenario.onActivity { activity ->
                val container = activity.requireVerticalChainLayout()
                val top = container.requireContentView("v-chain-top")
                val middle = container.requireContentView("v-chain-middle")
                val bottom = container.requireContentView("v-chain-bottom")
                assertNear(top.height, bottom.height)
                assertNear(top.height * 2, middle.height)
                assertNear(container.topOnScreen() + container.paddingTop, top.topOnScreen())
                assertNear(container.bottomOnScreen() - container.paddingBottom, bottom.bottomOnScreen())
            }

            scenario.onActivity { activity ->
                val container = activity.requireViewByTestTagVisible(DemoTestTags.LAYOUTS_CONSTRAINT_BASIC_CONTAINER)
                val content = activity.requireViewByTestTagVisible(DemoTestTags.LAYOUTS_CONSTRAINT_BASIC_CONTENT)
                val badge = activity.requireViewByTestTagVisible(DemoTestTags.LAYOUTS_CONSTRAINT_BASIC_BADGE)
                val margin = activity.dp(8f)
                val available = content.width - badge.width - margin * 2
                val expectedLeft = content.leftOnScreen() + margin + (available * 0.78f).roundToInt()
                assertNear(expectedLeft, badge.leftOnScreen())
                assertInsideOnScreen(container, badge)
            }

            scenario.onActivity { activity ->
                val leader = activity.requireViewByTestTagVisible(DemoTestTags.LAYOUTS_CONSTRAINT_ANCHOR_ADVANCED_LEADER)
                val baseline = activity.requireViewByTestTagVisible(
                    DemoTestTags.LAYOUTS_CONSTRAINT_ANCHOR_ADVANCED_BASELINE,
                ) as TextView
                val baselineTop = activity.requireViewByTestTagVisible(
                    DemoTestTags.LAYOUTS_CONSTRAINT_ANCHOR_ADVANCED_BASELINE_TOP,
                ) as TextView
                val baselineBottom = activity.requireViewByTestTagVisible(
                    DemoTestTags.LAYOUTS_CONSTRAINT_ANCHOR_ADVANCED_BASELINE_BOTTOM,
                ) as TextView
                val leaderText = leader as TextView
                assertNear(leaderText.topOnScreen() + leaderText.baseline, baseline.topOnScreen() + baseline.baseline)
                assertNear(leaderText.topOnScreen() + activity.dp(2f), baselineTop.topOnScreen() + baselineTop.baseline)
                assertNear(leaderText.bottomOnScreen() + activity.dp(2f), baselineBottom.topOnScreen() + baselineBottom.baseline)

                val container = activity.requireViewByTestTagVisible(
                    DemoTestTags.LAYOUTS_CONSTRAINT_ANCHOR_ADVANCED_CONTAINER,
                )
                val centered = activity.requireViewByTestTagVisible(
                    DemoTestTags.LAYOUTS_CONSTRAINT_ANCHOR_ADVANCED_CENTERED,
                )
                assertNear(container.centerXOnScreen(), centered.centerXOnScreen())
                assertNear(container.centerYOnScreen(), centered.centerYOnScreen())
                val circleCenter = activity.requireViewByTestTagVisible(
                    DemoTestTags.LAYOUTS_CONSTRAINT_ANCHOR_ADVANCED_CIRCLE_CENTER,
                )
                val circle = activity.requireViewByTestTagVisible(
                    DemoTestTags.LAYOUTS_CONSTRAINT_ANCHOR_ADVANCED_CIRCLE,
                )
                assertPolarOffset(activity, circleCenter, circle, radiusDp = 54f, angleDegrees = 225f)
                val target = activity.requireViewByTestTagVisible(DemoTestTags.LAYOUTS_CONSTRAINT_ANCHOR_ADVANCED_TARGET)
                val linked = activity.requireViewByTestTagVisible(DemoTestTags.LAYOUTS_CONSTRAINT_ANCHOR_ADVANCED_LINKED)
                assertNear(activity.dp(8f), target.topOnScreen() - linked.bottomOnScreen())
            }

            scenario.onActivity { activity ->
                assertDimensionState(activity, expanded = false)
                activity.clickByTestTag(DemoTestTags.LAYOUTS_CONSTRAINT_DIMENSION_ADVANCED_TOGGLE)
            }
            waitForUiIdle()
            scenario.onActivity { activity -> assertDimensionState(activity, expanded = true) }
            val warnings = relevantConstraintWarnings()
            assertTrue("Supported anchor, dimension, and chain states must remain warning-free.\n$warnings", warnings.isEmpty())
            assertNoRenderWarnings()
        }
    }

    @Test
    fun activityRecreation_rebuildsDensityAndLogicalDirection_withoutRetainingOldHelpers() {
        launchFixture(
            sections = listOf(
                "constraint_gone_margin",
                "constraint_helper_lifecycle",
            ),
        ).use { scenario ->
            waitForUiIdle()
            var ltrTargetLeft = 0
            scenario.onActivity { activity ->
                val target = activity.requireViewByTestTagVisible(DemoTestTags.LAYOUTS_CONSTRAINT_GONE_MARGIN_TARGET)
                ltrTargetLeft = target.leftOnScreen()
                assertNear(activity.dp(72f), target.width)
                activity.intent
                    .putExtra(LayoutsActivity.EXTRA_VERIFICATION_RTL, true)
                    .putExtra(LayoutsActivity.EXTRA_VERIFICATION_FONT_SCALE, 1.3f)
                    .putExtra(LayoutsActivity.EXTRA_VERIFICATION_DENSITY_SCALE, 1.25f)
            }
            scenario.recreate()
            SystemClock.sleep(RECREATION_SETTLE_MILLIS)
            waitForUiIdle()
            scenario.onActivity { activity ->
                val root = activity.requireScenarioViewById<View>(R.id.demo_layout_constraint_root)
                assertEquals(View.LAYOUT_DIRECTION_RTL, root.layoutDirection)
                val target = activity.requireViewByTestTagVisible(DemoTestTags.LAYOUTS_CONSTRAINT_GONE_MARGIN_TARGET)
                assertNear((activity.dp(72f) * 1.25f).roundToInt(), target.width)
                assertTrue("Logical start must move the target across the parent in RTL.", target.leftOnScreen() > ltrTargetLeft)
                val lifecycle = activity.requireViewByTestTagVisible(
                    DemoTestTags.LAYOUTS_CONSTRAINT_LIFECYCLE_FIRST,
                ).requireConstraintLayoutAncestor()
                assertEquals(4, lifecycle.managedHelperCount())
                assertEquals(7, lifecycle.childCount)
            }
            val warnings = relevantConstraintWarnings()
            assertTrue("Recreation must remain warning-free.\n$warnings", warnings.isEmpty())
            assertNoRenderWarnings()
        }
    }

    private fun launchFixture(sections: List<String>? = null) = launchDemoActivity<LayoutsActivity>(
        intent = LayoutsActivity.newConstraintVerificationIntent(
            context = ApplicationProvider.getApplicationContext(),
            rtl = false,
            fontScale = 1f,
            sections = sections,
        ),
        themeMode = DemoThemeMode.Light,
    )

    private fun lifecycleNodes(activity: LayoutsActivity): Map<String, View> = mapOf(
        "first" to activity.requireViewByTestTagVisible(DemoTestTags.LAYOUTS_CONSTRAINT_LIFECYCLE_FIRST),
        "second" to activity.requireViewByTestTagVisible(DemoTestTags.LAYOUTS_CONSTRAINT_LIFECYCLE_SECOND),
        "third" to activity.requireViewByTestTagVisible(DemoTestTags.LAYOUTS_CONSTRAINT_LIFECYCLE_THIRD),
    )

    private fun assertCircularOrbit(
        activity: LayoutsActivity,
        center: View,
        radiusDp: Float,
        angleOffsetDegrees: Float,
    ) {
        listOf(
            DemoTestTags.LAYOUTS_CONSTRAINT_CIRCULAR_TOP to angleOffsetDegrees,
            DemoTestTags.LAYOUTS_CONSTRAINT_CIRCULAR_RIGHT to angleOffsetDegrees + 90f,
            DemoTestTags.LAYOUTS_CONSTRAINT_CIRCULAR_BOTTOM to angleOffsetDegrees + 180f,
            DemoTestTags.LAYOUTS_CONSTRAINT_CIRCULAR_LEFT to angleOffsetDegrees + 270f,
        ).forEach { (tag, angle) ->
            assertPolarOffset(
                activity = activity,
                center = center,
                item = activity.requireViewByTestTagVisible(tag),
                radiusDp = radiusDp,
                angleDegrees = angle,
            )
        }
    }

    private fun assertPolarOffset(
        activity: LayoutsActivity,
        center: View,
        item: View,
        radiusDp: Float,
        angleDegrees: Float,
    ) {
        val radius = activity.dp(radiusDp)
        val radians = Math.toRadians((angleDegrees % 360f).toDouble())
        val expectedX = (radius * sin(radians)).roundToInt()
        val expectedY = (-radius * cos(radians)).roundToInt()
        assertNear(expectedX, item.centerXOnScreen() - center.centerXOnScreen(), tolerance = 3)
        assertNear(expectedY, item.centerYOnScreen() - center.centerYOnScreen(), tolerance = 3)
    }

    private fun assertDimensionState(activity: LayoutsActivity, expanded: Boolean) {
        val container = activity.requireViewByTestTagVisible(
            DemoTestTags.LAYOUTS_CONSTRAINT_DIMENSION_ADVANCED_CONTAINER,
        )
        val widthNode = activity.requireViewByTestTagVisible(
            DemoTestTags.LAYOUTS_CONSTRAINT_DIMENSION_ADVANCED_WIDTH,
        )
        val heightNode = activity.requireViewByTestTagVisible(
            DemoTestTags.LAYOUTS_CONSTRAINT_DIMENSION_ADVANCED_HEIGHT,
        )
        val ratioNode = activity.requireViewByTestTagVisible(
            DemoTestTags.LAYOUTS_CONSTRAINT_DIMENSION_ADVANCED_RATIO,
        )
        val solverWidth = container.width - container.paddingLeft - container.paddingRight
        val solverHeight = container.height - container.paddingTop - container.paddingBottom
        val expectedWidth = (solverWidth * if (expanded) 0.82f else 0.56f).roundToInt()
            .coerceIn(activity.dp(120f), activity.dp(280f))
        val expectedHeight = (solverHeight * if (expanded) 0.62f else 0.38f).roundToInt()
            .coerceIn(activity.dp(64f), activity.dp(146f))
        assertNear(expectedWidth, widthNode.width)
        assertNear(expectedHeight, heightNode.height)
        assertNear(activity.dp(80f), ratioNode.width)
        assertNear(activity.dp(if (expanded) 45f else 80f), ratioNode.height)
    }

    private fun relevantConstraintWarnings(): List<String> = device
        .executeShellCommand("logcat -d -v brief UIConstraintLayout:W ConstraintSet:W AndroidRuntime:E '*:S'")
        .lineSequence()
        .filter { line ->
            line.startsWith("W/UIConstraintLayout") ||
                line.startsWith("W/ConstraintSet") ||
                line.startsWith("E/AndroidRuntime")
        }
        .toList()

    private fun assertNoRenderWarnings() {
        val warnings = DemoRenderDiagnosticsStore.recentSnapshots().flatMap { it.warnings }.distinct()
        assertTrue("Expected warning-free render diagnostics, but observed: $warnings", warnings.isEmpty())
    }

    private companion object {
        const val DIAGNOSTIC_SETTLE_MILLIS = 120L
        const val RECREATION_SETTLE_MILLIS = 750L
    }
}

private data class ScreenBounds(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
)

private fun View.screenBounds(): ScreenBounds = ScreenBounds(
    left = leftOnScreen(),
    top = topOnScreen(),
    right = rightOnScreen(),
    bottom = bottomOnScreen(),
)

private fun View.leftOnScreen(): Int = IntArray(2).also(::getLocationOnScreen)[0]

private fun View.topOnScreen(): Int = IntArray(2).also(::getLocationOnScreen)[1]

private fun View.rightOnScreen(): Int = leftOnScreen() + width

private fun View.bottomOnScreen(): Int = topOnScreen() + height

private fun View.centerXOnScreen(): Int = leftOnScreen() + width / 2

private fun View.centerYOnScreen(): Int = topOnScreen() + height / 2

private fun LayoutsActivity.dp(value: Float): Int = (value * resources.displayMetrics.density).roundToInt()

private fun assertNear(expected: Int, actual: Int, tolerance: Int = 2) {
    assertTrue("Expected $expected ± $tolerance but was $actual.", abs(expected - actual) <= tolerance)
}

private fun assertInsideOnScreen(container: View, child: View) {
    assertTrue(child.leftOnScreen() >= container.leftOnScreen())
    assertTrue(child.topOnScreen() >= container.topOnScreen())
    assertTrue(child.rightOnScreen() <= container.rightOnScreen())
    assertTrue(child.bottomOnScreen() <= container.bottomOnScreen())
}

private fun View.requireConstraintLayoutAncestor(): ConstraintLayout {
    var current: View? = this
    while (current != null) {
        if (current is ConstraintLayout) return current
        current = current.parent as? View
    }
    error("Expected ${javaClass.simpleName} to have a ConstraintLayout ancestor.")
}

private fun LayoutsActivity.textForTag(tag: String): String {
    val root = findViewById<ViewGroup>(android.R.id.content)
    return (requireNotNull(findViewByTestTag(root, tag)) as TextView).text.toString()
}

private fun LayoutsActivity.requireVerticalChainLayout(): ConstraintLayout {
    val root = findViewById<ViewGroup>(android.R.id.content)
    val container = findViewByTestTag(root, DemoTestTags.LAYOUTS_CONSTRAINT_VERTICAL_CHAIN_CONTAINER)
        ?: error(
            "Missing vertical-chain container; verification sections=" +
                intent.getStringArrayExtra(LayoutsActivity.EXTRA_VERIFICATION_SECTIONS)?.toList(),
        )
    return container.requireConstraintLayoutAncestor()
}

private fun View.requireRecyclerView(): RecyclerView = findRecyclerView()
    ?: error("Missing RecyclerView below ${javaClass.simpleName}.")

private fun View.findRecyclerView(): RecyclerView? {
    if (this is RecyclerView) return this
    if (this is ViewGroup) {
        for (index in 0 until childCount) {
            getChildAt(index).findRecyclerView()?.let { return it }
        }
    }
    return null
}

private fun ConstraintLayout.acceptedRevision(): Long = readPrivateField("acceptedRevision") as Long

private fun ConstraintLayout.attemptedRevision(): Long = readPrivateField("attemptedRevision") as Long

private fun ConstraintLayout.managedHelperCount(): Int =
    (readPrivateField("helperViews") as Map<*, *>).size

private fun ConstraintLayout.requireContentView(referenceId: String): View {
    for (index in 0 until childCount) {
        val child = getChildAt(index)
        if (child.getTag(com.viewcompose.renderer.R.id.viewcompose_constraint_layout_id) == referenceId) {
            return child
        }
    }
    error("Missing direct ConstraintLayout content '$referenceId'.")
}

private fun ConstraintLayout.topologyFingerprint(): Long? =
    readPrivateField("acceptedGraph")?.readPrivateField("topologyFingerprint") as? Long

private fun ConstraintLayout.scalarFingerprint(): Long? =
    readPrivateField("acceptedGraph")?.readPrivateField("scalarFingerprint") as? Long

private fun ConstraintLayout.rejection(): Any? = readPrivateField("lastRejection")

private fun ConstraintLayout.rejectionReason(): String? =
    rejection()?.readPrivateField("reason")?.toString()

private fun Any.readPrivateField(name: String): Any? {
    var type: Class<*>? = javaClass
    while (type != null) {
        val field = runCatching { type.getDeclaredField(name) }.getOrNull()
        if (field != null) {
            field.isAccessible = true
            return field.get(this)
        }
        type = type.superclass
    }
    error("Missing field '$name' on ${javaClass.name}.")
}
