package com.viewcompose.renderer.view.tree

import android.view.View
import com.viewcompose.ui.node.VNode

/**
 * Mutable renderer-owned association between a committed VNode and its Android View.
 *
 * Instances belong to one `ViewTreeRenderer` tree and must not be moved between hosts. The renderer
 * replaces [vnode] and [children] only after their corresponding platform bindings succeed.
 *
 * @property vnode latest successfully committed declaration for [view]
 * @property view platform View owned by this mounted node until disposal
 * @property children direct mounted descendants managed inside [view]'s child host
 */
class MountedNode(
    var vnode: VNode,
    val view: View,
    var children: List<MountedNode> = emptyList(),
) {
    /** Prevents the same platform View from being released twice during recursive disposal. */
    internal var disposed: Boolean = false
}
