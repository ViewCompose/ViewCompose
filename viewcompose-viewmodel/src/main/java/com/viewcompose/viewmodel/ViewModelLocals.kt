package com.viewcompose.viewmodel

import androidx.lifecycle.ViewModelStoreOwner
import com.viewcompose.widget.core.ProvideLocal
import com.viewcompose.widget.core.UiLocals
import com.viewcompose.widget.core.UiTreeBuilder
import com.viewcompose.widget.core.uiLocalOf

private val LocalViewModelStoreOwnerValue = uiLocalOf<ViewModelStoreOwner?>(
    debugName = "ViewModelStoreOwner",
    debugValueFormatter = { owner -> owner?.javaClass?.name ?: "none" },
) { null }

/**
 * 当前 UI 子树关联的 Android ViewModelStoreOwner。
 * Android ViewModelStoreOwner associated with the current UI subtree.
 */
object LocalViewModelStoreOwner {
    /**
     * 当前上下文中的 ViewModelStoreOwner；未由 host 或 Provider 注入时为 null。
     * ViewModelStoreOwner in the current context, or null when no host or Provider has injected one.
     */
    val current: ViewModelStoreOwner?
        get() = UiLocals.current(LocalViewModelStoreOwnerValue)
}

/**
 * 在当前 UI 子树中提供 ViewModelStoreOwner。
 * Provides a ViewModelStoreOwner to the current UI subtree.
 */
fun UiTreeBuilder.ProvideViewModelStoreOwner(
    owner: ViewModelStoreOwner,
    content: UiTreeBuilder.() -> Unit,
) {
    ProvideLocal(
        local = LocalViewModelStoreOwnerValue,
        value = owner,
        content = content,
    )
}
