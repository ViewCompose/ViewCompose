package com.viewcompose.widget.core

/** Default container and content colors for scaffold components. */
object ScaffoldDefaults {
    /** Returns the scaffold background color. */
    fun containerColor(): Int = Theme.colors.background

    /** Returns the default content color inside a scaffold. */
    fun contentColor(): Int = Theme.colors.onSurface
}
