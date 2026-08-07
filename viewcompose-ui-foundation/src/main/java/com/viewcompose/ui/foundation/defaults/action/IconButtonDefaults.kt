package com.viewcompose.ui.foundation

import com.viewcompose.ui.node.UiStateLayerColors
import com.viewcompose.ui.unit.UiDp

import com.viewcompose.ui.shape.UiShape

/**
 * Resolves IconButton defaults by reusing Button hierarchy with a square touch target.
 */
object IconButtonDefaults {
    /** Delegates container color resolution to [ButtonDefaults]. */
    fun containerColor(
        variant: ButtonVariant = ButtonVariant.Text,
        enabled: Boolean = true,
    ): Int = ButtonDefaults.containerColor(variant, enabled)

    /** Delegates icon color resolution to [ButtonDefaults]. */
    fun contentColor(
        variant: ButtonVariant = ButtonVariant.Text,
        enabled: Boolean = true,
    ): Int {
        return if (variant == ButtonVariant.Text) {
            if (enabled) Theme.colors.onSurfaceVariant else colorWithAlpha(Theme.colors.onSurface, 0.38f)
        } else {
            ButtonDefaults.contentColor(variant, enabled)
        }
    }

    /** Delegates border color resolution to [ButtonDefaults]. */
    fun borderColor(
        variant: ButtonVariant = ButtonVariant.Text,
        enabled: Boolean = true,
    ): Int = ButtonDefaults.borderColor(variant, enabled)

    /** Delegates border width resolution to [ButtonDefaults]. */
    fun borderWidth(
        variant: ButtonVariant = ButtonVariant.Text,
    ): UiDp = ButtonDefaults.borderWidth(variant)

    /** Returns the full shape used by standard icon buttons. */
    fun shape(): UiShape = Theme.shapes.full

    /** Uses the corresponding Button height as square IconButton bounds. */
    fun size(
        size: ButtonSize = ButtonSize.Medium,
    ): UiDp = ButtonDefaults.height(size)

    /** Resolves uniform icon padding for [size]. */
    fun contentPadding(
        size: ButtonSize = ButtonSize.Medium,
    ): UiDp {
        return when (size) {
            ButtonSize.Compact -> 12.dp
            ButtonSize.Medium -> 12.dp
            ButtonSize.Large -> 16.dp
        }
    }

    /** Returns the Button pressed-state highlight. */
    fun pressedColor(): Int = ButtonDefaults.pressedColor()

    /**
     * Resolves transient interaction colors from the enabled icon role for [variant].
     *
     * @param variant visual hierarchy whose enabled icon role supplies the state-layer base
     * @return immutable pressed, focused, and hovered state-layer colors
     */
    fun stateLayerColors(
        variant: ButtonVariant = ButtonVariant.Text,
    ): UiStateLayerColors {
        val baseColor = contentColor(variant = variant, enabled = true)
        return stateLayerColorsFor(baseColor)
    }
}
