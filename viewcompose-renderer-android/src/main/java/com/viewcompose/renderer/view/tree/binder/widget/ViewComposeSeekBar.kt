package com.viewcompose.renderer.view.tree

import android.content.Context
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.view.accessibility.AccessibilityNodeInfo
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
    private var onInteractionStarted: (() -> Unit)? = null
    private var onInteractionFinished: (() -> Unit)? = null

    fun bindInteractionCallbacks(
        onStarted: (() -> Unit)?,
        onFinished: (() -> Unit)?,
    ) {
        onInteractionStarted = onStarted
        onInteractionFinished = onFinished
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (!isEnabled || !keyCode.isProgressKey()) return super.onKeyDown(keyCode, event)
        onInteractionStarted?.invoke()
        return try {
            super.onKeyDown(keyCode, event)
        } finally {
            onInteractionFinished?.invoke()
        }
    }

    override fun performAccessibilityAction(action: Int, arguments: Bundle?): Boolean {
        if (!isEnabled || !action.isProgressAccessibilityAction()) {
            return super.performAccessibilityAction(action, arguments)
        }
        onInteractionStarted?.invoke()
        return try {
            super.performAccessibilityAction(action, arguments)
        } finally {
            onInteractionFinished?.invoke()
        }
    }

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

    private fun Int.isProgressKey(): Boolean {
        return this == KeyEvent.KEYCODE_DPAD_LEFT ||
            this == KeyEvent.KEYCODE_DPAD_RIGHT ||
            this == KeyEvent.KEYCODE_MINUS ||
            this == KeyEvent.KEYCODE_PLUS ||
            this == KeyEvent.KEYCODE_EQUALS
    }

    private fun Int.isProgressAccessibilityAction(): Boolean {
        return this == AccessibilityNodeInfo.ACTION_SCROLL_FORWARD ||
            this == AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD ||
            this == AccessibilityNodeInfo.AccessibilityAction.ACTION_SET_PROGRESS.id
    }
}
