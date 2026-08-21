package com.viewcompose.renderer.view.container

import android.content.pm.ApplicationInfo
import android.view.View
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import com.viewcompose.renderer.R
import com.viewcompose.ui.environment.UiEnvironmentValues
import com.viewcompose.ui.environment.UiLayoutDirection
import com.viewcompose.ui.node.spec.ConstraintAnchor
import com.viewcompose.ui.node.spec.ConstraintAnchorLink
import com.viewcompose.ui.node.spec.ConstraintAnchorTarget
import com.viewcompose.ui.node.spec.ConstraintBarrierDirection
import com.viewcompose.ui.node.spec.ConstraintBarrierSpec
import com.viewcompose.ui.node.spec.ConstraintChainOrientation
import com.viewcompose.ui.node.spec.ConstraintChainSpec
import com.viewcompose.ui.node.spec.ConstraintChainStyle
import com.viewcompose.ui.node.spec.ConstraintCircularFlowItemSpec
import com.viewcompose.ui.node.spec.ConstraintCircularFlowSpec
import com.viewcompose.ui.node.spec.ConstraintDimension
import com.viewcompose.ui.node.spec.ConstraintGridOrientation
import com.viewcompose.ui.node.spec.ConstraintGridSkipSpec
import com.viewcompose.ui.node.spec.ConstraintGridSpanSpec
import com.viewcompose.ui.node.spec.ConstraintGridSpec
import com.viewcompose.ui.node.spec.ConstraintGuidelineDirection
import com.viewcompose.ui.node.spec.ConstraintGuidelinePosition
import com.viewcompose.ui.node.spec.ConstraintGuidelineSpec
import com.viewcompose.ui.node.spec.ConstraintHelpersSpec
import com.viewcompose.ui.node.spec.ConstraintItemSpec
import com.viewcompose.ui.node.spec.ConstraintSetSpec
import com.viewcompose.ui.node.spec.ConstraintWrapBehavior
import com.viewcompose.ui.unit.UiDensity
import com.viewcompose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], manifest = Config.NONE)
class ConstraintLayoutPhase2RendererTest {
    @Test
    fun `CL-P2-CHAIN-001 native chain keeps custom endpoints margins and physical plane`() {
        val layout = layout()
        val boundary = layout.addContent("boundary")
        val first = layout.addContent("first")
        val second = layout.addContent("second")
        layout.decoupledConstraintSetSpec = ConstraintSetSpec(
            constraints = mapOf(
                "boundary" to fixedItem(20, 20, left = parentLink(ConstraintAnchor.Left, 20)),
                "first" to fixedItem(20, 20),
                "second" to fixedItem(20, 20),
            ),
            helpers = ConstraintHelpersSpec(
                chains = listOf(
                    ConstraintChainSpec(
                        orientation = ConstraintChainOrientation.Horizontal,
                        referencedIds = listOf("first", "second"),
                        style = ConstraintChainStyle.Packed,
                        bias = 0f,
                        startTarget = ConstraintAnchorTarget.ref("boundary", ConstraintAnchor.Right),
                        endTarget = ConstraintAnchorTarget.parent(ConstraintAnchor.Right),
                        startMargin = 10.dp,
                        endMargin = 20.dp,
                    ),
                ),
            ),
        )

        layout.applyConstraintsNow()
        layout.measureExact(300, 100)

        assertEquals(listOf(20, 0, 40, 20), boundary.bounds())
        assertEquals(listOf(50, 0, 70, 20), first.bounds())
        assertEquals(listOf(70, 0, 90, 20), second.bounds())
        val firstParams = first.layoutParams as ConstraintLayout.LayoutParams
        val secondParams = second.layoutParams as ConstraintLayout.LayoutParams
        assertEquals(boundary.id, firstParams.leftToRight)
        assertEquals(10, firstParams.leftMargin)
        assertEquals(ConstraintLayout.LayoutParams.PARENT_ID, secondParams.rightToRight)
        assertEquals(20, secondParams.rightMargin)

        val helperLayout = layout()
        helperLayout.addContent("boundary")
        val helperFirst = helperLayout.addContent("first")
        val helperSecond = helperLayout.addContent("second")
        helperLayout.decoupledConstraintSetSpec = ConstraintSetSpec(
            constraints = mapOf(
                "boundary" to fixedItem(20, 20, left = parentLink(ConstraintAnchor.Left, 20)),
                "first" to fixedItem(20, 20),
                "second" to fixedItem(20, 20),
            ),
            helpers = ConstraintHelpersSpec(
                guidelines = listOf(
                    ConstraintGuidelineSpec(
                        "right-guide",
                        ConstraintGuidelineDirection.FromRight,
                        ConstraintGuidelinePosition.Offset(30.dp),
                    ),
                ),
                barriers = listOf(
                    ConstraintBarrierSpec(
                        id = "right-barrier",
                        direction = ConstraintBarrierDirection.Right,
                        referencedIds = listOf("boundary"),
                        margin = 5.dp,
                    ),
                ),
                chains = listOf(
                    ConstraintChainSpec(
                        orientation = ConstraintChainOrientation.Horizontal,
                        referencedIds = listOf("first", "second"),
                        style = ConstraintChainStyle.Packed,
                        bias = 0f,
                        startTarget = ConstraintAnchorTarget.ref("right-barrier", ConstraintAnchor.Right),
                        endTarget = ConstraintAnchorTarget.ref("right-guide", ConstraintAnchor.Left),
                        startMargin = 5.dp,
                        endMargin = 10.dp,
                    ),
                ),
            ),
        )
        helperLayout.applyConstraintsNow()
        helperLayout.measureExact(300, 100)
        assertEquals(listOf(50, 0, 70, 20), helperFirst.bounds())
        assertEquals(listOf(70, 0, 90, 20), helperSecond.bounds())
        val barrierView = (0 until helperLayout.childCount)
            .map(helperLayout::getChildAt)
            .filterIsInstance<androidx.constraintlayout.widget.Barrier>()
            .single()
        val guidelineView = (0 until helperLayout.childCount)
            .map(helperLayout::getChildAt)
            .filterIsInstance<androidx.constraintlayout.widget.Guideline>()
            .single()
        assertEquals(barrierView.id, (helperFirst.layoutParams as ConstraintLayout.LayoutParams).leftToRight)
        assertEquals(guidelineView.id, (helperSecond.layoutParams as ConstraintLayout.LayoutParams).rightToLeft)

        val logicalLtr = logicalGuidelineChain(UiLayoutDirection.Ltr)
        val logicalRtl = logicalGuidelineChain(UiLayoutDirection.Rtl)
        assertEquals(listOf(30, 0, 50, 20), logicalLtr.first.bounds())
        assertEquals(listOf(250, 0, 270, 20), logicalLtr.second.bounds())
        assertEquals(listOf(250, 0, 270, 20), logicalRtl.first.bounds())
        assertEquals(listOf(30, 0, 50, 20), logicalRtl.second.bounds())
    }

    @Test
    fun `CL-P2-BASELINE-002 baseline pixels and invalid retry preserve accepted geometry`() {
        val layout = layout()
        val peer = layout.addText("peer", "Peer")
        val label = layout.addText("label", "Label")
        layout.decoupledConstraintSetSpec = ConstraintSetSpec(
            constraints = mapOf(
                "peer" to ConstraintItemSpec(
                    width = ConstraintDimension.WrapContent,
                    height = ConstraintDimension.WrapContent,
                    left = parentLink(ConstraintAnchor.Left, 10),
                    top = parentLink(ConstraintAnchor.Top, 20),
                ),
                "label" to ConstraintItemSpec(
                    width = ConstraintDimension.WrapContent,
                    height = ConstraintDimension.WrapContent,
                    left = parentLink(ConstraintAnchor.Left, 100),
                    baseline = ConstraintAnchorLink(
                        ConstraintAnchorTarget.ref("peer", ConstraintAnchor.Baseline),
                        margin = 9.dp,
                        goneMargin = 17.dp,
                    ),
                ),
            ),
        )
        layout.applyConstraintsNow()
        layout.measureExact(300, 120)

        val params = label.layoutParams as ConstraintLayout.LayoutParams
        assertEquals(9, params.baselineMargin)
        assertEquals(17, params.goneBaselineMargin)
        assertEquals(9, label.top + label.baseline - (peer.top + peer.baseline))
        val (direct, directPeer, directLabel) = directBaselineLayout()
        direct.measureExact(300, 120)
        assertEquals(
            directLabel.top + directLabel.baseline - (directPeer.top + directPeer.baseline),
            label.top + label.baseline - (peer.top + peer.baseline),
        )
        peer.visibility = View.GONE
        directPeer.visibility = View.GONE
        layout.measureExact(300, 120)
        direct.measureExact(300, 120)
        assertEquals(
            directLabel.top + directLabel.baseline - directPeer.top,
            label.top + label.baseline - peer.top,
        )
        peer.visibility = View.VISIBLE
        layout.measureExact(300, 120)
        val acceptedBounds = label.bounds()
        val acceptedGraph = layout.acceptedGraphForTest

        layout.decoupledConstraintSetSpec = ConstraintSetSpec(
            constraints = mapOf(
                "label" to ConstraintItemSpec(
                    baseline = ConstraintAnchorLink(
                        ConstraintAnchorTarget.ref("missing", ConstraintAnchor.Baseline),
                    ),
                ),
            ),
        )
        layout.applyConstraintsNow()
        layout.measureExact(300, 120)

        assertSame(acceptedGraph, layout.acceptedGraphForTest)
        assertEquals(acceptedBounds, label.bounds())
    }

    @Test
    fun `CL-P2-WRAP-003 wrap behaviors contribute only to selected parent axes`() {
        val expected = mapOf(
            ConstraintWrapBehavior.Included to (130 to 180),
            ConstraintWrapBehavior.HorizontalOnly to (130 to 20),
            ConstraintWrapBehavior.VerticalOnly to (20 to 180),
            ConstraintWrapBehavior.Skipped to (20 to 20),
        )
        expected.forEach { (behavior, size) ->
            val layout = layout()
            layout.addContent("anchor")
            layout.addContent("outlier")
            layout.decoupledConstraintSetSpec = ConstraintSetSpec(
                constraints = mapOf(
                    "anchor" to fixedItem(
                        20,
                        20,
                        left = parentLink(ConstraintAnchor.Left),
                        top = parentLink(ConstraintAnchor.Top),
                    ),
                    "outlier" to fixedItem(
                        30,
                        30,
                        left = parentLink(ConstraintAnchor.Left, 100),
                        top = parentLink(ConstraintAnchor.Top, 150),
                        wrapBehavior = behavior,
                    ),
                ),
            )
            layout.applyConstraintsNow()
            layout.measureAtMost(500, 500)
            assertEquals("width for $behavior", size.first, layout.measuredWidth)
            assertEquals("height for $behavior", size.second, layout.measuredHeight)
        }
    }

    @Test
    fun `CL-P2-PHYSICAL-004 physical links and guidelines stay fixed while logical start mirrors`() {
        val context = RuntimeEnvironment.getApplication()
        context.applicationInfo.flags = context.applicationInfo.flags or ApplicationInfo.FLAG_SUPPORTS_RTL
        val layout = layout(UiLayoutDirection.Rtl).apply { layoutDirection = View.LAYOUT_DIRECTION_RTL }
        val physical = layout.addContent("physical")
        val logical = layout.addContent("logical")
        layout.decoupledConstraintSetSpec = ConstraintSetSpec(
            constraints = mapOf(
                "physical" to fixedItem(
                    20,
                    20,
                    left = ConstraintAnchorLink(
                        ConstraintAnchorTarget.ref("physical-left", ConstraintAnchor.Left),
                        goneMargin = 11.dp,
                    ),
                    top = parentLink(ConstraintAnchor.Top),
                ),
                "logical" to ConstraintItemSpec(
                    width = ConstraintDimension.Fixed(20.dp),
                    height = ConstraintDimension.Fixed(20.dp),
                    start = ConstraintAnchorLink(ConstraintAnchorTarget.ref("logical-start", ConstraintAnchor.Start)),
                    top = parentLink(ConstraintAnchor.Top, 30),
                ),
            ),
            helpers = ConstraintHelpersSpec(
                guidelines = listOf(
                    ConstraintGuidelineSpec(
                        "physical-left",
                        ConstraintGuidelineDirection.FromLeft,
                        ConstraintGuidelinePosition.Offset(30.dp),
                    ),
                    ConstraintGuidelineSpec(
                        "logical-start",
                        ConstraintGuidelineDirection.FromStart,
                        ConstraintGuidelinePosition.Offset(30.dp),
                    ),
                ),
            ),
        )
        layout.applyConstraintsNow()
        layout.measureExact(300, 100)

        assertEquals(listOf(41, 0, 61, 20), physical.bounds())
        assertEquals(listOf(250, 30, 270, 50), logical.bounds())
        assertEquals(11, (physical.layoutParams as ConstraintLayout.LayoutParams).goneLeftMargin)
        val guides = (0 until layout.childCount)
            .map(layout::getChildAt)
            .filterIsInstance<androidx.constraintlayout.widget.Guideline>()
            .associateBy { it.left }
        assertTrue(30 in guides)
        assertTrue(270 in guides)

        layout.setTag(
            R.id.viewcompose_environment_values,
            UiEnvironmentValues.Default.copy(
                density = UiDensity(1f, 1f),
                layoutDirection = UiLayoutDirection.Ltr,
            ),
        )
        layout.layoutDirection = View.LAYOUT_DIRECTION_LTR
        layout.requestConstraintRebuild()
        layout.applyConstraintsNow()
        layout.measureExact(300, 100)
        assertEquals(listOf(41, 0, 61, 20), physical.bounds())
        assertEquals(listOf(30, 30, 50, 50), logical.bounds())
    }

    @Test
    fun `CL-P2-GRID-005 exact weighted geometry and replacement keep registry bounded`() {
        val layout = layout()
        val children = listOf("one", "two", "three", "four").associateWith { id ->
            layout.addContent(id)
        }
        val grid = ConstraintGridSpec(
            id = "dashboard",
            referencedIds = children.keys.toList(),
            rows = 2,
            columns = 3,
            orientation = ConstraintGridOrientation.Horizontal,
            rowWeights = listOf(1f, 2f),
            columnWeights = listOf(1f, 2f, 1f),
            horizontalGap = 6.dp,
            verticalGap = 8.dp,
            spans = listOf(ConstraintGridSpanSpec("one", 0, columnSpan = 2)),
            skips = listOf(ConstraintGridSkipSpec(2)),
        )
        val itemSpecs = children.keys.associateWith {
            ConstraintItemSpec(
                width = ConstraintDimension.MatchConstraints(),
                height = ConstraintDimension.MatchConstraints(),
            )
        }
        layout.decoupledConstraintSetSpec = ConstraintSetSpec(
            constraints = itemSpecs,
            helpers = ConstraintHelpersSpec(grids = listOf(grid)),
        )
        layout.applyConstraintsNow()
        layout.measureExact(300, 200)

        assertEquals(listOf(0, 0, 222, 64), children.getValue("one").bounds())
        assertEquals(listOf(0, 72, 72, 200), children.getValue("two").bounds())
        assertEquals(listOf(78, 72, 222, 200), children.getValue("three").bounds())
        assertEquals(listOf(228, 72, 300, 200), children.getValue("four").bounds())
        assertEquals(5, layout.managedHelperCountForTest)
        val acceptedGraph = layout.acceptedGraphForTest
        val acceptedBounds = children.mapValues { (_, child) -> child.bounds() }

        layout.decoupledConstraintSetSpec = ConstraintSetSpec(
            constraints = itemSpecs,
            helpers = ConstraintHelpersSpec(
                grids = listOf(grid.copy(
                    spans = listOf(ConstraintGridSpanSpec("one", 0, columnSpan = 2)),
                    skips = listOf(ConstraintGridSkipSpec(1)),
                )),
            ),
        )
        layout.applyConstraintsNow()
        layout.measureExact(300, 200)
        assertSame(acceptedGraph, layout.acceptedGraphForTest)
        assertEquals(acceptedBounds, children.mapValues { (_, child) -> child.bounds() })
        assertEquals(5, layout.managedHelperCountForTest)

        repeat(1_000) { index ->
            layout.decoupledConstraintSetSpec = ConstraintSetSpec(
                constraints = itemSpecs,
                helpers = if (index % 2 == 0) ConstraintHelpersSpec(grids = listOf(grid)) else ConstraintHelpersSpec(),
            )
            layout.applyConstraintsNow()
            assertTrue(layout.managedHelperCountForTest in 0..5)
        }
        layout.decoupledConstraintSetSpec = ConstraintSetSpec(constraints = itemSpecs)
        layout.applyConstraintsNow()
        assertEquals(0, layout.managedHelperCountForTest)
    }

    @Test
    fun `CL-P2-CIRCULAR-006 exact circle geometry removal and stress retain no helper identity`() {
        val layout = layout()
        val center = layout.addContent("center")
        val orbit = layout.addContent("orbit")
        val constraints = mapOf(
            "center" to ConstraintItemSpec(
                width = ConstraintDimension.Fixed(20.dp),
                height = ConstraintDimension.Fixed(20.dp),
                left = parentLink(ConstraintAnchor.Left, 140),
                top = parentLink(ConstraintAnchor.Top, 90),
            ),
            "orbit" to ConstraintItemSpec(
                width = ConstraintDimension.Fixed(10.dp),
                height = ConstraintDimension.Fixed(10.dp),
            ),
        )
        val circular = ConstraintCircularFlowSpec(
            id = "orbit-flow",
            centerId = "center",
            items = listOf(ConstraintCircularFlowItemSpec("orbit", 40.dp, 0f)),
        )
        layout.decoupledConstraintSetSpec = ConstraintSetSpec(
            constraints = constraints,
            helpers = ConstraintHelpersSpec(circularFlows = listOf(circular)),
        )
        layout.applyConstraintsNow()
        layout.measureExact(300, 200)

        assertEquals(listOf(140, 90, 160, 110), center.bounds())
        // AndroidX circle coordinates place 0 degrees above the center and advance clockwise.
        assertEquals(listOf(145, 55, 155, 65), orbit.bounds())
        assertEquals(0, layout.managedHelperCountForTest)
        val acceptedGraph = layout.acceptedGraphForTest
        val acceptedOrbitBounds = orbit.bounds()

        layout.decoupledConstraintSetSpec = ConstraintSetSpec(
            constraints = constraints + (
                "orbit" to constraints.getValue("orbit").copy(
                    left = parentLink(ConstraintAnchor.Left),
                )
            ),
            helpers = ConstraintHelpersSpec(circularFlows = listOf(circular)),
        )
        layout.applyConstraintsNow()
        layout.measureExact(300, 200)
        assertSame(acceptedGraph, layout.acceptedGraphForTest)
        assertEquals(acceptedOrbitBounds, orbit.bounds())
        assertEquals(0, layout.managedHelperCountForTest)

        repeat(1_000) { index ->
            layout.decoupledConstraintSetSpec = ConstraintSetSpec(
                constraints = constraints,
                helpers = if (index % 2 == 0) {
                    ConstraintHelpersSpec(circularFlows = listOf(circular.copy(
                        items = listOf(circular.items.single().copy(angle = (index % 360).toFloat())),
                    )))
                } else {
                    ConstraintHelpersSpec()
                },
            )
            layout.applyConstraintsNow()
            assertEquals(0, layout.managedHelperCountForTest)
        }
        assertEquals(
            ConstraintLayout.LayoutParams.UNSET,
            (orbit.layoutParams as ConstraintLayout.LayoutParams).circleConstraint,
        )
    }

    private fun layout(direction: UiLayoutDirection = UiLayoutDirection.Ltr): DeclarativeConstraintLayout {
        return DeclarativeConstraintLayout(RuntimeEnvironment.getApplication()).apply {
            setTag(
                R.id.viewcompose_environment_values,
                UiEnvironmentValues.Default.copy(
                    density = UiDensity(1f, 1f),
                    layoutDirection = direction,
                ),
            )
        }
    }

    private fun logicalGuidelineChain(
        direction: UiLayoutDirection,
    ): Pair<View, View> {
        val context = RuntimeEnvironment.getApplication()
        context.applicationInfo.flags = context.applicationInfo.flags or ApplicationInfo.FLAG_SUPPORTS_RTL
        val layout = layout(direction).apply {
            layoutDirection = if (direction == UiLayoutDirection.Rtl) {
                View.LAYOUT_DIRECTION_RTL
            } else {
                View.LAYOUT_DIRECTION_LTR
            }
        }
        val first = layout.addContent("first")
        val second = layout.addContent("second")
        layout.decoupledConstraintSetSpec = ConstraintSetSpec(
            constraints = mapOf(
                "first" to fixedItem(20, 20),
                "second" to fixedItem(20, 20),
            ),
            helpers = ConstraintHelpersSpec(
                guidelines = listOf(
                    ConstraintGuidelineSpec(
                        "start-guide",
                        ConstraintGuidelineDirection.FromStart,
                        ConstraintGuidelinePosition.Offset(30.dp),
                    ),
                    ConstraintGuidelineSpec(
                        "end-guide",
                        ConstraintGuidelineDirection.FromEnd,
                        ConstraintGuidelinePosition.Offset(30.dp),
                    ),
                ),
                chains = listOf(
                    ConstraintChainSpec(
                        orientation = ConstraintChainOrientation.Horizontal,
                        referencedIds = listOf("first", "second"),
                        style = ConstraintChainStyle.SpreadInside,
                        startTarget = ConstraintAnchorTarget.ref("start-guide", ConstraintAnchor.Start),
                        endTarget = ConstraintAnchorTarget.ref("end-guide", ConstraintAnchor.End),
                    ),
                ),
            ),
        )
        layout.applyConstraintsNow()
        layout.measureExact(300, 100)
        return first to second
    }

    private fun DeclarativeConstraintLayout.addContent(id: String): View = View(context).also { child ->
        child.setTag(R.id.viewcompose_constraint_layout_id, id)
        addView(child)
    }

    private fun DeclarativeConstraintLayout.addText(id: String, text: String): TextView =
        TextView(context).also { child ->
            child.text = text
            child.textSize = 16f
            child.includeFontPadding = false
            child.setTag(R.id.viewcompose_constraint_layout_id, id)
            addView(child)
        }

    private fun directBaselineLayout(): Triple<ConstraintLayout, TextView, TextView> {
        val context = RuntimeEnvironment.getApplication()
        val layout = ConstraintLayout(context)
        val peer = TextView(context).apply {
            id = View.generateViewId()
            text = "Peer"
            textSize = 16f
            includeFontPadding = false
            layoutParams = ConstraintLayout.LayoutParams(
                ConstraintLayout.LayoutParams.WRAP_CONTENT,
                ConstraintLayout.LayoutParams.WRAP_CONTENT,
            ).apply {
                leftToLeft = ConstraintLayout.LayoutParams.PARENT_ID
                topToTop = ConstraintLayout.LayoutParams.PARENT_ID
                leftMargin = 10
                topMargin = 20
            }
        }
        val label = TextView(context).apply {
            id = View.generateViewId()
            text = "Label"
            textSize = 16f
            includeFontPadding = false
            layoutParams = ConstraintLayout.LayoutParams(
                ConstraintLayout.LayoutParams.WRAP_CONTENT,
                ConstraintLayout.LayoutParams.WRAP_CONTENT,
            ).apply {
                leftToLeft = ConstraintLayout.LayoutParams.PARENT_ID
                leftMargin = 100
                baselineToBaseline = peer.id
                baselineMargin = 9
                goneBaselineMargin = 17
            }
        }
        layout.addView(peer)
        layout.addView(label)
        return Triple(layout, peer, label)
    }

    private fun fixedItem(
        width: Int,
        height: Int,
        left: ConstraintAnchorLink? = null,
        top: ConstraintAnchorLink? = parentLink(ConstraintAnchor.Top),
        wrapBehavior: ConstraintWrapBehavior = ConstraintWrapBehavior.Included,
    ): ConstraintItemSpec = ConstraintItemSpec(
        width = ConstraintDimension.Fixed(width.dp),
        height = ConstraintDimension.Fixed(height.dp),
        left = left,
        top = top,
        wrapBehaviorInParent = wrapBehavior,
    )

    private fun parentLink(anchor: ConstraintAnchor, margin: Int = 0): ConstraintAnchorLink =
        ConstraintAnchorLink(ConstraintAnchorTarget.parent(anchor), margin.dp)

    private fun ConstraintLayout.measureExact(width: Int, height: Int) {
        measure(
            View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY),
        )
        layout(0, 0, width, height)
    }

    private fun DeclarativeConstraintLayout.measureAtMost(width: Int, height: Int) {
        measure(
            View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.AT_MOST),
            View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.AT_MOST),
        )
        layout(0, 0, measuredWidth, measuredHeight)
    }

    private fun View.bounds(): List<Int> = listOf(left, top, right, bottom)
}
