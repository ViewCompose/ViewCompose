package com.viewcompose.renderer.view.container

import android.app.Activity
import android.content.Context
import android.content.pm.ApplicationInfo
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import androidx.constraintlayout.helper.widget.Flow
import androidx.constraintlayout.helper.widget.Layer
import androidx.constraintlayout.widget.Barrier
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.Group
import androidx.constraintlayout.widget.Guideline
import androidx.constraintlayout.widget.Placeholder
import com.viewcompose.renderer.R
import com.viewcompose.renderer.modifier.resolve
import com.viewcompose.ui.environment.UiEnvironmentValues
import com.viewcompose.ui.environment.UiLayoutDirection
import com.viewcompose.ui.modifier.Modifier
import com.viewcompose.ui.modifier.Visibility
import com.viewcompose.ui.modifier.visibility
import com.viewcompose.ui.node.spec.ConstraintAnchor
import com.viewcompose.ui.node.spec.ConstraintAnchorLink
import com.viewcompose.ui.node.spec.ConstraintAnchorTarget
import com.viewcompose.ui.node.spec.ConstraintBarrierDirection
import com.viewcompose.ui.node.spec.ConstraintBarrierSpec
import com.viewcompose.ui.node.spec.ConstraintDimension
import com.viewcompose.ui.node.spec.ConstraintFlowSpec
import com.viewcompose.ui.node.spec.ConstraintGuidelineDirection
import com.viewcompose.ui.node.spec.ConstraintGuidelinePosition
import com.viewcompose.ui.node.spec.ConstraintGuidelineSpec
import com.viewcompose.ui.node.spec.ConstraintGroupSpec
import com.viewcompose.ui.node.spec.ConstraintHelperVisibility
import com.viewcompose.ui.node.spec.ConstraintHelpersSpec
import com.viewcompose.ui.node.spec.ConstraintItemSpec
import com.viewcompose.ui.node.spec.ConstraintLayerSpec
import com.viewcompose.ui.node.spec.ConstraintPlaceholderSpec
import com.viewcompose.ui.node.spec.ConstraintSetSpec
import com.viewcompose.ui.unit.UiDensity
import com.viewcompose.ui.unit.dp
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLog

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], manifest = Config.NONE)
class DeclarativeConstraintLayoutEnvironmentTest {
    @Test
    fun `native guideline rtl resolution preserves logical positions`() {
        val context = RuntimeEnvironment.getApplication()
        context.applicationInfo.flags =
            context.applicationInfo.flags or ApplicationInfo.FLAG_SUPPORTS_RTL
        val layout = DeclarativeConstraintLayout(context).apply {
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            installEnvironment(density = 2f, layoutDirection = UiLayoutDirection.Rtl)
        }

        fun assertGuideline(
            direction: ConstraintGuidelineDirection,
            position: ConstraintGuidelinePosition,
            expectedGuideBegin: Int = -1,
            expectedGuideEnd: Int = -1,
            expectedGuidePercent: Float = -1f,
            expectedLeft: Int,
        ) {
            layout.inlineHelpersSpec = ConstraintHelpersSpec(
                guidelines = listOf(
                    ConstraintGuidelineSpec(
                        id = "logical-guideline",
                        direction = direction,
                        position = position,
                    ),
                ),
            )
            layout.applyConstraintsNow()
            layout.measureAndLayout()

            val guideline = layout.getChildAt(0)
            val params = guideline.layoutParams as ConstraintLayout.LayoutParams
            assertEquals(expectedGuideBegin, params.guideBegin)
            assertEquals(expectedGuideEnd, params.guideEnd)
            assertEquals(expectedGuidePercent, params.guidePercent, 0f)
            assertTrue(params.guidelineUseRtl)
            assertEquals(expectedLeft, guideline.left)
        }

        assertGuideline(
            direction = ConstraintGuidelineDirection.FromStart,
            position = ConstraintGuidelinePosition.Offset(12.dp),
            expectedGuideBegin = 24,
            expectedLeft = 276,
        )
        assertGuideline(
            direction = ConstraintGuidelineDirection.FromEnd,
            position = ConstraintGuidelinePosition.Offset(16.dp),
            expectedGuideEnd = 32,
            expectedLeft = 32,
        )
        assertGuideline(
            direction = ConstraintGuidelineDirection.FromStart,
            position = ConstraintGuidelinePosition.Fraction(0.2f),
            expectedGuidePercent = 0.2f,
            expectedLeft = 240,
        )
        assertGuideline(
            direction = ConstraintGuidelineDirection.FromEnd,
            position = ConstraintGuidelinePosition.Fraction(0.3f),
            expectedGuidePercent = 0.7f,
            expectedLeft = 90,
        )
    }

    @Test
    fun `retained guideline follows runtime layout direction changes`() {
        val context = RuntimeEnvironment.getApplication()
        context.applicationInfo.flags =
            context.applicationInfo.flags or ApplicationInfo.FLAG_SUPPORTS_RTL
        val layout = DeclarativeConstraintLayout(context).apply {
            layoutDirection = View.LAYOUT_DIRECTION_LTR
            installEnvironment(density = 2f, layoutDirection = UiLayoutDirection.Ltr)
            inlineHelpersSpec = ConstraintHelpersSpec(
                guidelines = listOf(
                    ConstraintGuidelineSpec(
                        id = "logical-guideline",
                        direction = ConstraintGuidelineDirection.FromEnd,
                        position = ConstraintGuidelinePosition.Offset(16.dp),
                    ),
                ),
            )
        }

        layout.applyConstraintsNow()
        layout.measureAndLayout()
        val guideline = layout.getChildAt(0)
        assertEquals(View.LAYOUT_DIRECTION_LTR, guideline.layoutDirection)
        assertEquals(268, guideline.left)

        layout.layoutDirection = View.LAYOUT_DIRECTION_RTL
        // Android 9 can leave a retained programmatic helper resolved to its former direction.
        guideline.layoutDirection = View.LAYOUT_DIRECTION_LTR
        layout.installEnvironment(density = 2f, layoutDirection = UiLayoutDirection.Rtl)
        layout.requestConstraintRebuild()
        layout.applyConstraintsNow()
        layout.measureAndLayout()

        assertEquals(View.LAYOUT_DIRECTION_RTL, guideline.layoutDirection)
        assertEquals(32, guideline.left)
    }

    @Test
    fun `native layer accepts programmatic references before its first layout`() {
        val context = RuntimeEnvironment.getApplication()
        val layout = ConstraintLayout(context)
        val child = View(context).apply { id = View.generateViewId() }
        val layer = Layer(context).apply { id = View.generateViewId() }
        layout.addView(child)
        layout.addView(layer)

        layer.setReferencedIds(intArrayOf(child.id))
        layer.updatePreDraw(layout)
        layer.rotation = 30f

        assertEquals(30f, child.rotation, 0f)
    }

    @Test
    fun `constraint-only commits preserve modifier-owned runtime properties`() {
        val context = RuntimeEnvironment.getApplication()
        val layout = DeclarativeConstraintLayout(context).apply { installEnvironment(density = 1f) }
        val child = layout.addContent("child").apply {
            visibility = View.INVISIBLE
            alpha = 0.4f
            elevation = 7f
            rotation = 12f
            rotationX = 3f
            rotationY = 4f
            scaleX = 0.8f
            scaleY = 0.9f
            translationX = 5f
            translationY = 6f
            translationZ = 2f
        }
        layout.decoupledConstraintSetSpec = ConstraintSetSpec(
            constraints = mapOf("child" to fixedItem(width = 40, startMargin = 12)),
        )

        layout.applyConstraintsNow()
        layout.measureAndLayout()

        assertResolvedLayoutParams(child, width = 40, height = 20, startMargin = 12)
        assertEquals(View.INVISIBLE, child.visibility)
        assertEquals(0.4f, child.alpha, 0f)
        assertEquals(7f, child.elevation, 0f)
        assertEquals(12f, child.rotation, 0f)
        assertEquals(3f, child.rotationX, 0f)
        assertEquals(4f, child.rotationY, 0f)
        assertEquals(0.8f, child.scaleX, 0f)
        assertEquals(0.9f, child.scaleY, 0f)
        assertEquals(5f, child.translationX, 0f)
        assertEquals(6f, child.translationY, 0f)
        assertEquals(2f, child.translationZ, 0f)
    }

    @Test
    fun `group visibility changes survive constraint set application`() {
        val context = RuntimeEnvironment.getApplication()
        val layout = DeclarativeConstraintLayout(context).apply {
            installEnvironment(density = 1f)
        }
        val child = View(context).apply {
            setTag(R.id.viewcompose_constraint_layout_id, "child")
        }
        layout.addView(child)

        layout.inlineHelpersSpec = groupHelpers(ConstraintHelperVisibility.Gone)
        layout.applyConstraintsNow()
        layout.measureAndLayout()
        assertEquals(View.GONE, child.visibility)

        layout.inlineHelpersSpec = groupHelpers(ConstraintHelperVisibility.Visible)
        layout.applyConstraintsNow()
        layout.measureAndLayout()
        assertEquals(View.VISIBLE, child.visibility)
    }

    @Test
    fun `removing group restores content runtime properties instead of retaining helper overlay`() {
        val context = RuntimeEnvironment.getApplication()
        val layout = DeclarativeConstraintLayout(context).apply {
            installEnvironment(density = 1f)
        }
        val child = View(context).apply {
            visibility = View.INVISIBLE
            elevation = 7f
            translationZ = 3f
            setTag(R.id.viewcompose_constraint_layout_id, "child")
        }
        layout.addView(child)

        layout.inlineHelpersSpec = ConstraintHelpersSpec(
            groups = listOf(
                ConstraintGroupSpec(
                    id = "group",
                    referencedIds = listOf("child"),
                    visibility = ConstraintHelperVisibility.Gone,
                    elevation = 5.dp,
                ),
            ),
        )
        layout.applyConstraintsNow()
        layout.measureAndLayout()
        assertEquals(View.GONE, child.visibility)

        layout.inlineHelpersSpec = ConstraintHelpersSpec()
        layout.applyConstraintsNow()
        layout.measureAndLayout()

        assertEquals(View.INVISIBLE, child.visibility)
        assertEquals(7f, child.elevation, 0f)
        assertEquals(3f, child.translationZ, 0f)
        assertEquals(0, layout.managedHelperCountForTest)
    }

    @Test
    fun `removing group retains newly rebound declarative visibility`() {
        val context = RuntimeEnvironment.getApplication()
        val layout = DeclarativeConstraintLayout(context).apply { installEnvironment(density = 1f) }
        val child = View(context).apply {
            visibility = View.INVISIBLE
            setTag(
                R.id.viewcompose_resolved_modifiers,
                Modifier.visibility(Visibility.Invisible).resolve(),
            )
            setTag(R.id.viewcompose_constraint_layout_id, "child")
        }
        layout.addView(child)
        layout.inlineHelpersSpec = groupHelpers(ConstraintHelperVisibility.Gone)
        layout.applyConstraintsNow()
        layout.measureAndLayout()
        assertEquals(View.GONE, child.visibility)

        // Simulate the normal child binder running before its parent publishes the replacement graph.
        child.visibility = View.VISIBLE
        child.setTag(
            R.id.viewcompose_resolved_modifiers,
            Modifier.visibility(Visibility.Visible).resolve(),
        )
        layout.inlineHelpersSpec = ConstraintHelpersSpec()
        layout.applyConstraintsNow()
        layout.measureAndLayout()

        assertEquals(View.VISIBLE, child.visibility)
    }

    @Test
    fun `removing layer restores the child transform beneath the helper overlay`() {
        val context = RuntimeEnvironment.getApplication()
        val layout = DeclarativeConstraintLayout(context).apply { installEnvironment(density = 1f) }
        val activityController = Robolectric.buildActivity(Activity::class.java).setup()
        activityController.get().setContentView(layout)
        val child = layout.addContent("child").apply {
            rotation = 5f
            scaleX = 0.8f
            scaleY = 0.9f
            translationX = 2f
            translationY = 3f
        }
        layout.decoupledConstraintSetSpec = ConstraintSetSpec(
            constraints = mapOf("child" to fixedItem(width = 40, startMargin = 20)),
            helpers = ConstraintHelpersSpec(
                layers = listOf(
                    ConstraintLayerSpec(
                        id = "layer",
                        referencedIds = listOf("child"),
                        rotation = 30f,
                        scaleX = 1.5f,
                        scaleY = 0.5f,
                        translationX = 10.dp,
                        translationY = 12.dp,
                    ),
                ),
            ),
        )

        layout.applyConstraintsNow()
        assertEquals(null, layout.lastRejectionForTest)
        layout.measureAndLayout()
        assertEquals(false, layout.hasPendingLayerTransformForTest)
        assertEquals(30f, child.rotation, 0f)
        assertEquals(1.5f, child.scaleX, 0f)
        assertEquals(0.5f, child.scaleY, 0f)
        assertEquals(10f, child.translationX, 0f)
        assertEquals(12f, child.translationY, 0f)

        layout.decoupledConstraintSetSpec = ConstraintSetSpec(
            constraints = mapOf("child" to fixedItem(width = 40, startMargin = 20)),
        )
        layout.applyConstraintsNow()
        assertEquals(null, layout.lastRejectionForTest)
        assertEquals(5f, child.rotation, 0f)
        assertEquals(false, layout.hasPendingLayerTransformForTest)
        layout.measureAndLayoutBeforePreDraw()
        assertEquals(5f, child.rotation, 0f)
        layout.viewTreeObserver.dispatchOnPreDraw()

        assertEquals(5f, child.rotation, 0f)
        assertEquals(0.8f, child.scaleX, 0f)
        assertEquals(0.9f, child.scaleY, 0f)
        assertEquals(2f, child.translationX, 0f)
        assertEquals(3f, child.translationY, 0f)
        assertEquals(0, layout.managedHelperCountForTest)

        activityController.close()
    }

    @Test
    fun `detaching cancels and reattaching reschedules the accepted layer transform`() {
        val context = RuntimeEnvironment.getApplication()
        val layout = DeclarativeConstraintLayout(context).apply { installEnvironment(density = 1f) }
        val activityController = Robolectric.buildActivity(Activity::class.java).setup()
        activityController.get().setContentView(layout)
        val host = layout.parent as ViewGroup
        val child = layout.addContent("child")
        layout.decoupledConstraintSetSpec = ConstraintSetSpec(
            constraints = mapOf("child" to fixedItem(width = 40, startMargin = 20)),
            helpers = ConstraintHelpersSpec(
                layers = listOf(
                    ConstraintLayerSpec(
                        id = "layer",
                        referencedIds = listOf("child"),
                        rotation = 45f,
                    ),
                ),
            ),
        )

        layout.applyConstraintsNow()
        assertEquals(true, layout.hasPendingLayerTransformForTest)

        host.removeView(layout)
        assertEquals(false, layout.hasPendingLayerTransformForTest)

        host.addView(layout)
        assertEquals(true, layout.hasPendingLayerTransformForTest)
        layout.measureAndLayout()

        assertEquals(false, layout.hasPendingLayerTransformForTest)
        assertEquals(45f, child.rotation, 0f)
        assertEquals(null, layout.lastRejectionForTest)

        activityController.close()
    }

    @Test
    fun `pending graph rebuild survives detach and reattach`() {
        val context = RuntimeEnvironment.getApplication()
        val layout = DeclarativeConstraintLayout(context).apply { installEnvironment(density = 1f) }
        val activityController = Robolectric.buildActivity(Activity::class.java).setup()
        activityController.get().setContentView(layout)
        val host = layout.parent as ViewGroup
        val child = layout.addContent("child")
        layout.decoupledConstraintSetSpec = ConstraintSetSpec(
            constraints = mapOf("child" to fixedItem(width = 40, startMargin = 10)),
        )
        layout.applyConstraintsNow()
        val initialRevision = layout.acceptedRevisionForTest

        layout.decoupledConstraintSetSpec = ConstraintSetSpec(
            constraints = mapOf("child" to fixedItem(width = 70, startMargin = 25)),
        )
        host.removeView(layout)
        host.addView(layout)
        Shadows.shadowOf(Looper.getMainLooper()).idle()
        layout.measureAndLayout()

        assertTrue(layout.acceptedRevisionForTest > initialRevision)
        assertResolvedLayoutParams(child, width = 70, height = 20, startMargin = 25)
        assertEquals(listOf(25, 0, 95, 20), child.bounds())

        activityController.close()
    }

    @Test
    fun `removing placeholder releases content back to its own constraints`() {
        val context = RuntimeEnvironment.getApplication()
        val layout = DeclarativeConstraintLayout(context).apply { installEnvironment(density = 1f) }
        val content = layout.addContent("content")
        layout.decoupledConstraintSetSpec = ConstraintSetSpec(
            constraints = mapOf(
                "content" to fixedItem(width = 20, startMargin = 10),
                "slot" to ConstraintItemSpec(
                    width = ConstraintDimension.Fixed(60.dp),
                    height = ConstraintDimension.Fixed(30.dp),
                    start = ConstraintAnchorLink(
                        target = ConstraintAnchorTarget.parent(ConstraintAnchor.Start),
                        margin = 100.dp,
                    ),
                    top = ConstraintAnchorLink(
                        target = ConstraintAnchorTarget.parent(ConstraintAnchor.Top),
                        margin = 50.dp,
                    ),
                ),
            ),
            helpers = ConstraintHelpersSpec(
                placeholders = listOf(
                    ConstraintPlaceholderSpec(id = "slot", contentId = "content"),
                ),
            ),
        )

        layout.applyConstraintsNow()
        layout.measureAndLayout()
        val placeholder = (0 until layout.childCount)
            .map(layout::getChildAt)
            .filterIsInstance<Placeholder>()
            .single()
        assertEquals(listOf(100, 50, 160, 80), placeholder.bounds())
        assertEquals(placeholder.bounds(), content.bounds())

        layout.decoupledConstraintSetSpec = ConstraintSetSpec(
            constraints = mapOf("content" to fixedItem(width = 20, startMargin = 10)),
        )
        layout.applyConstraintsNow()
        layout.measureAndLayout()

        assertEquals(listOf(10, 0, 30, 20), content.bounds())
        assertEquals(View.VISIBLE, content.visibility)
        assertEquals(0, layout.managedHelperCountForTest)
    }

    @Test
    fun `constraint dimensions resolve again when local density changes`() {
        val context = RuntimeEnvironment.getApplication()
        val layout = DeclarativeConstraintLayout(context)
        val child = View(context).apply {
            setTag(R.id.viewcompose_constraint_layout_id, "child")
        }
        layout.addView(child)
        layout.decoupledConstraintSetSpec = ConstraintSetSpec(
            constraints = mapOf(
                "child" to ConstraintItemSpec(
                    width = ConstraintDimension.Fixed(20.dp),
                    height = ConstraintDimension.Fixed(10.dp),
                    start = ConstraintAnchorLink(
                        target = ConstraintAnchorTarget.parent(ConstraintAnchor.Start),
                        margin = 4.dp,
                    ),
                    top = ConstraintAnchorLink(
                        target = ConstraintAnchorTarget.parent(ConstraintAnchor.Top),
                    ),
                ),
            ),
        )

        layout.installEnvironment(density = 2f)
        layout.applyConstraintsNow()
        assertResolvedLayoutParams(child, width = 40, height = 20, startMargin = 8)

        layout.installEnvironment(density = 3f)
        layout.requestConstraintRebuild()
        layout.applyConstraintsNow()
        assertResolvedLayoutParams(child, width = 60, height = 30, startMargin = 12)
    }

    @Test
    fun `owned barrier resolves exact trailing geometry without unknown id warnings`() {
        ShadowLog.clear()
        val context = RuntimeEnvironment.getApplication()
        val layout = DeclarativeConstraintLayout(context).apply { installEnvironment(density = 1f) }
        val first = layout.addContent("first")
        val second = layout.addContent("second")
        val marker = layout.addContent("marker")
        layout.decoupledConstraintSetSpec = ConstraintSetSpec(
            constraints = mapOf(
                "first" to fixedItem(width = 40, startMargin = 10),
                "second" to fixedItem(width = 30, startMargin = 90),
                "marker" to ConstraintItemSpec(
                    width = ConstraintDimension.Fixed(10.dp),
                    height = ConstraintDimension.Fixed(10.dp),
                    start = ConstraintAnchorLink(
                        target = ConstraintAnchorTarget.ref("end", ConstraintAnchor.End),
                    ),
                    top = ConstraintAnchorLink(
                        target = ConstraintAnchorTarget.parent(ConstraintAnchor.Top),
                    ),
                ),
            ),
            helpers = ConstraintHelpersSpec(
                barriers = listOf(
                    ConstraintBarrierSpec(
                        id = "end",
                        direction = ConstraintBarrierDirection.End,
                        referencedIds = listOf("first", "second"),
                        margin = 5.dp,
                    ),
                ),
            ),
        )

        layout.applyConstraintsNow()
        layout.measureAndLayout()

        assertEquals(10, first.left)
        assertEquals(90, second.left)
        val expectedBarrierPosition = maxOf(first.right, second.right) + 5
        val barrier = (0 until layout.childCount)
            .map(layout::getChildAt)
            .filterIsInstance<Barrier>()
            .single()
        assertEquals(Barrier.END, barrier.type)
        assertEquals(5, barrier.margin)
        assertArrayEquals(intArrayOf(first.id, second.id), barrier.referencedIds)
        assertEquals(expectedBarrierPosition, barrier.left)
        assertEquals(expectedBarrierPosition, marker.left)
        assertEquals(1, layout.managedHelperCountForTest)
        assertTrue(
            ShadowLog.getLogsForTag("ConstraintSet").none { log ->
                log.msg.contains("id unknown", ignoreCase = true)
            },
        )
    }

    @Test
    fun `invalid candidate preserves accepted layout params bounds and revision`() {
        val context = RuntimeEnvironment.getApplication()
        val layout = DeclarativeConstraintLayout(context).apply { installEnvironment(density = 1f) }
        val child = layout.addContent("child")
        layout.decoupledConstraintSetSpec = ConstraintSetSpec(
            constraints = mapOf("child" to fixedItem(width = 40, startMargin = 12)),
        )
        layout.applyConstraintsNow()
        layout.measureAndLayout()
        val acceptedRevision = layout.acceptedRevisionForTest
        val acceptedParams = ConstraintLayout.LayoutParams(child.layoutParams as ConstraintLayout.LayoutParams)
        val acceptedBounds = listOf(child.left, child.top, child.right, child.bottom)

        layout.decoupledConstraintSetSpec = ConstraintSetSpec(
            constraints = mapOf(
                "child" to ConstraintItemSpec(
                    width = ConstraintDimension.Fixed(90.dp),
                    height = ConstraintDimension.Fixed(20.dp),
                    start = ConstraintAnchorLink(
                        target = ConstraintAnchorTarget.ref("missing", ConstraintAnchor.End),
                    ),
                ),
            ),
        )
        layout.applyConstraintsNow()
        layout.measureAndLayout()

        val actualParams = child.layoutParams as ConstraintLayout.LayoutParams
        assertEquals(acceptedRevision, layout.acceptedRevisionForTest)
        assertEquals(ConstraintGraphRejectionReason.MissingReference, layout.lastRejectionForTest?.reason)
        assertEquals(acceptedParams.width, actualParams.width)
        assertEquals(acceptedParams.height, actualParams.height)
        assertEquals(acceptedParams.marginStart, actualParams.marginStart)
        assertEquals(acceptedBounds, listOf(child.left, child.top, child.right, child.bottom))
    }

    @Test
    fun `valid candidate retries successfully after a rejected graph`() {
        val context = RuntimeEnvironment.getApplication()
        val layout = DeclarativeConstraintLayout(context).apply { installEnvironment(density = 1f) }
        val child = layout.addContent("child")
        layout.decoupledConstraintSetSpec = ConstraintSetSpec(
            constraints = mapOf(
                "child" to ConstraintItemSpec(
                    width = ConstraintDimension.Fixed(90.dp),
                    height = ConstraintDimension.Fixed(20.dp),
                    start = ConstraintAnchorLink(
                        target = ConstraintAnchorTarget.ref("missing", ConstraintAnchor.End),
                    ),
                ),
            ),
        )
        layout.applyConstraintsNow()
        val rejectedRevision = layout.acceptedRevisionForTest
        assertEquals(ConstraintGraphRejectionReason.MissingReference, layout.lastRejectionForTest?.reason)

        layout.decoupledConstraintSetSpec = ConstraintSetSpec(
            constraints = mapOf("child" to fixedItem(width = 60, startMargin = 30)),
        )
        layout.applyConstraintsNow()
        layout.measureAndLayout()

        assertTrue(layout.acceptedRevisionForTest > rejectedRevision)
        assertEquals(null, layout.lastRejectionForTest)
        assertResolvedLayoutParams(child, width = 60, height = 20, startMargin = 30)
        assertEquals(listOf(30, 0, 90, 20), child.bounds())
    }

    @Test
    fun `injected native commit failure rolls back helper and child state before retry`() {
        val context = RuntimeEnvironment.getApplication()
        val layout = DeclarativeConstraintLayout(context).apply { installEnvironment(density = 1f) }
        val child = FailingLayoutParamsView(context).apply {
            setTag(R.id.viewcompose_constraint_layout_id, "child")
        }
        layout.addView(child)
        layout.decoupledConstraintSetSpec = ConstraintSetSpec(
            constraints = mapOf("child" to fixedItem(width = 40, startMargin = 12)),
        )
        layout.applyConstraintsNow()
        layout.measureAndLayout()
        val acceptedRevision = layout.acceptedRevisionForTest
        val acceptedBounds = child.bounds()

        layout.decoupledConstraintSetSpec = ConstraintSetSpec(
            constraints = mapOf("child" to fixedItem(width = 90, startMargin = 50)),
            helpers = ConstraintHelpersSpec(
                barriers = listOf(
                    ConstraintBarrierSpec(
                        id = "temporary-helper",
                        direction = ConstraintBarrierDirection.End,
                        referencedIds = listOf("child"),
                    ),
                ),
            ),
        )
        child.failNextLayoutParamsAssignment = true
        layout.applyConstraintsNow()
        layout.measureAndLayout()

        assertEquals(acceptedRevision, layout.acceptedRevisionForTest)
        assertEquals(ConstraintGraphRejectionReason.NativeCommit, layout.lastRejectionForTest?.reason)
        assertEquals(0, layout.managedHelperCountForTest)
        assertResolvedLayoutParams(child, width = 40, height = 20, startMargin = 12)
        assertEquals(acceptedBounds, child.bounds())

        layout.decoupledConstraintSetSpec = ConstraintSetSpec(
            constraints = mapOf("child" to fixedItem(width = 60, startMargin = 30)),
        )
        layout.applyConstraintsNow()
        layout.measureAndLayout()

        assertTrue(layout.acceptedRevisionForTest > acceptedRevision)
        assertEquals(null, layout.lastRejectionForTest)
        assertResolvedLayoutParams(child, width = 60, height = 20, startMargin = 30)
        assertEquals(listOf(30, 0, 90, 20), child.bounds())
    }

    @Test
    fun `one registry retypes and prunes guideline and barrier without child growth`() {
        val context = RuntimeEnvironment.getApplication()
        val layout = DeclarativeConstraintLayout(context).apply { installEnvironment(density = 1f) }
        layout.addContent("child")

        repeat(1_000) { index ->
            layout.inlineHelpersSpec = if (index % 2 == 0) {
                ConstraintHelpersSpec(
                    guidelines = listOf(
                        ConstraintGuidelineSpec(
                            id = "switching-helper",
                            direction = ConstraintGuidelineDirection.FromStart,
                            position = ConstraintGuidelinePosition.Fraction(0.5f),
                        ),
                    ),
                )
            } else {
                ConstraintHelpersSpec(
                    barriers = listOf(
                        ConstraintBarrierSpec(
                            id = "switching-helper",
                            direction = ConstraintBarrierDirection.End,
                            referencedIds = listOf("child"),
                        ),
                    ),
                )
            }
            layout.applyConstraintsNow()
            assertEquals(1, layout.managedHelperCountForTest)
            assertEquals(2, layout.childCount)
        }

        assertTrue(layout.getChildAt(1) is Barrier)
        assertTrue((0 until layout.childCount).map(layout::getChildAt).none { it is Guideline })
        assertTrue(layout.diagnosticCountForTest <= 64)
    }

    @Test
    fun `one registry retypes a semantic id across every retained helper kind`() {
        val context = RuntimeEnvironment.getApplication()
        val layout = DeclarativeConstraintLayout(context).apply { installEnvironment(density = 1f) }
        val activityController = Robolectric.buildActivity(Activity::class.java).setup()
        activityController.get().setContentView(layout)
        val child = layout.addContent("child")
        val helperVariants = listOf(
            ConstraintHelpersSpec(
                guidelines = listOf(
                    ConstraintGuidelineSpec(
                        id = "shared-helper",
                        direction = ConstraintGuidelineDirection.FromStart,
                        position = ConstraintGuidelinePosition.Fraction(0.5f),
                    ),
                ),
            ) to Guideline::class.java,
            ConstraintHelpersSpec(
                barriers = listOf(
                    ConstraintBarrierSpec(
                        id = "shared-helper",
                        direction = ConstraintBarrierDirection.End,
                        referencedIds = listOf("child"),
                    ),
                ),
            ) to Barrier::class.java,
            ConstraintHelpersSpec(
                flows = listOf(
                    ConstraintFlowSpec(
                        id = "shared-helper",
                        referencedIds = listOf("child"),
                    ),
                ),
            ) to Flow::class.java,
            ConstraintHelpersSpec(
                groups = listOf(
                    ConstraintGroupSpec(
                        id = "shared-helper",
                        referencedIds = listOf("child"),
                    ),
                ),
            ) to Group::class.java,
            ConstraintHelpersSpec(
                layers = listOf(
                    ConstraintLayerSpec(
                        id = "shared-helper",
                        referencedIds = listOf("child"),
                        rotation = 15f,
                    ),
                ),
            ) to Layer::class.java,
            ConstraintHelpersSpec(
                placeholders = listOf(
                    ConstraintPlaceholderSpec(
                        id = "shared-helper",
                        contentId = "child",
                    ),
                ),
            ) to Placeholder::class.java,
        )

        helperVariants.forEach { (helpers, expectedClass) ->
            layout.inlineHelpersSpec = helpers
            layout.applyConstraintsNow()
            layout.measureAndLayout()

            assertEquals(null, layout.lastRejectionForTest)
            assertEquals(1, layout.managedHelperCountForTest)
            assertEquals(2, layout.childCount)
            val helper = (0 until layout.childCount)
                .map(layout::getChildAt)
                .single { it !== child }
            assertTrue(expectedClass.isInstance(helper))
        }

        layout.inlineHelpersSpec = ConstraintHelpersSpec()
        layout.applyConstraintsNow()
        layout.measureAndLayout()

        assertEquals(0, layout.managedHelperCountForTest)
        assertEquals(1, layout.childCount)
        assertEquals(View.VISIBLE, child.visibility)

        activityController.close()
    }

    @Test
    fun `reordering declarations preserves helper instances for every retained kind`() {
        val context = RuntimeEnvironment.getApplication()
        val layout = DeclarativeConstraintLayout(context).apply { installEnvironment(density = 1f) }
        val activityController = Robolectric.buildActivity(Activity::class.java).setup()
        activityController.get().setContentView(layout)
        val first = layout.addContent("first")
        val second = layout.addContent("second")
        layout.decoupledConstraintSetSpec = ConstraintSetSpec(
            constraints = mapOf(
                "first" to fixedItem(width = 20, startMargin = 10),
                "second" to fixedItem(width = 20, startMargin = 60),
            ),
        )
        val reorderVariants = listOf(
            ConstraintHelpersSpec(
                guidelines = listOf(
                    ConstraintGuidelineSpec(
                        id = "guide-a",
                        direction = ConstraintGuidelineDirection.FromStart,
                        position = ConstraintGuidelinePosition.Fraction(0.25f),
                    ),
                    ConstraintGuidelineSpec(
                        id = "guide-b",
                        direction = ConstraintGuidelineDirection.FromStart,
                        position = ConstraintGuidelinePosition.Fraction(0.75f),
                    ),
                ),
            ),
            ConstraintHelpersSpec(
                barriers = listOf(
                    ConstraintBarrierSpec("barrier-a", ConstraintBarrierDirection.End, listOf("first")),
                    ConstraintBarrierSpec("barrier-b", ConstraintBarrierDirection.End, listOf("second")),
                ),
            ),
            ConstraintHelpersSpec(
                flows = listOf(
                    ConstraintFlowSpec("flow-a", listOf("first")),
                    ConstraintFlowSpec("flow-b", listOf("second")),
                ),
            ),
            ConstraintHelpersSpec(
                groups = listOf(
                    ConstraintGroupSpec("group-a", listOf("first")),
                    ConstraintGroupSpec("group-b", listOf("second")),
                ),
            ),
            ConstraintHelpersSpec(
                layers = listOf(
                    ConstraintLayerSpec("layer-a", listOf("first")),
                    ConstraintLayerSpec("layer-b", listOf("second")),
                ),
            ),
            ConstraintHelpersSpec(
                placeholders = listOf(
                    ConstraintPlaceholderSpec("placeholder-a", "first"),
                    ConstraintPlaceholderSpec("placeholder-b", "second"),
                ),
            ),
        )

        reorderVariants.forEach { forward ->
            layout.inlineHelpersSpec = forward
            layout.applyConstraintsNow()
            layout.measureAndLayout()
            assertEquals(null, layout.lastRejectionForTest)
            val originalHelpers = layout.helperChildrenExcluding(first, second)
            assertEquals(2, originalHelpers.size)

            layout.inlineHelpersSpec = forward.reversedDeclarations()
            layout.applyConstraintsNow()
            layout.measureAndLayout()

            assertEquals(null, layout.lastRejectionForTest)
            assertEquals(originalHelpers, layout.helperChildrenExcluding(first, second))
            assertEquals(2, layout.managedHelperCountForTest)

            layout.inlineHelpersSpec = ConstraintHelpersSpec()
            layout.applyConstraintsNow()
            layout.measureAndLayout()
            assertEquals(0, layout.managedHelperCountForTest)
        }

        assertEquals(2, layout.childCount)
        activityController.close()
    }

    private fun DeclarativeConstraintLayout.installEnvironment(
        density: Float,
        layoutDirection: UiLayoutDirection = UiLayoutDirection.Ltr,
    ) {
        setTag(
            R.id.viewcompose_environment_values,
            UiEnvironmentValues.Default.copy(
                density = UiDensity(
                    density = density,
                    fontScale = 1f,
                ),
                layoutDirection = layoutDirection,
            ),
        )
    }

    private fun DeclarativeConstraintLayout.addContent(id: String): View {
        return View(context).also { child ->
            child.setTag(R.id.viewcompose_constraint_layout_id, id)
            addView(child)
        }
    }

    private fun fixedItem(
        width: Int,
        startMargin: Int,
    ): ConstraintItemSpec = ConstraintItemSpec(
        width = ConstraintDimension.Fixed(width.dp),
        height = ConstraintDimension.Fixed(20.dp),
        start = ConstraintAnchorLink(
            target = ConstraintAnchorTarget.parent(ConstraintAnchor.Start),
            margin = startMargin.dp,
        ),
        top = ConstraintAnchorLink(
            target = ConstraintAnchorTarget.parent(ConstraintAnchor.Top),
        ),
    )

    private fun groupHelpers(visibility: ConstraintHelperVisibility): ConstraintHelpersSpec =
        ConstraintHelpersSpec(
            groups = listOf(
                ConstraintGroupSpec(
                    id = "group",
                    referencedIds = listOf("child"),
                    visibility = visibility,
                ),
            ),
        )

    private fun DeclarativeConstraintLayout.measureAndLayout() {
        measureAndLayoutBeforePreDraw()
        viewTreeObserver.dispatchOnPreDraw()
    }

    private fun DeclarativeConstraintLayout.measureAndLayoutBeforePreDraw() {
        val spec = View.MeasureSpec.makeMeasureSpec(300, View.MeasureSpec.EXACTLY)
        measure(spec, spec)
        layout(0, 0, 300, 300)
    }

    private fun View.bounds(): List<Int> = listOf(left, top, right, bottom)

    private fun DeclarativeConstraintLayout.helperChildrenExcluding(
        vararg content: View,
    ): Set<View> = (0 until childCount)
        .map(::getChildAt)
        .filterNot { child -> content.any { it === child } }
        .toSet()

    private fun ConstraintHelpersSpec.reversedDeclarations(): ConstraintHelpersSpec = copy(
        guidelines = guidelines.reversed(),
        barriers = barriers.reversed(),
        flows = flows.reversed(),
        groups = groups.reversed(),
        layers = layers.reversed(),
        placeholders = placeholders.reversed(),
    )

    private class FailingLayoutParamsView(context: Context) : View(context) {
        var failNextLayoutParamsAssignment: Boolean = false

        override fun setLayoutParams(params: ViewGroup.LayoutParams?) {
            if (failNextLayoutParamsAssignment) {
                failNextLayoutParamsAssignment = false
                error("Injected LayoutParams assignment failure")
            }
            super.setLayoutParams(params)
        }
    }

    private fun assertResolvedLayoutParams(
        child: View,
        width: Int,
        height: Int,
        startMargin: Int,
    ) {
        val params = child.layoutParams as ConstraintLayout.LayoutParams
        assertEquals(width, params.width)
        assertEquals(height, params.height)
        assertEquals(startMargin, params.marginStart)
    }
}
