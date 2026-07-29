package com.viewcompose.widget.core

import com.viewcompose.ui.unit.UiDp

/**
 * Icon DSL 的默认尺寸和 tint。
 * Default size and tint for the Icon DSL.
 *
 * tint 默认继承 ContentColor，使图标能跟随 Surface/Text 的内容色上下文。
 * tint inherits ContentColor by default so icons follow Surface/Text content color context.
 */
object IconDefaults {
    fun size(): UiDp = 24.dp

    fun tint(): Int = ContentColor.current
}
