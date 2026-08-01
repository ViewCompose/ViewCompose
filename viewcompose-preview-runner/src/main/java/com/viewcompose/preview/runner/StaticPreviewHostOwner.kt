package com.viewcompose.preview.runner

import android.app.Application
import androidx.lifecycle.HasDefaultViewModelProviderFactory
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import com.viewcompose.host.android.viewComposeSaveableStateRegistry
import com.viewcompose.widget.core.SaveableStateRegistry as ViewComposeSaveableStateRegistry
import java.io.Closeable

/**
 * Gives static previews the same minimum owner environment as an Activity-hosted UI tree.
 * The owner is deliberately scoped to one rendered frame so previews cannot leak ViewModels or state.
 */
internal class StaticPreviewHostOwner(
    private val application: Application?,
) : LifecycleOwner,
    ViewModelStoreOwner,
    SavedStateRegistryOwner,
    HasDefaultViewModelProviderFactory,
    Closeable {
    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateController = SavedStateRegistryController.create(this)
    private val ownedViewModelStore = ViewModelStore()

    override val lifecycle: Lifecycle
        get() = lifecycleRegistry

    override val viewModelStore: ViewModelStore
        get() = ownedViewModelStore

    override val savedStateRegistry: SavedStateRegistry
        get() = savedStateController.savedStateRegistry

    override val defaultViewModelProviderFactory: ViewModelProvider.Factory =
        StaticPreviewViewModelFactory(application)

    val compositionSaveableStateRegistry: ViewComposeSaveableStateRegistry

    init {
        savedStateController.performAttach()
        savedStateController.performRestore(null)
        compositionSaveableStateRegistry = viewComposeSaveableStateRegistry(this)
        lifecycleRegistry.currentState = Lifecycle.State.RESUMED
    }

    override fun close() {
        if (lifecycleRegistry.currentState != Lifecycle.State.DESTROYED) {
            lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
        }
        ownedViewModelStore.clear()
    }
}

/**
 * Avoids AndroidX's application-wide default factory singleton inside the isolated worker.
 * That singleton is sensitive to mixed Lifecycle versions on an application's render classpath.
 */
private class StaticPreviewViewModelFactory(
    private val application: Application?,
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        val constructors = modelClass.declaredConstructors
        val constructor = application?.let {
            constructors.firstOrNull { candidate ->
                candidate.parameterTypes.contentEquals(
                    arrayOf(Application::class.java, SavedStateHandle::class.java),
                )
            }
        } ?: constructors.firstOrNull { candidate ->
            candidate.parameterTypes.contentEquals(arrayOf(SavedStateHandle::class.java))
        } ?: application?.let {
            constructors.firstOrNull { candidate ->
                candidate.parameterTypes.contentEquals(arrayOf(Application::class.java))
            }
        } ?: constructors.firstOrNull { candidate -> candidate.parameterCount == 0 }
        requireNotNull(constructor) {
            "Preview cannot create ${modelClass.name}. Supply an explicit ViewModel factory, or " +
                "provide a no-arg, Application, or SavedStateHandle constructor."
        }
        constructor.isAccessible = true
        val arguments = when {
            constructor.parameterCount == 0 -> emptyArray()
            constructor.parameterCount == 2 ->
                arrayOf(checkNotNull(application), SavedStateHandle())
            constructor.parameterTypes.single() == SavedStateHandle::class.java ->
                arrayOf(SavedStateHandle())
            else -> arrayOf(checkNotNull(application))
        }
        @Suppress("UNCHECKED_CAST")
        return constructor.newInstance(*arguments) as T
    }
}
