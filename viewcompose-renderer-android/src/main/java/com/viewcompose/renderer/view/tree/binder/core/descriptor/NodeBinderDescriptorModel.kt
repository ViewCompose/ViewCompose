package com.viewcompose.renderer.view.tree

import android.view.View
import com.viewcompose.ui.node.NodeType
import com.viewcompose.ui.node.VNode
import com.viewcompose.ui.node.spec.NodeSpec
import kotlin.reflect.KClass

/**
 * Function that binds a VNode to an Android View.
 * Function that binds a VNode to an Android View.
 */
internal typealias BindBlock = (View, VNode) -> Unit

/**
 * Function that applies a targeted NodeViewPatch to an Android View.
 * Function that applies a fine-grained NodeViewPatch to an Android View.
 */
internal typealias PatchApplyBlock = (View, NodeViewPatch) -> Unit

/**
 * Factory that creates a targeted patch from previous and next NodeSpecs.
 * Factory that creates a fine-grained patch from previous/next NodeSpec values.
 */
internal typealias PatchFactory = (NodeSpec, NodeSpec) -> NodeViewPatch

/**
 * Binder descriptor for one NodeType.
 * Binder descriptor for one NodeType.
 */
internal data class NodeBinderDescriptor(
    val nodeType: NodeType,
    val bind: BindBlock,
    val patch: NodePatchDescriptor? = null,
)

/**
 * Patch descriptor for one NodeSpec and NodeViewPatch pair.
 * Patch descriptor for one NodeSpec/NodeViewPatch pair.
 */
internal data class NodePatchDescriptor(
    val patchClass: KClass<out NodeViewPatch>,
    val specClass: KClass<out NodeSpec>,
    val factory: PatchFactory,
    val apply: PatchApplyBlock,
)

/**
 * Builds a binder descriptor for one NodeType.
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
 * Builds a type-safe patch descriptor and performs required erased casts at the registry boundary.
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
