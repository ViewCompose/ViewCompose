package com.viewcompose.renderer.view.container

import android.view.ViewGroup

/**
 * 标记真实承载子节点的 ViewGroup。
 * Marks the ViewGroup that should receive rendered child views.
 */
internal interface ChildHostViewGroup {
    /**
     * patch pipeline 追加、移动和删除 Android child 的目标容器。
     * Target container where the patch pipeline adds, moves, and removes Android children.
     */
    val childHost: ViewGroup
}
