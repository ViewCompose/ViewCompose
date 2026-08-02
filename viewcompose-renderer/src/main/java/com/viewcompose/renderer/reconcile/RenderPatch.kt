package com.viewcompose.renderer.reconcile

import com.viewcompose.ui.node.VNode

/**
 * Associates a previously rendered VNode with its retained platform payload.
 *
 * @param T platform payload type owned by the patch consumer
 * @property vnode latest successfully committed declaration for the payload
 * @property payload platform object eligible for reuse or removal
 */
data class ReconcileNode<T>(
    val vnode: VNode,
    val payload: T,
)

/**
 * One insert-or-reuse operation targeting the next child sequence.
 *
 * Implementations are produced in ascending [targetIndex] order by [ChildReconciler].
 *
 * @param T platform payload type retained across frames
 */
sealed interface RenderPatch<T> {
    /** Zero-based index the child must occupy after the plan is committed. */
    val targetIndex: Int
}

/**
 * Reuses a previous payload and updates its binding with nextVNode.
 *
 * @param T platform payload type retained across frames
 * @property targetIndex zero-based destination index in the next sibling list
 * @property previousIndex zero-based source index in the previous sibling list
 * @property payload retained platform object from [previousIndex]
 * @property nextVNode declaration that must replace the payload's previous binding
 */
data class ReusePatch<T>(
    override val targetIndex: Int,
    val previousIndex: Int,
    val payload: T,
    val nextVNode: VNode,
) : RenderPatch<T>

/**
 * Inserts a new VNode at the target position.
 *
 * @param T platform payload type that the patch consumer will create
 * @property targetIndex zero-based destination index in the next sibling list
 * @property nextVNode declaration for the newly created payload
 */
data class InsertPatch<T>(
    override val targetIndex: Int,
    val nextVNode: VNode,
) : RenderPatch<T>

/**
 * Removes a previous payload that was not reused.
 *
 * @param T platform payload type owned by the patch consumer
 * @property previousIndex zero-based index occupied before reconciliation
 * @property payload platform object to detach and dispose during commit
 */
data class RemovePatch<T>(
    val previousIndex: Int,
    val payload: T,
)
