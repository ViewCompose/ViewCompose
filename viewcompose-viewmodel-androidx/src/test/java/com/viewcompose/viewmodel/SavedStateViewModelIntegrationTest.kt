package com.viewcompose.viewmodel

/*
 * Test responsibility: proves constructor/default-Factory and initializer-owned SavedStateHandle
 * restoration and guards the single-owner contract after the standalone handle-holder hard cut.
 */

import android.os.Bundle
import androidx.lifecycle.DEFAULT_ARGS_KEY
import androidx.lifecycle.HasDefaultViewModelProviderFactory
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.SAVED_STATE_REGISTRY_OWNER_KEY
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.SavedStateViewModelFactory
import androidx.lifecycle.VIEW_MODEL_STORE_OWNER_KEY
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.enableSavedStateHandles
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.MutableCreationExtras
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SavedStateViewModelIntegrationTest {
    @Test
    fun `default factory supplies constructor handle with default arguments`() {
        val owner = TestSavedStateOwner(
            defaultArgs = Bundle().apply {
                putLong("documentId", 42L)
                putBoolean("editable", true)
            },
        )
        val harness = WidgetCoreRuntimeHarness()

        val model: SavedBusinessStateViewModel = harness.render {
            viewModel(owner = owner)
        }

        assertEquals(42L, model.handle.get<Long>("documentId"))
        assertEquals(true, model.handle.get<Boolean>("editable"))
        harness.dispose()
        owner.destroy()
    }

    @Test
    fun `initializer handle state flow survives process style owner recreation`() {
        val defaultArgs = Bundle().apply { putString("profileId", "primary") }
        val firstOwner = TestSavedStateOwner(defaultArgs = defaultArgs)
        val firstHarness = WidgetCoreRuntimeHarness()
        val first: SavedBusinessStateViewModel = firstHarness.render {
            viewModel(owner = firstOwner) {
                SavedBusinessStateViewModel(createSavedStateHandle())
            }
        }
        first.counter.value = 17

        val saved = firstOwner.save()
        firstHarness.dispose()
        firstOwner.destroy()

        val restoredOwner = TestSavedStateOwner(
            restoredState = saved,
            defaultArgs = defaultArgs,
        )
        val restoredHarness = WidgetCoreRuntimeHarness()
        val restored: SavedBusinessStateViewModel = restoredHarness.render {
            viewModel(owner = restoredOwner) {
                SavedBusinessStateViewModel(createSavedStateHandle())
            }
        }

        assertEquals("primary", restored.handle.get<String>("profileId"))
        assertEquals(17, restored.counter.value)
        restoredHarness.dispose()
        restoredOwner.destroy()
    }

    @Test
    fun `keyed business ViewModels restore isolated SavedStateHandle namespaces`() {
        val firstOwner = TestSavedStateOwner()
        val firstHarness = WidgetCoreRuntimeHarness()
        lateinit var first: SavedBusinessStateViewModel
        lateinit var second: SavedBusinessStateViewModel
        firstHarness.renderTree {
            first = viewModel(key = "first", owner = firstOwner) {
                SavedBusinessStateViewModel(createSavedStateHandle())
            }
            second = viewModel(key = "second", owner = firstOwner) {
                SavedBusinessStateViewModel(createSavedStateHandle())
            }
        }
        first.counter.value = 11
        second.counter.value = 22

        val saved = firstOwner.save()
        firstHarness.dispose()
        firstOwner.destroy()

        val restoredOwner = TestSavedStateOwner(restoredState = saved)
        val restoredHarness = WidgetCoreRuntimeHarness()
        lateinit var restoredFirst: SavedBusinessStateViewModel
        lateinit var restoredSecond: SavedBusinessStateViewModel
        restoredHarness.renderTree {
            restoredFirst = viewModel(key = "first", owner = restoredOwner) {
                SavedBusinessStateViewModel(createSavedStateHandle())
            }
            restoredSecond = viewModel(key = "second", owner = restoredOwner) {
                SavedBusinessStateViewModel(createSavedStateHandle())
            }
        }

        assertNotSame(restoredFirst, restoredSecond)
        assertEquals(11, restoredFirst.counter.value)
        assertEquals(22, restoredSecond.counter.value)
        restoredHarness.dispose()
        restoredOwner.destroy()
    }

    @Test
    fun `repeated lookup registers one handle provider and initializer per ViewModel key`() {
        val owner = TestSavedStateOwner()
        val harness = WidgetCoreRuntimeHarness()
        var initializerRuns = 0
        lateinit var first: SavedBusinessStateViewModel
        lateinit var second: SavedBusinessStateViewModel

        harness.renderTree {
            first = viewModel(key = "business", owner = owner) {
                initializerRuns += 1
                SavedBusinessStateViewModel(createSavedStateHandle())
            }
            second = viewModel(key = "business", owner = owner) {
                initializerRuns += 1
                SavedBusinessStateViewModel(createSavedStateHandle())
            }
        }

        assertSame(first, second)
        assertEquals(1, initializerRuns)
        first.counter.value = 31
        owner.save()
        assertEquals(31, first.counter.value)
        assertEquals(1, initializerRuns)
        harness.dispose()
        owner.destroy()
    }

    @Test
    fun `successive process style recreations persist the latest value without replay`() {
        val firstOwner = TestSavedStateOwner()
        val firstHarness = WidgetCoreRuntimeHarness()
        val first: SavedBusinessStateViewModel = firstHarness.render {
            viewModel(owner = firstOwner) {
                SavedBusinessStateViewModel(createSavedStateHandle())
            }
        }
        first.counter.value = 17
        val firstSaved = firstOwner.save()
        firstHarness.dispose()
        firstOwner.destroy()

        val secondOwner = TestSavedStateOwner(restoredState = firstSaved)
        val secondHarness = WidgetCoreRuntimeHarness()
        val second: SavedBusinessStateViewModel = secondHarness.render {
            viewModel(owner = secondOwner) {
                SavedBusinessStateViewModel(createSavedStateHandle())
            }
        }
        assertEquals(17, second.counter.value)
        second.counter.value = 29
        val secondSaved = secondOwner.save()
        secondHarness.dispose()
        secondOwner.destroy()

        val thirdOwner = TestSavedStateOwner(restoredState = secondSaved)
        val thirdHarness = WidgetCoreRuntimeHarness()
        val third: SavedBusinessStateViewModel = thirdHarness.render {
            viewModel(owner = thirdOwner) {
                SavedBusinessStateViewModel(createSavedStateHandle())
            }
        }

        assertEquals(29, third.counter.value)
        thirdHarness.dispose()
        thirdOwner.destroy()
    }

    class SavedBusinessStateViewModel(
        val handle: SavedStateHandle,
    ) : ViewModel() {
        val counter = handle.getMutableStateFlow("counter", 0)
    }

    private class TestSavedStateOwner(
        restoredState: Bundle? = null,
        defaultArgs: Bundle = Bundle(),
    ) : LifecycleOwner,
        ViewModelStoreOwner,
        SavedStateRegistryOwner,
        HasDefaultViewModelProviderFactory {
        private val lifecycleRegistry = LifecycleRegistry(this)
        private val savedStateController = SavedStateRegistryController.create(this)
        private val initialArguments = Bundle(defaultArgs)

        override val lifecycle: Lifecycle
            get() = lifecycleRegistry

        override val viewModelStore: ViewModelStore = ViewModelStore()

        override val savedStateRegistry: SavedStateRegistry
            get() = savedStateController.savedStateRegistry

        override val defaultViewModelProviderFactory: ViewModelProvider.Factory =
            SavedStateViewModelFactory(
                null,
                this,
                initialArguments,
            )

        override val defaultViewModelCreationExtras: CreationExtras
            get() = MutableCreationExtras().apply {
                this[SAVED_STATE_REGISTRY_OWNER_KEY] = this@TestSavedStateOwner
                this[VIEW_MODEL_STORE_OWNER_KEY] = this@TestSavedStateOwner
                this[DEFAULT_ARGS_KEY] = Bundle(initialArguments)
            }

        init {
            savedStateController.performAttach()
            enableSavedStateHandles()
            savedStateController.performRestore(restoredState?.let(::Bundle))
            lifecycleRegistry.currentState = Lifecycle.State.CREATED
        }

        fun save(): Bundle = Bundle().also(savedStateController::performSave)

        fun destroy() {
            lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
            viewModelStore.clear()
        }
    }
}
