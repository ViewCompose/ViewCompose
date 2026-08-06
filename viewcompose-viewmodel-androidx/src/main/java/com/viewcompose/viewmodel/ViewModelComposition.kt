package com.viewcompose.viewmodel

import androidx.annotation.MainThread
import androidx.lifecycle.HasDefaultViewModelProviderFactory
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.MutableCreationExtras
import com.viewcompose.ui.foundation.remember
import kotlin.reflect.KClass

/**
 * Returns a [VM] scoped to [owner] or the nearest [LocalViewModelStoreOwner].
 *
 * The resolved owner and [key] define persistent identity in its `ViewModelStore`. Recomposition and
 * repeated calls return the same instance until that owner clears its store. A `null` or blank key
 * uses AndroidX's default class-derived key. [factory] and [extras] affect creation only when no
 * matching instance already exists.
 *
 * This function must be called from a ViewCompose composition on the Android main thread. Standard
 * Activity, Fragment, navigation destination, and navigation graph hosts provide an owner. Pass one
 * explicitly or use [ProvideViewModelStoreOwner] for a custom boundary.
 *
 * @sample com.viewcompose.viewmodel.samples.viewModelSample
 * @param VM ViewModel type to resolve
 * @param key optional identity allowing multiple [VM] instances in one store
 * @param owner explicit owner, or `null` to use [LocalViewModelStoreOwner.current]
 * @param factory creation factory override; the owner's default is used when absent
 * @param extras creation extras override; a copy of the owner's defaults is used when absent
 * @return the existing or newly created [VM] for the resolved owner and key
 * @throws IllegalArgumentException if no owner is available
 * @throws RuntimeException if AndroidX cannot create the requested ViewModel
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
 * Returns [modelClass] scoped to [owner] or the nearest [LocalViewModelStoreOwner].
 *
 * Owner, [key], [factory], [extras], and [modelClass] form the composition lookup identity. Changing
 * one rebuilds the provider lookup, but AndroidX's `ViewModelStore` remains authoritative: a matching
 * owner/key entry is reused even when a different factory or extras object is supplied later.
 * `null` and blank keys both select AndroidX's default class-derived key.
 *
 * Factory resolution prefers [factory], then
 * [HasDefaultViewModelProviderFactory.defaultViewModelProviderFactory], then
 * [ViewModelProvider.NewInstanceFactory]. Extras resolution similarly prefers [extras], otherwise it
 * copies the owner's defaults so this function never mutates a shared extras object.
 *
 * @sample com.viewcompose.viewmodel.samples.keyedViewModelSample
 * @param VM ViewModel type to resolve
 * @param modelClass runtime class used by [ViewModelProvider]
 * @param key optional identity allowing multiple instances of [modelClass] in one store
 * @param owner explicit owner, or `null` to use [LocalViewModelStoreOwner.current]
 * @param factory creation factory override
 * @param extras creation extras override
 * @return the existing or newly created ViewModel for the resolved owner and key
 * @throws IllegalArgumentException if no owner is available
 * @throws RuntimeException if AndroidX cannot create [modelClass]
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
    // Provider inputs form the composition lookup identity; the owner's store remains authoritative.
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

/** Resolves explicit extras or copies owner defaults to avoid exposing a shared mutable instance. */
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
