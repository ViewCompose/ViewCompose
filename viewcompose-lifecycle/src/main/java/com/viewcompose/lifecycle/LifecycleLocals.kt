package com.viewcompose.lifecycle

import androidx.lifecycle.LifecycleOwner
import com.viewcompose.widget.core.ProvideLocal
import com.viewcompose.widget.core.UiLocals
import com.viewcompose.widget.core.UiTreeBuilder
import com.viewcompose.widget.core.uiLocalOf

private val LocalLifecycleOwnerValue = uiLocalOf<LifecycleOwner?>(
    debugName = "LifecycleOwner",
    debugValueFormatter = { owner -> owner?.lifecycle?.currentState?.name ?: "none" },
) { null }

object LocalLifecycleOwner {
    val current: LifecycleOwner?
        get() = UiLocals.current(LocalLifecycleOwnerValue)
}

fun UiTreeBuilder.ProvideLifecycleOwner(
    owner: LifecycleOwner,
    content: UiTreeBuilder.() -> Unit,
) {
    ProvideLocal(
        local = LocalLifecycleOwnerValue,
        value = owner,
        content = content,
    )
}
