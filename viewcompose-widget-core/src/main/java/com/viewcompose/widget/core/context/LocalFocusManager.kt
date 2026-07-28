package com.viewcompose.widget.core

import android.view.View
import android.view.ViewGroup
import com.viewcompose.ui.focus.FocusDirection
import com.viewcompose.ui.focus.FocusManager

/**
 * 当前正在 composition 的 render session 的焦点所有者。
 * Focus owner for the currently composing render session.
 */
object LocalFocusManager {
    val current: FocusManager
        get() = FocusManagerContext.requireCurrent()
}

/**
 * 焦点管理器的线程局部上下文。
 * Thread-local context for the focus manager.
 */
internal object FocusManagerContext {
    private val current = ThreadLocal<FocusManager?>()

    fun withFocusManager(
        focusManager: FocusManager,
        block: () -> Unit,
    ) {
        val previous = current.get()
        current.set(focusManager)
        try {
            block()
        } finally {
            current.set(previous)
        }
    }

    fun requireCurrent(): FocusManager {
        return checkNotNull(current.get()) {
            "LocalFocusManager.current is only available while composing a mounted RenderSession."
        }
    }
}

/**
 * 基于 Android ViewGroup 的 session 焦点管理器。
 * Session focus manager backed by an Android ViewGroup.
 */
internal class SessionFocusManager(
    private val root: ViewGroup,
) : FocusManager {
    override fun clearFocus(force: Boolean) {
        val focused = root.findFocus()
        focused?.clearFocus()
        if (force && root.hasFocus()) {
            root.clearFocus()
        }
    }

    override fun moveFocus(direction: FocusDirection): Boolean {
        val focused = root.findFocus()
        if (direction == FocusDirection.Exit) {
            val parent = focused?.parent as? View
            return parent?.requestFocus(View.FOCUS_BACKWARD) == true
        }
        val androidDirection = direction.toAndroidDirection()
        val target = focused?.focusSearch(androidDirection)
            ?: root.focusSearch(androidDirection)
            ?: return false
        return target.requestFocus(androidDirection)
    }
}

private fun FocusDirection.toAndroidDirection(): Int {
    return when (this) {
        FocusDirection.Next,
        FocusDirection.Enter,
        -> View.FOCUS_FORWARD
        FocusDirection.Previous -> View.FOCUS_BACKWARD
        FocusDirection.Left -> View.FOCUS_LEFT
        FocusDirection.Right -> View.FOCUS_RIGHT
        FocusDirection.Up -> View.FOCUS_UP
        FocusDirection.Down -> View.FOCUS_DOWN
        FocusDirection.Exit -> View.FOCUS_BACKWARD
    }
}
