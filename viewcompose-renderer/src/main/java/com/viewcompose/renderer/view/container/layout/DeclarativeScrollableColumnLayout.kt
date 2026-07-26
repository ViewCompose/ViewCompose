package com.viewcompose.renderer.view.container

import android.content.Context
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.core.widget.NestedScrollView

internal class DeclarativeScrollableColumnLayout(
    context: Context,
) : NestedScrollView(context), ChildHostViewGroup {
    internal val innerLayout = DeclarativeLinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
    }
    override val childHost: ViewGroup
        get() = innerLayout

    init {
        super.addView(
            innerLayout,
            LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT),
        )
        isFillViewport = true
    }
}
