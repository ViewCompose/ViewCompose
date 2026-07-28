package com.viewcompose.ui.node

/**
 * renderer 识别的节点类型集合，是 VNode.spec 与平台 View 绑定器之间的分发键。
 * Node type set recognized by renderers; it dispatches VNode.spec objects to platform View binders.
 */
sealed interface NodeType {
    // 文本与输入节点。
    // Text and input nodes.
    data object Text : NodeType
    data object TextField : NodeType
    data object Checkbox : NodeType
    data object Switch : NodeType
    data object RadioButton : NodeType
    data object Slider : NodeType

    // 反馈与操作节点。
    // Feedback and action nodes.
    data object LinearProgressIndicator : NodeType
    data object CircularProgressIndicator : NodeType
    data object Button : NodeType
    data object IconButton : NodeType

    // 基础容器和布局节点。
    // Basic container and layout nodes.
    data object Row : NodeType
    data object Column : NodeType
    data object Box : NodeType
    data object Surface : NodeType
    data object ConstraintLayout : NodeType
    data object AnimatedVisibilityHost : NodeType
    data object AnimatedSizeHost : NodeType
    data object NestedScrollHost : NodeType
    data object Spacer : NodeType
    data object Divider : NodeType
    data object Canvas : NodeType
    data object Image : NodeType
    data object AndroidView : NodeType

    // 集合、滚动和导航节点。
    // Collection, scrolling, and navigation nodes.
    data object LazyColumn : NodeType
    data object LazyRow : NodeType
    data object SegmentedControl : NodeType
    data object ScrollableColumn : NodeType
    data object ScrollableRow : NodeType
    data object FlowRow : NodeType
    data object FlowColumn : NodeType
    data object NavigationBar : NodeType
    data object HorizontalPager : NodeType
    data object VerticalPager : NodeType
    data object TabRow : NodeType
    data object LazyVerticalGrid : NodeType
    data object PullToRefresh : NodeType
}
