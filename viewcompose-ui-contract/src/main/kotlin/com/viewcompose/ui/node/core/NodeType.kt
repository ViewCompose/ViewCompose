package com.viewcompose.ui.node

sealed interface NodeType {
    data object Text : NodeType
    data object TextField : NodeType
    data object Checkbox : NodeType
    data object Switch : NodeType
    data object RadioButton : NodeType
    data object Slider : NodeType
    data object LinearProgressIndicator : NodeType
    data object CircularProgressIndicator : NodeType
    data object Button : NodeType
    data object IconButton : NodeType
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
