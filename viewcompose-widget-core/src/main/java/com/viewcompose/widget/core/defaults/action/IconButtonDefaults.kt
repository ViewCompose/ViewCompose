package com.viewcompose.widget.core

import com.viewcompose.ui.shape.UiShape

/**
 * IconButton DSL 的默认 token 入口。
 * Default token entry point for the IconButton DSL.
 *
 * IconButton 复用 Button 的颜色层级，仅将内容区尺寸压缩为正方形触控目标。
 * IconButton reuses Button color hierarchy and compresses the content area into a square touch target.
 */
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
