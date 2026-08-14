package com.viewcompose.ui.foundation

import com.viewcompose.ui.modifier.Modifier
import com.viewcompose.ui.node.NodeType
import com.viewcompose.ui.node.VNode
import com.viewcompose.ui.node.spec.ImageNodeSpec
import com.viewcompose.ui.node.spec.NodeSpec
import com.viewcompose.ui.tooling.UiNodeTooling

/**
 * ViewCompose declarative tree builder that collects widget DSL calls into platform-neutral VNode lists.
 */
@UiDslMarker
open class UiTreeBuilder {
    private val children = mutableListOf<VNode>()

    /**
     * Creates an explicit restart boundary without emitting an Android View.
     *
     * Snapshot state read by [content] invalidates only this boundary and its ancestors. Values
     * captured from ordinary Kotlin variables must be included in [inputs] so the cached result is
     * refreshed when they change.
     */
    fun RecomposeBoundary(
        key: Any? = null,
        inputs: List<Any?> = emptyList(),
        content: UiTreeBuilder.() -> Unit,
    ) {
        val composer = ComposerContext.currentComposer()
        if (composer == null) {
            children += UiTreeBuilder().apply(content).build()
            return
        }
        val parentSnapshot = LocalContext.snapshot()
        val boundaryNodes = composer.runGroup(
            signature = if (key == null) {
                unkeyedBoundarySignature
            } else {
                BoundaryGroupSignature(key)
            },
            inputs = BoundaryInputs(
                explicit = inputs,
                environment = parentSnapshot,
            ),
            reuseResult = List<VNode>::hasSameElementReferences,
        ) { scope ->
            var nextNodes: List<VNode>? = null
            LocalContext.withSnapshot(parentSnapshot) {
                nextNodes = UiTreeBuilder().apply(content).build()
                scope.updateLocalSnapshot(LocalContext.snapshot())
            }
            checkNotNull(nextNodes)
        }
        children += boundaryNodes
    }

    /** Every restart boundary observes framework locals without requiring caller-owned tokens. */
    private data class BoundaryInputs(
        val explicit: List<Any?>,
        val environment: LocalSnapshot,
    )

    /**
     * Emits one VNode and recursively records its optional child subtree.
     *
     * This is the low-level Q3 construction boundary for custom components; application components
     * normally use the typed widget functions. During an active composition, [type], [key], [spec],
     * [modifier], the current Local snapshot, and the reference identity of [content] participate in
     * group reuse. A newly supplied content closure is executed even when its other inputs compare
     * equal, so ordinary Kotlin values captured by that closure cannot leave a stale child tree. An
     * exact retained closure may reuse its previous result when no observed state or other input has
     * invalidated the group.
     *
     * [content] runs synchronously on the composition thread. The emitted node captures the current
     * environment, and [modifier] is stored unchanged for the renderer to interpret in order.
     *
     * @sample com.viewcompose.ui.foundation.samples.emittedContentClosureSample
     *
     * @receiver active tree builder receiving the node
     * @param type renderer node type paired with [spec]
     * @param key optional stable sibling identity used during reconciliation
     * @param spec immutable renderer properties compatible with [type]
     * @param modifier ordered layout, drawing, input, and semantics configuration
     * @param content optional child tree recorded beneath the emitted node
     */
    fun emit(
        type: NodeType,
        key: Any? = null,
        spec: NodeSpec,
        modifier: Modifier = Modifier,
        content: (UiTreeBuilder.() -> Unit)? = null,
    ) {
        val composer = ComposerContext.currentComposer()
        if (composer == null) {
            val nestedChildren = if (content == null) {
                emptyList()
            } else {
                UiTreeBuilder().apply(content).build()
            }
            emitResolved(
                type = type,
                key = key,
                spec = spec,
                modifier = modifier,
                children = nestedChildren,
            )
            return
        }
        val parentSnapshot = LocalContext.snapshot()
        val node = composer.runGroup(
            signature = emitGroupSignature(
                type = type,
                key = key,
                hasContent = content != null,
            ),
            inputs = EmitInputs(
                spec,
                modifier,
                parentSnapshot,
                content,
            ),
            reuseResult = ::canReuseVNode,
        ) { scope ->
            var nextNode: VNode? = null
            LocalContext.withSnapshot(parentSnapshot) {
                val nestedChildren = if (content == null) {
                    emptyList()
                } else {
                    UiTreeBuilder().apply(content).build()
                }
                nextNode = UiNodeTooling.attach(
                    VNode(
                        type = type,
                        key = key,
                        spec = spec,
                        modifier = modifier,
                        children = nestedChildren,
                        environment = Environment.values,
                    ),
                )
                scope.updateLocalSnapshot(LocalContext.snapshot())
            }
            checkNotNull(nextNode)
        }
        children += node
    }

    /**
     * Appends resolved VNode data directly, allowing internal DSLs or tests to bypass composer groups.
     */
    internal fun emitResolved(
        type: NodeType,
        key: Any? = null,
        spec: NodeSpec,
        modifier: Modifier = Modifier,
        children: List<VNode> = emptyList(),
    ) {
        this.children += UiNodeTooling.attach(
            VNode(
                type = type,
                key = key,
                spec = spec,
                modifier = modifier,
                children = children,
                environment = Environment.values,
            ),
        )
    }

    internal fun build(): List<VNode> = children.toList()

    private data class EmitGroupSignature(
        val type: NodeType,
        val key: Any?,
        val hasContent: Boolean,
    )

    private data class BoundaryGroupSignature(
        val key: Any?,
    )

    private fun emitGroupSignature(
        type: NodeType,
        key: Any?,
        hasContent: Boolean,
    ): EmitGroupSignature {
        if (key != null) {
            return EmitGroupSignature(
                type = type,
                key = key,
                hasContent = hasContent,
            )
        }
        return if (hasContent) {
            unkeyedContentSignatures.getOrPut(type) {
                EmitGroupSignature(
                    type = type,
                    key = null,
                    hasContent = true,
                )
            }
        } else {
            unkeyedLeafSignatures.getOrPut(type) {
                EmitGroupSignature(
                    type = type,
                    key = null,
                    hasContent = false,
                )
            }
        }
    }

    /** Preserves reference-sensitive spec payloads and child-content identity during group reuse. */
    private class EmitInputs(
        private val spec: NodeSpec,
        private val modifier: Modifier,
        private val localSnapshot: LocalSnapshot,
        private val content: (UiTreeBuilder.() -> Unit)?,
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is EmitInputs) return false
            return spec == other.spec &&
                modifier == other.modifier &&
                localSnapshot == other.localSnapshot &&
                content === other.content &&
                hasSameReferenceIdentity(spec, other.spec)
        }

        override fun hashCode(): Int {
            var result = spec.hashCode()
            result = 31 * result + modifier.hashCode()
            result = 31 * result + localSnapshot.hashCode()
            result = 31 * result + (content?.let(System::identityHashCode) ?: 0)
            return result
        }
    }

    private companion object {
        val unkeyedContentSignatures =
            java.util.concurrent.ConcurrentHashMap<NodeType, EmitGroupSignature>()
        val unkeyedLeafSignatures =
            java.util.concurrent.ConcurrentHashMap<NodeType, EmitGroupSignature>()
        val unkeyedBoundarySignature = BoundaryGroupSignature(key = null)
    }
}

/**
 * Builds one VNode tree without requiring a host session.
 */
fun buildVNodeTree(content: UiTreeBuilder.() -> Unit): List<VNode> {
    return UiTreeBuilder().apply(content).build()
}

/**
 * Returns whether two VNodes can reuse the previous composition result.
 */
private fun canReuseVNode(
    previous: VNode,
    next: VNode,
): Boolean {
    return previous.type == next.type &&
        previous.key == next.key &&
        previous.spec == next.spec &&
        hasSameReferenceIdentity(previous.spec, next.spec) &&
        previous.modifier == next.modifier &&
        previous.environment == next.environment &&
        previous.children.hasSameElementReferences(next.children)
}

/**
 * Compares child node references only, ensuring parent reuse happens only when the subtree was not rebuilt.
 */
private fun List<VNode>.hasSameElementReferences(other: List<VNode>): Boolean {
    if (size != other.size) return false
    return indices.all { index -> this[index] === other[index] }
}

/**
 * Compares identities that are intentionally excluded from ordinary value equality.
 */
private fun hasSameReferenceIdentity(
    previous: NodeSpec,
    next: NodeSpec,
): Boolean {
    return when {
        previous is ImageNodeSpec && next is ImageNodeSpec -> {
            previous.imageLoader === next.imageLoader
        }

        else -> true
    }
}
