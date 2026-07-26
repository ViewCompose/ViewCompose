package com.viewcompose.widget.core

import com.viewcompose.ui.shape.UiShape

object IconButtonDefaults {
    fun containerColor(
        variant: ButtonVariant = ButtonVariant.Primary,
        enabled: Boolean = true,
    ): Int = ButtonDefaults.containerColor(variant, enabled)

    fun contentColor(
        variant: ButtonVariant = ButtonVariant.Primary,
        enabled: Boolean = true,
    ): Int = ButtonDefaults.contentColor(variant, enabled)

    fun borderColor(
        variant: ButtonVariant = ButtonVariant.Primary,
        enabled: Boolean = true,
    ): Int = ButtonDefaults.borderColor(variant, enabled)

    fun borderWidth(
        variant: ButtonVariant = ButtonVariant.Primary,
    ): Int = ButtonDefaults.borderWidth(variant)

    fun shape(): UiShape = ButtonDefaults.shape()

    fun size(
        size: ButtonSize = ButtonSize.Medium,
    ): Int = ButtonDefaults.height(size)

    fun contentPadding(
        size: ButtonSize = ButtonSize.Medium,
    ): Int {
        return when (size) {
            ButtonSize.Compact -> 8.dp
            ButtonSize.Medium -> 10.dp
            ButtonSize.Large -> 12.dp
        }
    }

    fun pressedColor(): Int = ButtonDefaults.pressedColor()
}
