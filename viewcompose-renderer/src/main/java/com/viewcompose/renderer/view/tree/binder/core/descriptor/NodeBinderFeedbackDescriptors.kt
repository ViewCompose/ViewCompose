package com.viewcompose.renderer.view.tree

import com.google.android.material.progressindicator.CircularProgressIndicator
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.viewcompose.ui.node.NodeType
import com.viewcompose.ui.node.spec.ProgressIndicatorNodeProps
import com.viewcompose.renderer.view.tree.patch.FeedbackNodePatchApplier

/**
 * 注册进度指示器节点的 binder/patch 描述。
 * Registers binder/patch descriptors for progress indicator nodes.
 */
internal fun MutableList<NodeBinderDescriptor>.addFeedbackNodeBinderDescriptors() {
    val progressPatch = patchDescriptor<ProgressIndicatorNodeProps, ProgressIndicatorNodePatch>(
        factory = { previous, next -> ProgressIndicatorNodePatch(previous, next) },
        apply = { view, patch ->
            FeedbackNodePatchApplier.applyProgressIndicatorPatch(
                view = view as android.widget.ProgressBar,
                patch = patch,
            )
        },
    )

    add(
        descriptor(
            nodeType = NodeType.LinearProgressIndicator,
            bind = { view, node ->
                FeedbackViewBinder.bindLinearProgressIndicator(
                    view = view as LinearProgressIndicator,
                    spec = FeedbackViewBinder.readProgressSpec(node),
                )
            },
            patch = progressPatch,
        ),
    )
    add(
        descriptor(
            nodeType = NodeType.CircularProgressIndicator,
            bind = { view, node ->
                FeedbackViewBinder.bindCircularProgressIndicator(
                    view = view as CircularProgressIndicator,
                    spec = FeedbackViewBinder.readProgressSpec(node),
                )
            },
            patch = progressPatch,
        ),
    )
}
