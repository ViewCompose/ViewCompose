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
        val base = hostPadding ?: PaddingPx.Zero
        val content = view.lazyContentPadding()
        view.applyPadding(
            left = base.left + content.left,
            top = base.top + content.top,
            right = base.right + content.right,
            bottom = base.bottom + content.bottom,
        )
    }

    /** Updates the scroll-content contribution without overwriting modifier or inset padding. */
    fun applyLazyContentPadding(
        view: View,
        contentPadding: PaddingPx,
    ) {
        val previousContent = view.lazyContentPadding()
        view.setTag(R.id.viewcompose_lazy_content_padding, contentPadding)
        val state = view.getTag(
            R.id.viewcompose_system_bars_padding_state,
        ) as? WindowInsetsPaddingState
        if (state != null) {
            state.applyTo(view)
            return
        }
        view.applyPadding(
            left = view.paddingLeft - previousContent.left + contentPadding.left,
            top = view.paddingTop - previousContent.top + contentPadding.top,
            right = view.paddingRight - previousContent.right + contentPadding.right,
            bottom = view.paddingBottom - previousContent.bottom + contentPadding.bottom,
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
                state.appliedLeft = 0
                state.appliedTop = 0
                state.appliedRight = 0
                state.appliedBottom = 0
                state.applyTo(view)
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
            val content = view.lazyContentPadding()
            state.baseLeft = view.paddingLeft - state.appliedLeft - content.left
            state.baseTop = view.paddingTop - state.appliedTop - content.top
            state.baseRight = view.paddingRight - state.appliedRight - content.right
            state.baseBottom = view.paddingBottom - state.appliedBottom - content.bottom
        }

        // A full node rebind may run after insets were already delivered. Reapply the retained
        // snapshot immediately so a child binder cannot expose an unpadded frame until the next
        // platform insets dispatch.
        state.applyTo(view)

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
            state.applyTo(target)
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
    ) {
        fun applyTo(view: View) {
            val content = view.lazyContentPadding()
            view.applyPadding(
                left = baseLeft + content.left + appliedLeft,
                top = baseTop + content.top + appliedTop,
                right = baseRight + content.right + appliedRight,
                bottom = baseBottom + content.bottom + appliedBottom,
            )
        }
    }

    private fun View.lazyContentPadding(): PaddingPx {
        return getTag(R.id.viewcompose_lazy_content_padding) as? PaddingPx ?: PaddingPx.Zero
    }

    private fun View.applyPadding(
        left: Int,
        top: Int,
        right: Int,
        bottom: Int,
    ) {
        if (
            paddingLeft != left ||
            paddingTop != top ||
            paddingRight != right ||
            paddingBottom != bottom
        ) {
            setPadding(left, top, right, bottom)
        }
    }
}
