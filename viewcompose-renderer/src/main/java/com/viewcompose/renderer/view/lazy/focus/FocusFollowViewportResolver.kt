package com.viewcompose.renderer.view.lazy.focus

import android.graphics.Rect
import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import kotlin.math.max
import kotlin.math.min

/**
 * 解析焦点跟随可用视口的工具。
 * Resolver for the usable viewport used by focus-follow scrolling.
 *
 * 它合并 View 可见区域、窗口可见帧和 IME inset，避免把输入框滚到键盘遮挡区域。
 * It merges View visible bounds, the window visible frame, and IME insets so text fields are not scrolled behind the keyboard.
 */
internal object FocusFollowViewportResolver {
    /**
     * 返回 view 坐标系内的可滚动可见区域，解析失败时回退到调用方提供的区域。
     * Returns the visible scroll viewport in view coordinates, falling back to caller-provided bounds when resolution fails.
     */
    fun resolve(
        view: View,
        fallback: Rect,
    ): Rect {
        val location = IntArray(2)
        view.getLocationOnScreen(location)
        var viewport = Rect(fallback)

        val globalVisibleRect = Rect()
        if (view.getGlobalVisibleRect(globalVisibleRect)) {
            val globalViewport = Rect(
                globalVisibleRect.left - location[0],
                globalVisibleRect.top - location[1],
                globalVisibleRect.right - location[0],
                globalVisibleRect.bottom - location[1],
            )
            viewport = intersectViewport(
                current = viewport,
                candidate = globalViewport,
                fallback = fallback,
            )
        }

        val windowVisibleFrame = Rect()
        view.getWindowVisibleDisplayFrame(windowVisibleFrame)
        if (!windowVisibleFrame.isEmpty) {
            val windowViewport = Rect(
                windowVisibleFrame.left - location[0],
                windowVisibleFrame.top - location[1],
                windowVisibleFrame.right - location[0],
                windowVisibleFrame.bottom - location[1],
            )
            viewport = intersectViewport(
                current = viewport,
                candidate = windowViewport,
                fallback = fallback,
            )
        }

        val rootInsets = ViewCompat.getRootWindowInsets(view)
        if (rootInsets?.isVisible(WindowInsetsCompat.Type.ime()) == true) {
            val imeBottomInset = rootInsets.getInsets(WindowInsetsCompat.Type.ime()).bottom
            if (imeBottomInset > 0) {
                val rootHeight = view.rootView?.height ?: 0
                if (rootHeight > 0) {
                    // IME inset 属于窗口坐标，需转换到当前 view 坐标后再收窄 viewport。
                    // IME inset is in window coordinates, so convert it to this view before narrowing the viewport.
                    val imeTopInWindow = rootHeight - imeBottomInset
                    val imeTopInView = imeTopInWindow - location[1]
                    val boundedImeTop = imeTopInView.coerceIn(fallback.top, fallback.bottom)
                    if (boundedImeTop < viewport.bottom) {
                        viewport.bottom = boundedImeTop
                    }
                }
            }
        }

        if (viewport.right <= viewport.left || viewport.bottom <= viewport.top) {
            return fallback
        }
        return viewport
    }

    private fun intersectViewport(
        current: Rect,
        candidate: Rect,
        fallback: Rect,
    ): Rect {
        // candidate 先被限制在 fallback 内，再与当前 viewport 求交，避免异常窗口值扩大可见区域。
        // Bound candidates within fallback before intersecting so abnormal window values cannot enlarge the visible area.
        val bounded = Rect(
            candidate.left.coerceIn(fallback.left, fallback.right),
            candidate.top.coerceIn(fallback.top, fallback.bottom),
            candidate.right.coerceIn(fallback.left, fallback.right),
            candidate.bottom.coerceIn(fallback.top, fallback.bottom),
        )
        val intersected = Rect(
            max(current.left, bounded.left),
            max(current.top, bounded.top),
            min(current.right, bounded.right),
            min(current.bottom, bounded.bottom),
        )
        if (intersected.right <= intersected.left || intersected.bottom <= intersected.top) {
            return current
        }
        return intersected
    }
}
