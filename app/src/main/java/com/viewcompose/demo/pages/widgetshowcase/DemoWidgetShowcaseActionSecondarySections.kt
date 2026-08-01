package com.viewcompose

import com.viewcompose.preview.tooling.ViewComposePreview
import com.viewcompose.ui.layout.VerticalAlignment
import com.viewcompose.ui.modifier.Modifier
import com.viewcompose.ui.modifier.fillMaxWidth
import com.viewcompose.ui.modifier.margin
import com.viewcompose.ui.node.ImageSource
import com.viewcompose.runtime.mutableStateOf
import com.viewcompose.widget.core.Chip
import com.viewcompose.widget.core.ChipVariant
import com.viewcompose.widget.core.Column
import com.viewcompose.widget.core.ExtendedFloatingActionButton
import com.viewcompose.widget.core.FabSize
import com.viewcompose.widget.core.FloatingActionButton
import com.viewcompose.widget.core.FlowRow
import com.viewcompose.widget.core.Icon
import com.viewcompose.widget.core.Row
import com.viewcompose.widget.core.Text
import com.viewcompose.widget.core.TextDefaults
import com.viewcompose.widget.core.Theme
import com.viewcompose.widget.core.UiTextStyle
import com.viewcompose.widget.core.UiTreeBuilder
import com.viewcompose.ui.unit.dp
import com.viewcompose.widget.core.remember
import com.viewcompose.ui.unit.sp

@ViewComposePreview(name = "Chip", group = "Demo/Components")
internal fun UiTreeBuilder.ShowcaseChip() {
    val selectedState = remember { mutableStateOf(false) }

    Column(
        spacing = 0.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        DemoSection(title = "变体对比", subtitle = "Assist / Filter / Input / Suggestion") {
            FlowRow(
                horizontalSpacing = 8.dp,
                verticalSpacing = 8.dp,
            ) {
                ChipVariant.entries.forEach { variant ->
                    Chip(
                        label = variant.name,
                        variant = variant,
                        onClick = {},
                    )
                }
            }
        }

        DemoSection(title = "选中态", subtitle = "Filter Chip selected 切换") {
            Chip(
                label = if (selectedState.value) "已选中" else "未选中",
                variant = ChipVariant.Filter,
                selected = selectedState.value,
                onClick = { selectedState.value = !selectedState.value },
            )
        }

        DemoSection(title = "带图标", subtitle = "leadingIcon + onTrailingIconClick") {
            Chip(
                label = "带前置图标",
                leadingIcon = ImageSource.Resource(R.drawable.demo_media_icon),
                onClick = {},
            )
            Chip(
                label = "可删除",
                onTrailingIconClick = {},
                onClick = {},
                modifier = Modifier.margin(top = 8.dp),
            )
            Chip(
                label = "完整 Chip",
                leadingIcon = ImageSource.Resource(R.drawable.demo_media_icon),
                onTrailingIconClick = {},
                onClick = {},
                modifier = Modifier.margin(top = 8.dp),
            )
        }

        DemoSection(title = "禁用态", subtitle = "enabled = false") {
            Row(spacing = 8.dp) {
                Chip(label = "Enabled", onClick = {})
                Chip(label = "Disabled", enabled = false, onClick = {})
            }
        }
    }
}

@ViewComposePreview(name = "FloatingActionButton", group = "Demo/Components")
internal fun UiTreeBuilder.ShowcaseFab() {
    Column(
        spacing = 0.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        DemoSection(title = "尺寸对比", subtitle = "Small / Medium / Large") {
            Row(
                spacing = 16.dp,
                verticalAlignment = VerticalAlignment.Center,
            ) {
                FabSize.entries.forEach { size ->
                    FloatingActionButton(
                        onClick = {},
                        size = size,
                    ) {
                        Icon(source = ImageSource.Resource(R.drawable.demo_media_icon))
                    }
                }
            }
            Row(
                spacing = 16.dp,
                modifier = Modifier.margin(top = 4.dp),
            ) {
                FabSize.entries.forEach { size ->
                    Text(
                        text = size.name,
                        style = UiTextStyle(fontSizeSp = 12.sp),
                        color = TextDefaults.secondaryColor(),
                    )
                }
            }
        }

        DemoSection(title = "ExtendedFAB", subtitle = "文字 + 图标的扩展样式") {
            ExtendedFloatingActionButton(
                text = "新建",
                icon = ImageSource.Resource(R.drawable.demo_media_icon),
                onClick = {},
            )
            ExtendedFloatingActionButton(
                text = "仅文字",
                onClick = {},
                modifier = Modifier.margin(top = 12.dp),
            )
        }

        DemoSection(title = "自定义颜色", subtitle = "containerColor / contentColor") {
            FloatingActionButton(
                onClick = {},
                containerColor = Theme.colors.secondary,
                contentColor = Theme.colors.surface,
            ) {
                Icon(source = ImageSource.Resource(R.drawable.demo_media_icon))
            }
        }
    }
}
