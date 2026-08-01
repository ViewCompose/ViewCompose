package com.viewcompose

import com.viewcompose.preview.tooling.ViewComposePreview
import com.viewcompose.ui.layout.VerticalAlignment
import com.viewcompose.ui.modifier.Modifier
import com.viewcompose.ui.modifier.fillMaxWidth
import com.viewcompose.ui.modifier.margin
import com.viewcompose.ui.modifier.size
import com.viewcompose.ui.node.ImageContentScale
import com.viewcompose.ui.node.ImageSource
import com.viewcompose.ui.node.TextAlign
import com.viewcompose.ui.node.TextDecoration
import com.viewcompose.ui.node.TextOverflow
import com.viewcompose.widget.core.Column
import com.viewcompose.widget.core.Divider
import com.viewcompose.widget.core.Icon
import com.viewcompose.widget.core.Image
import com.viewcompose.widget.core.Row
import com.viewcompose.widget.core.Text
import com.viewcompose.widget.core.TextDefaults
import com.viewcompose.widget.core.Theme
import com.viewcompose.widget.core.UiTextStyle
import com.viewcompose.widget.core.UiTreeBuilder
import com.viewcompose.ui.unit.dp
import com.viewcompose.ui.unit.sp

@ViewComposePreview(name = "Text", group = "Demo/Components")
internal fun UiTreeBuilder.ShowcaseText() {
    Column(
        spacing = 0.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        DemoSection(title = "字号 (Style)", subtitle = "不同 fontSizeSp 对比") {
            listOf(12, 14, 16, 20, 24, 32).forEach { size ->
                Text(
                    text = "fontSize = ${size}sp",
                    style = UiTextStyle(fontSizeSp = size.sp),
                    modifier = Modifier.margin(bottom = 4.dp),
                )
            }
        }

        DemoSection(title = "颜色 (Color)", subtitle = "使用主题颜色和自定义颜色") {
            Text(text = "Primary Color", color = Theme.colors.primary)
            Text(text = "Secondary Color", color = TextDefaults.secondaryColor())
            Text(text = "Secondary Color", color = Theme.colors.secondary)
        }

        DemoSection(title = "对齐 (TextAlign)", subtitle = "Start / Center / End") {
            TextAlign.entries.forEach { align ->
                Text(
                    text = "textAlign = $align",
                    textAlign = align,
                    modifier = Modifier.fillMaxWidth().margin(bottom = 4.dp),
                )
            }
        }

        DemoSection(title = "装饰线 (TextDecoration)", subtitle = "下划线、删除线") {
            Text(text = "Underline", textDecoration = TextDecoration.Underline)
            Text(text = "LineThrough", textDecoration = TextDecoration.LineThrough)
            Text(
                text = "Underline + LineThrough",
                textDecoration = TextDecoration.UnderlineLineThrough,
            )
        }

        DemoSection(title = "截断 (MaxLines + Overflow)", subtitle = "单行截断 Ellipsis") {
            Text(
                text = "这是一段很长的文本，用于演示单行截断效果。当文本超出容器宽度时，会显示省略号来表示还有更多内容未显示。",
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                text = "这是一段很长的文本，用于演示两行截断效果。当文本超出两行时会被截断并显示省略号。这里需要足够长的文字才能触发截断效果。",
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth().margin(top = 8.dp),
            )
        }
    }
}

@ViewComposePreview(name = "Image and Icon", group = "Demo/Components")
internal fun UiTreeBuilder.ShowcaseImageIcon() {
    Column(
        spacing = 0.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        DemoSection(title = "Image 缩放模式", subtitle = "contentScale 对比") {
            Row(
                spacing = 12.dp,
                modifier = Modifier.fillMaxWidth(),
            ) {
                ImageContentScale.entries.forEach { scale ->
                    Column(spacing = 4.dp) {
                        Image(
                            source = ImageSource.Resource(R.drawable.demo_media_icon),
                            contentScale = scale,
                            modifier = Modifier.size(64.dp, 64.dp),
                        )
                        Text(
                            text = scale.name,
                            style = UiTextStyle(fontSizeSp = 12.sp),
                        )
                    }
                }
            }
        }

        DemoSection(title = "Image tint", subtitle = "图片着色对比") {
            Row(spacing = 12.dp) {
                Image(
                    source = ImageSource.Resource(R.drawable.demo_media_icon),
                    modifier = Modifier.size(48.dp, 48.dp),
                )
                Image(
                    source = ImageSource.Resource(R.drawable.demo_media_icon),
                    tint = Theme.colors.primary,
                    modifier = Modifier.size(48.dp, 48.dp),
                )
                Image(
                    source = ImageSource.Resource(R.drawable.demo_media_icon),
                    tint = Theme.colors.secondary,
                    modifier = Modifier.size(48.dp, 48.dp),
                )
            }
        }

        DemoSection(title = "Icon 尺寸与着色", subtitle = "默认 tint 和自定义 tint") {
            Row(
                spacing = 16.dp,
                verticalAlignment = VerticalAlignment.Center,
            ) {
                listOf(16, 24, 32, 48).forEach { s ->
                    Icon(
                        source = ImageSource.Resource(R.drawable.demo_media_icon),
                        size = s.dp,
                    )
                }
            }
            Row(
                spacing = 16.dp,
                verticalAlignment = VerticalAlignment.Center,
                modifier = Modifier.margin(top = 8.dp),
            ) {
                Icon(
                    source = ImageSource.Resource(R.drawable.demo_media_icon),
                    tint = Theme.colors.primary,
                )
                Icon(
                    source = ImageSource.Resource(R.drawable.demo_media_icon),
                    tint = Theme.colors.secondary,
                )
                Icon(
                    source = ImageSource.Resource(R.drawable.demo_media_icon),
                    tint = Theme.colors.onSurfaceVariant,
                )
            }
        }
    }
}

@ViewComposePreview(name = "Divider", group = "Demo/Components")
internal fun UiTreeBuilder.ShowcaseDivider() {
    Column(
        spacing = 0.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        DemoSection(title = "默认分隔线", subtitle = "使用主题默认颜色和粗细") {
            Divider()
        }

        DemoSection(title = "颜色对比", subtitle = "不同颜色的分隔线") {
            Text(text = "Primary", style = UiTextStyle(fontSizeSp = 13.sp))
            Divider(color = Theme.colors.primary)
            Text(
                text = "Accent",
                style = UiTextStyle(fontSizeSp = 13.sp),
                modifier = Modifier.margin(top = 8.dp),
            )
            Divider(color = Theme.colors.secondary)
            Text(
                text = "TextSecondary",
                style = UiTextStyle(fontSizeSp = 13.sp),
                modifier = Modifier.margin(top = 8.dp),
            )
            Divider(color = Theme.colors.onSurfaceVariant)
        }

        DemoSection(title = "粗细对比", subtitle = "不同 thickness 的分隔线") {
            listOf(1, 2, 4, 8).forEach { t ->
                Text(
                    text = "thickness = ${t}dp",
                    style = UiTextStyle(fontSizeSp = 13.sp),
                )
                Divider(
                    thickness = t.dp,
                    modifier = Modifier.margin(bottom = 8.dp),
                )
            }
        }
    }
}
