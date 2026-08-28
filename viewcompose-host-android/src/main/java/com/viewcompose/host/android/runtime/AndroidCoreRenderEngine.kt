package com.viewcompose.host.android.runtime

import android.view.ViewGroup
import com.viewcompose.renderer.R
import com.viewcompose.renderer.decoration.AndroidViewDecorationRuntime
import com.viewcompose.renderer.decoration.ViewDecorationHostLayout
import com.viewcompose.renderer.view.tree.MountedNode
import com.viewcompose.renderer.view.tree.ViewTreeRenderer
import com.viewcompose.renderer.view.tree.ViewTreeObservedPropertyPatch
import com.viewcompose.renderer.view.tree.RenderTreeTimingCollector
import com.viewcompose.renderer.view.tree.RenderTreeTimingPhase
import com.viewcompose.renderer.view.tree.RenderTreeTimingSpan
import com.viewcompose.ui.modifier.DropShadowModifierElement
import com.viewcompose.ui.modifier.InnerShadowModifierElement
import com.viewcompose.ui.modifier.ZIndexModifierElement
import com.viewcompose.ui.node.VNode
import com.viewcompose.ui.node.RenderContainerHandle
import com.viewcompose.ui.node.nativeContainer
import com.viewcompose.ui.node.spec.AndroidViewOperation
import com.viewcompose.ui.foundation.CoreRenderEngine
import com.viewcompose.ui.foundation.CoreInspectedMountedNode
import com.viewcompose.ui.foundation.CoreMountedNodeInspection
import com.viewcompose.ui.foundation.CoreRenderCommitEffect
import com.viewcompose.ui.foundation.CoreRenderCommitFailure
import com.viewcompose.ui.foundation.CoreRenderFrame
import com.viewcompose.ui.foundation.CoreRenderTimingCollector
import com.viewcompose.ui.foundation.CoreRenderTimingPhase
import com.viewcompose.ui.foundation.CoreRenderTimingSpan
import com.viewcompose.ui.foundation.CoreRenderTimingSubject
import com.viewcompose.ui.foundation.CoreObservedPropertyPatch
import com.viewcompose.ui.foundation.CoreObservedPropertyFrame
import com.viewcompose.ui.foundation.CoreObservedPropertyTarget
import com.viewcompose.ui.foundation.CoreReusableRenderTree
import com.viewcompose.ui.foundation.NodeTypeBindingStats
import com.viewcompose.ui.foundation.RenderStats
import com.viewcompose.ui.foundation.RenderStructureStats
import com.viewcompose.ui.foundation.RenderTreeResult
import com.viewcompose.ui.foundation.RenderTreeNode
import com.viewcompose.ui.foundation.RenderPatchRecord
import com.viewcompose.ui.foundation.RenderPatchOperation
import com.viewcompose.ui.foundation.RenderFailureOperation
import com.viewcompose.ui.foundation.RenderFrameDiagnosticLevel
import com.viewcompose.ui.foundation.RenderNodePlatformTarget
import com.viewcompose.ui.node.NodeType
import com.viewcompose.ui.tooling.UiNodeTooling
import java.lang.ref.WeakReference

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
     * @param diagnosticLevel renderer detail required by the owning diagnostics collection
     * @return mounted nodes, statistics, diagnostics, and deferred commit work for the frame
     */
    override fun renderInto(
        container: RenderContainerHandle,
        previousMountedNodes: List<Any>,
        nodes: List<VNode>,
        diagnosticLevel: RenderFrameDiagnosticLevel,
    ): CoreRenderFrame = renderIntoInternal(
        container = container,
        previousMountedNodes = previousMountedNodes,
        nodes = nodes,
        diagnosticLevel = diagnosticLevel,
        timingCollector = null,
    )

    /**
     * Reconciles one Android tree while forwarding finite renderer intervals to [timingCollector].
     *
     * The collector is translated to the renderer-neutral timing port for this synchronous frame
     * only. Transaction, rollback, diagnostic-level, and deferred-commit behavior is identical to
     * [renderInto]; the collector is never retained by the host engine.
     */
    override fun renderIntoWithTiming(
        container: RenderContainerHandle,
        previousMountedNodes: List<Any>,
        nodes: List<VNode>,
        diagnosticLevel: RenderFrameDiagnosticLevel,
        timingCollector: CoreRenderTimingCollector,
    ): CoreRenderFrame = renderIntoInternal(
        container = container,
        previousMountedNodes = previousMountedNodes,
        nodes = nodes,
        diagnosticLevel = diagnosticLevel,
        timingCollector = timingCollector,
    )

    private fun renderIntoInternal(
        container: RenderContainerHandle,
        previousMountedNodes: List<Any>,
        nodes: List<VNode>,
        diagnosticLevel: RenderFrameDiagnosticLevel,
        timingCollector: CoreRenderTimingCollector?,
    ): CoreRenderFrame {
        val androidContainer = container.requireAndroidViewGroup()
        val previous = previousMountedNodes.filterIsInstance<MountedNode>()
        val hostResolution = resolveRenderHost(
            container = androidContainer,
            previousMountedNodes = previous,
            nodes = nodes,
        )
        val logicalOwnerTransfer =
            androidContainer.getTag(R.id.viewcompose_lazy_logical_owner_transfer) == true
        val propagatedOwnerTransfer = logicalOwnerTransfer && hostResolution.host !== androidContainer
        if (propagatedOwnerTransfer) {
            hostResolution.host.setTag(R.id.viewcompose_lazy_logical_owner_transfer, true)
        }
        val rendererCollector = timingCollector?.toRendererTimingCollector()
        val result = try {
            if (rendererCollector == null) {
                ViewTreeRenderer.renderInto(
                    container = hostResolution.host,
                    previous = if (hostResolution.remounted) emptyList() else previous,
                    nodes = nodes,
                    collectDiagnostics = diagnosticLevel == RenderFrameDiagnosticLevel.Tree,
                    collectStatistics = diagnosticLevel != RenderFrameDiagnosticLevel.None,
                )
            } else {
                ViewTreeRenderer.renderIntoWithTiming(
                    container = hostResolution.host,
                    previous = if (hostResolution.remounted) emptyList() else previous,
                    nodes = nodes,
                    timingCollector = rendererCollector,
                    collectDiagnostics = diagnosticLevel == RenderFrameDiagnosticLevel.Tree,
                    collectStatistics = diagnosticLevel != RenderFrameDiagnosticLevel.None,
                )
            }
        } finally {
            if (propagatedOwnerTransfer) {
                hostResolution.host.setTag(R.id.viewcompose_lazy_logical_owner_transfer, null)
            }
        }
        return CoreRenderFrame(
            mountedNodes = result.mountedNodes,
            observedPropertyTargets = observedPropertyTargets(result.mountedNodes),
            renderStats = result.stats.toCoreStats(),
            renderResult = if (diagnosticLevel == RenderFrameDiagnosticLevel.Tree) {
                result.toCoreResult()
            } else {
                null
            },
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
     * Captures a bounded, privacy-safe view of the current renderer-owned mounted tree.
     *
     * Traversal occurs only for the explicit caller request. Native targets are wrapped in weak
     * resolvers; no application key, View text, semantics, listener, or layout mutation is read or
     * retained. Entries are depth-first and parents precede their children.
     */
    override fun inspectMountedNodes(
        mountedNodes: List<Any>,
        maxVisitedNodes: Int,
        maxReturnedNodes: Int,
        maxDepth: Int,
    ): CoreMountedNodeInspection {
        require(maxVisitedNodes > 0)
        require(maxReturnedNodes > 0)
        require(maxDepth >= 0)
        val roots = mountedNodes.filterIsInstance<MountedNode>()
        if (roots.size != mountedNodes.size) return CoreMountedNodeInspection.Unsupported
        val entries = ArrayList<CoreInspectedMountedNode>(
            minOf(maxReturnedNodes, mountedNodes.size.coerceAtLeast(1)),
        )
        var visitedNodes = 0
        var droppedNodes = 0
        var truncated = false

        fun visit(
            node: MountedNode,
            depth: Int,
            parentIndex: Int?,
        ) {
            if (visitedNodes >= maxVisitedNodes) {
                if (!truncated) droppedNodes += 1
                truncated = true
                return
            }
            visitedNodes += 1
            if (depth > maxDepth) {
                droppedNodes += 1
                truncated = true
                return
            }
            val metadata = UiNodeTooling.metadataOf(node.vnode)
            val retainedIndex = if (entries.size < maxReturnedNodes) {
                entries.size.also { index ->
                    entries += CoreInspectedMountedNode(
                        parentIndex = parentIndex,
                        type = node.vnode.type,
                        depth = depth,
                        synthetic = metadata?.synthetic == true ||
                            node.vnode.type in SYNTHETIC_NODE_TYPES,
                        sourceCallSites = metadata?.callSites.orEmpty(),
                        platformTarget = WeakMountedNodePlatformTarget(node),
                    )
                    check(index == entries.lastIndex)
                }
            } else {
                droppedNodes += 1
                truncated = true
                null
            }
            for (child in node.children) {
                if (visitedNodes >= maxVisitedNodes) {
                    if (!truncated) droppedNodes += 1
                    truncated = true
                    break
                }
                visit(
                    node = child,
                    depth = depth + 1,
                    parentIndex = retainedIndex,
                )
            }
        }

        for (root in roots) {
            if (visitedNodes >= maxVisitedNodes) {
                if (!truncated) droppedNodes += 1
                truncated = true
                break
            }
            visit(root, depth = 0, parentIndex = null)
        }
        return CoreMountedNodeInspection(
            nodes = entries,
            visitedNodes = visitedNodes,
            droppedNodes = droppedNodes,
            truncated = truncated,
            supported = true,
        )
    }

    /** Patches exact Android mounted nodes in one renderer transaction without tree reconciliation. */
    override fun patchObservedProperties(
        container: RenderContainerHandle,
        mountedNodes: List<Any>,
        patches: List<CoreObservedPropertyPatch>,
        diagnosticLevel: RenderFrameDiagnosticLevel,
    ): CoreObservedPropertyFrame = patchObservedPropertiesInternal(
        container = container,
        mountedNodes = mountedNodes,
        patches = patches,
        diagnosticLevel = diagnosticLevel,
        timingCollector = null,
    )

    /**
     * Applies one exact-target Android property batch while reporting its direct binding intervals.
     *
     * The collector is scoped to this transaction and does not change validation, rollback,
     * diagnostic-level, or commit-effect behavior from [patchObservedProperties].
     */
    override fun patchObservedPropertiesWithTiming(
        container: RenderContainerHandle,
        mountedNodes: List<Any>,
        patches: List<CoreObservedPropertyPatch>,
        diagnosticLevel: RenderFrameDiagnosticLevel,
        timingCollector: CoreRenderTimingCollector,
    ): CoreObservedPropertyFrame = patchObservedPropertiesInternal(
        container = container,
        mountedNodes = mountedNodes,
        patches = patches,
        diagnosticLevel = diagnosticLevel,
        timingCollector = timingCollector,
    )

    private fun patchObservedPropertiesInternal(
        container: RenderContainerHandle,
        mountedNodes: List<Any>,
        patches: List<CoreObservedPropertyPatch>,
        diagnosticLevel: RenderFrameDiagnosticLevel,
        timingCollector: CoreRenderTimingCollector?,
    ): CoreObservedPropertyFrame {
        container.requireAndroidViewGroup()
        val rendererCollector = timingCollector?.toRendererTimingCollector()
        val timedMountedRoots = if (rendererCollector == null) {
            null
        } else {
            ArrayList<MountedNode>(mountedNodes.size)
        }
        mountedNodes.forEach { mountedNode ->
            val androidMountedNode = mountedNode as? MountedNode
                ?: error(
                    "Observed-property transactions require Android MountedNode roots from this engine.",
                )
            timedMountedRoots?.add(androidMountedNode)
        }
        val rendererPatches = patches.map { patch ->
            val mountedNode = patch.target.handle as? MountedNode
                ?: error("Observed property ${patch.id} has a foreign renderer target.")
            check(mountedNode.vnode === patch.target.node && patch.target.node === patch.previous) {
                "Observed property ${patch.id} target is not the committed renderer snapshot."
            }
            ViewTreeObservedPropertyPatch(
                id = patch.id,
                mountedNode = mountedNode,
                previous = patch.previous,
                next = patch.next,
            )
        }
        val result = if (rendererCollector == null) {
            ViewTreeRenderer.patchObservedProperties(
                patches = rendererPatches,
                collectDiagnostics = diagnosticLevel == RenderFrameDiagnosticLevel.Tree,
                collectStatistics = diagnosticLevel != RenderFrameDiagnosticLevel.None,
            )
        } else {
            ViewTreeRenderer.patchObservedPropertiesWithTiming(
                mountedRoots = checkNotNull(timedMountedRoots),
                patches = rendererPatches,
                timingCollector = rendererCollector,
                collectDiagnostics = diagnosticLevel == RenderFrameDiagnosticLevel.Tree,
                collectStatistics = diagnosticLevel != RenderFrameDiagnosticLevel.None,
            )
        }
        return CoreObservedPropertyFrame(
            renderStats = result.stats.toCoreStats(),
            renderResult = if (diagnosticLevel == RenderFrameDiagnosticLevel.Tree) {
                result.toCoreResult()
            } else {
                null
            },
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

    private fun observedPropertyTargets(
        roots: List<MountedNode>,
    ): Map<Long, CoreObservedPropertyTarget> {
        val targets = LinkedHashMap<Long, CoreObservedPropertyTarget>()
        fun visit(node: MountedNode) {
            node.vnode.observedPropertyId?.let { id ->
                check(targets.put(id, CoreObservedPropertyTarget(node, node.vnode)) == null) {
                    "Duplicate observed property id $id in one mounted tree."
                }
            }
            node.children.forEach(::visit)
        }
        roots.forEach(::visit)
        return targets
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

    private fun com.viewcompose.renderer.view.tree.ObservedPropertyRenderResult.toCoreResult(): RenderTreeResult {
        return RenderTreeResult(
            stats = stats.toCoreStats(),
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

private class WeakMountedNodePlatformTarget(
    node: MountedNode,
) : RenderNodePlatformTarget {
    private val node = WeakReference(node)

    override fun resolve(): Any? = node.get()?.view
}

private val SYNTHETIC_NODE_TYPES = setOf(
    NodeType.AnimatedSizeHost,
    NodeType.AnimatedBoundsHost,
    NodeType.LayoutConstraintHost,
    NodeType.NestedScrollHost,
)

private fun CoreRenderTimingCollector.toRendererTimingCollector(): RenderTreeTimingCollector {
    return RenderTreeTimingCollector { subject, phase ->
        beginInterval(
            subject = CoreRenderTimingSubject(
                nodeIdentity = subject.nodeIdentity,
                nodeType = subject.nodeType,
                depth = subject.depth,
                synthetic = subject.synthetic,
            ),
            phase = when (phase) {
                RenderTreeTimingPhase.Reconciliation -> CoreRenderTimingPhase.Reconciliation
                RenderTreeTimingPhase.Binding -> CoreRenderTimingPhase.Binding
            },
        )?.let { coreSpan -> RenderTreeTimingSpan(coreSpan::close) }
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
