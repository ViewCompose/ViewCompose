package com.viewcompose

import com.viewcompose.preview.tooling.ViewComposePreview
import com.viewcompose.ui.layout.BoxAlignment
import com.viewcompose.ui.layout.HorizontalAlignment
import com.viewcompose.ui.layout.MainAxisArrangement
import com.viewcompose.ui.layout.VerticalAlignment
import com.viewcompose.ui.modifier.Modifier
import com.viewcompose.ui.modifier.shape
import com.viewcompose.ui.modifier.backgroundColor
import com.viewcompose.ui.modifier.clickable
import com.viewcompose.ui.modifier.cornerRadius
import com.viewcompose.ui.modifier.fillMaxSize
import com.viewcompose.ui.modifier.fillMaxWidth
import com.viewcompose.ui.modifier.height
import com.viewcompose.ui.modifier.layoutId
import com.viewcompose.ui.modifier.margin
import com.viewcompose.ui.modifier.offset
import com.viewcompose.ui.modifier.padding
import com.viewcompose.ui.modifier.testTag
import com.viewcompose.ui.modifier.visibility
import com.viewcompose.ui.modifier.zIndex
import com.viewcompose.ui.modifier.Visibility
import com.viewcompose.ui.node.ImageSource
import com.viewcompose.ui.node.spec.ConstraintChainStyle
import com.viewcompose.ui.node.spec.ConstraintDimension
import com.viewcompose.ui.node.spec.ConstraintHelperVisibility
import com.viewcompose.runtime.mutableStateOf
import com.viewcompose.widget.constraintlayout.*
import com.viewcompose.widget.core.Box
import com.viewcompose.widget.core.Button
import com.viewcompose.widget.core.ButtonSize
import com.viewcompose.widget.core.ButtonVariant
import com.viewcompose.widget.core.Column
import com.viewcompose.widget.core.Divider
import com.viewcompose.widget.core.FlowColumn
import com.viewcompose.widget.core.FlowRow
import com.viewcompose.widget.core.Icon
import com.viewcompose.widget.core.LazyColumn
import com.viewcompose.widget.core.Row
import com.viewcompose.widget.core.ScrollableColumn
import com.viewcompose.widget.core.ScrollableRow
import com.viewcompose.widget.core.Surface
import com.viewcompose.widget.core.SurfaceDefaults
import com.viewcompose.widget.core.SurfaceVariant
import com.viewcompose.widget.core.Text
import com.viewcompose.widget.core.TextDefaults
import com.viewcompose.widget.core.Theme
import com.viewcompose.widget.core.UiTextStyle
import com.viewcompose.widget.core.UiTreeBuilder
import com.viewcompose.ui.unit.dp
import com.viewcompose.widget.core.remember
import com.viewcompose.ui.unit.sp

@ViewComposePreview(name = "Layouts · Linear", group = "Demo/Pages", heightDp = 891)
internal fun UiTreeBuilder.PreviewLayoutsLinear() {
    LayoutPage(initialPageIndex = 0)
}

@ViewComposePreview(name = "Layouts · Overlay", group = "Demo/Pages", heightDp = 891)
internal fun UiTreeBuilder.PreviewLayoutsOverlay() {
    LayoutPage(initialPageIndex = 1)
}

@ViewComposePreview(name = "Layouts · Bounds", group = "Demo/Pages", heightDp = 891)
internal fun UiTreeBuilder.PreviewLayoutsBounds() {
    LayoutPage(initialPageIndex = 2)
}

@ViewComposePreview(name = "Layouts · Flow", group = "Demo/Pages", heightDp = 891)
internal fun UiTreeBuilder.PreviewLayoutsFlow() {
    LayoutPage(initialPageIndex = 3)
}

@ViewComposePreview(name = "Layouts · Scroll", group = "Demo/Pages", heightDp = 891)
internal fun UiTreeBuilder.PreviewLayoutsScroll() {
    LayoutPage(initialPageIndex = 4)
}

@ViewComposePreview(name = "Layouts · Constraint", group = "Demo/Pages", heightDp = 891)
internal fun UiTreeBuilder.PreviewLayoutsConstraint() {
    LayoutPage(initialPageIndex = 5)
}

@ViewComposePreview(name = "Layouts · Checklist", group = "Demo/Pages", heightDp = 891)
internal fun UiTreeBuilder.PreviewLayoutsChecklist() {
    LayoutPage(initialPageIndex = 6)
}

internal fun UiTreeBuilder.LayoutPage(
    initialPageIndex: Int = 0,
) {
    val boxTapState = remember { mutableStateOf(0) }
    val benchmarkState = remember { mutableStateOf(false) }
    val useLongLabelsState = remember { mutableStateOf(false) }
    val flowItemCountState = remember { mutableStateOf(8) }
    val constraintHelperLongState = remember { mutableStateOf(false) }
    val constraintSetExpandedState = remember { mutableStateOf(false) }
    val constraintDimensionAdvancedState = remember { mutableStateOf(false) }
    val constraintHelpersFullState = remember { mutableStateOf(false) }
    val constraintVerticalChainPackedState = remember { mutableStateOf(false) }
    val constraintSetHelpersAlternateState = remember { mutableStateOf(false) }
    val constraintVirtualAlternateState = remember { mutableStateOf(false) }
    val selectedPageState = remember { mutableStateOf(initialPageIndex.coerceIn(0, 6)) }
    val pageItems = when (selectedPageState.value) {
        0 -> listOf("benchmark", "page", "page_filter", "row", "column", "verify")
        1 -> listOf("page", "page_filter", "box", "verify")
        2 -> listOf("page", "page_filter", "edge", "verify")
        3 -> listOf("page", "page_filter", "flow", "verify")
        4 -> listOf("page", "page_filter", "scrollable", "verify")
        5 -> listOf(
            "page",
            "page_filter",
            "constraint_basic",
            "constraint_helpers",
            "constraint_chain",
            "constraint_set",
            "constraint_virtual_helpers",
            "constraint_anchor_advanced",
            "constraint_dimension_advanced",
            "constraint_helpers_full",
            "constraint_vertical_chain",
            "constraint_set_helpers_mirror",
            "verify",
        )
        else -> listOf("page", "page_filter", "verify")
    }
    LazyColumn(
        items = pageItems,
        key = { it },
        modifier = Modifier.fillMaxSize(),
    ) { section ->
        when (section) {
            "page" -> ChapterPageOverviewSection(
                title = "布局组件",
                goal = "验证线性容器、Box 叠加、流式布局和滚动容器的测量、摆放、间距和子项覆盖的稳定性。",
                modules = listOf(
                    "DeclarativeLinearLayout",
                    "DeclarativeBoxLayout",
                    "FlowRow",
                    "FlowColumn",
                    "ScrollableColumn",
                    "ScrollableRow",
                    "DeclarativeConstraintLayout",
                ),
            )

            "page_filter" -> ChapterPageFilterSection(
                pages = listOf("线性", "叠加", "边界", "流式", "滚动", "约束", "清单"),
                selectedIndex = selectedPageState.value,
                onSelectionChange = { selectedPageState.value = it },
            )

            "benchmark" -> ScenarioSection(
                kind = ScenarioKind.Benchmark,
                title = "布局 Benchmark 锚点",
                subtitle = "线性布局 benchmark 路径，不依赖分页过滤器。",
            ) {
                BenchmarkRouteCallout(
                    route = "Launcher -> Layouts -> 线性页 -> Benchmark 锚点",
                    stableTargets = listOf(
                        "Layouts Benchmark Compact / Expanded",
                        "Reset Layouts Benchmark",
                    ),
                )
                Button(
                    text = if (benchmarkState.value) "布局 Benchmark 已展开" else "布局 Benchmark 已收起",
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(DemoTestTags.LAYOUTS_BENCHMARK_TOGGLE),
                    onClick = { benchmarkState.value = !benchmarkState.value },
                )
                Row(
                    spacing = 8.dp,
                    verticalAlignment = VerticalAlignment.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .backgroundColor(SurfaceDefaults.backgroundColor())
                        .shape(SurfaceDefaults.shape())
                        .padding(12.dp),
                ) {
                    Text(text = "前导")
                    Button(
                        text = if (benchmarkState.value) {
                            "展开的 benchmark 标签，应该保持兄弟布局稳定"
                        } else {
                            "紧凑"
                        },
                        modifier = Modifier.weight(1f),
                    )
                    Button(
                        text = "重置",
                        variant = ButtonVariant.Outlined,
                        modifier = Modifier.testTag(DemoTestTags.LAYOUTS_BENCHMARK_RESET),
                        onClick = { benchmarkState.value = false },
                    )
                }
            }

            "row" -> ScenarioSection(
                kind = ScenarioKind.Core,
                title = "Row + Spacer + 交叉轴对齐",
                subtitle = "自定义线性布局支持 spacing、arrangement 和子级交叉轴覆盖。",
            ) {
                Row(
                    arrangement = MainAxisArrangement.Start,
                    verticalAlignment = VerticalAlignment.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .backgroundColor(SurfaceDefaults.backgroundColor())
                        .padding(12.dp),
                ) {
                    Text(
                        text = "顶部",
                        modifier = Modifier
                            .align(VerticalAlignment.Top)
                            .backgroundColor(Theme.colors.surfaceVariant)
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                    )
                    FlexibleSpacer()
                    Text(
                        text = "底部",
                        modifier = Modifier
                            .align(VerticalAlignment.Bottom)
                            .backgroundColor(Theme.colors.secondary)
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                    )
                }
            }

            "box" -> ScenarioSection(
                kind = ScenarioKind.Visual,
                title = "Box 叠加",
                subtitle = "默认对齐、子级覆盖、offset 和 zIndex 在同一容器中协同工作。",
            ) {
                Box(
                    contentAlignment = BoxAlignment.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp)
                        .backgroundColor(SurfaceDefaults.variantBackgroundColor())
                        .shape(SurfaceDefaults.shape())
                        .clickable { boxTapState.value = boxTapState.value + 1 }
                        .padding(12.dp),
                ) {
                    Text(
                        text = "居中内容 · 点击 ${boxTapState.value}",
                        modifier = Modifier
                            .backgroundColor(Theme.colors.primary)
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                    )
                    Text(
                        text = "固定标签",
                        modifier = Modifier
                            .align(BoxAlignment.BottomEnd)
                            .offset(x = (-8).dp, y = (-8).dp)
                            .zIndex(1f)
                            .backgroundColor(Theme.colors.secondary)
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                    )
                }
            }

            "column" -> ScenarioSection(
                kind = ScenarioKind.Core,
                title = "Column 排列",
                subtitle = "主轴 arrangement 和 Divider 在自定义线性容器中的稳定性。",
            ) {
                Column(
                    arrangement = MainAxisArrangement.SpaceEvenly,
                    horizontalAlignment = HorizontalAlignment.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                        .backgroundColor(SurfaceDefaults.backgroundColor())
                        .padding(12.dp),
                ) {
                    Text(text = "一")
                    Divider()
                    Text(text = "二")
                    Divider()
                    Text(text = "三")
                }
            }

            "edge" -> ScenarioSection(
                kind = ScenarioKind.Stress,
                title = "布局边界用例",
                subtitle = "wrap、weight 和嵌套容器 sizing 的极端组合。",
            ) {
                BenchmarkRouteCallout(
                    route = "Catalog -> Layouts -> 边界页",
                    stableTargets = listOf("长标签 / 短标签", "Weighted", "Action", "Wrap"),
                )
                Button(
                    text = if (useLongLabelsState.value) "使用短标签" else "使用长标签",
                    modifier = Modifier
                        .margin(bottom = 12.dp)
                        .testTag(DemoTestTags.LAYOUTS_EDGE_TOGGLE),
                    onClick = { useLongLabelsState.value = !useLongLabelsState.value },
                )
                Row(
                    spacing = 8.dp,
                    verticalAlignment = VerticalAlignment.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .backgroundColor(SurfaceDefaults.backgroundColor())
                        .shape(SurfaceDefaults.shape())
                        .padding(12.dp)
                        .margin(bottom = 12.dp),
                ) {
                    Surface(modifier = Modifier.padding(8.dp)) {
                        Icon(
                            source = ImageSource.Resource(R.drawable.demo_media_icon),
                            contentDescription = "布局探测图标",
                            modifier = Modifier.testTag(DemoTestTags.LAYOUTS_EDGE_PROBE_ICON),
                        )
                    }
                    Button(
                        text = if (useLongLabelsState.value) {
                            "一个很长的 weighted 标签，应该在不破坏兄弟布局的情况下换行"
                        } else {
                            "Weighted"
                        },
                        modifier = Modifier
                            .weight(1f)
                            .testTag(DemoTestTags.LAYOUTS_EDGE_WEIGHTED),
                    )
                    Button(
                        text = "操作",
                        variant = ButtonVariant.Outlined,
                        modifier = Modifier
                            .weight(1f)
                            .testTag(DemoTestTags.LAYOUTS_EDGE_ACTION),
                    )
                }
                Row(
                    spacing = 8.dp,
                    verticalAlignment = VerticalAlignment.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .backgroundColor(SurfaceDefaults.variantBackgroundColor())
                        .shape(SurfaceDefaults.shape())
                        .padding(12.dp),
                ) {
                    Surface(modifier = Modifier.padding(8.dp)) {
                        Text(text = "Wrap")
                    }
                    Surface(
                        variant = SurfaceVariant.Variant,
                        modifier = Modifier.padding(8.dp),
                    ) {
                        Text(text = "仍然 Wrap")
                    }
                    Text(
                        text = "嵌套的 Surface 应该紧贴内容，把剩余宽度留给这段文本。",
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            "flow" -> ScenarioSection(
                kind = ScenarioKind.Core,
                title = "流式布局",
                subtitle = "FlowRow 实现标签云，FlowColumn 实现竖向流。支持 spacing 和 maxItemsInEachRow/Column。",
            ) {
                Text(
                    text = "FlowRow 标签云（${flowItemCountState.value} 个标签）",
                    style = UiTextStyle(fontSizeSp = 14.sp),
                    modifier = Modifier.margin(bottom = 8.dp),
                )
                Row(
                    spacing = 8.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .margin(bottom = 8.dp),
                ) {
                    Button(
                        text = "增加标签",
                        size = ButtonSize.Compact,
                        onClick = { flowItemCountState.value = (flowItemCountState.value + 2).coerceAtMost(20) },
                    )
                    Button(
                        text = "减少标签",
                        size = ButtonSize.Compact,
                        variant = ButtonVariant.Outlined,
                        onClick = { flowItemCountState.value = (flowItemCountState.value - 2).coerceAtLeast(2) },
                    )
                }
                FlowRow(
                    horizontalSpacing = 8.dp,
                    verticalSpacing = 8.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .margin(bottom = 16.dp)
                        .testTag(DemoTestTags.LAYOUTS_FLOW_ROW),
                ) {
                    (1..flowItemCountState.value).forEach { i ->
                        Surface(
                            variant = SurfaceVariant.Variant,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        ) {
                            Text(text = "标签 $i")
                        }
                    }
                }
                Divider(modifier = Modifier.margin(bottom = 12.dp))
                Text(
                    text = "FlowColumn 竖向流（maxItemsInEachColumn = 3）",
                    style = UiTextStyle(fontSizeSp = 14.sp),
                    modifier = Modifier.margin(bottom = 8.dp),
                )
                FlowColumn(
                    horizontalSpacing = 12.dp,
                    verticalSpacing = 8.dp,
                    maxItemsInEachColumn = 3,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp),
                ) {
                    (1..9).forEach { i ->
                        Surface(
                            variant = SurfaceVariant.Default,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        ) {
                            Text(text = "竖向项 $i")
                        }
                    }
                }
            }

            "scrollable" -> ScenarioSection(
                kind = ScenarioKind.Core,
                title = "滚动容器",
                subtitle = "ScrollableColumn 和 ScrollableRow 提供可滚动的线性容器。",
            ) {
                Text(
                    text = "ScrollableColumn（超长内容可滚动）",
                    style = UiTextStyle(fontSizeSp = 14.sp),
                    modifier = Modifier.margin(bottom = 8.dp),
                )
                ScrollableColumn(
                    spacing = 8.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .backgroundColor(SurfaceDefaults.variantBackgroundColor())
                        .shape(SurfaceDefaults.shape())
                        .padding(12.dp)
                        .testTag(DemoTestTags.LAYOUTS_SCROLLABLE_COLUMN),
                ) {
                    (1..15).forEach { i ->
                        Surface(
                            variant = SurfaceVariant.Default,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                        ) {
                            Text(text = "滚动内容行 $i")
                        }
                    }
                    Text(
                        text = "更多内容在下方 ↓",
                        style = UiTextStyle(fontSizeSp = 13.sp),
                        color = TextDefaults.secondaryColor(),
                    )
                }
                Divider(modifier = Modifier.margin(vertical = 12.dp))
                Text(
                    text = "ScrollableRow（横向可滚动标签行）",
                    style = UiTextStyle(fontSizeSp = 14.sp),
                    modifier = Modifier.margin(bottom = 8.dp),
                )
                ScrollableRow(
                    spacing = 8.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .backgroundColor(SurfaceDefaults.variantBackgroundColor())
                        .shape(SurfaceDefaults.shape())
                        .padding(8.dp)
                        .testTag(DemoTestTags.LAYOUTS_SCROLLABLE_ROW),
                ) {
                    (1..20).forEach { i ->
                        Surface(
                            variant = SurfaceVariant.Default,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        ) {
                            Text(text = "横向标签 $i")
                        }
                    }
                }
            }

            "constraint_basic" -> ScenarioSection(
                kind = ScenarioKind.Core,
                title = "Constraint 基础锚点",
                subtitle = "anchors + dimension + bias 组合，验证约束节点在 renderer 主链路可稳定布局。",
            ) {
                ConstraintLayout(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(188.dp)
                        .backgroundColor(SurfaceDefaults.backgroundColor())
                        .shape(SurfaceDefaults.shape())
                        .padding(12.dp)
                        .testTag(DemoTestTags.LAYOUTS_CONSTRAINT_BASIC_CONTAINER),
                ) {
                    val (titleRef, contentRef, badgeRef) = createRefs("title", "content", "badge")
                    Text(
                        text = "约束基础卡片",
                        style = UiTextStyle(fontSizeSp = 15.sp),
                        modifier = Modifier.constrainAs(titleRef) {
                            topToTop(parent)
                            startToStart(parent)
                        },
                    )
                    Surface(
                        variant = SurfaceVariant.Variant,
                        modifier = Modifier.constrainAs(contentRef) {
                            startToStart(parent)
                            endToEnd(parent)
                            topToBottom(titleRef, margin = 12.dp)
                            bottomToBottom(parent)
                            width = ConstraintDimension.FillToConstraints
                            height = ConstraintDimension.FillToConstraints
                        }.padding(12.dp),
                    ) {
                        Text(
                            text = "content 通过 FillToConstraints 拉伸；badge 由 bias 控制位置。",
                            style = UiTextStyle(fontSizeSp = 13.sp),
                            color = TextDefaults.secondaryColor(),
                        )
                    }
                    Surface(
                        variant = SurfaceVariant.Default,
                        modifier = Modifier
                            .constrainAs(badgeRef) {
                                startToStart(contentRef, margin = 8.dp)
                                endToEnd(contentRef, margin = 8.dp)
                                topToTop(contentRef, margin = 8.dp)
                                horizontalBias = 0.78f
                            }
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                            .testTag(DemoTestTags.LAYOUTS_CONSTRAINT_BASIC_BADGE),
                    ) {
                        Text(text = "Bias")
                    }
                }
            }

            "constraint_helpers" -> ScenarioSection(
                kind = ScenarioKind.Core,
                title = "Guideline + Barrier",
                subtitle = "guideline 控制分区，barrier 在文本宽度变化后仍给右侧内容稳定锚点。",
            ) {
                Button(
                    text = if (constraintHelperLongState.value) "使用短文案" else "使用长文案",
                    size = ButtonSize.Compact,
                    variant = ButtonVariant.Outlined,
                    modifier = Modifier
                        .margin(bottom = 8.dp)
                        .testTag(DemoTestTags.LAYOUTS_CONSTRAINT_HELPERS_TOGGLE),
                    onClick = { constraintHelperLongState.value = !constraintHelperLongState.value },
                )
                ConstraintLayout(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(176.dp)
                        .backgroundColor(SurfaceDefaults.variantBackgroundColor())
                        .shape(SurfaceDefaults.shape())
                        .padding(12.dp)
                        .testTag(DemoTestTags.LAYOUTS_CONSTRAINT_HELPERS_CONTAINER),
                ) {
                    val leftPartition = createGuidelineFromStart(0.62f)
                    val (headlineRef, summaryRef, markerRef) = createRefs("headline", "summary", "marker")
                    val endBarrier = createEndBarrier(headlineRef, summaryRef, margin = 6.dp)
                    Text(
                        text = "Helper 区",
                        style = UiTextStyle(fontSizeSp = 14.sp),
                        modifier = Modifier.constrainAs(headlineRef) {
                            startToStart(parent)
                            topToTop(parent)
                            endToStart(leftPartition, margin = 8.dp)
                            width = ConstraintDimension.FillToConstraints
                        },
                    )
                    Text(
                        text = if (constraintHelperLongState.value) {
                            "这是一段更长的说明文案，用于验证 barrier 会跟随最长文本边界。"
                        } else {
                            "简短说明文案。"
                        },
                        style = UiTextStyle(fontSizeSp = 12.sp),
                        color = TextDefaults.secondaryColor(),
                        modifier = Modifier.constrainAs(summaryRef) {
                            startToStart(parent)
                            topToBottom(headlineRef, margin = 8.dp)
                            endToStart(leftPartition, margin = 8.dp)
                            width = ConstraintDimension.FillToConstraints
                        },
                    )
                    Surface(
                        variant = SurfaceVariant.Default,
                        modifier = Modifier
                            .constrainAs(markerRef) {
                                startToEnd(endBarrier, margin = 8.dp)
                                topToTop(parent)
                            }
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                            .testTag(DemoTestTags.LAYOUTS_CONSTRAINT_HELPERS_MARKER),
                    ) {
                        Text(text = "Barrier Marker")
                    }
                }
            }

            "constraint_chain" -> ScenarioSection(
                kind = ScenarioKind.Core,
                title = "Chain 编排",
                subtitle = "horizontal chain 统一分配空间，验证链式布局在 patch 后保持稳定。",
            ) {
                ConstraintLayout(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(148.dp)
                        .backgroundColor(SurfaceDefaults.backgroundColor())
                        .shape(SurfaceDefaults.shape())
                        .padding(12.dp)
                        .testTag(DemoTestTags.LAYOUTS_CONSTRAINT_CHAIN_CONTAINER),
                ) {
                    val (startRef, middleRef, endRef) = createRefs("start", "middle", "end")
                    createHorizontalChain(
                        startRef,
                        middleRef,
                        endRef,
                        style = ConstraintChainStyle.SpreadInside,
                    )
                    Surface(
                        variant = SurfaceVariant.Variant,
                        modifier = Modifier
                            .constrainAs(startRef) {
                                topToTop(parent)
                                bottomToBottom(parent)
                                width = ConstraintDimension.Fixed(88.dp)
                                height = ConstraintDimension.Fixed(56.dp)
                            }
                            .testTag(DemoTestTags.LAYOUTS_CONSTRAINT_CHAIN_START),
                    ) { Box(contentAlignment = BoxAlignment.Center, modifier = Modifier.fillMaxSize()) { Text(text = "A") } }
                    Surface(
                        variant = SurfaceVariant.Default,
                        modifier = Modifier
                            .constrainAs(middleRef) {
                                topToTop(parent)
                                bottomToBottom(parent)
                                width = ConstraintDimension.Fixed(88.dp)
                                height = ConstraintDimension.Fixed(56.dp)
                            }
                            .testTag(DemoTestTags.LAYOUTS_CONSTRAINT_CHAIN_MIDDLE),
                    ) { Box(contentAlignment = BoxAlignment.Center, modifier = Modifier.fillMaxSize()) { Text(text = "B") } }
                    Surface(
                        variant = SurfaceVariant.Variant,
                        modifier = Modifier
                            .constrainAs(endRef) {
                                topToTop(parent)
                                bottomToBottom(parent)
                                width = ConstraintDimension.Fixed(88.dp)
                                height = ConstraintDimension.Fixed(56.dp)
                            }
                            .testTag(DemoTestTags.LAYOUTS_CONSTRAINT_CHAIN_END),
                    ) { Box(contentAlignment = BoxAlignment.Center, modifier = Modifier.fillMaxSize()) { Text(text = "C") } }
                }
            }

            "constraint_set" -> ScenarioSection(
                kind = ScenarioKind.Visual,
                title = "Decoupled ConstraintSet",
                subtitle = "constraintSet 动态切换，验证约束重算与布局即时刷新。",
            ) {
                Button(
                    text = if (constraintSetExpandedState.value) "切换到竖向布局" else "切换到横向布局",
                    variant = ButtonVariant.Outlined,
                    modifier = Modifier
                        .margin(bottom = 8.dp)
                        .testTag(DemoTestTags.LAYOUTS_CONSTRAINT_SET_TOGGLE),
                    onClick = { constraintSetExpandedState.value = !constraintSetExpandedState.value },
                )
                val compactSet = constraintSet {
                    val (titleRef, markerRef) = createRefs("title", "marker")
                    constrain("title") {
                        startToStart(parent)
                        topToTop(parent)
                    }
                    constrain("marker") {
                        startToStart(titleRef)
                        topToBottom(titleRef, margin = 12.dp)
                        endToEnd(parent)
                        width = ConstraintDimension.FillToConstraints
                    }
                }
                val expandedSet = constraintSet {
                    val (titleRef, markerRef) = createRefs("title", "marker")
                    constrain("title") {
                        startToStart(parent)
                        topToTop(parent)
                        bottomToBottom(parent)
                    }
                    constrain("marker") {
                        startToEnd(titleRef, margin = 12.dp)
                        endToEnd(parent)
                        topToTop(parent)
                        bottomToBottom(parent)
                        width = ConstraintDimension.FillToConstraints
                    }
                }
                ConstraintLayout(
                    constraintSet = if (constraintSetExpandedState.value) expandedSet else compactSet,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(156.dp)
                        .backgroundColor(SurfaceDefaults.variantBackgroundColor())
                        .shape(SurfaceDefaults.shape())
                        .padding(12.dp),
                ) {
                    Text(
                        text = if (constraintSetExpandedState.value) "横向模式" else "竖向模式",
                        style = UiTextStyle(fontSizeSp = 14.sp),
                        modifier = Modifier.layoutId("title"),
                    )
                    Surface(
                        variant = SurfaceVariant.Default,
                        modifier = Modifier
                            .layoutId("marker")
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                            .testTag(DemoTestTags.LAYOUTS_CONSTRAINT_SET_MARKER),
                    ) {
                        Text(
                            text = if (constraintSetExpandedState.value) {
                                "marker 已切换到标题右侧。"
                            } else {
                                "marker 位于标题下方。"
                            },
                        )
                    }
                }
            }

            "constraint_anchor_advanced" -> ScenarioSection(
                kind = ScenarioKind.Core,
                title = "Anchor Advanced",
                subtitle = "覆盖 bottomToTop / baseline* / center* / circular 等高级锚点 API。",
            ) {
                ConstraintLayout(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(232.dp)
                        .backgroundColor(SurfaceDefaults.backgroundColor())
                        .shape(SurfaceDefaults.shape())
                        .padding(12.dp)
                        .testTag(DemoTestTags.LAYOUTS_CONSTRAINT_ANCHOR_ADVANCED_CONTAINER),
                ) {
                    val leaderRef = createRef("anchor-leader")
                    val baselineRef = createRef("anchor-baseline")
                    val baselineTopRef = createRef("anchor-baseline-top")
                    val baselineBottomRef = createRef("anchor-baseline-bottom")
                    val centeredRef = createRef("anchor-centered")
                    val circleCenterRef = createRef("anchor-circle-center")
                    val circleNodeRef = createRef("anchor-circle-node")
                    val targetRef = createRef("anchor-target")
                    val linkedRef = createRef("anchor-linked")
                    Text(
                        text = "Leader 16sp",
                        style = UiTextStyle(fontSizeSp = 16.sp),
                        modifier = Modifier.constrainAs(leaderRef) {
                            topToTop(parent)
                            startToStart(parent)
                        },
                    )
                    Text(
                        text = "Baseline",
                        style = UiTextStyle(fontSizeSp = 12.sp),
                        modifier = Modifier
                            .constrainAs(baselineRef) {
                                startToEnd(leaderRef, margin = 10.dp)
                                baselineToBaseline(leaderRef)
                            }
                            .testTag(DemoTestTags.LAYOUTS_CONSTRAINT_ANCHOR_ADVANCED_BASELINE),
                    )
                    Text(
                        text = "B→Top",
                        style = UiTextStyle(fontSizeSp = 12.sp),
                        modifier = Modifier.constrainAs(baselineTopRef) {
                            startToEnd(baselineRef, margin = 10.dp)
                            baselineToTop(leaderRef, margin = 2.dp)
                        },
                    )
                    Text(
                        text = "B→Bottom",
                        style = UiTextStyle(fontSizeSp = 12.sp),
                        modifier = Modifier.constrainAs(baselineBottomRef) {
                            startToEnd(baselineTopRef, margin = 10.dp)
                            baselineToBottom(leaderRef, margin = 2.dp)
                        },
                    )
                    Surface(
                        variant = SurfaceVariant.Variant,
                        modifier = Modifier
                            .constrainAs(centeredRef) {
                                centerHorizontallyTo(parent)
                                centerVerticallyTo(parent)
                                width = ConstraintDimension.Fixed(118.dp)
                                height = ConstraintDimension.Fixed(44.dp)
                            },
                    ) {
                        Box(contentAlignment = BoxAlignment.Center, modifier = Modifier.fillMaxSize()) {
                            Text(text = "center*")
                        }
                    }
                    Surface(
                        variant = SurfaceVariant.Default,
                        modifier = Modifier
                            .constrainAs(circleCenterRef) {
                                topToTop(parent)
                                endToEnd(parent)
                                width = ConstraintDimension.Fixed(40.dp)
                                height = ConstraintDimension.Fixed(40.dp)
                            },
                    ) {
                        Box(contentAlignment = BoxAlignment.Center, modifier = Modifier.fillMaxSize()) {
                            Text(text = "C")
                        }
                    }
                    Surface(
                        variant = SurfaceVariant.Variant,
                        modifier = Modifier
                            .constrainAs(circleNodeRef) {
                                circular(
                                    target = circleCenterRef,
                                    radius = 54.dp,
                                    angle = 225f,
                                )
                                width = ConstraintDimension.Fixed(56.dp)
                                height = ConstraintDimension.Fixed(30.dp)
                            }
                            .testTag(DemoTestTags.LAYOUTS_CONSTRAINT_ANCHOR_ADVANCED_CIRCLE),
                    ) {
                        Box(contentAlignment = BoxAlignment.Center, modifier = Modifier.fillMaxSize()) {
                            Text(text = "circular")
                        }
                    }
                    Surface(
                        variant = SurfaceVariant.Default,
                        modifier = Modifier
                            .constrainAs(targetRef) {
                                bottomToBottom(parent)
                                endToEnd(parent)
                                width = ConstraintDimension.Fixed(92.dp)
                                height = ConstraintDimension.Fixed(32.dp)
                            },
                    ) {
                        Box(contentAlignment = BoxAlignment.Center, modifier = Modifier.fillMaxSize()) {
                            Text(text = "Target")
                        }
                    }
                    Surface(
                        variant = SurfaceVariant.Variant,
                        modifier = Modifier
                            .constrainAs(linkedRef) {
                                bottomToTop(targetRef, margin = 8.dp)
                                endToEnd(targetRef)
                                width = ConstraintDimension.Fixed(92.dp)
                                height = ConstraintDimension.Fixed(32.dp)
                            },
                    ) {
                        Box(contentAlignment = BoxAlignment.Center, modifier = Modifier.fillMaxSize()) {
                            Text(text = "bottomToTop")
                        }
                    }
                }
                Text(
                    text = "已覆盖：bottomToTop / baselineToBaseline / baselineToTop / baselineToBottom / centerHorizontallyTo / centerVerticallyTo / circular。",
                    style = UiTextStyle(fontSizeSp = 12.sp),
                    color = TextDefaults.secondaryColor(),
                    modifier = Modifier
                        .margin(top = 6.dp)
                        .testTag(DemoTestTags.LAYOUTS_CONSTRAINT_ANCHOR_ADVANCED_STATUS),
                )
            }

            "constraint_dimension_advanced" -> ScenarioSection(
                kind = ScenarioKind.Visual,
                title = "Dimension Advanced",
                subtitle = "覆盖 min/max/percent、constrainedWidth/Height 和 dimensionRatio。",
            ) {
                Button(
                    text = if (constraintDimensionAdvancedState.value) "切回紧凑尺寸" else "切到扩展尺寸",
                    variant = ButtonVariant.Outlined,
                    modifier = Modifier
                        .margin(bottom = 8.dp)
                        .testTag(DemoTestTags.LAYOUTS_CONSTRAINT_DIMENSION_ADVANCED_TOGGLE),
                    onClick = { constraintDimensionAdvancedState.value = !constraintDimensionAdvancedState.value },
                )
                ConstraintLayout(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp)
                        .backgroundColor(SurfaceDefaults.variantBackgroundColor())
                        .shape(SurfaceDefaults.shape())
                        .padding(12.dp)
                        .testTag(DemoTestTags.LAYOUTS_CONSTRAINT_DIMENSION_ADVANCED_CONTAINER),
                ) {
                    val (widthRef, heightRef, ratioRef) = createRefs("dim-width", "dim-height", "dim-ratio")
                    Surface(
                        variant = SurfaceVariant.Default,
                        modifier = Modifier
                            .constrainAs(widthRef) {
                                startToStart(parent)
                                endToEnd(parent)
                                topToTop(parent)
                                width = ConstraintDimension.FillToConstraints
                                height = ConstraintDimension.Fixed(38.dp)
                                widthPercent = if (constraintDimensionAdvancedState.value) 0.82f else 0.56f
                                widthMin = 120.dp
                                widthMax = 280.dp
                                constrainedWidth = true
                            }
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                    ) {
                        Text(text = "widthPercent + widthMin/Max + constrainedWidth")
                    }
                    Surface(
                        variant = SurfaceVariant.Variant,
                        modifier = Modifier
                            .constrainAs(heightRef) {
                                startToStart(parent)
                                topToBottom(widthRef, margin = 10.dp)
                                bottomToBottom(parent)
                                width = ConstraintDimension.Fixed(104.dp)
                                height = ConstraintDimension.FillToConstraints
                                heightPercent = if (constraintDimensionAdvancedState.value) 0.62f else 0.38f
                                heightMin = 64.dp
                                heightMax = 146.dp
                                constrainedHeight = true
                            }
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                    ) {
                        Text(text = "heightPercent\nmin/max")
                    }
                    Surface(
                        variant = SurfaceVariant.Default,
                        modifier = Modifier
                            .constrainAs(ratioRef) {
                                startToEnd(heightRef, margin = 10.dp)
                                endToEnd(parent)
                                topToBottom(widthRef, margin = 10.dp)
                                bottomToBottom(parent)
                                width = ConstraintDimension.FillToConstraints
                                height = ConstraintDimension.FillToConstraints
                                dimensionRatio = if (constraintDimensionAdvancedState.value) "16:9" else "1:1"
                            }
                            .testTag(DemoTestTags.LAYOUTS_CONSTRAINT_DIMENSION_ADVANCED_RATIO),
                    ) {
                        Box(contentAlignment = BoxAlignment.Center, modifier = Modifier.fillMaxSize()) {
                            Text(text = if (constraintDimensionAdvancedState.value) "ratio 16:9" else "ratio 1:1")
                        }
                    }
                }
                Text(
                    text = if (constraintDimensionAdvancedState.value) {
                        "扩展模式：widthPercent=0.82, heightPercent=0.62, ratio=16:9"
                    } else {
                        "紧凑模式：widthPercent=0.56, heightPercent=0.38, ratio=1:1"
                    },
                    style = UiTextStyle(fontSizeSp = 12.sp),
                    color = TextDefaults.secondaryColor(),
                    modifier = Modifier
                        .margin(top = 6.dp)
                        .testTag(DemoTestTags.LAYOUTS_CONSTRAINT_DIMENSION_ADVANCED_STATUS),
                )
            }

            "constraint_helpers_full" -> ScenarioSection(
                kind = ScenarioKind.Visual,
                title = "Guideline + Barrier Full",
                subtitle = "覆盖 End/Top/Bottom guideline 与 Start/Top/Bottom barrier（含 margin/allowsGoneWidgets）。",
            ) {
                Button(
                    text = if (constraintHelpersFullState.value) "切到 fraction 模式" else "切到 offset 模式",
                    variant = ButtonVariant.Outlined,
                    modifier = Modifier
                        .margin(bottom = 8.dp)
                        .testTag(DemoTestTags.LAYOUTS_CONSTRAINT_HELPERS_FULL_TOGGLE),
                    onClick = { constraintHelpersFullState.value = !constraintHelpersFullState.value },
                )
                ConstraintLayout(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(208.dp)
                        .backgroundColor(SurfaceDefaults.backgroundColor())
                        .shape(SurfaceDefaults.shape())
                        .padding(12.dp)
                        .testTag(DemoTestTags.LAYOUTS_CONSTRAINT_HELPERS_FULL_CONTAINER),
                ) {
                    val guideEnd = if (constraintHelpersFullState.value) {
                        createGuidelineFromEnd(0.26f, id = "helpers-full-guide-end")
                    } else {
                        createGuidelineFromEnd(68.dp, id = "helpers-full-guide-end")
                    }
                    val guideTop = if (constraintHelpersFullState.value) {
                        createGuidelineFromTop(0.22f, id = "helpers-full-guide-top")
                    } else {
                        createGuidelineFromTop(24.dp, id = "helpers-full-guide-top")
                    }
                    val guideBottom = if (constraintHelpersFullState.value) {
                        createGuidelineFromBottom(0.18f, id = "helpers-full-guide-bottom")
                    } else {
                        createGuidelineFromBottom(30.dp, id = "helpers-full-guide-bottom")
                    }
                    val (probeTopRef, probeMiddleRef, probeBottomRef, markerRef) = createRefs(
                        "helpers-full-probe-top",
                        "helpers-full-probe-middle",
                        "helpers-full-probe-bottom",
                        "helpers-full-marker",
                    )
                    val startBarrier = createStartBarrier(
                        probeTopRef,
                        probeMiddleRef,
                        probeBottomRef,
                        id = "helpers-full-start-barrier",
                        margin = if (constraintHelpersFullState.value) 14.dp else 4.dp,
                        allowsGoneWidgets = constraintHelpersFullState.value,
                    )
                    val topBarrier = createTopBarrier(
                        probeTopRef,
                        probeMiddleRef,
                        id = "helpers-full-top-barrier",
                        margin = if (constraintHelpersFullState.value) 10.dp else 4.dp,
                        allowsGoneWidgets = constraintHelpersFullState.value,
                    )
                    val bottomBarrier = createBottomBarrier(
                        probeMiddleRef,
                        probeBottomRef,
                        id = "helpers-full-bottom-barrier",
                        margin = if (constraintHelpersFullState.value) 10.dp else 4.dp,
                        allowsGoneWidgets = constraintHelpersFullState.value,
                    )
                    Surface(
                        variant = SurfaceVariant.Variant,
                        modifier = Modifier
                            .constrainAs(probeTopRef) {
                                topToTop(guideTop)
                                endToStart(guideEnd, margin = 6.dp)
                                width = ConstraintDimension.Fixed(110.dp)
                                height = ConstraintDimension.Fixed(30.dp)
                            },
                    ) {
                        Box(contentAlignment = BoxAlignment.Center, modifier = Modifier.fillMaxSize()) {
                            Text(text = "Top Probe")
                        }
                    }
                    Surface(
                        variant = SurfaceVariant.Default,
                        modifier = Modifier
                            .constrainAs(probeMiddleRef) {
                                topToBottom(probeTopRef, margin = 8.dp)
                                endToStart(guideEnd, margin = 6.dp)
                                width = ConstraintDimension.Fixed(126.dp)
                                height = ConstraintDimension.Fixed(30.dp)
                            },
                    ) {
                        Box(contentAlignment = BoxAlignment.Center, modifier = Modifier.fillMaxSize()) {
                            Text(text = "Middle Probe")
                        }
                    }
                    Surface(
                        variant = SurfaceVariant.Variant,
                        modifier = Modifier
                            .constrainAs(probeBottomRef) {
                                bottomToTop(guideBottom)
                                endToStart(guideEnd, margin = 6.dp)
                                width = ConstraintDimension.Fixed(98.dp)
                                height = ConstraintDimension.Fixed(30.dp)
                            },
                    ) {
                        Box(contentAlignment = BoxAlignment.Center, modifier = Modifier.fillMaxSize()) {
                            Text(text = "Bottom Probe")
                        }
                    }
                    Surface(
                        variant = SurfaceVariant.Default,
                        modifier = Modifier
                            .constrainAs(markerRef) {
                                endToStart(startBarrier, margin = 8.dp)
                                topToBottom(topBarrier, margin = 8.dp)
                                bottomToTop(bottomBarrier, margin = 8.dp)
                                width = ConstraintDimension.Fixed(112.dp)
                                height = ConstraintDimension.Fixed(34.dp)
                            }
                            .testTag(DemoTestTags.LAYOUTS_CONSTRAINT_HELPERS_FULL_MARKER),
                    ) {
                        Box(contentAlignment = BoxAlignment.Center, modifier = Modifier.fillMaxSize()) {
                            Text(text = "Barrier Marker")
                        }
                    }
                }
                Text(
                    text = if (constraintHelpersFullState.value) {
                        "fraction 模式：Guideline=End/Top/Bottom fraction，Barrier margin 更大且 allowsGoneWidgets=true。"
                    } else {
                        "offset 模式：Guideline=End/Top/Bottom offset，Barrier margin 较小且 allowsGoneWidgets=false。"
                    },
                    style = UiTextStyle(fontSizeSp = 12.sp),
                    color = TextDefaults.secondaryColor(),
                    modifier = Modifier
                        .margin(top = 6.dp)
                        .testTag(DemoTestTags.LAYOUTS_CONSTRAINT_HELPERS_FULL_STATUS),
                )
            }

            "constraint_vertical_chain" -> ScenarioSection(
                kind = ScenarioKind.Core,
                title = "Vertical Chain",
                subtitle = "覆盖 createVerticalChain 的 weights + bias + style。",
            ) {
                Button(
                    text = if (constraintVerticalChainPackedState.value) "切到 SpreadInside" else "切到 Packed",
                    variant = ButtonVariant.Outlined,
                    modifier = Modifier
                        .margin(bottom = 8.dp)
                        .testTag(DemoTestTags.LAYOUTS_CONSTRAINT_VERTICAL_CHAIN_TOGGLE),
                    onClick = { constraintVerticalChainPackedState.value = !constraintVerticalChainPackedState.value },
                )
                ConstraintLayout(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp)
                        .backgroundColor(SurfaceDefaults.variantBackgroundColor())
                        .shape(SurfaceDefaults.shape())
                        .padding(12.dp)
                        .testTag(DemoTestTags.LAYOUTS_CONSTRAINT_VERTICAL_CHAIN_CONTAINER),
                ) {
                    val (topRef, middleRef, bottomRef) = createRefs("v-chain-top", "v-chain-middle", "v-chain-bottom")
                    createVerticalChain(
                        topRef,
                        middleRef,
                        bottomRef,
                        weights = if (constraintVerticalChainPackedState.value) {
                            listOf(1f, 2f, 1f)
                        } else {
                            listOf(1f, 1f, 1f)
                        },
                        style = if (constraintVerticalChainPackedState.value) {
                            ConstraintChainStyle.Packed
                        } else {
                            ConstraintChainStyle.SpreadInside
                        },
                        bias = if (constraintVerticalChainPackedState.value) 0.3f else 0.5f,
                    )
                    Surface(
                        variant = SurfaceVariant.Default,
                        modifier = Modifier
                            .constrainAs(topRef) {
                                topToTop(parent)
                                startToStart(parent)
                                endToEnd(parent)
                                width = ConstraintDimension.FillToConstraints
                                height = ConstraintDimension.Fixed(42.dp)
                            }
                            .testTag(DemoTestTags.LAYOUTS_CONSTRAINT_VERTICAL_CHAIN_TOP),
                    ) {
                        Box(contentAlignment = BoxAlignment.Center, modifier = Modifier.fillMaxSize()) {
                            Text(text = "Top")
                        }
                    }
                    Surface(
                        variant = SurfaceVariant.Variant,
                        modifier = Modifier
                            .constrainAs(middleRef) {
                                startToStart(parent)
                                endToEnd(parent)
                                width = ConstraintDimension.FillToConstraints
                                height = ConstraintDimension.Fixed(42.dp)
                            }
                            .testTag(DemoTestTags.LAYOUTS_CONSTRAINT_VERTICAL_CHAIN_MIDDLE),
                    ) {
                        Box(contentAlignment = BoxAlignment.Center, modifier = Modifier.fillMaxSize()) {
                            Text(text = "Middle")
                        }
                    }
                    Surface(
                        variant = SurfaceVariant.Default,
                        modifier = Modifier
                            .constrainAs(bottomRef) {
                                bottomToBottom(parent)
                                startToStart(parent)
                                endToEnd(parent)
                                width = ConstraintDimension.FillToConstraints
                                height = ConstraintDimension.Fixed(42.dp)
                            }
                            .testTag(DemoTestTags.LAYOUTS_CONSTRAINT_VERTICAL_CHAIN_BOTTOM),
                    ) {
                        Box(contentAlignment = BoxAlignment.Center, modifier = Modifier.fillMaxSize()) {
                            Text(text = "Bottom")
                        }
                    }
                }
            }

            "constraint_set_helpers_mirror" -> ScenarioSection(
                kind = ScenarioKind.Visual,
                title = "ConstraintSet Helper Mirror",
                subtitle = "在 decoupled constraintSet 中复用 helper + chain，验证 builder 入口完整可用。",
            ) {
                Button(
                    text = if (constraintSetHelpersAlternateState.value) "切回横向 helper-set" else "切到纵向 helper-set",
                    variant = ButtonVariant.Outlined,
                    modifier = Modifier
                        .margin(bottom = 8.dp)
                        .testTag(DemoTestTags.LAYOUTS_CONSTRAINT_SET_HELPERS_TOGGLE),
                    onClick = { constraintSetHelpersAlternateState.value = !constraintSetHelpersAlternateState.value },
                )
                val helperSetHorizontal = constraintSet {
                    val (aRef, bRef, cRef, markerRef) = createRefs("set-h-a", "set-h-b", "set-h-c", "set-marker")
                    createHorizontalChain(
                        aRef,
                        bRef,
                        cRef,
                        style = ConstraintChainStyle.SpreadInside,
                        bias = 0.5f,
                    )
                    val endBarrier = createEndBarrier(
                        aRef,
                        bRef,
                        cRef,
                        id = "set-h-end-barrier",
                        margin = 6.dp,
                    )
                    constrain("set-h-a") {
                        topToTop(parent)
                        bottomToBottom(parent)
                        width = ConstraintDimension.Fixed(64.dp)
                        height = ConstraintDimension.Fixed(36.dp)
                    }
                    constrain("set-h-b") {
                        topToTop(parent)
                        bottomToBottom(parent)
                        width = ConstraintDimension.Fixed(64.dp)
                        height = ConstraintDimension.Fixed(36.dp)
                    }
                    constrain("set-h-c") {
                        topToTop(parent)
                        bottomToBottom(parent)
                        width = ConstraintDimension.Fixed(64.dp)
                        height = ConstraintDimension.Fixed(36.dp)
                    }
                    constrain("set-marker") {
                        startToEnd(endBarrier, margin = 8.dp)
                        topToTop(parent)
                        width = ConstraintDimension.Fixed(92.dp)
                        height = ConstraintDimension.Fixed(36.dp)
                    }
                }
                val helperSetVertical = constraintSet {
                    val (aRef, bRef, cRef, markerRef) = createRefs("set-h-a", "set-h-b", "set-h-c", "set-marker")
                    createVerticalChain(
                        aRef,
                        bRef,
                        cRef,
                        style = ConstraintChainStyle.Packed,
                        bias = 0.22f,
                    )
                    val topBarrier = createTopBarrier(
                        aRef,
                        bRef,
                        cRef,
                        id = "set-v-top-barrier",
                        margin = 6.dp,
                    )
                    constrain("set-h-a") {
                        startToStart(parent)
                        endToEnd(parent)
                        width = ConstraintDimension.Fixed(92.dp)
                        height = ConstraintDimension.Fixed(34.dp)
                    }
                    constrain("set-h-b") {
                        startToStart(parent)
                        endToEnd(parent)
                        width = ConstraintDimension.Fixed(92.dp)
                        height = ConstraintDimension.Fixed(34.dp)
                    }
                    constrain("set-h-c") {
                        startToStart(parent)
                        endToEnd(parent)
                        width = ConstraintDimension.Fixed(92.dp)
                        height = ConstraintDimension.Fixed(34.dp)
                    }
                    constrain("set-marker") {
                        topToBottom(topBarrier, margin = 8.dp)
                        startToStart(parent)
                        endToEnd(parent)
                        width = ConstraintDimension.Fixed(126.dp)
                        height = ConstraintDimension.Fixed(34.dp)
                    }
                }
                ConstraintLayout(
                    constraintSet = if (constraintSetHelpersAlternateState.value) helperSetVertical else helperSetHorizontal,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(172.dp)
                        .backgroundColor(SurfaceDefaults.backgroundColor())
                        .shape(SurfaceDefaults.shape())
                        .padding(12.dp)
                        .testTag(DemoTestTags.LAYOUTS_CONSTRAINT_SET_HELPERS_CONTAINER),
                ) {
                    Surface(
                        variant = SurfaceVariant.Default,
                        modifier = Modifier.layoutId("set-h-a").padding(horizontal = 8.dp, vertical = 6.dp),
                    ) {
                        Text(text = "A")
                    }
                    Surface(
                        variant = SurfaceVariant.Variant,
                        modifier = Modifier.layoutId("set-h-b").padding(horizontal = 8.dp, vertical = 6.dp),
                    ) {
                        Text(text = "B")
                    }
                    Surface(
                        variant = SurfaceVariant.Default,
                        modifier = Modifier.layoutId("set-h-c").padding(horizontal = 8.dp, vertical = 6.dp),
                    ) {
                        Text(text = "C")
                    }
                    Surface(
                        variant = SurfaceVariant.Variant,
                        modifier = Modifier
                            .layoutId("set-marker")
                            .padding(horizontal = 8.dp, vertical = 6.dp)
                            .testTag(DemoTestTags.LAYOUTS_CONSTRAINT_SET_HELPERS_MARKER),
                    ) {
                        Text(text = if (constraintSetHelpersAlternateState.value) "vertical helper-set" else "horizontal helper-set")
                    }
                }
                Text(
                    text = if (constraintSetHelpersAlternateState.value) {
                        "ConstraintSet(B): createVerticalChain + createTopBarrier 生效。"
                    } else {
                        "ConstraintSet(A): createHorizontalChain + createEndBarrier 生效。"
                    },
                    style = UiTextStyle(fontSizeSp = 12.sp),
                    color = TextDefaults.secondaryColor(),
                    modifier = Modifier
                        .margin(top = 6.dp)
                        .testTag(DemoTestTags.LAYOUTS_CONSTRAINT_SET_HELPERS_STATUS),
                )
            }

            "constraint_virtual_helpers" -> ScenarioSection(
                kind = ScenarioKind.Visual,
                title = "Virtual Helpers",
                subtitle = "Flow/Group/Layer/Placeholder 对比模式：一键切换后，排布、可见性、占位承载与成组变换都应有明显变化。",
            ) {
                Button(
                    text = if (constraintVirtualAlternateState.value) {
                        "切回基线模式（A）"
                    } else {
                        "切到对比模式（B）"
                    },
                    variant = ButtonVariant.Outlined,
                    modifier = Modifier
                        .margin(bottom = 8.dp)
                        .testTag(DemoTestTags.LAYOUTS_CONSTRAINT_VIRTUAL_TOGGLE),
                    onClick = { constraintVirtualAlternateState.value = !constraintVirtualAlternateState.value },
                )
                Column(
                    spacing = 10.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .backgroundColor(SurfaceDefaults.variantBackgroundColor())
                        .shape(SurfaceDefaults.shape())
                        .padding(12.dp)
                        .testTag(DemoTestTags.LAYOUTS_CONSTRAINT_VIRTUAL_CONTAINER),
                ) {
                    Text(
                        text = if (constraintVirtualAlternateState.value) {
                            "模式 B：Flow 单列 · Group 隐藏 · Placeholder=A · Layer 强变换"
                        } else {
                            "模式 A：Flow 双列 · Group 显示 · Placeholder=D · Layer 中性"
                        },
                        style = UiTextStyle(fontSizeSp = 14.sp),
                    )

                    val flowSet = constraintSet {
                        val flowRef = createRef("flow-helper")
                        constrain("flow-helper") {
                            topToTop(parent)
                            startToStart(parent)
                            endToEnd(parent)
                            width = ConstraintDimension.FillToConstraints
                        }
                    }
                    ConstraintLayout(
                        constraintSet = flowSet,
                        modifier = Modifier.fillMaxWidth().height(78.dp),
                    ) {
                        val (flowA, flowB, flowC, flowD) = createRefs("flow-a", "flow-b", "flow-c", "flow-d")
                        createFlow(
                            flowA,
                            flowB,
                            flowC,
                            flowD,
                            id = "flow-helper",
                            horizontalGap = if (constraintVirtualAlternateState.value) 14.dp else 8.dp,
                            verticalGap = if (constraintVirtualAlternateState.value) 14.dp else 8.dp,
                            maxElementsWrap = if (constraintVirtualAlternateState.value) 1 else 2,
                        )
                        Surface(variant = SurfaceVariant.Default, modifier = Modifier.layoutId("flow-a").padding(horizontal = 10.dp, vertical = 6.dp)) { Text("Flow-1") }
                        Surface(variant = SurfaceVariant.Variant, modifier = Modifier.layoutId("flow-b").padding(horizontal = 10.dp, vertical = 6.dp)) { Text("Flow-2") }
                        Surface(variant = SurfaceVariant.Default, modifier = Modifier.layoutId("flow-c").padding(horizontal = 10.dp, vertical = 6.dp)) { Text("Flow-3") }
                        Surface(variant = SurfaceVariant.Variant, modifier = Modifier.layoutId("flow-d").padding(horizontal = 10.dp, vertical = 6.dp)) { Text("Flow-4") }
                    }

                    val groupSet = constraintSet {
                        constrain("group-a") {
                            topToTop(parent)
                            startToStart(parent)
                        }
                        constrain("group-b") {
                            topToTop(parent)
                            endToEnd(parent)
                        }
                    }
                    ConstraintLayout(
                        constraintSet = groupSet,
                        modifier = Modifier.fillMaxWidth().height(42.dp),
                    ) {
                        val (groupA, groupB) = createRefs("group-a", "group-b")
                        createGroup(
                            groupA,
                            groupB,
                            id = "group-helper",
                            visibility = if (constraintVirtualAlternateState.value) {
                                ConstraintHelperVisibility.Gone
                            } else {
                                ConstraintHelperVisibility.Visible
                            },
                        )
                        Surface(
                            variant = SurfaceVariant.Variant,
                            modifier = Modifier
                                .layoutId("group-a")
                                .visibility(
                                    if (constraintVirtualAlternateState.value) {
                                        Visibility.Gone
                                    } else {
                                        Visibility.Visible
                                    },
                                )
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                                .testTag(DemoTestTags.LAYOUTS_CONSTRAINT_VIRTUAL_GROUP_MEMBER),
                        ) {
                            Text(text = "Group-A")
                        }
                        Surface(
                            variant = SurfaceVariant.Default,
                            modifier = Modifier.layoutId("group-b").padding(horizontal = 10.dp, vertical = 6.dp),
                        ) {
                            Text(text = "Group-B")
                        }
                    }

                    val layerSet = constraintSet {
                        constrain("layer-a") {
                            topToTop(parent)
                            startToStart(parent)
                        }
                        constrain("layer-b") {
                            topToTop(parent)
                            endToEnd(parent)
                        }
                    }
                    ConstraintLayout(
                        constraintSet = layerSet,
                        modifier = Modifier.fillMaxWidth().height(62.dp),
                    ) {
                        val (layerA, layerB) = createRefs("layer-a", "layer-b")
                        createLayer(
                            layerA,
                            layerB,
                            id = "layer-helper",
                            rotation = if (constraintVirtualAlternateState.value) 30f else 0f,
                            scaleX = if (constraintVirtualAlternateState.value) 1.16f else 1f,
                            scaleY = if (constraintVirtualAlternateState.value) 1.16f else 1f,
                            translationX = if (constraintVirtualAlternateState.value) 24.dp else 0.dp,
                            translationY = if (constraintVirtualAlternateState.value) (-10).dp else 0.dp,
                        )
                        Surface(
                            variant = SurfaceVariant.Default,
                            modifier = Modifier
                                .layoutId("layer-a")
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                                .testTag(DemoTestTags.LAYOUTS_CONSTRAINT_VIRTUAL_CHIP_A),
                        ) {
                            Text(text = "Layer-A")
                        }
                        Surface(
                            variant = SurfaceVariant.Variant,
                            modifier = Modifier.layoutId("layer-b").padding(horizontal = 10.dp, vertical = 6.dp),
                        ) {
                            Text(text = "Layer-B")
                        }
                    }

                    val placeholderSet = constraintSet {
                        val hostRef = createRef("placeholder-helper")
                        constrain("placeholder-a") {
                            topToTop(parent)
                            startToStart(parent)
                        }
                        constrain("placeholder-b") {
                            topToTop(parent)
                            endToEnd(parent)
                        }
                        constrain("placeholder-helper") {
                            topToBottom(createRef("placeholder-a"), margin = 6.dp)
                            startToStart(parent)
                            endToEnd(parent)
                            width = ConstraintDimension.FillToConstraints
                            height = ConstraintDimension.Fixed(38.dp)
                        }
                        constrain("placeholder-note") {
                            topToBottom(hostRef, margin = 4.dp)
                            startToStart(parent)
                        }
                    }
                    ConstraintLayout(
                        constraintSet = placeholderSet,
                        modifier = Modifier.fillMaxWidth().height(88.dp),
                    ) {
                        val placeholderA = createRef("placeholder-a")
                        val placeholderB = createRef("placeholder-b")
                        createPlaceholder(
                            content = if (constraintVirtualAlternateState.value) placeholderA else placeholderB,
                            id = "placeholder-helper",
                        )
                        Surface(
                            variant = SurfaceVariant.Default,
                            modifier = Modifier.layoutId("placeholder-a").padding(horizontal = 8.dp, vertical = 4.dp),
                        ) {
                            Text(text = "Placeholder-A")
                        }
                        Surface(
                            variant = SurfaceVariant.Variant,
                            modifier = Modifier.layoutId("placeholder-b").padding(horizontal = 8.dp, vertical = 4.dp),
                        ) {
                            Text(text = "Placeholder-B")
                        }
                        Surface(
                            variant = SurfaceVariant.Default,
                            modifier = Modifier.layoutId("placeholder-note").padding(horizontal = 8.dp, vertical = 4.dp),
                        ) {
                            Text(text = if (constraintVirtualAlternateState.value) "当前承载 A" else "当前承载 B")
                        }
                    }

                    Text(
                        text = if (constraintVirtualAlternateState.value) {
                            "Group: hidden(A/B) · Placeholder: A · Layer: rotate+scale+translate · Flow: single column"
                        } else {
                            "Group: visible(A/B) · Placeholder: B · Layer: neutral · Flow: 2-column wrap"
                        },
                        style = UiTextStyle(fontSizeSp = 12.sp),
                        color = TextDefaults.secondaryColor(),
                        modifier = Modifier.testTag(DemoTestTags.LAYOUTS_CONSTRAINT_VIRTUAL_STATUS),
                    )
                }
            }

            else -> VerificationNotesSection(
                what = "布局组件应验证线性容器、Box 叠加、流式布局和滚动容器的稳定性。",
                howToVerify = listOf(
                    "反复点击 Box 区域，确认点击态和固定标签不会错位。",
                    "在不同宽度设备上观察 Row 中顶部/底部对齐文本。",
                    "切换长短标签，确认 weighted button 与嵌套 surface 布局稳定。",
                    "增减 FlowRow 标签数量，确认自动换行正确。",
                    "上下滑动 ScrollableColumn，确认滚动流畅。",
                    "左右滑动 ScrollableRow，确认横向滚动正常。",
                    "切到约束页，按分区验证 API 覆盖矩阵：Anchor Advanced、Dimension Advanced、Guideline+Barrier Full、Vertical Chain、ConstraintSet Helper Mirror、Virtual Helpers。",
                    "切换约束场景中的 toggle 按钮，确认状态文案、关键 marker、位置/尺寸关系有可见变化。",
                ),
                expected = listOf(
                    "线性容器默认子项不会意外扩展成整行。",
                    "FlowRow/FlowColumn 自动换行/换列，spacing 均匀。",
                    "ScrollableColumn/ScrollableRow 滚动流畅无卡顿。",
                    "ConstraintLayout 全部业务 API 在 demo 中均有可见锚点；场景切换后布局即时刷新，无崩溃与错位。",
                ),
            )
        }
    }
}
