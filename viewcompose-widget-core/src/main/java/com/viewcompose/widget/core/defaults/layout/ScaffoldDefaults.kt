package com.viewcompose.widget.core

/**
 * Scaffold 容器的默认背景和内容色。
 * Default background and content color for Scaffold containers.
 */
object ScaffoldDefaults {
    fun containerColor(): Int = Theme.colors.background

    fun contentColor(): Int = Theme.colors.onSurface
}
