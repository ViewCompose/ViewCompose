package com.viewcompose.renderer.view.tree

import com.viewcompose.ui.node.VNode
import com.viewcompose.ui.node.spec.NodeSpec

/**
 * 读取强类型 NodeSpec，失败时带上 VNode type 方便定位 descriptor 绑定错误。
 * Reads a strongly typed NodeSpec and includes VNode type in failures to diagnose descriptor binding errors.
 */
internal inline fun <reified T : NodeSpec> VNode.requireSpec(): T {
    return spec as? T ?: error(
        "VNode(type=$type) requires spec=${T::class.simpleName}, but was ${spec::class.simpleName}",
    )
}
