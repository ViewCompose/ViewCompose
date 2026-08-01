package com.viewcompose.renderer.decoration

import android.view.View
import android.view.ViewGroup
import com.viewcompose.renderer.R
import java.util.IdentityHashMap

/** Stable declarative sibling order, independent from platform elevation and shadow backends. */
object DecorationChildDrawingOrder {
    fun update(
        view: View,
        zIndex: Float,
    ): Boolean {
        require(zIndex.isFinite()) { "zIndex must be finite." }
        val normalized = zIndex.takeUnless { it == 0f }
        val previous = view.getTag(R.id.viewcompose_sibling_z_index) as? Float
        if (previous == normalized) return false
        view.setTag(R.id.viewcompose_sibling_z_index, normalized)
        (view.parent as? ViewGroup)?.let { parent ->
            if (normalized == null) {
                stateOrNull(parent)?.remove(parent, view)
            } else {
                state(parent).update(parent, view, normalized)
            }
        }
        return true
    }

    fun onViewAdded(parent: ViewGroup, child: View) {
        val zIndex = zIndexOrNull(child) ?: return
        state(parent).update(parent, child, zIndex)
    }

    fun onViewRemoved(parent: ViewGroup, child: View) {
        stateOrNull(parent)?.remove(parent, child)
    }

    fun invalidate(parent: ViewGroup) {
        stateOrNull(parent)?.let { state ->
            state.dirty = true
            parent.invalidate()
        }
    }

    fun getChildDrawingOrder(
        parent: ViewGroup,
        childCount: Int,
        drawingPosition: Int,
    ): Int {
        if (childCount <= 1) return drawingPosition
        val state = state(parent)
        if (state.dirty || state.childCount != childCount) {
            state.rebuild(parent, childCount)
        }
        return state.indices.getOrElse(drawingPosition) { drawingPosition }
    }

    fun zIndex(view: View): Float {
        return zIndexOrNull(view) ?: 0f
    }

    private fun state(parent: ViewGroup): DrawingOrderState {
        return stateOrNull(parent)
            ?: DrawingOrderState().also { parent.setTag(R.id.viewcompose_sibling_drawing_order, it) }
    }

    private fun stateOrNull(parent: ViewGroup): DrawingOrderState? {
        return parent.getTag(R.id.viewcompose_sibling_drawing_order) as? DrawingOrderState
    }

    private fun zIndexOrNull(view: View): Float? {
        return view.getTag(R.id.viewcompose_sibling_z_index) as? Float
    }

    private class DrawingOrderState {
        private val indexedChildren = IdentityHashMap<View, Float>()
        var dirty: Boolean = true
        var childCount: Int = -1
        var indices: IntArray = IntArray(0)

        fun update(parent: ViewGroup, child: View, zIndex: Float) {
            indexedChildren[child] = zIndex
            dirty = true
            parent.setDecorationDrawingOrderEnabled(true)
            parent.invalidate()
        }

        fun remove(parent: ViewGroup, child: View) {
            if (indexedChildren.remove(child) == null) return
            dirty = true
            if (indexedChildren.isEmpty()) {
                parent.setDecorationDrawingOrderEnabled(false)
                parent.setTag(R.id.viewcompose_sibling_drawing_order, null)
            }
            parent.invalidate()
        }

        fun rebuild(parent: ViewGroup, childCount: Int) {
            indices = (0 until childCount)
                .sortedWith(
                    compareBy<Int> { index -> indexedChildren[parent.getChildAt(index)] ?: 0f }
                        .thenBy { index -> index },
                )
                .toIntArray()
            this.childCount = childCount
            dirty = false
        }
    }
}

internal interface DecorationDrawingOrderContainer {
    fun setDecorationDrawingOrderEnabled(enabled: Boolean)
}

private fun ViewGroup.setDecorationDrawingOrderEnabled(enabled: Boolean) {
    when (this) {
        is ViewDecorationHostLayout -> setDecorationDrawingOrderEnabled(enabled)
        is DecorationDrawingOrderContainer -> setDecorationDrawingOrderEnabled(enabled)
    }
}
