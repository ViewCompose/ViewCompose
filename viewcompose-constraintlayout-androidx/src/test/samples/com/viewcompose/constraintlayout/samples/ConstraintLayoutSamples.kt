package com.viewcompose.constraintlayout.samples

import com.viewcompose.ui.modifier.Modifier
import com.viewcompose.ui.modifier.layoutId
import com.viewcompose.ui.node.spec.ConstraintChainStyle
import com.viewcompose.ui.node.spec.ConstraintDimension
import com.viewcompose.ui.node.spec.ConstraintFlowWrapMode
import com.viewcompose.ui.node.spec.ConstraintRatio
import com.viewcompose.ui.node.spec.ConstraintSetSpec
import com.viewcompose.ui.unit.dp
import com.viewcompose.constraintlayout.ConstraintLayout
import com.viewcompose.constraintlayout.constrainAs
import com.viewcompose.constraintlayout.constraintSet
import com.viewcompose.constraintlayout.createFlow
import com.viewcompose.constraintlayout.createGuidelineFromStart
import com.viewcompose.constraintlayout.createGuidelineFromTop
import com.viewcompose.constraintlayout.createHorizontalChain
import com.viewcompose.constraintlayout.createRefs
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
