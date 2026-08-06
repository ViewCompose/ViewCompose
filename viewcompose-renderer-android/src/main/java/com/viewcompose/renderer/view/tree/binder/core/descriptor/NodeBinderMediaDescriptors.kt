package com.viewcompose.renderer.view.tree

import com.viewcompose.ui.node.NodeType
import com.viewcompose.ui.node.spec.IconButtonNodeProps
import com.viewcompose.ui.node.spec.ImageNodeProps
import com.viewcompose.renderer.view.tree.patch.MediaNodePatchApplier

/**
 * Registers binder and patch descriptors for image and icon-button nodes.
 * Registers binder/patch descriptors for image and icon button nodes.
 */
internal fun MutableList<NodeBinderDescriptor>.addMediaNodeBinderDescriptors() {
    val imagePatch = patchDescriptor<ImageNodeProps, ImageNodePatch>(
        factory = { previous, next -> ImageNodePatch(previous, next) },
        apply = { view, patch ->
            MediaNodePatchApplier.applyImagePatch(
                view = view as android.widget.ImageView,
                patch = patch,
            )
        },
    )
    val iconButtonPatch = patchDescriptor<IconButtonNodeProps, IconButtonNodePatch>(
        factory = { previous, next -> IconButtonNodePatch(previous, next) },
        apply = { view, patch ->
            MediaNodePatchApplier.applyIconButtonPatch(
                view = view as android.widget.ImageButton,
                patch = patch,
            )
        },
    )

    add(
        descriptor(
            nodeType = NodeType.Image,
            bind = { view, node ->
                MediaViewBinder.bindImage(
                    view = view as android.widget.ImageView,
                    spec = MediaViewBinder.readImageSpec(node),
                )
            },
            patch = imagePatch,
        ),
    )
    add(
        descriptor(
            nodeType = NodeType.IconButton,
            bind = { view, node ->
                MediaViewBinder.bindImage(
                    view = view as android.widget.ImageView,
                    spec = MediaViewBinder.readImageSpec(node),
                )
                MediaViewBinder.bindIconButton(
                    view = view as android.widget.ImageButton,
                    enabled = MediaViewBinder.readIconButtonEnabled(node),
                )
            },
            patch = iconButtonPatch,
        ),
    )
}
