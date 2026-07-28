package com.viewcompose.renderer.view.tree

import android.content.Context
import android.util.Log
import android.view.ViewGroup
import androidx.annotation.VisibleForTesting
import com.viewcompose.ui.node.VNode
import com.viewcompose.renderer.reconcile.ChildReconciler
import com.viewcompose.renderer.reconcile.ReconcileNode

/**
 * Android View 树 renderer 的核心入口。
 * Core entry point for rendering into the Android View tree.
 *
 * renderer 将 VNode 列表包装为平台需要的 host 节点，执行 reconcile/patch 事务，并返回诊断结果。
 * The renderer wraps VNodes into platform host nodes, executes reconcile/patch transactions, and returns diagnostics.
 */
object ViewTreeRenderer {
    private const val DEFAULT_RIPPLE_COLOR: Int = 0x22000000
    private const val WARNING_TAG: String = "ViewCompose"
    private const val MAX_WARNING_ENTRIES: Int = 200
    private val emittedModifierWarnings = mutableSetOf<String>()
    private val emittedStructureWarnings = mutableSetOf<String>()

    @VisibleForTesting
    fun resetWarnings() {
        emittedModifierWarnings.clear()
        emittedStructureWarnings.clear()
    }

    /**
     * 释放已挂载节点并从 container 移除对应 View。
     * Disposes mounted nodes and removes their Views from the container.
     */
    fun disposeMounted(
        container: ViewGroup,
        mountedNodes: List<MountedNode>,
    ): List<RenderTreeCommitFailure> {
        val failures = mutableListOf<RenderTreeCommitFailure>()
        mountedNodes.forEach { mountedNode ->
            try {
                ViewTreeDisposer.disposeMountedNode(mountedNode)
            } catch (error: Throwable) {
                failures += error.toRenderTreeCommitFailures(
                    fallbackNodeKey = mountedNode.vnode.key,
                )
                Log.e(
                    WARNING_TAG,
                    "Failed to dispose ${mountedNode.vnode.type} node.",
                    error,
                )
            } finally {
                container.removeView(mountedNode.view)
            }
        }
        return failures
    }

    /**
     * 将 VNode 列表渲染进 container。
     * Renders a VNode list into the container.
     *
     * 所有 View 结构改动都先进入事务；失败时 rollback，成功后再 commit deferred removal。
     * All View structure changes go through a transaction; failures roll back, success commits deferred removals.
     */
    fun renderInto(
        container: ViewGroup,
        previous: List<MountedNode>,
        nodes: List<VNode>,
        collectDiagnostics: Boolean = true,
        onReconcile: ((RenderTreeResult) -> Unit)? = null,
    ): RenderTreeResult {
        // wrapper 在 reconcile 前插入平台 host 节点，使 modifier 语义变成普通树结构。
        // Wrappers insert platform host nodes before reconciliation, turning modifier semantics into normal tree structure.
        val renderNodes = AnimatedSizeNodeWrapper.wrapTree(
            NestedScrollNodeWrapper.wrapTree(nodes),
        )
        val transaction = ViewTreePatchPipeline.beginTransaction()
        val result = try {
            renderIntoTransaction(
                container = container,
                previous = previous,
                nodes = renderNodes,
                transaction = transaction,
                collectStats = collectDiagnostics,
                collectStructure = collectDiagnostics,
                collectWarnings = collectDiagnostics && onReconcile != null,
                parentNodeKey = null,
            )
        } catch (error: Throwable) {
            ViewTreePatchPipeline.rollbackTransaction(
                transaction = transaction,
                cause = error,
                defaultRippleColor = DEFAULT_RIPPLE_COLOR,
            )
            throw error
        }
        val commitEffects = transaction.commitEffects.toList()
        // 删除节点延迟到 commit 后释放，避免中途失败时无法恢复旧树。
        // Removed nodes are disposed after commit so the old tree can still be restored if patching fails.
        val commitFailures = ViewTreePatchPipeline.commitTransaction(
            transaction = transaction,
            warningTag = WARNING_TAG,
        )
        val committedResult = result.copy(
            commitEffects = commitEffects,
            commitFailures = commitFailures,
        )
        onReconcile?.invoke(committedResult)
        return committedResult
    }

    private fun renderIntoTransaction(
        container: ViewGroup,
        previous: List<MountedNode>,
        nodes: List<VNode>,
        transaction: ViewTreePatchPipeline.RenderTransaction,
        collectStats: Boolean,
        collectStructure: Boolean,
        collectWarnings: Boolean,
        parentNodeKey: Any?,
    ): RenderTreeResult {
        val reconcileResult = ChildReconciler.reconcile(
            previous = previous.map { mountedNode ->
                ReconcileNode(
                    vnode = mountedNode.vnode,
                    payload = mountedNode,
                )
            },
            nodes = nodes,
        )
        val pipelineResult = ViewTreePatchPipeline.execute(
            container = container,
            reconcileResult = reconcileResult,
            defaultRippleColor = DEFAULT_RIPPLE_COLOR,
            warningTag = WARNING_TAG,
            emittedModifierWarnings = cappedModifierWarnings(),
            transaction = transaction,
            collectStats = collectStats,
            parentNodeKey = parentNodeKey,
            renderChildren = { childContainer, childPrevious, childNodes, childParentKey ->
                renderIntoTransaction(
                    container = childContainer,
                    previous = childPrevious,
                    nodes = childNodes,
                    transaction = transaction,
                    collectStats = collectStats,
                    collectStructure = false,
                    collectWarnings = false,
                    parentNodeKey = childParentKey,
                )
            },
        )
        val structure = if (collectStructure) {
            RenderStructureStats.from(
                nodes = nodes,
                mountedNodes = pipelineResult.mountedNodes,
            )
        } else {
            RenderStructureStats()
        }
        val warnings = if (!collectWarnings) {
            emptyList()
        } else {
            collectRenderWarnings(
                nodes = nodes,
                structure = structure,
                stats = pipelineResult.stats,
            )
        }
        return RenderTreeResult(
            mountedNodes = pipelineResult.mountedNodes,
            reconcileResult = reconcileResult,
            stats = pipelineResult.stats,
            structure = structure,
            warnings = warnings,
            tree = if (collectStructure) RenderTreeNode.from(nodes) else emptyList(),
            patches = if (collectStructure) transaction.patchRecords.toList() else emptyList(),
        )
    }

    private fun cappedModifierWarnings(): MutableSet<String> {
        if (emittedModifierWarnings.size >= MAX_WARNING_ENTRIES) {
            emittedModifierWarnings.clear()
        }
        return emittedModifierWarnings
    }

    private fun collectRenderWarnings(
        nodes: List<VNode>,
        structure: RenderStructureStats,
        stats: RenderStats,
    ): List<String> {
        val warnings = RenderWarningCollector.collect(
            nodes = nodes,
            structure = structure,
            stats = stats,
        )
        warnings.forEach { warning ->
            val key = "structure|$warning"
            if (emittedStructureWarnings.size >= MAX_WARNING_ENTRIES) {
                emittedStructureWarnings.clear()
            }
            if (emittedStructureWarnings.add(key)) {
                Log.w(WARNING_TAG, warning)
            }
        }
        return warnings
    }

}
