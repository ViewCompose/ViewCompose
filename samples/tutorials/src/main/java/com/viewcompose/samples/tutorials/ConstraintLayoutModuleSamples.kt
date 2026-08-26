package com.viewcompose.samples.tutorials

import com.viewcompose.constraintlayout.ConstraintCircularFlowItem
import com.viewcompose.constraintlayout.ConstraintConstrainScope
import com.viewcompose.constraintlayout.ConstraintGridSkip
import com.viewcompose.constraintlayout.ConstraintGridSpan
import com.viewcompose.constraintlayout.ConstraintLayout
import com.viewcompose.constraintlayout.constrainAs
import com.viewcompose.constraintlayout.constraintSet
import com.viewcompose.constraintlayout.createCircularFlow
import com.viewcompose.constraintlayout.createGrid
import com.viewcompose.constraintlayout.createGuidelineFromStart
import com.viewcompose.constraintlayout.createRefs
import com.viewcompose.constraintlayout.parent
import com.viewcompose.ui.foundation.Text
import com.viewcompose.ui.foundation.UiTreeBuilder
import com.viewcompose.ui.modifier.Modifier
import com.viewcompose.ui.node.spec.ConstraintDimension
import com.viewcompose.ui.node.spec.ConstraintGridOrientation
import com.viewcompose.ui.node.spec.ConstraintMatchMode
import com.viewcompose.ui.node.spec.ConstraintRatio
import com.viewcompose.ui.node.spec.ConstraintRatioSide
import com.viewcompose.ui.node.spec.ConstraintSetSpec
import com.viewcompose.ui.unit.dp

private fun UiTreeBuilder.inlineConstraintLayout() {
    // DOCS_REGION_START(constraintlayout-inline)
ConstraintLayout {
    val (title, body) = createRefs("title", "body")
    Text(
        "Title",
        modifier = Modifier.constrainAs(title) {
            startToStart(parent)
            topToTop(parent)
        },
    )
    Text(
        "Body",
        modifier = Modifier.constrainAs(body) {
            startToStart(title)
            topToBottom(title, margin = 8.dp)
        },
    )
}
    // DOCS_REGION_END(constraintlayout-inline)
}

private fun ConstraintConstrainScope.dimensionConstraints() {
    // DOCS_REGION_START(constraintlayout-dimensions)
width = ConstraintDimension.MatchConstraints(
    mode = ConstraintMatchMode.Percent(0.6f),
    min = 120.dp,
    max = 360.dp,
)
height = ConstraintDimension.Fixed(180.dp)
ratio = ConstraintRatio(width = 16f, height = 9f, constrainedSide = ConstraintRatioSide.Width)
    // DOCS_REGION_END(constraintlayout-dimensions)
}

private fun reusableConstraintSet(): ConstraintSetSpec {
    // DOCS_REGION_START(constraintlayout-set)
val set = constraintSet {
    val (title, body) = createRefs("title", "body")
    constrain(title) {
        startToStart(parent)
        topToTop(parent)
    }
    constrain(body) {
        startToStart(title)
        topToBottom(title, margin = 8.dp)
    }
}
    // DOCS_REGION_END(constraintlayout-set)
    return set
}

private fun UiTreeBuilder.typedConstraintHelpers() {
    // DOCS_REGION_START(constraintlayout-helpers)
ConstraintLayout {
    val (hero, metric, status, center, orbit) = createRefs(
        "hero", "metric", "status", "center", "orbit",
    )
    val start = createGuidelineFromStart(0.1f)
    createGrid(
        hero,
        metric,
        status,
        rows = 2,
        columns = 2,
        orientation = ConstraintGridOrientation.Horizontal,
        spans = listOf(ConstraintGridSpan(hero, index = 0, columnSpan = 2)),
        skips = listOf(ConstraintGridSkip(index = 2)),
    )
    createCircularFlow(
        center,
        ConstraintCircularFlowItem(orbit, radius = 48.dp, angle = 90f),
    )
    Text("Hero", modifier = Modifier.constrainAs(hero) { startToStart(start) })
    Text("Metric", modifier = Modifier.constrainAs(metric) {})
    Text("Status", modifier = Modifier.constrainAs(status) {})
    Text("Center", modifier = Modifier.constrainAs(center) {})
    Text("Orbit", modifier = Modifier.constrainAs(orbit) {})
}
    // DOCS_REGION_END(constraintlayout-helpers)
}
