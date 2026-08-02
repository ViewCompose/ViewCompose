package com.viewcompose.renderer.decoration

import android.view.View
import android.view.ViewGroup
import com.viewcompose.renderer.R
import java.util.IdentityHashMap

/**
 * Maintains stable declarative sibling drawing order independently of platform elevation.
 *
 * Values are stored on child Views and the parent order is rebuilt lazily. Equal `zIndex` values
 * retain platform child order, so updating this metadata does not change measurement or layout.
 */
object DecorationChildDrawingOrder {
    /**
     * Replaces the declarative drawing depth associated with [view].
     *
     * @param view mounted child whose drawing metadata is updated
     * @param zIndex finite drawing depth; larger values draw later, and `0f` removes stored metadata
     * @return `true` when the effective value changed and the parent order was invalidated
     * @throws IllegalArgumentException if [zIndex] is not finite
     */
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

    /** Registers previously stored child metadata after [child] is attached to [parent]. */
    fun onViewAdded(parent: ViewGroup, child: View) {
        val zIndex = zIndexOrNull(child) ?: return
        state(parent).update(parent, child, zIndex)
    }

    /** Removes [child] from the cached drawing order before or after detachment from [parent]. */
    fun onViewRemoved(parent: ViewGroup, child: View) {
        stateOrNull(parent)?.remove(parent, child)
    }

    /** Marks [parent]'s cached child order dirty after structural order changes. */
    fun invalidate(parent: ViewGroup) {
        stateOrNull(parent)?.let { state ->
            state.dirty = true
            parent.invalidate()
        }
    }

    /**
     * Returns the platform child index to draw at [drawingPosition].
     *
     * @param parent parent whose direct children are being drawn
     * @param childCount current direct-child count supplied by [ViewGroup]
     * @param drawingPosition zero-based position in the drawing sequence
     * @return platform child index ordered by ascending declarative `zIndex`, then child order
     */
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

    /** Returns the stored declarative drawing depth for [view], or `0f` when none is stored. */
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
