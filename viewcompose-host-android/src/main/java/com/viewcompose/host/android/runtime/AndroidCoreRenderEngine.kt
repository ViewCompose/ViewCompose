package com.viewcompose.host.android.runtime

import android.view.ViewGroup
import com.viewcompose.renderer.view.tree.MountedNode
import com.viewcompose.renderer.view.tree.ViewTreeRenderer
import com.viewcompose.shadow.android.ShadowDecorationHostLayout
import com.viewcompose.ui.node.VNode
import com.viewcompose.ui.node.spec.AndroidViewOperation
import com.viewcompose.widget.core.CoreRenderEngine
import com.viewcompose.widget.core.CoreRenderCommitEffect
import com.viewcompose.widget.core.CoreRenderCommitFailure
import com.viewcompose.widget.core.CoreRenderFrame
import com.viewcompose.widget.core.NodeTypeBindingStats
import com.viewcompose.widget.core.RenderStats
import com.viewcompose.widget.core.RenderStructureStats
import com.viewcompose.widget.core.RenderTreeResult
import com.viewcompose.widget.core.RenderTreeNode
import com.viewcompose.widget.core.RenderPatchRecord
import com.viewcompose.widget.core.RenderPatchOperation
import com.viewcompose.widget.core.RenderFailureOperation
import java.util.WeakHashMap

/**
 * widget-core 与 renderer 模块之间的 Android 渲染引擎适配器。
 * Android render-engine adapter between widget-core and the renderer module.
 *
 * widget-core 只依赖 CoreRenderEngine 契约，具体 MountedNode 和诊断类型在这里转换。
 * widget-core depends only on CoreRenderEngine, while concrete MountedNode and diagnostic types are translated here.
 */
class AndroidCoreRenderEngine : CoreRenderEngine {
    private val decorationHosts = WeakHashMap<ViewGroup, ShadowDecorationHostLayout>()

    override fun renderInto(
        container: ViewGroup,
        previousMountedNodes: List<Any>,
        nodes: List<VNode>,
        collectDiagnostics: Boolean,
    ): CoreRenderFrame {
        val renderHost = resolveRenderHost(container)
        val result = ViewTreeRenderer.renderInto(
            container = renderHost,
            previous = previousMountedNodes.filterIsInstance<MountedNode>(),
            nodes = nodes,
            collectDiagnostics = collectDiagnostics,
        )
        return CoreRenderFrame(
            mountedNodes = result.mountedNodes,
            renderStats = result.stats.toCoreStats(),
            renderResult = if (collectDiagnostics) result.toCoreResult() else null,
            commitEffects = result.commitEffects.map { effect ->
                CoreRenderCommitEffect(
                    operation = effect.operation.toCoreOperation(),
                    nodeKey = effect.nodeKey,
                    commit = effect.commit,
                )
            },
            commitFailures = result.commitFailures.map { failure ->
                CoreRenderCommitFailure(
                    operation = failure.operation?.toCoreOperation(),
                    nodeKey = failure.nodeKey,
                    cause = failure.cause,
                )
            },
        )
    }

    override fun disposeMounted(
        container: ViewGroup,
        mountedNodes: List<Any>,
    ): List<CoreRenderCommitFailure> {
        val renderHost = decorationHosts[container] ?: container
        val failures = ViewTreeRenderer.disposeMounted(
            container = renderHost,
            mountedNodes = mountedNodes.filterIsInstance<MountedNode>(),
        ).map { failure ->
            CoreRenderCommitFailure(
                operation = failure.operation?.toCoreOperation(),
                nodeKey = failure.nodeKey,
                cause = failure.cause,
            )
        }
        if (renderHost !== container && renderHost.childCount == 0) {
            container.removeView(renderHost)
            decorationHosts.remove(container)
        }
        return failures
    }

    private fun resolveRenderHost(container: ViewGroup): ViewGroup {
        if (container is ShadowDecorationHostLayout) return container
        val existing = decorationHosts[container]
        if (existing != null) {
            if (existing.parent !== container) {
                (existing.parent as? ViewGroup)?.removeView(existing)
                container.addView(
                    existing,
                    ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                    ),
                )
            }
            return existing
        }
        return ShadowDecorationHostLayout(container.context).also { host ->
            decorationHosts[container] = host
            container.addView(
                host,
                ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                ),
            )
        }
    }

    private fun com.viewcompose.renderer.view.tree.RenderStats.toCoreStats(): RenderStats {
        return RenderStats(
            inserts = inserts,
            reuses = reuses,
            removals = removals,
            reboundNodes = reboundNodes,
            patchedNodes = patchedNodes,
            skippedBindings = skippedBindings,
            skippedSubtrees = skippedSubtrees,
            bindingsByType = bindingsByType.mapValues { (_, value) ->
                NodeTypeBindingStats(
                    rebound = value.rebound,
                    patched = value.patched,
                    skipped = value.skipped,
                )
            },
        )
    }

    private fun com.viewcompose.renderer.view.tree.RenderTreeResult.toCoreResult(): RenderTreeResult {
        // 诊断结构复制为 core 类型，避免把 renderer 内部类型泄漏给 widget-core 调用方。
        // Diagnostics are copied into core types so renderer internals do not leak to widget-core callers.
        return RenderTreeResult(
            stats = stats.toCoreStats(),
            structure = RenderStructureStats(
                vnodeCount = structure.vnodeCount,
                mountedNodeCount = structure.mountedNodeCount,
                maxVNodeDepth = structure.maxVNodeDepth,
                maxMountedDepth = structure.maxMountedDepth,
            ),
            warnings = warnings,
            tree = tree.map { node -> node.toCoreNode() },
            patches = patches.map { patch -> patch.toCorePatch() },
        )
    }

    private fun com.viewcompose.renderer.view.tree.RenderTreeNode.toCoreNode(): RenderTreeNode {
        return RenderTreeNode(
            type = type,
            key = key,
            toolingMetadata = toolingMetadata,
            children = children.map { child -> child.toCoreNode() },
        )
    }

    private fun com.viewcompose.renderer.view.tree.RenderPatchRecord.toCorePatch(): RenderPatchRecord {
        return RenderPatchRecord(
            operation = when (operation) {
                com.viewcompose.renderer.view.tree.RenderPatchOperation.Insert -> RenderPatchOperation.Insert
                com.viewcompose.renderer.view.tree.RenderPatchOperation.Remove -> RenderPatchOperation.Remove
                com.viewcompose.renderer.view.tree.RenderPatchOperation.Rebind -> RenderPatchOperation.Rebind
                com.viewcompose.renderer.view.tree.RenderPatchOperation.Patch -> RenderPatchOperation.Patch
                com.viewcompose.renderer.view.tree.RenderPatchOperation.SkipSelf -> RenderPatchOperation.SkipSelf
                com.viewcompose.renderer.view.tree.RenderPatchOperation.SkipSubtree -> RenderPatchOperation.SkipSubtree
            },
            type = type,
            key = key,
            parentKey = parentKey,
            index = index,
            moved = moved,
            detail = detail,
        )
    }

    private fun AndroidViewOperation.toCoreOperation(): RenderFailureOperation {
        return when (this) {
            AndroidViewOperation.Factory -> RenderFailureOperation.AndroidViewFactory
            AndroidViewOperation.Update -> RenderFailureOperation.AndroidViewUpdate
            AndroidViewOperation.Reset -> RenderFailureOperation.AndroidViewReset
            AndroidViewOperation.Commit -> RenderFailureOperation.AndroidViewCommit
            AndroidViewOperation.Release -> RenderFailureOperation.AndroidViewRelease
        }
    }
}
