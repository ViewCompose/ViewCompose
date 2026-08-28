package com.viewcompose.lifecycle

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import com.viewcompose.ui.foundation.buildVNodeTree
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class LifecycleLocalsTest {
    @Test
    fun `local lifecycle owner defaults to null`() {
        assertNull(LocalLifecycleOwner.current)
    }

    @Test
    fun `provide lifecycle owner publishes value and restores after scope`() {
        val owner = TestLifecycleOwner()
        var inside: LifecycleOwner? = null

        buildVNodeTree {
            ProvideLifecycleOwner(owner) {
                inside = LocalLifecycleOwner.current
            }
        }

        assertSame(owner, inside)
        assertNull(LocalLifecycleOwner.current)
    }

    @Test
    fun `nested lifecycle owner restores outer owner after declaration failure`() {
        val outerOwner = TestLifecycleOwner()
        val innerOwner = TestLifecycleOwner()
        var restoredOwner: LifecycleOwner? = null

        val error = runCatching {
            buildVNodeTree {
                ProvideLifecycleOwner(outerOwner) {
                    runCatching {
                        ProvideLifecycleOwner(innerOwner) {
                            assertSame(innerOwner, LocalLifecycleOwner.current)
                            error("declaration failed")
                        }
                    }
                    restoredOwner = LocalLifecycleOwner.current
                    error("propagate failure")
                }
            }
        }.exceptionOrNull()

        assertSame(outerOwner, restoredOwner)
        assertNull(LocalLifecycleOwner.current)
        org.junit.Assert.assertTrue(error is IllegalStateException)
    }

    @Test
    fun `saved state owner local is independent and restores after scope`() {
        val owner = TestSavedStateOwner()
        var inside: SavedStateRegistryOwner? = null

        buildVNodeTree {
            ProvideSavedStateRegistryOwner(owner) {
                inside = LocalSavedStateRegistryOwner.current
                assertNull(LocalLifecycleOwner.current)
            }
        }

        assertSame(owner, inside)
        assertNull(LocalSavedStateRegistryOwner.current)
        owner.destroy()
    }

    private class TestLifecycleOwner : LifecycleOwner {
        private val registry = LifecycleRegistry.createUnsafe(this)

        override val lifecycle: Lifecycle
            get() = registry
    }

    private class TestSavedStateOwner : SavedStateRegistryOwner {
        private val registry = LifecycleRegistry.createUnsafe(this)
        private val controller = SavedStateRegistryController.create(this)

        override val lifecycle: Lifecycle
            get() = registry

        override val savedStateRegistry: SavedStateRegistry
            get() = controller.savedStateRegistry

        init {
            controller.performAttach()
            controller.performRestore(null)
            registry.currentState = Lifecycle.State.CREATED
        }

        fun destroy() {
            registry.currentState = Lifecycle.State.DESTROYED
        }
    }
}
