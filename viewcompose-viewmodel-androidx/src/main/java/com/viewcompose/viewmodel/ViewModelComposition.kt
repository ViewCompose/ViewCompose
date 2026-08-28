package com.viewcompose.viewmodel

import androidx.annotation.MainThread
import androidx.lifecycle.HasDefaultViewModelProviderFactory
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.MutableCreationExtras
import androidx.lifecycle.viewmodel.ViewModelInitializer
import com.viewcompose.ui.foundation.remember
import kotlin.reflect.KClass

/**
 * Returns a [VM] scoped to [owner] or the nearest [LocalViewModelStoreOwner].
 *
 * The resolved owner and [key] define persistent identity in its `ViewModelStore`. Recomposition
 * and repeated calls query that store and return the same instance until the owner clears it. Only
 * a `null` key uses AndroidX's default class-derived key; every non-null string is passed unchanged
 * as an explicit key. [factory] and [extras] affect creation only when no matching entry exists.
 *
 * This function must be called from a ViewCompose composition on the Android main thread. Standard
 * Activity, Fragment, navigation destination, and navigation graph hosts provide an owner. Pass one
 * explicitly or use [ProvideViewModelStoreOwner] for a custom boundary.
 *
 * This lookup performs one bounded in-memory `ViewModelProvider` query whenever the composition
 * call executes. It does not retain a second composition-level instance cache, so clearing the
 * store is observable on the next lookup.
 *
 * @sample com.viewcompose.viewmodel.samples.viewModelSample
 * @param VM ViewModel type to resolve
 * @param key optional identity; `null` selects the class-derived default and every non-null string
 * is an explicit key
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
 * AndroidX's `ViewModelStore` is authoritative: a matching owner/key entry is reused even when a
 * different Factory or extras object is supplied later. Only `null` selects the class-derived
 * default key. Empty, whitespace-only, and ordinary non-null strings are distinct explicit keys.
 *
 * Factory resolution prefers [factory], then
 * [HasDefaultViewModelProviderFactory.defaultViewModelProviderFactory], then
 * [ViewModelProvider.NewInstanceFactory]. Extras resolution similarly prefers [extras], otherwise it
 * copies the owner's defaults so this function never mutates a shared extras object.
 *
 * This function runs on the Android main thread inside an active ViewCompose composition. It makes
 * one bounded in-memory provider lookup per executed call and stores no ViewModel instance in a
 * composition slot. If an explicit key already contains a different model class, AndroidX replaces
 * that entry and clears the old model according to `ViewModelProvider` semantics.
 *
 * @sample com.viewcompose.viewmodel.samples.keyedViewModelSample
 * @param VM ViewModel type to resolve
 * @param modelClass runtime class used by [ViewModelProvider]
 * @param key optional identity; `null` selects the class-derived default and every non-null string
 * is an explicit key
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
): VM = resolveViewModel(
    modelClass = modelClass,
    key = key,
    owner = owner,
    factory = factory,
    extras = extras,
)

private fun <VM : ViewModel> resolveViewModel(
    modelClass: KClass<VM>,
    key: String?,
    owner: ViewModelStoreOwner?,
    factory: ViewModelProvider.Factory?,
    extras: CreationExtras?,
): VM {
    val resolvedOwner = owner ?: LocalViewModelStoreOwner.current
    requireNotNull(resolvedOwner) {
        "No ViewModelStoreOwner found. Use ComponentActivity/Fragment.setUiContent " +
            "or wrap with ProvideViewModelStoreOwner."
    }
    requireActiveViewModelComposition()
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
    return if (key == null) {
        provider[modelClass.java]
    } else {
        provider[key, modelClass.java]
    }
}

/**
 * Returns a [VM] created by [initializer] when its resolved store has no matching entry.
 *
 * The initializer receives the resolved owner's default [CreationExtras] and runs at most once for
 * the owner/key entry. It may call AndroidX extras extensions such as `createSavedStateHandle()`.
 * Recomposition performs a store lookup but does not invoke the initializer again for an existing
 * entry. Only `null` selects the class-derived default key; every non-null string is explicit.
 *
 * This function is main-thread confined and must run in an active ViewCompose composition. An
 * initializer exception propagates without publishing a ViewModel entry, so a later lookup may
 * retry creation. The initializer must not block, perform I/O, or retain the receiver extras.
 *
 * @sample com.viewcompose.viewmodel.samples.initializerViewModelSample
 * @param VM ViewModel type created and resolved from the store
 * @param key optional identity; `null` selects the class-derived default and every non-null string
 * is an explicit key
 * @param owner explicit owner, or `null` to use [LocalViewModelStoreOwner.current]
 * @param initializer synchronous creation callback invoked only when the store entry is absent
 * @return the existing or newly initialized [VM] for the resolved owner and key
 * @throws IllegalArgumentException if no owner is available
 * @throws RuntimeException if [initializer] fails
 */
@MainThread
inline fun <reified VM : ViewModel> viewModel(
    key: String? = null,
    owner: ViewModelStoreOwner? = null,
    noinline initializer: CreationExtras.() -> VM,
): VM {
    return viewModel(
        modelClass = VM::class,
        key = key,
        owner = owner,
        initializer = initializer,
    )
}

/**
 * Returns [modelClass] created by [initializer] when its resolved store entry is absent.
 *
 * This overload supports runtime-selected model classes while preserving the same owner, key,
 * `CreationExtras`, failure, threading, and store-only caching contract as the reified initializer
 * overload. The callback receives the owner's default extras and an existing entry ignores a later
 * callback object.
 *
 * Factory construction and the provider query are bounded in-memory work. [initializer] runs
 * synchronously on the Android main thread and must not block, perform I/O, or retain its extras
 * receiver.
 *
 * @sample com.viewcompose.viewmodel.samples.kClassInitializerViewModelSample
 * @param VM ViewModel type created and resolved from the store
 * @param modelClass runtime class registered with the initializer Factory
 * @param key optional identity; `null` selects the class-derived default and every non-null string
 * is an explicit key
 * @param owner explicit owner, or `null` to use [LocalViewModelStoreOwner.current]
 * @param initializer synchronous creation callback invoked only when the store entry is absent
 * @return the existing or newly initialized [VM] for the resolved owner and key
 * @throws IllegalArgumentException if no owner is available or the Factory cannot match [modelClass]
 * @throws RuntimeException if [initializer] fails
 */
@MainThread
fun <VM : ViewModel> viewModel(
    modelClass: KClass<VM>,
    key: String? = null,
    owner: ViewModelStoreOwner? = null,
    initializer: CreationExtras.() -> VM,
): VM {
    val initializerFactory = ViewModelProvider.Factory.from(
        ViewModelInitializer(modelClass, initializer),
    )
    return resolveViewModel(
        modelClass = modelClass,
        key = key,
        owner = owner,
        factory = initializerFactory,
        extras = null,
    )
}

/** Uses one ordinary remember slot only to preserve this API's composition-call boundary. */
private fun requireActiveViewModelComposition() {
    remember { Unit }
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
