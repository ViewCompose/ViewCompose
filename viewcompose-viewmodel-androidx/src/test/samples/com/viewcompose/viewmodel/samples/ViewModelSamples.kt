package com.viewcompose.viewmodel.samples

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelStoreOwner
import com.viewcompose.viewmodel.ProvideViewModelStoreOwner
import com.viewcompose.viewmodel.savedStateHandle
import com.viewcompose.viewmodel.viewModel
import com.viewcompose.ui.foundation.UiTreeBuilder

// DOCS_REGION_START(viewmodel-resolution)
class ProfileViewModel : ViewModel()

/** Resolves one instance from the owner installed by the current Android host. */
fun UiTreeBuilder.viewModelSample(): ProfileViewModel {
    return viewModel()
}

/** Keeps two instances of the same class in one store under stable application keys. */
fun UiTreeBuilder.keyedViewModelSample(
    owner: ViewModelStoreOwner,
): Pair<ProfileViewModel, ProfileViewModel> {
    val primary = viewModel(
        modelClass = ProfileViewModel::class,
        key = "primary-profile",
        owner = owner,
    )
    val comparison = viewModel(
        modelClass = ProfileViewModel::class,
        key = "comparison-profile",
        owner = owner,
    )
    return primary to comparison
}
// DOCS_REGION_END(viewmodel-resolution)

// DOCS_REGION_START(viewmodel-owner-boundary)
/** Installs a custom store owner for a nested subtree. */
fun UiTreeBuilder.provideViewModelStoreOwnerSample(
    owner: ViewModelStoreOwner,
): ProfileViewModel {
    lateinit var model: ProfileViewModel
    ProvideViewModelStoreOwner(owner) {
        model = viewModel()
    }
    return model
}
// DOCS_REGION_END(viewmodel-owner-boundary)

// DOCS_REGION_START(viewmodel-saved-state)
/** Resolves an independent saved-state namespace under a stable key. */
fun UiTreeBuilder.savedStateHandleSample(): SavedStateHandle {
    return savedStateHandle(key = "profile-filters")
}
// DOCS_REGION_END(viewmodel-saved-state)
