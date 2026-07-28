package com.viewcompose.renderer.view.tree

import android.view.View
import com.viewcompose.ui.node.VNode

/**
 * 已挂载到 Android View 树中的节点记录。
 * Node record mounted into the Android View tree.
 *
 * vnode 表示最近一次成功绑定的声明节点，children 对应 renderer 管理的直接子 mounted nodes。
 * vnode is the latest successfully bound declarative node; children are direct mounted nodes owned by the renderer.
 */
class MountedNode(
    var vnode: VNode,
    val view: View,
    var children: List<MountedNode> = emptyList(),
) {
    /**
     * 防止 dispose 递归过程中重复释放同一个平台 View。
     * Prevents the same platform View from being released twice during recursive dispose.
     */
    internal var disposed: Boolean = false
}
