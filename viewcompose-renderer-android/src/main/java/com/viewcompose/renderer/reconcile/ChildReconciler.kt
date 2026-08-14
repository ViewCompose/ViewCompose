package com.viewcompose.renderer.reconcile

import com.viewcompose.ui.node.VNode

/**
 * Immutable child-reconciliation plan for one parent.
 *
 * @property patches target-ordered insert or reuse operations for every next child
 * @property removals previous children that were not selected for reuse
 */
data class ReconcileResult<T>(
    val patches: List<RenderPatch<T>>,
    val removals: List<RemovePatch<T>>,
)

/**
 * Aligns previous platform children with next VNodes without mutating either input.
 *
 * Keyed nodes reuse a previous payload with the same key and type even when their index changes.
 * Unkeyed nodes reuse only the payload at the same index and type, preventing state from moving
 * between indistinguishable siblings. Duplicate keys are consumed in previous-list order; callers
 * should enforce unique stable keys whenever identity must survive reordering.
 */
object ChildReconciler {
    /**
     * Builds patches and removals needed for the next render.
     *
     * The algorithm runs in linear expected time plus candidate scans for duplicate keys. Returned
     * lists are snapshots and may be applied after this method returns.
     *
     * @sample com.viewcompose.renderer.samples.childReconciliationSample
     * @param T platform payload retained for a mounted declarative node
     * @param previous previous nodes in current platform sibling order
     * @param nodes next declarative children in target sibling order
     * @return complete immutable plan containing one target patch per next node
     */
    fun <T> reconcile(
        previous: List<ReconcileNode<T>>,
        nodes: List<VNode>,
    ): ReconcileResult<T> = reconcile(
        previous = previous,
        nodes = nodes,
        reuseByTypeAtSamePosition = false,
    )

    internal fun <T> reconcileForCrossOwnerReuse(
        previous: List<ReconcileNode<T>>,
        nodes: List<VNode>,
    ): ReconcileResult<T> = reconcile(
        previous = previous,
        nodes = nodes,
        reuseByTypeAtSamePosition = true,
    )

    private fun <T> reconcile(
        previous: List<ReconcileNode<T>>,
        nodes: List<VNode>,
        reuseByTypeAtSamePosition: Boolean,
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
                    reuseByTypeAtSamePosition = reuseByTypeAtSamePosition,
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
        reuseByTypeAtSamePosition: Boolean,
    ): Int? {
        if (reuseByTypeAtSamePosition) {
            val candidate = previous.getOrNull(targetIndex) ?: return null
            return if (!usedPrevious[targetIndex] && candidate.vnode.type == node.type) {
                targetIndex
            } else {
                null
            }
        }
        if (node.key != null) {
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

        // Unkeyed nodes reuse only by position to avoid misbinding state among reordered siblings.
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
