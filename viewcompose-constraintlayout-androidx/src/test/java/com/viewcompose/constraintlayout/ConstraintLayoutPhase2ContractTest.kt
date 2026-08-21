package com.viewcompose.constraintlayout

import com.viewcompose.ui.foundation.Text
import com.viewcompose.ui.foundation.buildVNodeTree
import com.viewcompose.ui.modifier.ConstraintModifierElement
import com.viewcompose.ui.modifier.Modifier
import com.viewcompose.ui.node.spec.ConstraintAnchor
import com.viewcompose.ui.node.spec.ConstraintChainStyle
import com.viewcompose.ui.node.spec.ConstraintGridOrientation
import com.viewcompose.ui.node.spec.ConstraintLayoutNodeProps
import com.viewcompose.ui.node.spec.ConstraintWrapBehavior
import com.viewcompose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ConstraintLayoutPhase2ContractTest {
    @Test
    fun `CL-P2-CHAIN-001 chain endpoints preserve typed targets sides and margins`() {
        val tree = buildVNodeTree {
            ConstraintLayout {
                val (boundary, first, second) = createRefs("boundary", "first", "second")
                val trailing = createEndBarrier(boundary, id = "trailing")
                createHorizontalChain(
                    first,
                    second,
                    style = ConstraintChainStyle.Packed,
                    startTarget = boundary,
                    startTargetSide = ConstraintHorizontalAnchorSide.End,
                    startMargin = 7.dp,
                    endTarget = trailing,
                    endTargetSide = ConstraintHorizontalAnchorSide.Start,
                    endMargin = 11.dp,
                )
                createVerticalChain(
                    first,
                    second,
                    topTarget = boundary,
                    topTargetSide = ConstraintVerticalAnchorSide.Bottom,
                    topMargin = 5.dp,
                    bottomTarget = parent,
                    bottomTargetSide = ConstraintVerticalAnchorSide.Bottom,
                    bottomMargin = 13.dp,
                )
                Text("Boundary", modifier = Modifier.constrainAs(boundary) { topToTop(parent) })
                Text("First", modifier = Modifier.constrainAs(first) {})
                Text("Second", modifier = Modifier.constrainAs(second) {})
            }
        }

        val chains = (tree.single().spec as ConstraintLayoutNodeProps).helpers.chains
        assertEquals(ConstraintAnchor.End, chains[0].startTarget?.anchor)
        assertEquals(7.dp, chains[0].startMargin)
        assertEquals(ConstraintAnchor.Start, chains[0].endTarget?.anchor)
        assertEquals(11.dp, chains[0].endMargin)
        assertEquals(ConstraintAnchor.Bottom, chains[1].startTarget?.anchor)
        assertEquals(5.dp, chains[1].startMargin)
        assertEquals(ConstraintAnchor.Bottom, chains[1].endTarget?.anchor)
        assertEquals(13.dp, chains[1].endMargin)
    }

    @Test
    fun `CL-P2-BASELINE-002 baseline normal and gone margins remain exact transport values`() {
        val peer = ConstraintReference("peer")
        val spec = Modifier.constrain("label") {
            baselineToBaseline(peer, margin = 9.dp, goneMargin = 17.dp)
        }.elements.filterIsInstance<ConstraintModifierElement>().single().constraint

        assertEquals(9.dp, spec.baseline?.margin)
        assertEquals(17.dp, spec.baseline?.goneMargin)
    }

    @Test
    fun `CL-P2-WRAP-003 every parent wrap contribution is represented explicitly`() {
        ConstraintWrapBehavior.entries.forEach { behavior ->
            val spec = Modifier.constrain("item-${behavior.name}") {
                wrapBehaviorInParent = behavior
            }.elements.filterIsInstance<ConstraintModifierElement>().single().constraint
            assertEquals(behavior, spec.wrapBehaviorInParent)
        }
        assertEquals(
            listOf(
                ConstraintWrapBehavior.Included,
                ConstraintWrapBehavior.HorizontalOnly,
                ConstraintWrapBehavior.VerticalOnly,
                ConstraintWrapBehavior.Skipped,
            ),
            ConstraintWrapBehavior.entries,
        )
    }

    @Test
    fun `CL-P2-PHYSICAL-004 physical anchors barriers and guidelines remain distinct from logical forms`() {
        val tree = buildVNodeTree {
            ConstraintLayout {
                val (source, target) = createRefs("source", "target")
                val leftGuide = createGuidelineFromLeft(0.25f, id = "left-guide")
                val rightGuide = createGuidelineFromRight(12.dp, id = "right-guide")
                val leftBarrier = createLeftBarrier(source, id = "left-barrier")
                val rightBarrier = createRightBarrier(target, id = "right-barrier")
                Text(
                    "Source",
                    modifier = Modifier.constrainAs(source) {
                        leftToRight(leftGuide, margin = 3.dp)
                        rightToLeft(rightGuide, margin = 4.dp)
                    },
                )
                Text(
                    "Target",
                    modifier = Modifier.constrainAs(target) {
                        leftToLeft(leftBarrier)
                        rightToRight(rightBarrier)
                    },
                )
            }
        }

        val props = tree.single().spec as ConstraintLayoutNodeProps
        assertEquals(
            listOf(ConstraintAnchor.Right, ConstraintAnchor.Left),
            tree.single().children.map { child ->
                child.modifier.elements.filterIsInstance<ConstraintModifierElement>()
                    .single().constraint.left?.target?.anchor
            },
        )
        assertEquals(
            listOf(ConstraintAnchor.Left, ConstraintAnchor.Right),
            props.helpers.barriers.map { barrier ->
                when (barrier.direction) {
                    com.viewcompose.ui.node.spec.ConstraintBarrierDirection.Left -> ConstraintAnchor.Left
                    com.viewcompose.ui.node.spec.ConstraintBarrierDirection.Right -> ConstraintAnchor.Right
                    else -> error("Unexpected logical barrier")
                }
            },
        )
        assertTrue(props.helpers.guidelines.all { guideline ->
            guideline.direction.name == "FromLeft" || guideline.direction.name == "FromRight"
        })

        assertThrows(IllegalArgumentException::class.java) {
            Modifier.constrain("mixed") {
                startToStart(parent)
                leftToLeft(parent)
            }
        }
    }

    @Test
    fun `CL-P2-GRID-005 typed grid snapshots axes weights spans skips and gaps`() {
        val tree = buildVNodeTree {
            ConstraintLayout {
                val (one, two, three, four) = createRefs("one", "two", "three", "four")
                createGrid(
                    one,
                    two,
                    three,
                    four,
                    id = "dashboard",
                    rows = 2,
                    columns = 3,
                    orientation = ConstraintGridOrientation.Horizontal,
                    rowWeights = listOf(1f, 2f),
                    columnWeights = listOf(1f, 2f, 1f),
                    horizontalGap = 6.dp,
                    verticalGap = 8.dp,
                    spans = listOf(ConstraintGridSpan(one, index = 0, columnSpan = 2)),
                    skips = listOf(ConstraintGridSkip(index = 2)),
                )
                listOf(one, two, three, four).forEach { ref ->
                    Text(ref.id, modifier = Modifier.constrainAs(ref) {})
                }
            }
        }

        val grid = (tree.single().spec as ConstraintLayoutNodeProps).helpers.grids.single()
        assertEquals("dashboard", grid.id)
        assertEquals(listOf(1f, 2f), grid.rowWeights)
        assertEquals(listOf(1f, 2f, 1f), grid.columnWeights)
        assertEquals(2, grid.spans.single().columnSpan)
        assertEquals(2, grid.skips.single().index)

        assertThrows(IllegalArgumentException::class.java) {
            constraintSet {
                val item = createRef("item")
                createGrid(item, rows = 51, columns = 1)
            }
        }
    }

    @Test
    fun `CL-P2-CIRCULAR-006 declarative circular flow owns explicit member geometry`() {
        val tree = buildVNodeTree {
            ConstraintLayout {
                val (center, first, second) = createRefs("center", "first", "second")
                createCircularFlow(
                    center,
                    ConstraintCircularFlowItem(first, radius = 40.dp, angle = 45f),
                    ConstraintCircularFlowItem(second, radius = 72.dp, angle = 180f),
                    id = "orbit",
                )
                Text("Center", modifier = Modifier.constrainAs(center) { centerHorizontallyTo() })
                Text("First", modifier = Modifier.constrainAs(first) {})
                Text("Second", modifier = Modifier.constrainAs(second) {})
            }
        }

        val flow = (tree.single().spec as ConstraintLayoutNodeProps).helpers.circularFlows.single()
        assertEquals("center", flow.centerId)
        assertEquals(listOf("first", "second"), flow.items.map { item -> item.referenceId })
        assertEquals(listOf(40.dp, 72.dp), flow.items.map { item -> item.radius })
        assertEquals(listOf(45f, 180f), flow.items.map { item -> item.angle })

        assertThrows(IllegalArgumentException::class.java) {
            constraintSet {
                val center = createRef("center")
                createCircularFlow(
                    center,
                    ConstraintCircularFlowItem(center, radius = 10.dp, angle = 0f),
                )
            }
        }
    }
}
