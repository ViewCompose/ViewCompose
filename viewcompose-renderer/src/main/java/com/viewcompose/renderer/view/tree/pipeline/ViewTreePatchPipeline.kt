package com.viewcompose.renderer.view.tree

import android.content.Context
import android.util.Log
import android.view.View
import android.view.ViewGroup
import com.viewcompose.ui.node.NodeType
import com.viewcompose.ui.node.VNode
import com.viewcompose.ui.node.spec.AndroidViewNodeProps
import com.viewcompose.ui.node.spec.AndroidViewOperation
import com.viewcompose.renderer.modifier.ResolvedModifiers
import com.viewcompose.renderer.modifier.layoutModifiersChanged
import com.viewcompose.renderer.modifier.resolve
import com.viewcompose.renderer.reconcile.InsertPatch
import com.viewcompose.renderer.reconcile.ReconcileResult
import com.viewcompose.renderer.reconcile.RemovePatch
import com.viewcompose.renderer.reconcile.RenderPatch
import com.viewcompose.renderer.reconcile.ReusePatch
import com.viewcompose.renderer.view.container.DeclarativeConstraintLayout
import com.viewcompose.renderer.view.container.ChildHostViewGroup
import com.viewcompose.shadow.android.DecorationChildDrawingOrder

/**
 * 将 reconcile patch 应用到 Android View 树的事务管线。
 * Transactional pipeline that applies reconcile patches to the Android View tree.
 *
 * preflight 阶段先解析 modifier、binding plan 和 layout params；apply 阶段才实际变更 View 树。
 * The preflight phase resolves modifiers, binding plans, and layout params before the apply phase mutates the View tree.
 */
internal object ViewTreePatchPipeline {
    private val emptyStats = RenderStats()

    /**
     * 单个 container 执行 patch 后的 mounted node 与统计。
     * Mounted nodes and statistics after applying patches for one container.
     */
    data class ExecutionResult(
        val mountedNodes: List<MountedNode>,
        val stats: RenderStats,
    )

    private data class PatchApplicationResult(
        val mountedNode: MountedNode,
        val stats: RenderStats,
    )

    private data class PreparedPatch(
        val patch: RenderPatch<MountedNode>,
        val bindingPlan: NodeBindingPlan? = null,
        val nextResolved: ResolvedModifiers? = null,
        val layoutParams: ViewGroup.LayoutParams? = null,
        val updatesLayoutParams: Boolean = false,
    )

    /**
     * 一次 render transaction 中需要回滚或延迟提交的状态。
     * State that must be rolled back or deferred during one render transaction.
     */
    internal class RenderTransaction internal constructor() {
        internal val mountedCheckpoints = LinkedHashMap<MountedNode, MountedCheckpoint>()
        internal val containerCheckpoints = LinkedHashMap<ViewGroup, ContainerCheckpoint>()
        internal val insertedNodes = LinkedHashSet<MountedNode>()
        internal val deferredRemovals = LinkedHashSet<MountedNode>()
        internal val commitEffects = mutableListOf<RenderTreeCommitEffect>()
        internal val patchRecords = mutableListOf<RenderPatchRecord>()
    }

    /**
     * mounted node 的回滚检查点。
     * Rollback checkpoint for a mounted node.
     */
    internal data class MountedCheckpoint(
        val mountedNode: MountedNode,
        val vnode: VNode,
        val children: List<MountedNode>,
        val layoutParams: ViewGroup.LayoutParams?,
        val disposed: Boolean,
    )

    /**
     * container child 顺序的回滚检查点。
     * Rollback checkpoint for a container's child order.
     */
    internal data class ContainerCheckpoint(
        val container: ViewGroup,
        val children: List<View>,
    )

    /**
     * 开始一次新的 View 树事务。
     * Starts a new View tree transaction.
     */
    internal fun beginTransaction(): RenderTransaction = RenderTransaction()

    /**
     * 提交事务的延迟释放阶段。
     * Commits the transaction's deferred release phase.
     */
    internal fun commitTransaction(
        transaction: RenderTransaction,
        warningTag: String,
    ): List<RenderTreeCommitFailure> {
        val failures = mutableListOf<RenderTreeCommitFailure>()
        transaction.deferredRemovals.forEach { mountedNode ->
            try {
                ViewTreeDisposer.disposeMountedNode(mountedNode)
            } catch (error: Throwable) {
                failures += error.toRenderTreeCommitFailures(
                    fallbackNodeKey = mountedNode.vnode.key,
                )
                Log.e(
                    warningTag,
                    "Failed to release removed ${mountedNode.vnode.type} node after render commit.",
                    error,
                )
            }
        }
        transaction.deferredRemovals.clear()
        transaction.insertedNodes.clear()
        return failures
    }

    /**
     * 按检查点回滚失败的 View 树变更。
     * Rolls back failed View tree mutations from checkpoints.
     */
    internal fun rollbackTransaction(
        transaction: RenderTransaction,
        cause: Throwable,
        defaultRippleColor: Int,
    ) {
        fun bestEffort(block: () -> Unit) {
            try {
                block()
            } catch (rollbackError: Throwable) {
                cause.addSuppressed(rollbackError)
            }
        }

        // 新插入 View 先从父容器移除，再恢复旧 mounted node 与 container 顺序。
        // Newly inserted Views are removed first, then previous mounted-node and container state is restored.
        transaction.insertedNodes.toList().asReversed().forEach { mountedNode ->
            bestEffort {
                (mountedNode.view.parent as? ViewGroup)?.let { parent ->
                    parent.removeView(mountedNode.view)
                    DecorationChildDrawingOrder.invalidate(parent)
                }
            }
        }

        transaction.mountedCheckpoints.values.forEach { checkpoint ->
            checkpoint.mountedNode.vnode = checkpoint.vnode
            ViewNodeToolingRegistry.bind(
                view = checkpoint.mountedNode.view,
                node = checkpoint.vnode,
            )
            checkpoint.mountedNode.children = checkpoint.children
            checkpoint.mountedNode.disposed = checkpoint.disposed
            checkpoint.mountedNode.view.layoutParams = checkpoint.layoutParams
        }

        transaction.mountedCheckpoints.values.forEach { checkpoint ->
            bestEffort {
                bindView(
                    view = checkpoint.mountedNode.view,
                    node = checkpoint.vnode,
                    defaultRippleColor = defaultRippleColor,
                    resolved = checkpoint.vnode.modifier.resolve(),
                )
                checkpoint.mountedNode.view.layoutParams = checkpoint.layoutParams
            }
        }

        transaction.containerCheckpoints.values.toList().asReversed().forEach { checkpoint ->
            checkpoint.children.forEachIndexed { index, child ->
                bestEffort {
                    val currentParent = child.parent as? ViewGroup
                    if (currentParent !== checkpoint.container) {
                        currentParent?.removeView(child)
                        currentParent?.let(DecorationChildDrawingOrder::invalidate)
                        checkpoint.container.addView(
                            child,
                            index.coerceAtMost(checkpoint.container.childCount),
                        )
                        DecorationChildDrawingOrder.invalidate(checkpoint.container)
                    } else {
                        moveViewToIndex(
                            container = checkpoint.container,
                            view = child,
                            targetIndex = index,
                        )
                    }
                }
            }
        }

        transaction.insertedNodes.toList().asReversed().forEach { mountedNode ->
            bestEffort {
                ViewTreeDisposer.disposeMountedNode(mountedNode)
            }
        }
        transaction.deferredRemovals.clear()
        transaction.insertedNodes.clear()
        transaction.commitEffects.clear()
        transaction.patchRecords.clear()
    }

    fun execute(
        container: ViewGroup,
        reconcileResult: ReconcileResult<MountedNode>,
        defaultRippleColor: Int,
        warningTag: String,
        emittedModifierWarnings: MutableSet<String>,
        transaction: RenderTransaction,
        collectStats: Boolean,
        parentNodeKey: Any?,
        renderChildren: (ViewGroup, List<MountedNode>, List<VNode>, Any?) -> RenderTreeResult,
    ): ExecutionResult {
        val mountContainer = resolveChildHost(container)
        // preflight 在任何结构变更前完成可能抛错的解析，降低回滚复杂度。
        // preflight completes potentially throwing resolution before structural mutations, reducing rollback complexity.
        val preparedPatches = preflight(
            container = mountContainer,
            reconcileResult = reconcileResult,
            warningTag = warningTag,
            emittedModifierWarnings = emittedModifierWarnings,
        )
        var stats = emptyStats
        val nextMounted = mutableListOf<MountedNode>()
        preparedPatches.forEach { preparedPatch ->
            val patchResult = applyPatch(
                container = mountContainer,
                preparedPatch = preparedPatch,
                defaultRippleColor = defaultRippleColor,
                transaction = transaction,
                collectStats = collectStats,
                parentNodeKey = parentNodeKey,
                renderChildren = renderChildren,
            )
            if (collectStats) {
                stats = stats.mergeWith(patchResult.stats)
            }
            nextMounted += patchResult.mountedNode
        }
        reconcileResult.removals.forEach { removal ->
            // remove 先从父容器摘除，但释放延迟到 commit，方便 rollback 恢复旧树。
            // remove detaches from the parent immediately but defers disposal until commit so rollback can restore the old tree.
            applyRemoval(
                container = mountContainer,
                removal = removal,
                transaction = transaction,
            )
            if (collectStats) {
                stats = stats.withRemoval()
                transaction.recordPatch(RenderPatchRecord(
                    operation = RenderPatchOperation.Remove,
                    type = removal.payload.vnode.type,
                    key = removal.payload.vnode.key,
                    parentKey = parentNodeKey,
                    index = removal.previousIndex,
                ))
            }
        }
        return ExecutionResult(
            mountedNodes = nextMounted,
            stats = stats,
        )
    }

    private fun applyPatch(
        container: ViewGroup,
        preparedPatch: PreparedPatch,
        defaultRippleColor: Int,
        transaction: RenderTransaction,
        collectStats: Boolean,
        parentNodeKey: Any?,
        renderChildren: (ViewGroup, List<MountedNode>, List<VNode>, Any?) -> RenderTreeResult,
    ): PatchApplicationResult {
        val patch = preparedPatch.patch
        return when (patch) {
            is InsertPatch -> {
                val resolved = checkNotNull(preparedPatch.nextResolved)
                val mountedNode = mountNode(
                    context = container.context,
                    node = patch.nextVNode,
                    defaultRippleColor = defaultRippleColor,
                    resolved = resolved,
                    transaction = transaction,
                    renderChildren = renderChildren,
                )
                if (collectStats) {
                    transaction.recordPatch(RenderPatchRecord(
                        operation = RenderPatchOperation.Insert,
                        type = patch.nextVNode.type,
                        key = patch.nextVNode.key,
                        parentKey = parentNodeKey,
                        index = patch.targetIndex,
                    ))
                }
                captureContainer(
                    transaction = transaction,
                    target = container,
                )
                // 插入前记录 container 顺序，失败时可恢复到原 child 列表。
                // Capture container order before insertion so failures can restore the original child list.
                container.addView(
                    mountedNode.view,
                    patch.targetIndex.coerceAtMost(container.childCount),
                    checkNotNull(preparedPatch.layoutParams),
                )
                DecorationChildDrawingOrder.invalidate(container)
                PatchApplicationResult(
                    mountedNode = mountedNode,
                    stats = if (collectStats) RenderStats(inserts = 1) else emptyStats,
                )
            }

            is ReusePatch -> {
                val mountedNode = patch.payload
                val bindingPlan = checkNotNull(preparedPatch.bindingPlan)
                val reusesExactVNode = bindingPlan == NodeBindingPlan.SkipSubtree &&
                    mountedNode.vnode === patch.nextVNode
                val needsMove = container.indexOfChild(mountedNode.view) != patch.targetIndex
                if (reusesExactVNode && !needsMove) {
                    // 完全相同 VNode 且无需移动时，整个子树可跳过绑定和 child reconcile。
                    // When the exact VNode is reused and no move is needed, binding and child reconciliation can be skipped.
                    if (collectStats) {
                        transaction.recordPatch(RenderPatchRecord(
                            operation = RenderPatchOperation.SkipSubtree,
                            type = patch.nextVNode.type,
                            key = patch.nextVNode.key,
                            parentKey = parentNodeKey,
                            index = patch.targetIndex,
                        ))
                    }
                    return PatchApplicationResult(
                        mountedNode = mountedNode,
                        stats = if (collectStats) {
                            emptyStats.withReuse(
                                result = ReuseBindingResult.SkippedSubtree,
                                nodeType = patch.nextVNode.type,
                            )
                        } else {
                            emptyStats
                        },
                    )
                }
                if (!reusesExactVNode) {
                    captureNode(
                        transaction = transaction,
                        mountedNode = mountedNode,
                    )
                }
                if (patch.nextVNode.type == NodeType.AndroidView &&
                    mountedNode.vnode.spec != patch.nextVNode.spec
                ) {
                    // AndroidView spec 变化先触发 Reset，再进入 rebind/patch 和 commit effect。
                    // AndroidView spec changes trigger Reset before rebind/patch and the later commit effect.
                    patch.nextVNode.runAndroidViewOperation(AndroidViewOperation.Reset) {
                        patch.nextVNode
                            .requireSpec<AndroidViewNodeProps>()
                            .onReset
                            ?.invoke(mountedNode.view)
                    }
                }
                when (bindingPlan) {
                    NodeBindingPlan.Rebind -> {
                        bindView(
                            view = mountedNode.view,
                            node = patch.nextVNode,
                            defaultRippleColor = defaultRippleColor,
                            resolved = checkNotNull(preparedPatch.nextResolved),
                        )
                        scheduleAndroidViewCommit(
                            transaction = transaction,
                            view = mountedNode.view,
                            node = patch.nextVNode,
                        )
                    }

                    NodeBindingPlan.SkipSelfOnly,
                    NodeBindingPlan.SkipSubtree,
                    -> Unit
                    is NodeBindingPlan.Patch -> {
                        if (bindingPlan.modifierChanged) {
                            ViewModifierApplier.applyModifier(
                                view = mountedNode.view,
                                node = patch.nextVNode,
                                defaultRippleColor = defaultRippleColor,
                                resolved = checkNotNull(preparedPatch.nextResolved),
                            )
                        }
                        NodeViewBinderRegistry.applyPatch(
                            view = mountedNode.view,
                            patch = bindingPlan.patch,
                        )
                        if (bindingPlan.modifierChanged) {
                            ModifierInteractionApplier.applyNativeViewConfigs(
                                view = mountedNode.view,
                                node = patch.nextVNode,
                            )
                        }
                    }
                }
                if (preparedPatch.updatesLayoutParams) {
                    // layout modifier 变化会重建 LayoutParams，并通知 ConstraintLayout 重新生成约束。
                    // Layout modifier changes rebuild LayoutParams and ask ConstraintLayout to rebuild constraints.
                    mountedNode.view.layoutParams = checkNotNull(preparedPatch.layoutParams)
                    (container as? DeclarativeConstraintLayout)?.requestConstraintRebuild()
                }
                val childResult = if (shouldReconcileChildren(bindingPlan)) {
                    reconcileChildren(
                        view = mountedNode.view,
                        previousChildren = mountedNode.children,
                        node = patch.nextVNode,
                        renderChildren = renderChildren,
                    )
                } else {
                    emptyChildResult(mountedNode.children)
                }
                mountedNode.children = childResult.mountedNodes
                mountedNode.vnode = patch.nextVNode
                ViewNodeToolingRegistry.bind(
                    view = mountedNode.view,
                    node = patch.nextVNode,
                )
                if (needsMove) {
                    captureContainer(
                        transaction = transaction,
                        target = container,
                    )
                }
                moveViewToIndex(
                    container = container,
                    view = mountedNode.view,
                    targetIndex = patch.targetIndex,
                )
                if (collectStats) {
                    transaction.recordPatch(RenderPatchRecord(
                        operation = bindingPlan.toPatchOperation(),
                        type = patch.nextVNode.type,
                        key = patch.nextVNode.key,
                        parentKey = parentNodeKey,
                        index = patch.targetIndex,
                        moved = needsMove,
                        detail = (bindingPlan as? NodeBindingPlan.Patch)
                            ?.patch
                            ?.let { nodePatch -> nodePatch::class.simpleName },
                    ))
                }
                PatchApplicationResult(
                    mountedNode = mountedNode,
                    stats = if (collectStats) {
                        childResult.stats.withReuse(
                            result = when (bindingPlan) {
                                NodeBindingPlan.Rebind -> ReuseBindingResult.Rebound
                                NodeBindingPlan.SkipSelfOnly -> ReuseBindingResult.Skipped
                                NodeBindingPlan.SkipSubtree -> ReuseBindingResult.SkippedSubtree
                                is NodeBindingPlan.Patch -> ReuseBindingResult.Patched
                            },
                            nodeType = patch.nextVNode.type,
                        )
                    } else {
                        emptyStats
                    },
                )
            }
        }
    }

    private fun applyRemoval(
        container: ViewGroup,
        removal: RemovePatch<MountedNode>,
        transaction: RenderTransaction,
    ) {
        captureContainer(
            transaction = transaction,
            target = container,
        )
        container.removeView(removal.payload.view)
        DecorationChildDrawingOrder.invalidate(container)
        transaction.deferredRemovals += removal.payload
    }

    private fun reconcileChildren(
        view: View,
        previousChildren: List<MountedNode>,
        node: VNode,
        renderChildren: (ViewGroup, List<MountedNode>, List<VNode>, Any?) -> RenderTreeResult,
    ): RenderTreeResult {
        val viewGroup = view as? ViewGroup ?: return RenderTreeResult(
            mountedNodes = emptyList(),
            reconcileResult = ReconcileResult(
                patches = emptyList(),
                removals = emptyList(),
            ),
            stats = emptyStats,
        )
        return renderChildren(
            resolveChildHost(viewGroup),
            previousChildren,
            node.children,
            node.key,
        )
    }

    internal fun shouldReconcileChildren(bindingPlan: NodeBindingPlan): Boolean {
        return bindingPlan != NodeBindingPlan.SkipSubtree
    }

    private fun emptyChildResult(children: List<MountedNode>): RenderTreeResult {
        return RenderTreeResult(
            mountedNodes = children,
            reconcileResult = ReconcileResult(
                patches = emptyList(),
                removals = emptyList(),
            ),
            stats = emptyStats,
        )
    }

    private fun mountNode(
        context: Context,
        node: VNode,
        defaultRippleColor: Int,
        resolved: ResolvedModifiers,
        transaction: RenderTransaction,
        renderChildren: (ViewGroup, List<MountedNode>, List<VNode>, Any?) -> RenderTreeResult,
    ): MountedNode {
        val view = ViewNodeFactory.createView(
            context = context,
            node = node,
            createAndroidView = when (node.type) {
                NodeType.AndroidView -> {
                    val factory = node.requireSpec<AndroidViewNodeProps>().factory
                    { rawContext ->
                        node.runAndroidViewOperation(AndroidViewOperation.Factory) {
                            factory(rawContext)
                        }
                    }
                }
                else -> null
            },
        )
        val mountedNode = MountedNode(
            vnode = node,
            view = view,
        )
        ViewNodeToolingRegistry.bind(
            view = view,
            node = node,
        )
        transaction.insertedNodes += mountedNode
        ViewModifierApplier.cacheOriginalBackground(view)
        ViewModifierApplier.cacheOriginalForeground(view)
        bindView(
            view = view,
            node = node,
            defaultRippleColor = defaultRippleColor,
            resolved = resolved,
        )
        scheduleAndroidViewCommit(
            transaction = transaction,
            view = view,
            node = node,
        )
        val children = if (view is ViewGroup) {
            renderChildren(
                resolveChildHost(view),
                emptyList(),
                node.children,
                node.key,
            ).mountedNodes
        } else {
            emptyList()
        }
        mountedNode.children = children
        return mountedNode
    }

    private fun preflight(
        container: ViewGroup,
        reconcileResult: ReconcileResult<MountedNode>,
        warningTag: String,
        emittedModifierWarnings: MutableSet<String>,
    ): List<PreparedPatch> {
        return reconcileResult.patches.map { patch ->
            when (patch) {
                is InsertPatch -> {
                    val nextNode = patch.nextVNode
                    val resolved = nextNode.modifier.resolve()
                    if (nextNode.type == NodeType.AndroidView) {
                        nextNode.requireSpec<AndroidViewNodeProps>()
                    }
                    PreparedPatch(
                        patch = patch,
                        nextResolved = resolved,
                        layoutParams = ViewLayoutParamsFactory.createLayoutParams(
                            parent = container,
                            node = nextNode,
                            warningTag = warningTag,
                            emittedModifierWarnings = emittedModifierWarnings,
                            resolved = resolved,
                        ),
                        updatesLayoutParams = true,
                    )
                }

                is ReusePatch -> {
                    val nextNode = patch.nextVNode
                    val previousNode = patch.payload.vnode
                    val bindingPlan = NodeBindingDiffer.plan(
                        previous = previousNode,
                        next = nextNode,
                    )
                    if (nextNode.type == NodeType.AndroidView) {
                        nextNode.requireSpec<AndroidViewNodeProps>()
                    }
                    val nextResolved = when {
                        bindingPlan == NodeBindingPlan.Rebind -> nextNode.modifier.resolve()
                        bindingPlan is NodeBindingPlan.Patch && bindingPlan.modifierChanged -> {
                            nextNode.modifier.resolve()
                        }

                        else -> null
                    }
                    // 只有 rebind 或 modifier patch 需要重新解析 modifier；纯 spec patch 可跳过。
                    // Only rebind or modifier patches need modifier resolution; spec-only patches can skip it.
                    val updatesLayoutParams = when {
                        bindingPlan == NodeBindingPlan.Rebind -> true
                        bindingPlan is NodeBindingPlan.Patch && bindingPlan.modifierChanged -> {
                            layoutModifiersChanged(
                                previous = previousNode.modifier.resolve(),
                                next = checkNotNull(nextResolved),
                            )
                        }

                        else -> false
                    }
                    PreparedPatch(
                        patch = patch,
                        bindingPlan = bindingPlan,
                        nextResolved = nextResolved,
                        layoutParams = if (updatesLayoutParams) {
                            ViewLayoutParamsFactory.createLayoutParams(
                                parent = container,
                                node = nextNode,
                                warningTag = warningTag,
                                emittedModifierWarnings = emittedModifierWarnings,
                                resolved = checkNotNull(nextResolved),
                            )
                        } else {
                            null
                        },
                        updatesLayoutParams = updatesLayoutParams,
                    )
                }
            }
        }
    }

    private fun bindView(
        view: View,
        node: VNode,
        defaultRippleColor: Int,
        resolved: ResolvedModifiers,
    ) {
        ViewModifierApplier.bindView(
            view = view,
            node = node,
            defaultRippleColor = defaultRippleColor,
            resolved = resolved,
        )
    }

    private fun scheduleAndroidViewCommit(
        transaction: RenderTransaction,
        view: View,
        node: VNode,
    ) {
        if (node.type != NodeType.AndroidView) return
        val onCommit = node.requireSpec<AndroidViewNodeProps>().onCommit ?: return
        // onCommit 延迟到事务成功后执行，避免失败回滚后业务收到提交信号。
        // onCommit is deferred until transaction success so business code is not notified after a rolled-back render.
        transaction.commitEffects += RenderTreeCommitEffect(
            operation = AndroidViewOperation.Commit,
            nodeKey = node.key,
            commit = {
                node.runAndroidViewOperation(AndroidViewOperation.Commit) {
                    onCommit(view)
                }
            },
        )
    }

    private fun captureNode(
        transaction: RenderTransaction,
        mountedNode: MountedNode,
    ) {
        // 同一个 node 在事务中只捕获第一次状态，确保 rollback 回到事务开始前。
        // Capture each node only once per transaction so rollback returns to the transaction start state.
        transaction.mountedCheckpoints.getOrPut(mountedNode) {
            MountedCheckpoint(
                mountedNode = mountedNode,
                vnode = mountedNode.vnode,
                children = mountedNode.children.toList(),
                layoutParams = mountedNode.view.layoutParams,
                disposed = mountedNode.disposed,
            )
        }
    }

    private fun captureContainer(
        transaction: RenderTransaction,
        target: ViewGroup,
    ) {
        val container = resolveChildHost(target)
        // ChildHostViewGroup 的真实 child host 需要单独捕获，否则恢复顺序会落在外层壳上。
        // ChildHostViewGroup's actual child host must be captured or order restoration would target the shell.
        transaction.containerCheckpoints.getOrPut(container) {
            ContainerCheckpoint(
                container = container,
                children = List(container.childCount, container::getChildAt),
            )
        }
    }

    private fun moveViewToIndex(
        container: ViewGroup,
        view: View,
        targetIndex: Int,
    ) {
        val currentIndex = container.indexOfChild(view)
        if (currentIndex == -1 || currentIndex == targetIndex) {
            return
        }
        container.removeViewAt(currentIndex)
        container.addView(
            view,
            targetIndex.coerceAtMost(container.childCount),
        )
        DecorationChildDrawingOrder.invalidate(container)
    }

    private fun NodeBindingPlan.toPatchOperation(): RenderPatchOperation {
        return when (this) {
            NodeBindingPlan.Rebind -> RenderPatchOperation.Rebind
            NodeBindingPlan.SkipSelfOnly -> RenderPatchOperation.SkipSelf
            NodeBindingPlan.SkipSubtree -> RenderPatchOperation.SkipSubtree
            is NodeBindingPlan.Patch -> RenderPatchOperation.Patch
        }
    }

    private fun RenderTransaction.recordPatch(record: RenderPatchRecord) {
        if (patchRecords.size < MAX_PATCH_RECORDS) {
            patchRecords += record
        }
    }

    private fun resolveChildHost(container: ViewGroup): ViewGroup {
        return (container as? ChildHostViewGroup)?.childHost ?: container
    }

    private const val MAX_PATCH_RECORDS = 5_000
}
