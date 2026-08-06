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
import org.junit.Assert.assertEquals
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

    class TestViewModel : ViewModel()

    class FactoryBackedViewModel(
        val index: Int,
    ) : ViewModel()

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
}
