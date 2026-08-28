package com.viewcompose.navigation

import android.app.Application
import android.os.Bundle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.CreationExtras
import com.viewcompose.viewmodel.ViewModelScopeProvider

internal class NavigationTestParentViewModelStoreOwner(
    override val viewModelStore: ViewModelStore = ViewModelStore(),
) : ViewModelStoreOwner

internal fun navigationTestOwnerStore(
    application: Application,
    restoredState: Bundle? = null,
    parentViewModelProviderFactory: ViewModelProvider.Factory? = null,
    parentViewModelCreationExtras: CreationExtras = CreationExtras.Empty,
    parentOwner: ViewModelStoreOwner = NavigationTestParentViewModelStoreOwner(),
    providerKey: Any = Any(),
): NavEntryOwnerStore {
    return NavEntryOwnerStore(
        application = application,
        viewModelScopeProvider = ViewModelScopeProvider(
            parentOwner = parentOwner,
            providerKey = providerKey,
        ),
        restoredState = restoredState,
        parentViewModelProviderFactory = parentViewModelProviderFactory,
        parentViewModelCreationExtras = parentViewModelCreationExtras,
    )
}

internal fun NavEntryOwner.bindNavigationTestViewModelScope(
    parentOwner: ViewModelStoreOwner = NavigationTestParentViewModelStoreOwner(),
    providerKey: Any = Any(),
): NavEntryOwner {
    val provider = ViewModelScopeProvider(
        parentOwner = parentOwner,
        providerKey = providerKey,
    )
    bindViewModelStoreOwnerLease(
        provider.acquireOwner(
            key = entry.id,
            savedStateRegistryOwner = this,
        ),
    )
    return this
}
