package com.viewcompose.renderer.view.tree

import com.viewcompose.ui.node.VNode

/**
 * 根据渲染结构和复用统计生成诊断 warning。
 * Generates diagnostic warnings from render structure and reuse statistics.
 */
internal object RenderWarningCollector {
    private const val DEEP_TREE_WARNING_DEPTH: Int = 10
    private const val LARGE_REBIND_WARNING_COUNT: Int = 8
    private const val LARGE_STRUCTURE_CHURN_COUNT: Int = 10

    fun collect(
        nodes: List<VNode>,
        structure: RenderStructureStats,
        stats: RenderStats,
    ): List<String> {
        return buildList {
            addAll(collectStructureWarnings(structure))
            addAll(collectIdentityWarnings(nodes))
            addAll(collectChurnWarnings(stats))
        }
    }

    private fun collectStructureWarnings(
        structure: RenderStructureStats,
    ): List<String> {
        val warnings = mutableListOf<String>()
        if (structure.maxMountedDepth > DEEP_TREE_WARNING_DEPTH) {
            warnings += "Deep mounted view tree detected: depth=${structure.maxMountedDepth} exceeds recommended limit $DEEP_TREE_WARNING_DEPTH."
        }
        return warnings
    }

    private fun collectIdentityWarnings(
        nodes: List<VNode>,
    ): List<String> {
        val warnings = mutableListOf<String>()
        collectIdentityWarningsRecursively(
            parentLabel = "root",
            siblings = nodes,
            warnings = warnings,
        )
        return warnings
    }

    private fun collectIdentityWarningsRecursively(
        parentLabel: String,
        siblings: List<VNode>,
        warnings: MutableList<String>,
    ) {
        if (siblings.isEmpty()) {
            return
        }
        // sibling key 只要求同一父节点内唯一；不同父节点可复用相同 key。
        // Sibling keys only need to be unique under the same parent; different parents may reuse keys.
        val duplicateKeys = siblings
            .mapNotNull { it.key }
            .groupingBy { it }
            .eachCount()
            .filterValues { count -> count > 1 }
            .keys
            .toList()
        if (duplicateKeys.isNotEmpty()) {
            warnings += "Duplicate sibling keys under $parentLabel: $duplicateKeys."
        }

        val repeatedUnkeyedTypes = siblings
            .filter { it.key == null }
            .groupingBy { it.type }
            .eachCount()
            .filterValues { count -> count > 1 }
        if (repeatedUnkeyedTypes.isNotEmpty()) {
            val summary = repeatedUnkeyedTypes.entries.joinToString { "${it.key} x${it.value}" }
            warnings += "Repeated unkeyed siblings under $parentLabel: $summary. Add keys to stabilize reuse and reordering."
        }

        siblings.forEach { child ->
            collectIdentityWarningsRecursively(
                parentLabel = child.debugLabel(),
                siblings = child.children,
                warnings = warnings,
            )
        }
    }

    private fun collectChurnWarnings(
        stats: RenderStats,
    ): List<String> {
        val warnings = mutableListOf<String>()
        // 高 rebind 说明节点身份或 patch 能力可能不足，优先提示给诊断 UI。
        // High rebind count suggests weak identity or patch coverage, so surface it to diagnostics first.
        if (stats.reboundNodes >= LARGE_REBIND_WARNING_COUNT && stats.patchedNodes <= stats.reboundNodes / 2) {
            warnings += "High rebind churn detected: rebound=${stats.reboundNodes}, patched=${stats.patchedNodes}, skipped=${stats.skippedBindings}, subtreeSkipped=${stats.skippedSubtrees}."
        }
        val structureChurn = stats.inserts + stats.removals
        if (structureChurn >= LARGE_STRUCTURE_CHURN_COUNT) {
            warnings += "High structural churn detected: inserts=${stats.inserts}, removals=${stats.removals}."
        }
        return warnings
    }

    private fun VNode.debugLabel(): String {
        return buildString {
            append(type)
            if (key != null) {
                append("(key=")
                append(key)
                append(")")
            }
        }
    }
}
