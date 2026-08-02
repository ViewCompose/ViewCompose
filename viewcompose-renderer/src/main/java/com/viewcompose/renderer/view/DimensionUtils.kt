package com.viewcompose.renderer.view

import android.content.Context

/**
 * Converts an integer dp value to pixels using the current Context density.
 * Converts an integer dp value to pixels for the current Context.
 */
internal fun Context.dpToPx(dp: Int): Int = (dp * resources.displayMetrics.density + 0.5f).toInt()
