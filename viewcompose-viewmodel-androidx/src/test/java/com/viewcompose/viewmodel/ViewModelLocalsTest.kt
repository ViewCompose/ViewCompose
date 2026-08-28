package com.viewcompose.viewmodel

/*
 * 测试职责：覆盖 viewmodel integration 中的 View Model Locals 行为，防止关键契约在后续重构中回退。
 * Test responsibility: covers View Model Locals behavior in viewmodel integration and guards the contract against regressions.
 */

import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import com.viewcompose.ui.foundation.buildVNodeTree
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class ViewModelLocalsTest {
    @Test
    fun `local view model owner defaults to null`() {
        assertNull(LocalViewModelStoreOwner.current)
    }

    @Test
    fun `provide view model owner publishes value and restores after scope`() {
        val owner = TestViewModelStoreOwner()
        var inside: ViewModelStoreOwner? = null

        buildVNodeTree {
            ProvideViewModelStoreOwner(owner) {
                inside = LocalViewModelStoreOwner.current
            }
        }

        assertSame(owner, inside)
        assertNull(LocalViewModelStoreOwner.current)
        owner.viewModelStore.clear()
    }

    @Test
    fun `nested owner restores outer owner after child failure`() {
        val outerOwner = TestViewModelStoreOwner()
        val innerOwner = TestViewModelStoreOwner()
        var failure: Throwable? = null
        var restoredOwner: ViewModelStoreOwner? = null

        buildVNodeTree {
            ProvideViewModelStoreOwner(outerOwner) {
                assertSame(outerOwner, LocalViewModelStoreOwner.current)
                failure = runCatching {
                    ProvideViewModelStoreOwner(innerOwner) {
                        assertSame(innerOwner, LocalViewModelStoreOwner.current)
                        error("expected child failure")
                    }
                }.exceptionOrNull()
                restoredOwner = LocalViewModelStoreOwner.current
            }
        }

        assertEquals("expected child failure", failure?.message)
        assertSame(outerOwner, restoredOwner)
        assertNull(LocalViewModelStoreOwner.current)
        outerOwner.viewModelStore.clear()
        innerOwner.viewModelStore.clear()
    }

    private class TestViewModelStoreOwner : ViewModelStoreOwner {
        override val viewModelStore: ViewModelStore = ViewModelStore()
    }
}
