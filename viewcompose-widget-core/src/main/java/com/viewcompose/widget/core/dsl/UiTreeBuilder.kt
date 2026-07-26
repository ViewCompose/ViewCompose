package com.viewcompose.widget.core

import com.viewcompose.ui.modifier.Modifier
import com.viewcompose.ui.node.LazyListItem
import com.viewcompose.ui.node.NodeType
import com.viewcompose.ui.node.VNode
import com.viewcompose.ui.node.collection.TabRowTab
import com.viewcompose.ui.node.spec.HorizontalPagerNodeProps
import com.viewcompose.ui.node.spec.LazyColumnNodeProps
import com.viewcompose.ui.node.spec.LazyRowNodeProps
import com.viewcompose.ui.node.spec.LazyVerticalGridNodeProps
import com.viewcompose.ui.node.spec.NodeSpec
import com.viewcompose.ui.node.spec.TabRowNodeProps
import com.viewcompose.ui.node.spec.VerticalPagerNodeProps

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
            inputs = inputs,
            reuseResult = List<VNode>::hasSameElementReferences,
        ) { scope ->
            val restoreSnapshot = (scope.localSnapshotOrNull() as? LocalSnapshot) ?: parentSnapshot
            var nextNodes: List<VNode>? = null
            LocalContext.withSnapshot(restoreSnapshot) {
                nextNodes = UiTreeBuilder().apply(content).build()
                scope.updateLocalSnapshot(LocalContext.snapshot())
            }
            checkNotNull(nextNodes)
        }
        children += boundaryNodes
    }

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
            ),
            reuseResult = ::canReuseVNode,
        ) { scope ->
            val restoreSnapshot = (scope.localSnapshotOrNull() as? LocalSnapshot) ?: parentSnapshot
            var nextNode: VNode? = null
            LocalContext.withSnapshot(restoreSnapshot) {
                val nestedChildren = if (content == null) {
                    emptyList()
                } else {
                    UiTreeBuilder().apply(content).build()
                }
                nextNode = VNode(
                    type = type,
                    key = key,
                    spec = spec,
                    modifier = modifier,
                    children = nestedChildren,
                )
                scope.updateLocalSnapshot(LocalContext.snapshot())
            }
            checkNotNull(nextNode)
        }
        children += node
    }

    internal fun emitResolved(
        type: NodeType,
        key: Any? = null,
        spec: NodeSpec,
        modifier: Modifier = Modifier,
        children: List<VNode> = emptyList(),
    ) {
        this.children += VNode(
            type = type,
            key = key,
            spec = spec,
            modifier = modifier,
            children = children,
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

    private class EmitInputs(
        private val spec: NodeSpec,
        private val modifier: Modifier,
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is EmitInputs) return false
            return spec == other.spec &&
                modifier == other.modifier &&
                hasSameSessionIdentity(spec, other.spec)
        }

        override fun hashCode(): Int = 31 * spec.hashCode() + modifier.hashCode()
    }

    private companion object {
        val unkeyedContentSignatures =
            java.util.concurrent.ConcurrentHashMap<NodeType, EmitGroupSignature>()
        val unkeyedLeafSignatures =
            java.util.concurrent.ConcurrentHashMap<NodeType, EmitGroupSignature>()
        val unkeyedBoundarySignature = BoundaryGroupSignature(key = null)
    }
}

fun buildVNodeTree(content: UiTreeBuilder.() -> Unit): List<VNode> {
    return UiTreeBuilder().apply(content).build()
}

private fun canReuseVNode(
    previous: VNode,
    next: VNode,
): Boolean {
    return previous.type == next.type &&
        previous.key == next.key &&
        previous.spec == next.spec &&
        hasSameSessionIdentity(previous.spec, next.spec) &&
        previous.modifier == next.modifier &&
        previous.children.hasSameElementReferences(next.children)
}

private fun List<VNode>.hasSameElementReferences(other: List<VNode>): Boolean {
    if (size != other.size) return false
    return indices.all { index -> this[index] === other[index] }
}

private fun hasSameSessionIdentity(
    previous: NodeSpec,
    next: NodeSpec,
): Boolean {
    return when {
        previous is LazyColumnNodeProps && next is LazyColumnNodeProps -> {
            previous.items.hasSameSessionIdentity(next.items)
        }

        previous is LazyRowNodeProps && next is LazyRowNodeProps -> {
            previous.items.hasSameSessionIdentity(next.items)
        }

        previous is LazyVerticalGridNodeProps && next is LazyVerticalGridNodeProps -> {
            previous.items.hasSameSessionIdentity(next.items)
        }

        previous is HorizontalPagerNodeProps && next is HorizontalPagerNodeProps -> {
            previous.pages.hasSameSessionIdentity(next.pages)
        }

        previous is VerticalPagerNodeProps && next is VerticalPagerNodeProps -> {
            previous.pages.hasSameSessionIdentity(next.pages)
        }

        previous is TabRowNodeProps && next is TabRowNodeProps -> {
            previous.tabs.hasSameTabSessionIdentity(next.tabs)
        }

        else -> true
    }
}

private fun List<LazyListItem>.hasSameSessionIdentity(other: List<LazyListItem>): Boolean {
    if (size != other.size) return false
    return indices.all { index ->
        this[index].sessionFactory === other[index].sessionFactory &&
            this[index].sessionUpdater === other[index].sessionUpdater
    }
}

private fun List<TabRowTab>.hasSameTabSessionIdentity(other: List<TabRowTab>): Boolean {
    if (size != other.size) return false
    return indices.all { index ->
        val previous = this[index].item
        val next = other[index].item
        previous.sessionFactory === next.sessionFactory &&
            previous.sessionUpdater === next.sessionUpdater
    }
}
