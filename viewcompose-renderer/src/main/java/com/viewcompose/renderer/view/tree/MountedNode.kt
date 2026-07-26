package com.viewcompose.renderer.view.tree

import android.view.View
import com.viewcompose.ui.node.VNode

class MountedNode(
    var vnode: VNode,
    val view: View,
    var children: List<MountedNode> = emptyList(),
) {
    internal var disposed: Boolean = false
}
