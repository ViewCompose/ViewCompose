package com.viewcompose.renderer.layout

import android.view.Gravity
import com.viewcompose.ui.layout.HorizontalAlignment
import com.viewcompose.ui.layout.VerticalAlignment

/**
 * 从 Android gravity 解析线性布局交叉轴对齐。
 * Resolves linear-layout cross-axis alignment from Android gravity.
 */
internal object LinearCrossAxisAlignmentResolver {
    /**
     * 解析水平对齐，child gravity 优先于 container gravity。
     * Resolves horizontal alignment, with child gravity taking precedence over container gravity.
     */
    fun resolveHorizontal(
        containerGravity: Int,
        childGravity: Int?,
    ): HorizontalAlignment {
        val effectiveGravity = childGravity ?: containerGravity
        return when {
            effectiveGravity and Gravity.HORIZONTAL_GRAVITY_MASK == Gravity.CENTER_HORIZONTAL -> {
                HorizontalAlignment.Center
            }

            effectiveGravity and Gravity.RELATIVE_HORIZONTAL_GRAVITY_MASK == Gravity.END ||
                effectiveGravity and Gravity.HORIZONTAL_GRAVITY_MASK == Gravity.RIGHT -> {
                HorizontalAlignment.End
            }

            else -> HorizontalAlignment.Start
        }
    }

    /**
     * 解析垂直对齐，child gravity 优先于 container gravity。
     * Resolves vertical alignment, with child gravity taking precedence over container gravity.
     */
    fun resolveVertical(
        containerGravity: Int,
        childGravity: Int?,
    ): VerticalAlignment {
        val effectiveGravity = childGravity ?: containerGravity
        return when (effectiveGravity and Gravity.VERTICAL_GRAVITY_MASK) {
            Gravity.CENTER_VERTICAL -> VerticalAlignment.Center
            Gravity.BOTTOM -> VerticalAlignment.Bottom
            else -> VerticalAlignment.Top
        }
    }
}
