package com.viewcompose.renderer.view.lazy.focus

import android.content.Context
import android.graphics.Rect
import android.view.View
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

/**
 * 禁用 RecyclerView 默认 requestChildRectangleOnScreen 的 LinearLayoutManager。
 * LinearLayoutManager that disables RecyclerView's default requestChildRectangleOnScreen behavior.
 *
 * 焦点跟随由容器层统一处理，避免布局刷新和键盘可见区域调整产生竞争滚动。
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
        // 焦点跟随由容器层协调，避免状态驱动重排时产生竞争滚动锚点。
        // Focus-follow is coordinated at the container layer to avoid competing scroll anchors during state-driven relayout.
        return false
    }
}

/**
 * 禁用默认焦点矩形滚动的 GridLayoutManager。
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
        // 焦点跟随由容器层协调，避免状态驱动重排时产生竞争滚动锚点。
        // Focus-follow is coordinated at the container layer to avoid competing scroll anchors during state-driven relayout.
        return false
    }
}
