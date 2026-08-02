package com.viewcompose.widget.constraintlayout.samples

import com.viewcompose.ui.modifier.Modifier
import com.viewcompose.ui.node.spec.ConstraintChainStyle
import com.viewcompose.ui.node.spec.ConstraintFlowWrapMode
import com.viewcompose.ui.node.spec.ConstraintSetSpec
import com.viewcompose.ui.unit.dp
import com.viewcompose.widget.constraintlayout.ConstraintLayout
import com.viewcompose.widget.constraintlayout.constrainAs
import com.viewcompose.widget.constraintlayout.constraintSet
import com.viewcompose.widget.constraintlayout.createFlow
import com.viewcompose.widget.constraintlayout.createGuidelineFromTop
import com.viewcompose.widget.constraintlayout.createHorizontalChain
import com.viewcompose.widget.constraintlayout.createRefs
import com.viewcompose.widget.constraintlayout.parent
import com.viewcompose.widget.core.Text
import com.viewcompose.widget.core.UiTreeBuilder

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
