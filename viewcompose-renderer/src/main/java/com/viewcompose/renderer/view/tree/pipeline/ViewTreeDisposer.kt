package com.viewcompose.renderer.view.tree

import androidx.recyclerview.widget.RecyclerView
import com.viewcompose.ui.node.NodeType
import com.viewcompose.ui.node.spec.AndroidViewNodeProps
import com.viewcompose.ui.node.spec.AndroidViewOperation
import com.viewcompose.ui.node.spec.LazyColumnNodeProps
import com.viewcompose.ui.node.spec.LazyRowNodeProps
import com.viewcompose.renderer.view.container.DeclarativeHorizontalPagerLayout
import com.viewcompose.renderer.view.container.DeclarativeLazyVerticalGridLayout
import com.viewcompose.renderer.view.container.DeclarativeTabRowLayout
import com.viewcompose.renderer.view.container.DeclarativeVerticalPagerLayout
import com.viewcompose.renderer.view.lazy.adapter.LazyListAdapter

/**
 * MountedNode 释放器。
 * MountedNode disposer.
 *
 * 释放顺序是子节点、modifier 持有资源、容器私有资源、AndroidView release 回调。
 * Disposal order is children, modifier-owned resources, container-private resources, then AndroidView release callback.
 */
internal object ViewTreeDisposer {
    /**
     * 递归释放一个已挂载节点。
     * Recursively disposes one mounted node.
     */
    fun disposeMountedNode(mountedNode: MountedNode) {
        if (mountedNode.disposed) return
        mountedNode.disposed = true
        val failures = mutableListOf<Throwable>()
        // 尽量释放所有资源；最后把第一个异常抛出，并附带其余 suppressed 异常。
        // Release as many resources as possible; throw the first error with the rest suppressed at the end.
        fun disposeOperation(block: () -> Unit) {
            try {
                block()
            } catch (error: Throwable) {
                failures += error
            }
        }

        mountedNode.children.forEach { child ->
            disposeOperation {
                disposeMountedNode(child)
            }
        }
        disposeOperation {
            ModifierFocusInputApplier.dispose(mountedNode.view)
        }
        disposeOperation {
            ModifierNestedScrollApplier.dispose(mountedNode.view)
        }
        disposeOperation {
            (mountedNode.view as? DeclarativeHorizontalPagerLayout)?.dispose()
        }
        disposeOperation {
            (mountedNode.view as? DeclarativeVerticalPagerLayout)?.dispose()
        }
        disposeOperation {
            (mountedNode.view as? DeclarativeTabRowLayout)?.dispose()
        }
        disposeOperation {
            (mountedNode.view as? DeclarativeLazyVerticalGridLayout)?.dispose()
        }
        disposeOperation {
            (mountedNode.view as? RecyclerView)?.let { recyclerView ->
                if (mountedNode.view !is DeclarativeLazyVerticalGridLayout) {
                    (recyclerView.adapter as? LazyListAdapter)?.disposeAll()
                }
                when (mountedNode.vnode.type) {
                    NodeType.LazyColumn -> mountedNode.vnode.requireSpec<LazyColumnNodeProps>().state?.attach(null)
                    NodeType.LazyRow -> mountedNode.vnode.requireSpec<LazyRowNodeProps>().state?.attach(null)
                    else -> Unit
                }
            }
        }
        mountedNode.children = emptyList()
        ViewNodeToolingRegistry.clear(mountedNode.view)
        if (mountedNode.vnode.type == NodeType.AndroidView) {
            disposeOperation {
                mountedNode.vnode.runAndroidViewOperation(AndroidViewOperation.Release) {
                    mountedNode.vnode
                        .requireSpec<AndroidViewNodeProps>()
                        .onRelease
                        ?.invoke(mountedNode.view)
                }
            }
        }
        failures.firstOrNull()?.let { first ->
            failures.drop(1).forEach(first::addSuppressed)
            throw first
        }
    }
}
