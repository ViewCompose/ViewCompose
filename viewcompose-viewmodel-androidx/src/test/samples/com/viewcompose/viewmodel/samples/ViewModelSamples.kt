package com.viewcompose.viewmodel.samples

import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.createSavedStateHandle
import com.viewcompose.viewmodel.ProvideViewModelStoreOwner
import com.viewcompose.viewmodel.ViewModelScopeProvider
import com.viewcompose.viewmodel.rememberViewModelScopeProvider
import com.viewcompose.viewmodel.rememberViewModelStoreOwner
import com.viewcompose.viewmodel.savedStateHandle
import com.viewcompose.viewmodel.viewModel
import com.viewcompose.ui.foundation.UiTreeBuilder

// DOCS_REGION_START(viewmodel-resolution)
class ProfileViewModel : ViewModel()

class SavedProfileViewModel(
    val handle: SavedStateHandle,
    val profileId: String,
) : ViewModel()

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

/** Creates a ViewModel with constructor dependencies and the owner's restored state handle. */
fun UiTreeBuilder.initializerViewModelSample(
    owner: ViewModelStoreOwner,
): SavedProfileViewModel {
    return viewModel(owner = owner) {
        SavedProfileViewModel(
            handle = createSavedStateHandle(),
            profileId = "primary-profile",
        )
    }
}

/** Uses the initializer contract when the model class is selected at runtime. */
fun UiTreeBuilder.kClassInitializerViewModelSample(
    owner: ViewModelStoreOwner,
): SavedProfileViewModel {
    return viewModel(
        modelClass = SavedProfileViewModel::class,
        owner = owner,
    ) {
        SavedProfileViewModel(
            handle = createSavedStateHandle(),
            profileId = "runtime-selected-profile",
        )
    }
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

// DOCS_REGION_START(viewmodel-scoped-owners)
/** Retains one profile subtree below a stable parent and child identity. */
fun UiTreeBuilder.retainedViewModelScopeSample(
    parentOwner: ViewModelStoreOwner,
    parentLifecycleOwner: LifecycleOwner,
): ProfileViewModel {
    val provider = rememberViewModelScopeProvider(
        key = "profile-pane-provider",
        parentOwner = parentOwner,
        lifecycleOwner = parentLifecycleOwner,
    )
    val profileOwner = rememberViewModelStoreOwner(
        key = "primary-profile-pane",
        provider = provider,
    )
    lateinit var model: ProfileViewModel
    ProvideViewModelStoreOwner(profileOwner) {
        model = viewModel()
    }
    return model
}

/** Sends the terminal signal only when the logical profile pane is permanently removed. */
fun removeRetainedProfileScope(provider: ViewModelScopeProvider) {
    provider.clear("primary-profile-pane")
}
// DOCS_REGION_END(viewmodel-scoped-owners)

// DOCS_REGION_START(viewmodel-saved-state)
/** Resolves an independent saved-state namespace under a stable key. */
fun UiTreeBuilder.savedStateHandleSample(): SavedStateHandle {
    return savedStateHandle(key = "profile-filters")
}
// DOCS_REGION_END(viewmodel-saved-state)
