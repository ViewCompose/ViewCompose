package com.viewcompose.renderer.view.tree

import android.view.View
import com.viewcompose.ui.node.NodeType
import com.viewcompose.ui.node.VNode
import com.viewcompose.ui.node.spec.NodeSpec
import kotlin.reflect.KClass

/**
 * Registry of View binders and patch appliers used by the renderer.
 * Internal View binder/patch applier registry used by the renderer.
 */
internal object NodeViewBinderRegistry {
    private val binders: Map<NodeType, (View, VNode) -> Unit> by lazy {
        NodeBinderDescriptors.bindersByType()
    }
    private val patchAppliers: Map<KClass<out NodeViewPatch>, (View, NodeViewPatch) -> Unit> by lazy {
        NodeBinderDescriptors.patchAppliersByType()
    }

    /**
     * Performs complete binding for a new or rebound View.
     * Performs a full bind for newly created or rebound Views.
     */
    fun bind(
        view: View,
        node: VNode,
    ) {
        binders.getValue(node.type).invoke(view, node)
    }

    /**
     * Applies a targeted patch to a reused View.
     * Applies a fine-grained patch to a reused View.
     */
    fun applyPatch(
        view: View,
        patch: NodeViewPatch,
    ) {
        patchAppliers[patch::class]?.invoke(view, patch)
            ?: error("Unknown patch type: ${patch::class.simpleName}")
    }

    internal fun descriptorsForTest(): List<NodeBinderDescriptor> = NodeBinderDescriptors.all

    internal fun patchAppliersForTest(): Map<KClass<out NodeViewPatch>, (View, NodeViewPatch) -> Unit> = patchAppliers

    internal fun patchFactoriesForTest(): Map<KClass<out NodeSpec>, PatchFactory> = NodeBinderDescriptors.patchFactoriesBySpec()
}
