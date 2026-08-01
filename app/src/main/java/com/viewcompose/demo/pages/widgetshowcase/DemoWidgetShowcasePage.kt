package com.viewcompose

import com.viewcompose.preview.tooling.ViewComposePreview
import com.viewcompose.ui.modifier.Modifier
import com.viewcompose.ui.modifier.fillMaxSize
import com.viewcompose.ui.modifier.margin
import com.viewcompose.ui.modifier.padding
import com.viewcompose.runtime.mutableStateOf
import com.viewcompose.widget.core.Button
import com.viewcompose.widget.core.ButtonVariant
import com.viewcompose.widget.core.Card
import com.viewcompose.widget.core.CardVariant
import com.viewcompose.widget.core.Divider
import com.viewcompose.widget.core.LazyColumn
import com.viewcompose.widget.core.ListItem
import com.viewcompose.widget.core.Text
import com.viewcompose.widget.core.TextDefaults
import com.viewcompose.widget.core.UiTextStyle
import com.viewcompose.widget.core.UiTreeBuilder
import com.viewcompose.ui.unit.dp
import com.viewcompose.widget.core.remember
import com.viewcompose.ui.unit.sp

internal data class WidgetEntry(
    val key: String,
    val name: String,
    val description: String,
)

internal data class WidgetGroup(
    val title: String,
    val widgets: List<WidgetEntry>,
)

private val WIDGET_GROUPS = listOf(
    WidgetGroup(
        title = "内容展示",
        widgets = listOf(
            WidgetEntry("text", "Text", "文本显示，支持字号、颜色、对齐、装饰线、截断等"),
            WidgetEntry("image", "Image / Icon", "图片与图标展示，支持缩放模式、着色、占位图"),
            WidgetEntry("divider", "Divider", "分隔线，支持颜色和粗细自定义"),
        ),
    ),
    WidgetGroup(
        title = "按钮与操作",
        widgets = listOf(
            WidgetEntry("button", "Button / TextButton", "按钮控件，5 种变体 × 3 种尺寸，支持图标"),
            WidgetEntry("icon_button", "IconButton", "图标按钮，5 种变体，支持着色"),
            WidgetEntry("segmented_control", "SegmentedControl", "分段选择器，3 种尺寸"),
            WidgetEntry("chip", "Chip", "标签芯片，4 种变体，支持选中态和图标"),
            WidgetEntry("fab", "FloatingActionButton", "浮动操作按钮，3 种尺寸 + 扩展样式"),
        ),
    ),
    WidgetGroup(
        title = "输入控件",
        widgets = listOf(
            WidgetEntry("text_field", "TextField", "文本输入框，3 种变体 × 3 种尺寸，支持标签/错误/只读"),
            WidgetEntry("text_field_variants", "PasswordField / EmailField / NumberField / TextArea", "特殊输入框变体"),
            WidgetEntry("checkbox", "Checkbox", "复选框，支持选中/禁用态和自定义颜色"),
            WidgetEntry("switch", "Switch", "开关控件，支持选中/禁用态和自定义颜色"),
            WidgetEntry("radio_button", "RadioButton", "单选按钮，支持选中/禁用态和自定义颜色"),
            WidgetEntry("slider", "Slider", "滑动条，支持范围设置和自定义颜色"),
            WidgetEntry("search_bar", "SearchBar", "搜索栏，支持占位文本和清除按钮"),
        ),
    ),
    WidgetGroup(
        title = "反馈指示",
        widgets = listOf(
            WidgetEntry("linear_progress", "LinearProgressIndicator", "线性进度条，确定/不确定模式"),
            WidgetEntry("circular_progress", "CircularProgressIndicator", "环形进度条，确定/不确定模式"),
            WidgetEntry("badge", "Badge / BadgedBox", "徽标，支持数字/圆点/自定义颜色"),
        ),
    ),
    WidgetGroup(
        title = "列表与卡片",
        widgets = listOf(
            WidgetEntry("list_item", "ListItem", "列表项，单行/双行/完整模式，支持前后插槽"),
            WidgetEntry("card", "Card", "卡片，3 种变体（Filled/Elevated/Outlined）"),
        ),
    ),
)

private val WIDGET_MAP: Map<String, WidgetEntry> by lazy {
    WIDGET_GROUPS.flatMap { it.widgets }.associateBy { it.key }
}

@ViewComposePreview(name = "Widget showcase", group = "Demo/Pages")
internal fun UiTreeBuilder.WidgetShowcasePage() {
    val selectedWidget = remember { mutableStateOf<String?>(null) }

    val items: List<String> = if (selectedWidget.value == null) {
        buildList {
            WIDGET_GROUPS.forEach { group ->
                add("header:${group.title}")
                group.widgets.forEach { add("widget:${it.key}") }
                add("divider:${group.title}")
            }
        }
    } else {
        listOf("back", "detail:${selectedWidget.value}")
    }

    LazyColumn(
        items = items,
        key = { it },
        modifier = Modifier.fillMaxSize(),
    ) { item ->
        when {
            item == "back" -> {
                Button(
                    text = "← 返回控件列表",
                    variant = ButtonVariant.Text,
                    onClick = { selectedWidget.value = null },
                    modifier = Modifier.margin(bottom = 8.dp),
                )
            }

            item.startsWith("detail:") -> {
                ShowcaseContent(item.removePrefix("detail:"))
            }

            item.startsWith("header:") -> {
                Text(
                    text = item.removePrefix("header:"),
                    style = UiTextStyle(fontSizeSp = 16.sp),
                    color = TextDefaults.secondaryColor(),
                    modifier = Modifier
                        .padding(top = 16.dp, bottom = 8.dp)
                        .padding(horizontal = 4.dp),
                )
            }

            item.startsWith("divider:") -> {
                Divider(modifier = Modifier.margin(top = 4.dp))
            }

            item.startsWith("widget:") -> {
                val key = item.removePrefix("widget:")
                val widget = WIDGET_MAP[key] ?: return@LazyColumn
                Card(variant = CardVariant.Outlined, modifier = Modifier.margin(bottom = 4.dp)) {
                    ListItem(
                        headlineText = widget.name,
                        supportingText = widget.description,
                        onClick = { selectedWidget.value = widget.key },
                    )
                }
            }
        }
    }
}

private fun UiTreeBuilder.ShowcaseContent(widgetKey: String) {
    when (widgetKey) {
        "text" -> ShowcaseText()
        "image" -> ShowcaseImageIcon()
        "divider" -> ShowcaseDivider()
        "button" -> ShowcaseButton()
        "icon_button" -> ShowcaseIconButton()
        "segmented_control" -> ShowcaseSegmentedControl()
        "chip" -> ShowcaseChip()
        "fab" -> ShowcaseFab()
        "text_field" -> ShowcaseTextField()
        "text_field_variants" -> ShowcaseTextFieldVariants()
        "checkbox" -> ShowcaseCheckbox()
        "switch" -> ShowcaseSwitch()
        "radio_button" -> ShowcaseRadioButton()
        "slider" -> ShowcaseSlider()
        "search_bar" -> ShowcaseSearchBar()
        "linear_progress" -> ShowcaseLinearProgress()
        "circular_progress" -> ShowcaseCircularProgress()
        "badge" -> ShowcaseBadge()
        "list_item" -> ShowcaseListItem()
        "card" -> ShowcaseCard()
    }
}
