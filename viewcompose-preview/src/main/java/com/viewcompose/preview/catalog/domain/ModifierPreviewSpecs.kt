package com.viewcompose.preview.catalog.domain

import com.viewcompose.preview.catalog.model.PreviewDomain
import com.viewcompose.preview.catalog.model.PreviewSpec
import com.viewcompose.ui.layout.BoxAlignment
import com.viewcompose.ui.modifier.Modifier
import com.viewcompose.ui.modifier.backgroundColor
import com.viewcompose.ui.modifier.border
import com.viewcompose.ui.modifier.cornerRadius
import com.viewcompose.ui.modifier.elevation
import com.viewcompose.ui.modifier.fillMaxWidth
import com.viewcompose.ui.modifier.margin
import com.viewcompose.ui.modifier.overlayAnchor
import com.viewcompose.ui.modifier.padding
import com.viewcompose.ui.modifier.zIndex
import com.viewcompose.ui.foundation.Box
import com.viewcompose.ui.foundation.Column
import com.viewcompose.ui.foundation.Surface
import com.viewcompose.ui.foundation.Text
import com.viewcompose.ui.foundation.Theme
import com.viewcompose.ui.unit.dp

internal object ModifierPreviewSpecs {
    val all: List<PreviewSpec> = listOf(
        PreviewSpec(
            id = "modifier-style-anchor",
            title = "Style + Anchor Metadata",
            domain = PreviewDomain.Modifier,
            content = {
                Column(
                    spacing = 10.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .margin(horizontal = 12.dp, vertical = 8.dp),
                ) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .cornerRadius(16.dp)
                            .border(1.dp, Theme.colors.outlineVariant)
                            .padding(12.dp),
                    ) {
                        Text(text = "圆角/边框/内边距")
                    }
                    Box(
                        contentAlignment = BoxAlignment.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .backgroundColor(Theme.colors.surfaceVariant)
                            .cornerRadius(12.dp)
                            .elevation(6.dp)
                            .overlayAnchor("preview-anchor")
                            .zIndex(2f)
                            .padding(12.dp),
                    ) {
                        Text(text = "overlayAnchor + elevation + zIndex")
                    }
                }
            },
        ),
    )
}
