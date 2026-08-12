package com.viewcompose.renderer.view.tree

import com.viewcompose.ui.node.NodeType
import com.viewcompose.ui.node.spec.NodeSpec
import kotlin.reflect.KClass

/**
 * Central registry of NodeType binders and NodeSpec patch descriptors.
 * Central registry of NodeType binders and NodeSpec patch descriptors.
 */
internal object NodeBinderDescriptors {
    /**
     * Complete descriptor list assembled from feature-specific files.
     * Complete descriptor list built by feature-domain files.
     */
    val all: List<NodeBinderDescriptor> by lazy { buildDescriptors() }

    /**
     * Builds the NodeType-to-bind-function map and rejects duplicate NodeTypes.
     * Builds NodeType -> bind mapping and validates uniqueness.
     */
    fun bindersByType(): Map<NodeType, BindBlock> = all.associateByUnique(
        keySelector = { it.nodeType },
            valueSelector = { it.bind },
        duplicateMessage = { "Duplicate binder descriptor for NodeType: $it" },
    )

    /**
     * Builds the NodeViewPatch-class-to-apply-function map.
     * Builds NodeViewPatch class -> apply function mapping.
     */
    fun patchAppliersByType(): Map<KClass<out NodeViewPatch>, PatchApplyBlock> = all
        .uniquePatchDescriptorsBy(
            keySelector = { it.patchClass },
            duplicateMessage = {
                "Conflicting patch applier descriptor for NodeViewPatch: ${it.simpleName}"
            },
        )
        .associateByUnique(
            keySelector = { it.patchClass },
            valueSelector = { it.apply },
            duplicateMessage = { "Duplicate patch applier descriptor for NodeViewPatch: ${it.simpleName}" },
        )

    /**
     * Builds the NodeSpec-class-to-patch-factory map used by NodeBindingDiffer.
     * Builds NodeSpec class -> patch factory mapping for NodeBindingDiffer.
     */
    fun patchFactoriesBySpec(): Map<KClass<out NodeSpec>, PatchFactory> = all
        .uniquePatchDescriptorsBy(
            keySelector = { it.specClass },
            duplicateMessage = { "Conflicting patch factory descriptor for NodeSpec: ${it.simpleName}" },
        )
        .associateByUnique(
            keySelector = { it.specClass },
            valueSelector = { it.factory },
            duplicateMessage = { "Duplicate patch factory descriptor for NodeSpec: ${it.simpleName}" },
        )

    private inline fun <K, V, T> List<T>.associateByUnique(
        keySelector: (T) -> K,
        valueSelector: (T) -> V,
        duplicateMessage: (K) -> String,
    ): Map<K, V> {
        // Expose descriptor conflicts during startup instead of allowing unexplained binding replacement during render.
        // Descriptor conflicts fail during startup to avoid unexplained binding overrides mid-render.
        val result = LinkedHashMap<K, V>(size)
        for (item in this) {
            val key = keySelector(item)
            require(!result.containsKey(key)) { duplicateMessage(key) }
            result[key] = valueSelector(item)
        }
        return result
    }

    private inline fun <K> List<NodeBinderDescriptor>.uniquePatchDescriptorsBy(
        keySelector: (NodePatchDescriptor) -> K,
        duplicateMessage: (K) -> String,
    ): List<NodePatchDescriptor> {
        // Multiple NodeTypes may share a descriptor, but one spec or patch class cannot have conflicting implementations.
        // Multiple NodeTypes may share one patch descriptor, but a spec/patch class cannot have conflicting implementations.
        val result = LinkedHashMap<K, NodePatchDescriptor>()
        for (descriptor in this) {
            val patch = descriptor.patch ?: continue
            val key = keySelector(patch)
            val existing = result[key]
            if (existing == null) {
                result[key] = patch
                continue
            }
            require(existing === patch || (existing.specClass == patch.specClass && existing.patchClass == patch.patchClass)) {
                duplicateMessage(key)
            }
        }
        return result.values.toList()
    }

    private fun buildDescriptors(): List<NodeBinderDescriptor> {
        return buildList {
            addContentNodeBinderDescriptors()
            addInputNodeBinderDescriptors()
            addFeedbackNodeBinderDescriptors()
            addMediaNodeBinderDescriptors()
            addContainerNodeBinderDescriptors()
            addCollectionNodeBinderDescriptors()
        }
    }
}
