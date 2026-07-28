package com.viewcompose.renderer.view.tree

import com.viewcompose.ui.node.spec.BoxNodeProps
import com.viewcompose.ui.node.spec.ButtonNodeProps
import com.viewcompose.ui.node.spec.ColumnNodeProps
import com.viewcompose.ui.node.spec.ConstraintLayoutNodeProps
import com.viewcompose.ui.node.spec.DividerNodeProps
import com.viewcompose.ui.node.spec.AnimatedSizeHostNodeProps
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
 * 复用节点时的绑定策略。
 * Binding strategy for a reused node.
 */
internal sealed interface NodeBindingPlan {
    /**
     * 跳过当前节点绑定，但仍继续 reconcile 子节点。
     * Skips binding this node while continuing to reconcile children.
     */
    data object SkipSelfOnly : NodeBindingPlan

    /**
     * 跳过当前节点和整棵子树。
     * Skips this node and the entire subtree.
     */
    data object SkipSubtree : NodeBindingPlan

    /**
     * 重新执行完整 bind。
     * Re-runs a full bind.
     */
    data object Rebind : NodeBindingPlan

    /**
     * 执行细粒度 patch，可同时标记 modifier 是否变化。
     * Applies a fine-grained patch and records whether modifiers changed.
     */
    data class Patch(
        val patch: NodeViewPatch,
        val modifierChanged: Boolean = false,
    ) : NodeBindingPlan
}

/**
 * 细粒度 View patch 的公共标记接口。
 * Marker interface for fine-grained View patches.
 *
 * 具体 patch 只携带 previous/next spec，实际差异判断由对应 applier 完成。
 * Concrete patches only carry previous/next specs; the matching applier performs property-level diffing.
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
