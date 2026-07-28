package com.viewcompose.renderer.reconcile

import com.viewcompose.ui.node.VNode

/**
 * child reconcile 的输出结果。
 * Output of child reconciliation.
 */
data class ReconcileResult<T>(
    val patches: List<RenderPatch<T>>,
    val removals: List<RemovePatch<T>>,
)

/**
 * 将上一轮 child 列表与新 VNode 列表对齐为 insert/reuse/remove patch。
 * Aligns previous children with new VNodes into insert/reuse/remove patches.
 *
 * 有 key 的节点按 key 优先复用；无 key 节点只在相同 index 且类型相同时复用。
 * Keyed nodes reuse by key first; unkeyed nodes only reuse at the same index with the same type.
 */
object ChildReconciler {
    /**
     * 生成下一轮渲染所需的 patch 与删除列表。
     * Builds patches and removals needed for the next render.
     */
    fun <T> reconcile(
        previous: List<ReconcileNode<T>>,
        nodes: List<VNode>,
    ): ReconcileResult<T> {
        val usedPrevious = BooleanArray(previous.size)
        val keyedIndex = buildKeyedIndex(previous)
        val patches = buildList {
            nodes.forEachIndexed { index, node ->
                val reusableIndex = findReusableIndex(
                    previous = previous,
                    usedPrevious = usedPrevious,
                    keyedIndex = keyedIndex,
                    targetIndex = index,
                    node = node,
                )
                val previousNode = reusableIndex?.let(previous::get)
                if (previousNode != null) {
                    usedPrevious[reusableIndex] = true
                    add(
                        ReusePatch(
                            targetIndex = index,
                            previousIndex = reusableIndex,
                            payload = previousNode.payload,
                            nextVNode = node,
                        ),
                    )
                } else {
                    add(
                        InsertPatch(
                            targetIndex = index,
                            nextVNode = node,
                        ),
                    )
                }
            }
        }
        val removals = buildList {
            previous.forEachIndexed { index, mountedNode ->
                if (!usedPrevious[index]) {
                    add(
                        RemovePatch(
                            previousIndex = index,
                            payload = mountedNode.payload,
                        ),
                    )
                }
            }
        }
        return ReconcileResult(
            patches = patches,
            removals = removals,
        )
    }

    private fun <T> buildKeyedIndex(
        previous: List<ReconcileNode<T>>,
    ): Map<Any, MutableList<Int>> {
        val map = HashMap<Any, MutableList<Int>>(previous.size)
        previous.forEachIndexed { index, node ->
            val key = node.vnode.key
            if (key != null) {
                map.getOrPut(key) { mutableListOf() }.add(index)
            }
        }
        return map
    }

    private fun <T> findReusableIndex(
        previous: List<ReconcileNode<T>>,
        usedPrevious: BooleanArray,
        keyedIndex: Map<Any, MutableList<Int>>,
        targetIndex: Int,
        node: VNode,
    ): Int? {
        if (node.key != null) {
            // keyed 节点允许跨 index 复用，从而支持重排时保留平台 View。
            // Keyed nodes may be reused across indexes, preserving platform Views during reorder.
            val candidates = keyedIndex[node.key] ?: return null
            for (candidateIndex in candidates) {
                if (!usedPrevious[candidateIndex] &&
                    canReuse(previous[candidateIndex].vnode, node)
                ) {
                    return candidateIndex
                }
            }
            return null
        }

        // unkeyed 节点只按位置复用，避免同类型兄弟重排时错绑状态。
        // Unkeyed nodes reuse only by position to avoid misbinding state among reordered same-type siblings.
        val candidate = previous.getOrNull(targetIndex) ?: return null
        return if (!usedPrevious[targetIndex] && canReuse(candidate.vnode, node)) {
            targetIndex
        } else {
            null
        }
    }

    private fun canReuse(previous: VNode, next: VNode): Boolean {
        if (previous.type != next.type) {
            return false
        }
        return previous.key == next.key
    }
}
