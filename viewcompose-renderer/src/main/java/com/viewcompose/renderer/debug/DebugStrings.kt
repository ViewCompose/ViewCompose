package com.viewcompose.renderer.debug

import com.viewcompose.ui.node.VNode
import com.viewcompose.renderer.reconcile.InsertPatch
import com.viewcompose.renderer.reconcile.ReconcileResult
import com.viewcompose.renderer.reconcile.RemovePatch
import com.viewcompose.renderer.reconcile.ReusePatch
import com.viewcompose.renderer.view.tree.RenderStats
import com.viewcompose.renderer.view.tree.RenderStructureStats
import com.viewcompose.renderer.view.tree.RenderTreeResult

/**
 * 将 VNode 列表格式化为调试树。
 * Formats a VNode list as a debug tree.
 */
fun List<VNode>.debugTree(): String {
    if (isEmpty()) {
        return "<empty>"
    }
    return joinToString(separator = "\n") { node ->
        node.debugTree(indent = 0)
    }
}

/**
 * 将 reconcile 结果格式化为摘要。
 * Formats a reconcile result as a summary.
 */
fun <T> ReconcileResult<T>.debugSummary(): String {
    val parts = mutableListOf<String>()
    patches.forEach { patch ->
        val line = when (patch) {
            is InsertPatch -> "insert@${patch.targetIndex}:${patch.nextVNode.type}"
            is ReusePatch -> "reuse ${patch.previousIndex}->${patch.targetIndex}:${patch.nextVNode.type}"
        }
        parts += line
    }
    removals.forEach { removal ->
        parts += "remove@${removal.previousIndex}"
    }
    return if (parts.isEmpty()) {
        "<no patches>"
    } else {
        parts.joinToString(separator = "\n")
    }
}

/**
 * 将 render 结果格式化为摘要。
 * Formats a render result as a summary.
 */
fun RenderTreeResult.debugSummary(): String {
    val reconcile = reconcileResult.debugSummary()
    val stats = stats.debugSummary()
    val structure = structure.debugSummary()
    return buildString {
        append(reconcile)
        append("\n--\n")
        append(stats)
        append("\n")
        append(structure)
        if (warnings.isNotEmpty()) {
            append("\n")
            append(warnings.joinToString(separator = "\n") { warning -> "warning: $warning" })
        }
    }
}

/**
 * 将 render 操作统计格式化为摘要。
 * Formats render operation statistics as a summary.
 */
fun RenderStats.debugSummary(): String {
    return "inserts=$inserts reuses=$reuses removals=$removals rebound=$reboundNodes patched=$patchedNodes skipped=$skippedBindings subtreeSkipped=$skippedSubtrees"
}

fun RenderStats.debugBindingsByType(): String {
    if (bindingsByType.isEmpty()) {
        return "<no per-type data>"
    }
    return bindingsByType.entries
        .sortedByDescending { it.value.patched + it.value.rebound }
        .joinToString(separator = "\n") { (type, stats) ->
            val typeName = type::class.simpleName ?: "?"
            "  $typeName: patched=${stats.patched} rebound=${stats.rebound} skipped=${stats.skipped}"
        }
}

fun RenderStructureStats.debugSummary(): String {
    return "vnodeCount=$vnodeCount mountedCount=$mountedNodeCount vnodeDepth=$maxVNodeDepth mountedDepth=$maxMountedDepth"
}

private fun VNode.debugTree(indent: Int): String {
    val prefix = "  ".repeat(indent)
    val label = buildString {
        append(type)
        if (key != null) {
            append("(key=")
            append(key)
            append(")")
        }
    }
    if (children.isEmpty()) {
        return prefix + label
    }
    return buildString {
        append(prefix)
        append(label)
        children.forEach { child ->
            append('\n')
            append(child.debugTree(indent + 1))
        }
    }
}
