package com.viewcompose.preview.catalog.domain

import com.viewcompose.preview.catalog.model.PreviewDomain
import com.viewcompose.preview.catalog.model.PreviewSpec
import com.viewcompose.ui.modifier.Modifier
import com.viewcompose.ui.modifier.fillMaxWidth
import com.viewcompose.ui.modifier.height
import com.viewcompose.ui.modifier.padding
import com.viewcompose.ui.modifier.size
import com.viewcompose.ui.foundation.Badge
import com.viewcompose.ui.foundation.BadgedBox
import com.viewcompose.ui.foundation.Button
import com.viewcompose.ui.foundation.Column
import com.viewcompose.ui.foundation.Image
import com.viewcompose.ui.foundation.Surface
import com.viewcompose.ui.foundation.SurfaceVariant
import com.viewcompose.ui.foundation.Text
import com.viewcompose.ui.foundation.UiTextStyle
import com.viewcompose.ui.unit.dp
import com.viewcompose.ui.unit.sp
import com.viewcompose.ui.node.ImageSource

internal object ContentPreviewSpecs {
    val all: List<PreviewSpec> = listOf(
        PreviewSpec(
            id = "content-text-image",
            title = "Text + Image",
            domain = PreviewDomain.Content,
            content = {
                Column(
                    spacing = 8.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                ) {
                    Text(
                        text = "ViewCompose 内容预览",
                        style = UiTextStyle(fontSizeSp = 20.sp),
                    )
                    Text(
                        text = "用于校验字体层级、行高和图片渲染。",
                        style = UiTextStyle(fontSizeSp = 14.sp),
                    )
                    Image(
                        source = ImageSource.Resource(android.R.drawable.ic_menu_gallery),
                        contentDescription = "Gallery",
                        modifier = Modifier.size(40.dp, 40.dp),
                    )
                }
            },
        ),
        PreviewSpec(
            id = "content-badge-surface",
            title = "Badge + Surface",
            domain = PreviewDomain.Content,
            content = {
                Surface(
                    variant = SurfaceVariant.Variant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp)
                        .padding(12.dp),
                ) {
                    BadgedBox(
                        badge = {
                            Badge(count = 8)
                        },
                    ) {
                        Button(
                            text = "消息中心",
                            onClick = {},
                        )
                    }
                }
            },
        ),
    )
}
