package com.viewcompose.ui.foundation

import com.viewcompose.ui.unit.UiDp

import com.viewcompose.ui.shape.UiShape

/**
 * Resolves IconButton defaults by reusing Button hierarchy with a square touch target.
 */
object IconButtonDefaults {
    /** Delegates container color resolution to [ButtonDefaults]. */
    fun containerColor(
        variant: ButtonVariant = ButtonVariant.Primary,
        enabled: Boolean = true,
    ): Int = ButtonDefaults.containerColor(variant, enabled)

    /** Delegates icon color resolution to [ButtonDefaults]. */
    fun contentColor(
        variant: ButtonVariant = ButtonVariant.Primary,
        enabled: Boolean = true,
    ): Int = ButtonDefaults.contentColor(variant, enabled)

    /** Delegates border color resolution to [ButtonDefaults]. */
    fun borderColor(
        variant: ButtonVariant = ButtonVariant.Primary,
        enabled: Boolean = true,
    ): Int = ButtonDefaults.borderColor(variant, enabled)

    /** Delegates border width resolution to [ButtonDefaults]. */
    fun borderWidth(
        variant: ButtonVariant = ButtonVariant.Primary,
    ): UiDp = ButtonDefaults.borderWidth(variant)

    /** Returns the Button default shape. */
    fun shape(): UiShape = ButtonDefaults.shape()

    /** Uses the corresponding Button height as square IconButton bounds. */
    fun size(
        size: ButtonSize = ButtonSize.Medium,
    ): UiDp = ButtonDefaults.height(size)

    /** Resolves uniform icon padding for [size]. */
    fun contentPadding(
        size: ButtonSize = ButtonSize.Medium,
    ): UiDp {
        return when (size) {
            ButtonSize.Compact -> 8.dp
            ButtonSize.Medium -> 10.dp
            ButtonSize.Large -> 12.dp
        }
    }

    /** Returns the Button pressed-state highlight. */
    fun pressedColor(): Int = ButtonDefaults.pressedColor()
}
