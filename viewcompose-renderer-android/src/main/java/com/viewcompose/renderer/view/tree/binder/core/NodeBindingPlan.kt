package com.viewcompose.renderer.view.tree

import com.viewcompose.ui.node.spec.BoxNodeProps
import com.viewcompose.ui.node.spec.ButtonNodeProps
import com.viewcompose.ui.node.spec.ColumnNodeProps
import com.viewcompose.ui.node.spec.ConstraintLayoutNodeProps
import com.viewcompose.ui.node.spec.DividerNodeProps
import com.viewcompose.ui.node.spec.AnimatedSizeHostNodeProps
import com.viewcompose.ui.node.spec.LayoutConstraintHostNodeProps
import com.viewcompose.ui.node.spec.AnimatedVisibilityHostNodeProps
import com.viewcompose.ui.node.spec.CanvasNodeProps
import com.viewcompose.ui.node.spec.FlowColumnNodeProps
import com.viewcompose.ui.node.spec.FlowRowNodeProps
import com.viewcompose.ui.node.spec.HorizontalPagerNodeProps
import com.viewcompose.ui.node.spec.IconButtonNodeProps
import com.viewcompose.ui.node.spec.ImageNodeProps
import com.viewcompose.ui.node.spec.LazyColumnNodeProps
import com.viewcompose.ui.node.spec.LazyRowNodeProps
import com.viewcompose.ui.node.spec.LazyVerticalGridNodeProps
import com.viewcompose.ui.node.spec.NavigationBarNodeProps
import com.viewcompose.ui.node.spec.ProgressIndicatorNodeProps
import com.viewcompose.ui.node.spec.PullToRefreshNodeProps
import com.viewcompose.ui.node.spec.RowNodeProps
import com.viewcompose.ui.node.spec.ScrollableColumnNodeProps
import com.viewcompose.ui.node.spec.ScrollableRowNodeProps
import com.viewcompose.ui.node.spec.SegmentedControlNodeProps
import com.viewcompose.ui.node.spec.SliderNodeProps
import com.viewcompose.ui.node.spec.TabRowNodeProps
import com.viewcompose.ui.node.spec.TextNodeProps
import com.viewcompose.ui.node.spec.TextFieldNodeProps
import com.viewcompose.ui.node.spec.ToggleNodeProps
import com.viewcompose.ui.node.spec.VerticalPagerNodeProps

/**
 * Binding strategy for a reused node.
 */
internal sealed interface NodeBindingPlan {
    /**
     * Skips this node's binding while continuing child reconciliation.
     */
    data object SkipSelfOnly : NodeBindingPlan

    /**
     * Skips this node and its complete subtree.
     */
    data object SkipSubtree : NodeBindingPlan

    /**
     * Executes a complete bind again.
     */
    data object Rebind : NodeBindingPlan

    /** Applies only modifier-owned behavior while preserving the node's semantic binding. */
    data object ModifierOnly : NodeBindingPlan

    /**
     * Applies a targeted patch and records whether modifiers changed.
     */
    data class Patch(
        val patch: NodeViewPatch,
        val modifierChanged: Boolean = false,
    ) : NodeBindingPlan
}

/**
 * Common marker interface for targeted View patches.
 *
 * Concrete patches carry previous and next specs; their applier determines the actual changed fields.
 */
internal sealed interface NodeViewPatch

internal data class ButtonNodePatch(
    val previous: ButtonNodeProps,
    val next: ButtonNodeProps,
) : NodeViewPatch

internal data class TextNodePatch(
    val previous: TextNodeProps,
    val next: TextNodeProps,
) : NodeViewPatch

internal data class TextFieldNodePatch(
    val previous: TextFieldNodeProps,
    val next: TextFieldNodeProps,
) : NodeViewPatch

internal data class SegmentedControlNodePatch(
    val previous: SegmentedControlNodeProps,
    val next: SegmentedControlNodeProps,
) : NodeViewPatch

internal data class LazyColumnNodePatch(
    val previous: LazyColumnNodeProps,
    val next: LazyColumnNodeProps,
) : NodeViewPatch

internal data class LazyRowNodePatch(
    val previous: LazyRowNodeProps,
    val next: LazyRowNodeProps,
) : NodeViewPatch

internal data class ToggleNodePatch(
    val previous: ToggleNodeProps,
    val next: ToggleNodeProps,
) : NodeViewPatch

internal data class SliderNodePatch(
    val previous: SliderNodeProps,
    val next: SliderNodeProps,
) : NodeViewPatch

internal data class ProgressIndicatorNodePatch(
    val previous: ProgressIndicatorNodeProps,
    val next: ProgressIndicatorNodeProps,
) : NodeViewPatch

internal data class RowNodePatch(
    val previous: RowNodeProps,
    val next: RowNodeProps,
) : NodeViewPatch

internal data class ColumnNodePatch(
    val previous: ColumnNodeProps,
    val next: ColumnNodeProps,
) : NodeViewPatch

internal data class BoxNodePatch(
    val previous: BoxNodeProps,
    val next: BoxNodeProps,
) : NodeViewPatch

internal data class ConstraintLayoutNodePatch(
    val previous: ConstraintLayoutNodeProps,
    val next: ConstraintLayoutNodeProps,
) : NodeViewPatch

internal data class AnimatedVisibilityHostNodePatch(
    val previous: AnimatedVisibilityHostNodeProps,
    val next: AnimatedVisibilityHostNodeProps,
) : NodeViewPatch

internal data class AnimatedSizeHostNodePatch(
    val previous: AnimatedSizeHostNodeProps,
    val next: AnimatedSizeHostNodeProps,
) : NodeViewPatch

internal data class LayoutConstraintHostNodePatch(
    val previous: LayoutConstraintHostNodeProps,
    val next: LayoutConstraintHostNodeProps,
) : NodeViewPatch

internal data class ImageNodePatch(
    val previous: ImageNodeProps,
    val next: ImageNodeProps,
) : NodeViewPatch

internal data class IconButtonNodePatch(
    val previous: IconButtonNodeProps,
    val next: IconButtonNodeProps,
) : NodeViewPatch

internal data class DividerNodePatch(
    val previous: DividerNodeProps,
    val next: DividerNodeProps,
) : NodeViewPatch

internal data class CanvasNodePatch(
    val previous: CanvasNodeProps,
    val next: CanvasNodeProps,
) : NodeViewPatch

internal data class ScrollableColumnNodePatch(
    val previous: ScrollableColumnNodeProps,
    val next: ScrollableColumnNodeProps,
) : NodeViewPatch

internal data class ScrollableRowNodePatch(
    val previous: ScrollableRowNodeProps,
    val next: ScrollableRowNodeProps,
) : NodeViewPatch

internal data class FlowRowNodePatch(
    val previous: FlowRowNodeProps,
    val next: FlowRowNodeProps,
) : NodeViewPatch

internal data class FlowColumnNodePatch(
    val previous: FlowColumnNodeProps,
    val next: FlowColumnNodeProps,
) : NodeViewPatch

internal data class NavigationBarNodePatch(
    val previous: NavigationBarNodeProps,
    val next: NavigationBarNodeProps,
) : NodeViewPatch

internal data class HorizontalPagerNodePatch(
    val previous: HorizontalPagerNodeProps,
    val next: HorizontalPagerNodeProps,
) : NodeViewPatch

internal data class TabRowNodePatch(
    val previous: TabRowNodeProps,
    val next: TabRowNodeProps,
) : NodeViewPatch

internal data class VerticalPagerNodePatch(
    val previous: VerticalPagerNodeProps,
    val next: VerticalPagerNodeProps,
) : NodeViewPatch

internal data class LazyVerticalGridNodePatch(
    val previous: LazyVerticalGridNodeProps,
    val next: LazyVerticalGridNodeProps,
) : NodeViewPatch

internal data class PullToRefreshNodePatch(
    val previous: PullToRefreshNodeProps,
    val next: PullToRefreshNodeProps,
) : NodeViewPatch
