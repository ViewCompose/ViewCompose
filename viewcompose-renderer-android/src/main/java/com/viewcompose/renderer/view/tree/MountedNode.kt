package com.viewcompose.renderer.view.tree

import android.view.View
import com.viewcompose.ui.node.VNode

/**
 * Mutable renderer-owned association between a committed VNode and its Android View.
 *
 * Instances belong to one renderer-owned physical tree. They may move between lazy-item hosts only
 * through the renderer's reset/reuse protocol; callers must never move them directly. The renderer
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

    /** Forces a complete binding pass after this physical node moves to another logical owner. */
    internal var requiresCrossOwnerRebind: Boolean = false
}
