package com.viewcompose.ui.foundation

import com.viewcompose.ui.unit.UiDp

/** Default size and tint tokens for icon components. */
object IconDefaults {
    /** Returns the default square icon size. */
    fun size(): UiDp = 24.dp

    /** Returns the tint inherited from the nearest content-color provider. */
    fun tint(): Int = ContentColor.current
}
