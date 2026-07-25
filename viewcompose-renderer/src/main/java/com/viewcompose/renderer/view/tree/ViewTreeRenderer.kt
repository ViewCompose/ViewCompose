package com.viewcompose.renderer.view.tree

import android.content.Context
import android.util.Log
import android.view.ViewGroup
import androidx.annotation.VisibleForTesting
import com.viewcompose.ui.node.VNode
import com.viewcompose.renderer.reconcile.ChildReconciler
import com.viewcompose.renderer.reconcile.ReconcileNode

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

    fun disposeMounted(
        container: ViewGroup,
        mountedNodes: List<MountedNode>,
    ) {
        mountedNodes.forEach { mountedNode ->
            ViewTreeDisposer.disposeMountedNode(mountedNode)
            container.removeView(mountedNode.view)
        }
    }

    fun renderInto(
        container: ViewGroup,
        previous: List<MountedNode>,
        nodes: List<VNode>,
        onReconcile: ((RenderTreeResult) -> Unit)? = null,
    ): RenderTreeResult {
        val transaction = ViewTreePatchPipeline.beginTransaction(
            container = container,
            previous = previous,
        )
        val result = try {
            renderIntoTransaction(
                container = container,
                previous = previous,
                nodes = nodes,
                transaction = transaction,
                collectWarnings = onReconcile != null,
            )
        } catch (error: Throwable) {
            ViewTreePatchPipeline.rollbackTransaction(
                transaction = transaction,
                cause = error,
                defaultRippleColor = DEFAULT_RIPPLE_COLOR,
            )
            throw error
        }
        ViewTreePatchPipeline.commitTransaction(
            transaction = transaction,
            warningTag = WARNING_TAG,
        )
        onReconcile?.invoke(result)
        return result
    }

    private fun renderIntoTransaction(
        container: ViewGroup,
        previous: List<MountedNode>,
        nodes: List<VNode>,
        transaction: ViewTreePatchPipeline.RenderTransaction,
        collectWarnings: Boolean,
    ): RenderTreeResult {
        val renderNodes = AnimatedSizeNodeWrapper.wrapTree(nodes)
        val reconcileResult = ChildReconciler.reconcile(
            previous = previous.map { mountedNode ->
                ReconcileNode(
                    vnode = mountedNode.vnode,
                    payload = mountedNode,
                )
            },
            nodes = renderNodes,
        )
        val pipelineResult = ViewTreePatchPipeline.execute(
            container = container,
            reconcileResult = reconcileResult,
            defaultRippleColor = DEFAULT_RIPPLE_COLOR,
            warningTag = WARNING_TAG,
            emittedModifierWarnings = cappedModifierWarnings(),
            transaction = transaction,
            renderChildren = { childContainer, childPrevious, childNodes ->
                renderIntoTransaction(
                    container = childContainer,
                    previous = childPrevious,
                    nodes = childNodes,
                    transaction = transaction,
                    collectWarnings = false,
                )
            },
        )
        val structure = RenderStructureStats.from(
            nodes = renderNodes,
            mountedNodes = pipelineResult.mountedNodes,
        )
        val warnings = if (!collectWarnings) {
            emptyList()
        } else {
            collectRenderWarnings(
                nodes = renderNodes,
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
