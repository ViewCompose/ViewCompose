package com.viewcompose.navigation

import android.app.Application
import android.os.Bundle
import androidx.annotation.MainThread
import androidx.lifecycle.DEFAULT_ARGS_KEY
import androidx.lifecycle.HasDefaultViewModelProviderFactory
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.SAVED_STATE_REGISTRY_OWNER_KEY
import androidx.lifecycle.SavedStateViewModelFactory
import androidx.lifecycle.VIEW_MODEL_STORE_OWNER_KEY
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.enableSavedStateHandles
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.MutableCreationExtras
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import com.viewcompose.host.android.viewComposeSaveableStateRegistry
import com.viewcompose.navigation.core.NavEntry
import com.viewcompose.navigation.core.NavEntryLifecycleState
import com.viewcompose.navigation.core.NavValue
import com.viewcompose.ui.foundation.SaveableStateRegistry as ViewComposeSaveableStateRegistry
import com.viewcompose.viewmodel.ViewModelStoreOwnerLease

/**
 * Android owner bundle for one navigation entry.
 *
 * Each entry owns an independent lifecycle, SavedStateRegistry, and ViewCompose saveable registry.
 * Its ViewModelStore is leased from the host's shared scoped-owner provider so multiple instances
 * of the same route remain isolated without a navigation-specific store allocator.
 */
internal class NavEntryOwner(
    val entry: NavEntry,
    private val application: Application?,
    restoredState: Bundle?,
    private val parentViewModelProviderFactory: ViewModelProvider.Factory? = null,
    parentViewModelCreationExtras: CreationExtras = CreationExtras.Empty,
) : LifecycleOwner,
    ViewModelStoreOwner,
    SavedStateRegistryOwner,
    HasDefaultViewModelProviderFactory {
    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateController = SavedStateRegistryController.create(this)
    private val defaultArguments = entry.route.arguments.toBundle()
    private val inheritedCreationExtras = MutableCreationExtras(parentViewModelCreationExtras)
    private var viewModelStoreOwnerLease: ViewModelStoreOwnerLease? = null

    override val lifecycle: Lifecycle
        get() = lifecycleRegistry

    override val viewModelStore: ViewModelStore
        get() = checkNotNull(viewModelStoreOwnerLease) {
            "Navigation entry ${entry.id} has no bound ViewModel scope lease."
        }.owner.viewModelStore

    override val savedStateRegistry: SavedStateRegistry
        get() = savedStateController.savedStateRegistry

    override val defaultViewModelProviderFactory: ViewModelProvider.Factory =
        parentViewModelProviderFactory ?: SavedStateViewModelFactory(
            application,
            this,
            defaultArguments,
        )

    override val defaultViewModelCreationExtras: CreationExtras
        get() = MutableCreationExtras(inheritedCreationExtras).apply {
            // The entry owns saved state and storage; unrelated parent DI/application extras remain intact.
            this[SAVED_STATE_REGISTRY_OWNER_KEY] = this@NavEntryOwner
            this[VIEW_MODEL_STORE_OWNER_KEY] = this@NavEntryOwner
            this[DEFAULT_ARGS_KEY] = Bundle(defaultArguments)
            if (this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] == null) {
                application?.let { app ->
                    this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] = app
                }
            }
        }

    val compositionSaveableStateRegistry: ViewComposeSaveableStateRegistry

    /** Stable destination-local holder retained independently of the native presentation. */
    val destinationContext = NavDestinationContext(
        entry = entry,
        initialPresentation = entry.preparedDestinationPresentation(),
    )

    var entryLifecycleState: NavEntryLifecycleState = NavEntryLifecycleState.Initialized
        private set

    init {
        savedStateController.performAttach()
        enableSavedStateHandles()
        savedStateController.performRestore(restoredState?.let(::Bundle))
        compositionSaveableStateRegistry = viewComposeSaveableStateRegistry(this)
    }

    @MainThread
    fun bindViewModelStoreOwnerLease(lease: ViewModelStoreOwnerLease) {
        check(entryLifecycleState == NavEntryLifecycleState.Initialized) {
            "Navigation entry ${entry.id} cannot bind a ViewModel scope after lifecycle start."
        }
        check(viewModelStoreOwnerLease == null) {
            "Navigation entry ${entry.id} already has a ViewModel scope lease."
        }
        viewModelStoreOwnerLease = lease
    }

    @MainThread
    fun moveTo(state: NavEntryLifecycleState) {
        if (state == entryLifecycleState) {
            return
        }
        check(entryLifecycleState != NavEntryLifecycleState.Destroyed) {
            "Destroyed navigation entry ${entry.id} cannot change lifecycle state."
        }
        if (state == NavEntryLifecycleState.Initialized) {
            error(
                "Navigation entry ${entry.id} cannot return to Initialized from " +
                    "$entryLifecycleState.",
            )
        }
        if (state == NavEntryLifecycleState.Destroyed) {
            try {
                if (lifecycleRegistry.currentState == Lifecycle.State.INITIALIZED) {
                    // LifecycleRegistry intentionally disallows INITIALIZED -> DESTROYED. A candidate page can
                    // still be rolled back before its first render, so give observers the minimal balanced
                    // ON_CREATE/ON_DESTROY sequence.
                    lifecycleRegistry.currentState = Lifecycle.State.CREATED
                }
                lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
            } finally {
                viewModelStoreOwnerLease?.close()
                viewModelStoreOwnerLease = null
                entryLifecycleState = NavEntryLifecycleState.Destroyed
            }
            return
        }
        lifecycleRegistry.currentState = state.toAndroidLifecycleState()
        entryLifecycleState = state
    }

    @MainThread
    fun performSave(): Bundle {
        check(entryLifecycleState != NavEntryLifecycleState.Destroyed) {
            "Cannot save destroyed navigation entry ${entry.id}."
        }
        return Bundle().also(savedStateController::performSave)
    }

    private fun NavEntryLifecycleState.toAndroidLifecycleState(): Lifecycle.State {
        return when (this) {
            NavEntryLifecycleState.Initialized -> Lifecycle.State.INITIALIZED
            NavEntryLifecycleState.Created -> Lifecycle.State.CREATED
            NavEntryLifecycleState.Started -> Lifecycle.State.STARTED
            NavEntryLifecycleState.Resumed -> Lifecycle.State.RESUMED
            NavEntryLifecycleState.Destroyed -> Lifecycle.State.DESTROYED
        }
    }
}

/**
 * Converts navigation arguments into an Android default-arguments Bundle.
 */
private fun Map<String, NavValue>.toBundle(): Bundle {
    return Bundle().apply {
        this@toBundle.forEach { (key, value) ->
            when (value) {
                NavValue.Null -> putString(key, null)
                is NavValue.Text -> putString(key, value.value)
                is NavValue.IntValue -> putInt(key, value.value)
                is NavValue.LongValue -> putLong(key, value.value)
                is NavValue.BooleanValue -> putBoolean(key, value.value)
                is NavValue.FloatValue -> putFloat(key, value.value)
                is NavValue.DoubleValue -> putDouble(key, value.value)
            }
        }
    }
}
