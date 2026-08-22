package com.viewcompose.renderer.view.tree

import android.graphics.Rect
import android.view.View
import android.view.ViewTreeObserver
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.viewcompose.renderer.R

/**
 * Reissues the focused editor's native rectangle request when its window viewport changes.
 *
 * Android dispatches the initial rectangle request before the IME finishes resizing older
 * windows. One coordinator per window observes viewport changes only while a ViewCompose editor is
 * focused, then lets ordinary ViewParent propagation choose and move the nearest scroll owner.
 */
internal class FocusedEditorVisibilityCoordinator private constructor(
    private val windowRoot: View,
) : ViewTreeObserver.OnGlobalLayoutListener {
    private var focusedEditor: ViewComposeEditText? = null
    private val previousViewport = Rect()
    private val currentViewport = Rect()
    private val requestedEditorBounds = Rect()
    private val requestFocusedEditorVisibility = Runnable {
        val editor = focusedEditor ?: return@Runnable
        if (editor.isAttachedToWindow && editor.isFocused) {
            editor.getDrawingRect(requestedEditorBounds)
            val imeBottom = ViewCompat.getRootWindowInsets(windowRoot)
                ?.getInsets(WindowInsetsCompat.Type.ime())
                ?.bottom
                ?: 0
            requestedEditorBounds.bottom += imeBottom
            editor.requestRectangleOnScreen(requestedEditorBounds, false)
        }
    }

    private fun activate(editor: ViewComposeEditText) {
        if (focusedEditor === editor) return
        val alreadyObserving = focusedEditor != null
        focusedEditor?.removeCallbacks(requestFocusedEditorVisibility)
        focusedEditor = editor
        windowRoot.getWindowVisibleDisplayFrame(previousViewport)
        if (!alreadyObserving) {
            windowRoot.viewTreeObserver.addOnGlobalLayoutListener(this)
        }
        val imeVisible = ViewCompat.getRootWindowInsets(windowRoot)
            ?.isVisible(WindowInsetsCompat.Type.ime())
            ?: false
        if (imeVisible) {
            editor.postOnAnimation(requestFocusedEditorVisibility)
        }
    }

    fun deactivate(editor: ViewComposeEditText) {
        if (focusedEditor !== editor) return
        editor.removeCallbacks(requestFocusedEditorVisibility)
        focusedEditor = null
        val observer = windowRoot.viewTreeObserver
        if (observer.isAlive) {
            observer.removeOnGlobalLayoutListener(this)
        }
    }

    override fun onGlobalLayout() {
        val editor = focusedEditor ?: return
        if (!editor.isAttachedToWindow || !editor.isFocused) {
            deactivate(editor)
            return
        }
        windowRoot.getWindowVisibleDisplayFrame(currentViewport)
        if (currentViewport == previousViewport) return
        previousViewport.set(currentViewport)
        editor.removeCallbacks(requestFocusedEditorVisibility)
        editor.postOnAnimation(requestFocusedEditorVisibility)
    }

    companion object {
        fun activate(editor: ViewComposeEditText): FocusedEditorVisibilityCoordinator {
            return coordinator(editor).also { coordinator ->
                coordinator.activate(editor)
            }
        }

        private fun coordinator(editor: ViewComposeEditText): FocusedEditorVisibilityCoordinator {
            val root = editor.rootView
            return (root.getTag(
                R.id.viewcompose_focused_editor_visibility_coordinator,
            ) as? FocusedEditorVisibilityCoordinator)
                ?: FocusedEditorVisibilityCoordinator(root).also { coordinator ->
                    root.setTag(
                        R.id.viewcompose_focused_editor_visibility_coordinator,
                        coordinator,
                    )
                }
        }
    }
}
