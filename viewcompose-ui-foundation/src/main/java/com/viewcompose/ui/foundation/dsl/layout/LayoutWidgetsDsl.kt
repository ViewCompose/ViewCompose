package com.viewcompose.ui.foundation

import com.viewcompose.graphics.core.Brush
import com.viewcompose.ui.layout.BoxAlignment
import com.viewcompose.ui.layout.HorizontalAlignment
import com.viewcompose.ui.layout.MainAxisArrangement
import com.viewcompose.ui.layout.VerticalAlignment
import com.viewcompose.ui.modifier.Modifier
import com.viewcompose.ui.modifier.alpha
import com.viewcompose.ui.modifier.clickable
import com.viewcompose.ui.modifier.dropShadows
import com.viewcompose.ui.modifier.elevation
import com.viewcompose.ui.modifier.innerShadows
import com.viewcompose.ui.modifier.interactionIndication
import com.viewcompose.ui.modifier.semantics
import com.viewcompose.ui.node.NodeType
import com.viewcompose.ui.node.UiInteractionIndication
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
import com.viewcompose.ui.node.spec.SurfaceNodeProps
import com.viewcompose.ui.modifier.SemanticsRole
import com.viewcompose.ui.unit.UiDp
import com.viewcompose.ui.state.ScrollState

/**
 * Overlays children in one layout container without adding interaction behavior.
 *
 * Children are measured against the same available bounds and placed in declaration order; later
 * children draw above earlier children. Use [Modifier.interactionIndication] together with an input
 * modifier when a custom box needs feedback rather than introducing component policy here.
 *
 * @sample com.viewcompose.ui.foundation.samples.layoutDslSample
 * @receiver active tree builder receiving the layout node
 * @param key optional stable sibling identity used during reconciliation
 * @param contentAlignment default placement for children without explicit [BoxScope] parent data
 * @param modifier ordered layout, drawing, input, and semantics configuration for the container
 * @param content children emitted synchronously into the box scope
 */
fun UiTreeBuilder.Box(
    key: Any? = null,
    contentAlignment: BoxAlignment = BoxAlignment.TopStart,
    modifier: Modifier = Modifier,
    content: BoxScope.() -> Unit,
) {
    emitResolved(
        type = NodeType.Box,
        key = key,
        spec = BoxNodeProps(contentAlignment = contentAlignment),
        modifier = modifier,
        children = BoxScope().apply(content).build(),
    )
}

/**
 * Emits a design-system-neutral surface from fully resolved visual and interaction values.
 *
 * The surface owns one Android View whose effective bounds participate in layout, input, focus,
 * and semantics. [visualHeight] optionally centers only the visual fill, border, ripple, and shape
 * inside those bounds. A caller background, border, or shape in [modifier] replaces the resolved
 * surface and therefore occupies the complete effective bounds. Caller elevation replaces
 * [BasicSurfaceStyle.elevation], while caller shadow modifiers are drawn after the style shadows.
 *
 * The callback runs synchronously on the renderer input thread, which is the Android main thread
 * for the standard renderer. A non-null [onClick] with [enabled] set to `false` retains disabled
 * semantics but installs no click listener or interaction layer.
 *
 * @sample com.viewcompose.ui.foundation.samples.basicSurfaceSample
 *
 * @receiver active tree builder receiving the surface node
 * @param style resolved fill, geometry, border, clipping, elevation, and shadow values
 * @param contentColor packed ARGB value provided to descendant content defaults
 * @param enabled whether the optional click action and state layers participate in input
 * @param onClick optional click callback; `null` creates a non-interactive surface
 * @param minimumWidth non-negative minimum effective width in dp
 * @param minimumHeight non-negative minimum effective height in dp
 * @param visualHeight optional non-negative visual surface height centered inside effective bounds
 * @param role optional accessibility role applied to the merged surface subtree
 * @param key optional stable sibling identity used during reconciliation
 * @param contentAlignment default alignment for children without explicit box parent data
 * @param modifier caller configuration appended after resolved surface behavior and visuals
 * @param content child content emitted inside the surface
 * @throws IllegalArgumentException if a supplied dimension is negative
 */
fun UiTreeBuilder.BasicSurface(
    style: BasicSurfaceStyle,
    contentColor: Int,
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null,
    minimumWidth: UiDp = UiDp.Zero,
    minimumHeight: UiDp = UiDp.Zero,
    visualHeight: UiDp? = null,
    role: SemanticsRole? = null,
    key: Any? = null,
    contentAlignment: BoxAlignment = BoxAlignment.TopStart,
    modifier: Modifier = Modifier,
    content: BoxScope.() -> Unit,
) {
    require(minimumWidth >= UiDp.Zero) { "BasicSurface minimumWidth must be non-negative." }
    require(minimumHeight >= UiDp.Zero) { "BasicSurface minimumHeight must be non-negative." }
    require(visualHeight == null || visualHeight >= UiDp.Zero) {
        "BasicSurface visualHeight must be non-negative when specified."
    }

    val hasAction = onClick != null
    val defaultModifier = Modifier
        .then(
            if (style.elevation > UiDp.Zero) Modifier.elevation(style.elevation) else Modifier,
        )
        .then(Modifier.dropShadows(style.stableDropShadows, shape = style.shape))
        .then(Modifier.innerShadows(style.stableInnerShadows, shape = style.shape))
        .then(
            if (enabled && onClick != null) {
                style.interactionIndication?.let(Modifier::interactionIndication) ?: Modifier
            } else {
                Modifier
            },
        )
        .then(
            if (enabled && onClick != null) Modifier.clickable(onClick) else Modifier,
        )
        .then(
            if (hasAction || role != null) {
                Modifier.semantics(mergeDescendants = true) {
                    this.role = role
                    this.enabled = enabled
                }
            } else {
                Modifier
            },
        )
        .then(modifier)

    ProvideLocal(LocalContentColor, contentColor) {
        emitResolved(
            type = NodeType.Surface,
            key = key,
            spec = SurfaceNodeProps(
                contentAlignment = contentAlignment,
                fill = style.fill,
                shape = style.shape,
                borderWidth = style.borderWidth,
                borderColor = style.borderColor,
                minimumWidth = minimumWidth,
                minimumHeight = minimumHeight,
                visualHeight = visualHeight,
                clipContent = style.clipContent,
            ),
            modifier = defaultModifier,
            children = BoxScope().apply(content).build(),
        )
    }
}

/**
 * Creates a themed surface that provides [contentColor] to descendants.
 *
 * A non-null [onClick] turns the surface into one merged interaction target. Disabled surfaces keep
 * disabled semantics and appearance but install neither a click listener nor transient indication.
 * Instance modifiers are applied after resolved component appearance.
 *
 * @sample com.viewcompose.ui.foundation.samples.layoutDslSample
 * @receiver active tree builder receiving the surface
 * @param key optional stable sibling identity used during reconciliation
 * @param variant semantic appearance tier resolved by the active design system
 * @param enabled whether the optional action accepts input and renders enabled appearance
 * @param contentAlignment default placement for children without explicit box parent data
 * @param contentColor packed ARGB value provided through [LocalContentColor]
 * @param onClick optional callback invoked synchronously for an accepted click
 * @param modifier ordered caller configuration applied after resolved surface behavior
 * @param content child content emitted inside the surface
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
    BasicSurface(
        style = BasicSurfaceStyle(
            fill = Brush.SolidColor(SurfaceDefaults.backgroundColor(variant)),
            shape = SurfaceDefaults.shape(),
            interactionIndication = if (onClick != null) {
                UiInteractionIndication.StateLayer(stateLayerColorsFor(contentColor))
            } else {
                null
            },
        ),
        contentColor = contentColor,
        enabled = enabled,
        onClick = onClick,
        key = key,
        contentAlignment = contentAlignment,
        modifier = Modifier
            .then(
                if (!enabled) {
                    Modifier.alpha(SurfaceDefaults.disabledAlpha())
                } else {
                    Modifier
                },
            )
            .then(modifier),
        content = content,
    )
}

/**
 * Reserves empty layout space determined by [modifier].
 *
 * @sample com.viewcompose.ui.foundation.samples.layoutDslSample
 * @receiver active tree builder receiving the empty node
 * @param key optional stable sibling identity used during reconciliation
 * @param modifier size and parent-data configuration that determines the occupied bounds
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
 * Draws a solid divider with a theme-resolved default thickness.
 *
 * @sample com.viewcompose.ui.foundation.samples.layoutDslSample
 * @receiver active tree builder receiving the divider
 * @param color packed ARGB divider color
 * @param thickness non-negative cross-axis thickness in dp
 * @param key optional stable sibling identity used during reconciliation
 * @param modifier ordered layout and drawing configuration for the divider node
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
 * Places eager children along a horizontal main axis.
 *
 * All children remain mounted. Prefer [LazyRow] when the collection is large or unbounded.
 *
 * @sample com.viewcompose.ui.foundation.samples.layoutDslSample
 * @receiver active tree builder receiving the row
 * @param key optional stable sibling identity used during reconciliation
 * @param spacing fixed gap in dp between adjacent children
 * @param arrangement placement policy along the horizontal main axis
 * @param verticalAlignment default cross-axis placement for children
 * @param modifier ordered configuration applied to the row container
 * @param content eager children emitted synchronously into the row scope
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
 * Places eager children along a vertical main axis.
 *
 * All children remain mounted. Prefer [LazyColumn] when the collection is large or unbounded.
 *
 * @sample com.viewcompose.ui.foundation.samples.layoutDslSample
 * @receiver active tree builder receiving the column
 * @param key optional stable sibling identity used during reconciliation
 * @param spacing fixed gap in dp between adjacent children
 * @param arrangement placement policy along the vertical main axis
 * @param horizontalAlignment default cross-axis placement for children
 * @param modifier ordered configuration applied to the column container
 * @param content eager children emitted synchronously into the column scope
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
 * Emits a vertically scrolling container whose complete child tree remains mounted.
 *
 * [state] observes pixel offsets in the first-party Android renderer and accepts immediate or
 * animated commands. Focused descendants use the platform child-rectangle request chain to remain
 * visible; this programmatic movement remains available when [userScrollEnabled] is `false`.
 * Prefer [LazyColumn] for large or unbounded collections.
 *
 * @sample com.viewcompose.ui.foundation.samples.eagerScrollStateSample
 * @sample com.viewcompose.ui.foundation.samples.focusVisibilityOwnershipSample
 * @receiver active tree builder receiving the scroll container
 * @param key optional stable sibling identity used during reconciliation
 * @param spacing fixed gap between adjacent eager children
 * @param arrangement main-axis placement when content is smaller than the viewport
 * @param horizontalAlignment default cross-axis child alignment
 * @param state optional caller-owned observable position and command state
 * @param userScrollEnabled whether direct user scrolling is accepted
 * @param modifier ordered configuration applied to the scroll container root
 * @param content eager column content retained while the node is mounted
 */
fun UiTreeBuilder.ScrollableColumn(
    key: Any? = null,
    spacing: UiDp = UiDp.Zero,
    arrangement: MainAxisArrangement = MainAxisArrangement.Start,
    horizontalAlignment: HorizontalAlignment = HorizontalAlignment.Start,
    state: ScrollState? = null,
    userScrollEnabled: Boolean = true,
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
            state = state,
            userScrollEnabled = userScrollEnabled,
        ),
        modifier = modifier,
        children = ColumnScope().apply(content).build(),
    )
}

/**
 * Emits a horizontally scrolling container whose complete child tree remains mounted.
 *
 * [state] offsets are measured from logical start, including in RTL. Disabling
 * [userScrollEnabled] affects only direct scrolling; descendants and state commands remain active.
 * Prefer [LazyRow] for large or unbounded collections.
 *
 * @sample com.viewcompose.ui.foundation.samples.eagerScrollStateSample
 * @receiver active tree builder receiving the scroll container
 * @param key optional stable sibling identity used during reconciliation
 * @param spacing fixed gap between adjacent eager children
 * @param arrangement main-axis placement when content is smaller than the viewport
 * @param verticalAlignment default cross-axis child alignment
 * @param state optional caller-owned observable position and command state
 * @param userScrollEnabled whether direct user scrolling is accepted
 * @param modifier ordered configuration applied to the scroll container root
 * @param content eager row content retained while the node is mounted
 */
fun UiTreeBuilder.ScrollableRow(
    key: Any? = null,
    spacing: UiDp = UiDp.Zero,
    arrangement: MainAxisArrangement = MainAxisArrangement.Start,
    verticalAlignment: VerticalAlignment = VerticalAlignment.Top,
    state: ScrollState? = null,
    userScrollEnabled: Boolean = true,
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
            state = state,
            userScrollEnabled = userScrollEnabled,
        ),
        modifier = modifier,
        children = RowScope().apply(content).build(),
    )
}

/**
 * Places eager children left-to-right and wraps them into additional rows.
 *
 * @sample com.viewcompose.ui.foundation.samples.layoutDslSample
 * @receiver active tree builder receiving the flow layout
 * @param key optional stable sibling identity used during reconciliation
 * @param horizontalSpacing gap in dp between children in one row
 * @param verticalSpacing gap in dp between wrapped rows
 * @param maxItemsInEachRow maximum children placed in a row before forced wrapping
 * @param modifier ordered configuration applied to the flow container
 * @param content eager children emitted synchronously into the layout scope
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
 * Places eager children top-to-bottom and wraps them into additional columns.
 *
 * @sample com.viewcompose.ui.foundation.samples.layoutDslSample
 * @receiver active tree builder receiving the flow layout
 * @param key optional stable sibling identity used during reconciliation
 * @param horizontalSpacing gap in dp between wrapped columns
 * @param verticalSpacing gap in dp between children in one column
 * @param maxItemsInEachColumn maximum children placed in a column before forced wrapping
 * @param modifier ordered configuration applied to the flow container
 * @param content eager children emitted synchronously into the layout scope
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
 * Emits a controlled pull-to-refresh container around one scrollable-content scope.
 *
 * [isRefreshing] is caller-owned. An enabled native pull gesture invokes [onRefresh] once after its
 * threshold is crossed; the caller updates [isRefreshing] in a later render. Setting [enabled] to
 * `false` disables only refresh interception, leaving descendant scrolling and input enabled.
 *
 * @sample com.viewcompose.ui.foundation.samples.pullToRefreshEnablementSample
 * @receiver active tree builder receiving the refresh container
 * @param isRefreshing whether the refresh indicator is currently active
 * @param onRefresh callback invoked synchronously for an accepted refresh request
 * @param enabled whether the container may intercept a pull as refresh input
 * @param indicatorColor resolved refresh-indicator ARGB color
 * @param key optional stable sibling identity used during reconciliation
 * @param modifier ordered configuration applied to the refresh root
 * @param content scrollable child content built synchronously
 */
fun UiTreeBuilder.PullToRefresh(
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    enabled: Boolean = true,
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
            enabled = enabled,
            indicatorColor = indicatorColor,
        ),
        modifier = modifier,
        children = ScrollableScope().apply(content).build(),
    )
}
