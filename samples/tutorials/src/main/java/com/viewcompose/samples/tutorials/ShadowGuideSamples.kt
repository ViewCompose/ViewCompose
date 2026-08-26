package com.viewcompose.samples.tutorials

import com.viewcompose.ui.foundation.Surface
import com.viewcompose.ui.foundation.Text
import com.viewcompose.ui.foundation.UiTreeBuilder
import com.viewcompose.ui.graphics.UiShadow
import com.viewcompose.ui.modifier.Modifier
import com.viewcompose.ui.modifier.dropShadows
import com.viewcompose.ui.modifier.innerShadow
import com.viewcompose.ui.modifier.padding
import com.viewcompose.ui.modifier.shape
import com.viewcompose.ui.shape.UiShape
import com.viewcompose.ui.unit.dp

// DOCS_REGION_START(shadow-card)
fun UiTreeBuilder.ShadowCard() {
    val cardShape = UiShape.rounded(20.dp)

    Surface(
        modifier = Modifier
            .shape(cardShape)
            .dropShadows(
                shadows = listOf(
                    UiShadow(
                        color = 0x33000000,
                        blurRadius = 12.dp,
                        offsetY = 5.dp,
                    ),
                    UiShadow(
                        color = 0x223B82F6,
                        blurRadius = 18.dp,
                        spreadRadius = 2.dp,
                        offsetX = (-4).dp,
                    ),
                ),
                shape = cardShape,
            )
            .innerShadow(
                shadow = UiShadow(
                    color = 0x44000000,
                    blurRadius = 8.dp,
                    offsetY = 3.dp,
                ),
                shape = cardShape,
            )
            .padding(20.dp),
    ) {
        Text("Exact outer and inner shadows")
    }
}
// DOCS_REGION_END(shadow-card)
