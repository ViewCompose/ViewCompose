package com.viewcompose.renderer.view.lazy.focus

import android.graphics.Rect
import android.util.Log
import android.view.View
import android.view.ViewTreeObserver
import androidx.recyclerview.widget.RecyclerView
import com.viewcompose.renderer.R

/**
 * Keyboard focus-follow listener manager for RecyclerView lazy containers.
 * Listener manager for keyboard focus-follow behavior in RecyclerView-backed lazy containers.
 *
 * Checks whether the focused text input remains in the visible viewport after layout, focus, and global-layout changes.
 * It checks after layout, global focus, and global layout changes whether the focused text editor remains inside the visible viewport.
 */
internal object LazyFocusFollowLayoutMonitor {
    private const val TAG = "UIFocusFollow"

    fun isEnabled(recyclerView: RecyclerView): Boolean {
        return recyclerView.getTag(R.id.viewcompose_focus_follow_enabled) as? Boolean == true
    }

    fun apply(
        recyclerView: RecyclerView,
        enabled: Boolean,
    ) {
        val existingLayoutListener = recyclerView.getTag(R.id.viewcompose_focus_follow_layout_listener)
            as? View.OnLayoutChangeListener
        val existingGlobalFocusListener = recyclerView.getTag(R.id.viewcompose_focus_follow_global_focus_listener)
            as? ViewTreeObserver.OnGlobalFocusChangeListener
        val existingGlobalLayoutListener = recyclerView.getTag(R.id.viewcompose_focus_follow_global_layout_listener)
            as? ViewTreeObserver.OnGlobalLayoutListener
        if (!enabled) {
            // Unbind every tag-backed listener when disabled so invalid ViewTreeObservers retain nothing.
            // On disable, remove each listener stored in tags to avoid stale references after ViewTreeObserver changes.
            if (existingLayoutListener != null) {
                recyclerView.removeOnLayoutChangeListener(existingLayoutListener)
                recyclerView.setTag(R.id.viewcompose_focus_follow_layout_listener, null)
            }
            if (existingGlobalFocusListener != null) {
                val viewTreeObserver = recyclerView.viewTreeObserver
                if (viewTreeObserver.isAlive) {
                    viewTreeObserver.removeOnGlobalFocusChangeListener(existingGlobalFocusListener)
                }
                recyclerView.setTag(R.id.viewcompose_focus_follow_global_focus_listener, null)
            }
            if (existingGlobalLayoutListener != null) {
                val viewTreeObserver = recyclerView.viewTreeObserver
                if (viewTreeObserver.isAlive) {
                    viewTreeObserver.removeOnGlobalLayoutListener(existingGlobalLayoutListener)
                }
                recyclerView.setTag(R.id.viewcompose_focus_follow_global_layout_listener, null)
            }
            debugLog {
                "detach listeners rv=${recyclerView.hashCode()}"
            }
            recyclerView.setTag(R.id.viewcompose_focus_follow_enabled, false)
            return
        }
        if (existingLayoutListener == null) {
            // Layout changes cover focus occlusion caused by same-frame item reordering.
            // layoutChange handles focus occlusion caused by item relayout within the same frame.
            val listener = View.OnLayoutChangeListener { view, _, _, _, _, _, _, _, _ ->
                val target = view as? RecyclerView ?: return@OnLayoutChangeListener
                ensureFocusedChildVisible(target, trigger = "layoutChange")
            }
            recyclerView.addOnLayoutChangeListener(listener)
            recyclerView.setTag(R.id.viewcompose_focus_follow_layout_listener, listener)
        }
        if (existingGlobalFocusListener == null) {
            // Global focus covers movement between nested children when RecyclerView itself did not relayout.
            // globalFocus covers focus moving between nested children without RecyclerView relayout.
            val globalFocusListener = ViewTreeObserver.OnGlobalFocusChangeListener { _, _ ->
                ensureFocusedChildVisible(recyclerView, trigger = "globalFocus")
            }
            recyclerView.viewTreeObserver.addOnGlobalFocusChangeListener(globalFocusListener)
            recyclerView.setTag(R.id.viewcompose_focus_follow_global_focus_listener, globalFocusListener)
        }
        if (existingGlobalLayoutListener == null) {
            // Global layout captures viewport shrinkage after IME or window-inset changes.
            // globalLayout captures visible viewport changes after IME/window inset updates.
            val globalLayoutListener = ViewTreeObserver.OnGlobalLayoutListener {
                ensureFocusedChildVisible(recyclerView, trigger = "globalLayout")
            }
            recyclerView.viewTreeObserver.addOnGlobalLayoutListener(globalLayoutListener)
            recyclerView.setTag(R.id.viewcompose_focus_follow_global_layout_listener, globalLayoutListener)
        }
        debugLog {
            "attach listeners rv=${recyclerView.hashCode()} " +
                "layout=${existingLayoutListener != null} " +
                "focus=${existingGlobalFocusListener != null} " +
                "globalLayout=${existingGlobalLayoutListener != null}"
        }
        recyclerView.setTag(R.id.viewcompose_focus_follow_enabled, true)
        ensureFocusedChildVisible(recyclerView, trigger = "apply")
    }

    private fun ensureFocusedChildVisible(
        recyclerView: RecyclerView,
        trigger: String,
    ) {
        val focused = recyclerView.findFocus()
            ?.takeIf { it.onCheckIsTextEditor() }
            ?: return
        if (focused === recyclerView) {
            return
        }
        val layoutManager = recyclerView.layoutManager ?: return
        val focusedRect = Rect().also { rect ->
            focused.getDrawingRect(rect)
            recyclerView.offsetDescendantRectToMyCoords(focused, rect)
        }
        val viewport = resolveVisibleViewport(
            recyclerView = recyclerView,
            fallback = Rect(
                recyclerView.paddingLeft,
                recyclerView.paddingTop,
                recyclerView.width - recyclerView.paddingRight,
                recyclerView.height - recyclerView.paddingBottom,
            ),
        )
        if (layoutManager.canScrollVertically()) {
            val bottomOverflow = focusedRect.bottom - viewport.bottom
            val topOverflow = focusedRect.top - viewport.top
            val dy = when {
                bottomOverflow > 0 -> bottomOverflow
                topOverflow < 0 -> topOverflow
                else -> 0
            }
            if (dy > 0 && !recyclerView.canScrollVertically(1)) {
                // Do not force scrolling at an edge; it would produce ineffective scrollBy calls and log noise.
                // Do not force scroll at list boundaries, avoiding no-op scrollBy calls and log noise.
                debugLog {
                    "skip vertical scroll (end reached) trigger=$trigger rv=${recyclerView.hashCode()} " +
                        "holderPos=${recyclerView.findContainingViewHolder(focused)?.bindingAdapterPosition} " +
                        "focusedRect=${focusedRect.toShortString()} viewport=${viewport.toShortString()} dy=$dy"
                }
                return
            }
            if (dy < 0 && !recyclerView.canScrollVertically(-1)) {
                debugLog {
                    "skip vertical scroll (start reached) trigger=$trigger rv=${recyclerView.hashCode()} " +
                        "holderPos=${recyclerView.findContainingViewHolder(focused)?.bindingAdapterPosition} " +
                        "focusedRect=${focusedRect.toShortString()} viewport=${viewport.toShortString()} dy=$dy"
                }
                return
            }
            if (dy != 0) {
                debugLog {
                    "scroll vertical trigger=$trigger rv=${recyclerView.hashCode()} " +
                        "holderPos=${recyclerView.findContainingViewHolder(focused)?.bindingAdapterPosition} " +
                        "focusedRect=${focusedRect.toShortString()} viewport=${viewport.toShortString()} dy=$dy " +
                        "bottomOverflow=$bottomOverflow topOverflow=$topOverflow"
                }
                recyclerView.scrollBy(0, dy)
            } else {
                debugLog {
                    "no vertical scroll trigger=$trigger rv=${recyclerView.hashCode()} " +
                        "holderPos=${recyclerView.findContainingViewHolder(focused)?.bindingAdapterPosition} " +
                        "focusedRect=${focusedRect.toShortString()} viewport=${viewport.toShortString()}"
                }
            }
            return
        }
        if (layoutManager.canScrollHorizontally()) {
            val rightOverflow = focusedRect.right - viewport.right
            val leftOverflow = focusedRect.left - viewport.left
            val dx = when {
                rightOverflow > 0 -> rightOverflow
                leftOverflow < 0 -> leftOverflow
                else -> 0
            }
            if (dx > 0 && !recyclerView.canScrollHorizontally(1)) {
                debugLog {
                    "skip horizontal scroll (end reached) trigger=$trigger rv=${recyclerView.hashCode()} " +
                        "focusedRect=${focusedRect.toShortString()} viewport=${viewport.toShortString()} dx=$dx"
                }
                return
            }
            if (dx < 0 && !recyclerView.canScrollHorizontally(-1)) {
                debugLog {
                    "skip horizontal scroll (start reached) trigger=$trigger rv=${recyclerView.hashCode()} " +
                        "focusedRect=${focusedRect.toShortString()} viewport=${viewport.toShortString()} dx=$dx"
                }
                return
            }
            if (dx != 0) {
                debugLog {
                    "scroll horizontal trigger=$trigger rv=${recyclerView.hashCode()} " +
                        "focusedRect=${focusedRect.toShortString()} viewport=${viewport.toShortString()} dx=$dx"
                }
                recyclerView.scrollBy(dx, 0)
            } else {
                debugLog {
                    "no horizontal scroll trigger=$trigger rv=${recyclerView.hashCode()} " +
                        "focusedRect=${focusedRect.toShortString()} viewport=${viewport.toShortString()}"
                }
            }
        }
    }

    private fun resolveVisibleViewport(
        recyclerView: RecyclerView,
        fallback: Rect,
    ): Rect {
        return FocusFollowViewportResolver.resolve(
            view = recyclerView,
            fallback = fallback,
        )
    }

    private inline fun debugLog(message: () -> String) {
        if (!Log.isLoggable(TAG, Log.DEBUG)) {
            return
        }
        Log.d(TAG, message())
    }
}
