package com.viewcompose.studio.preview

import java.nio.file.Path
import kotlin.math.abs

internal fun findMappedNativeViewAt(
    views: List<StudioPreviewNativeViewNode>,
    x: Int,
    y: Int,
): StudioPreviewNativeViewNode? {
    return views.asReversed().firstNotNullOfOrNull { view ->
        view.findMappedDescendantAt(x, y)
    }
}

internal fun findNativeViewByNodeId(
    views: List<StudioPreviewNativeViewNode>,
    nodeId: String,
): StudioPreviewNativeViewNode? {
    views.forEach { view ->
        if (view.nodeId == nodeId) return view
        findNativeViewByNodeId(view.children, nodeId)?.let { child ->
            return child
        }
    }
    return null
}

internal class PreviewRuntimeNodeIndex private constructor(
    private val entries: Map<String, RuntimeNodeEntry>,
) {
    fun contains(nodeId: String): Boolean = nodeId in entries

    fun findNodeId(
        filePath: String,
        line: Int,
    ): String? {
        if (line <= 0) return null
        val fileName = runCatching { Path.of(filePath).fileName?.toString() }
            .getOrNull()
            ?.takeIf(String::isNotBlank)
            ?: return null
        val match = entries.values.asSequence()
            .flatMap { entry ->
                entry.sourceCallSites.asSequence()
                    .filter { callSite -> callSite.fileName == fileName }
                    .map { callSite ->
                        RuntimeSourceMatch(
                            entry = entry,
                            distance = abs(callSite.lineNumber - line),
                        )
                    }
            }
            .filter { match -> match.distance <= MAXIMUM_CARET_LINE_DISTANCE }
            .minWithOrNull(
                compareBy<RuntimeSourceMatch> { match -> match.distance }
                    .thenBy { match -> match.entry.synthetic }
                    .thenByDescending { match -> match.entry.hasNativeView }
                    .thenByDescending { match -> match.entry.depth }
                    .thenBy { match -> match.entry.order },
            )
        return match?.entry?.nodeId
    }

    companion object {
        fun from(snapshot: StudioPreviewRenderSnapshot): PreviewRuntimeNodeIndex {
            val entries = LinkedHashMap<String, MutableRuntimeNodeEntry>()
            var order = 0
            fun record(
                nodeId: String?,
                sourceCallSites: List<StudioPreviewSourceCallSite>,
                synthetic: Boolean,
                depth: Int,
                nativeView: Boolean,
            ) {
                if (nodeId == null) return
                val entry = entries.getOrPut(nodeId) {
                    MutableRuntimeNodeEntry(
                        nodeId = nodeId,
                        order = order++,
                    )
                }
                entry.sourceCallSites += sourceCallSites
                entry.synthetic = entry.synthetic && synthetic
                entry.depth = maxOf(entry.depth, depth)
                entry.hasNativeView = entry.hasNativeView || nativeView
            }
            fun visitRenderNode(
                node: StudioPreviewRenderTreeNode,
                depth: Int,
            ) {
                record(
                    nodeId = node.nodeId,
                    sourceCallSites = node.sourceCallSites,
                    synthetic = node.synthetic,
                    depth = depth,
                    nativeView = false,
                )
                node.children.forEach { child -> visitRenderNode(child, depth + 1) }
            }
            fun visitNativeView(
                view: StudioPreviewNativeViewNode,
                depth: Int,
            ) {
                record(
                    nodeId = view.nodeId,
                    sourceCallSites = view.sourceCallSites,
                    synthetic = view.synthetic,
                    depth = depth,
                    nativeView = true,
                )
                view.children.forEach { child -> visitNativeView(child, depth + 1) }
            }
            snapshot.tree.forEach { node -> visitRenderNode(node, depth = 0) }
            snapshot.nativeViewTree.forEach { view -> visitNativeView(view, depth = 0) }
            return PreviewRuntimeNodeIndex(
                entries = entries.mapValues { (_, entry) -> entry.toImmutable() },
            )
        }
    }
}

private fun StudioPreviewNativeViewNode.findMappedDescendantAt(
    x: Int,
    y: Int,
): StudioPreviewNativeViewNode? {
    if (visibility != "VISIBLE" || !bounds.contains(x, y)) return null
    children.asReversed().forEach { child ->
        child.findMappedDescendantAt(x, y)?.let { mappedChild ->
            return mappedChild
        }
    }
    return takeIf { view -> view.sourceCallSites.isNotEmpty() }
}

private data class MutableRuntimeNodeEntry(
    val nodeId: String,
    val order: Int,
    val sourceCallSites: LinkedHashSet<StudioPreviewSourceCallSite> = LinkedHashSet(),
    var synthetic: Boolean = true,
    var depth: Int = 0,
    var hasNativeView: Boolean = false,
) {
    fun toImmutable(): RuntimeNodeEntry {
        return RuntimeNodeEntry(
            nodeId = nodeId,
            sourceCallSites = sourceCallSites.toList(),
            synthetic = synthetic,
            depth = depth,
            hasNativeView = hasNativeView,
            order = order,
        )
    }
}

private data class RuntimeNodeEntry(
    val nodeId: String,
    val sourceCallSites: List<StudioPreviewSourceCallSite>,
    val synthetic: Boolean,
    val depth: Int,
    val hasNativeView: Boolean,
    val order: Int,
)

private data class RuntimeSourceMatch(
    val entry: RuntimeNodeEntry,
    val distance: Int,
)

private const val MAXIMUM_CARET_LINE_DISTANCE = 8

private fun StudioPreviewLayoutBounds.contains(
    x: Int,
    y: Int,
): Boolean {
    return width > 0 &&
        height > 0 &&
        x >= left &&
        x < right &&
        y >= top &&
        y < bottom
}
