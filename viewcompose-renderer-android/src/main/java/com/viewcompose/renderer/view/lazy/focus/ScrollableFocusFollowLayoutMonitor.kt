package com.viewcompose.renderer.view.lazy.focus

import android.graphics.Rect
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import com.viewcompose.renderer.R

/**
 * Keyboard focus-follow listener manager for ScrollView and NestedScrollView containers.
 * Listener manager for keyboard focus-follow behavior in ScrollView-like containers.
 *
 * Unlike the RecyclerView variant, it performs only vertical scrollBy and owns no holder or adapter state.
 * Unlike the RecyclerView variant, it only issues vertical scrollBy and does not touch holder or adapter state.
 */
internal object ScrollableFocusFollowLayoutMonitor {
    private const val TAG = "UIFocusFollow"

    fun apply(
        scrollView: ViewGroup,
        enabled: Boolean,
    ) {
        val existingLayoutListener = scrollView.getTag(R.id.viewcompose_focus_follow_layout_listener)
            as? View.OnLayoutChangeListener
        val existingGlobalFocusListener = scrollView.getTag(R.id.viewcompose_focus_follow_global_focus_listener)
            as? ViewTreeObserver.OnGlobalFocusChangeListener
        val existingGlobalLayoutListener = scrollView.getTag(R.id.viewcompose_focus_follow_global_layout_listener)
            as? ViewTreeObserver.OnGlobalLayoutListener
        if (!enabled) {
            // Remove every tag-backed listener when the policy is disabled.
            // When disabling the policy, clean up every listener stored on tags.
            if (existingLayoutListener != null) {
                scrollView.removeOnLayoutChangeListener(existingLayoutListener)
                scrollView.setTag(R.id.viewcompose_focus_follow_layout_listener, null)
            }
            if (existingGlobalFocusListener != null) {
                val observer = scrollView.viewTreeObserver
                if (observer.isAlive) {
                    observer.removeOnGlobalFocusChangeListener(existingGlobalFocusListener)
                }
                scrollView.setTag(R.id.viewcompose_focus_follow_global_focus_listener, null)
            }
            if (existingGlobalLayoutListener != null) {
                val observer = scrollView.viewTreeObserver
                if (observer.isAlive) {
                    observer.removeOnGlobalLayoutListener(existingGlobalLayoutListener)
                }
                scrollView.setTag(R.id.viewcompose_focus_follow_global_layout_listener, null)
            }
            scrollView.setTag(R.id.viewcompose_focus_follow_enabled, false)
            return
        }
        if (existingLayoutListener == null) {
            // Plain scroll containers have no adapter events, so layout changes trigger correction.
            // Plain scroll containers have no adapter events, so layout changes trigger recalculation.
            val listener = View.OnLayoutChangeListener { view, _, _, _, _, _, _, _, _ ->
                val target = view as? ViewGroup ?: return@OnLayoutChangeListener
                ensureFocusedChildVisible(target)
            }
            scrollView.addOnLayoutChangeListener(listener)
            scrollView.setTag(R.id.viewcompose_focus_follow_layout_listener, listener)
        }
        if (existingGlobalFocusListener == null) {
            // Correct immediately after focus changes so the new input is not hidden by the keyboard.
            // Correct immediately after focus changes so the new editor is not hidden by the keyboard.
            val listener = ViewTreeObserver.OnGlobalFocusChangeListener { _, _ ->
                ensureFocusedChildVisible(scrollView)
            }
            scrollView.viewTreeObserver.addOnGlobalFocusChangeListener(listener)
            scrollView.setTag(R.id.viewcompose_focus_follow_global_focus_listener, listener)
        }
        if (existingGlobalLayoutListener == null) {
            // IME visibility usually appears as a global-layout change and requires viewport resolution.
            // IME show/hide usually arrives as a global layout change, requiring viewport resolution again.
            val listener = ViewTreeObserver.OnGlobalLayoutListener {
                ensureFocusedChildVisible(scrollView)
            }
            scrollView.viewTreeObserver.addOnGlobalLayoutListener(listener)
            scrollView.setTag(R.id.viewcompose_focus_follow_global_layout_listener, listener)
        }
        scrollView.setTag(R.id.viewcompose_focus_follow_enabled, true)
        ensureFocusedChildVisible(scrollView)
    }

    private fun ensureFocusedChildVisible(scrollView: ViewGroup) {
        val focused = scrollView.findFocus()
            ?.takeIf { it.onCheckIsTextEditor() }
            ?: return
        if (focused === scrollView) {
            return
        }
        val focusedRect = Rect().also { rect ->
            focused.getDrawingRect(rect)
            scrollView.offsetDescendantRectToMyCoords(focused, rect)
        }
        val viewport = FocusFollowViewportResolver.resolve(
            view = scrollView,
            fallback = Rect(
                scrollView.paddingLeft,
                scrollView.paddingTop,
                scrollView.width - scrollView.paddingRight,
                scrollView.height - scrollView.paddingBottom,
            ),
        )
        val bottomOverflow = focusedRect.bottom - viewport.bottom
        val topOverflow = focusedRect.top - viewport.top
        val dy = when {
            bottomOverflow > 0 -> bottomOverflow
            topOverflow < 0 -> topOverflow
            else -> 0
        }
        if (dy == 0) {
            return
        }
        if (dy > 0 && !scrollView.canScrollVertically(1)) {
            return
        }
        if (dy < 0 && !scrollView.canScrollVertically(-1)) {
            return
        }
        debugLog {
            "scroll scrollable-column dy=$dy focusedRect=${focusedRect.toShortString()} viewport=${viewport.toShortString()}"
        }
        scrollView.scrollBy(0, dy)
    }

    private inline fun debugLog(message: () -> String) {
        if (!Log.isLoggable(TAG, Log.DEBUG)) {
            return
        }
        Log.d(TAG, message())
    }
}
