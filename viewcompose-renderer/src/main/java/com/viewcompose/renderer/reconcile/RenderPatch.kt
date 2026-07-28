package com.viewcompose.renderer.reconcile

import com.viewcompose.ui.node.VNode

/**
 * reconcile 阶段使用的前一轮节点与平台 payload 绑定。
 * Previous node plus platform payload binding used during reconciliation.
 */
data class ReconcileNode<T>(
    val vnode: VNode,
    val payload: T,
)

/**
 * 对目标 child index 的渲染 patch。
 * Render patch targeting a child index.
 */
sealed interface RenderPatch<T> {
    val targetIndex: Int
}

/**
 * 复用前一轮 payload，并用 nextVNode 更新其绑定。
 * Reuses a previous payload and updates its binding with nextVNode.
 */
data class ReusePatch<T>(
    override val targetIndex: Int,
    val previousIndex: Int,
    val payload: T,
    val nextVNode: VNode,
) : RenderPatch<T>

/**
 * 在目标位置插入一个新的 VNode。
 * Inserts a new VNode at the target position.
 */
data class InsertPatch<T>(
    override val targetIndex: Int,
    val nextVNode: VNode,
) : RenderPatch<T>

/**
 * 移除前一轮中未被复用的 payload。
 * Removes a previous payload that was not reused.
 */
data class RemovePatch<T>(
    val previousIndex: Int,
    val payload: T,
)
