package com.viewcompose.viewmodel

import androidx.annotation.MainThread
import androidx.lifecycle.HasDefaultViewModelProviderFactory
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.MutableCreationExtras
import com.viewcompose.widget.core.remember
import kotlin.reflect.KClass

/**
 * 从当前或显式 ViewModelStoreOwner 获取指定类型的 ViewModel。
 * Retrieves a ViewModel of the requested type from the current or explicit ViewModelStoreOwner.
 */
@MainThread
inline fun <reified VM : ViewModel> viewModel(
    key: String? = null,
    owner: ViewModelStoreOwner? = null,
    factory: ViewModelProvider.Factory? = null,
    extras: CreationExtras? = null,
): VM {
    return viewModel(
        modelClass = VM::class,
        key = key,
        owner = owner,
        factory = factory,
        extras = extras,
    )
}

/**
 * 从当前或显式 ViewModelStoreOwner 获取指定 KClass 的 ViewModel。
 * Retrieves a ViewModel for the given KClass from the current or explicit ViewModelStoreOwner.
 *
 * 实例由 remember 缓存，只有 owner/key/factory/extras/modelClass 变化时才重新解析 provider。
 * The instance is cached by remember and resolves the provider again only when owner/key/factory/extras/modelClass changes.
 */
@MainThread
fun <VM : ViewModel> viewModel(
    modelClass: KClass<VM>,
    key: String? = null,
    owner: ViewModelStoreOwner? = null,
    factory: ViewModelProvider.Factory? = null,
    extras: CreationExtras? = null,
): VM {
    val resolvedOwner = owner ?: LocalViewModelStoreOwner.current
    requireNotNull(resolvedOwner) {
        "No ViewModelStoreOwner found. Use ComponentActivity/Fragment.setUiContent " +
            "or wrap with ProvideViewModelStoreOwner."
    }
    // owner/factory/extras 都参与 remember key，保持 ViewModelProvider 解析与调用方输入一致。
    // owner/factory/extras all participate in the remember key so ViewModelProvider resolution matches caller inputs.
    return remember(
        resolvedOwner,
        key,
        factory,
        extras,
        modelClass,
    ) {
        val provider = ViewModelProvider(
            resolvedOwner.viewModelStore,
            resolveFactory(
                owner = resolvedOwner,
                override = factory,
            ),
            resolveCreationExtras(
                owner = resolvedOwner,
                override = extras,
            ),
        )
        if (key.isNullOrBlank()) {
            provider[modelClass.java]
        } else {
            provider[key, modelClass.java]
        }
    }
}

private fun resolveFactory(
    owner: ViewModelStoreOwner,
    override: ViewModelProvider.Factory?,
): ViewModelProvider.Factory {
    if (override != null) {
        return override
    }
    return (owner as? HasDefaultViewModelProviderFactory)?.defaultViewModelProviderFactory
        ?: ViewModelProvider.NewInstanceFactory()
}

/**
 * 解析 CreationExtras，并复制 owner 默认 extras，避免后续调用方修改共享实例。
 * Resolves CreationExtras and copies owner defaults so later callers do not mutate a shared instance.
 */
private fun resolveCreationExtras(
    owner: ViewModelStoreOwner,
    override: CreationExtras?,
): CreationExtras {
    if (override != null) {
        return override
    }
    val defaults = (owner as? HasDefaultViewModelProviderFactory)?.defaultViewModelCreationExtras
        ?: CreationExtras.Empty
    return MutableCreationExtras(defaults)
}
