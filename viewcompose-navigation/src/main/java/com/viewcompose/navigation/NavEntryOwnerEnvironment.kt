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

/** Accesses the graph-owner hierarchy of the destination currently being rendered. */
object LocalNavGraphOwnerScope {
    val current: NavGraphOwnerScope?
        get() = UiLocals.current(LocalNavGraphOwnerScopeValue)
}

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
