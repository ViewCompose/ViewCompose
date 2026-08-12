package com.viewcompose.renderer.view.tree

import android.view.View
import com.viewcompose.ui.node.NodeType
import com.viewcompose.ui.node.VNode
import com.viewcompose.ui.node.spec.NodeSpec
import kotlin.reflect.KClass

/** Function that binds a VNode to an Android View and may stage retained-child work. */
internal typealias BindBlock = (View, VNode, RetainedSessionSubmission) -> Unit

/** Function that performs an immediate VNode-to-View binding without a retained submission. */
internal typealias ImmediateBindBlock = (View, VNode) -> Unit

/** Function that applies a targeted NodeViewPatch and may stage retained-child work. */
internal typealias PatchApplyBlock = (View, NodeViewPatch, RetainedSessionSubmission) -> Unit

/** Factory that creates a targeted patch from previous and next NodeSpecs. */
internal typealias PatchFactory = (NodeSpec, NodeSpec) -> NodeViewPatch

/** Binder descriptor for one NodeType. */
internal data class NodeBinderDescriptor(
    val nodeType: NodeType,
    val bind: BindBlock,
    val deferUntilCommit: Boolean = false,
    val patch: NodePatchDescriptor? = null,
)

/** Patch descriptor for one NodeSpec and NodeViewPatch pair. */
internal data class NodePatchDescriptor(
    val patchClass: KClass<out NodeViewPatch>,
    val specClass: KClass<out NodeSpec>,
    val factory: PatchFactory,
    val apply: PatchApplyBlock,
    val deferUntilCommit: Boolean = false,
)

/** Builds a binder descriptor for one NodeType. */
internal fun descriptor(
    nodeType: NodeType,
    bind: ImmediateBindBlock,
    patch: NodePatchDescriptor? = null,
): NodeBinderDescriptor = NodeBinderDescriptor(
    nodeType = nodeType,
    bind = { view, node, _ -> bind(view, node) },
    patch = patch,
)

/** Builds a descriptor whose native binding owns retained child render sessions. */
internal fun retainedDescriptor(
    nodeType: NodeType,
    bind: BindBlock,
    patch: NodePatchDescriptor? = null,
): NodeBinderDescriptor = NodeBinderDescriptor(
    nodeType = nodeType,
    bind = bind,
    deferUntilCommit = true,
    patch = patch,
)

/** Builds a type-safe patch descriptor and performs erased casts at the registry boundary. */
internal inline fun <reified S : NodeSpec, reified P : NodeViewPatch> patchDescriptor(
    noinline factory: (S, S) -> P,
    noinline apply: (View, P) -> Unit,
): NodePatchDescriptor {
    return NodePatchDescriptor(
        patchClass = P::class,
        specClass = S::class,
        factory = { previous, next -> factory(previous as S, next as S) },
        apply = { view, patch, _ -> apply(view, patch as P) },
    )
}

/** Builds a patch descriptor whose application may create or render retained child sessions. */
internal inline fun <reified S : NodeSpec, reified P : NodeViewPatch> retainedPatchDescriptor(
    noinline factory: (S, S) -> P,
    noinline apply: (View, P, RetainedSessionSubmission) -> Unit,
): NodePatchDescriptor {
    return NodePatchDescriptor(
        patchClass = P::class,
        specClass = S::class,
        factory = { previous, next -> factory(previous as S, next as S) },
        apply = { view, patch, submission ->
            apply(view, patch as P, submission)
        },
        deferUntilCommit = true,
    )
}
