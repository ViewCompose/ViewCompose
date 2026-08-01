package com.viewcompose.host.android.runtime

import android.view.ViewGroup
import com.viewcompose.renderer.R
import com.viewcompose.renderer.decoration.AndroidViewDecorationRuntime
import com.viewcompose.renderer.decoration.ViewDecorationHostLayout
import com.viewcompose.renderer.view.tree.MountedNode
import com.viewcompose.renderer.view.tree.ViewTreeRenderer
import com.viewcompose.ui.modifier.DropShadowModifierElement
import com.viewcompose.ui.modifier.InnerShadowModifierElement
import com.viewcompose.ui.modifier.ZIndexModifierElement
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

/**
 * widget-core 与 renderer 模块之间的 Android 渲染引擎适配器。
 * Android render-engine adapter between widget-core and the renderer module.
 *
 * widget-core 只依赖 CoreRenderEngine 契约，具体 MountedNode 和诊断类型在这里转换。
 * widget-core depends only on CoreRenderEngine, while concrete MountedNode and diagnostic types are translated here.
 */
class AndroidCoreRenderEngine : CoreRenderEngine {
    override fun renderInto(
        container: ViewGroup,
        previousMountedNodes: List<Any>,
        nodes: List<VNode>,
        collectDiagnostics: Boolean,
    ): CoreRenderFrame {
        val previous = previousMountedNodes.filterIsInstance<MountedNode>()
        val hostResolution = resolveRenderHost(
            container = container,
            previousMountedNodes = previous,
            nodes = nodes,
        )
        val result = ViewTreeRenderer.renderInto(
            container = hostResolution.host,
            previous = if (hostResolution.remounted) emptyList() else previous,
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
            commitFailures = hostResolution.transitionFailures + result.commitFailures.map { failure ->
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
        val renderHost = decorationHostOrNull(container) ?: container
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
            container.setTag(R.id.viewcompose_decoration_render_host, null)
        }
        return failures
    }

    private fun resolveRenderHost(
        container: ViewGroup,
        previousMountedNodes: List<MountedNode>,
        nodes: List<VNode>,
    ): RenderHostResolution {
        if (container is ViewDecorationHostLayout) {
            return RenderHostResolution(host = container)
        }
        val existing = decorationHostOrNull(container)
        val requiresHost = requiresRootHost(nodes)
        if (!requiresHost) {
            if (existing == null) {
                return RenderHostResolution(host = container)
            }
            val failures = disposeForHostTransition(existing, previousMountedNodes)
            container.removeView(existing)
            container.setTag(R.id.viewcompose_decoration_render_host, null)
            return RenderHostResolution(
                host = container,
                remounted = previousMountedNodes.isNotEmpty(),
                transitionFailures = failures,
            )
        }

        if (existing != null) {
            if (existing.parent !== container) {
                (existing.parent as? ViewGroup)?.removeView(existing)
                attachDecorationHost(container, existing)
            }
            return RenderHostResolution(host = existing)
        }

        val transitionFailures = disposeForHostTransition(container, previousMountedNodes)
        val host = ViewDecorationHostLayout(container.context).also { newHost ->
            container.setTag(R.id.viewcompose_decoration_render_host, newHost)
            attachDecorationHost(container, newHost)
        }
        return RenderHostResolution(
            host = host,
            remounted = previousMountedNodes.isNotEmpty(),
            transitionFailures = transitionFailures,
        )
    }

    private fun decorationHostOrNull(container: ViewGroup): ViewDecorationHostLayout? {
        return container.getTag(
            R.id.viewcompose_decoration_render_host,
        ) as? ViewDecorationHostLayout
    }

    private fun attachDecorationHost(
        container: ViewGroup,
        host: ViewDecorationHostLayout,
    ) {
        container.addView(
            host,
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            ),
        )
    }

    private fun disposeForHostTransition(
        host: ViewGroup,
        mountedNodes: List<MountedNode>,
    ): List<CoreRenderCommitFailure> {
        if (mountedNodes.isEmpty()) return emptyList()
        return ViewTreeRenderer.disposeMounted(
            container = host,
            mountedNodes = mountedNodes,
        ).map { failure ->
            CoreRenderCommitFailure(
                operation = failure.operation?.toCoreOperation(),
                nodeKey = failure.nodeKey,
                cause = failure.cause,
            )
        }
    }

    private fun requiresRootHost(nodes: List<VNode>): Boolean {
        var hasShadow = false
        nodes.forEach { node ->
            node.modifier.elements.forEach { element ->
                when (element) {
                    is ZIndexModifierElement -> if (element.zIndex != 0f) return true
                    is DropShadowModifierElement,
                    is InnerShadowModifierElement,
                    -> hasShadow = true
                }
            }
        }
        return hasShadow && AndroidViewDecorationRuntime.hasBackend()
    }

    private data class RenderHostResolution(
        val host: ViewGroup,
        val remounted: Boolean = false,
        val transitionFailures: List<CoreRenderCommitFailure> = emptyList(),
    )

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
            toolingMetadata = toolingMetadata,
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
