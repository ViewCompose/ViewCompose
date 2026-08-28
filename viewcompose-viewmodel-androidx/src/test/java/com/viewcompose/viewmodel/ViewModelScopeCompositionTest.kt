package com.viewcompose.viewmodel

/*
 * 测试职责：覆盖 retained ViewModel scope 的组合提交、回滚、本地传播与宿主重建契约。
 * Test responsibility: covers composition commit, rollback, local propagation, and host recreation contracts for retained ViewModel scopes.
 */

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import com.viewcompose.ui.foundation.captureUiLocalSnapshot
import com.viewcompose.ui.foundation.withUiLocalSnapshot
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ViewModelScopeCompositionTest {
    @Test
    fun `remembered provider and owner reuse one child store across recomposition`() {
        val parent = TestParentOwner()
        val harness = WidgetCoreRuntimeHarness()
        lateinit var first: TrackingViewModel
        lateinit var second: TrackingViewModel

        harness.render {
            val provider = rememberViewModelScopeProvider("provider", parent)
            val owner = rememberViewModelStoreOwner("child", provider)
            first = viewModel(owner = owner) { TrackingViewModel() }
        }
        harness.render {
            val provider = rememberViewModelScopeProvider("provider", parent)
            val owner = rememberViewModelStoreOwner("child", provider)
            second = viewModel(owner = owner) { TrackingViewModel() }
        }

        assertSame(first, second)
        harness.dispose()
        assertTrue(first.cleared)
        parent.viewModelStore.clear()
    }

    @Test
    fun `temporary owner absence releases its lease without clearing logical scope`() {
        val parent = TestParentOwner()
        val harness = WidgetCoreRuntimeHarness()
        var includeChild = true
        lateinit var provider: ViewModelScopeProvider
        lateinit var model: TrackingViewModel

        fun render() {
            harness.render {
                provider = rememberViewModelScopeProvider("provider", parent)
                if (includeChild) {
                    val owner = rememberViewModelStoreOwner("child", provider)
                    model = viewModel(owner = owner) { TrackingViewModel() }
                }
            }
        }

        render()
        includeChild = false
        render()

        assertFalse(model.cleared)
        provider.clear("child")
        assertTrue(model.cleared)
        harness.dispose()
        parent.viewModelStore.clear()
    }

    @Test
    fun `aborted first candidate clears its new scope and retry starts fresh`() {
        val parent = TestParentOwner()
        val harness = WidgetCoreRuntimeHarness()
        lateinit var abandoned: TrackingViewModel
        val prepared = harness.prepare {
            val provider = rememberViewModelScopeProvider("provider", parent)
            val owner = rememberViewModelStoreOwner("child", provider)
            abandoned = viewModel(owner = owner) { TrackingViewModel() }
        }

        prepared.abort()

        assertTrue(abandoned.cleared)
        val recovered = harness.render {
            val provider = rememberViewModelScopeProvider("provider", parent)
            val owner = rememberViewModelStoreOwner("child", provider)
            viewModel(owner = owner) { TrackingViewModel() }
        }
        assertNotSame(abandoned, recovered)

        harness.dispose()
        assertTrue(recovered.cleared)
        parent.viewModelStore.clear()
    }

    @Test
    fun `aborted candidate sharing a committed scope preserves existing ViewModel`() {
        val parent = TestParentOwner()
        val committedHarness = WidgetCoreRuntimeHarness()
        val committed = committedHarness.render {
            val provider = rememberViewModelScopeProvider("provider", parent)
            val owner = rememberViewModelStoreOwner("child", provider)
            viewModel(owner = owner) { TrackingViewModel() }
        }
        val candidateHarness = WidgetCoreRuntimeHarness()
        lateinit var candidate: TrackingViewModel
        val prepared = candidateHarness.prepare {
            val provider = rememberViewModelScopeProvider("provider", parent)
            val owner = rememberViewModelStoreOwner("child", provider)
            candidate = viewModel(owner = owner) { TrackingViewModel() }
        }

        assertSame(committed, candidate)
        prepared.abort()
        assertFalse(committed.cleared)

        val retained = committedHarness.render {
            val provider = rememberViewModelScopeProvider("provider", parent)
            val owner = rememberViewModelStoreOwner("child", provider)
            viewModel(owner = owner) { TrackingViewModel() }
        }
        assertSame(committed, retained)

        candidateHarness.dispose()
        committedHarness.dispose()
        assertTrue(committed.cleared)
        parent.viewModelStore.clear()
    }

    @Test
    fun `destroyed lifecycle disposal retains stores for parent recreation`() {
        val retainedStore = ViewModelStore()
        val firstParent = TestParentOwner(retainedStore)
        val firstHarness = WidgetCoreRuntimeHarness()
        val first = firstHarness.render {
            val provider = rememberViewModelScopeProvider("provider", firstParent)
            val owner = rememberViewModelStoreOwner("child", provider)
            viewModel(owner = owner) { TrackingViewModel() }
        }

        firstParent.moveTo(Lifecycle.State.DESTROYED)
        firstHarness.dispose()
        assertFalse(first.cleared)

        val recreatedParent = TestParentOwner(retainedStore)
        val recreatedHarness = WidgetCoreRuntimeHarness()
        val recreated = recreatedHarness.render {
            val provider = rememberViewModelScopeProvider("provider", recreatedParent)
            val owner = rememberViewModelStoreOwner("child", provider)
            viewModel(owner = owner) { TrackingViewModel() }
        }

        assertSame(first, recreated)
        recreatedHarness.dispose()
        assertTrue(first.cleared)
        retainedStore.clear()
    }

    @Test
    fun `captured locals keep the scoped owner for delayed composition`() {
        val parent = TestParentOwner()
        val declarationHarness = WidgetCoreRuntimeHarness()
        lateinit var declared: TrackingViewModel
        lateinit var snapshot: com.viewcompose.ui.foundation.UiLocalSnapshot

        declarationHarness.renderTree {
            ProvideViewModelStoreOwner(parent) {
                val provider = rememberViewModelScopeProvider("provider")
                val owner = rememberViewModelStoreOwner("child", provider)
                ProvideViewModelStoreOwner(owner) {
                    declared = viewModel { TrackingViewModel() }
                    snapshot = captureUiLocalSnapshot()
                }
            }
        }

        val delayedHarness = WidgetCoreRuntimeHarness()
        val delayed = delayedHarness.render {
            withUiLocalSnapshot(snapshot) {
                viewModel<TrackingViewModel>()
            }
        }

        assertSame(declared, delayed)
        delayedHarness.dispose()
        declarationHarness.dispose()
        assertTrue(declared.cleared)
        parent.viewModelStore.clear()
    }

    @Test
    fun `missing lifecycle requires the explicit split-boundary parameter`() {
        val parent = object : ViewModelStoreOwner {
            override val viewModelStore: ViewModelStore = ViewModelStore()
        }
        val harness = WidgetCoreRuntimeHarness()

        val failure = runCatching {
            harness.render {
                rememberViewModelScopeProvider("provider", parent)
            }
        }.exceptionOrNull()

        assertTrue(failure.hasCause<IllegalStateException>())
        assertTrue(failure.hasCauseMessage("pass lifecycleOwner explicitly"))
        harness.dispose()
        parent.viewModelStore.clear()
    }

    private class TrackingViewModel : ViewModel() {
        var cleared: Boolean = false
            private set

        override fun onCleared() {
            cleared = true
        }
    }

    private inline fun <reified T : Throwable> Throwable?.hasCause(): Boolean {
        var current = this
        while (current != null) {
            if (current is T) return true
            current = current.cause
        }
        return false
    }

    private fun Throwable?.hasCauseMessage(expected: String): Boolean {
        var current = this
        while (current != null) {
            if (current.message.orEmpty().contains(expected)) return true
            current = current.cause
        }
        return false
    }

    private class TestParentOwner(
        override val viewModelStore: ViewModelStore = ViewModelStore(),
    ) : ViewModelStoreOwner, LifecycleOwner {
        private val registry = LifecycleRegistry(this)

        override val lifecycle: Lifecycle
            get() = registry

        init {
            registry.currentState = Lifecycle.State.CREATED
        }

        fun moveTo(state: Lifecycle.State) {
            registry.currentState = state
        }
    }
}
