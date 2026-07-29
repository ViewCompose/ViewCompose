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

/**
 * ViewCompose 声明式树构建器，负责把 widget DSL 调用收集为平台无关的 VNode 列表。
 * ViewCompose declarative tree builder that collects widget DSL calls into platform-neutral VNode lists.
 */
@UiDslMarker
open class UiTreeBuilder {
    private val children = mutableListOf<VNode>()

    /**
     * 创建显式重组边界，但不产生 Android View。
     * Creates an explicit restart boundary without emitting an Android View.
     *
     * [content] 中读取的 snapshot state 只会让该边界及其祖先失效。
     * 普通 Kotlin 变量捕获必须放入 [inputs]，否则缓存结果不会因变量变化而刷新。
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
            var nextNodes: List<VNode>? = null
            LocalContext.withSnapshot(parentSnapshot) {
                nextNodes = UiTreeBuilder().apply(content).build()
                scope.updateLocalSnapshot(LocalContext.snapshot())
            }
            checkNotNull(nextNodes)
        }
        children += boundaryNodes
    }

    /**
     * 发射一个 VNode，带 content 时会递归构建子树。
     * Emits one VNode and recursively builds its subtree when content is provided.
     *
     * 在活跃 composition 中，节点会被包进 composer group 以支持增量重组和结果复用。
     * During active composition, the node is wrapped in a composer group for incremental recomposition and result reuse.
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
                nextNode = VNode(
                    type = type,
                    key = key,
                    spec = spec,
                    modifier = modifier,
                    children = nestedChildren,
                    environment = Environment.values,
                )
                scope.updateLocalSnapshot(LocalContext.snapshot())
            }
            checkNotNull(nextNode)
        }
        children += node
    }

    /**
     * 直接追加已经解析好的 VNode 数据，供内部 DSL 或测试绕过 composer group 使用。
     * Appends resolved VNode data directly, allowing internal DSLs or tests to bypass composer groups.
     */
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
            environment = Environment.values,
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

    /**
     * 节点重组输入，额外比较 session identity，避免懒容器内容 lambda 被误复用。
     * Node recomposition inputs that also compare session identity to avoid reusing stale lazy-container lambdas.
     */
    private class EmitInputs(
        private val spec: NodeSpec,
        private val modifier: Modifier,
        private val localSnapshot: LocalSnapshot,
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is EmitInputs) return false
            return spec == other.spec &&
                modifier == other.modifier &&
                localSnapshot == other.localSnapshot &&
                hasSameSessionIdentity(spec, other.spec)
        }

        override fun hashCode(): Int {
            var result = spec.hashCode()
            result = 31 * result + modifier.hashCode()
            result = 31 * result + localSnapshot.hashCode()
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
 * 在无宿主 session 的情况下构建一棵 VNode 树。
 * Builds one VNode tree without requiring a host session.
 */
fun buildVNodeTree(content: UiTreeBuilder.() -> Unit): List<VNode> {
    return UiTreeBuilder().apply(content).build()
}

/**
 * 判断两个 VNode 是否可以直接复用上一轮结果。
 * Returns whether two VNodes can reuse the previous composition result.
 */
private fun canReuseVNode(
    previous: VNode,
    next: VNode,
): Boolean {
    return previous.type == next.type &&
        previous.key == next.key &&
        previous.spec == next.spec &&
        hasSameSessionIdentity(previous.spec, next.spec) &&
        previous.modifier == next.modifier &&
        previous.environment == next.environment &&
        previous.children.hasSameElementReferences(next.children)
}

/**
 * 只比较子节点对象引用，确保子树未被重新构建时才复用父结果。
 * Compares child node references only, ensuring parent reuse happens only when the subtree was not rebuilt.
 */
private fun List<VNode>.hasSameElementReferences(other: List<VNode>): Boolean {
    if (size != other.size) return false
    return indices.all { index -> this[index] === other[index] }
}

/**
 * 比较带独立子 session 的节点是否仍指向相同内容工厂。
 * Compares whether nodes with child sessions still point to the same content factories.
 */
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

/**
 * 比较 lazy item 的 session 工厂和 updater 引用是否保持不变。
 * Compares whether lazy item session factories and updaters keep the same references.
 */
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
