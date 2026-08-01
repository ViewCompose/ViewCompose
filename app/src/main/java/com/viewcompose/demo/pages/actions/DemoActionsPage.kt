package com.viewcompose

import com.viewcompose.preview.tooling.PreviewTheme
import com.viewcompose.preview.tooling.ViewComposePreview
import com.viewcompose.ui.layout.VerticalAlignment
import com.viewcompose.ui.modifier.Modifier
import com.viewcompose.ui.modifier.backgroundColor
import com.viewcompose.ui.modifier.cornerRadius
import com.viewcompose.ui.modifier.fillMaxSize
import com.viewcompose.ui.modifier.fillMaxWidth
import com.viewcompose.ui.modifier.height
import com.viewcompose.ui.modifier.margin
import com.viewcompose.ui.modifier.padding
import com.viewcompose.ui.modifier.size
import com.viewcompose.ui.modifier.testTag
import com.viewcompose.ui.node.ImageSource
import com.viewcompose.runtime.mutableStateOf
import com.viewcompose.widget.core.Badge
import com.viewcompose.widget.core.BadgedBox
import com.viewcompose.widget.core.Button
import com.viewcompose.widget.core.ButtonVariant
import com.viewcompose.widget.core.Card
import com.viewcompose.widget.core.CardVariant
import com.viewcompose.widget.core.Chip
import com.viewcompose.widget.core.ChipVariant
import com.viewcompose.widget.core.Column
import com.viewcompose.widget.core.Divider
import com.viewcompose.widget.core.ElevatedCard
import com.viewcompose.widget.core.ExtendedFloatingActionButton
import com.viewcompose.widget.core.FabSize
import com.viewcompose.widget.core.FloatingActionButton
import com.viewcompose.widget.core.FlowRow
import com.viewcompose.widget.core.Icon
import com.viewcompose.widget.core.IconButton
import com.viewcompose.widget.core.LazyColumn
import com.viewcompose.widget.core.ListItem
import com.viewcompose.widget.core.OutlinedCard
import com.viewcompose.widget.core.Row
import com.viewcompose.widget.core.SurfaceDefaults
import com.viewcompose.widget.core.Text
import com.viewcompose.widget.core.TextButton
import com.viewcompose.widget.core.TextDefaults
import com.viewcompose.widget.core.Theme
import com.viewcompose.widget.core.UiTextStyle
import com.viewcompose.widget.core.UiTreeBuilder
import com.viewcompose.ui.unit.dp
import com.viewcompose.widget.core.remember
import com.viewcompose.ui.unit.sp

@ViewComposePreview(
    name = "ActionsPage · Light",
    group = "Demo",
    widthDp = 411,
    theme = PreviewTheme.Light,
)
@ViewComposePreview(
    name = "ActionsPage · Dark",
    group = "Demo",
    widthDp = 411,
    theme = PreviewTheme.Dark,
)
fun UiTreeBuilder.PreviewActionsPage() {
    ActionsPage(initialPageIndex = 0)
}

@ViewComposePreview(name = "Actions · FAB", group = "Demo/Pages")
internal fun UiTreeBuilder.PreviewActionsFab() {
    ActionsPage(initialPageIndex = 1)
}

@ViewComposePreview(name = "Actions · Chip", group = "Demo/Pages")
internal fun UiTreeBuilder.PreviewActionsChip() {
    ActionsPage(initialPageIndex = 2)
}

@ViewComposePreview(name = "Actions · List items", group = "Demo/Pages")
internal fun UiTreeBuilder.PreviewActionsListItems() {
    ActionsPage(initialPageIndex = 3)
}

internal fun UiTreeBuilder.ActionsPage(
    initialPageIndex: Int = 0,
) {
    val selectedPageState = remember { mutableStateOf(initialPageIndex.coerceIn(0, 3)) }
    val cardClickState = remember { mutableStateOf(0) }
    val chipSelectedState = remember { mutableStateOf(false) }
    val fabClickState = remember { mutableStateOf(0) }
    val benchmarkState = remember { mutableStateOf(false) }

    val pageItems = when (selectedPageState.value) {
        0 -> listOf("benchmark", "page", "page_filter", "cards", "verify")
        1 -> listOf("page", "page_filter", "fab", "verify")
        2 -> listOf("page", "page_filter", "chips", "verify")
        else -> listOf("page", "page_filter", "list_items", "verify")
    }

    LazyColumn(
        items = pageItems,
        key = { it },
        modifier = Modifier.fillMaxSize(),
    ) { section ->
        when (section) {
            "page" -> ChapterPageOverviewSection(
                title = "Action 组件",
                goal = "验证 Card、FAB、Chip、TextButton、ListItem、Badge 等 Action 类组件的渲染、交互状态和主题适配。",
                modules = listOf("Card", "FloatingActionButton", "Chip", "TextButton", "ListItem", "Badge"),
            )

            "page_filter" -> ChapterPageFilterSection(
                pages = listOf("卡片", "FAB", "Chip", "列表项"),
                selectedIndex = selectedPageState.value,
                onSelectionChange = { selectedPageState.value = it },
            )

            "benchmark" -> ScenarioSection(
                kind = ScenarioKind.Benchmark,
                title = "Action 组件 Benchmark 锚点",
                subtitle = "Card variant 切换和 Chip selected 态切换的稳定路径。",
            ) {
                BenchmarkRouteCallout(
                    route = "Catalog -> Actions -> 卡片页 -> Benchmark 锚点",
                    stableTargets = listOf(
                        "Card variant toggle",
                        "Chip selected toggle",
                    ),
                )
                Button(
                    text = if (benchmarkState.value) "Benchmark 已展开" else "Benchmark 已收起",
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { benchmarkState.value = !benchmarkState.value },
                )
                Button(
                    text = "重置 Benchmark",
                    variant = ButtonVariant.Outlined,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { benchmarkState.value = false },
                )
            }

            "cards" -> ScenarioSection(
                kind = ScenarioKind.Core,
                title = "Card 卡片",
                subtitle = "Card 提供三种变体：Filled、Elevated、Outlined，支持点击和禁用态。",
            ) {
                Text(
                    text = "点击次数: ${cardClickState.value}",
                    style = UiTextStyle(fontSizeSp = 13.sp),
                    color = TextDefaults.secondaryColor(),
                    modifier = Modifier.margin(bottom = 8.dp),
                )
                Card(
                    variant = CardVariant.Filled,
                    onClick = { cardClickState.value += 1 },
                    modifier = Modifier
                        .fillMaxWidth()
                        .margin(bottom = 12.dp),
                ) {
                    Column(
                        spacing = 4.dp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                    ) {
                        Text(
                            text = "填充卡片 (Filled)",
                            style = UiTextStyle(fontSizeSp = 16.sp),
                        )
                        Text(
                            text = "默认变体，使用 Surface 背景色填充",
                            style = UiTextStyle(fontSizeSp = 13.sp),
                            color = TextDefaults.secondaryColor(),
                        )
                    }
                }
                ElevatedCard(
                    onClick = { cardClickState.value += 1 },
                    modifier = Modifier
                        .fillMaxWidth()
                        .margin(bottom = 12.dp)
                        .testTag(DemoTestTags.ACTIONS_ELEVATED_CARD),
                ) {
                    Column(
                        spacing = 4.dp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                    ) {
                        Text(
                            text = "高程卡片 (Elevated)",
                            style = UiTextStyle(fontSizeSp = 16.sp),
                        )
                        Text(
                            text = "带阴影的卡片，自动提升 elevation",
                            style = UiTextStyle(fontSizeSp = 13.sp),
                            color = TextDefaults.secondaryColor(),
                        )
                    }
                }
                OutlinedCard(
                    onClick = { cardClickState.value += 1 },
                    modifier = Modifier
                        .fillMaxWidth()
                        .margin(bottom = 12.dp),
                ) {
                    Column(
                        spacing = 4.dp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                    ) {
                        Text(
                            text = "边框卡片 (Outlined)",
                            style = UiTextStyle(fontSizeSp = 16.sp),
                        )
                        Text(
                            text = "带边框无填充，适合次要内容",
                            style = UiTextStyle(fontSizeSp = 13.sp),
                            color = TextDefaults.secondaryColor(),
                        )
                    }
                }
                Card(
                    variant = CardVariant.Filled,
                    enabled = false,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        spacing = 4.dp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                    ) {
                        Text(
                            text = "禁用卡片",
                            style = UiTextStyle(fontSizeSp = 16.sp),
                        )
                        Text(
                            text = "enabled = false，不可点击",
                            style = UiTextStyle(fontSizeSp = 13.sp),
                            color = TextDefaults.secondaryColor(),
                        )
                    }
                }
                Row(
                    spacing = 8.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .margin(top = 12.dp),
                ) {
                    TextButton(
                        text = "TextButton 标准",
                        onClick = {},
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(
                        text = "TextButton 禁用",
                        onClick = {},
                        enabled = false,
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            "fab" -> ScenarioSection(
                kind = ScenarioKind.Core,
                title = "FloatingActionButton",
                subtitle = "FAB 提供三种尺寸：Small、Medium、Large，以及带文字的 ExtendedFAB。",
            ) {
                Text(
                    text = "FAB 点击次数: ${fabClickState.value}",
                    style = UiTextStyle(fontSizeSp = 13.sp),
                    color = TextDefaults.secondaryColor(),
                    modifier = Modifier.margin(bottom = 12.dp),
                )
                Row(
                    spacing = 16.dp,
                    verticalAlignment = VerticalAlignment.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .margin(bottom = 12.dp),
                ) {
                    FloatingActionButton(
                        onClick = { fabClickState.value += 1 },
                        size = FabSize.Small,
                    ) {
                        Icon(
                            source = ImageSource.Resource(R.drawable.demo_media_icon),
                            contentDescription = "小型 FAB",
                        )
                    }
                    FloatingActionButton(
                        onClick = { fabClickState.value += 1 },
                        size = FabSize.Medium,
                    ) {
                        Icon(
                            source = ImageSource.Resource(R.drawable.demo_media_icon),
                            contentDescription = "中型 FAB",
                        )
                    }
                    FloatingActionButton(
                        onClick = { fabClickState.value += 1 },
                        size = FabSize.Large,
                    ) {
                        Icon(
                            source = ImageSource.Resource(R.drawable.demo_media_icon),
                            contentDescription = "大型 FAB",
                        )
                    }
                }
                Text(
                    text = "Small / Medium / Large 三种尺寸对比",
                    style = UiTextStyle(fontSizeSp = 13.sp),
                    color = TextDefaults.secondaryColor(),
                    modifier = Modifier.margin(bottom = 12.dp),
                )
                ExtendedFloatingActionButton(
                    text = "新建项目",
                    onClick = { fabClickState.value += 1 },
                    icon = ImageSource.Resource(R.drawable.demo_media_icon),
                    modifier = Modifier.margin(bottom = 12.dp),
                )
                ExtendedFloatingActionButton(
                    text = "自定义颜色",
                    onClick = { fabClickState.value += 1 },
                    icon = ImageSource.Resource(R.drawable.demo_media_icon),
                    containerColor = Theme.colors.secondary,
                    contentColor = Theme.colors.background,
                )
            }

            "chips" -> ScenarioSection(
                kind = ScenarioKind.Core,
                title = "Chip 标签",
                subtitle = "Chip 有四种变体：Assist、Filter、Input、Suggestion，支持选中态和图标。",
            ) {
                Text(
                    text = "四种变体",
                    style = UiTextStyle(fontSizeSp = 14.sp),
                    modifier = Modifier.margin(bottom = 8.dp),
                )
                FlowRow(
                    horizontalSpacing = 8.dp,
                    verticalSpacing = 8.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .margin(bottom = 12.dp),
                ) {
                    Chip(
                        label = "辅助标签",
                        onClick = {},
                        variant = ChipVariant.Assist,
                        leadingIcon = ImageSource.Resource(R.drawable.demo_media_icon),
                    )
                    Chip(
                        label = "筛选标签",
                        onClick = { chipSelectedState.value = !chipSelectedState.value },
                        variant = ChipVariant.Filter,
                        selected = chipSelectedState.value,
                        modifier = Modifier.testTag(DemoTestTags.ACTIONS_CHIP_FILTER),
                    )
                    Chip(
                        label = "输入标签",
                        onClick = {},
                        variant = ChipVariant.Input,
                        onTrailingIconClick = {},
                    )
                    Chip(
                        label = "建议标签",
                        onClick = {},
                        variant = ChipVariant.Suggestion,
                    )
                }
                Divider(modifier = Modifier.margin(bottom = 12.dp))
                Text(
                    text = "选中与禁用状态",
                    style = UiTextStyle(fontSizeSp = 14.sp),
                    modifier = Modifier.margin(bottom = 8.dp),
                )
                FlowRow(
                    horizontalSpacing = 8.dp,
                    verticalSpacing = 8.dp,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Chip(
                        label = "已选中",
                        onClick = {},
                        variant = ChipVariant.Filter,
                        selected = true,
                    )
                    Chip(
                        label = "未选中",
                        onClick = {},
                        variant = ChipVariant.Filter,
                        selected = false,
                    )
                    Chip(
                        label = "禁用",
                        onClick = {},
                        variant = ChipVariant.Assist,
                        enabled = false,
                    )
                    Chip(
                        label = "选中+禁用",
                        onClick = {},
                        variant = ChipVariant.Filter,
                        selected = true,
                        enabled = false,
                    )
                }
            }

            "list_items" -> ScenarioSection(
                kind = ScenarioKind.Core,
                title = "ListItem 列表项 + Badge 徽标",
                subtitle = "ListItem 提供标准化的列表行结构，Badge/BadgedBox 提供数字或圆点徽标。",
            ) {
                ListItem(
                    headlineText = "单行列表项",
                    modifier = Modifier.margin(bottom = 8.dp),
                )
                ListItem(
                    headlineText = "带副标题列表项",
                    supportingText = "这是支撑文本，用于补充说明",
                    modifier = Modifier.margin(bottom = 8.dp),
                )
                ListItem(
                    headlineText = "完整列表项",
                    supportingText = "包含上方标注、前导图标和尾部内容",
                    overlineText = "分类标注",
                    leadingContent = {
                        Icon(
                            source = ImageSource.Resource(R.drawable.demo_media_icon),
                            contentDescription = "列表图标",
                        )
                    },
                    trailingContent = {
                        Text(
                            text = "详情",
                            style = UiTextStyle(fontSizeSp = 13.sp),
                            color = TextDefaults.secondaryColor(),
                        )
                    },
                    modifier = Modifier.margin(bottom = 8.dp),
                )
                ListItem(
                    headlineText = "可点击列表项",
                    supportingText = "点击触发交互",
                    onClick = { cardClickState.value += 1 },
                    leadingContent = {
                        Icon(
                            source = ImageSource.Resource(R.drawable.demo_media_icon),
                            contentDescription = "点击图标",
                        )
                    },
                    trailingContent = {
                        BadgedBox(
                            badge = { Badge(count = 5) },
                        ) {
                            Icon(
                                source = ImageSource.Resource(R.drawable.demo_media_icon),
                                contentDescription = "带徽标图标",
                            )
                        }
                    },
                    modifier = Modifier.margin(bottom = 8.dp),
                )
                Divider(modifier = Modifier.margin(vertical = 8.dp))
                Text(
                    text = "Badge 徽标独立展示",
                    style = UiTextStyle(fontSizeSp = 14.sp),
                    modifier = Modifier.margin(bottom = 8.dp),
                )
                Row(
                    spacing = 24.dp,
                    verticalAlignment = VerticalAlignment.Center,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(spacing = 4.dp) {
                        BadgedBox(
                            badge = { Badge(count = 99) },
                        ) {
                            Icon(
                                source = ImageSource.Resource(R.drawable.demo_media_icon),
                                contentDescription = "数字徽标",
                            )
                        }
                        Text(
                            text = "数字",
                            style = UiTextStyle(fontSizeSp = 12.sp),
                            color = TextDefaults.secondaryColor(),
                        )
                    }
                    Column(spacing = 4.dp) {
                        BadgedBox(
                            badge = { Badge() },
                        ) {
                            Icon(
                                source = ImageSource.Resource(R.drawable.demo_media_icon),
                                contentDescription = "圆点徽标",
                            )
                        }
                        Text(
                            text = "圆点",
                            style = UiTextStyle(fontSizeSp = 12.sp),
                            color = TextDefaults.secondaryColor(),
                        )
                    }
                    Column(spacing = 4.dp) {
                        BadgedBox(
                            badge = {
                                Badge(
                                    count = 3,
                                    containerColor = Theme.colors.secondary,
                                )
                            },
                        ) {
                            IconButton(
                                icon = ImageSource.Resource(R.drawable.demo_media_icon),
                                contentDescription = "自定义颜色徽标",
                                onClick = {},
                            )
                        }
                        Text(
                            text = "自定义色",
                            style = UiTextStyle(fontSizeSp = 12.sp),
                            color = TextDefaults.secondaryColor(),
                        )
                    }
                }
            }

            else -> VerificationNotesSection(
                what = "Action 组件应验证 Card/FAB/Chip/ListItem/Badge 的渲染、交互和主题适配。",
                howToVerify = listOf(
                    "点击三种 Card 变体，确认点击次数正确累加，禁用卡片不可点击。",
                    "点击三种尺寸 FAB 和 ExtendedFAB，确认点击计数和自定义颜色生效。",
                    "点击 Filter Chip 切换选中态，确认视觉状态正确切换。",
                    "观察 ListItem 的各属性组合，确认 overlineText、leadingContent、trailingContent 正常渲染。",
                    "确认 Badge 的数字徽标和圆点徽标正常显示。",
                ),
                expected = listOf(
                    "Card 三种变体视觉差异明显（填充/阴影/边框）。",
                    "FAB 三种尺寸递增，ExtendedFAB 图标+文字水平排列。",
                    "Chip 四种变体样式各异，禁用态降低透明度。",
                    "ListItem 各属性组合布局稳定，Badge 定位准确。",
                ),
            )
        }
    }
}
