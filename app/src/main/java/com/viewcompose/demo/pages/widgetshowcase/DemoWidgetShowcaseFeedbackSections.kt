package com.viewcompose

import com.viewcompose.preview.tooling.ViewComposePreview
import com.viewcompose.ui.layout.VerticalAlignment
import com.viewcompose.ui.modifier.Modifier
import com.viewcompose.ui.modifier.fillMaxWidth
import com.viewcompose.ui.modifier.margin
import com.viewcompose.ui.node.ImageSource
import com.viewcompose.ui.foundation.Badge
import com.viewcompose.ui.foundation.BadgedBox
import com.viewcompose.ui.foundation.CircularProgressIndicator
import com.viewcompose.ui.foundation.Column
import com.viewcompose.ui.foundation.Icon
import com.viewcompose.ui.foundation.IconButton
import com.viewcompose.ui.foundation.LinearProgressIndicator
import com.viewcompose.ui.foundation.Row
import com.viewcompose.ui.foundation.SearchBar
import com.viewcompose.ui.foundation.Text
import com.viewcompose.ui.foundation.Theme
import com.viewcompose.ui.foundation.UiTextStyle
import com.viewcompose.ui.foundation.UiTreeBuilder
import com.viewcompose.ui.unit.dp
import com.viewcompose.ui.foundation.rememberTextFieldState
import com.viewcompose.ui.unit.sp

@ViewComposePreview(name = "SearchBar", group = "Demo/Components")
internal fun UiTreeBuilder.ShowcaseSearchBar() {
    val query1 = rememberTextFieldState()
    val query2 = rememberTextFieldState("搜索内容")
    val disabledQuery = rememberTextFieldState()

    Column(
        spacing = 0.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        DemoSection(title = "基础搜索", subtitle = "带占位文本") {
            SearchBar(
                state = query1,
                placeholder = "搜索...",
                modifier = Modifier.fillMaxWidth(),
            )
        }

        DemoSection(title = "带清除按钮", subtitle = "trailingIcon 自定义") {
            SearchBar(
                state = query2,
                placeholder = "搜索...",
                trailingIcon = if (query2.text.isNotEmpty()) {
                    {
                        IconButton(
                            icon = ImageSource.Resource(R.drawable.demo_media_icon),
                            onClick = query2::clearText,
                        )
                    }
                } else {
                    null
                },
                modifier = Modifier.fillMaxWidth(),
            )
        }

        DemoSection(title = "禁用态", subtitle = "enabled = false") {
            SearchBar(
                state = disabledQuery,
                placeholder = "搜索已禁用",
                enabled = false,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@ViewComposePreview(name = "LinearProgressIndicator", group = "Demo/Components")
internal fun UiTreeBuilder.ShowcaseLinearProgress() {
    Column(
        spacing = 0.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        DemoSection(title = "确定模式", subtitle = "progress = 0.0 ~ 1.0") {
            listOf(0.0f, 0.25f, 0.5f, 0.75f, 1.0f).forEach { p ->
                Text(
                    text = "progress = $p",
                    style = UiTextStyle(fontSizeSp = 13.sp),
                )
                LinearProgressIndicator(
                    progress = p,
                    modifier = Modifier.margin(bottom = 8.dp),
                )
            }
        }

        DemoSection(title = "不确定模式", subtitle = "progress = null") {
            LinearProgressIndicator()
        }

        DemoSection(title = "自定义颜色与粗细", subtitle = "indicatorColor / trackThickness") {
            LinearProgressIndicator(
                progress = 0.6f,
                indicatorColor = Theme.colors.secondary,
                trackColor = Theme.colors.surfaceVariant,
            )
            LinearProgressIndicator(
                progress = 0.4f,
                trackThickness = 8.dp,
                modifier = Modifier.margin(top = 12.dp),
            )
        }
    }
}

@ViewComposePreview(name = "CircularProgressIndicator", group = "Demo/Components")
internal fun UiTreeBuilder.ShowcaseCircularProgress() {
    Column(
        spacing = 0.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        DemoSection(title = "确定模式", subtitle = "progress = 0.0 ~ 1.0") {
            Row(
                spacing = 16.dp,
                verticalAlignment = VerticalAlignment.Center,
            ) {
                listOf(0.25f, 0.5f, 0.75f, 1.0f).forEach { p ->
                    Column(spacing = 4.dp) {
                        CircularProgressIndicator(progress = p)
                        Text(
                            text = "${(p * 100).toInt()}%",
                            style = UiTextStyle(fontSizeSp = 12.sp),
                        )
                    }
                }
            }
        }

        DemoSection(title = "不确定模式", subtitle = "progress = null") {
            CircularProgressIndicator()
        }

        DemoSection(title = "自定义颜色与尺寸", subtitle = "indicatorColor / size") {
            Row(
                spacing = 16.dp,
                verticalAlignment = VerticalAlignment.Center,
            ) {
                CircularProgressIndicator(
                    progress = 0.7f,
                    indicatorColor = Theme.colors.secondary,
                )
                CircularProgressIndicator(
                    progress = 0.5f,
                    size = 64.dp,
                    trackThickness = 6.dp,
                )
            }
        }
    }
}

@ViewComposePreview(name = "Badge", group = "Demo/Components")
internal fun UiTreeBuilder.ShowcaseBadge() {
    Column(
        spacing = 0.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        DemoSection(title = "数字徽标", subtitle = "count = 数字") {
            Row(
                spacing = 24.dp,
                verticalAlignment = VerticalAlignment.Center,
            ) {
                listOf(1, 9, 99, 100).forEach { count ->
                    BadgedBox(
                        badge = { Badge(count = count) },
                    ) {
                        Icon(source = ImageSource.Resource(R.drawable.demo_media_icon))
                    }
                }
            }
        }

        DemoSection(title = "圆点徽标", subtitle = "count = null") {
            BadgedBox(
                badge = { Badge() },
            ) {
                Icon(source = ImageSource.Resource(R.drawable.demo_media_icon))
            }
        }

        DemoSection(title = "自定义颜色", subtitle = "containerColor / contentColor") {
            Row(spacing = 24.dp) {
                BadgedBox(
                    badge = {
                        Badge(
                            count = 5,
                            containerColor = Theme.colors.secondary,
                            contentColor = Theme.colors.surface,
                        )
                    },
                ) {
                    Icon(source = ImageSource.Resource(R.drawable.demo_media_icon))
                }
                BadgedBox(
                    badge = {
                        Badge(
                            count = 3,
                            containerColor = Theme.colors.primary,
                            contentColor = Theme.colors.surface,
                        )
                    },
                ) {
                    Icon(source = ImageSource.Resource(R.drawable.demo_media_icon))
                }
            }
        }
    }
}
