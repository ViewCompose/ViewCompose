package com.viewcompose.widget.core

import com.viewcompose.ui.unit.UiDp
import com.viewcompose.ui.unit.UiSp

/**
 * widget-core 内部的单位简写；公开 API 位于 com.viewcompose.ui.unit。
 */
internal val Int.dp: UiDp
    get() = UiDp(toFloat())

internal val Float.dp: UiDp
    get() = UiDp(this)

internal val Int.sp: UiSp
    get() = UiSp(toFloat())

internal val Float.sp: UiSp
    get() = UiSp(this)
