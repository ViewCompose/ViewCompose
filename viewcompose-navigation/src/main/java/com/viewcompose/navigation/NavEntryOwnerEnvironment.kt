package com.viewcompose.navigation

import com.viewcompose.lifecycle.ProvideLifecycleOwner
import com.viewcompose.viewmodel.ProvideViewModelStoreOwner
import com.viewcompose.widget.core.ProvideLocal
import com.viewcompose.widget.core.ProvideSaveableStateRegistry
import com.viewcompose.widget.core.UiLocals
import com.viewcompose.widget.core.UiTreeBuilder
import com.viewcompose.widget.core.uiLocalOf

private val LocalNavGraphOwnerScopeValue = uiLocalOf<NavGraphOwnerScope?>(
    debugName = "NavGraphOwnerScope",
    debugValueFormatter = { scope ->
        scope?.entries
            ?.joinToString(prefix = "[", postfix = "]") { entry -> entry.route.name }
            ?: "none"
    },
) { null }

/**
 * 访问当前正在渲染目的地的图 owner 层级。
 * Accesses the graph-owner hierarchy of the destination currently being rendered.
 */
object LocalNavGraphOwnerScope {
    val current: NavGraphOwnerScope?
        get() = UiLocals.current(LocalNavGraphOwnerScopeValue)
}

/**
 * 将目的地 owner 注入 lifecycle、ViewModelStore 和组合保存状态 locals。
 * Provides a destination owner into lifecycle, ViewModelStore, and composition saveable-state locals.
 */
internal fun UiTreeBuilder.ProvideNavEntryOwner(
    owner: NavEntryOwner,
    content: UiTreeBuilder.() -> Unit,
) {
    ProvideLifecycleOwner(owner) {
        ProvideViewModelStoreOwner(owner) {
            ProvideSaveableStateRegistry(owner.compositionSaveableStateRegistry) {
                content()
            }
        }
    }
}

/**
 * 为目的地子树暴露当前活跃的父图 owner 层级。
 * Exposes the active parent graph-owner hierarchy to a destination subtree.
 */
internal fun UiTreeBuilder.ProvideNavGraphOwnerScope(
    scope: NavGraphOwnerScope,
    content: UiTreeBuilder.() -> Unit,
) {
    ProvideLocal(
        local = LocalNavGraphOwnerScopeValue,
        value = scope,
        content = content,
    )
}

/**
 * 使用 [route] 对应的活跃图作为 [content] 的 Lifecycle、ViewModel 和 saved-state owner。
 * Runs [content] with the active graph identified by [route] as its Lifecycle, ViewModel, and
 * saved-state owner.
 */
fun UiTreeBuilder.ProvideNavGraphOwner(
    route: String,
    content: UiTreeBuilder.() -> Unit,
) {
    val owner = checkNotNull(LocalNavGraphOwnerScope.current) {
        "ProvideNavGraphOwner must be called inside NavHost destination content."
    }.requireOwner(route)
    ProvideLifecycleOwner(owner) {
        ProvideViewModelStoreOwner(owner) {
            ProvideSaveableStateRegistry(owner.delegate.compositionSaveableStateRegistry) {
                content()
            }
        }
    }
}
