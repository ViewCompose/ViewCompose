package com.viewcompose.navigation

import android.content.Context
import android.widget.FrameLayout

internal class NavHostView(
    context: Context,
) : FrameLayout(context) {
    internal var runtime: NavHostRuntime? = null

    init {
        layoutParams = LayoutParams(
            LayoutParams.MATCH_PARENT,
            LayoutParams.MATCH_PARENT,
        )
    }
}

internal fun destinationContainer(context: Context): FrameLayout {
    return FrameLayout(context).apply {
        layoutParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT,
        )
    }
}
