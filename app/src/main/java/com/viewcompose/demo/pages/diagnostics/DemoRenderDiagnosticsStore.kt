package com.viewcompose

import com.viewcompose.runtime.composition.CompositionDiagnostics
import com.viewcompose.widget.core.RenderPatchRecord
import com.viewcompose.widget.core.RenderStats
import com.viewcompose.widget.core.RenderStructureStats
import com.viewcompose.widget.core.RenderTreeNode
import com.viewcompose.widget.core.RenderTreeResult

/**
 * 记录一次渲染后的诊断快照，供 Diagnostics 页面展示 renderer 是否进入 patch-active 路径。
 * Records diagnostics after one render so the Diagnostics page can show whether the renderer used the patch-active path.
 */
internal data class DemoRenderSnapshot(
    val renderCount: Int = 0,
    val stats: RenderStats = RenderStats(),
    val structure: RenderStructureStats = RenderStructureStats(),
    val warnings: List<String> = emptyList(),
    val tree: List<RenderTreeNode> = emptyList(),
    val patches: List<RenderPatchRecord> = emptyList(),
    val composition: CompositionDiagnostics = CompositionDiagnostics(),
    val updatedAtMillis: Long = 0L,
) {
    val hasPatchActivity: Boolean
        get() = stats.patchedNodes > 0 || stats.skippedBindings > 0 || stats.reboundNodes > 0
}

/**
 * 保存最近的 demo 渲染诊断历史，作为 Activity 与声明式诊断页面之间的轻量共享状态。
 * Stores recent demo render diagnostics as lightweight shared state between Activities and the declarative diagnostics page.
 */
internal object DemoRenderDiagnosticsStore {
    private const val MAX_HISTORY = 12

    @Volatile
    private var latestSnapshot: DemoRenderSnapshot = DemoRenderSnapshot()

    @Volatile
    private var snapshotHistory: List<DemoRenderSnapshot> = listOf(latestSnapshot)

    fun record(
        result: RenderTreeResult,
    ) {
        val previous = latestSnapshot
        val snapshot = DemoRenderSnapshot(
            renderCount = previous.renderCount + 1,
            stats = result.stats,
            structure = result.structure,
            warnings = result.warnings,
            tree = result.tree,
            patches = result.patches,
            composition = result.composition,
            updatedAtMillis = System.currentTimeMillis(),
        )
        latestSnapshot = snapshot
        snapshotHistory = listOf(snapshot) + snapshotHistory.take(MAX_HISTORY - 1)
    }

    fun reset() {
        latestSnapshot = DemoRenderSnapshot()
        snapshotHistory = listOf(latestSnapshot)
    }

    fun latestSnapshot(): DemoRenderSnapshot = latestSnapshot

    fun latestPatchActiveSnapshot(): DemoRenderSnapshot? {
        return snapshotHistory.firstOrNull { it.hasPatchActivity }
    }

    fun recentSnapshots(): List<DemoRenderSnapshot> = snapshotHistory
}
