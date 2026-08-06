package com.viewcompose.renderer.view.tree

import com.viewcompose.renderer.view.feedback.DeclarativeProgressIndicatorView
import com.viewcompose.ui.node.NodeType
import com.viewcompose.ui.node.spec.ProgressIndicatorNodeProps
import com.viewcompose.renderer.view.tree.patch.FeedbackNodePatchApplier

/**
 * Registers binder and patch descriptors for progress indicators.
 * Registers binder/patch descriptors for progress indicator nodes.
 */
internal fun MutableList<NodeBinderDescriptor>.addFeedbackNodeBinderDescriptors() {
    val progressPatch = patchDescriptor<ProgressIndicatorNodeProps, ProgressIndicatorNodePatch>(
        factory = { previous, next -> ProgressIndicatorNodePatch(previous, next) },
        apply = { view, patch ->
            FeedbackNodePatchApplier.applyProgressIndicatorPatch(
                view = view as DeclarativeProgressIndicatorView,
                patch = patch,
            )
        },
    )

    add(
        descriptor(
            nodeType = NodeType.LinearProgressIndicator,
            bind = { view, node ->
                FeedbackViewBinder.bindProgressIndicator(
                    view = view as DeclarativeProgressIndicatorView,
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
                FeedbackViewBinder.bindProgressIndicator(
                    view = view as DeclarativeProgressIndicatorView,
                    spec = FeedbackViewBinder.readProgressSpec(node),
                )
            },
            patch = progressPatch,
        ),
    )
}
