package com.viewcompose.constraintlayout.samples

import com.viewcompose.ui.modifier.Modifier
import com.viewcompose.ui.modifier.layoutId
import com.viewcompose.ui.node.spec.ConstraintChainStyle
import com.viewcompose.ui.node.spec.ConstraintDimension
import com.viewcompose.ui.node.spec.ConstraintFlowWrapMode
import com.viewcompose.ui.node.spec.ConstraintGridOrientation
import com.viewcompose.ui.node.spec.ConstraintRatio
import com.viewcompose.ui.node.spec.ConstraintSetSpec
import com.viewcompose.ui.node.spec.ConstraintWrapBehavior
import com.viewcompose.ui.unit.dp
import com.viewcompose.constraintlayout.ConstraintLayout
import com.viewcompose.constraintlayout.ConstraintCircularFlowItem
import com.viewcompose.constraintlayout.ConstraintGridSkip
import com.viewcompose.constraintlayout.ConstraintGridSpan
import com.viewcompose.constraintlayout.ConstraintHorizontalAnchorSide
import com.viewcompose.constraintlayout.ConstraintVerticalAnchorSide
import com.viewcompose.constraintlayout.constrainAs
import com.viewcompose.constraintlayout.constraintSet
import com.viewcompose.constraintlayout.createFlow
import com.viewcompose.constraintlayout.createCircularFlow
import com.viewcompose.constraintlayout.createGrid
import com.viewcompose.constraintlayout.createGuidelineFromLeft
import com.viewcompose.constraintlayout.createGuidelineFromRight
import com.viewcompose.constraintlayout.createGuidelineFromStart
import com.viewcompose.constraintlayout.createGuidelineFromTop
import com.viewcompose.constraintlayout.createHorizontalChain
import com.viewcompose.constraintlayout.createLeftBarrier
import com.viewcompose.constraintlayout.createRefs
import com.viewcompose.constraintlayout.createRightBarrier
import com.viewcompose.constraintlayout.createVerticalChain
import com.viewcompose.constraintlayout.parent
import com.viewcompose.ui.foundation.Text
import com.viewcompose.ui.foundation.UiTreeBuilder

fun UiTreeBuilder.constraintLayoutSample() {
    ConstraintLayout {
        val (title, body) = createRefs("title", "body")
        val start = createGuidelineFromStart(0.1f)
        val top = createGuidelineFromTop(0.1f)
        Text(
            text = "Title",
            modifier = Modifier.constrainAs(title) {
                startToStart(start)
                topToTop(top)
            },
        )
        Text(
            text = "Body",
            modifier = Modifier.constrainAs(body) {
                startToStart(title)
                topToBottom(title, margin = 8.dp)
            },
        )
    }
}

fun UiTreeBuilder.constraintHelpersSample() {
    ConstraintLayout {
        val (first, second, third, fourth, fifth) = createRefs(
            "first",
            "second",
            "third",
            "fourth",
            "fifth",
        )
        createFlow(
            first,
            second,
            third,
            wrapMode = ConstraintFlowWrapMode.Chain,
            horizontalGap = 8.dp,
            maxElementsWrap = 2,
        )
        createHorizontalChain(fourth, fifth, style = ConstraintChainStyle.SpreadInside)
        Text("First", modifier = Modifier.layoutId(first.id))
        Text("Second", modifier = Modifier.layoutId(second.id))
        Text("Third", modifier = Modifier.layoutId(third.id))
        Text("Fourth", modifier = Modifier.constrainAs(fourth) { topToTop(parent) })
        Text("Fifth", modifier = Modifier.constrainAs(fifth) { topToTop(parent) })
    }
}

fun constraintSetSample(): ConstraintSetSpec {
    return constraintSet {
        val (title, body) = createRefs("title", "body")
        constrain(title) {
            startToStart(parent)
            topToTop(parent)
        }
        constrain(body) {
            startToStart(title)
            topToBottom(title, margin = 8.dp)
            width = ConstraintDimension.MatchConstraints()
            height = ConstraintDimension.Fixed(90.dp)
            ratio = ConstraintRatio(width = 16f, height = 9f)
        }
    }
}

fun UiTreeBuilder.constraintChainEndpointsAndWrapSample() {
    ConstraintLayout {
        val (boundary, first, second, third, fourth) = createRefs(
            "boundary",
            "first",
            "second",
            "third",
            "fourth",
        )
        val startGuide = createGuidelineFromStart(0.15f)
        val topGuide = createGuidelineFromTop(0.35f)
        createHorizontalChain(
            first,
            second,
            startTarget = startGuide,
            startTargetSide = ConstraintHorizontalAnchorSide.Start,
            startMargin = 8.dp,
            endTarget = parent,
            endTargetSide = ConstraintHorizontalAnchorSide.End,
            endMargin = 16.dp,
        )
        createVerticalChain(
            third,
            fourth,
            topTarget = topGuide,
            topTargetSide = ConstraintVerticalAnchorSide.Top,
            topMargin = 8.dp,
            bottomMargin = 16.dp,
        )
        Text(
            "Boundary",
            modifier = Modifier.constrainAs(boundary) {
                startToStart(parent)
                topToTop(parent)
                wrapBehaviorInParent = ConstraintWrapBehavior.HorizontalOnly
            },
        )
        Text("First", modifier = Modifier.constrainAs(first) { topToBottom(boundary) })
        Text("Second", modifier = Modifier.constrainAs(second) { topToBottom(boundary) })
        Text("Third", modifier = Modifier.constrainAs(third) { startToStart(parent) })
        Text("Fourth", modifier = Modifier.constrainAs(fourth) { startToStart(parent) })
    }
}

fun UiTreeBuilder.constraintPhysicalEdgesSample() {
    ConstraintLayout {
        val (leftLabel, rightLabel) = createRefs("left-label", "right-label")
        val physicalLeft = createGuidelineFromLeft(24.dp)
        val physicalRight = createGuidelineFromRight(24.dp)
        createLeftBarrier(leftLabel, rightLabel, margin = 4.dp)
        createRightBarrier(leftLabel, rightLabel, margin = 4.dp)
        Text(
            "Fixed left",
            modifier = Modifier.constrainAs(leftLabel) {
                leftToLeft(physicalLeft)
                topToTop(parent)
            },
        )
        Text(
            "Fixed right",
            modifier = Modifier.constrainAs(rightLabel) {
                rightToRight(physicalRight)
                topToBottom(leftLabel, margin = 8.dp)
            },
        )
    }
}

fun constraintSetPhaseTwoSample(): ConstraintSetSpec {
    return constraintSet {
        val edgeA = createRef("edge-a")
        val edgeB = createRef("edge-b")
        val chainA = createRef("chain-a")
        val chainB = createRef("chain-b")
        val gridA = createRef("grid-a")
        val gridB = createRef("grid-b")
        val center = createRef("center")
        val orbit = createRef("orbit")
        val physicalLeft = createGuidelineFromLeft(16.dp)
        val physicalRight = createGuidelineFromRight(0.9f)
        val leftBarrier = createLeftBarrier(edgeA, edgeB, margin = 4.dp)
        val rightBarrier = createRightBarrier(edgeA, edgeB, margin = 4.dp)
        constrain(edgeA) {
            leftToLeft(physicalLeft)
            topToTop(parent)
            wrapBehaviorInParent = ConstraintWrapBehavior.HorizontalOnly
        }
        constrain(edgeB) {
            rightToRight(physicalRight)
            topToBottom(edgeA, margin = 8.dp)
        }
        createHorizontalChain(
            chainA,
            chainB,
            startTarget = leftBarrier,
            startTargetSide = ConstraintHorizontalAnchorSide.Left,
            endTarget = rightBarrier,
            endTargetSide = ConstraintHorizontalAnchorSide.Right,
        )
        createGrid(gridA, gridB, rows = 1, columns = 2, horizontalGap = 8.dp)
        createCircularFlow(
            center,
            ConstraintCircularFlowItem(orbit, radius = 48.dp, angle = 90f),
        )
    }
}

fun UiTreeBuilder.constraintGridSample() {
    ConstraintLayout {
        val (hero, metric, status, action) = createRefs("hero", "metric", "status", "action")
        createGrid(
            hero,
            metric,
            status,
            action,
            rows = 2,
            columns = 3,
            orientation = ConstraintGridOrientation.Horizontal,
            columnWeights = listOf(1f, 2f, 1f),
            horizontalGap = 8.dp,
            verticalGap = 8.dp,
            spans = listOf(ConstraintGridSpan(hero, index = 0, columnSpan = 2)),
            skips = listOf(ConstraintGridSkip(index = 2)),
        )
        listOf(hero, metric, status, action).forEach { reference ->
            Text(
                reference.id,
                modifier = Modifier.constrainAs(reference) {
                    width = ConstraintDimension.MatchConstraints()
                    height = ConstraintDimension.MatchConstraints()
                },
            )
        }
    }
}

fun UiTreeBuilder.constraintCircularFlowSample() {
    ConstraintLayout {
        val (center, top, right) = createRefs("center", "top", "right")
        createCircularFlow(
            center,
            ConstraintCircularFlowItem(top, radius = 48.dp, angle = 0f),
            ConstraintCircularFlowItem(right, radius = 48.dp, angle = 90f),
        )
        Text(
            "Center",
            modifier = Modifier.constrainAs(center) {
                centerHorizontallyTo()
                centerVerticallyTo()
            },
        )
        Text("Top", modifier = Modifier.constrainAs(top) {})
        Text("Right", modifier = Modifier.constrainAs(right) {})
    }
}
