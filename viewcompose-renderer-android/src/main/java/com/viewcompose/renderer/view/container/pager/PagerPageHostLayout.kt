package com.viewcompose.renderer.view.container

import android.content.Context
import android.graphics.Rect
import android.graphics.RectF
import android.view.View
import com.viewcompose.renderer.decoration.ViewDecorationHostLayout

/**
 * Separates descendant visibility inside a page from the pager's discrete page-selection axis.
 *
 * A page-local scroll owner handles the descendant rectangle before propagation reaches this
 * boundary. The outer pager viewport therefore receives only complete-page selection commands and
 * cannot interpret an editor's within-page coordinates as partial pager movement.
 */
internal class PagerPageHostLayout(
    context: Context,
) : ViewDecorationHostLayout(context) {
    /** Propagates a descendant request through page-local parents without entering the pager. */
    fun requestDescendantRectangleOnScreen(
        descendant: View,
        rectangle: Rect,
        immediate: Boolean,
    ): Boolean {
        var child = descendant
        var parent = child.parent
        val position = RectF(rectangle)
        var scrolled = false
        while (parent != null && parent !== this) {
            rectangle.set(
                position.left.toInt(),
                position.top.toInt(),
                position.right.toInt(),
                position.bottom.toInt(),
            )
            scrolled = parent.requestChildRectangleOnScreen(child, rectangle, immediate) || scrolled
            val parentView = parent as? View ?: break
            position.offset(
                child.left.toFloat() - child.scrollX,
                child.top.toFloat() - child.scrollY,
            )
            child = parentView
            parent = child.parent
        }
        return scrolled
    }
}
