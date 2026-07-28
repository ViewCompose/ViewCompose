package com.viewcompose.renderer.view.tree

import android.view.View
import com.viewcompose.ui.node.NodeType
import com.viewcompose.ui.node.VNode
import com.viewcompose.ui.node.spec.NodeSpec
import kotlin.reflect.KClass

/**
 * 将 VNode 绑定到 Android View 的函数。
 * Function that binds a VNode to an Android View.
 */
internal typealias BindBlock = (View, VNode) -> Unit

/**
 * 将细粒度 NodeViewPatch 应用到 Android View 的函数。
 * Function that applies a fine-grained NodeViewPatch to an Android View.
 */
internal typealias PatchApplyBlock = (View, NodeViewPatch) -> Unit

/**
 * 由 previous/next NodeSpec 创建细粒度 patch 的工厂。
 * Factory that creates a fine-grained patch from previous/next NodeSpec values.
 */
internal typealias PatchFactory = (NodeSpec, NodeSpec) -> NodeViewPatch

/**
 * 单个 NodeType 的 binder 描述。
 * Binder descriptor for one NodeType.
 */
internal data class NodeBinderDescriptor(
    val nodeType: NodeType,
    val bind: BindBlock,
    val patch: NodePatchDescriptor? = null,
)

/**
 * 单个 NodeSpec/NodeViewPatch 组合的 patch 描述。
 * Patch descriptor for one NodeSpec/NodeViewPatch pair.
 */
internal data class NodePatchDescriptor(
    val patchClass: KClass<out NodeViewPatch>,
    val specClass: KClass<out NodeSpec>,
    val factory: PatchFactory,
    val apply: PatchApplyBlock,
)

/**
 * 构建一个 NodeType binder 描述。
 * Builds one NodeType binder descriptor.
 */
internal fun descriptor(
    nodeType: NodeType,
    bind: BindBlock,
    patch: NodePatchDescriptor? = null,
): NodeBinderDescriptor = NodeBinderDescriptor(
    nodeType = nodeType,
    bind = bind,
    patch = patch,
)

/**
 * 构建类型安全的 patch 描述，并在注册表边界做必要的擦除类型转换。
 * Builds a type-safe patch descriptor and performs required erased casts at the registry boundary.
 */
internal inline fun <reified S : NodeSpec, reified P : NodeViewPatch> patchDescriptor(
    noinline factory: (S, S) -> P,
    noinline apply: (View, P) -> Unit,
): NodePatchDescriptor {
    return NodePatchDescriptor(
        patchClass = P::class,
        specClass = S::class,
        factory = { previous, next -> factory(previous as S, next as S) },
        apply = { view, patch -> apply(view, patch as P) },
    )
}
