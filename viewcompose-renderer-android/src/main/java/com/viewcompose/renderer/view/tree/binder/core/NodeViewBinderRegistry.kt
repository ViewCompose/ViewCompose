package com.viewcompose.renderer.view.tree

import android.view.View
import com.viewcompose.ui.node.NodeType
import com.viewcompose.ui.node.VNode
import com.viewcompose.ui.node.spec.AndroidViewOperation
import com.viewcompose.ui.node.spec.NodeSpec
import kotlin.reflect.KClass

/** Internal View binder/patch applier registry used by the renderer. */
internal object NodeViewBinderRegistry {
    private val descriptors: Map<NodeType, NodeBinderDescriptor> by lazy {
        NodeBinderDescriptors.all.associateBy(NodeBinderDescriptor::nodeType)
    }
    private val patchDescriptors: Map<KClass<out NodeViewPatch>, NodePatchDescriptor> by lazy {
        NodeBinderDescriptors.all
            .mapNotNull(NodeBinderDescriptor::patch)
            .associateBy(NodePatchDescriptor::patchClass)
    }

    /** Performs a full bind for a newly created or rebound View. */
    fun bind(
        view: View,
        node: VNode,
        mode: NodeBindingMode = NodeBindingMode.Immediate,
    ): RenderTreeCommitEffect? {
        val descriptor = descriptors.getValue(node.type)
        val submission = RetainedSessionSubmission.create(
            deferred = descriptor.deferUntilCommit,
            mode = mode,
            nodeKey = node.key,
        )
        descriptor.bind(view, node, submission)
        return submission.commitEffect()
    }

    /** Applies a fine-grained patch to a reused View. */
    fun applyPatch(
        view: View,
        patch: NodeViewPatch,
        mode: NodeBindingMode = NodeBindingMode.Immediate,
        nodeKey: Any? = null,
    ): RenderTreeCommitEffect? {
        val descriptor = patchDescriptors[patch::class]
            ?: error("Unknown patch type: ${patch::class.simpleName}")
        val submission = RetainedSessionSubmission.create(
            deferred = descriptor.deferUntilCommit,
            mode = mode,
            nodeKey = nodeKey,
        )
        descriptor.apply(view, patch, submission)
        return submission.commitEffect()
    }

    internal fun descriptorsForTest(): List<NodeBinderDescriptor> = NodeBinderDescriptors.all

    internal fun patchAppliersForTest(): Map<KClass<out NodeViewPatch>, PatchApplyBlock> =
        NodeBinderDescriptors.patchAppliersByType()

    internal fun patchFactoriesForTest(): Map<KClass<out NodeSpec>, PatchFactory> =
        NodeBinderDescriptors.patchFactoriesBySpec()
}

/** Selects immediate, parent-commit, or rollback behavior for one native binder invocation. */
internal enum class NodeBindingMode {
    Immediate,
    Deferred,
    Rollback,
}

/** Collects retained-child submissions while ordinary parent View binding stays synchronous. */
internal class RetainedSessionSubmission private constructor(
    val revision: Long,
    private val mode: NodeBindingMode,
    private val nodeKey: Any?,
) {
    private val actions = mutableListOf<() -> Unit>()

    fun publish(action: () -> Unit) {
        when (mode) {
            NodeBindingMode.Immediate -> action()
            NodeBindingMode.Deferred -> actions += action
            NodeBindingMode.Rollback -> Unit
        }
    }

    fun commitEffect(): RenderTreeCommitEffect? {
        if (actions.isEmpty()) return null
        val pending = actions.toList()
        return RenderTreeCommitEffect(
            operation = AndroidViewOperation.Commit,
            nodeKey = nodeKey,
            commit = { pending.forEach { action -> action() } },
        )
    }

    companion object {
        private val nextRevision = java.util.concurrent.atomic.AtomicLong(0L)
        private val unused = RetainedSessionSubmission(
            revision = Long.MIN_VALUE,
            mode = NodeBindingMode.Rollback,
            nodeKey = null,
        )

        fun immediate(): RetainedSessionSubmission = create(
            deferred = true,
            mode = NodeBindingMode.Immediate,
            nodeKey = null,
        )

        internal fun create(
            deferred: Boolean,
            mode: NodeBindingMode,
            nodeKey: Any?,
        ): RetainedSessionSubmission {
            // Ordinary descriptors cannot publish retained-child work. Avoid a contended atomic
            // increment on every leaf-node bind while keeping revisions global for collection hosts.
            if (!deferred) return unused
            return RetainedSessionSubmission(
                revision = nextRevision.incrementAndGet(),
                mode = mode,
                nodeKey = nodeKey,
            )
        }
    }
}
