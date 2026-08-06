package com.viewcompose.ui.foundation

import com.viewcompose.ui.unit.UiDp
import com.viewcompose.ui.unit.UiSp

/**
 */
internal val Int.dp: UiDp
    get() = UiDp(toFloat())

internal val Float.dp: UiDp
    get() = UiDp(this)

internal val Int.sp: UiSp
    get() = UiSp(toFloat())

internal val Float.sp: UiSp
    get() = UiSp(this)
