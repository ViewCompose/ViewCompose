package com.viewcompose.widget.core

import com.viewcompose.ui.layout.BoxAlignment
import com.viewcompose.ui.layout.HorizontalAlignment
import com.viewcompose.ui.layout.MainAxisArrangement
import com.viewcompose.ui.layout.VerticalAlignment
import com.viewcompose.ui.modifier.Modifier
import com.viewcompose.ui.modifier.alpha
import com.viewcompose.ui.modifier.backgroundColor
import com.viewcompose.ui.modifier.clickable
import com.viewcompose.ui.modifier.shape
import com.viewcompose.ui.node.NodeType
import com.viewcompose.ui.node.spec.BoxNodeProps
import com.viewcompose.ui.node.spec.ColumnNodeProps
import com.viewcompose.ui.node.spec.DividerNodeProps
import com.viewcompose.ui.node.spec.EmptyNodeSpec
import com.viewcompose.ui.node.spec.FlowColumnNodeProps
import com.viewcompose.ui.node.spec.FlowRowNodeProps
import com.viewcompose.ui.node.spec.PullToRefreshNodeProps
import com.viewcompose.ui.node.spec.RowNodeProps
import com.viewcompose.ui.node.spec.ScrollableColumnNodeProps
import com.viewcompose.ui.node.spec.ScrollableRowNodeProps
import com.viewcompose.ui.unit.UiDp

/**
 * Emits a Box container node.
 */
fun UiTreeBuilder.Box(
    key: Any? = null,
    contentAlignment: BoxAlignment = BoxAlignment.TopStart,
    rippleColor: Int? = null,
    modifier: Modifier = Modifier,
    content: BoxScope.() -> Unit,
) {
    emitResolved(
        type = NodeType.Box,
        key = key,
        spec = BoxNodeProps(
            contentAlignment = contentAlignment,
            rippleColor = rippleColor,
        ),
        modifier = modifier,
        children = BoxScope().apply(content).build(),
    )
}

/**
 * Emits a Surface container and provides a default content color within its content scope.
 */
fun UiTreeBuilder.Surface(
    key: Any? = null,
    variant: SurfaceVariant = SurfaceVariant.Default,
    enabled: Boolean = true,
    contentAlignment: BoxAlignment = BoxAlignment.TopStart,
    contentColor: Int = SurfaceDefaults.contentColor(variant),
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    content: BoxScope.() -> Unit,
) {
    val semanticModifier = Modifier
        .backgroundColor(SurfaceDefaults.backgroundColor(variant))
        .shape(SurfaceDefaults.shape())
        .then(
            if (!enabled) {
                Modifier.alpha(SurfaceDefaults.disabledAlpha())
            } else {
                Modifier
            },
        )
        .then(
            if (enabled && onClick != null) {
                Modifier.clickable(onClick)
            } else {
                Modifier
            },
        )
        .then(modifier)
    ProvideLocal(LocalContentColor, contentColor) {
        emitResolved(
            type = NodeType.Surface,
            key = key,
            spec = BoxNodeProps(
                contentAlignment = contentAlignment,
                rippleColor = SurfaceDefaults.pressedColor(),
            ),
            modifier = semanticModifier,
            children = BoxScope().apply(content).build(),
        )
    }
}

/**
 * Emits an empty spacer node whose size is usually controlled by Modifier.
 */
fun UiTreeBuilder.Spacer(
    key: Any? = null,
    modifier: Modifier = Modifier,
) {
    emit(
        type = NodeType.Spacer,
        key = key,
        spec = EmptyNodeSpec,
        modifier = modifier,
    )
}

/**
 * Emits a divider node.
 */
fun UiTreeBuilder.Divider(
    color: Int = DividerDefaults.color(),
    thickness: UiDp = DividerDefaults.thickness(),
    key: Any? = null,
    modifier: Modifier = Modifier,
) {
    emit(
        type = NodeType.Divider,
        key = key,
        spec = DividerNodeProps(
            color = color,
            thickness = thickness,
        ),
        modifier = modifier,
    )
}

/**
 * Emits a horizontal linear layout node.
 */
fun UiTreeBuilder.Row(
    key: Any? = null,
    spacing: UiDp = UiDp.Zero,
    arrangement: MainAxisArrangement = MainAxisArrangement.Start,
    verticalAlignment: VerticalAlignment = VerticalAlignment.Top,
    modifier: Modifier = Modifier,
    content: RowScope.() -> Unit,
) {
    emitResolved(
        type = NodeType.Row,
        key = key,
        spec = RowNodeProps(
            spacing = spacing,
            arrangement = arrangement,
            verticalAlignment = verticalAlignment,
        ),
        modifier = modifier,
        children = RowScope().apply(content).build(),
    )
}

/**
 * Emits a vertical linear layout node.
 */
fun UiTreeBuilder.Column(
    key: Any? = null,
    spacing: UiDp = UiDp.Zero,
    arrangement: MainAxisArrangement = MainAxisArrangement.Start,
    horizontalAlignment: HorizontalAlignment = HorizontalAlignment.Start,
    modifier: Modifier = Modifier,
    content: ColumnScope.() -> Unit,
) {
    emitResolved(
        type = NodeType.Column,
        key = key,
        spec = ColumnNodeProps(
            spacing = spacing,
            arrangement = arrangement,
            horizontalAlignment = horizontalAlignment,
        ),
        modifier = modifier,
        children = ColumnScope().apply(content).build(),
    )
}

/**
 * Emits a vertically scrollable Column node.
 */
fun UiTreeBuilder.ScrollableColumn(
    key: Any? = null,
    spacing: UiDp = UiDp.Zero,
    arrangement: MainAxisArrangement = MainAxisArrangement.Start,
    horizontalAlignment: HorizontalAlignment = HorizontalAlignment.Start,
    focusFollowKeyboard: Boolean = false,
    modifier: Modifier = Modifier,
    content: ColumnScope.() -> Unit,
) {
    emitResolved(
        type = NodeType.ScrollableColumn,
        key = key,
        spec = ScrollableColumnNodeProps(
            spacing = spacing,
            arrangement = arrangement,
            horizontalAlignment = horizontalAlignment,
            focusFollowKeyboard = focusFollowKeyboard,
        ),
        modifier = modifier,
        children = ColumnScope().apply(content).build(),
    )
}

/**
 * Emits a horizontally scrollable Row node.
 */
fun UiTreeBuilder.ScrollableRow(
    key: Any? = null,
    spacing: UiDp = UiDp.Zero,
    arrangement: MainAxisArrangement = MainAxisArrangement.Start,
    verticalAlignment: VerticalAlignment = VerticalAlignment.Top,
    modifier: Modifier = Modifier,
    content: RowScope.() -> Unit,
) {
    emitResolved(
        type = NodeType.ScrollableRow,
        key = key,
        spec = ScrollableRowNodeProps(
            spacing = spacing,
            arrangement = arrangement,
            verticalAlignment = verticalAlignment,
        ),
        modifier = modifier,
        children = RowScope().apply(content).build(),
    )
}

/**
 * Emits a row-flow layout node.
 */
fun UiTreeBuilder.FlowRow(
    key: Any? = null,
    horizontalSpacing: UiDp = UiDp.Zero,
    verticalSpacing: UiDp = UiDp.Zero,
    maxItemsInEachRow: Int = Int.MAX_VALUE,
    modifier: Modifier = Modifier,
    content: LayoutScope.() -> Unit,
) {
    emitResolved(
        type = NodeType.FlowRow,
        key = key,
        spec = FlowRowNodeProps(
            horizontalSpacing = horizontalSpacing,
            verticalSpacing = verticalSpacing,
            maxItemsInEachRow = maxItemsInEachRow,
        ),
        modifier = modifier,
        children = LayoutScope().apply(content).build(),
    )
}

/**
 * Emits a column-flow layout node.
 */
fun UiTreeBuilder.FlowColumn(
    key: Any? = null,
    horizontalSpacing: UiDp = UiDp.Zero,
    verticalSpacing: UiDp = UiDp.Zero,
    maxItemsInEachColumn: Int = Int.MAX_VALUE,
    modifier: Modifier = Modifier,
    content: LayoutScope.() -> Unit,
) {
    emitResolved(
        type = NodeType.FlowColumn,
        key = key,
        spec = FlowColumnNodeProps(
            horizontalSpacing = horizontalSpacing,
            verticalSpacing = verticalSpacing,
            maxItemsInEachColumn = maxItemsInEachColumn,
        ),
        modifier = modifier,
        children = LayoutScope().apply(content).build(),
    )
}

/**
 * Emits a pull-to-refresh container whose content is limited to ScrollableScope.
 */
fun UiTreeBuilder.PullToRefresh(
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    indicatorColor: Int = PullToRefreshDefaults.indicatorColor(),
    key: Any? = null,
    modifier: Modifier = Modifier,
    content: ScrollableScope.() -> Unit,
) {
    emitResolved(
        type = NodeType.PullToRefresh,
        key = key,
        spec = PullToRefreshNodeProps(
            isRefreshing = isRefreshing,
            onRefresh = onRefresh,
            indicatorColor = indicatorColor,
        ),
        modifier = modifier,
        children = ScrollableScope().apply(content).build(),
    )
}
