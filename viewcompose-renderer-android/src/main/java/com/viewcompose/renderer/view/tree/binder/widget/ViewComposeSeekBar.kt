package com.viewcompose.renderer.view.tree

import android.content.Context
import android.view.View
import android.widget.SeekBar
import kotlin.math.max

/**
 * Preserves an explicit minimum interactive height that the platform SeekBar measurement ignores.
 *
 * Exact parent or application constraints remain authoritative. Under an at-most constraint, the
 * native track and thumb stay centered while the View grows to its declared minimum target.
 */
internal class ViewComposeSeekBar(
    context: Context,
) : androidx.appcompat.widget.AppCompatSeekBar(context) {
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        val desiredHeight = max(measuredHeight, suggestedMinimumHeight)
        val heightAndState = View.resolveSizeAndState(
            desiredHeight,
            heightMeasureSpec,
            measuredState shl View.MEASURED_HEIGHT_STATE_SHIFT,
        )
        setMeasuredDimension(measuredWidthAndState, heightAndState)
    }
}
