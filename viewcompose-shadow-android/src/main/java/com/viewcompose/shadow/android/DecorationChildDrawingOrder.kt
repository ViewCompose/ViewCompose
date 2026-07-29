package com.viewcompose.shadow.android

import android.view.View
import android.view.ViewGroup

/**
 * 独立于平台 elevation/translationZ 的稳定兄弟绘制顺序。
 * Stable sibling drawing order independent from platform elevation/translationZ.
 *
 * zIndex 较小的 child 先绘制；值相同时保持真实 child 顺序。排序结果只在 zIndex 或
 * View 树结构变化时重建，普通帧只执行 O(1) 索引读取。
 * Children with lower zIndex draw first, with real child order breaking ties. The sorted result is
 * rebuilt only when zIndex or tree structure changes; ordinary frames use O(1) index lookup.
 */
object DecorationChildDrawingOrder {
    /**
     * 更新 child 的声明式 zIndex，但不写入 View.translationZ，避免改变平台阴影。
     * Updates declarative zIndex without touching View.translationZ so platform shadows stay unchanged.
     */
    fun update(
        view: View,
        zIndex: Float,
    ): Boolean {
        require(zIndex.isFinite()) {
            "zIndex must be finite."
        }
        val normalized = zIndex.takeUnless { it == 0f }
        val previous = view.getTag(R.id.viewcompose_sibling_z_index) as? Float
        if (previous == normalized) return false
        view.setTag(R.id.viewcompose_sibling_z_index, normalized)
        (view.parent as? ViewGroup)?.let(::invalidate)
        return true
    }

    /** Marks a parent's cached order dirty after insert, move, or removal. */
    fun invalidate(parent: ViewGroup) {
        state(parent).dirty = true
        parent.invalidate()
    }

    /**
     * Returns the real child index for one drawing position.
     * Participating ViewGroups must enable custom child drawing order and delegate here.
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

    /** Returns the declarative zIndex currently attached to a child. */
    fun zIndex(view: View): Float {
        return view.getTag(R.id.viewcompose_sibling_z_index) as? Float ?: 0f
    }

    private fun state(parent: ViewGroup): DrawingOrderState {
        return (parent.getTag(R.id.viewcompose_sibling_drawing_order) as? DrawingOrderState)
            ?: DrawingOrderState().also { state ->
                parent.setTag(R.id.viewcompose_sibling_drawing_order, state)
            }
    }

    private class DrawingOrderState {
        var dirty: Boolean = true
        var childCount: Int = -1
        var indices: IntArray = IntArray(0)

        fun rebuild(
            parent: ViewGroup,
            childCount: Int,
        ) {
            indices = (0 until childCount)
                .sortedWith(
                    compareBy<Int> { index ->
                        zIndex(parent.getChildAt(index))
                    }.thenBy { index ->
                        index
                    },
                )
                .toIntArray()
            this.childCount = childCount
            dirty = false
        }
    }
}
