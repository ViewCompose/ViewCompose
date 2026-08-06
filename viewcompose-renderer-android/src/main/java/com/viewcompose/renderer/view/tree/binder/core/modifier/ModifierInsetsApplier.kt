package com.viewcompose.renderer.view.tree

import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.viewcompose.renderer.R
import com.viewcompose.ui.modifier.ImeInsetsPaddingModifierElement
import com.viewcompose.ui.modifier.SystemBarsInsetsPaddingModifierElement
import com.viewcompose.renderer.view.PaddingPx

/**
 * Applies system-bar and IME inset padding and restores base padding when the modifier is removed.
 * Applies systemBars/IME inset padding and restores base padding when modifiers are removed.
 */
internal object ModifierInsetsApplier {
    /**
     * Applies host padding directly when no inset-padding modifier is present.
     * Applies host padding directly when no inset-padding modifier is present.
     */
    fun applyHostPaddingWhenNoInsets(
        view: View,
        hasWindowInsetsPadding: Boolean,
        hostPadding: PaddingPx?,
    ) {
        if (hasWindowInsetsPadding) return
        if (hostPadding == null) {
            view.setPadding(0, 0, 0, 0)
            return
        }
        view.setPadding(
            hostPadding.left,
            hostPadding.top,
            hostPadding.right,
            hostPadding.bottom,
        )
    }

    /**
     * Installs a WindowInsets listener and adds system-bar or keyboard insets to base padding.
     * Installs a WindowInsets listener and adds system-bar/IME insets to base padding.
     */
    fun applyWindowInsetsPadding(
        view: View,
        systemBarsModifier: SystemBarsInsetsPaddingModifierElement?,
        imeModifier: ImeInsetsPaddingModifierElement?,
        basePadding: PaddingPx?,
    ) {
        if (systemBarsModifier == null && imeModifier == null) {
            // Restore recorded base padding and remove the listener when the modifier disappears.
            // When modifiers are removed, restore recorded base padding and uninstall the listener.
            val state = view.getTag(R.id.viewcompose_system_bars_padding_state) as? WindowInsetsPaddingState
            if (state != null) {
                view.setPadding(state.baseLeft, state.baseTop, state.baseRight, state.baseBottom)
                view.setTag(R.id.viewcompose_system_bars_padding_state, null)
            }
            ViewCompat.setOnApplyWindowInsetsListener(view, null)
            return
        }

        val state = (view.getTag(R.id.viewcompose_system_bars_padding_state) as? WindowInsetsPaddingState)
            ?: WindowInsetsPaddingState().also {
                view.setTag(R.id.viewcompose_system_bars_padding_state, it)
            }
        if (basePadding != null) {
            state.baseLeft = basePadding.left
            state.baseTop = basePadding.top
            state.baseRight = basePadding.right
            state.baseBottom = basePadding.bottom
        } else {
            state.baseLeft = view.paddingLeft - state.appliedLeft
            state.baseTop = view.paddingTop - state.appliedTop
            state.baseRight = view.paddingRight - state.appliedRight
            state.baseBottom = view.paddingBottom - state.appliedBottom
        }

        ViewCompat.setOnApplyWindowInsetsListener(view) { target, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
            state.appliedLeft =
                (if (systemBarsModifier?.left == true) systemBars.left else 0) +
                (if (imeModifier?.left == true) ime.left else 0)
            state.appliedTop =
                (if (systemBarsModifier?.top == true) systemBars.top else 0) +
                (if (imeModifier?.top == true) ime.top else 0)
            state.appliedRight =
                (if (systemBarsModifier?.right == true) systemBars.right else 0) +
                (if (imeModifier?.right == true) ime.right else 0)
            state.appliedBottom =
                (if (systemBarsModifier?.bottom == true) systemBars.bottom else 0) +
                (if (imeModifier?.bottom == true) ime.bottom else 0)
            target.setPadding(
                state.baseLeft + state.appliedLeft,
                state.baseTop + state.appliedTop,
                state.baseRight + state.appliedRight,
                state.baseBottom + state.appliedBottom,
            )
            insets
        }
        view.requestApplyInsetsWhenAttached()
    }

    private fun View.requestApplyInsetsWhenAttached() {
        if (isAttachedToWindow) {
            requestApplyInsets()
            return
        }
        addOnAttachStateChangeListener(
            object : View.OnAttachStateChangeListener {
                override fun onViewAttachedToWindow(view: View) {
                    view.removeOnAttachStateChangeListener(this)
                    view.requestApplyInsets()
                }

                override fun onViewDetachedFromWindow(view: View) = Unit
            },
        )
    }

    private data class WindowInsetsPaddingState(
        var baseLeft: Int = 0,
        var baseTop: Int = 0,
        var baseRight: Int = 0,
        var baseBottom: Int = 0,
        var appliedLeft: Int = 0,
        var appliedTop: Int = 0,
        var appliedRight: Int = 0,
        var appliedBottom: Int = 0,
    )
}
