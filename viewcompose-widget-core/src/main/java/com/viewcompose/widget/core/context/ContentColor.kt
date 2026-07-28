package com.viewcompose.widget.core

val LocalContentColor = uiLocalOf(
    debugName = "ContentColor",
    debugValueFormatter = { color -> "0x${color.toUInt().toString(16).padStart(8, '0')}" },
) { Theme.colors.onSurface }

/**
 * 当前内容默认颜色，通常由主题或容器组件提供。
 * Current default content color, usually provided by theme or container components.
 */
object ContentColor {
    val current: Int
        get() = UiLocals.current(LocalContentColor)
}
