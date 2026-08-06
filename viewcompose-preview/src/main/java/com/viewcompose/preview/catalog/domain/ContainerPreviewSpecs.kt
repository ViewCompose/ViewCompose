package com.viewcompose.preview.catalog.domain

import com.viewcompose.preview.catalog.model.PreviewDomain
import com.viewcompose.preview.catalog.model.PreviewSpec
import com.viewcompose.ui.layout.BoxAlignment
import com.viewcompose.ui.layout.HorizontalAlignment
import com.viewcompose.ui.layout.VerticalAlignment
import com.viewcompose.ui.modifier.Modifier
import com.viewcompose.ui.modifier.backgroundColor
import com.viewcompose.ui.modifier.fillMaxWidth
import com.viewcompose.ui.modifier.height
import com.viewcompose.ui.modifier.layoutId
import com.viewcompose.ui.modifier.margin
import com.viewcompose.ui.modifier.padding
import com.viewcompose.ui.node.spec.ConstraintChainStyle
import com.viewcompose.ui.node.spec.ConstraintDimension
import com.viewcompose.ui.foundation.Box
import com.viewcompose.ui.foundation.Column
import com.viewcompose.ui.foundation.FlowRow
import com.viewcompose.ui.foundation.Row
import com.viewcompose.ui.foundation.ScrollableRow
import com.viewcompose.ui.foundation.Surface
import com.viewcompose.ui.foundation.SurfaceVariant
import com.viewcompose.ui.foundation.Text
import com.viewcompose.ui.foundation.Theme
import com.viewcompose.ui.unit.dp
import com.viewcompose.constraintlayout.*

internal object ContainerPreviewSpecs {
    val all: List<PreviewSpec> = listOf(
        PreviewSpec(
            id = "container-box-row-column",
            title = "Box / Row / Column / Surface",
            domain = PreviewDomain.Container,
            content = {
                Column(
                    spacing = 8.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .margin(horizontal = 12.dp, vertical = 8.dp),
                ) {
                    Surface(
                        variant = SurfaceVariant.Variant,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                    ) {
                        Row(
                            spacing = 8.dp,
                            verticalAlignment = VerticalAlignment.Center,
                        ) {
                            Box(
                                contentAlignment = BoxAlignment.Center,
                                modifier = Modifier
                                    .height(48.dp)
                                    .weight(1f)
                                    .backgroundColor(Theme.colors.primary),
                            ) {
                                Text(text = "Primary")
                            }
                            Box(
                                contentAlignment = BoxAlignment.Center,
                                modifier = Modifier
                                    .height(48.dp)
                                    .weight(1f)
                                    .backgroundColor(Theme.colors.secondary),
                            ) {
                                Text(text = "Secondary")
                            }
                        }
                    }
                }
            },
        ),
        PreviewSpec(
            id = "container-flow-scroll",
            title = "FlowRow / ScrollableRow",
            domain = PreviewDomain.Container,
            content = {
                Column(
                    spacing = 8.dp,
                    horizontalAlignment = HorizontalAlignment.Start,
                    modifier = Modifier
                        .fillMaxWidth()
                        .margin(horizontal = 12.dp, vertical = 8.dp),
                ) {
                    FlowRow(
                        horizontalSpacing = 8.dp,
                        verticalSpacing = 8.dp,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        (1..8).forEach { index ->
                            Surface(
                                variant = SurfaceVariant.Variant,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            ) {
                                Text(text = "标签 $index")
                            }
                        }
                    }
                    ScrollableRow(
                        spacing = 8.dp,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        (1..6).forEach { index ->
                            Surface(
                                variant = SurfaceVariant.Variant,
                                modifier = Modifier
                                    .height(40.dp)
                                    .padding(horizontal = 12.dp),
                            ) {
                                Box(
                                    contentAlignment = BoxAlignment.Center,
                                    modifier = Modifier.align(BoxAlignment.Center),
                                ) {
                                    Text(text = "Item $index")
                                }
                            }
                        }
                    }
                }
            },
        ),
        PreviewSpec(
            id = "container-constraint-layout",
            title = "ConstraintLayout (Anchors + Helpers + Set)",
            domain = PreviewDomain.Container,
            content = {
                val compactSet = constraintSet {
                    val (titleRef, markerRef) = createRefs("title", "marker")
                    constrain("title") {
                        startToStart(parent)
                        topToTop(parent)
                    }
                    constrain("marker") {
                        startToStart(titleRef)
                        topToBottom(titleRef, margin = 10.dp)
                        endToEnd(parent)
                        width = ConstraintDimension.FillToConstraints
                    }
                }
                Column(
                    spacing = 8.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .margin(horizontal = 12.dp, vertical = 8.dp),
                ) {
                    ConstraintLayout(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(136.dp)
                            .backgroundColor(Theme.colors.surfaceVariant)
                            .padding(10.dp),
                    ) {
                        val (aRef, bRef, cRef) = createRefs("a", "b", "c")
                        createHorizontalChain(aRef, bRef, cRef, style = ConstraintChainStyle.SpreadInside)
                        Surface(
                            variant = SurfaceVariant.Default,
                            modifier = Modifier.constrainAs(aRef) {
                                topToTop(parent)
                                bottomToBottom(parent)
                                width = ConstraintDimension.Fixed(64.dp)
                                height = ConstraintDimension.Fixed(44.dp)
                            },
                        ) { Box(contentAlignment = BoxAlignment.Center, modifier = Modifier.fillMaxWidth()) { Text(text = "A") } }
                        Surface(
                            variant = SurfaceVariant.Variant,
                            modifier = Modifier.constrainAs(bRef) {
                                topToTop(parent)
                                bottomToBottom(parent)
                                width = ConstraintDimension.Fixed(64.dp)
                                height = ConstraintDimension.Fixed(44.dp)
                            },
                        ) { Box(contentAlignment = BoxAlignment.Center, modifier = Modifier.fillMaxWidth()) { Text(text = "B") } }
                        Surface(
                            variant = SurfaceVariant.Default,
                            modifier = Modifier.constrainAs(cRef) {
                                topToTop(parent)
                                bottomToBottom(parent)
                                width = ConstraintDimension.Fixed(64.dp)
                                height = ConstraintDimension.Fixed(44.dp)
                            },
                        ) { Box(contentAlignment = BoxAlignment.Center, modifier = Modifier.fillMaxWidth()) { Text(text = "C") } }
                    }
                    ConstraintLayout(
                        constraintSet = compactSet,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(96.dp)
                            .backgroundColor(Theme.colors.background)
                            .padding(10.dp),
                    ) {
                        Text(
                            text = "Decoupled Set",
                            modifier = Modifier.layoutId("title"),
                        )
                        Surface(
                            variant = SurfaceVariant.Variant,
                            modifier = Modifier.layoutId("marker").padding(horizontal = 10.dp, vertical = 6.dp),
                        ) {
                            Text(text = "layout updates by set")
                        }
                    }
                }
            },
        ),
    )
}
