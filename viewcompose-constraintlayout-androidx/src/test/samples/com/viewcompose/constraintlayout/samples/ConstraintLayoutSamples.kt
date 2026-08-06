package com.viewcompose.constraintlayout.samples

import com.viewcompose.ui.modifier.Modifier
import com.viewcompose.ui.node.spec.ConstraintChainStyle
import com.viewcompose.ui.node.spec.ConstraintFlowWrapMode
import com.viewcompose.ui.node.spec.ConstraintSetSpec
import com.viewcompose.ui.unit.dp
import com.viewcompose.constraintlayout.ConstraintLayout
import com.viewcompose.constraintlayout.constrainAs
import com.viewcompose.constraintlayout.constraintSet
import com.viewcompose.constraintlayout.createFlow
import com.viewcompose.constraintlayout.createGuidelineFromTop
import com.viewcompose.constraintlayout.createHorizontalChain
import com.viewcompose.constraintlayout.createRefs
import com.viewcompose.constraintlayout.parent
import com.viewcompose.ui.foundation.Text
import com.viewcompose.ui.foundation.UiTreeBuilder

fun UiTreeBuilder.constraintLayoutSample() {
    ConstraintLayout {
        val (title, body) = createRefs("title", "body")
        val top = createGuidelineFromTop(0.1f)
        Text(
            text = "Title",
            modifier = Modifier.constrainAs(title) {
                startToStart(parent)
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
        val (first, second, third) = createRefs("first", "second", "third")
        createFlow(
            first,
            second,
            third,
            wrapMode = ConstraintFlowWrapMode.Chain,
            horizontalGap = 8.dp,
            maxElementsWrap = 2,
        )
        createHorizontalChain(first, second, style = ConstraintChainStyle.SpreadInside)
    }
}

fun constraintSetSample(): ConstraintSetSpec {
    return constraintSet {
        val (title, body) = createRefs("title", "body")
        constrain(title.id) {
            startToStart(parent)
            topToTop(parent)
        }
        constrain(body.id) {
            startToStart(title)
            topToBottom(title, margin = 8.dp)
        }
    }
}
