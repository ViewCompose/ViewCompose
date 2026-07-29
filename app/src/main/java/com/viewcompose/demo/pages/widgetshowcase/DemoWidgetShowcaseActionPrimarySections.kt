package com.viewcompose

import com.viewcompose.ui.layout.VerticalAlignment
import com.viewcompose.ui.modifier.Modifier
import com.viewcompose.ui.modifier.fillMaxWidth
import com.viewcompose.ui.modifier.margin
import com.viewcompose.ui.node.ImageSource
import com.viewcompose.runtime.mutableStateOf
import com.viewcompose.widget.core.Button
import com.viewcompose.widget.core.ButtonSize
import com.viewcompose.widget.core.ButtonVariant
import com.viewcompose.widget.core.Column
import com.viewcompose.widget.core.IconButton
import com.viewcompose.widget.core.Row
import com.viewcompose.widget.core.SegmentedControl
import com.viewcompose.widget.core.SegmentedControlSize
import com.viewcompose.widget.core.Text
import com.viewcompose.widget.core.TextButton
import com.viewcompose.widget.core.TextDefaults
import com.viewcompose.widget.core.Theme
import com.viewcompose.widget.core.UiTextStyle
import com.viewcompose.widget.core.UiTreeBuilder
import com.viewcompose.ui.unit.dp
import com.viewcompose.widget.core.remember
import com.viewcompose.ui.unit.sp

internal fun UiTreeBuilder.ShowcaseButton() {
    Column(
        spacing = 0.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        DemoSection(title = "变体 × 尺寸矩阵", subtitle = "5 种 variant × 3 种 size") {
            ButtonVariant.entries.forEach { variant ->
                Text(
                    text = variant.name,
                    style = UiTextStyle(fontSizeSp = 14.sp),
                    color = TextDefaults.secondaryColor(),
                    modifier = Modifier.margin(bottom = 4.dp),
                )
                Row(
                    spacing = 8.dp,
                    verticalAlignment = VerticalAlignment.Center,
                    modifier = Modifier.margin(bottom = 12.dp),
                ) {
                    ButtonSize.entries.forEach { size ->
                        Button(
                            text = size.name,
                            variant = variant,
                            size = size,
                            onClick = {},
                        )
                    }
                }
            }
        }

        DemoSection(title = "禁用态", subtitle = "enabled = false") {
            Row(spacing = 8.dp) {
                Button(text = "Enabled", onClick = {})
                Button(text = "Disabled", enabled = false, onClick = {})
            }
        }

        DemoSection(title = "带图标", subtitle = "leadingIcon / trailingIcon") {
            val icon = ImageSource.Resource(R.drawable.demo_media_icon)
            Column(spacing = 8.dp) {
                Button(text = "Leading Icon", leadingIcon = icon, onClick = {})
                Button(text = "Trailing Icon", trailingIcon = icon, onClick = {})
                Button(
                    text = "Both Icons",
                    leadingIcon = icon,
                    trailingIcon = icon,
                    onClick = {},
                )
            }
        }

        DemoSection(title = "TextButton", subtitle = "尺寸对比和禁用态") {
            Row(
                spacing = 8.dp,
                verticalAlignment = VerticalAlignment.Center,
            ) {
                ButtonSize.entries.forEach { size ->
                    TextButton(
                        text = size.name,
                        size = size,
                        onClick = {},
                    )
                }
            }
            TextButton(
                text = "Disabled TextButton",
                enabled = false,
                onClick = {},
                modifier = Modifier.margin(top = 8.dp),
            )
        }
    }
}

internal fun UiTreeBuilder.ShowcaseIconButton() {
    Column(
        spacing = 0.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        DemoSection(title = "变体对比", subtitle = "5 种 ButtonVariant") {
            Row(
                spacing = 8.dp,
                verticalAlignment = VerticalAlignment.Center,
            ) {
                ButtonVariant.entries.forEach { variant ->
                    IconButton(
                        icon = ImageSource.Resource(R.drawable.demo_media_icon),
                        variant = variant,
                        onClick = {},
                    )
                }
            }
            Row(
                spacing = 8.dp,
                modifier = Modifier.margin(top = 4.dp),
            ) {
                ButtonVariant.entries.forEach { variant ->
                    Text(
                        text = variant.name,
                        style = UiTextStyle(fontSizeSp = 11.sp),
                        color = TextDefaults.secondaryColor(),
                    )
                }
            }
        }

        DemoSection(title = "自定义 Tint", subtitle = "着色自定义") {
            Row(spacing = 12.dp) {
                IconButton(
                    icon = ImageSource.Resource(R.drawable.demo_media_icon),
                    tint = Theme.colors.primary,
                    onClick = {},
                )
                IconButton(
                    icon = ImageSource.Resource(R.drawable.demo_media_icon),
                    tint = Theme.colors.secondary,
                    onClick = {},
                )
                IconButton(
                    icon = ImageSource.Resource(R.drawable.demo_media_icon),
                    tint = Theme.colors.onSurfaceVariant,
                    onClick = {},
                )
            }
        }

        DemoSection(title = "禁用态", subtitle = "enabled = false") {
            Row(spacing = 8.dp) {
                IconButton(
                    icon = ImageSource.Resource(R.drawable.demo_media_icon),
                    onClick = {},
                )
                IconButton(
                    icon = ImageSource.Resource(R.drawable.demo_media_icon),
                    enabled = false,
                    onClick = {},
                )
            }
        }
    }
}

internal fun UiTreeBuilder.ShowcaseSegmentedControl() {
    val selectedCompact = remember { mutableStateOf(0) }
    val selectedMedium = remember { mutableStateOf(1) }
    val selectedLarge = remember { mutableStateOf(2) }

    Column(
        spacing = 0.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        DemoSection(title = "尺寸对比", subtitle = "Compact / Medium / Large") {
            val items = listOf("选项 A", "选项 B", "选项 C")

            Text(text = "Compact", style = UiTextStyle(fontSizeSp = 14.sp))
            SegmentedControl(
                items = items,
                selectedIndex = selectedCompact.value,
                onSelectionChange = { selectedCompact.value = it },
                size = SegmentedControlSize.Compact,
                modifier = Modifier.fillMaxWidth().margin(bottom = 12.dp),
            )

            Text(text = "Medium", style = UiTextStyle(fontSizeSp = 14.sp))
            SegmentedControl(
                items = items,
                selectedIndex = selectedMedium.value,
                onSelectionChange = { selectedMedium.value = it },
                size = SegmentedControlSize.Medium,
                modifier = Modifier.fillMaxWidth().margin(bottom = 12.dp),
            )

            Text(text = "Large", style = UiTextStyle(fontSizeSp = 14.sp))
            SegmentedControl(
                items = items,
                selectedIndex = selectedLarge.value,
                onSelectionChange = { selectedLarge.value = it },
                size = SegmentedControlSize.Large,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        DemoSection(title = "禁用态", subtitle = "enabled = false") {
            SegmentedControl(
                items = listOf("A", "B", "C"),
                selectedIndex = 0,
                onSelectionChange = {},
                enabled = false,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
