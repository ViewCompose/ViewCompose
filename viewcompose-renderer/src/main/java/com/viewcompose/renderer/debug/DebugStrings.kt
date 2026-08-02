package com.viewcompose.renderer.debug

import com.viewcompose.ui.node.VNode
import com.viewcompose.renderer.reconcile.InsertPatch
import com.viewcompose.renderer.reconcile.ReconcileResult
import com.viewcompose.renderer.reconcile.RemovePatch
import com.viewcompose.renderer.reconcile.ReusePatch
import com.viewcompose.renderer.view.tree.RenderStats
import com.viewcompose.renderer.view.tree.RenderStructureStats
import com.viewcompose.renderer.view.tree.RenderTreeResult

/** Formats this VNode list as a deterministic, indentation-based debug tree. */
fun List<VNode>.debugTree(): String {
    if (isEmpty()) {
        return "<empty>"
    }
    return joinToString(separator = "\n") { node ->
        node.debugTree(indent = 0)
    }
}

/** Formats this reconciliation result as one ordered operation per line. */
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

/** Formats this render result as reconciliation, binding, structure, and warning sections. */
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

/** Formats aggregate renderer counters as one compact line. */
fun RenderStats.debugSummary(): String {
    return "inserts=$inserts reuses=$reuses removals=$removals rebound=$reboundNodes patched=$patchedNodes skipped=$skippedBindings subtreeSkipped=$skippedSubtrees"
}

/** Formats per-node-type binding counters in descending work order. */
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

/** Formats declarative and mounted tree size and depth as one compact line. */
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
