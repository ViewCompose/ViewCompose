package com.viewcompose.renderer.view.tree

import com.viewcompose.ui.node.NodeType
import com.viewcompose.ui.node.spec.NodeSpec
import kotlin.reflect.KClass

/**
 * NodeType binder 和 NodeSpec patch 描述的集中注册表。
 * Central registry of NodeType binders and NodeSpec patch descriptors.
 */
internal object NodeBinderDescriptors {
    /**
     * 全量 descriptor 列表，按功能域分文件构建。
     * Complete descriptor list built by feature-domain files.
     */
    val all: List<NodeBinderDescriptor> by lazy { buildDescriptors() }

    /**
     * 构建 NodeType -> bind 函数映射，并校验 NodeType 不重复。
     * Builds NodeType -> bind mapping and validates uniqueness.
     */
    fun bindersByType(): Map<NodeType, BindBlock> = all.associateByUnique(
        keySelector = { it.nodeType },
        valueSelector = { it.bind },
        duplicateMessage = { "Duplicate binder descriptor for NodeType: $it" },
    )

    /**
     * 构建 NodeViewPatch class -> apply 函数映射。
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
     * 构建 NodeSpec class -> patch factory 映射，供 NodeBindingDiffer 使用。
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
        // descriptor 冲突在启动期暴露，避免 render 中途遇到不可解释的绑定覆盖。
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
        // 多个 NodeType 可共享同一个 patch descriptor，但同一个 spec/patch class 不允许冲突实现。
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
