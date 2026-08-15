package com.viewcompose.renderer.view.shape

import android.graphics.drawable.Drawable
import com.viewcompose.ui.shape.UiShape
import com.viewcompose.ui.unit.UiDensity

/** Creates Android drawables from renderer-neutral ViewCompose shape geometry. */
object AndroidUiShapeDrawables {
    /**
     * Creates a bounds-aware solid drawable for [shape].
     *
     * Absolute corners use [density], relative corners resolve from the drawable bounds, and start
     * and end corners use [layoutDirection]. The returned drawable is newly allocated and owned by
     * the caller; changing its bounds does not mutate [shape] or [density].
     *
     * @param shape immutable logical corner geometry
     * @param color solid ARGB fill color
     * @param layoutDirection Android `View.LAYOUT_DIRECTION_LTR` or `View.LAYOUT_DIRECTION_RTL`
     * @param density immutable logical-to-physical density used for absolute corners
     * @return a new mutable Android drawable owned by the caller
     */
    fun solid(
        shape: UiShape,
        color: Int,
        layoutDirection: Int,
        density: UiDensity,
    ): Drawable = UiShapeDrawable(shape, layoutDirection, density).apply {
        setFillColor(color)
    }
}
