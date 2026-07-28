package com.viewcompose.viewmodel

import androidx.annotation.MainThread
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelStoreOwner

/**
 * 返回作用域绑定到当前 ViewModelStoreOwner 的 [SavedStateHandle]。
 * Returns a [SavedStateHandle] scoped to the current [ViewModelStoreOwner].
 *
 * 该 handle 由内部 ViewModel 条目持有，因此能跨配置变更保留。
 * The handle is backed by an internal ViewModel entry and survives configuration changes.
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
 * 持有 SavedStateHandle 的内部 ViewModel。
 * Internal ViewModel that owns a SavedStateHandle.
 */
class SavedStateHandleHolderViewModel(
    val handle: SavedStateHandle,
) : ViewModel()
