package com.viewcompose.renderer.view.lazy.focus

import android.graphics.Rect
import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import kotlin.math.max
import kotlin.math.min

/**
 * Resolves the usable viewport for keyboard focus following.
 * Resolver for the usable viewport used by focus-follow scrolling.
 *
 * Intersects View visibility, window visibility, and IME insets so inputs are not scrolled behind the keyboard.
 * It merges View visible bounds, the window visible frame, and IME insets so text fields are not scrolled behind the keyboard.
 */
internal object FocusFollowViewportResolver {
    /**
     * Returns the scrollable visible region in View coordinates, falling back to the caller's bounds when resolution fails.
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
                    // IME insets use window coordinates and must be converted before narrowing the View-local viewport.
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
        // Constrain the candidate to fallback bounds before intersection so malformed window values cannot enlarge visibility.
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
