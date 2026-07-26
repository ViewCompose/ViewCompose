package com.viewcompose.renderer.view.tree

import androidx.recyclerview.widget.RecyclerView
import com.viewcompose.ui.node.NodeType
import com.viewcompose.ui.node.spec.AndroidViewNodeProps
import com.viewcompose.ui.node.spec.LazyColumnNodeProps
import com.viewcompose.ui.node.spec.LazyRowNodeProps
import com.viewcompose.renderer.view.container.DeclarativeHorizontalPagerLayout
import com.viewcompose.renderer.view.container.DeclarativeLazyVerticalGridLayout
import com.viewcompose.renderer.view.container.DeclarativeTabRowLayout
import com.viewcompose.renderer.view.container.DeclarativeVerticalPagerLayout
import com.viewcompose.renderer.view.lazy.adapter.LazyListAdapter

internal object ViewTreeDisposer {
    fun disposeMountedNode(mountedNode: MountedNode) {
        if (mountedNode.disposed) return
        mountedNode.disposed = true
        try {
            mountedNode.children.forEach(::disposeMountedNode)
            ModifierFocusInputApplier.dispose(mountedNode.view)
            (mountedNode.view as? DeclarativeHorizontalPagerLayout)?.dispose()
            (mountedNode.view as? DeclarativeVerticalPagerLayout)?.dispose()
            (mountedNode.view as? DeclarativeTabRowLayout)?.dispose()
            (mountedNode.view as? DeclarativeLazyVerticalGridLayout)?.dispose()
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
        } finally {
            mountedNode.children = emptyList()
            if (mountedNode.vnode.type == NodeType.AndroidView) {
                mountedNode.vnode
                    .requireSpec<AndroidViewNodeProps>()
                    .onRelease
                    ?.invoke(mountedNode.view)
            }
        }
    }
}
