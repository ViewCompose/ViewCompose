package com.viewcompose.renderer.view

import android.content.Context

/**
 * 将 dp 整数转换为当前 Context 下的像素值。
 * Converts an integer dp value to pixels for the current Context.
 */
internal fun Context.dpToPx(dp: Int): Int = (dp * resources.displayMetrics.density + 0.5f).toInt()
