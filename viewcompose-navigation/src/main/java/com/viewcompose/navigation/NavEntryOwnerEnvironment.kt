package com.viewcompose.navigation

import com.viewcompose.lifecycle.ProvideLifecycleOwner
import com.viewcompose.viewmodel.ProvideViewModelStoreOwner
import com.viewcompose.widget.core.ProvideSaveableStateRegistry
import com.viewcompose.widget.core.UiTreeBuilder

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
