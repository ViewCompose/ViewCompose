package com.viewcompose.viewmodel

/*
 * 测试职责：覆盖 viewmodel integration 中的 View Model Composition 行为，防止关键契约在后续重构中回退。
 * Test responsibility: covers View Model Composition behavior in viewmodel integration and guards the contract against regressions.
 */

import androidx.lifecycle.HasDefaultViewModelProviderFactory
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.MutableCreationExtras
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class ViewModelCompositionTest {
    @Test
    fun `viewModel reuses instance when owner is stable`() {
        val harness = WidgetCoreRuntimeHarness()
        val owner = TestViewModelStoreOwner()
        val first: TestViewModel = harness.render { viewModel(owner = owner) }
        val second: TestViewModel = harness.render { viewModel(owner = owner) }

        assertSame(first, second)
        harness.dispose()
        owner.viewModelStore.clear()
    }

    @Test
    fun `KClass lookup delegates to the same store-only resolver`() {
        val harness = WidgetCoreRuntimeHarness()
        val owner = TestViewModelStoreOwner()
        val reified: TestViewModel = harness.render {
            viewModel(key = "shared", owner = owner)
        }
        val runtimeSelected = harness.render {
            viewModel(
                modelClass = TestViewModel::class,
                key = "shared",
                owner = owner,
            )
        }

        assertSame(reified, runtimeSelected)
        harness.dispose()
        owner.viewModelStore.clear()
    }

    @Test
    fun `viewModel returns different instances for different keys`() {
        val harness = WidgetCoreRuntimeHarness()
        val owner = TestViewModelStoreOwner()
        lateinit var first: TestViewModel
        lateinit var second: TestViewModel

        harness.render {
            first = viewModel(key = "first", owner = owner)
            second = viewModel(key = "second", owner = owner)
        }

        assertNotSame(first, second)
        harness.dispose()
        owner.viewModelStore.clear()
    }

    @Test
    fun `viewModel resolves owner from local provider`() {
        val harness = WidgetCoreRuntimeHarness()
        val owner = TestViewModelStoreOwner()
        lateinit var first: TestViewModel
        lateinit var second: TestViewModel

        harness.renderTree {
            ProvideViewModelStoreOwner(owner) {
                first = viewModel()
            }
        }
        harness.renderTree {
            ProvideViewModelStoreOwner(owner) {
                second = viewModel()
            }
        }

        assertSame(first, second)
        harness.dispose()
        owner.viewModelStore.clear()
    }

    @Test
    fun `explicit owner wins over an unrelated local owner`() {
        val localOwner = TestViewModelStoreOwner()
        val explicitOwner = TestViewModelStoreOwner()
        val harness = WidgetCoreRuntimeHarness()

        lateinit var explicitModel: TestViewModel
        harness.renderTree {
            ProvideViewModelStoreOwner(localOwner) {
                explicitModel = viewModel(owner = explicitOwner)
            }
        }

        val explicitStoreModel = ViewModelProvider(explicitOwner)[TestViewModel::class.java]
        val localStoreModel = ViewModelProvider(localOwner)[TestViewModel::class.java]
        assertSame(explicitStoreModel, explicitModel)
        assertNotSame(localStoreModel, explicitModel)

        harness.dispose()
        localOwner.viewModelStore.clear()
        explicitOwner.viewModelStore.clear()
    }

    @Test
    fun `viewModel throws when owner is missing`() {
        val error = runCatching {
            viewModel<TestViewModel>()
        }.exceptionOrNull()

        assertTrue(error is IllegalArgumentException)
        assertTrue(error?.message.orEmpty().contains("ProvideViewModelStoreOwner"))
    }

    @Test
    fun `viewModel uses custom factory and keeps same instance across calls`() {
        val harness = WidgetCoreRuntimeHarness()
        val owner = TestViewModelStoreOwner()
        var createCount = 0
        val factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                createCount += 1
                return FactoryBackedViewModel(createCount) as T
            }
        }

        val first: FactoryBackedViewModel = harness.render {
            viewModel(
                owner = owner,
                factory = factory,
            )
        }
        val second: FactoryBackedViewModel = harness.render {
            viewModel(
                owner = owner,
                factory = factory,
            )
        }

        assertSame(first, second)
        assertEquals(1, createCount)
        harness.dispose()
        owner.viewModelStore.clear()
    }

    @Test
    fun `viewModel uses owner's default factory when override is absent`() {
        val harness = WidgetCoreRuntimeHarness()
        val owner = DefaultFactoryOwner()
        val first: FactoryBackedViewModel = harness.render { viewModel(owner = owner) }
        val second: FactoryBackedViewModel = harness.render { viewModel(owner = owner) }

        assertSame(first, second)
        assertEquals(1, owner.createCount)
        harness.dispose()
        owner.viewModelStore.clear()
    }

    @Test
    fun `viewModel override factory has priority over owner default factory`() {
        val harness = WidgetCoreRuntimeHarness()
        val owner = DefaultFactoryOwner()
        var overrideCreateCount = 0
        val overrideFactory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                overrideCreateCount += 1
                return FactoryBackedViewModel(100 + overrideCreateCount) as T
            }
        }

        harness.render {
            viewModel<FactoryBackedViewModel>(
                owner = owner,
                factory = overrideFactory,
            )
        }

        assertEquals(0, owner.createCount)
        assertEquals(1, overrideCreateCount)
        harness.dispose()
        owner.viewModelStore.clear()
    }

    @Test
    fun `viewModel queries store again after clear instead of returning remembered instance`() {
        val harness = WidgetCoreRuntimeHarness()
        val owner = TestViewModelStoreOwner()
        var clearedCount = 0

        val first: ClearingViewModel = harness.render {
            viewModel(owner = owner) {
                ClearingViewModel { clearedCount += 1 }
            }
        }

        owner.viewModelStore.clear()
        assertEquals(1, clearedCount)

        val second: ClearingViewModel = harness.render {
            viewModel(owner = owner) {
                ClearingViewModel { clearedCount += 1 }
            }
        }

        assertNotSame(first, second)
        harness.dispose()
        owner.viewModelStore.clear()
        assertEquals(2, clearedCount)
    }

    @Test
    fun `null empty blank and ordinary keys retain distinct AndroidX identities`() {
        val harness = WidgetCoreRuntimeHarness()
        val owner = TestViewModelStoreOwner()
        lateinit var defaultModel: TestViewModel
        lateinit var emptyModel: TestViewModel
        lateinit var blankModel: TestViewModel
        lateinit var ordinaryModel: TestViewModel
        lateinit var repeatedEmptyModel: TestViewModel

        harness.render {
            defaultModel = viewModel(key = null, owner = owner)
            emptyModel = viewModel(key = "", owner = owner)
            blankModel = viewModel(key = "   ", owner = owner)
            ordinaryModel = viewModel(key = "profile", owner = owner)
            repeatedEmptyModel = viewModel(key = "", owner = owner)
        }

        assertNotSame(defaultModel, emptyModel)
        assertNotSame(emptyModel, blankModel)
        assertNotSame(blankModel, ordinaryModel)
        assertSame(emptyModel, repeatedEmptyModel)
        harness.dispose()
        owner.viewModelStore.clear()
    }

    @Test
    fun `explicit keys reach initializer extras byte for byte`() {
        val harness = WidgetCoreRuntimeHarness()
        val owner = TestViewModelStoreOwner()
        val observedKeys = mutableListOf<String?>()

        harness.render {
            viewModel<TestViewModel>(key = "", owner = owner) {
                observedKeys += this[ViewModelProvider.VIEW_MODEL_KEY]
                TestViewModel()
            }
            viewModel<TestViewModel>(key = " \t ", owner = owner) {
                observedKeys += this[ViewModelProvider.VIEW_MODEL_KEY]
                TestViewModel()
            }
        }

        assertEquals(listOf("", " \t "), observedKeys)
        harness.dispose()
        owner.viewModelStore.clear()
    }

    @Test
    fun `viewModel owner replacement resolves a different store entry`() {
        val harness = WidgetCoreRuntimeHarness()
        val firstOwner = TestViewModelStoreOwner()
        val secondOwner = TestViewModelStoreOwner()
        val first: TestViewModel = harness.render { viewModel(owner = firstOwner) }
        val second: TestViewModel = harness.render { viewModel(owner = secondOwner) }

        assertNotSame(first, second)
        harness.dispose()
        firstOwner.viewModelStore.clear()
        secondOwner.viewModelStore.clear()
    }

    @Test
    fun `explicit extras reach custom factory without mutating owner defaults`() {
        val harness = WidgetCoreRuntimeHarness()
        val ownerExtras = MutableCreationExtras().apply { this[TestIndexKey] = 7 }
        val owner = DefaultExtrasOwner(ownerExtras)
        val explicitExtras = MutableCreationExtras().apply { this[TestIndexKey] = 41 }
        var observedIndex: Int? = null
        val factory = extrasFactory { extras ->
            observedIndex = extras[TestIndexKey]
            FactoryBackedViewModel(checkNotNull(observedIndex))
        }

        val model: FactoryBackedViewModel = harness.render {
            viewModel(
                owner = owner,
                factory = factory,
                extras = explicitExtras,
            )
        }

        assertEquals(41, model.index)
        assertEquals(7, ownerExtras[TestIndexKey])
        harness.dispose()
        owner.viewModelStore.clear()
    }

    @Test
    fun `owner default extras reach default factory through a defensive copy`() {
        val harness = WidgetCoreRuntimeHarness()
        val ownerExtras = MutableCreationExtras().apply { this[TestIndexKey] = 9 }
        val owner = DefaultExtrasOwner(ownerExtras)
        var receivedExtras: CreationExtras? = null
        owner.factory = extrasFactory { extras ->
            receivedExtras = extras
            FactoryBackedViewModel(checkNotNull(extras[TestIndexKey]))
        }

        val model: FactoryBackedViewModel = harness.render { viewModel(owner = owner) }

        assertEquals(9, model.index)
        assertNotSame(ownerExtras, receivedExtras)
        harness.dispose()
        owner.viewModelStore.clear()
    }

    @Test
    fun `existing store entry ignores replacement factory and extras`() {
        val harness = WidgetCoreRuntimeHarness()
        val owner = TestViewModelStoreOwner()
        var firstCreates = 0
        var replacementCreates = 0
        val firstFactory = extrasFactory {
            firstCreates += 1
            FactoryBackedViewModel(1)
        }
        val replacementFactory = extrasFactory {
            replacementCreates += 1
            FactoryBackedViewModel(2)
        }

        val first: FactoryBackedViewModel = harness.render {
            viewModel(
                owner = owner,
                factory = firstFactory,
                extras = MutableCreationExtras().apply { this[TestIndexKey] = 1 },
            )
        }
        val second: FactoryBackedViewModel = harness.render {
            viewModel(
                owner = owner,
                factory = replacementFactory,
                extras = MutableCreationExtras().apply { this[TestIndexKey] = 2 },
            )
        }

        assertSame(first, second)
        assertEquals(1, firstCreates)
        assertEquals(0, replacementCreates)
        harness.dispose()
        owner.viewModelStore.clear()
    }

    @Test
    fun `reified initializer receives owner extras and runs once`() {
        val harness = WidgetCoreRuntimeHarness()
        val ownerExtras = MutableCreationExtras().apply { this[TestIndexKey] = 23 }
        val owner = DefaultExtrasOwner(ownerExtras)
        var initializeCount = 0

        val first: FactoryBackedViewModel = harness.render {
            viewModel(owner = owner) {
                initializeCount += 1
                FactoryBackedViewModel(checkNotNull(this[TestIndexKey]))
            }
        }
        val second: FactoryBackedViewModel = harness.render {
            viewModel(owner = owner) {
                initializeCount += 1
                FactoryBackedViewModel(99)
            }
        }

        assertSame(first, second)
        assertEquals(23, first.index)
        assertEquals(1, initializeCount)
        harness.dispose()
        owner.viewModelStore.clear()
    }

    @Test
    fun `KClass initializer uses the same store and extras contract`() {
        val harness = WidgetCoreRuntimeHarness()
        val ownerExtras = MutableCreationExtras().apply { this[TestIndexKey] = 31 }
        val owner = DefaultExtrasOwner(ownerExtras)
        var initializeCount = 0

        val first = harness.render {
            viewModel(
                modelClass = FactoryBackedViewModel::class,
                owner = owner,
            ) {
                initializeCount += 1
                FactoryBackedViewModel(checkNotNull(this[TestIndexKey]))
            }
        }
        val second = harness.render {
            viewModel(
                modelClass = FactoryBackedViewModel::class,
                owner = owner,
            ) {
                initializeCount += 1
                FactoryBackedViewModel(99)
            }
        }

        assertSame(first, second)
        assertEquals(31, first.index)
        assertEquals(1, initializeCount)
        harness.dispose()
        owner.viewModelStore.clear()
    }

    @Test
    fun `initializer failure publishes no store entry and later lookup retries`() {
        val harness = WidgetCoreRuntimeHarness()
        val owner = TestViewModelStoreOwner()
        var attempts = 0

        val failure = runCatching {
            harness.render<FactoryBackedViewModel> {
                viewModel(owner = owner) {
                    attempts += 1
                    error("initializer failed")
                }
            }
        }.exceptionOrNull()
        val recovered: FactoryBackedViewModel = harness.render {
            viewModel(owner = owner) {
                attempts += 1
                FactoryBackedViewModel(attempts)
            }
        }

        assertTrue(failure.hasCauseMessage("initializer failed"))
        assertEquals(2, attempts)
        assertEquals(2, recovered.index)
        harness.dispose()
        owner.viewModelStore.clear()
    }

    @Test
    fun `factory failure publishes no store entry and later lookup retries`() {
        val harness = WidgetCoreRuntimeHarness()
        val owner = TestViewModelStoreOwner()
        var attempts = 0
        val factory = extrasFactory {
            attempts += 1
            if (attempts == 1) error("factory failed")
            FactoryBackedViewModel(attempts)
        }

        val failure = runCatching {
            harness.render<FactoryBackedViewModel> {
                viewModel(owner = owner, factory = factory)
            }
        }.exceptionOrNull()
        val recovered: FactoryBackedViewModel = harness.render {
            viewModel(owner = owner, factory = factory)
        }

        assertTrue(failure.hasCauseMessage("factory failed"))
        assertEquals(2, recovered.index)
        harness.dispose()
        owner.viewModelStore.clear()
    }

    @Test
    fun `different model class under one explicit key replaces and clears old entry`() {
        val harness = WidgetCoreRuntimeHarness()
        val owner = TestViewModelStoreOwner()
        var clearedCount = 0
        val first: ClearingViewModel = harness.render {
            viewModel(key = "shared", owner = owner) {
                ClearingViewModel { clearedCount += 1 }
            }
        }

        val second: FactoryBackedViewModel = harness.render {
            viewModel(key = "shared", owner = owner) {
                FactoryBackedViewModel(77)
            }
        }

        assertNotEquals(first::class, second::class)
        assertEquals(1, clearedCount)
        assertEquals(77, second.index)
        harness.dispose()
        owner.viewModelStore.clear()
    }

    @Test
    fun `explicit owner still requires an active ViewCompose composition`() {
        val owner = TestViewModelStoreOwner()

        val failure = runCatching {
            viewModel<TestViewModel>(owner = owner)
        }.exceptionOrNull()

        assertTrue(failure is IllegalStateException)
        assertTrue(failure?.message.orEmpty().contains("active ViewCompose composition"))
        owner.viewModelStore.clear()
    }

    class TestViewModel : ViewModel()

    class FactoryBackedViewModel(
        val index: Int,
    ) : ViewModel()

    class ClearingViewModel(
        private val onClear: () -> Unit,
    ) : ViewModel() {
        override fun onCleared() {
            onClear()
        }
    }

    class TestViewModelStoreOwner : ViewModelStoreOwner {
        override val viewModelStore: ViewModelStore = ViewModelStore()
    }

    class DefaultFactoryOwner : ViewModelStoreOwner, HasDefaultViewModelProviderFactory {
        override val viewModelStore: ViewModelStore = ViewModelStore()
        var createCount: Int = 0

        override val defaultViewModelProviderFactory: ViewModelProvider.Factory
            get() = object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    createCount += 1
                    return FactoryBackedViewModel(createCount) as T
                }
            }

        override val defaultViewModelCreationExtras: CreationExtras
            get() = CreationExtras.Empty
    }

    class DefaultExtrasOwner(
        private val extras: CreationExtras,
    ) : ViewModelStoreOwner, HasDefaultViewModelProviderFactory {
        override val viewModelStore: ViewModelStore = ViewModelStore()
        var factory: ViewModelProvider.Factory = ViewModelProvider.NewInstanceFactory()

        override val defaultViewModelProviderFactory: ViewModelProvider.Factory
            get() = factory

        override val defaultViewModelCreationExtras: CreationExtras
            get() = extras
    }

    private object TestIndexKey : CreationExtras.Key<Int>

    private fun extrasFactory(
        create: (CreationExtras) -> ViewModel,
    ): ViewModelProvider.Factory {
        return object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(
                modelClass: Class<T>,
                extras: CreationExtras,
            ): T = create(extras) as T
        }
    }

    private fun Throwable?.hasCauseMessage(expected: String): Boolean {
        var current = this
        while (current != null) {
            if (current.message == expected) return true
            current = current.cause
        }
        return false
    }
}
