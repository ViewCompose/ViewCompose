package com.viewcompose

import com.viewcompose.preview.tooling.ViewComposePreview
import com.viewcompose.ui.modifier.Modifier
import com.viewcompose.ui.modifier.fillMaxWidth
import com.viewcompose.runtime.mutableStateOf
import com.viewcompose.widget.core.Checkbox
import com.viewcompose.widget.core.Column
import com.viewcompose.widget.core.RadioButton
import com.viewcompose.widget.core.Slider
import com.viewcompose.widget.core.Switch
import com.viewcompose.widget.core.Text
import com.viewcompose.widget.core.Theme
import com.viewcompose.widget.core.UiTreeBuilder
import com.viewcompose.ui.unit.dp
import com.viewcompose.widget.core.remember

@ViewComposePreview(name = "Checkbox", group = "Demo/Components")
internal fun UiTreeBuilder.ShowcaseCheckbox() {
    val checked1 = remember { mutableStateOf(true) }
    val checked2 = remember { mutableStateOf(false) }

    Column(
        spacing = 0.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        DemoSection(title = "基础用法", subtitle = "checked / unchecked 切换") {
            Checkbox(
                text = "选中状态",
                checked = checked1.value,
                onCheckedChange = { checked1.value = it },
            )
            Checkbox(
                text = "未选中状态",
                checked = checked2.value,
                onCheckedChange = { checked2.value = it },
            )
        }

        DemoSection(title = "禁用态", subtitle = "enabled = false") {
            Checkbox(
                text = "禁用 - 选中",
                checked = true,
                onCheckedChange = {},
                enabled = false,
            )
            Checkbox(
                text = "禁用 - 未选中",
                checked = false,
                onCheckedChange = {},
                enabled = false,
            )
        }

        DemoSection(title = "自定义颜色", subtitle = "checkedColor / uncheckedColor") {
            val c = remember { mutableStateOf(true) }
            Checkbox(
                text = "自定义颜色",
                checked = c.value,
                onCheckedChange = { c.value = it },
                checkedColor = Theme.colors.secondary,
                uncheckedColor = Theme.colors.onSurfaceVariant,
            )
        }
    }
}

@ViewComposePreview(name = "Switch", group = "Demo/Components")
internal fun UiTreeBuilder.ShowcaseSwitch() {
    val checked1 = remember { mutableStateOf(true) }
    val checked2 = remember { mutableStateOf(false) }

    Column(
        spacing = 0.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        DemoSection(title = "基础用法", subtitle = "checked / unchecked 切换") {
            Switch(
                text = "已开启",
                checked = checked1.value,
                onCheckedChange = { checked1.value = it },
            )
            Switch(
                text = "已关闭",
                checked = checked2.value,
                onCheckedChange = { checked2.value = it },
            )
        }

        DemoSection(title = "禁用态", subtitle = "enabled = false") {
            Switch(
                text = "禁用 - 开启",
                checked = true,
                onCheckedChange = {},
                enabled = false,
            )
            Switch(
                text = "禁用 - 关闭",
                checked = false,
                onCheckedChange = {},
                enabled = false,
            )
        }

        DemoSection(title = "自定义颜色", subtitle = "thumbColor / trackColor") {
            val c = remember { mutableStateOf(true) }
            Switch(
                text = "自定义颜色",
                checked = c.value,
                onCheckedChange = { c.value = it },
                thumbColor = Theme.colors.secondary,
                trackColor = Theme.colors.surfaceVariant,
            )
        }
    }
}

@ViewComposePreview(name = "RadioButton", group = "Demo/Components")
internal fun UiTreeBuilder.ShowcaseRadioButton() {
    val selectedIndex = remember { mutableStateOf(0) }

    Column(
        spacing = 0.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        DemoSection(title = "基础用法", subtitle = "单选按钮组") {
            listOf("选项 A", "选项 B", "选项 C").forEachIndexed { index, label ->
                RadioButton(
                    text = label,
                    checked = selectedIndex.value == index,
                    onCheckedChange = { if (it) selectedIndex.value = index },
                )
            }
        }

        DemoSection(title = "禁用态", subtitle = "enabled = false") {
            RadioButton(
                text = "禁用 - 选中",
                checked = true,
                onCheckedChange = {},
                enabled = false,
            )
            RadioButton(
                text = "禁用 - 未选中",
                checked = false,
                onCheckedChange = {},
                enabled = false,
            )
        }

        DemoSection(title = "自定义颜色", subtitle = "checkedColor / uncheckedColor") {
            val c = remember { mutableStateOf(true) }
            RadioButton(
                text = "自定义颜色",
                checked = c.value,
                onCheckedChange = { c.value = it },
                checkedColor = Theme.colors.secondary,
                uncheckedColor = Theme.colors.onSurfaceVariant,
            )
        }
    }
}

@ViewComposePreview(name = "Slider", group = "Demo/Components")
internal fun UiTreeBuilder.ShowcaseSlider() {
    val value1 = remember { mutableStateOf(50) }
    val value2 = remember { mutableStateOf(25) }

    Column(
        spacing = 0.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        DemoSection(title = "基础用法", subtitle = "默认范围 0-100") {
            Text(text = "当前值: ${value1.value}")
            Slider(
                value = value1.value,
                onValueChange = { value1.value = it },
                modifier = Modifier.fillMaxWidth(),
            )
        }

        DemoSection(title = "自定义范围", subtitle = "min=0, max=50") {
            Text(text = "当前值: ${value2.value}")
            Slider(
                value = value2.value,
                onValueChange = { value2.value = it },
                min = 0,
                max = 50,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        DemoSection(title = "禁用态", subtitle = "enabled = false") {
            Slider(
                value = 30,
                onValueChange = {},
                enabled = false,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        DemoSection(title = "自定义颜色", subtitle = "thumbColor / trackColor") {
            val v = remember { mutableStateOf(60) }
            Slider(
                value = v.value,
                onValueChange = { v.value = it },
                thumbColor = Theme.colors.secondary,
                trackColor = Theme.colors.surfaceVariant,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
