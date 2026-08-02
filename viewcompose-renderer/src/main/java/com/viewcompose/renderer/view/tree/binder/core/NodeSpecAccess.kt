package com.viewcompose.renderer.view.tree

import com.viewcompose.ui.node.VNode
import com.viewcompose.ui.node.spec.NodeSpec

/**
 * Reads a strongly typed NodeSpec and includes the VNode type when descriptor binding is invalid.
 * Reads a strongly typed NodeSpec and includes VNode type in failures to diagnose descriptor binding errors.
 */
internal inline fun <reified T : NodeSpec> VNode.requireSpec(): T {
    return spec as? T ?: error(
        "VNode(type=$type) requires spec=${T::class.simpleName}, but was ${spec::class.simpleName}",
    )
}
