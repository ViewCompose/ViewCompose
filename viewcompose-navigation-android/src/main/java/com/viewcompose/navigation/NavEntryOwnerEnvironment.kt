package com.viewcompose.navigation

import com.viewcompose.lifecycle.ProvideLifecycleOwner
import com.viewcompose.viewmodel.ProvideViewModelStoreOwner
import com.viewcompose.ui.foundation.ProvideLocal
import com.viewcompose.ui.foundation.ProvideSaveableStateRegistry
import com.viewcompose.ui.foundation.UiLocals
import com.viewcompose.ui.foundation.UiTreeBuilder
import com.viewcompose.ui.foundation.uiLocalOf

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
    /** Current hierarchy, or `null` outside [NavHost] destination content. */
    val current: NavGraphOwnerScope?
        get() = UiLocals.current(LocalNavGraphOwnerScopeValue)
}

/** Provides a destination owner into lifecycle, ViewModelStore, and composition saveable-state locals. */
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

/** Exposes the active parent graph-owner hierarchy to a destination subtree. */
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
 *
 * @throws IllegalStateException outside destination content or when [route] is not an active parent
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
