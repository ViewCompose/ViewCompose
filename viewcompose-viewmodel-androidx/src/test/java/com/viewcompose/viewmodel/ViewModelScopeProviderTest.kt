package com.viewcompose.viewmodel

/*
 * 测试职责：覆盖 retained ViewModel scope provider 的引用、清理、隔离与配置重建契约。
 * Test responsibility: covers reference, cleanup, isolation, and recreation contracts for retained ViewModel scopes.
 */

import android.os.Bundle
import androidx.lifecycle.HasDefaultViewModelProviderFactory
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.SavedStateViewModelFactory
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.MutableCreationExtras
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ViewModelScopeProviderTest {
    @Test
    fun `equal child keys share one store while different keys remain isolated`() {
        val parent = TestParentOwner()
        val provider = ViewModelScopeProvider(parent, "provider")
        val first = provider.acquireOwner("shared")
        val second = provider.acquireOwner("shared")
        val isolated = provider.acquireOwner("isolated")

        assertSame(first.owner.viewModelStore, second.owner.viewModelStore)
        assertNotSame(first.owner.viewModelStore, isolated.owner.viewModelStore)

        first.close()
        second.close()
        isolated.close()
        provider.clearAll()
        parent.viewModelStore.clear()
    }

    @Test
    fun `terminal clear defers until every lease closes and rejects resurrection`() {
        val parent = TestParentOwner()
        val provider = ViewModelScopeProvider(parent, "provider")
        val first = provider.acquireOwner("child")
        val second = provider.acquireOwner("child")
        val model = trackingViewModel(first.owner)

        provider.clear("child")

        val terminalFailure = runCatching {
            provider.acquireOwner("child")
        }.exceptionOrNull()
        assertTrue(terminalFailure is IllegalStateException)
        assertTrue(terminalFailure?.message.orEmpty().contains("permanently removed"))
        assertFalse(model.cleared)

        first.close()
        first.close()
        assertFalse(model.cleared)

        second.close()
        assertTrue(model.cleared)

        val fresh = provider.acquireOwner("child")
        val freshModel = trackingViewModel(fresh.owner)
        assertNotSame(model, freshModel)

        fresh.close()
        provider.clearAll()
        parent.viewModelStore.clear()
    }

    @Test
    fun `closing a lease without clear retains the logical child`() {
        val parent = TestParentOwner()
        val provider = ViewModelScopeProvider(parent, "provider")
        val first = provider.acquireOwner("child")
        val model = trackingViewModel(first.owner)

        first.close()

        val second = provider.acquireOwner("child")
        assertSame(model, trackingViewModel(second.owner))
        assertFalse(model.cleared)

        second.close()
        provider.clear("child")
        assertTrue(model.cleared)
        provider.clearAll()
        parent.viewModelStore.clear()
    }

    @Test
    fun `equal provider keys share retained stores across provider recreation`() {
        val parent = TestParentOwner()
        val firstProvider = ViewModelScopeProvider(parent, StableKey("provider"))
        val firstLease = firstProvider.acquireOwner("child")
        val model = trackingViewModel(firstLease.owner)
        firstLease.close()

        val recreatedProvider = ViewModelScopeProvider(parent, StableKey("provider"))
        val recreatedLease = recreatedProvider.acquireOwner("child")

        assertSame(model, trackingViewModel(recreatedLease.owner))

        recreatedLease.close()
        recreatedProvider.clearAll()
        parent.viewModelStore.clear()
    }

    @Test
    fun `equal child keys under different providers remain isolated`() {
        val parent = TestParentOwner()
        val firstProvider = ViewModelScopeProvider(parent, "first-provider")
        val secondProvider = ViewModelScopeProvider(parent, "second-provider")
        val first = firstProvider.acquireOwner("child")
        val second = secondProvider.acquireOwner("child")

        assertNotSame(first.owner.viewModelStore, second.owner.viewModelStore)
        assertNotSame(trackingViewModel(first.owner), trackingViewModel(second.owner))

        first.close()
        second.close()
        firstProvider.clearAll()
        secondProvider.clearAll()
        parent.viewModelStore.clear()
    }

    @Test
    fun `provider clear all rejects use and waits for active child leases`() {
        val parent = TestParentOwner()
        val provider = ViewModelScopeProvider(parent, "provider")
        val lease = provider.acquireOwner("child")
        val model = trackingViewModel(lease.owner)

        provider.clearAll()

        assertFalse(model.cleared)
        val disposedFailure = runCatching {
            provider.acquireOwner("another-child")
        }.exceptionOrNull()
        assertTrue(disposedFailure is IllegalStateException)
        assertTrue(disposedFailure?.message.orEmpty().contains("permanently disposed"))

        lease.close()
        assertTrue(model.cleared)

        val replacement = ViewModelScopeProvider(parent, "provider")
        val replacementLease = replacement.acquireOwner("child")
        assertNotSame(model, trackingViewModel(replacementLease.owner))
        replacementLease.close()
        replacement.clearAll()
        parent.viewModelStore.clear()
    }

    @Test
    fun `parent store clear defers child onCleared until its lease closes`() {
        val parent = TestParentOwner()
        val provider = ViewModelScopeProvider(parent, "provider")
        val lease = provider.acquireOwner("child")
        val model = trackingViewModel(lease.owner)

        parent.viewModelStore.clear()

        assertFalse(model.cleared)
        lease.close()
        assertTrue(model.cleared)
    }

    @Test
    fun `child owner inherits defensive extras factory and default arguments`() {
        val parentExtras = MutableCreationExtras().apply {
            this[TestIndexKey] = 17
        }
        val parentFactory = SavedStateViewModelFactory()
        val parent = TestParentOwner(
            extras = parentExtras,
            factory = parentFactory,
        )
        val arguments = Bundle().apply { putString("profile", "primary") }
        val provider = ViewModelScopeProvider(
            parentOwner = parent,
            providerKey = "provider",
            defaultArguments = arguments,
        )
        parentExtras[TestIndexKey] = 99
        arguments.putString("profile", "mutated")
        val lease = provider.acquireOwner("child")
        val defaults = lease.owner as HasDefaultViewModelProviderFactory

        assertSame(parentFactory, defaults.defaultViewModelProviderFactory)
        assertEquals(17, defaults.defaultViewModelCreationExtras[TestIndexKey])
        assertEquals(
            "primary",
            defaults.defaultViewModelCreationExtras[androidx.lifecycle.DEFAULT_ARGS_KEY]
                ?.getString("profile"),
        )

        lease.close()
        provider.clearAll()
        parent.viewModelStore.clear()
    }

    @Test
    fun `active child rejects a different live saved state boundary`() {
        val parent = TestParentOwner()
        val provider = ViewModelScopeProvider(parent, "provider")
        val firstOwner = TestSavedStateOwner()
        val secondOwner = TestSavedStateOwner()
        val lease = provider.acquireOwner(
            key = "child",
            savedStateRegistryOwner = firstOwner,
        )

        val failure = runCatching {
            provider.acquireOwner(
                key = "child",
                savedStateRegistryOwner = secondOwner,
            )
        }.exceptionOrNull()

        assertTrue(failure is IllegalStateException)
        assertTrue(failure?.message.orEmpty().contains("cannot change SavedStateRegistryOwner"))
        lease.close()
        provider.clearAll()
        parent.viewModelStore.clear()
    }

    private fun trackingViewModel(owner: ViewModelStoreOwner): TrackingViewModel {
        return ViewModelProvider(
            owner,
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return TrackingViewModel() as T
                }
            },
        )[TrackingViewModel::class.java]
    }

    private class TrackingViewModel : ViewModel() {
        var cleared: Boolean = false
            private set

        override fun onCleared() {
            cleared = true
        }
    }

    private data class StableKey(
        val value: String,
    )

    private object TestIndexKey : CreationExtras.Key<Int>

    private class TestParentOwner(
        private val extras: CreationExtras = CreationExtras.Empty,
        private val factory: ViewModelProvider.Factory = ViewModelProvider.NewInstanceFactory(),
        override val viewModelStore: ViewModelStore = ViewModelStore(),
    ) : ViewModelStoreOwner, HasDefaultViewModelProviderFactory {
        override val defaultViewModelCreationExtras: CreationExtras
            get() = extras

        override val defaultViewModelProviderFactory: ViewModelProvider.Factory
            get() = factory
    }

    private class TestSavedStateOwner : SavedStateRegistryOwner, LifecycleOwner {
        private val lifecycleRegistry = LifecycleRegistry(this)
        private val controller = SavedStateRegistryController.create(this)

        override val lifecycle: Lifecycle
            get() = lifecycleRegistry

        override val savedStateRegistry: SavedStateRegistry
            get() = controller.savedStateRegistry

        init {
            controller.performAttach()
            controller.performRestore(null)
            lifecycleRegistry.currentState = Lifecycle.State.CREATED
        }
    }
}
