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
import com.viewcompose.ui.node.RenderContainerHandle
import com.viewcompose.ui.node.nativeContainer
import com.viewcompose.ui.node.spec.AndroidViewOperation
import com.viewcompose.ui.foundation.CoreRenderEngine
import com.viewcompose.ui.foundation.CoreRenderCommitEffect
import com.viewcompose.ui.foundation.CoreRenderCommitFailure
import com.viewcompose.ui.foundation.CoreRenderFrame
import com.viewcompose.ui.foundation.CoreReusableRenderTree
import com.viewcompose.ui.foundation.NodeTypeBindingStats
import com.viewcompose.ui.foundation.RenderStats
import com.viewcompose.ui.foundation.RenderStructureStats
import com.viewcompose.ui.foundation.RenderTreeResult
import com.viewcompose.ui.foundation.RenderTreeNode
import com.viewcompose.ui.foundation.RenderPatchRecord
import com.viewcompose.ui.foundation.RenderPatchOperation
import com.viewcompose.ui.foundation.RenderFailureOperation

/**
 * Adapts the platform-neutral core render contract to the Android View renderer.
 *
 * Mounted-node and diagnostic implementation types are translated at this boundary so widget-core
 * does not depend on renderer internals. The engine also installs a root decoration host only when
 * root-level z-order or an installed shadow backend requires one; changing that requirement may
 * remount the current root nodes.
 *
 * Calls must run on the Android main thread through a host-managed render session.
 */
class AndroidCoreRenderEngine : CoreRenderEngine {
    /**
     * Reconciles [nodes] into [container] and returns a core-owned frame snapshot.
     *
     * [previousMountedNodes] may contain opaque values from another engine; values that are not
     * renderer [MountedNode] instances are ignored. Commit effects are returned for the render
     * session to execute only after the complete frame succeeds.
     *
     * @param container Android parent that owns the rendered tree
     * @param previousMountedNodes opaque nodes returned by the preceding successful frame
     * @param nodes immutable VNode roots for the next frame
     * @param collectDiagnostics whether to materialize tree and patch diagnostics
     * @return mounted nodes, statistics, diagnostics, and deferred commit work for the frame
     */
    override fun renderInto(
        container: RenderContainerHandle,
        previousMountedNodes: List<Any>,
        nodes: List<VNode>,
        collectDiagnostics: Boolean,
    ): CoreRenderFrame {
        val androidContainer = container.requireAndroidViewGroup()
        val previous = previousMountedNodes.filterIsInstance<MountedNode>()
        val hostResolution = resolveRenderHost(
            container = androidContainer,
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

    /**
     * Releases [mountedNodes] and removes an empty synthetic decoration host.
     *
     * Cleanup continues after individual native release failures; all failures are returned to the
     * render session for reporting.
     *
     * @param container Android parent that owns the mounted tree
     * @param mountedNodes opaque nodes returned by a previous frame
     * @return native release failures collected while disposing the tree
     */
    override fun disposeMounted(
        container: RenderContainerHandle,
        mountedNodes: List<Any>,
    ): List<CoreRenderCommitFailure> {
        val androidContainer = container.requireAndroidViewGroup()
        val renderHost = decorationHostOrNull(androidContainer) ?: androidContainer
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
        if (renderHost !== androidContainer && renderHost.childCount == 0) {
            androidContainer.removeView(renderHost)
            androidContainer.setTag(R.id.viewcompose_decoration_render_host, null)
        }
        return failures
    }

    /**
     * Resets eligible Android interop nodes and detaches the complete mounted tree from [container].
     *
     * This is UI-thread confined. The method returns `null` without detaching when the tree is
     * empty, owns a nested lazy or pager session, or contains an `AndroidView` without `onReset`.
     * A successful result transfers physical-tree ownership to the caller; logical composition
     * state has already been disposed by UI Foundation.
     *
     * @param container Android parent that currently owns [mountedNodes]
     * @param mountedNodes exact opaque roots returned by the preceding successful frame
     * @return renderer-owned detached tree, or `null` when cross-key reuse is unsafe
     */
    override fun detachMountedForReuse(
        container: RenderContainerHandle,
        mountedNodes: List<Any>,
    ): CoreReusableRenderTree? {
        val androidContainer = container.requireAndroidViewGroup()
        val renderHost = decorationHostOrNull(androidContainer) ?: androidContainer
        val nodes = mountedNodes.filterIsInstance<MountedNode>()
        if (nodes.isEmpty() || !ViewTreeRenderer.detachMountedForReuse(renderHost, nodes)) {
            return null
        }
        if (renderHost !== androidContainer && renderHost.childCount == 0) {
            androidContainer.removeView(renderHost)
            androidContainer.setTag(R.id.viewcompose_decoration_render_host, null)
        }
        return AndroidReusableRenderTree(nodes)
    }

    /**
     * Transfers one compatible detached tree into [container] for a new logical item session.
     *
     * The returned nodes are marked for a mandatory full rebind on the new session's first frame;
     * key equality and ordinary patch skipping cannot preserve the old declaration. A consumed or
     * foreign tree returns an empty list. Calls are UI-thread confined.
     *
     * @param container Android parent that will own the presentation
     * @param tree detached tree returned by this engine
     * @return attached opaque roots, or an empty list when ownership cannot be accepted
     */
    override fun attachReusableMounted(
        container: RenderContainerHandle,
        tree: CoreReusableRenderTree,
    ): List<Any> {
        val reusable = tree as? AndroidReusableRenderTree ?: return emptyList()
        val nodes = reusable.peekNodes() ?: return emptyList()
        val androidContainer = container.requireAndroidViewGroup()
        // Reattach through the host required by the detached tree. Attaching decorated roots
        // directly to the outer container would make the first cross-owner render interpret the
        // missing synthetic host as a host transition and release the tree before it can rebind.
        val renderHost = resolveRenderHost(
            container = androidContainer,
            previousMountedNodes = emptyList(),
            nodes = nodes.map(MountedNode::vnode),
        ).host
        ViewTreeRenderer.attachReusableMounted(renderHost, nodes)
        return reusable.takeNodes(expected = nodes).orEmpty()
    }

    /**
     * Permanently releases an unadopted detached tree after cache eviction or container disposal.
     *
     * Release is consuming and idempotent for the handle. Every root is attempted and structured
     * native failures are returned in encounter order. Because a cached tree has no logical
     * `RenderSession`, renderer diagnostics also log release failures without retaining the former
     * item owner.
     *
     * @param tree detached tree previously returned by this engine
     * @return native release failures; empty for a foreign or already consumed handle
     */
    override fun releaseReusableMounted(tree: CoreReusableRenderTree): List<CoreRenderCommitFailure> {
        val reusable = tree as? AndroidReusableRenderTree ?: return emptyList()
        val nodes = reusable.takeNodes() ?: return emptyList()
        return ViewTreeRenderer.releaseReusableMounted(nodes).map { failure ->
            CoreRenderCommitFailure(
                operation = failure.operation?.toCoreOperation(),
                nodeKey = failure.nodeKey,
                cause = failure.cause,
            )
        }
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
        // Copy diagnostics into core-owned models so renderer internals cannot cross the boundary.
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

private class AndroidReusableRenderTree(
    nodes: List<MountedNode>,
) : CoreReusableRenderTree {
    private var nodes: List<MountedNode>? = nodes

    fun peekNodes(): List<MountedNode>? = nodes

    fun takeNodes(expected: List<MountedNode>? = null): List<MountedNode>? {
        val owned = nodes
        if (expected != null && owned !== expected) return null
        nodes = null
        return owned
    }
}

private fun RenderContainerHandle.requireAndroidViewGroup(): ViewGroup {
    return nativeContainer as? ViewGroup
        ?: error("AndroidCoreRenderEngine requires a ViewGroup-backed render container.")
}
