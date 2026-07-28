package com.viewcompose.renderer.view.tree

import com.viewcompose.ui.modifier.AnimateContentSizeModifierElement
import com.viewcompose.ui.modifier.BoxAlignModifierElement
import com.viewcompose.ui.modifier.HeightModifierElement
import com.viewcompose.ui.modifier.HorizontalAlignModifierElement
import com.viewcompose.ui.modifier.MarginModifierElement
import com.viewcompose.ui.modifier.Modifier
import com.viewcompose.ui.modifier.ModifierElement
import com.viewcompose.ui.modifier.OffsetModifierElement
import com.viewcompose.ui.modifier.SizeModifierElement
import com.viewcompose.ui.modifier.VerticalAlignModifierElement
import com.viewcompose.ui.modifier.WeightModifierElement
import com.viewcompose.ui.modifier.WidthModifierElement
import com.viewcompose.ui.modifier.ZIndexModifierElement
import com.viewcompose.ui.node.NodeType
import com.viewcompose.ui.node.VNode
import com.viewcompose.ui.node.spec.AnimatedSizeHostNodeProps

/**
 * 将 animateContentSize modifier 提升为显式 AnimatedSizeHost 节点。
 * Promotes animateContentSize modifiers into explicit AnimatedSizeHost nodes.
 *
 * 这样 renderer 可以用普通容器测量/布局流程承载尺寸动画，而业务 VNode 仍保持原 DSL 语义。
 * This lets the renderer host size animation through normal container measure/layout while preserving the original DSL semantics.
 */
internal object AnimatedSizeNodeWrapper {
    /**
     * 包装整棵 VNode 子树；无变化时返回原列表以保留引用相等优化。
     * Wraps a VNode subtree and returns the original list when unchanged to preserve referential optimizations.
     */
    fun wrapTree(nodes: List<VNode>): List<VNode> {
        var changedNodes: MutableList<VNode>? = null
        nodes.forEachIndexed { index, node ->
            val wrapped = wrapNode(node)
            if (wrapped !== node && changedNodes == null) {
                changedNodes = ArrayList<VNode>(nodes.size).also { result ->
                    repeat(index) { previousIndex ->
                        result += nodes[previousIndex]
                    }
                }
            }
            changedNodes?.add(wrapped)
        }
        return changedNodes ?: nodes
    }

    private fun wrapNode(node: VNode): VNode {
        val wrappedChildren = wrapTree(node.children)
        if (node.type == NodeType.AnimatedSizeHost) {
            return if (wrappedChildren === node.children) {
                node
            } else {
                node.copy(children = wrappedChildren)
            }
        }
        val animateElement = node.modifier.elements
            .asReversed()
            .filterIsInstance<AnimateContentSizeModifierElement>()
            .firstOrNull()
            ?: return if (wrappedChildren === node.children) {
                node
            } else {
                node.copy(children = wrappedChildren)
            }
        val withoutAnimate = node.modifier.elements.filterNot { it is AnimateContentSizeModifierElement }
        val (hostElements, childElements) = splitHostAndChildElements(withoutAnimate)
        // 尺寸、margin、parent-data 等布局 modifier 必须留在外层 host，内容 modifier 留给原节点。
        // Layout modifiers such as size, margin, and parent data must stay on the outer host; content modifiers stay on the original node.
        val wrappedChild = node.copy(
            modifier = childElements.toModifier(),
            children = wrappedChildren,
        )
        return VNode(
            type = NodeType.AnimatedSizeHost,
            key = node.key?.let(::AnimatedSizeHostKey),
            spec = AnimatedSizeHostNodeProps(
                animationSpec = animateElement.animationSpec,
            ),
            modifier = hostElements.toModifier(),
            children = listOf(wrappedChild),
        )
    }

    private fun splitHostAndChildElements(elements: List<ModifierElement>): Pair<List<ModifierElement>, List<ModifierElement>> {
        val host = mutableListOf<ModifierElement>()
        val child = mutableListOf<ModifierElement>()
        elements.forEach { element ->
            if (element.isHostLayoutElement()) {
                host += element
            } else {
                child += element
            }
        }
        return host to child
    }

    private fun ModifierElement.isHostLayoutElement(): Boolean {
        return this is MarginModifierElement ||
            this is SizeModifierElement ||
            this is WidthModifierElement ||
            this is HeightModifierElement ||
            this is WeightModifierElement ||
            this is BoxAlignModifierElement ||
            this is HorizontalAlignModifierElement ||
            this is VerticalAlignModifierElement ||
            this is OffsetModifierElement ||
            this is ZIndexModifierElement
    }

    private fun List<ModifierElement>.toModifier(): Modifier {
        var modifier: Modifier = Modifier
        forEach { element ->
            modifier = modifier.then(element)
        }
        return modifier
    }

    /**
     * 派生 host key，避免外层包装节点与被包装 child 共用同一个 key。
     * Derived host key preventing the wrapper node from sharing the same key as the wrapped child.
     */
    private data class AnimatedSizeHostKey(
        val childKey: Any,
    )
}
