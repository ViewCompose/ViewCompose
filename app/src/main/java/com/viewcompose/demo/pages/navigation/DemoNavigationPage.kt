package com.viewcompose

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
import com.viewcompose.ui.modifier.testTag
import com.viewcompose.ui.node.ImageSource
import com.viewcompose.runtime.mutableStateOf
import com.viewcompose.widget.core.Badge
import com.viewcompose.widget.core.BadgedBox
import com.viewcompose.widget.core.BottomAppBar
import com.viewcompose.widget.core.Box
import com.viewcompose.widget.core.Button
import com.viewcompose.widget.core.ButtonVariant
import com.viewcompose.widget.core.Column
import com.viewcompose.widget.core.ExtendedFloatingActionButton
import com.viewcompose.widget.core.FloatingActionButton
import com.viewcompose.widget.core.Icon
import com.viewcompose.widget.core.IconButton
import com.viewcompose.widget.core.LazyColumn
import com.viewcompose.widget.core.NavigationBar
import com.viewcompose.widget.core.Row
import com.viewcompose.widget.core.Scaffold
import com.viewcompose.widget.core.SurfaceDefaults
import com.viewcompose.widget.core.Text
import com.viewcompose.widget.core.TextDefaults
import com.viewcompose.widget.core.Theme
import com.viewcompose.widget.core.TopAppBar
import com.viewcompose.widget.core.UiTextStyle
import com.viewcompose.widget.core.UiTreeBuilder
import com.viewcompose.ui.unit.dp
import com.viewcompose.widget.core.remember
import com.viewcompose.ui.unit.sp

@ViewComposePreview(name = "Navigation · App bar", group = "Demo/Pages")
internal fun UiTreeBuilder.PreviewNavigationAppBar() {
    NavigationPage(initialPageIndex = 0)
}

@ViewComposePreview(name = "Navigation · Navigation bar", group = "Demo/Pages")
internal fun UiTreeBuilder.PreviewNavigationBar() {
    NavigationPage(initialPageIndex = 1)
}

@ViewComposePreview(name = "Navigation · Scaffold", group = "Demo/Pages")
internal fun UiTreeBuilder.PreviewNavigationScaffold() {
    NavigationPage(initialPageIndex = 2)
}

internal fun UiTreeBuilder.NavigationPage(
    initialPageIndex: Int = 0,
) {
    val selectedPageState = remember { mutableStateOf(initialPageIndex.coerceIn(0, 2)) }
    val navSelectedState = remember { mutableStateOf(0) }
    val benchmarkState = remember { mutableStateOf(false) }

    val pageItems = when (selectedPageState.value) {
        0 -> listOf("benchmark", "page", "page_filter", "appbar", "verify")
        1 -> listOf("page", "page_filter", "navbar", "verify")
        else -> listOf("page", "page_filter", "scaffold", "verify")
    }

    LazyColumn(
        items = pageItems,
        key = { it },
        modifier = Modifier.fillMaxSize(),
    ) { section ->
        when (section) {
            "page" -> ChapterPageOverviewSection(
                title = "导航组件",
                goal = "验证 TopAppBar、BottomAppBar、NavigationBar、Scaffold 的渲染和交互。",
                modules = listOf("TopAppBar", "BottomAppBar", "NavigationBar", "Scaffold"),
            )

            "page_filter" -> ChapterPageFilterSection(
                pages = listOf("AppBar", "NavigationBar", "Scaffold"),
                selectedIndex = selectedPageState.value,
                onSelectionChange = { selectedPageState.value = it },
            )

            "benchmark" -> ScenarioSection(
                kind = ScenarioKind.Benchmark,
                title = "导航组件 Benchmark 锚点",
                subtitle = "NavigationBar selectedIndex 切换的稳定路径。",
            ) {
                BenchmarkRouteCallout(
                    route = "Catalog -> Navigation -> AppBar 页 -> Benchmark 锚点",
                    stableTargets = listOf(
                        "NavigationBar selection toggle",
                        "Scaffold content refresh",
                    ),
                )
                Button(
                    text = if (benchmarkState.value) "Benchmark 已展开" else "Benchmark 已收起",
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { benchmarkState.value = !benchmarkState.value },
                )
            }

            "appbar" -> ScenarioSection(
                kind = ScenarioKind.Core,
                title = "TopAppBar + BottomAppBar",
                subtitle = "TopAppBar 提供标题、导航图标和 actions 插槽。BottomAppBar 提供底部操作栏。",
            ) {
                Text(
                    text = "TopAppBar 标准",
                    style = UiTextStyle(fontSizeSp = 14.sp),
                    modifier = Modifier.margin(bottom = 8.dp),
                )
                TopAppBar(
                    title = "页面标题",
                    navigationIcon = {
                        IconButton(
                            icon = ImageSource.Resource(R.drawable.demo_media_icon),
                            contentDescription = "返回",
                            onClick = {},
                        )
                    },
                    actions = {
                        IconButton(
                            icon = ImageSource.Resource(R.drawable.demo_media_icon),
                            contentDescription = "搜索",
                            onClick = {},
                        )
                        IconButton(
                            icon = ImageSource.Resource(R.drawable.demo_media_icon),
                            contentDescription = "更多",
                            onClick = {},
                        )
                    },
                    modifier = Modifier.margin(bottom = 12.dp),
                )
                Text(
                    text = "TopAppBar 仅标题",
                    style = UiTextStyle(fontSizeSp = 14.sp),
                    modifier = Modifier.margin(bottom = 8.dp),
                )
                TopAppBar(
                    title = "简洁标题",
                    modifier = Modifier.margin(bottom = 12.dp),
                )
                Text(
                    text = "BottomAppBar",
                    style = UiTextStyle(fontSizeSp = 14.sp),
                    modifier = Modifier.margin(bottom = 8.dp),
                )
                BottomAppBar {
                    IconButton(
                        icon = ImageSource.Resource(R.drawable.demo_media_icon),
                        contentDescription = "首页",
                        onClick = {},
                    )
                    IconButton(
                        icon = ImageSource.Resource(R.drawable.demo_media_icon),
                        contentDescription = "收藏",
                        onClick = {},
                    )
                    IconButton(
                        icon = ImageSource.Resource(R.drawable.demo_media_icon),
                        contentDescription = "设置",
                        onClick = {},
                    )
                }
            }

            "navbar" -> ScenarioSection(
                kind = ScenarioKind.Core,
                title = "NavigationBar 底部导航",
                subtitle = "NavigationBar 提供图标 + 标签的标准底部导航，支持 badge 和颜色自定义。",
            ) {
                Text(
                    text = "当前选中: ${navSelectedState.value}",
                    style = UiTextStyle(fontSizeSp = 13.sp),
                    color = TextDefaults.secondaryColor(),
                    modifier = Modifier
                        .margin(bottom = 8.dp)
                        .testTag(DemoTestTags.NAVIGATION_SELECTED_SUMMARY),
                )
                NavigationBar(
                    selectedIndex = navSelectedState.value,
                    onItemSelected = { navSelectedState.value = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .margin(bottom = 16.dp)
                        .testTag(DemoTestTags.NAVIGATION_BAR_PRIMARY),
                ) {
                    Item(
                        label = "首页",
                        icon = ImageSource.Resource(R.drawable.demo_media_icon),
                    )
                    Item(
                        label = "搜索",
                        icon = ImageSource.Resource(R.drawable.demo_media_icon),
                    )
                    Item(
                        label = "消息",
                        icon = ImageSource.Resource(R.drawable.demo_media_icon),
                        badgeCount = 3,
                    )
                    Item(
                        label = "我的",
                        icon = ImageSource.Resource(R.drawable.demo_media_icon),
                    )
                }
                Text(
                    text = "自定义颜色 NavigationBar",
                    style = UiTextStyle(fontSizeSp = 14.sp),
                    modifier = Modifier.margin(bottom = 8.dp),
                )
                NavigationBar(
                    selectedIndex = navSelectedState.value,
                    onItemSelected = { navSelectedState.value = it },
                    containerColor = Theme.colors.surfaceVariant,
                    selectedIconColor = Theme.colors.secondary,
                    selectedLabelColor = Theme.colors.secondary,
                    indicatorColor = Theme.colors.background,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Item(
                        label = "动态",
                        icon = ImageSource.Resource(R.drawable.demo_media_icon),
                    )
                    Item(
                        label = "发现",
                        icon = ImageSource.Resource(R.drawable.demo_media_icon),
                        badgeCount = 12,
                    )
                    Item(
                        label = "通知",
                        icon = ImageSource.Resource(R.drawable.demo_media_icon),
                    )
                }
            }

            "scaffold" -> ScenarioSection(
                kind = ScenarioKind.Core,
                title = "Scaffold 脚手架集成",
                subtitle = "Scaffold 组合 TopAppBar + NavigationBar + FAB + 内容区，构成完整页面结构。",
            ) {
                Scaffold(
                    topBar = {
                        TopAppBar(
                            title = "Scaffold 演示",
                            navigationIcon = {
                                IconButton(
                                    icon = ImageSource.Resource(R.drawable.demo_media_icon),
                                    contentDescription = "返回",
                                    onClick = {},
                                )
                            },
                        )
                    },
                    bottomBar = {
                        NavigationBar(
                            selectedIndex = navSelectedState.value,
                            onItemSelected = { navSelectedState.value = it },
                        ) {
                            Item(
                                label = "首页",
                                icon = ImageSource.Resource(R.drawable.demo_media_icon),
                            )
                            Item(
                                label = "消息",
                                icon = ImageSource.Resource(R.drawable.demo_media_icon),
                                badgeCount = 5,
                            )
                            Item(
                                label = "我的",
                                icon = ImageSource.Resource(R.drawable.demo_media_icon),
                            )
                        }
                    },
                    floatingActionButton = {
                        FloatingActionButton(onClick = {}) {
                            Icon(
                                source = ImageSource.Resource(R.drawable.demo_media_icon),
                                contentDescription = "添加",
                            )
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(400.dp)
                        .testTag(DemoTestTags.NAVIGATION_SCAFFOLD),
                ) {
                    Column(
                        spacing = 8.dp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                    ) {
                        Text(
                            text = "这是 Scaffold 的内容区域",
                            style = UiTextStyle(fontSizeSp = 16.sp),
                        )
                        Text(
                            text = "当前导航项: ${navSelectedState.value}",
                            style = UiTextStyle(fontSizeSp = 13.sp),
                            color = TextDefaults.secondaryColor(),
                        )
                        Text(
                            text = "TopAppBar + NavigationBar + FAB 组合展示完整页面结构。Scaffold 自动处理各组件的定位。",
                            style = UiTextStyle(fontSizeSp = 13.sp),
                            color = TextDefaults.secondaryColor(),
                        )
                    }
                }
            }

            else -> VerificationNotesSection(
                what = "导航组件应验证 TopAppBar、BottomAppBar、NavigationBar、Scaffold 的渲染和交互。",
                howToVerify = listOf(
                    "确认 TopAppBar 的导航图标和 actions 按钮正常显示和响应点击。",
                    "切换 NavigationBar 的选中项，确认选中态指示器和颜色正确变化。",
                    "确认带 badge 的导航项正确显示数字徽标。",
                    "观察 Scaffold 集成页面，确认 TopAppBar、NavigationBar、FAB 和内容区的布局稳定。",
                ),
                expected = listOf(
                    "TopAppBar 导航图标和 actions 水平排列，标题居中或居左。",
                    "NavigationBar 选中态有指示器高亮，badge 定位准确。",
                    "Scaffold 内各组件不重叠，FAB 悬浮在内容区之上。",
                ),
            )
        }
    }
}
