package com.viewcompose.ui.node

/**
 * Selects the renderer binder that interprets a VNode's `NodeSpec`.
 *
 * This sealed hierarchy is an inter-module dispatch contract. Renderers should handle known values
 * exhaustively and reject a node whose type/spec pair does not match. New node types may be added in
 * a future artifact version and require a compatible renderer update.
 */
sealed interface NodeType {
    /** Static or rich text content. */
    data object Text : NodeType

    /** Editable text input. */
    data object TextField : NodeType

    /** Boolean checkbox input. */
    data object Checkbox : NodeType

    /** Boolean switch input. */
    data object Switch : NodeType

    /** Mutually exclusive radio-button input. */
    data object RadioButton : NodeType

    /** Continuous or stepped range slider input. */
    data object Slider : NodeType

    /** Horizontal determinate or indeterminate progress feedback. */
    data object LinearProgressIndicator : NodeType

    /** Circular determinate or indeterminate progress feedback. */
    data object CircularProgressIndicator : NodeType

    /** Text/content action button. */
    data object Button : NodeType

    /** Icon-only action button. */
    data object IconButton : NodeType

    /** Horizontal linear child layout. */
    data object Row : NodeType

    /** Vertical linear child layout. */
    data object Column : NodeType

    /** Stacking child layout. */
    data object Box : NodeType

    /** Styled surface container. */
    data object Surface : NodeType

    /** Constraint-based child layout. */
    data object ConstraintLayout : NodeType

    /** Host that animates child visibility changes. */
    data object AnimatedVisibilityHost : NodeType

    /** Host that measures and places the bounded pair participating in content replacement. */
    data object AnimatedContentHost : NodeType

    /** Host for one renderable content-replacement subtree and its interaction ownership. */
    data object AnimatedContentItemHost : NodeType

    /** Host that animates measured content-size changes. */
    data object AnimatedSizeHost : NodeType

    /** Host that animates one child's real parent-local position and size. */
    data object AnimatedBoundsHost : NodeType

    /** Transparent host enforcing portable maximum-size and aspect-ratio constraints. */
    data object LayoutConstraintHost : NodeType

    /** Transparent renderer host participating in nested scrolling. */
    data object NestedScrollHost : NodeType

    /** Empty fixed or modifier-sized layout node. */
    data object Spacer : NodeType

    /** Horizontal or vertical dividing line. */
    data object Divider : NodeType

    /** Command-recorded custom drawing surface. */
    data object Canvas : NodeType

    /** Resource, URI, file, URL, or adapter-model image content. */
    data object Image : NodeType

    /** Caller-supplied native Android View boundary. */
    data object AndroidView : NodeType

    /** Vertically scrolling, virtualized item collection. */
    data object LazyColumn : NodeType

    /** Horizontally scrolling, virtualized item collection. */
    data object LazyRow : NodeType

    /** Single-selection segmented control. */
    data object SegmentedControl : NodeType

    /** Vertically scrolling eager child container. */
    data object ScrollableColumn : NodeType

    /** Horizontally scrolling eager child container. */
    data object ScrollableRow : NodeType

    /** Horizontally wrapping flow layout. */
    data object FlowRow : NodeType

    /** Vertically wrapping flow layout. */
    data object FlowColumn : NodeType

    /** Primary destination navigation bar. */
    data object NavigationBar : NodeType

    /** Horizontally paged virtualized content. */
    data object HorizontalPager : NodeType

    /** Vertically paged virtualized content. */
    data object VerticalPager : NodeType

    /** Horizontally arranged tab selector. */
    data object TabRow : NodeType

    /** Vertically scrolling, virtualized grid. */
    data object LazyVerticalGrid : NodeType

    /** Pull gesture container with refresh feedback. */
    data object PullToRefresh : NodeType
}
