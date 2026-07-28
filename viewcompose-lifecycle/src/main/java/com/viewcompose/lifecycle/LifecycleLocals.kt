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

/**
 * 当前 UI 子树关联的 Android LifecycleOwner。
 * Android LifecycleOwner associated with the current UI subtree.
 */
object LocalLifecycleOwner {
    /**
     * 当前上下文中的 LifecycleOwner；未由 host 或 Provider 注入时为 null。
     * LifecycleOwner in the current context, or null when no host or Provider has injected one.
     */
    val current: LifecycleOwner?
        get() = UiLocals.current(LocalLifecycleOwnerValue)
}

/**
 * 在当前 UI 子树中提供 Android LifecycleOwner。
 * Provides an Android LifecycleOwner to the current UI subtree.
 */
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
