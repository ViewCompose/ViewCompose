package com.viewcompose.viewmodel

import androidx.annotation.MainThread
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelStoreOwner

/**
 * Returns a [SavedStateHandle] scoped to [owner] or the nearest [LocalViewModelStoreOwner].
 *
 * The handle is owned by an internal [SavedStateHandleHolderViewModel], so repeated calls with the
 * same owner and [key] return the same handle and survive configuration change. Process-death
 * persistence requires an owner whose default factory and creation extras support SavedStateHandle,
 * such as a standard Android host, navigation destination, or navigation graph owner.
 *
 * Use a distinct stable key for independent handles in the same store. The default key is reserved
 * for one general-purpose handle per owner. This function follows [viewModel] ownership and must run
 * on the Android main thread during composition.
 *
 * @sample com.viewcompose.viewmodel.samples.savedStateHandleSample
 * @param key ViewModel-store identity of the internal handle owner
 * @param owner explicit owner, or `null` to use [LocalViewModelStoreOwner.current]
 * @return the existing or newly created handle for [owner] and [key]
 * @throws IllegalArgumentException if no owner is available
 * @throws RuntimeException if the resolved factory or extras cannot construct a SavedStateHandle
 */
@MainThread
fun savedStateHandle(
    key: String = "__viewcompose_saved_state_handle__",
    owner: ViewModelStoreOwner? = null,
): SavedStateHandle {
    val holder: SavedStateHandleHolderViewModel = viewModel(
        modelClass = SavedStateHandleHolderViewModel::class,
        key = key,
        owner = owner,
    )
    return holder.handle
}

/**
 * ViewModel used internally to retain one [SavedStateHandle] in a `ViewModelStore`.
 *
 * This type is public so AndroidX factories can construct it. Application code should call
 * [savedStateHandle] instead of requesting or instantiating the holder directly.
 *
 * @property handle handle created and restored by the owner's ViewModel factory
 */
class SavedStateHandleHolderViewModel(
    /** Handle retained for the lifetime of this ViewModel entry. */
    val handle: SavedStateHandle,
) : ViewModel()
