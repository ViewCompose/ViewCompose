package com.viewcompose.renderer.view.tree

import com.viewcompose.ui.modifier.BoxAlignModifierElement
import com.viewcompose.ui.modifier.HeightModifierElement
import com.viewcompose.ui.modifier.HorizontalAlignModifierElement
import com.viewcompose.ui.modifier.MarginModifierElement
import com.viewcompose.ui.modifier.Modifier
import com.viewcompose.ui.modifier.ModifierElement
import com.viewcompose.ui.modifier.NestedScrollModifierElement
import com.viewcompose.ui.modifier.OffsetModifierElement
import com.viewcompose.ui.modifier.SizeModifierElement
import com.viewcompose.ui.modifier.VerticalAlignModifierElement
import com.viewcompose.ui.modifier.WeightModifierElement
import com.viewcompose.ui.modifier.WidthModifierElement
import com.viewcompose.ui.modifier.ZIndexModifierElement
import com.viewcompose.ui.node.NodeType
import com.viewcompose.ui.node.VNode
import com.viewcompose.ui.node.spec.EmptyNodeSpec
import com.viewcompose.ui.tooling.UiNodeTooling

/**
 * 将 nestedScroll modifier 提升为显式 NestedScrollHost 节点。
 * Promotes nestedScroll modifiers into explicit NestedScrollHost nodes.
 *
 * 多个 nestedScroll modifier 会生成嵌套 host，保持 modifier 顺序与事件分发链一致。
 * Multiple nestedScroll modifiers generate nested hosts, preserving modifier order and event dispatch chain.
 */
internal object NestedScrollNodeWrapper {
    /**
     * 包装整棵 VNode 子树；无变化时返回原列表以减少后续 diff 成本。
     * Wraps a VNode subtree and returns the original list when unchanged to reduce later diff cost.
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
        if (node.type == NodeType.NestedScrollHost) {
            return if (wrappedChildren === node.children) {
                node
            } else {
                UiNodeTooling.inheritCopy(
                    target = node.copy(children = wrappedChildren),
                    source = node,
                )
            }
        }
        val nestedElements = node.modifier.elements
            .filterIsInstance<NestedScrollModifierElement>()
        if (nestedElements.isEmpty()) {
            return if (wrappedChildren === node.children) {
                node
            } else {
                UiNodeTooling.inheritCopy(
                    target = node.copy(children = wrappedChildren),
                    source = node,
                )
            }
        }

        val withoutNested = node.modifier.elements
            .filterNot { element -> element is NestedScrollModifierElement }
        val (hostLayoutElements, childElements) = splitHostAndChildElements(withoutNested)
        // 外层 host 继承影响父布局的 modifier，内层原节点保留绘制/交互类 modifier。
        // The outer host inherits parent-layout modifiers, while the inner original node keeps drawing/interaction modifiers.
        var wrapped = UiNodeTooling.inheritCopy(
            target = node.copy(
                modifier = childElements.toModifier(),
                children = wrappedChildren,
            ),
            source = node,
        )
        nestedElements.asReversed().forEachIndexed { reversedIndex, nestedElement ->
            val sourceIndex = nestedElements.lastIndex - reversedIndex
            val isOutermost = sourceIndex == 0
            val hostElements = buildList {
                if (isOutermost) {
                    addAll(hostLayoutElements)
                }
                add(nestedElement)
            }
            wrapped = UiNodeTooling.inheritSynthetic(
                target = VNode(
                    type = NodeType.NestedScrollHost,
                    key = node.key?.let { childKey ->
                        NestedScrollHostKey(
                            childKey = childKey,
                            modifierIndex = sourceIndex,
                        )
                    },
                    spec = EmptyNodeSpec,
                    modifier = hostElements.toModifier(),
                    children = listOf(wrapped),
                ),
                source = node,
                discriminator = "nested-scroll-$sourceIndex",
            )
        }
        return wrapped
    }

    private fun splitHostAndChildElements(
        elements: List<ModifierElement>,
    ): Pair<List<ModifierElement>, List<ModifierElement>> {
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
     * 派生 host key，支持同一个 child 上多个 nestedScroll modifier 的稳定复用。
     * Derived host key supporting stable reuse for multiple nestedScroll modifiers on one child.
     */
    private data class NestedScrollHostKey(
        val childKey: Any,
        val modifierIndex: Int,
    )
}
