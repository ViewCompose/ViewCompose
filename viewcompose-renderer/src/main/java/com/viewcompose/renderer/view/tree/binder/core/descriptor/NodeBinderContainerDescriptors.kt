package com.viewcompose.renderer.view.tree

import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.viewcompose.ui.node.NodeType
import com.viewcompose.ui.node.spec.AnimatedSizeHostNodeProps
import com.viewcompose.ui.node.spec.AnimatedVisibilityHostNodeProps
import com.viewcompose.ui.node.spec.BoxNodeProps
import com.viewcompose.ui.node.spec.ColumnNodeProps
import com.viewcompose.ui.node.spec.ConstraintLayoutNodeProps
import com.viewcompose.ui.node.spec.FlowColumnNodeProps
import com.viewcompose.ui.node.spec.FlowRowNodeProps
import com.viewcompose.ui.node.spec.PullToRefreshNodeProps
import com.viewcompose.ui.node.spec.RowNodeProps
import com.viewcompose.ui.node.spec.ScrollableColumnNodeProps
import com.viewcompose.ui.node.spec.ScrollableRowNodeProps
import com.viewcompose.renderer.view.container.DeclarativeBoxLayout
import com.viewcompose.renderer.view.container.DeclarativeAnimatedSizeHostLayout
import com.viewcompose.renderer.view.container.DeclarativeAnimatedVisibilityHostLayout
import com.viewcompose.renderer.view.container.DeclarativeConstraintLayout
import com.viewcompose.renderer.view.container.DeclarativeFlowColumnLayout
import com.viewcompose.renderer.view.container.DeclarativeFlowRowLayout
import com.viewcompose.renderer.view.container.DeclarativeLinearLayout
import com.viewcompose.renderer.view.container.DeclarativeScrollableColumnLayout
import com.viewcompose.renderer.view.container.DeclarativeScrollableRowLayout
import com.viewcompose.renderer.view.tree.patch.ContainerNodePatchApplier

internal fun MutableList<NodeBinderDescriptor>.addContainerNodeBinderDescriptors() {
    val rowPatch = patchDescriptor<RowNodeProps, RowNodePatch>(
        factory = { previous, next -> RowNodePatch(previous, next) },
        apply = { view, patch ->
            ContainerNodePatchApplier.applyRowPatch(
                view = view as DeclarativeLinearLayout,
                patch = patch,
            )
        },
    )
    val columnPatch = patchDescriptor<ColumnNodeProps, ColumnNodePatch>(
        factory = { previous, next -> ColumnNodePatch(previous, next) },
        apply = { view, patch ->
            ContainerNodePatchApplier.applyColumnPatch(
                view = view as DeclarativeLinearLayout,
                patch = patch,
            )
        },
    )
    val boxPatch = patchDescriptor<BoxNodeProps, BoxNodePatch>(
        factory = { previous, next -> BoxNodePatch(previous, next) },
        apply = { view, patch ->
            ContainerNodePatchApplier.applyBoxPatch(
                view = view as DeclarativeBoxLayout,
                patch = patch,
            )
        },
    )
    val constraintLayoutPatch = patchDescriptor<ConstraintLayoutNodeProps, ConstraintLayoutNodePatch>(
        factory = { previous, next -> ConstraintLayoutNodePatch(previous, next) },
        apply = { view, patch ->
            ContainerNodePatchApplier.applyConstraintLayoutPatch(
                view = view as DeclarativeConstraintLayout,
                patch = patch,
            )
        },
    )
    val animatedVisibilityHostPatch = patchDescriptor<AnimatedVisibilityHostNodeProps, AnimatedVisibilityHostNodePatch>(
        factory = { previous, next -> AnimatedVisibilityHostNodePatch(previous, next) },
        apply = { view, patch ->
            ContainerNodePatchApplier.applyAnimatedVisibilityHostPatch(
                view = view as DeclarativeAnimatedVisibilityHostLayout,
                patch = patch,
            )
        },
    )
    val animatedSizeHostPatch = patchDescriptor<AnimatedSizeHostNodeProps, AnimatedSizeHostNodePatch>(
        factory = { previous, next -> AnimatedSizeHostNodePatch(previous, next) },
        apply = { view, patch ->
            ContainerNodePatchApplier.applyAnimatedSizeHostPatch(
                view = view as DeclarativeAnimatedSizeHostLayout,
                patch = patch,
            )
        },
    )
    val scrollableColumnPatch = patchDescriptor<ScrollableColumnNodeProps, ScrollableColumnNodePatch>(
        factory = { previous, next -> ScrollableColumnNodePatch(previous, next) },
        apply = { view, patch ->
            ContainerNodePatchApplier.applyScrollableColumnPatch(
                view = view as DeclarativeScrollableColumnLayout,
                patch = patch,
            )
        },
    )
    val scrollableRowPatch = patchDescriptor<ScrollableRowNodeProps, ScrollableRowNodePatch>(
        factory = { previous, next -> ScrollableRowNodePatch(previous, next) },
        apply = { view, patch ->
            ContainerNodePatchApplier.applyScrollableRowPatch(
                view = view as DeclarativeScrollableRowLayout,
                patch = patch,
            )
        },
    )
    val flowRowPatch = patchDescriptor<FlowRowNodeProps, FlowRowNodePatch>(
        factory = { previous, next -> FlowRowNodePatch(previous, next) },
        apply = { view, patch ->
            ContainerNodePatchApplier.applyFlowRowPatch(
                view = view as DeclarativeFlowRowLayout,
                patch = patch,
            )
        },
    )
    val flowColumnPatch = patchDescriptor<FlowColumnNodeProps, FlowColumnNodePatch>(
        factory = { previous, next -> FlowColumnNodePatch(previous, next) },
        apply = { view, patch ->
            ContainerNodePatchApplier.applyFlowColumnPatch(
                view = view as DeclarativeFlowColumnLayout,
                patch = patch,
            )
        },
    )
    val pullToRefreshPatch = patchDescriptor<PullToRefreshNodeProps, PullToRefreshNodePatch>(
        factory = { previous, next -> PullToRefreshNodePatch(previous, next) },
        apply = { view, patch ->
            ContainerNodePatchApplier.applyPullToRefreshPatch(
                view = view as SwipeRefreshLayout,
                patch = patch,
            )
        },
    )

    add(
        descriptor(
            nodeType = NodeType.Row,
            bind = { view, node ->
                ContainerViewBinder.bindRow(
                    view = view as DeclarativeLinearLayout,
                    spec = ContainerViewBinder.readRowSpec(node),
                )
            },
            patch = rowPatch,
        ),
    )
    add(
        descriptor(
            nodeType = NodeType.Column,
            bind = { view, node ->
                ContainerViewBinder.bindColumn(
                    view = view as DeclarativeLinearLayout,
                    spec = ContainerViewBinder.readColumnSpec(node),
                )
            },
            patch = columnPatch,
        ),
    )
    add(
        descriptor(
            nodeType = NodeType.Box,
            bind = { view, node ->
                ContainerViewBinder.bindBox(
                    view = view as DeclarativeBoxLayout,
                    spec = ContainerViewBinder.readBoxSpec(node),
                )
            },
            patch = boxPatch,
        ),
    )
    add(
        descriptor(
            nodeType = NodeType.ConstraintLayout,
            bind = { view, node ->
                ContainerViewBinder.bindConstraintLayout(
                    view = view as DeclarativeConstraintLayout,
                    spec = ContainerViewBinder.readConstraintLayoutSpec(node),
                )
            },
            patch = constraintLayoutPatch,
        ),
    )
    add(
        descriptor(
            nodeType = NodeType.Surface,
            bind = { view, node ->
                ContainerViewBinder.bindBox(
                    view = view as DeclarativeBoxLayout,
                    spec = ContainerViewBinder.readBoxSpec(node),
                )
            },
            patch = boxPatch,
        ),
    )
    add(
        descriptor(
            nodeType = NodeType.AnimatedVisibilityHost,
            bind = { view, node ->
                ContainerViewBinder.bindAnimatedVisibilityHost(
                    view = view as DeclarativeAnimatedVisibilityHostLayout,
                    spec = ContainerViewBinder.readAnimatedVisibilityHostSpec(node),
                )
            },
            patch = animatedVisibilityHostPatch,
        ),
    )
    add(
        descriptor(
            nodeType = NodeType.AnimatedSizeHost,
            bind = { view, node ->
                ContainerViewBinder.bindAnimatedSizeHost(
                    view = view as DeclarativeAnimatedSizeHostLayout,
                    spec = ContainerViewBinder.readAnimatedSizeHostSpec(node),
                )
            },
            patch = animatedSizeHostPatch,
        ),
    )
    add(
        descriptor(
            nodeType = NodeType.NestedScrollHost,
            bind = { _, _ -> Unit },
        ),
    )
    add(
        descriptor(
            nodeType = NodeType.AndroidView,
            bind = { view, node ->
                val update = ContainerViewBinder.readAndroidViewSpec(node).update
                update?.invoke(view)
            },
        ),
    )
    add(
        descriptor(
            nodeType = NodeType.ScrollableColumn,
            bind = { view, node ->
                ScrollableViewBinder.bindScrollableColumn(
                    view = view as DeclarativeScrollableColumnLayout,
                    spec = ScrollableViewBinder.readScrollableColumnSpec(node),
                )
            },
            patch = scrollableColumnPatch,
        ),
    )
    add(
        descriptor(
            nodeType = NodeType.ScrollableRow,
            bind = { view, node ->
                ScrollableViewBinder.bindScrollableRow(
                    view = view as DeclarativeScrollableRowLayout,
                    spec = ScrollableViewBinder.readScrollableRowSpec(node),
                )
            },
            patch = scrollableRowPatch,
        ),
    )
    add(
        descriptor(
            nodeType = NodeType.FlowRow,
            bind = { view, node ->
                ContainerViewBinder.bindFlowRow(
                    view = view as DeclarativeFlowRowLayout,
                    spec = ContainerViewBinder.readFlowRowSpec(node),
                )
            },
            patch = flowRowPatch,
        ),
    )
    add(
        descriptor(
            nodeType = NodeType.FlowColumn,
            bind = { view, node ->
                ContainerViewBinder.bindFlowColumn(
                    view = view as DeclarativeFlowColumnLayout,
                    spec = ContainerViewBinder.readFlowColumnSpec(node),
                )
            },
            patch = flowColumnPatch,
        ),
    )
    add(
        descriptor(
            nodeType = NodeType.PullToRefresh,
            bind = { view, node ->
                ScrollableViewBinder.bindPullToRefresh(
                    view = view as SwipeRefreshLayout,
                    spec = ScrollableViewBinder.readPullToRefreshSpec(node),
                )
            },
            patch = pullToRefreshPatch,
        ),
    )
}
