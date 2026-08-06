package com.viewcompose.renderer.view.lazy.focus

import android.content.Context
import android.graphics.Rect
import android.view.View
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

/**
 * LinearLayoutManager that disables RecyclerView's default requestChildRectangleOnScreen behavior.
 * LinearLayoutManager that disables RecyclerView's default requestChildRectangleOnScreen behavior.
 *
 * Focus following is coordinated by the container to avoid competing scrolls during layout refresh and keyboard viewport changes.
 * Focus-follow is handled at the container layer to avoid competing scrolls during relayout and keyboard viewport updates.
 */
internal class LazyLinearLayoutManager(
    context: Context,
    orientation: Int = RecyclerView.VERTICAL,
    reverseLayout: Boolean = false,
) : LinearLayoutManager(context, orientation, reverseLayout) {
    override fun requestChildRectangleOnScreen(
        parent: RecyclerView,
        child: View,
        rect: Rect,
        immediate: Boolean,
        focusedChildVisible: Boolean,
    ): Boolean {
        // Container-level focus following avoids competing scroll anchors during state-driven reordering.
        // Focus-follow is coordinated at the container layer to avoid competing scroll anchors during state-driven relayout.
        return false
    }
}

/**
 * GridLayoutManager that disables default focus-rectangle scrolling.
 * GridLayoutManager that disables default focus-rectangle scrolling.
 */
internal class LazyGridLayoutManager(
    context: Context,
    spanCount: Int,
    reverseLayout: Boolean = false,
) : GridLayoutManager(context, spanCount, RecyclerView.VERTICAL, reverseLayout) {
    override fun requestChildRectangleOnScreen(
        parent: RecyclerView,
        child: View,
        rect: Rect,
        immediate: Boolean,
        focusedChildVisible: Boolean,
    ): Boolean {
        // Container-level focus following avoids competing scroll anchors during state-driven reordering.
        // Focus-follow is coordinated at the container layer to avoid competing scroll anchors during state-driven relayout.
        return false
    }
}
