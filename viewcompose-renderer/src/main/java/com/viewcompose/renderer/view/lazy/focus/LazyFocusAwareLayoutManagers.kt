package com.viewcompose.renderer.view.lazy.focus

import android.content.Context
import android.graphics.Rect
import android.view.View
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

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
        // Focus-follow is coordinated at the container layer to avoid
        // competing scroll anchors during state-driven relayout.
        return false
    }
}

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
        // Focus-follow is coordinated at the container layer to avoid
        // competing scroll anchors during state-driven relayout.
        return false
    }
}
